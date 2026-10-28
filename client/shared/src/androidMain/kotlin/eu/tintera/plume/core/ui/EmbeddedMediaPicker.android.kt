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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
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
import java.io.File
import kotlinx.coroutines.CoroutineScope
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
 *
 * [scope] patří obrazovce, ne panelu: v něm běží příprava, kterou zavření galerie nesmí zrušit
 * (viz [EmbeddedMediaSelection]). [pending] je, co se právě kopíruje — odznačení takové položky
 * ji jen vyškrtne a hotová kopie se pak zahodí, místo aby naskočila nad pole.
 */
private class PickedMedia(val scope: CoroutineScope) : EmbeddedMediaSelection {
    val paths = mutableMapOf<Uri, String>()
    val pending = mutableSetOf<Uri>()
    var preparingCount by mutableIntStateOf(0)
    override val preparing: Boolean get() = preparingCount > 0
}

@Composable
actual fun rememberEmbeddedMediaSelection(): EmbeddedMediaSelection {
    val scope = rememberCoroutineScope()
    return remember(scope) { PickedMedia(scope) }
}

@OptIn(ExperimentalPhotoPickerComposeApi::class)
@Composable
actual fun EmbeddedMediaPicker(
    selection: EmbeddedMediaSelection,
    pickedPaths: List<String>,
    sources: Map<String, String>,
    allowPhotos: Boolean,
    allowVideos: Boolean,
    expanded: Boolean,
    single: Boolean,
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
    val picked by rememberUpdatedState(onPicked)
    val unpicked by rememberUpdatedState(onUnpicked)
    val failed by rememberUpdatedState(onError)
    val done by rememberUpdatedState(onDone)

    // Paměť výběru i rozsah přípravy; drží je obrazovka, takže přežijí zavření panelu.
    val media = selection as? PickedMedia ?: run {
        val fallback = rememberCoroutineScope()
        remember(fallback) { PickedMedia(fallback) }
    }
    val prepared = media.paths

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
        /*
         * Sdílená fotka je v galerii taky. Zapíše se do `prepared` pod identifikátorem pickeru:
         * když ji uživatel v mřížce ťukne, oprávnění se přeskočí (v dávce už je) a odznačení ji
         * z dávky odebere přes cestu.
         *
         * Do předvýběru (`initialMediaSelection`) jde taky, ale MediaProvider ji ODMÍTNE —
         * „package does not have permission for the uri": předvybrat jde jen fotku, ke které
         * aplikace dostala oprávnění přes picker, a sdílená přišla s oprávněním k URI cizí
         * aplikace. Zaškrtnutá se tedy neukáže; zaškrtne se, až ji uživatel ťukne. Kdyby sdílející
         * aplikace poslala rovnou URI pickeru i s oprávněním, předvýběr projde.
         */
        for ((path, source) in sources) {
            if (path !in live) continue
            val pickerUri = pickerUriFor(Uri.parse(source)) ?: continue
            if (pickerUri !in prepared) prepared[pickerUri] = path
        }
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
        onUriPermissionGranted = { granted ->
            /*
             * Obnovené zaškrtnutí (`initialMediaSelection`) picker při startu session ohlásí
             * ZNOVU jako udělené oprávnění. Ty fotky už nad polem jsou, takže by se přidaly
             * podruhé — v dávce pak byla tatáž fotka dvakrát (ověřeno na emulátoru, „3 z 3"
             * po jediném výběru). Co už připravené je, se proto přeskočí.
             */
            val uris = granted.filterNot { it in prepared || it in media.pending }
            if (uris.isNotEmpty()) {
                media.pending += uris
                media.preparingCount += uris.size
                /*
                 * V rozsahu OBRAZOVKY, ne panelu: kopie velkého videa trvá vteřiny a „Hotovo"
                 * panel zavře dřív — v rozsahu panelu se příprava zrušila a video nad polem
                 * nikdy nenaskočilo (ověřeno na emulátoru se 400MB videem).
                 */
                media.scope.launch {
                    try {
                        // Kopie a zmenšení jsou práce na disku a procesoru — mimo hlavní vlákno,
                        // souběžně; `awaitAll` drží pořadí výběru.
                        val results = uris
                            .map { uri -> async(AttachmentPrepareDispatcher) { runCatching { prepareVisual(context, uri) } } }
                            .awaitAll()
                        val ready = mutableListOf<OutgoingAttachment>()
                        var failures = 0
                        results.forEachIndexed { index, result ->
                            val uri = uris[index]
                            val stillPicked = media.pending.remove(uri)
                            result.onSuccess { attachment ->
                                if (stillPicked) {
                                    prepared[uri] = attachment.path
                                    ready += attachment
                                } else {
                                    // Odznačeno během kopírování — nad pole nepatří.
                                    discardPrepared(attachment)
                                }
                            }
                            result.onFailure {
                                if (stillPicked) failures++
                                Logger.withTag("Attachments").w(it) { "Vybranou přílohu se nepodařilo připravit." }
                            }
                        }
                        if (ready.isNotEmpty()) picked(ready)
                        if (failures > 0) failed(UiText(Res.string.attachment_prepare_failed))
                    } finally {
                        media.pending -= uris.toSet()
                        media.preparingCount -= uris.size
                    }
                }
            }
        },
        onUriPermissionRevoked = { uris ->
            // Co se ještě kopíruje, se jen vyškrtne; hotová kopie se pak zahodí.
            media.pending -= uris.toSet()
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
    val info = remember(accent, dark, allowPhotos, allowVideos, single) {
        EmbeddedPhotoPickerFeatureInfo.Builder()
            // Výměna přílohy bere jednu položku; picker pak druhou zaškrtnout nedovolí.
            .setMaxSelectionLimit(if (single) 1 else systemPickLimit())
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
     * Sbalený picker je jen náhled: bez záložek a se zakázaným scrollováním
     * (`embeddedCollapsedModeScrollingEnabled=false` v logu pickeru; veřejné API ho zapnout
     * neumí). Sheet v konverzaci ho proto chce vždycky rozbalený — dokud nebyl, zůstala v něm
     * mřížka, se kterou nešlo pohnout (hlášeno z Pixelu 11 Pro Foldu, zreprodukováno na emulátoru).
     *
     * Před startem session se hodnota promítne do `setPickerLaunchedInExpandedState`, potom jde
     * přes `notifyPhotoPickerExpanded`. Picker přitom zůstává vestavěný (`mCurrentFocus` zůstává
     * na naší aktivitě, ověřeno na emulátoru s API 37.2 rozloženém i složeném).
     */
    LaunchedEffect(state, expanded) { state.isExpanded = expanded }

    EmbeddedPhotoPicker(state = state, modifier = modifier, embeddedPhotoPickerFeatureInfo = info)
}

/** Kopie přílohy, která už nikam nepůjde: celý její adresář v cache (i náhled videa). */
private fun discardPrepared(attachment: OutgoingAttachment) {
    File(attachment.path).parentFile?.deleteRecursively()
}

/**
 * Identifikátor, pod kterým fotku zná výběr fotek, z URI, pod kterým ji sdílela jiná aplikace.
 *
 * Picker drží místní fotky jako `content://media/picker/<uživatel>/<poskytovatel>/media/<id>`,
 * kde `<id>` je id v MediaStore (ověřeno logem pickeru: `mediaId=17` ↔ `…/images/media/17`).
 * Sdílené URI bývá:
 * - **už picker URI** (aplikace si fotku vybrala systémovým výběrem) — bere se, jak je,
 * - **MediaStore** (`content://media/external/images/media/17`, Soubory, správci souborů),
 * - **zabalené MediaStore URI** v cestě cizího poskytovatele — tak sdílí Fotky Google
 *   (`…photos.contentprovider/-1/1/content%3A%2F%2Fmedia%2Fexternal%2F…/ORIGINAL/…`).
 *
 * Co z toho nejde vyčíst (soubor z cloudu, jiná aplikace), vrací `null` — galerie ho neoznačí,
 * v dávce zůstane dál. Fotky z cloudu Google Fotek MediaStore id nemají, a tak se neoznačí.
 */
internal fun pickerUriFor(source: Uri): Uri? {
    if (source.scheme != "content") return null
    val segments = source.pathSegments
    if (source.authority == MEDIA_AUTHORITY) {
        if (segments.firstOrNull() == "picker") return source
        // external / external_primary / svazek, pak images|video, media, id
        val mediaAt = segments.indexOf("media")
        val id = segments.getOrNull(mediaAt + 1)?.toLongOrNull()
        if (mediaAt >= 2 && id != null && segments[mediaAt - 1] in MEDIA_KINDS) {
            val user = android.os.Process.myUid() / PER_USER_RANGE
            return Uri.parse("content://$MEDIA_AUTHORITY/picker/$user/$LOCAL_PICKER_PROVIDER/media/$id")
        }
        return null
    }
    // Cizí poskytovatel s MediaStore URI zabaleným v cestě (Fotky Google).
    return segments.firstNotNullOfOrNull { segment ->
        segment.takeIf { it.startsWith("content://$MEDIA_AUTHORITY/") }?.let { pickerUriFor(Uri.parse(it)) }
    }
}

private const val MEDIA_AUTHORITY = "media"
private val MEDIA_KINDS = setOf("images", "video")
/** Poskytovatel místních fotek ve výběru fotek (AOSP `PickerUriResolver`). */
private const val LOCAL_PICKER_PROVIDER = "com.android.providers.media.photopicker"
/** `UserHandle.PER_USER_RANGE` — uid je uživatel × 100 000 + id aplikace; veřejně to API nemá. */
private const val PER_USER_RANGE = 100_000

/** Vestavěný výběr je v SDK Extensions 15 nad Androidem 14. */
private const val REQUIRED_EXTENSION = 15

/** Meze akcentní barvy podle dokumentace `setAccentColor`. */
private const val MIN_ACCENT_LUMINANCE = 0.05f
private const val MAX_ACCENT_LUMINANCE = 0.9f
