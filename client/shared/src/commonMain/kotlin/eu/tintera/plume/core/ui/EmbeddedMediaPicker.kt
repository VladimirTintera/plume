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
 * Co už uživatel v galerii vybral — paměť, která přežije zavření panelu.
 *
 * Bez ní začíná každé otevření s prázdným výběrem: fotky nad polem sice zůstanou, ale v mřížce
 * nejsou zaškrtnuté, takže se dají vybrat podruhé a v albu je pak dvakrát. Obsah je platformní
 * (na Androidu identifikátory od systémového výběru), proto je to jen značka bez členů.
 *
 * **Drží ji obrazovka, ne panel** ([rememberEmbeddedMediaSelection]) — panel se zavřením zahodí.
 * S odchodem z konverzace zaniká i ona, což je správně: přílohy odejdou s ní.
 */
interface EmbeddedMediaSelection

@Composable
expect fun rememberEmbeddedMediaSelection(): EmbeddedMediaSelection

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
    /** Paměť výběru z minulého otevření ([EmbeddedMediaSelection]). */
    selection: EmbeddedMediaSelection,
    /**
     * Cesty příloh, které právě čekají nad vstupním polem.
     *
     * Podle nich se obnoví zaškrtnutí v mřížce — a jen podle nich: co uživatel mezitím odebral
     * křížkem nebo odeslal, z výběru vypadne samo, aniž by to panel musel sledovat.
     */
    pickedPaths: List<String>,
    /**
     * Přílohy, které do dávky přišly odjinud (sdílení z jiné aplikace): cesta → původní URI.
     *
     * Sdílená fotka z Fotek je v mřížce taky. **Zaškrtnutá se neukáže** — systém předvybere jen
     * fotku, ke které aplikace dostala oprávnění přes výběr fotek, a sdílenou odmítne. Galerie ji
     * ale pozná: ťuknutí na ni ji nepřidá podruhé a odznačení ji z dávky odebere ([onUnpicked])
     * jako kteroukoli jinou. Co galerie nezná (soubor z cizí aplikace), zůstane jen v dávce.
     */
    sources: Map<String, String> = emptyMap(),
    /** Co chat přijme; zakázaný druh galerie vůbec nenabídne. Aspoň jedno musí být `true`. */
    allowPhotos: Boolean,
    allowVideos: Boolean,
    /**
     * Rozbalená galerie: záložky, celá mřížka a scrollování. **Sbalená je jen náhled** — bez
     * záložek a posouvat se v ní nedá (na Androidu to picker zakazuje sám a veřejné API to
     * změnit neumí). Sbalenou má smysl ukázat jen v panelu, který jde vytáhnout nahoru;
     * kde je galerie celá vidět (sheet v konverzaci), musí být rozbalená.
     */
    expanded: Boolean,
    onPicked: (List<OutgoingAttachment>) -> Unit,
    onUnpicked: (List<String>) -> Unit,
    onError: (UiText) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier,
)
