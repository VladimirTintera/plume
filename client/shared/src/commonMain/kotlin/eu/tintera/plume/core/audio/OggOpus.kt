package eu.tintera.plume.core.audio

/**
 * Opus stream vybalený z kontejneru Ogg — to, co Telegram posílá jako hlasovku.
 *
 * iOS Ogg neumí přehrát (AVFoundation zná Opus jen v CAF), ale dekodér Opusu v systému má. Proto se
 * pakety vytahují tady, v čistém Kotlinu, a platforma je jen dekóduje.
 */
class OggOpusStream(
    val channels: Int,
    /** Vzorky na začátku, které dekodér vydá „na zahřátí" a mají se zahodit (při 48 kHz). */
    val preSkip: Int,
    val packets: List<ByteArray>,
)

/**
 * Rozebere Ogg stránky a složí z nich pakety (segment dlouhý 255 bajtů znamená, že paket pokračuje,
 * i přes hranici stránky). První paket je `OpusHead`, druhý `OpusTags`, zbytek zvuk.
 * Bere jen první logický stream — hlasovka jiný nemá.
 */
fun parseOggOpus(bytes: ByteArray): OggOpusStream {
    val packets = mutableListOf<ByteArray>()
    var current = ByteArrayBuilder()
    var serial: Int? = null
    var offset = 0

    while (offset + OGG_HEADER_SIZE <= bytes.size) {
        require(bytes[offset] == 'O'.code.toByte() && bytes[offset + 1] == 'g'.code.toByte() &&
            bytes[offset + 2] == 'g'.code.toByte() && bytes[offset + 3] == 'S'.code.toByte()) {
            "Není to Ogg (offset $offset)."
        }
        val pageSerial = bytes.intLe(offset + 14)
        val segmentCount = bytes[offset + 26].toInt() and 0xFF
        val tableStart = offset + OGG_HEADER_SIZE
        require(tableStart + segmentCount <= bytes.size) { "Useknutá Ogg stránka." }

        var dataOffset = tableStart + segmentCount
        val ownStream = serial == null || serial == pageSerial
        if (serial == null) serial = pageSerial

        for (segment in 0 until segmentCount) {
            val length = bytes[tableStart + segment].toInt() and 0xFF
            require(dataOffset + length <= bytes.size) { "Useknutá Ogg stránka." }
            if (ownStream) {
                current.append(bytes, dataOffset, length)
                if (length < 255) {
                    packets += current.toByteArray()
                    current = ByteArrayBuilder()
                }
            }
            dataOffset += length
        }
        offset = dataOffset
    }

    val head = packets.firstOrNull() ?: error("Prázdný Ogg.")
    require(head.size >= 19 && head.decodeToString(0, 8) == "OpusHead") { "Ogg nenese Opus." }
    val channels = head[9].toInt() and 0xFF
    val preSkip = (head[10].toInt() and 0xFF) or ((head[11].toInt() and 0xFF) shl 8)

    // Za hlavičkou jsou tagy; zvuk začíná až za nimi.
    val audio = packets.drop(1).dropWhile { it.size >= 8 && it.decodeToString(0, 8) == "OpusTags" }
    return OggOpusStream(channels = channels, preSkip = preSkip, packets = audio)
}

/**
 * Počet vzorků (při 48 kHz) v jednom Opus paketu, z jeho TOC bajtu (RFC 6716, 3.1). `0` u paketu,
 * který nejde přečíst. Dekodér na iOS potřebuje vědět, kolik vzorků paket nese.
 */
fun opusPacketSamples(packet: ByteArray): Int {
    if (packet.isEmpty()) return 0
    val toc = packet[0].toInt() and 0xFF
    val config = toc shr 3
    val frameSize = when {
        config < 12 -> intArrayOf(480, 960, 1920, 2880)[config and 3] // SILK: 10/20/40/60 ms
        config < 16 -> intArrayOf(480, 960)[config and 1] // hybrid: 10/20 ms
        else -> intArrayOf(120, 240, 480, 960)[config and 3] // CELT: 2,5/5/10/20 ms
    }
    val frames = when (toc and 3) {
        0 -> 1
        1, 2 -> 2
        else -> if (packet.size < 2) return 0 else packet[1].toInt() and 0x3F
    }
    return frameSize * frames
}

/** WAV (PCM 16 bit, little endian) s hlavičkou; to přehraje každý systémový přehrávač. */
fun wavFile(pcm16: ByteArray, sampleRate: Int, channels: Int): ByteArray {
    val header = ByteArrayBuilder()
    fun ascii(text: String) = text.encodeToByteArray().let { header.append(it, 0, it.size) }
    fun int(value: Int) = header.append(byteArrayOf(value.toByte(), (value shr 8).toByte(), (value shr 16).toByte(), (value shr 24).toByte()), 0, 4)
    fun short(value: Int) = header.append(byteArrayOf(value.toByte(), (value shr 8).toByte()), 0, 2)

    ascii("RIFF"); int(36 + pcm16.size); ascii("WAVE")
    ascii("fmt "); int(16); short(1); short(channels); int(sampleRate)
    int(sampleRate * channels * 2); short(channels * 2); short(16)
    ascii("data"); int(pcm16.size)
    return header.toByteArray() + pcm16
}

private fun ByteArray.intLe(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16) or ((this[offset + 3].toInt() and 0xFF) shl 24)

private class ByteArrayBuilder {
    private var buffer = ByteArray(256)
    private var size = 0

    fun append(source: ByteArray, offset: Int, length: Int) {
        if (size + length > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + length))
        source.copyInto(buffer, size, offset, offset + length)
        size += length
    }

    fun toByteArray(): ByteArray = buffer.copyOf(size)
}

private const val OGG_HEADER_SIZE = 27
