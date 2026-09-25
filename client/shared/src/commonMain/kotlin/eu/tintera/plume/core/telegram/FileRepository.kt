package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow
import eu.tintera.plume.core.ui.UiText

/**
 * Stahování souborů (fotky, videa, avatary) z TDLib.
 *
 * TDLib nedává URL — drží soubory ve **vlastním adresáři na disku** a identifikuje je celočíselným
 * `fileId`. Cesta k souboru je známá až po stažení, průběh chodí updaty. Tohle rozhraní ten model
 * překládá do jednoho reaktivního stavu, aby UI nemuselo řešit ani stahování, ani cesty.
 */
interface FileRepository {

    /**
     * Živý stav souboru. Odběr **sám spustí stažení**, pokud soubor ještě není na disku —
     * UI tedy jen vykreslí to, co dostane, a nic neinicializuje.
     */
    fun file(fileId: Int): Flow<FileState>

    /**
     * Průběh nahrávání souboru 0f–1f, nebo `null`, když se nenahrává. Na rozdíl od [file] nic
     * nespouští — nahrávání začne samo odesláním zprávy.
     */
    fun uploadProgress(fileId: Int): Flow<Float?>

    /**
     * Vyžádá stažení znovu, i když se o soubor už jednou žádalo.
     *
     * [file] si pamatuje, na co už request odešel, aby stahování nespouštěl každý odběr při
     * scrollu. Kdyby ale stahování skončilo v půli (spadlé spojení, TDLib soubor uvolnil),
     * zůstal by stav viset a další ťuknutí by nic neudělalo — tohle je ta cesta ven.
     */
    suspend fun retryDownload(fileId: Int)

    /**
     * Přesune stahování na [offset] — pro streamování, když si přehrávač řekne o data, která
     * ještě nedorazila.
     *
     * TDLib pak plní soubor od téhle pozice a `downloadedPrefixSize` se počítá **od ní**, ne od
     * začátku souboru: dostupný úsek je `downloadOffset ..< downloadOffset + downloadedPrefixSize`.
     * Typicky se tím skáče na konec pro `moov` atom (index bývá u videí na konci) a zpátky.
     */
    suspend fun downloadFrom(fileId: Int, offset: Long)

    /**
     * Statická mapa kolem polohy jako soubor — obrázek PNG, který dál jde přes [file] jako každý
     * jiný. Vrací `fileId`, nebo `null`, když ji TDLib vydat odmítne.
     *
     * Mapu kreslí Telegram (`getMapThumbnailFile`), takže aplikace nepotřebuje klíč k žádnému
     * mapovému API a nic se neposílá cizí službě. [map] je v bodech, ne v pixelech (viz [MapRequest]).
     */
    suspend fun mapThumbnail(map: MapRequest): Int?
}

/**
 * Výřez mapy pro [FileRepository.mapThumbnail]. Meze jsou od TDLib: [zoom] 13–20, [width]
 * a [height] 16–1024 bodů „před měřítkem", [scale] 1–3 (hustota displeje) a [chatId] chat,
 * ve kterém se mapa ukáže (0 = neznámý). Kdo meze nedodrží, dostane chybu, ne oříznutí —
 * proto je hlídá [StaticMap][eu.tintera.plume.core.ui.StaticMap], ne volající.
 */
data class MapRequest(
    val latitude: Double,
    val longitude: Double,
    val zoom: Int,
    val width: Int,
    val height: Int,
    val scale: Int,
    val chatId: Long = 0,
)

sealed interface FileState {

    /** Ještě se nezačalo stahovat, nebo čekáme na první update. */
    data object Pending : FileState

    /**
     * [progress] je 0f–1f; u souborů s neznámou velikostí zůstane na 0f.
     *
     * [path] je rozestavěný soubor — cestu TDLib vyplňuje od začátku stahování, takže se z něj
     * dá číst dřív, než je hotový. Čte se **jen po [downloadedPrefixSize]**: to je souvisle
     * stažený úsek od začátku, za ním jsou v souboru díry. Na tom stojí streamování videa.
     */
    data class Downloading(
        val progress: Float,
        val path: String? = null,
        val downloadOffset: Long = 0,
        val downloadedPrefixSize: Long = 0,
        val totalSize: Long = 0,
    ) : FileState {
        /** Úsek souboru, který je na disku souvisle k dispozici. */
        val downloaded: LongRange
            get() = downloadOffset until (downloadOffset + downloadedPrefixSize)
    }

    /** [path] je absolutní cesta do adresáře TDLib. */
    data class Ready(val path: String) : FileState

    data class Failed(val message: UiText) : FileState
}
