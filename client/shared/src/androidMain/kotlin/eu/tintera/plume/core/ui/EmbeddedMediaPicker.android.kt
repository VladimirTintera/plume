package eu.tintera.plume.core.ui

import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.ext.SdkExtensions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

/**
 * Co galerie vydala a co se z toho připravilo.
 *
 * Klíč je identifikátor od systémového výběru — jen ten umí picker vzít zpátky
 * (`initialMediaSelection`); hodnota je cesta do cache, podle které přílohu zná konverzace.
 * Odznačením položka vypadne ([EmbeddedMediaPicker]), zbytek se při dalším otevření prosítá
 * podle toho, co je opravdu nad polem.
 */
private class PickedMedia : EmbeddedMediaSelection {
    val paths = mutableMapOf<Uri, String>()
}

@Composable
actual fun rememberEmbeddedMediaSelection(): EmbeddedMediaSelection = remember { PickedMedia() }

@OptIn(ExperimentalPhotoPickerComposeApi::class)
@Composable
actual fun EmbeddedMediaPicker(
    selection: EmbeddedMediaSelection,
    pickedPaths: List<String>,
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

    // Paměť výběru; drží ji obrazovka, takže přežije zavření panelu.
    val prepared = (selection as? PickedMedia)?.paths ?: remember { mutableMapOf() }

    /*
     * Zaškrtnuté při otevření = co je z minulého výběru POŘÁD nad polem.
     *
     * Průsečík s `pickedPaths` je celá synchronizace: přílohu odebranou křížkem nebo odeslanou
     * nemusí panel nijak sledovat, prostě v dalším otevření chybí. Bez toho by zůstala v mřížce
     * zaškrtnutá a odznačení by pak sahalo po příloze, která už není.
     *
     * Počítá se JEDNOU za otevření (`remember` bez klíče). Během vybírání `pickedPaths` roste
     * a přepočítávat podle něj by znamenalo měnit i klíč stavu níž — tedy restartovat session
     * uprostřed výběru.
     */
    val restored = remember {
        val live = pickedPaths.toSet()
        prepared.entries.retainAll { it.value in live }
        prepared.keys.toSet()
    }

    val state = rememberEmbeddedPhotoPickerState(
        /*
         * Klíč, na kterém stav resetuje. Bez něj se `initialMediaSelection` neuplatní: stav je
         * saveable a při dalším otevření se obnoví ten uložený i s výběrem, který už neplatí —
         * odebraná fotka pak zůstala v mřížce zaškrtnutá (ověřeno na emulátoru). Množina se mezi
         * otevřeními mění jen tehdy, když se změnily přílohy, takže víc resetů to nepřidá.
         */
        restored,
        initialMediaSelection = restored,
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

    /*
     * Picker MUSÍ být rozbalený, jinak v něm nejde scrollovat. Sbalený stav je jen náhled pro
     * sheet, který hostitel teprve táhne nahoru: bez záložek a se zakázaným scrollováním
     * (`embeddedCollapsedModeScrollingEnabled=false` v logu pickeru; veřejné API ho zapnout
     * neumí). Náš sheet je vždycky celý rozbalený, takže sbalený picker v něm byl mřížka,
     * se kterou nešlo pohnout (hlášeno z Pixelu 11 Pro Foldu, zreprodukováno na emulátoru).
     *
     * Nastavuje se před startem session, takže se promítne do `setPickerLaunchedInExpandedState`;
     * picker přitom zůstane vestavěný v sheetu (`mCurrentFocus` zůstává na naší aktivitě,
     * ověřeno na emulátoru s API 37.2 rozloženém i složeném).
     */
    LaunchedEffect(state) { state.isExpanded = true }

    EmbeddedPhotoPicker(state = state, modifier = modifier, embeddedPhotoPickerFeatureInfo = info)
}

/** Vestavěný výběr je v SDK Extensions 15 nad Androidem 14. */
private const val REQUIRED_EXTENSION = 15

/** Meze akcentní barvy podle dokumentace `setAccentColor`. */
private const val MIN_ACCENT_LUMINANCE = 0.05f
private const val MAX_ACCENT_LUMINANCE = 0.9f
