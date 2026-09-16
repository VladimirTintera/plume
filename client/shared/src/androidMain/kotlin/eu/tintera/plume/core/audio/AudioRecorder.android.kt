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

/**
 * Android: `MediaRecorder`.
 *
 * **Android 10+ nahrává Ogg/Opus**, přesně to, co Telegram chce (48 kHz mono, 32 kbit/s jako
 * oficiální klienti). Starší Android Opus nahrávat neumí; nahrává se M4A/AAC, které TDLib u hlasovky
 * přijme taky.
 */
actual class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var file: File? = null

    actual fun start() {
        val opus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        val target = File(context.cacheDir, "$OUTGOING_DIRECTORY/${UUID.randomUUID()}/voice.${if (opus) "ogg" else "m4a"}")
        target.parentFile?.mkdirs()

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
        recorder = created
        file = target
    }

    /** `maxAmplitude` je špička od posledního čtení; odmocnina zvedne tiché úrovně, ať je řeč vidět. */
    actual val level: Float
        get() = recorder?.let { sqrt(it.maxAmplitude / 32767f).coerceIn(0f, 1f) } ?: 0f

    actual suspend fun stop(): String {
        val active = recorder ?: error("Nic se nenahrává.")
        val target = file ?: error("Nic se nenahrává.")
        recorder = null
        file = null
        try {
            // Po příliš krátkém nahrávání `stop` vyhodí výjimku — soubor pak nic nenese.
            active.stop()
        } catch (failure: RuntimeException) {
            target.delete()
            throw failure
        } finally {
            active.release()
        }
        return target.absolutePath
    }

    actual fun cancel() {
        recorder?.let { active ->
            runCatching { active.stop() }
            active.release()
        }
        file?.parentFile?.deleteRecursively()
        recorder = null
        file = null
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
