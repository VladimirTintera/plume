package eu.tintera.plume.core.video

/**
 * Video ze souboru WebM, rozebrané na snímky — pro videosamolepky (`.webm`, VP9).
 *
 * **Průhlednost není součást VP9.** WebM ji nese jako DRUHÝ, samostatně kódovaný VP9 stream:
 * stopa má `AlphaMode = 1` a každý snímek je `BlockGroup`, jehož `BlockAdditional` (s
 * `BlockAddID = 1`) je VP9 snímek, v jehož jasu je průhlednost. Dekóduje se tedy dvěma obyčejnými
 * VP9 dekodéry — [WebmFrame.data] a [WebmFrame.alpha] — a výsledky se složí. Neprůhledná samolepka
 * má jen `SimpleBlock` a [alpha] je `null`.
 */
class WebmVideo(
    val codec: WebmCodec,
    val width: Int,
    val height: Int,
    /** Délka jednoho snímku podle stopy (`DefaultDuration`); `0`, když ji soubor neuvádí. */
    val frameDurationMs: Long,
    /** Stopa nese průhlednost (`AlphaMode`). Snímek ji pak má v [WebmFrame.alpha]. */
    val hasAlpha: Boolean,
    val colour: WebmColour,
    val frames: List<WebmFrame>,
) {
    /** Délka celého videa: začátek posledního snímku a jeho délka. */
    val durationMs: Long
        get() = (frames.lastOrNull()?.timeMs ?: 0L) + frameDurationMs.coerceAtLeast(MIN_FRAME_MS)
}

enum class WebmCodec { Vp8, Vp9 }

/** Jak převést YUV na RGB — podle `Colour` ve stopě. */
data class WebmColour(
    /** BT.709 (`MatrixCoefficients = 1`); jinak BT.601, jak to bez údaje dělají přehrávače. */
    val bt709: Boolean = false,
    /** Plný rozsah 0–255 (`Range = 2`); jinak omezený 16–235 („broadcast"). */
    val fullRange: Boolean = false,
)

class WebmFrame(
    val timeMs: Long,
    val isKey: Boolean,
    val data: ByteArray,
    /** VP9 snímek s průhledností v jasu; `null` = snímek je neprůhledný. */
    val alpha: ByteArray?,
)

/**
 * Čtení WebM (Matroska / EBML) — jen to, co videosamolepka potřebuje: jedna video stopa, snímky
 * a jejich průhlednost. Soubor má nejvýš 256 kB, takže se čte celý z paměti.
 *
 * Systémový `MediaExtractor` na Androidu `BlockAdditional` neposkytuje a iOS WebM neumí vůbec,
 * proto vlastní čtení. Čte se jen první video stopa ve VP8/VP9. Snímky s lacingem (několik
 * snímků v jednom bloku) se přeskočí — u videa je enkodéry nepoužívají.
 *
 * Nikdy nevyhodí: poškozený soubor je `null` a samolepka zůstane statickým náhledem.
 */
object WebmDemuxer {

    fun parse(bytes: ByteArray): WebmVideo? = try {
        Parser(bytes).parse()
    } catch (_: IndexOutOfBoundsException) {
        null
    } catch (_: IllegalStateException) {
        null
    }

    private class Parser(private val b: ByteArray) {
        private var timecodeScale = 1_000_000L
        private var trackNumber = -1L
        private var codec: WebmCodec? = null
        private var width = 0
        private var height = 0
        private var frameDurationNs = 0L
        private var alphaMode = false
        private var colour = WebmColour()
        private val frames = mutableListOf<WebmFrame>()

        fun parse(): WebmVideo? {
            var i = 0
            while (i < b.size) {
                val element = readHeader(i, b.size)
                if (element.id == SEGMENT) segment(element.dataStart, element.end) else Unit
                i = element.end
            }
            val codec = codec ?: return null
            if (width <= 0 || height <= 0 || frames.isEmpty()) return null
            return WebmVideo(
                codec = codec,
                width = width,
                height = height,
                frameDurationMs = frameDurationNs / 1_000_000,
                hasAlpha = alphaMode,
                colour = colour,
                frames = frames.sortedBy { it.timeMs },
            )
        }

        private fun segment(start: Int, end: Int) {
            var i = start
            while (i < end) {
                val e = readHeader(i, end)
                when (e.id) {
                    INFO -> children(e) { c -> if (c.id == TIMECODE_SCALE) timecodeScale = uint(c) }
                    TRACKS -> children(e) { c -> if (c.id == TRACK_ENTRY) trackEntry(c) }
                    CLUSTER -> cluster(e)
                }
                i = e.end
            }
        }

        private fun trackEntry(entry: Element) {
            // Bere se první video stopa ve VP8/VP9; ostatní (zvuk samolepka nemá) se přeskočí.
            if (codec != null) return
            var number = -1L
            var codecId = ""
            var duration = 0L
            var w = 0
            var h = 0
            var alpha = false
            var c = WebmColour()
            children(entry) { e ->
                when (e.id) {
                    TRACK_NUMBER -> number = uint(e)
                    CODEC_ID -> codecId = b.decodeToString(e.dataStart, e.end).trimEnd('\u0000')
                    DEFAULT_DURATION -> duration = uint(e)
                    VIDEO -> children(e) { v ->
                        when (v.id) {
                            PIXEL_WIDTH -> w = uint(v).toInt()
                            PIXEL_HEIGHT -> h = uint(v).toInt()
                            ALPHA_MODE -> alpha = uint(v) == 1L
                            COLOUR -> children(v) { col ->
                                when (col.id) {
                                    MATRIX_COEFFICIENTS -> c = c.copy(bt709 = uint(col) == 1L)
                                    RANGE -> c = c.copy(fullRange = uint(col) == 2L)
                                }
                            }
                        }
                    }
                }
            }
            val found = when (codecId) {
                "V_VP9" -> WebmCodec.Vp9
                "V_VP8" -> WebmCodec.Vp8
                else -> return
            }
            codec = found
            trackNumber = number
            frameDurationNs = duration
            width = w
            height = h
            alphaMode = alpha
            colour = c
        }

        private fun cluster(cluster: Element) {
            var clusterTime = 0L
            var i = cluster.dataStart
            while (i < cluster.end) {
                val e = readHeader(i, cluster.end)
                // Cluster neznámé délky končí, kde začíná další prvek segmentu.
                if (cluster.unknownSize && e.id in SEGMENT_LEVEL) return
                when (e.id) {
                    TIMECODE -> clusterTime = uint(e)
                    SIMPLE_BLOCK -> block(e.dataStart, e.end, clusterTime, simple = true, alpha = null, referenced = false)
                    BLOCK_GROUP -> blockGroup(e, clusterTime)
                }
                i = e.end
            }
        }

        private fun blockGroup(group: Element, clusterTime: Long) {
            var block: Element? = null
            var alpha: ByteArray? = null
            var referenced = false
            children(group) { e ->
                when (e.id) {
                    BLOCK -> block = e
                    REFERENCE_BLOCK -> referenced = true
                    BLOCK_ADDITIONS -> children(e) { more ->
                        if (more.id != BLOCK_MORE) return@children
                        var addId = 1L
                        var data: ByteArray? = null
                        children(more) { m ->
                            when (m.id) {
                                BLOCK_ADD_ID -> addId = uint(m)
                                BLOCK_ADDITIONAL -> data = b.copyOfRange(m.dataStart, m.end)
                            }
                        }
                        if (addId == 1L && data != null) alpha = data
                    }
                }
            }
            val found = block ?: return
            block(found.dataStart, found.end, clusterTime, simple = false, alpha = alpha, referenced = referenced)
        }

        private fun block(start: Int, end: Int, clusterTime: Long, simple: Boolean, alpha: ByteArray?, referenced: Boolean) {
            val (track, trackLength) = readVint(start, keepMarker = false)
            if (track != trackNumber) return
            var i = start + trackLength
            val relative = ((b[i].toInt() shl 8) or (b[i + 1].toInt() and 0xFF)).toShort().toLong()
            i += 2
            val flags = b[i].toInt() and 0xFF
            i += 1
            // Lacing (víc snímků v bloku) video enkodéry nepoužívají; kdyby přišel, snímek se vynechá.
            if (flags and LACING_MASK != 0) return
            val key = if (simple) flags and KEYFRAME_FLAG != 0 else !referenced
            val timeNs = (clusterTime + relative) * timecodeScale
            frames += WebmFrame(
                timeMs = timeNs / 1_000_000,
                isKey = key,
                data = b.copyOfRange(i, end),
                alpha = alpha,
            )
        }

        private inline fun children(parent: Element, action: (Element) -> Unit) {
            var i = parent.dataStart
            while (i < parent.end) {
                val e = readHeader(i, parent.end)
                action(e)
                i = e.end
            }
        }

        private fun uint(e: Element): Long {
            var v = 0L
            for (k in e.dataStart until e.end) v = (v shl 8) or (b[k].toLong() and 0xFF)
            return v
        }

        private fun readHeader(at: Int, parentEnd: Int): Element {
            val (id, idLength) = readVint(at, keepMarker = true)
            val (size, sizeLength) = readVint(at + idLength, keepMarker = false)
            val dataStart = at + idLength + sizeLength
            // Velikost ze samých jedniček = „neznámá"; sahá do konce rodiče.
            val unknown = size == (1L shl (7 * sizeLength)) - 1
            val end = if (unknown) parentEnd else (dataStart + size).coerceAtMost(parentEnd.toLong()).toInt()
            check(dataStart <= parentEnd) { "Prvek za koncem rodiče" }
            return Element(id, dataStart, end, unknown)
        }

        /** EBML celé číslo proměnné délky; s [keepMarker] jako id prvku, bez něj jako hodnota. */
        private fun readVint(at: Int, keepMarker: Boolean): Pair<Long, Int> {
            val first = b[at].toInt() and 0xFF
            check(first != 0) { "Neplatné číslo EBML" }
            var length = 1
            var mask = 0x80
            while (first and mask == 0) {
                mask = mask shr 1
                length++
            }
            var value = (if (keepMarker) first else first and (mask - 1)).toLong()
            for (k in 1 until length) value = (value shl 8) or (b[at + k].toLong() and 0xFF)
            return value to length
        }
    }

    private class Element(val id: Long, val dataStart: Int, val end: Int, val unknownSize: Boolean)

    private const val SEGMENT = 0x18538067L
    private const val INFO = 0x1549A966L
    private const val TIMECODE_SCALE = 0x2AD7B1L
    private const val TRACKS = 0x1654AE6BL
    private const val TRACK_ENTRY = 0xAEL
    private const val TRACK_NUMBER = 0xD7L
    private const val CODEC_ID = 0x86L
    private const val DEFAULT_DURATION = 0x23E383L
    private const val VIDEO = 0xE0L
    private const val PIXEL_WIDTH = 0xB0L
    private const val PIXEL_HEIGHT = 0xBAL
    private const val ALPHA_MODE = 0x53C0L
    private const val COLOUR = 0x55B0L
    private const val MATRIX_COEFFICIENTS = 0x55B1L
    private const val RANGE = 0x55B9L
    private const val CLUSTER = 0x1F43B675L
    private const val TIMECODE = 0xE7L
    private const val SIMPLE_BLOCK = 0xA3L
    private const val BLOCK_GROUP = 0xA0L
    private const val BLOCK = 0xA1L
    private const val REFERENCE_BLOCK = 0xFBL
    private const val BLOCK_ADDITIONS = 0x75A1L
    private const val BLOCK_MORE = 0xA6L
    private const val BLOCK_ADD_ID = 0xEEL
    private const val BLOCK_ADDITIONAL = 0xA5L

    /** Prvky na úrovni segmentu — u clusteru neznámé délky ohlašují jeho konec. */
    private val SEGMENT_LEVEL = setOf(CLUSTER, 0x1C53BB6BL, 0x1254C367L, 0x1941A469L, 0x114D9B74L, INFO, TRACKS)

    private const val KEYFRAME_FLAG = 0x80
    private const val LACING_MASK = 0x06
}

/** Snímek kratší než tohle by smyčku točil naprázdno; samolepky mají 30 snímků za vteřinu. */
internal const val MIN_FRAME_MS = 10L
