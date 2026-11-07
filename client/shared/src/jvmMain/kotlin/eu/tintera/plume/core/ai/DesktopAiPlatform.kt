package eu.tintera.plume.core.ai

import eu.tintera.ai.core.AudioClipDecoder
import eu.tintera.plume.core.audio.FfmpegAudioDecoder
import eu.tintera.plume.core.platform.DesktopOs
import eu.tintera.plume.core.platform.desktopDataDirectory
import eu.tintera.plume.core.platform.desktopOs
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/*
 * Co desktopová platforma modelů v zařízení (`DesktopLiteRtPlatform` z :client:ai-core) bere od
 * Plume: adresář pro modely a dekodér zvuku.
 */

/**
 * Místní (neroamingový) adresář pro modely a jejich keš. Data aplikace na Windows leží
 * v `%APPDATA%` (Roaming), který se v doménovém profilu synchronizuje mezi počítači — gigabajty
 * modelu tam nepatří, proto `%LOCALAPPDATA%`. Na macOS a Linuxu ve složce dat; s `-Dplume.dataDir`
 * (ladicí běh) pod ní, ať nesahá na modely nainstalované aplikace.
 */
val aiModelsDirectory: File by lazy {
    val override = System.getProperty("plume.dataDir")?.takeIf { it.isNotBlank() }
    when {
        override != null -> desktopDataDirectory
        desktopOs == DesktopOs.Windows ->
            File(System.getenv("LOCALAPPDATA") ?: "${System.getProperty("user.home")}/AppData/Local", "Plume")
        else -> desktopDataDirectory
    }
}

/**
 * Zvuk pro Gemmu přes FFmpeg ([FfmpegAudioDecoder], hlasovka Ogg/Opus i kolečko MP4). Dekodér
 * vydává 48 kHz stereo 16 bitů (pro reproduktor); na 16 kHz mono se to dostane průměrem obou
 * kanálů a tří po sobě jdoucích vzorků — 48 000 je přesně trojnásobek.
 */
object DesktopAudioClips : AudioClipDecoder {

    override fun wavClips(path: String): List<ByteArray> {
        val mono = ShortArrayBuilder()
        FfmpegAudioDecoder.open(path).use { decoder ->
            var sum = 0
            var count = 0
            while (true) {
                val chunk = decoder.read() ?: break
                val samples = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                while (samples.remaining() >= 2) {
                    sum += samples.get() + samples.get()
                    count += 2
                    if (count == DOWNSAMPLE * 2) {
                        mono.add((sum / count).toShort())
                        sum = 0
                        count = 0
                    }
                }
            }
        }
        val samples = mono.toArray()
        val clip = SAMPLE_RATE * MAX_CLIP_SECONDS
        return (samples.indices step clip).map { from -> wav(samples.copyOfRange(from, minOf(from + clip, samples.size))) }
    }

    private fun wav(samples: ShortArray): ByteArray {
        val data = samples.size * 2
        return ByteBuffer.allocate(WAV_HEADER + data).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(WAV_HEADER - 8 + data); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(1)
            putInt(SAMPLE_RATE); putInt(SAMPLE_RATE * 2); putShort(2); putShort(16)
            put("data".toByteArray()); putInt(data)
            samples.forEach { putShort(it) }
        }.array()
    }

    /** Rostoucí pole vzorků bez krabičkování `Short`. */
    private class ShortArrayBuilder {
        private var data = ShortArray(SAMPLE_RATE * 10)
        private var size = 0
        fun add(value: Short) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = value
        }
        fun toArray(): ShortArray = data.copyOf(size)
    }

    private const val SAMPLE_RATE = 16_000
    private const val MAX_CLIP_SECONDS = 30
    private const val DOWNSAMPLE = FfmpegAudioDecoder.OUTPUT_RATE / SAMPLE_RATE
    private const val WAV_HEADER = 44
}
