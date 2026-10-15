package eu.tintera.plume.core.video

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import platform.AVFoundation.AVAssetResourceLoader
import platform.AVFoundation.AVAssetResourceLoaderDelegateProtocol
import platform.AVFoundation.AVAssetResourceLoadingRequest
import platform.Foundation.NSError
import platform.Foundation.NSLocalizedDescriptionKey
import platform.Foundation.NSURL
import platform.darwin.NSObject
import platform.darwin.DISPATCH_QUEUE_PRIORITY_HIGH
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_queue_create

/**
 * Rozestavěný soubor TDLib jako zdroj dat pro `AVPlayer` — iOS obdoba `PartialFileDataSource`
 * z Androidu, nad týmž [StreamingSource].
 *
 * `AVPlayer` soubor, který roste, přehrát neumí: `file://` URL čte jako hotový soubor a na
 * konci stažené části skončí. Proto dostane URL s vlastním schématem ([streamingUrl]), které neumí
 * otevřít sám, a o data si říká delegátovi po rozsazích bajtů (`AVAssetResourceLoadingRequest`).
 *
 * - **Na data se ČEKÁ, ne končí**: rozsah mimo stažený úsek přesune stahování na sebe
 *   ([StreamingSource.requestFrom], viz [StreamingReader.awaitRange]) a čeká, až dorazí. Index (`moov`) bývá na
 *   konci souboru a přehrávač o něj žádá hned na začátku — bez přesunu by se na něj čekalo,
 *   než se stáhne všechno před ním.
 * - **Každý požadavek se obslouží na vlastním vlákně** (globální fronta). Čekání blokuje, a na
 *   frontě delegáta by zablokovalo i rušení ostatních požadavků — AVPlayer jich má najednou víc
 *   (začátek souboru a index na konci) a zbytečné ruší.
 * - **Data jdou po kusech** ([STREAM_CHUNK_BYTES]): požadavek bývá „až do konce souboru" a AVPlayer
 *   ho zruší, jakmile má dost. Mezi kusy se kontroluje zrušení.
 * - **Cesta se čte při každém kusu**: dostažený soubor TDLib přesune z `temp/` na konečnou
 *   cestu (viz [StreamingSource]).
 * - **`AVAssetResourceLoader` si delegáta drží SLABĚ** — silnou referenci drží [VideoPlayer],
 *   jinak by delegát zmizel uprostřed přehrávání a video by se zastavilo bez chyby.
 * - **Jen progresivní MP4** (původní soubory). Fragmentované (přepočítané kvality) AVPlayer přes
 *   vlastní zdroj neumí: délku zná jen z přečtených fragmentů a skok za ně skončí koncem videa —
 *   ty jdou přes HLS a místní HTTP ([HlsServer]).
 */
@OptIn(ExperimentalForeignApi::class)
internal class StreamingResourceLoader(private val reader: StreamingReader) : NSObject(), AVAssetResourceLoaderDelegateProtocol {

    private val source = reader.source

    /** Fronta, na které AVPlayer volá delegáta; obsluha samotná běží jinde. */
    val queue = dispatch_queue_create("eu.tintera.plume.video-stream", null)

    @ObjCSignatureOverride
    override fun resourceLoader(
        resourceLoader: AVAssetResourceLoader,
        shouldWaitForLoadingOfRequestedResource: AVAssetResourceLoadingRequest,
    ): Boolean {
        val request = shouldWaitForLoadingOfRequestedResource
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_HIGH.toLong(), 0u)) { serve(request) }
        return true
    }

    private fun serve(request: AVAssetResourceLoadingRequest) {
        try {
            request.contentInformationRequest?.let { info ->
                // Druh souboru z hlavičky: Telegram posílá MP4, ale z iPhonu přijde i QuickTime.
                info.contentType = if (awaitRange(0L, BRAND_END) == null) MPEG4_TYPE else contentType()
                info.contentLength = source.totalSize
                info.byteRangeAccessSupported = true
            }
            val data = request.dataRequest
            if (data == null) {
                request.finishLoading()
                return
            }
            var offset = data.currentOffset
            val end = if (data.requestsAllDataToEndOfResource) {
                source.totalSize
            } else {
                (data.requestedOffset + data.requestedLength).coerceAtMost(source.totalSize)
            }
            while (offset < end) {
                if (request.isCancelled() || reader.released) return
                val available = awaitRange(offset, end) ?: run {
                    request.finishLoadingWithError(timeoutError(offset))
                    return
                }
                val length = minOf(available, STREAM_CHUNK_BYTES.toLong(), end - offset)
                val bytes = read(offset, length) ?: run {
                    request.finishLoadingWithError(timeoutError(offset))
                    return
                }
                data.respondWithData(bytes)
                offset += bytes.length.toLong()
                if (bytes.length.toLong() == 0L) break
            }
            if (!request.isCancelled()) request.finishLoading()
        } catch (failure: Throwable) {
            // Výjimka z Kotlinu na vlákně GCD by shodila aplikaci; přehrávač dostane chybu.
            logger.w(failure) { "Čtení streamu selhalo." }
            if (!request.isFinished()) request.finishLoadingWithError(timeoutError(-1))
        }
    }

    private fun awaitRange(offset: Long, end: Long) = reader.awaitRange(offset, end)

    private fun read(offset: Long, length: Long) = reader.read(offset, length)

    /** `ftyp` s hlavní značkou `qt  ` = QuickTime, jinak MP4. */
    private fun contentType(): String {
        val header = read(0L, BRAND_END) ?: return MPEG4_TYPE
        if (header.length.toLong() < BRAND_END) return MPEG4_TYPE
        val bytes = header.bytes?.reinterpret<ByteVar>() ?: return MPEG4_TYPE
        val brand = CharArray(4) { bytes[8 + it].toInt().toChar() }.concatToString()
        return if (brand == "qt  ") QUICKTIME_TYPE else MPEG4_TYPE
    }

    private fun timeoutError(offset: Long) = NSError.errorWithDomain(
        domain = "eu.tintera.plume.video-stream",
        code = 1,
        userInfo = mapOf<Any?, Any?>(NSLocalizedDescriptionKey to "Stream data did not arrive in time (offset $offset)."),
    )
}

/**
 * Adresa pro [StreamingResourceLoader]: schéma, které AVPlayer neumí otevřít sám, takže se ptá
 * delegáta. Přípona kvůli tomu, aby AVFoundation odhadla kontejner, než dorazí `contentType`.
 * (Companion podtřídy ObjC typu pole mít nesmí — proto top-level.)
 */
internal val streamingUrl: NSURL = NSURL(string = "plume-stream://tdlib/video.mp4")

private val logger = Logger.withTag("VideoStream")

/** Hlavička `ftyp` po hlavní značku včetně: velikost (4), `ftyp` (4), značka (4). */
private const val BRAND_END = 12L

private const val MPEG4_TYPE = "public.mpeg-4"
private const val QUICKTIME_TYPE = "com.apple.quicktime-movie"

