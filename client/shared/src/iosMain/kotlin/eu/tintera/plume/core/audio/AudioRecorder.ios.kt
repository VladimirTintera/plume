package eu.tintera.plume.core.audio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioFile
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatInt16
import platform.AVFAudio.AVAudioRecorder
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionDefaultToSpeaker
import platform.AVFAudio.AVAudioSessionCategoryPlayAndRecord
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVFormatIDKey
import platform.AVFAudio.AVLinearPCMBitDepthKey
import platform.AVFAudio.AVLinearPCMIsBigEndianKey
import platform.AVFAudio.AVLinearPCMIsFloatKey
import platform.AVFAudio.AVNumberOfChannelsKey
import platform.AVFAudio.AVSampleRateKey
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.kAudioFormatLinearPCM
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.writeToFile
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * iOS: `AVAudioRecorder` do PCM, po skončení zakódování do Opusu a zabalení do Oggu.
 *
 * `AVAudioRecorder` umí nahrávat Opus, ale jen do CAF — Telegram chce Ogg. Proto se nahrává
 * nekomprimované PCM (48 kHz mono, minuta ≈ 5,5 MB) a kóduje se až na konci; hlasovky jsou krátké
 * a kódování trvá zlomek jejich délky.
 *
 * Relace je PlayAndRecord s reproduktorem jako výchozím výstupem — bez toho by po nahrávání hrálo
 * všechno do sluchátka u ucha.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class AudioRecorder {

    private var recorder: AVAudioRecorder? = null
    private var directory: String? = null

    actual fun start() {
        val session = AVAudioSession.sharedInstance()
        session.setCategory(
            AVAudioSessionCategoryPlayAndRecord,
            mode = AVAudioSessionModeDefault,
            options = AVAudioSessionCategoryOptionDefaultToSpeaker,
            error = null,
        )
        session.setActive(true, error = null)

        val folder = NSTemporaryDirectory() + "outgoing/" + NSUUID().UUIDString
        NSFileManager.defaultManager.createDirectoryAtPath(folder, withIntermediateDirectories = true, attributes = null, error = null)
        val settings = mapOf<Any?, Any?>(
            AVFormatIDKey to NSNumber(unsignedInt = kAudioFormatLinearPCM),
            AVSampleRateKey to NSNumber(double = OPUS_SAMPLE_RATE.toDouble()),
            AVNumberOfChannelsKey to NSNumber(int = 1),
            AVLinearPCMBitDepthKey to NSNumber(int = 16),
            AVLinearPCMIsFloatKey to NSNumber(bool = false),
            AVLinearPCMIsBigEndianKey to NSNumber(bool = false),
        )
        val created = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            AVAudioRecorder(uRL = NSURL.fileURLWithPath("$folder/voice.caf"), settings = settings, error = error.ptr)
                .takeIf { error.value == null }
                ?: error(error.value?.localizedDescription ?: "Mikrofon nejde otevřít.")
        }
        created.meteringEnabled = true
        if (!created.record()) {
            NSFileManager.defaultManager.removeItemAtPath(folder, error = null)
            error("Nahrávání nejde spustit.")
        }
        recorder = created
        directory = folder
    }

    /** Průměrná hlasitost v dB (−160…0) převedená na 0..1; pod −50 dB je ticho. */
    actual val level: Float
        get() {
            val active = recorder ?: return 0f
            active.updateMeters()
            return ((active.averagePowerForChannel(0u) + 50f) / 50f).coerceIn(0f, 1f)
        }

    actual suspend fun stop(): String {
        val active = recorder ?: error("Nic se nenahrává.")
        val folder = directory ?: error("Nic se nenahrává.")
        recorder = null
        directory = null
        active.stop()

        return withContext(Dispatchers.IO) {
            val source = "$folder/voice.caf"
            val pcm = memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                val file = AVAudioFile(
                    forReading = NSURL.fileURLWithPath(source),
                    commonFormat = AVAudioPCMFormatInt16,
                    interleaved = true,
                    error = error.ptr,
                ).takeIf { error.value == null } ?: error("Nahrávku nejde přečíst.")
                val buffer = AVAudioPCMBuffer(pCMFormat = file.processingFormat, frameCapacity = file.length.toUInt())
                check(file.readIntoBuffer(buffer, error.ptr)) { "Nahrávku nejde přečíst." }
                buffer
            }
            val packets = encodeOpus(pcm)
            val ogg = oggOpusFile(
                packets = packets,
                channels = 1,
                preSkip = OPUS_PRE_SKIP,
                samplesPerPacket = OPUS_ENCODE_SAMPLES_PER_PACKET,
            )
            val target = "$folder/voice.ogg"
            check(ogg.toNSData().writeToFile(target, atomically = true)) { "Nahrávku nejde uložit." }
            NSFileManager.defaultManager.removeItemAtPath(source, error = null)
            target
        }
    }

    actual fun pause() {
        recorder?.pause()
    }

    actual fun resume() {
        recorder?.record()
    }

    actual fun cancel() {
        recorder?.stop()
        recorder?.deleteRecording()
        directory?.let { NSFileManager.defaultManager.removeItemAtPath(it, error = null) }
        recorder = null
        directory = null
    }
}

@Composable
actual fun rememberRecordPermission(): RecordPermission = remember {
    object : RecordPermission {
        override fun request(onResult: (granted: Boolean) -> Unit) {
            val session = AVAudioSession.sharedInstance()
            if (session.recordPermission == AVAudioSessionRecordPermissionGranted) {
                onResult(true)
                return
            }
            // Systémový dialog se ukáže jen poprvé; potom odpovídá rovnou. Callback chodí mimo hlavní vlákno.
            session.requestRecordPermission { granted ->
                dispatch_async(dispatch_get_main_queue()) { onResult(granted) }
            }
        }
    }
}

/** Zpoždění kodéru na začátku streamu, které přehrávač zahodí (běžná hodnota libopus při 48 kHz). */
private const val OPUS_PRE_SKIP = 312
