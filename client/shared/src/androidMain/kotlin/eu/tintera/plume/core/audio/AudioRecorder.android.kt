package eu.tintera.plume.core.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import eu.tintera.plume.core.ui.OUTGOING_DIRECTORY
import java.io.File
import java.util.UUID
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android: `MediaRecorder`.
 *
 * **Android 10+ nahrává Ogg/Opus**, přesně to, co Telegram chce (48 kHz mono, 32 kbit/s jako
 * oficiální klienti). Starší Android Opus nahrávat neumí; nahrává se M4A/AAC, které TDLib u hlasovky
 * přijme taky.
 */
actual class AudioRecorder(private val context: Context) : MediaCapture {

    private var recorder: MediaRecorder? = null

    /** Složka nahrávky; úseky i výsledný soubor jsou v ní. */
    private var folder: File? = null

    /** Hotové úseky Ogg/Opus, pořadím nahrávání (viz [pause]). */
    private val segments = mutableListOf<File>()

    private val opus get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    actual override fun start() {
        val target = File(context.cacheDir, "$OUTGOING_DIRECTORY/${UUID.randomUUID()}")
        target.mkdirs()
        folder = target
        segments.clear()
        try {
            recorder = startSegment()
        } catch (failure: Throwable) {
            target.deleteRecursively()
            folder = null
            throw failure
        }
    }

    /** Založí nahrávání do dalšího souboru ve složce. */
    private fun startSegment(): MediaRecorder {
        val target = File(folder ?: error("Nic se nenahrává."), "voice-${segments.size}.${if (opus) "ogg" else "m4a"}")
        val created = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            if (opus) {
                created.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                created.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                created.setAudioSamplingRate(48_000)
                created.setAudioEncodingBitRate(32_000)
            } else {
                created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                created.setAudioSamplingRate(44_100)
                created.setAudioEncodingBitRate(64_000)
            }
            created.setAudioChannels(1)
            created.setOutputFile(target.absolutePath)
            created.prepare()
            created.start()
        } catch (failure: Throwable) {
            created.release()
            target.delete()
            throw failure
        }
        segments += target
        return created
    }

    /** `maxAmplitude` je špička od posledního čtení; odmocnina zvedne tiché úrovně, ať je řeč vidět. */
    actual override val level: Float
        get() = recorder?.let { sqrt(it.maxAmplitude / 32767f).coerceIn(0f, 1f) } ?: 0f

    /**
     * Ogg/Opus se pozastavuje UKONČENÍM úseku, ne `MediaRecorder.pause`. Ten s výstupem Ogg
     * pauzu nedodržel — na emulátoru kódoval dál a hlasovka pozastavená na výběr času měla
     * místo tří vteřin dvaatřicet. Úseky spojí [stop] ([joinOggOpus]). M4A (Android 9 a starší)
     * se spojovat neumí, tam zůstává `pause`.
     */
    actual override fun pause() {
        val active = recorder ?: return
        if (!opus) {
            active.pause()
            return
        }
        recorder = null
        finishSegment(active)
    }

    actual override fun resume() {
        if (!opus) {
            recorder?.resume()
            return
        }
        if (recorder == null) recorder = startSegment()
    }

    /** Ukončí úsek; prázdný (pauza hned po obnovení) `stop` odmítne a úsek se zahodí. */
    private fun finishSegment(active: MediaRecorder) {
        try {
            active.stop()
        } catch (failure: RuntimeException) {
            segments.removeLastOrNull()?.delete()
        } finally {
            active.release()
        }
    }

    actual override suspend fun stop(): String {
        val target = folder ?: error("Nic se nenahrává.")
        recorder?.let { active ->
            recorder = null
            if (opus) {
                finishSegment(active)
            } else {
                try {
                    // Po příliš krátkém nahrávání `stop` vyhodí výjimku — soubor pak nic nenese.
                    active.stop()
                } catch (failure: RuntimeException) {
                    target.deleteRecursively()
                    throw failure
                } finally {
                    active.release()
                }
            }
        }
        folder = null
        val parts = segments.toList()
        segments.clear()
        if (parts.isEmpty()) {
            target.deleteRecursively()
            error("Nahrávka je prázdná.")
        }
        if (parts.size == 1) return parts.single().absolutePath
        return withContext(Dispatchers.IO) {
            val joined = File(target, "voice.ogg")
            joined.writeBytes(joinOggOpus(parts.map { it.readBytes() }))
            parts.forEach { it.delete() }
            joined.absolutePath
        }
    }

    actual override fun cancel() {
        recorder?.let { active ->
            runCatching { active.stop() }
            active.release()
        }
        folder?.deleteRecursively()
        recorder = null
        folder = null
        segments.clear()
    }
}

@Composable
actual fun rememberRecordPermission(): RecordPermission {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<(Boolean) -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending[0]?.invoke(granted)
        pending[0] = null
    }
    return remember(launcher) {
        object : RecordPermission {
            override fun request(onResult: (granted: Boolean) -> Unit) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    onResult(true)
                } else {
                    pending[0] = onResult
                    launcher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }
    }
}
