package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.tintera.plume.core.telegram.OutgoingAttachment

/**
 * Výběr fotek kreslený UVNITŘ aplikace, ne jako odchod do systémové obrazovky.
 *
 * Systémový výběr ([rememberAttachmentPicker]) je samostatná obrazovka: konverzace na dobu výběru
 * zmizí a vrátí se s hotovým výsledkem. Vestavěný výběr zůstane nad vstupním polem, takže je při
 * vybírání pořád vidět, do jakého chatu se posílá — a vybírat jde dál, aniž by se chat opustil.
 * Stejně to má Google Zprávy.
 *
 * **Soukromí zůstává systémové.** Galerii kreslí proces výběru fotek, ne my (na Androidu do
 * `SurfaceView`); aplikace dostane jen to, co uživatel vybral, a nepotřebuje k tomu žádné
 * oprávnění — přesně jako u systémového výběru.
 *
 * **Není všude** ([embeddedMediaPickerAvailable]): na Androidu ho má až 14 se SDK Extensions 15,
 * na iOS obdoba není. Kde chybí, zůstává systémový výběr — proto je to samostatná schopnost
 * navíc, ne náhrada [AttachmentPicker].
 */
@Composable
expect fun embeddedMediaPickerAvailable(): Boolean

/**
 * Vykreslí vestavěný výběr na místo, které dostane. Potřebuje **pevnou velikost** od volajícího:
 * na Androidu je to cizí okno (`SurfaceView`), které se s rodičem neposouvá a neumí prolínat.
 *
 * **Přílohy chodí PRŮBĚŽNĚ, jak uživatel vybírá** ([onPicked]), ne až na konci: v pruhu nad
 * vstupním polem je pak hned vidět, co odejde. Odznačení v galerii je proto [onUnpicked] —
 * dostane cesty příloh, které se mají zase odebrat. Přílohy jsou připravené k odeslání stejně
 * jako u [rememberAttachmentPicker] (zmenšená fotka, náhled z videa) a v pořadí výběru.
 *
 * [onDone] říká, že uživatel výběr uzavřel a panel se má zavřít.
 */
@Composable
expect fun EmbeddedMediaPicker(
    /** Co chat přijme; zakázaný druh galerie vůbec nenabídne. Aspoň jedno musí být `true`. */
    allowPhotos: Boolean,
    allowVideos: Boolean,
    onPicked: (List<OutgoingAttachment>) -> Unit,
    onUnpicked: (List<String>) -> Unit,
    onError: (UiText) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier,
)
