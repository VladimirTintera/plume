package eu.tintera.plume.core.ui

import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.photopicker.compose.EmbeddedPhotoPicker
import androidx.photopicker.compose.ExperimentalPhotoPickerComposeApi
import androidx.photopicker.compose.rememberEmbeddedPhotoPickerState
import android.widget.photopicker.EmbeddedPhotoPickerFeatureInfo
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.attachment_prepare_failed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/**
 * Vestavěný výběr fotek (`androidx.photopicker`) — galerie kreslená do `SurfaceView` procesem
 * výběru fotek.
 *
 * **Volat se smí až od Androidu 14 se SDK Extensions 15** ([embeddedMediaPickerAvailable]);
 * samotná knihovna má nižší `minSdk`, takže překlad nic nehlídá a na starším systému by to
 * spadlo za běhu. Kde chybí, zůstává klasický Photo Picker.
 */
@Composable
actual fun embeddedMediaPickerAvailable(): Boolean = remember { embeddedPickerSupported }

/**
 * `SdkExtensions.getExtensionVersion` samo je od API 30, takže se napřed ptáme na verzi systému.
 * Rozšíření se ptáme na to od Androidu 14 (`UPSIDE_DOWN_CAKE`), pod kterým je picker vydaný —
 * i na novějším systému může být starší a pak vestavěný výběr není.
 */
private val embeddedPickerSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        SdkExtensions.getExtensionVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE) >= REQUIRED_EXTENSION

@OptIn(ExperimentalPhotoPickerComposeApi::class)
@Composable
actual fun EmbeddedMediaPicker(
    allowPhotos: Boolean,
    allowVideos: Boolean,
    onPicked: (List<OutgoingAttachment>) -> Unit,
    onUnpicked: (List<String>) -> Unit,
    onError: (UiText) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier,
) {
    // Pojistka: volající se ptá `embeddedMediaPickerAvailable`, ale volání pod tou hranicí
    // je pád za běhu — a to je horší než chybějící panel.
    if (!embeddedPickerSupported) return

    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val picked by rememberUpdatedState(onPicked)
    val unpicked by rememberUpdatedState(onUnpicked)
    val failed by rememberUpdatedState(onError)
    val done by rememberUpdatedState(onDone)

    /*
     * Co se z které položky galerie připravilo.
     *
     * Odznačení přijde jako URI od pickeru, ale konverzace zná přílohy podle CESTY do cache —
     * jinak by se odebíralo naslepo. Mapa žije s panelem: nová session začíná prázdná, protože
     * picker o dřívějším výběru stejně neví.
     */
    val prepared = remember { mutableMapOf<Uri, String>() }

    val state = rememberEmbeddedPhotoPickerState(
        onUriPermissionGranted = { uris ->
            if (uris.isNotEmpty()) {
                scope.launch {
                    // Kopie a zmenšení jsou práce na disku a procesoru — mimo hlavní vlákno,
                    // souběžně; `awaitAll` drží pořadí výběru.
                    val results = uris
                        .map { uri -> async(Dispatchers.IO) { runCatching { prepareVisual(context, uri) } } }
                        .awaitAll()
                    results.forEachIndexed { index, result ->
                        result.onSuccess { prepared[uris[index]] = it.path }
                        result.onFailure {
                            Logger.withTag("Attachments").w(it) { "Vybranou přílohu se nepodařilo připravit." }
                        }
                    }
                    val ready = results.mapNotNull { it.getOrNull() }
                    if (ready.isNotEmpty()) picked(ready)
                    if (ready.size < results.size) failed(UiText(Res.string.attachment_prepare_failed))
                }
            }
        },
        onUriPermissionRevoked = { uris ->
            val paths = uris.mapNotNull { prepared.remove(it) }
            if (paths.isNotEmpty()) unpicked(paths)
        },
        onSelectionComplete = { done() },
        onSessionError = { throwable ->
            // Session je po chybě mrtvá, takže panel nemá co ukazovat — zavře se jako po výběru.
            Logger.withTag("Attachments").w(throwable) { "Vestavěný výběr fotek skončil chybou." }
            done()
        },
    )

    /*
     * Rozbalený stav se NEZAPÍNÁ (`setPickerLaunchedInExpandedState`, `initialExpandedValue`).
     * Zní to jako „ukaž celou galerii", ale picker se v něm přepne do VLASTNÍ AKTIVITY —
     * ověřeno na emulátoru: `mCurrentFocus` skočil na `com.google.android.photopicker`
     * a za sheetem zůstala plocha systému. Tím by z vestavěného výběru zase byl ten systémový,
     * jen složitější cestou.
     */
    val colors = MaterialTheme.colorScheme
    val dark = colors.surface.luminance() < 0.5f
    val accent = colors.primary
    val info = remember(accent, dark, allowPhotos, allowVideos) {
        EmbeddedPhotoPickerFeatureInfo.Builder()
            .setMaxSelectionLimit(PICK_LIMIT)
            /*
             * Co chat nepřijme, se ani nenabídne — jinak by skupina jen pro fotky nechala
             * uživatele vybrat video a chyba by přišla až od serveru (totéž pravidlo jako
             * `ContentAccess` u sponky). Bez omezení se nechává výchozí, tedy obojí.
             */
            .apply {
                if (allowPhotos != allowVideos) {
                    setMimeTypes(listOf(if (allowPhotos) "image/*" else "video/*"))
                }
            }
            // Pořadí výběru se drží — album odejde tak, jak ho uživatel naklikal (na iOS to
            // u PHPickeru dělá `selection = Ordered`).
            .setOrderedSelection(true)
            // Picker jinak jede podle systémového motivu; uživatel si ale v Nastavení může
            // vynutit světlý nebo tmavý, a galerie v opačném by vedle konverzace svítila.
            .setThemeNightMode(if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
            .apply {
                // Akcent bere picker jen v rozumném rozsahu jasu a plně krycí; mimo něj by
                // volání selhalo, takže se pak nechá na systémových barvách.
                val luminance = accent.luminance()
                if (luminance in MIN_ACCENT_LUMINANCE..MAX_ACCENT_LUMINANCE) {
                    setAccentColor(accent.toArgb().toLong() and 0xFFFFFFL)
                }
            }
            .build()
    }

    EmbeddedPhotoPicker(state = state, modifier = modifier, embeddedPhotoPickerFeatureInfo = info)
}

/** Vestavěný výběr je v SDK Extensions 15 nad Androidem 14. */
private const val REQUIRED_EXTENSION = 15

/** Meze akcentní barvy podle dokumentace `setAccentColor`. */
private const val MIN_ACCENT_LUMINANCE = 0.05f
private const val MAX_ACCENT_LUMINANCE = 0.9f
