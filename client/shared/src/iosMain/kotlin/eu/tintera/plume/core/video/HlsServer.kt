package eu.tintera.plume.core.video

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.plus
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSLock
import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.darwin.DISPATCH_QUEUE_PRIORITY_HIGH
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.posix.AF_INET
import platform.posix.EAGAIN
import platform.posix.EINTR
import platform.posix.MSG_DONTWAIT
import platform.posix.MSG_PEEK
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_NOSIGPIPE
import platform.posix.SO_RCVTIMEO
import platform.posix.SO_REUSEADDR
import platform.posix.accept
import platform.posix.bind
import platform.posix.close
import platform.posix.errno
import platform.posix.getsockname
import platform.posix.listen
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar
import platform.posix.timeval

/**
 * Místní HTTP server (127.0.0.1) pro HLS: přepočítané kvality videa streamuje AVPlayer jen takhle.
 *
 * Přepočítané kvality jsou fragmentované MP4 a přes vlastní schéma ([StreamingResourceLoader])
 * je AVPlayer neumí — délku skládá z přečtených fragmentů a skok za ně skončí koncem videa. Server
 * k nim ale posílá HLS seznam s rozsahy bajtů do téhož souboru (`alternativeVideo.hlsFile`), a ten
 * dá AVPlayeru délku i mapu segmentů hned. **Segmenty HLS přes vlastní schéma AVPlayer nebere**
 * (`AVAssetResourceLoader` obslouží jen seznam), proto skutečné HTTP. Totéž dělá oficiální klient
 * pro iOS (`SharedHLSServer`), jen přehrává přes hls.js ve WebView.
 *
 * - **Každé video má vlastní cestu s náhodným tokenem** (`/<token>/index.m3u8`, `/<token>/video.mp4`)
 *   — jiná aplikace v zařízení by se na místní port připojit mohla, cizí video nedostane bez tokenu.
 * - **Data čte [StreamingReader]**: stejné čekání a přesouvání stahování TDLib jako u progresivního
 *   streamu. Opuštěný požadavek (AVPlayer zavřel spojení) čekat přestane, jinak by stahování táhl
 *   k sobě.
 * - **Holé sokety POSIX**, každé spojení na vlastním vlákně GCD (čekání na data blokuje); spojení
 *   bez požadavku se po [IDLE_TIMEOUT_S] zavře. `SO_NOSIGPIPE`: zápis do spojení, které AVPlayer
 *   zavřel, by jinak shodil celou aplikaci signálem.
 * - **Uspaná aplikace o naslouchající soket přijde** (iOS ho „defunctne"). Server se pak založí
 *   znovu při dalším požadavku ([Registration.url]), napřed na tomtéž portu.
 */
@OptIn(ExperimentalForeignApi::class)
internal object HlsServer {

    private val lock = NSLock()
    private val streams = mutableMapOf<String, Stream>()
    private var listener = -1
    private var port = 0

    private class Stream(val reader: StreamingReader, val playlist: ByteArray)

    /** Zaregistrované video; [url] je adresa seznamu pro AVPlayer, [close] ho odhlásí. */
    class Registration internal constructor(private val token: String) {
        /** Adresa seznamu; server se při tom rozběhne, pokud neběží. `null`, když se nerozběhl. */
        fun url(): NSURL? {
            val port = ensureListening() ?: return null
            return NSURL(string = "http://127.0.0.1:$port/$token/$PLAYLIST")
        }

        fun close() {
            locked { streams.remove(token) }
        }
    }

    /** Zaregistruje rozestavěný soubor s jeho seznamem; `null`, když seznam nejde použít. */
    fun register(reader: StreamingReader, playlist: String): Registration? {
        val rewritten = rewriteHlsPlaylist(playlist, VIDEO) ?: run {
            logger.w { "Seznam HLS nejde použít: ${playlist.take(200)}" }
            return null
        }
        val token = NSUUID().UUIDString
        locked { streams[token] = Stream(reader, rewritten.encodeToByteArray()) }
        return Registration(token)
    }

    private inline fun <T> locked(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    private fun ensureListening(): Int? = locked {
        if (listener >= 0) return@locked port
        // Napřed tentýž port: přehrávač s adresou ze staršího serveru se pak chytí i po obnově.
        val (socket, opened) = openListener(port) ?: openListener(0) ?: return@locked null
        listener = socket
        port = opened
        NSThread(block = { acceptLoop(socket) }).start()
        logger.i { "Server HLS naslouchá na portu $port." }
        port
    }

    /** Naslouchající soket na 127.0.0.1 ([wanted] = 0: port vybere systém); (soket, port). */
    private fun openListener(wanted: Int): Pair<Int, Int>? = memScoped {
        val fd = socket(AF_INET, SOCK_STREAM, 0)
        if (fd < 0) return@memScoped null
        val one = alloc<IntVar>().apply { value = 1 }
        setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, one.ptr, sizeOf<IntVar>().convert())
        val address = alloc<sockaddr_in>().apply {
            sin_len = sizeOf<sockaddr_in>().convert()
            sin_family = AF_INET.convert()
            sin_port = swap16(wanted).convert()
            sin_addr.s_addr = LOOPBACK_NETWORK_ORDER
        }
        val length = alloc<socklen_tVar>().apply { value = sizeOf<sockaddr_in>().convert() }
        if (bind(fd, address.ptr.reinterpret(), length.value) != 0 || listen(fd, BACKLOG) != 0 ||
            getsockname(fd, address.ptr.reinterpret(), length.ptr) != 0
        ) {
            close(fd)
            return@memScoped null
        }
        fd to swap16(address.sin_port.toInt())
    }

    private fun acceptLoop(fd: Int) {
        while (true) {
            val connection = accept(fd, null, null)
            if (connection < 0) {
                if (errno == EINTR) continue
                // Soket je pryč (aplikace byla uspaná). Další požadavek založí nový.
                logger.w { "Server HLS přestal naslouchat (errno $errno)." }
                locked { if (listener == fd) listener = -1 }
                close(fd)
                return
            }
            configure(connection)
            dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_HIGH.toLong(), 0u)) {
                serveConnection(connection)
            }
        }
    }

    private fun configure(fd: Int) = memScoped {
        val one = alloc<IntVar>().apply { value = 1 }
        setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, one.ptr, sizeOf<IntVar>().convert())
        val idle = alloc<timeval>().apply {
            tv_sec = IDLE_TIMEOUT_S.convert()
            tv_usec = 0
        }
        setsockopt(fd, SOL_SOCKET, SO_RCVTIMEO, idle.ptr, sizeOf<timeval>().convert())
    }

    /** Požadavky jednoho spojení po sobě (AVPlayer spojení drží), dokud ho někdo nezavře. */
    private fun serveConnection(fd: Int) {
        try {
            val input = RequestReader(fd)
            while (true) {
                val head = input.nextHead() ?: break
                if (!respond(fd, head)) break
            }
        } catch (failure: Throwable) {
            // Výjimka z Kotlinu na vlákně GCD by shodila aplikaci.
            logger.w(failure) { "Obsluha HLS selhala." }
        } finally {
            close(fd)
        }
    }

    /** Odpoví na jeden požadavek; `false` = spojení zavřít. */
    private fun respond(fd: Int, head: String): Boolean {
        val lines = head.split("\r\n")
        val parts = lines.first().split(' ')
        val method = parts.getOrNull(0)
        val path = parts.getOrNull(1)?.trimStart('/')?.split('/')
        if ((method != "GET" && method != "HEAD") || path == null || path.size != 2) {
            return sendHead(fd, "400 Bad Request", emptyMap()) && false
        }
        val stream = locked { streams[path[0]] } ?: return sendHead(fd, "404 Not Found", mapOf("Content-Length" to "0"))
        val headOnly = method == "HEAD"
        return when (path[1]) {
            PLAYLIST -> sendHead(
                fd,
                "200 OK",
                mapOf("Content-Type" to "application/vnd.apple.mpegurl", "Content-Length" to "${stream.playlist.size}"),
            ) && (headOnly || sendBytes(fd, stream.playlist))
            VIDEO -> respondVideo(fd, stream.reader, lines.drop(1), headOnly)
            else -> sendHead(fd, "404 Not Found", mapOf("Content-Length" to "0"))
        }
    }

    private fun respondVideo(fd: Int, reader: StreamingReader, headers: List<String>, headOnly: Boolean): Boolean {
        val total = reader.source.totalSize
        val rangeHeader = headers.firstNotNullOfOrNull { line ->
            line.substringAfter(':', "").takeIf { line.substringBefore(':').trim().equals("Range", ignoreCase = true) }
        }
        val range = rangeHeader?.let { parseByteRange(it, total) }
        if (rangeHeader != null && range == null) {
            return sendHead(fd, "416 Range Not Satisfiable", mapOf("Content-Range" to "bytes */$total", "Content-Length" to "0"))
        }
        val first = range?.first ?: 0L
        val end = (range?.last ?: (total - 1)) + 1
        val common = mapOf(
            "Content-Type" to "video/mp4",
            "Accept-Ranges" to "bytes",
            "Content-Length" to "${end - first}",
        )
        val status = if (range != null) "206 Partial Content" else "200 OK"
        val headers = if (range != null) common + ("Content-Range" to "bytes $first-${end - 1}/$total") else common
        if (headOnly) return sendHead(fd, status, headers)
        // Hlavička až s prvními daty: když nedorazí, dostane AVPlayer chybu, ne useknutou odpověď.
        var offset = first
        var headSent = false
        while (offset < end) {
            val available = reader.awaitRange(offset, end) { isClosed(fd) } ?: run {
                if (!headSent) sendHead(fd, "504 Gateway Timeout", mapOf("Content-Length" to "0"))
                return false
            }
            val bytes = reader.read(offset, minOf(available, STREAM_CHUNK_BYTES.toLong(), end - offset)) ?: return false
            val length = bytes.length.toLong()
            if (length == 0L) return false
            if (!headSent) {
                if (!sendHead(fd, status, headers)) return false
                headSent = true
            }
            val pointer = bytes.bytes?.reinterpret<ByteVar>() ?: return false
            if (!sendAll(fd, pointer, length)) return false
            offset += length
        }
        return true
    }

    private fun sendHead(fd: Int, status: String, headers: Map<String, String>): Boolean {
        val text = buildString {
            append("HTTP/1.1 ").append(status).append("\r\n")
            headers.forEach { (name, value) -> append(name).append(": ").append(value).append("\r\n") }
            append("Cache-Control: no-store\r\n\r\n")
        }
        return sendBytes(fd, text.encodeToByteArray())
    }

    private fun sendBytes(fd: Int, bytes: ByteArray): Boolean =
        bytes.isEmpty() || bytes.usePinned { sendAll(fd, it.addressOf(0), bytes.size.toLong()) }

    private fun sendAll(fd: Int, data: kotlinx.cinterop.CPointer<ByteVar>, length: Long): Boolean {
        var sent = 0L
        while (sent < length) {
            val written = send(fd, data + sent, (length - sent).convert(), 0)
            if (written < 0 && errno == EINTR) continue
            if (written <= 0) return false
            sent += written
        }
        return true
    }

    /** Protějšek spojení zavřel (AVPlayer požadavek nepotřebuje). Nečte — jen nahlédne. */
    private fun isClosed(fd: Int): Boolean = memScoped {
        val byte = alloc<ByteVar>()
        val result = recv(fd, byte.ptr, 1.convert(), MSG_PEEK or MSG_DONTWAIT)
        result == 0L || (result < 0 && errno != EAGAIN && errno != EINTR)
    }

    /** Čte hlavičky požadavků ze spojení; co přijde za koncem hlavičky, nechá dalšímu. */
    private class RequestReader(private val fd: Int) {
        private var pending = ByteArray(0)

        fun nextHead(): String? {
            val buffer = ByteArray(READ_BUFFER)
            while (true) {
                val end = headEnd(pending)
                if (end >= 0) {
                    val head = pending.decodeToString(0, end)
                    pending = pending.copyOfRange(end + 4, pending.size)
                    return head
                }
                if (pending.size > MAX_HEAD) return null
                val read = buffer.usePinned { recv(fd, it.addressOf(0), buffer.size.convert(), 0) }
                if (read < 0 && errno == EINTR) continue
                if (read <= 0) return null
                pending += buffer.copyOf(read.toInt())
            }
        }

        private fun headEnd(bytes: ByteArray): Int {
            for (i in 0..bytes.size - 4) {
                if (bytes[i] == CR && bytes[i + 1] == LF && bytes[i + 2] == CR && bytes[i + 3] == LF) return i
            }
            return -1
        }
    }
}

/** Bajty 16bitového čísla prohozené: síťové pořadí ↔ pořadí ARM (little-endian). */
private fun swap16(value: Int): Int = ((value and 0xFF) shl 8) or ((value shr 8) and 0xFF)

/** 127.0.0.1 v síťovém pořadí bajtů, jak ho čte little-endian procesor. */
private const val LOOPBACK_NETWORK_ORDER = 0x0100007Fu

private const val PLAYLIST = "index.m3u8"
private const val VIDEO = "video.mp4"
private const val BACKLOG = 16
private const val READ_BUFFER = 4096
private const val MAX_HEAD = 64 * 1024
private const val IDLE_TIMEOUT_S = 60
private const val CR = '\r'.code.toByte()
private const val LF = '\n'.code.toByte()

private val logger = Logger.withTag("HlsServer")
