package eu.tintera.plume.core.audio

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.AVFAudio.AVAudioCompressedBuffer
import platform.AVFAudio.AVAudioConverter
import platform.AVFAudio.AVAudioConverterInputStatus_EndOfStream
import platform.AVFAudio.AVAudioConverterInputStatus_HaveData
import platform.AVFAudio.AVAudioConverterOutputStatus_EndOfStream
import platform.AVFAudio.AVAudioConverterOutputStatus_Error
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioPCMBuffer
import platform.AVFAudio.AVAudioPCMFormatInt16
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.AudioStreamBasicDescription
import platform.CoreAudioTypes.kAudioFormatOpus
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile
import platform.darwin.NSObject
import platform.posix.memcpy

/**
 * iOS: `AVAudioPlayer` nad WAV.
 *
 * **AVFoundation Ogg nepřehraje** — Opus zná jen v kontejneru CAF. Dekodér Opusu ale systém má
 * (`AVAudioConverter`), takže se pakety vytáhnou z Oggu v Kotlinu ([parseOggOpus]), dekódují do
 * PCM a uloží jako WAV vedle sebe v dočasném adresáři. Hlasovky jsou krátké; minuta mono při
 * 48 kHz je necelých 6 MB. Dekóduje se jednou, druhé přehrání vezme hotový soubor.
 *
 * Soubor, který Ogg není (např. MP3 přeposlané jako hlasovka), jde do `AVAudioPlayer` rovnou.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class AudioEngine {

    private var player: AVAudioPlayer? = null

    private var speed = 1f

    /** Delegáta AVAudioPlayer drží jen slabě — bez silné reference by dohrání nikdo nehlásil. */
    private val delegate = object : NSObject(), AVAudioPlayerDelegateProtocol {
        override fun audioPlayerDidFinishPlaying(player: AVAudioPlayer, successfully: Boolean) {
            onCompleted?.invoke()
        }
    }

    /**
     * Přerušení hovorem, budíkem nebo Sirim.
     *
     * `AVAudioPlayer` přehrávání při přerušení zastaví sám, ale nikomu to neřekne — bez tohohle
     * pozorovatele by hlasovka v UI dál „hrála" a posuvník by stál. Notifikační centrum si
     * pozorovatele drží jen slabě, proto silná reference a odhlášení v [release].
     *
     * Reaguje se jen na ZAČÁTEK přerušení: po hovoru se zvuk sám nerozezní, stejně jako na
     * Androidu po návratu audio focus.
     */
    private val interruptionObserver = NSNotificationCenter.defaultCenter.addObserverForName(
        name = AVAudioSessionInterruptionNotification,
        `object` = AVAudioSession.sharedInstance(),
        queue = NSOperationQueue.mainQueue,
    ) { notification ->
        val type = (notification?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongValue
        if (type == AVAudioSessionInterruptionTypeBegan) onInterrupted?.invoke()
    }

    actual suspend fun load(path: String): Long {
        val playable = withContext(Dispatchers.IO) { playablePath(path) }

        // Kategorie „přehrávání": zvuk hraje i s přepínačem ztlumení, jak se u hlasovky čeká.
        AVAudioSession.sharedInstance().setCategory(AVAudioSessionCategoryPlayback, error = null)
        AVAudioSession.sharedInstance().setActive(true, error = null)

        val created = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            AVAudioPlayer(contentsOfURL = NSURL.fileURLWithPath(playable), error = error.ptr)
                .takeIf { error.value == null }
                ?: error(error.value?.localizedDescription ?: "Zvuk nejde otevřít.")
        }
        created.delegate = delegate
        // `enableRate` se MUSÍ nastavit před `prepareToPlay()`, jinak `rate` nic neudělá.
        created.enableRate = true
        check(created.prepareToPlay()) { "Zvuk nejde připravit." }
        created.rate = speed
        player = created
        return (created.duration * 1000).toLong()
    }

    actual fun start() {
        player?.play()
        // `play()` rychlost resetuje na 1; nastavit ji jde až na hrajícím přehrávači.
        player?.rate = speed
    }

    actual fun pause() {
        player?.pause()
    }

    actual fun seekTo(positionMs: Long) {
        player?.currentTime = positionMs / 1000.0
    }

    actual fun setSpeed(speed: Float) {
        this.speed = speed
        // Na pozastaveném přehrávači zápis `rate` přehrávání nerozjede (na rozdíl od Androidu).
        player?.rate = speed
    }

    actual val positionMs: Long get() = ((player?.currentTime ?: 0.0) * 1000).toLong()

    actual var onCompleted: (() -> Unit)? = null

    actual var onInterrupted: (() -> Unit)? = null

    actual fun release() {
        onCompleted = null
        onInterrupted = null
        NSNotificationCenter.defaultCenter.removeObserver(interruptionObserver)
        player?.stop()
        player?.delegate = null
        player = null
    }
}

/** Cesta k souboru, který `AVAudioPlayer` přehraje: u Oggu dekódovaný WAV, jinak původní soubor. */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun playablePath(path: String): String {
    val data = NSData.dataWithContentsOfFile(path) ?: error("Soubor nejde přečíst.")
    val bytes = data.toByteArray()
    if (bytes.size < 4 || bytes.decodeToString(0, 4) != "OggS") return path

    val directory = NSTemporaryDirectory() + "voice"
    NSFileManager.defaultManager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
    // Soubory TDLib se nemění, takže cesta a velikost soubor spolehlivě pojmenují.
    val target = "$directory/${path.hashCode().toUInt()}-${bytes.size}.wav"
    if (NSFileManager.defaultManager.fileExistsAtPath(target)) return target

    val stream = parseOggOpus(bytes)
    val pcm = decodeOpus(stream)
    val skip = (stream.preSkip * stream.channels * 2).coerceAtMost(pcm.size)
    val wav = wavFile(pcm.copyOfRange(skip, pcm.size), sampleRate = OPUS_SAMPLE_RATE, channels = stream.channels)
    check(wav.toNSData().writeToFile(target, atomically = true)) { "Zvuk nejde uložit." }
    return target
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).also { bytes ->
        bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.convert())
}

