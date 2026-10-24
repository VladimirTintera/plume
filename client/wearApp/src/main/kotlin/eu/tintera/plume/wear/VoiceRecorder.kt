package eu.tintera.plume.wear

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File
import kotlin.math.sqrt

/** Nahraná hlasovka připravená k odeslání. */
class VoiceRecording(val path: String, val durationSeconds: Int, val levels: List<Float>)

/**
 * Nahrávání hlasovky mikrofonem hodinek: Ogg/Opus mono 48 kHz, 32 kbit/s — formát, který chce
 * Telegram, stejně jako telefon (Android 10+ ho `MediaRecorder` umí rovnou). Úrovně pro waveform
 * se sbírají během nahrávání ([sampleLevel]); kratší než vteřinová nahrávka se zahodí.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private val levels = mutableListOf<Float>()

    val isRecording: Boolean get() = recorder != null

    fun start() {
        val output = File(context.cacheDir, "voice-${System.currentTimeMillis()}.ogg")
        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        runCatching {
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            created.setOutputFormat(MediaRecorder.OutputFormat.OGG)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            created.setAudioChannels(1)
            created.setAudioSamplingRate(48_000)
            created.setAudioEncodingBitRate(32_000)
            created.setOutputFile(output.path)
            created.prepare()
            created.start()
        }.onFailure {
            created.release()
            output.delete()
            throw it
        }
        recorder = created
        file = output
        levels.clear()
        startedAt = SystemClock.elapsedRealtime()
    }

    /** `maxAmplitude` je špička od posledního čtení; odmocnina zvedne tiché úrovně, ať je řeč vidět. */
    fun sampleLevel() {
        recorder?.let { levels += sqrt(it.maxAmplitude / 32767f).coerceIn(0f, 1f) }
    }

    fun elapsedSeconds(): Int = if (recorder == null) 0 else ((SystemClock.elapsedRealtime() - startedAt) / 1000).toInt()

    /** Zastaví nahrávání; `null`, když je nahrávka moc krátká nebo `stop()` selhal. */
    fun stop(): VoiceRecording? {
        val active = recorder ?: return null
        val duration = elapsedSeconds()
        recorder = null
        // `stop()` po příliš krátkém nahrávání vyhodí výjimku — pak nahrávka nestojí za odeslání.
        val stopped = runCatching { active.stop() }.isSuccess
        active.release()
        val output = file
        file = null
        return if (stopped && output != null && duration >= 1) {
            VoiceRecording(output.path, duration, levels.toList())
        } else {
            output?.delete()
            null
        }
    }

    fun cancel() {
        recorder?.let { active ->
            runCatching { active.stop() }
            active.release()
        }
        recorder = null
        file?.delete()
        file = null
    }
}
