package eu.tintera.plume.core.video

// V `core`, ne ve `shared`: přehrávání za běhu stahování potřebuje i televize
// (`:client:tvApp`), která `shared` nemá. Přehrávače platforem zůstávají ve `shared`.

/**
 * Rozestavěný soubor pro streamování.
 *
 * [path] je soubor TDLib, který na disku roste. Číst se z něj smí **jen uvnitř [downloaded]** —
 * mimo ten úsek jsou díry: TDLib stahuje od `downloadOffset` a souvisle má jen
 * `downloadedPrefixSize` bajtů.
 *
 * **[path] je funkce, ne hodnota: hotový soubor TDLib PŘESUNE** z `files/tdlib-files/temp/`
 * na konečnou cestu. Přehrávač soubor otevírá znovu při každém skoku i při přehrání od začátku,
 * a se zapamatovanou dočasnou cestou by skončil na `FileNotFoundException` (ENOENT). Už otevřený
 * soubor přesun přežije, takže hrající video nic nepozná.
 *
 * [requestFrom] přesune stahování jinam. **Bez toho streamování nestačí:** přehrávač si hned na
 * začátku řekne o `moov` atom, a ten u většiny videí leží na KONCI souboru — příznak
 * `supportsStreaming` od serveru říká jen to, co tvrdí odesílatel, ne kde index doopravdy je.
 *
 * [hlsPlaylist] je HLS seznam přepočítané kvality (text souboru `alternativeVideo.hlsFile`), kde
 * ho platforma potřebuje (`videoFragmentedStreamingNeedsHls` ve `shared`); jinak `null`.
 *
 * Obě funkce se volají z vlákna přehrávače, ne z kompozice.
 */
class StreamingSource(
    val path: () -> String,
    val totalSize: Long,
    val downloaded: () -> LongRange,
    val requestFrom: (Long) -> Unit,
    val hlsPlaylist: String? = null,
)

/**
 * Míří stahování na [offset]? Tedy: stažený úsek [downloaded] ho obsahuje, nebo končí těsně
 * před ním — TDLib stahuje souvisle od začátku úseku, takže data dorazí sama a přesouvat
 * stahování ([StreamingSource.requestFrom]) není třeba.
 *
 * **Bez téhle podmínky se stahování „přesouvalo" desítkykrát za vteřinu.** Přehrávač čte
 * rychleji, než data chodí, takže čtení pořád dohání konec staženého úseku; Android tam pokaždé
 * zavolal `downloadFile` s offsetem, kam se stejně stahovalo (v logu řádky „Stahování souboru …
 * se přesouvá na …" po desítkách KB). Desktop a iOS podmínku měly odjakživa.
 */
fun downloadHeadsTo(downloaded: LongRange, offset: Long): Boolean =
    !downloaded.isEmpty() && downloaded.first <= offset && offset <= downloaded.last + 1

/**
 * Zkusit u původního souboru streamování z rozestavěného souboru?
 *
 * **Příznak `supportsStreaming` nestačí jako podmínka.** Říká jen, co tvrdí odesílatel (bot
 * s příznakem poslal video s `moov` na konci), a naopak ho nemá spousta MP4, které streamovat
 * jde — třeba video z foťáku, poslané bez přebalení. Přehrávače na všech platformách umí MP4
 * i s indexem na konci: o konec souboru si řeknou a [StreamingSource.requestFrom] tam přesune
 * stahování. MP4 a MOV se proto streamují i bez příznaku; když stream selže dřív, než se video
 * rozběhne, čeká `VideoPage` na celý soubor (viz „Video bez příznaku supportsStreaming"
 * v docs/poznatky/prohlizec-medii.md).
 *
 * Jiné kontejnery (WebM, MKV, AVI…) jen s příznakem: tam se rozestavěný soubor neověřoval.
 */
fun streamOriginalVideo(supportsStreaming: Boolean, mimeType: String): Boolean =
    supportsStreaming || mimeType.substringBefore(';').trim().lowercase() in MP4_FAMILY

/** MP4 a QuickTime (týž formát boxů), jak je TDLib hlásí v `Video.mimeType` / `Document.mimeType`. */
private val MP4_FAMILY = setOf("video/mp4", "video/quicktime", "video/x-m4v")
