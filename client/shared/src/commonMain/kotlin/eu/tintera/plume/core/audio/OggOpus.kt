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

/**
 * Spojí nahrávku z víc úseků (pozastavené nahrávání) do jednoho Oggu.
 *
 * `MediaRecorder` s výstupem Ogg/Opus pauzu nedodrží: na emulátoru kódoval i během ní
 * a hlasovka pozastavená na tři vteřiny výběru času měla 32 s. Nahrává se proto po úsecích
 * a zvukové pakety se tady skládají za sebe. [preSkip] se bere z prvního úseku — na začátku
 * dalších úseků zbyde zahřívací kousek dekodéru, jednotky milisekund.
 */
fun joinOggOpus(segments: List<ByteArray>): ByteArray {
    require(segments.isNotEmpty()) { "Žádný úsek." }
    val streams = segments.map { parseOggOpus(it) }
    val first = streams.first()
    // `parseOggOpus` hlavičku a tagy odřízne, zbývá jen zvuk.
    val audio = streams.flatMap { it.packets }
    val samples = audio.firstOrNull()?.let(::opusPacketSamples) ?: DEFAULT_SAMPLES_PER_PACKET
    return oggOpusFile(audio, channels = first.channels, preSkip = first.preSkip, samplesPerPacket = samples)
}

/** 20 ms při 48 kHz — rámec, který Android i iOS kodér používají. */
private const val DEFAULT_SAMPLES_PER_PACKET = 960

/**
 * Zabalí Opus pakety do Ogg — formát, ve kterém hlasovku chce Telegram. iOS umí Opus zakódovat,
 * ale ne do Oggu, takže kontejner se skládá tady.
 *
 * Stránky: `OpusHead` (začátek streamu), `OpusTags`, pak zvuk po [PACKETS_PER_PAGE] paketech;
 * poslední stránka nese příznak konce. Granule je počet vzorků (48 kHz) včetně [preSkip] na konci
 * stránky — podle ní přehrávače počítají délku. [samplesPerPacket] musí sedět s kodérem.
 */
fun oggOpusFile(packets: List<ByteArray>, channels: Int, preSkip: Int, samplesPerPacket: Int): ByteArray {
    val out = ByteArrayBuilder()
    val serial = 0x504C554D // „PLUM"
    var sequence = 0

    fun page(pagePackets: List<ByteArray>, granule: Long, flags: Int) {
        val lacing = ByteArrayBuilder()
        pagePackets.forEach { packet ->
            var remaining = packet.size
            while (remaining >= 255) {
                lacing.append(byteArrayOf(255.toByte()), 0, 1)
                remaining -= 255
            }
            lacing.append(byteArrayOf(remaining.toByte()), 0, 1)
        }
        val segments = lacing.toByteArray()
        val header = ByteArray(OGG_HEADER_SIZE)
        "OggS".encodeToByteArray().copyInto(header)
        header[5] = flags.toByte()
        for (i in 0 until 8) header[6 + i] = (granule shr (8 * i)).toByte()
        for (i in 0 until 4) header[14 + i] = (serial shr (8 * i)).toByte()
        for (i in 0 until 4) header[18 + i] = (sequence shr (8 * i)).toByte()
        header[26] = segments.size.toByte()
        sequence++

        val body = ByteArrayBuilder()
        pagePackets.forEach { body.append(it, 0, it.size) }
        val pageBytes = header + segments + body.toByteArray()
        val crc = oggCrc(pageBytes)
        for (i in 0 until 4) pageBytes[22 + i] = (crc shr (8 * i)).toByte()
        out.append(pageBytes, 0, pageBytes.size)
    }

    val head = "OpusHead".encodeToByteArray() + byteArrayOf(
        1, channels.toByte(), preSkip.toByte(), (preSkip shr 8).toByte(),
        0x80.toByte(), 0xBB.toByte(), 0, 0, // původní vzorkování 48 000 Hz
        0, 0, // zesílení
        0, // mapování kanálů: mono/stereo
    )
    page(listOf(head), granule = 0, flags = 0x02)
    val vendor = "Plume".encodeToByteArray()
    val tags = "OpusTags".encodeToByteArray() +
        byteArrayOf(vendor.size.toByte(), 0, 0, 0) + vendor + byteArrayOf(0, 0, 0, 0)
    page(listOf(tags), granule = 0, flags = 0)

    var granule = preSkip.toLong()
    val pages = packets.chunked(PACKETS_PER_PAGE)
    if (pages.isEmpty()) page(emptyList(), granule, flags = 0x04)
    pages.forEachIndexed { index, chunk ->
        granule += chunk.size.toLong() * samplesPerPacket
        page(chunk, granule, flags = if (index == pages.lastIndex) 0x04 else 0)
    }
    return out.toByteArray()
}

/** CRC Oggu: polynom 0x04C11DB7, bez zrcadlení, počáteční hodnota 0. Pole CRC se počítá jako nuly. */
internal fun oggCrc(bytes: ByteArray): Int {
    var crc = 0
    for (byte in bytes) {
        crc = crc xor ((byte.toInt() and 0xFF) shl 24)
        repeat(8) {
            crc = if (crc and 0x80000000.toInt() != 0) (crc shl 1) xor 0x04C11DB7 else crc shl 1
        }
    }
    return crc
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

/** Kolik paketů na stránku Oggu: pro 20ms pakety zhruba vteřina, a segmentová tabulka (255) nepřeteče. */
private const val PACKETS_PER_PAGE = 50
