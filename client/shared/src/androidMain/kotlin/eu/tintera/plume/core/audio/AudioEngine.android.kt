package eu.tintera.plume.core.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import co.touchlab.kermit.Logger
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Android: Media3 ExoPlayer. Hlasovky Telegramu jsou Ogg/Opus a ExoPlayer je přehraje sám
 * (Ogg rozebere, Opus dekóduje systémový kodek).
 *
 * Dřív `MediaPlayer`; ExoPlayer je tu kvůli přeskakování ticha (`skipSilenceEnabled`), které
 * `MediaPlayer` nemá. Atributy říkají systému, že jde o řeč — hlasitost se řídí médii, ne
 * vyzváněním.
 */
actual class AudioEngine(private val context: Context) {

    private val audioManager = context.getSystemService<AudioManager>()
    private val logger = Logger.withTag("AudioEngine")

    private var player: ExoPlayer? = null

    /** Cesta k přehrávanému souboru. */
    private var path: String? = null

    /** Rychlost; drží se i bez přehrávače, aby platila od prvního [start]. */
    private var speed = 1f

    /** Přeskakovat ticho ([setSkipSilence]); drží se, aby platilo i pro přehrávač z [load]. */
    private var skipSilence = false

    /** Hraje se do sluchátka u ucha místo do reproduktoru? */
    private var earpiece = false

    /**
     * Sáhli jsme na režim zvuku systému?
     *
     * `AudioManager.mode` je nastavení CELÉHO systému, ne našeho přehrávače. Vracet ho na
     * `MODE_NORMAL` naslepo by během hovoru shodilo zvuk hovoru — proto se vrací jen to, co jsme
     * sami nastavili.
     */
    private var routeApplied = false

    /** Poslední známá pozice; po uvolnění přehrávače není koho se zeptat. */
    private var lastPosition = 0L

    /** Hudba, ne hlasovka — jiné atributy, jiný audio focus a pauza po vytažení sluchátek. */
    private var music = false

    /**
     * Audio focus: hlasovka na chvíli utne hudbu, a když o zvuk přijdeme my (hovor, budík, jiná
     * aplikace), přehrávání se pozastaví.
     *
     * `GAIN_TRANSIENT`, ne `MAY_DUCK`: řeč pod ztlumenou hudbou se neposlouchá, a je to krátké.
     * Po návratu focusu se NEPOKRAČUJE sama od sebe — po telefonátu si uživatel pustí hlasovku
     * znovu vědomě, kdežto zvuk, který se rozezní bez ťuknutí, překvapí.
     */
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> interrupt()
            // Hudbu pod navigací nebo zvukem notifikace systém ztiší sám (od API 26); řeč pod
            // ztlumeným hlášením se neposlouchá, tu je lepší zastavit.
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> if (!music) interrupt()
        }
    }

    /**
     * Audio focus: hlasovka si ho bere jen na chvíli (`GAIN_TRANSIENT`) a hudbě jiné aplikace
     * se po ní vrátí; hudba si ho bere natrvalo (`GAIN`), jako každý přehrávač hudby — jiná
     * hudební aplikace se tím zastaví a sama nenaváže. Sestavuje se v [load], až je jasné co.
     * `AudioFocusRequest` je od API 26, tedy od `minSdk` projektu.
     */
    private var focusRequest: AudioFocusRequest? = null

    /**
     * Vytažená sluchátka hudbu zastaví (`ACTION_AUDIO_BECOMING_NOISY`), jinak by se po vytržení
     * kabelu rozezněla z reproduktoru. U hlasovky ne — ta hraje z reproduktoru běžně.
     */
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = interrupt()
    }
    private var noisyRegistered = false

    private fun interrupt() {
        // `playWhenReady`, ne `isPlaying`: ten je `false` i ve chvíli, kdy přehrávač jen čeká na data.
        player?.takeIf { it.playWhenReady }?.let {
            it.pause()
            abandonFocus()
            onInterrupted?.invoke()
        }
    }

    actual suspend fun load(path: String, music: Boolean): Long {
        this.path = path
        this.music = music
        focusRequest = AudioFocusRequest.Builder(
            if (music) AudioManager.AUDIOFOCUS_GAIN else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
        )
            .setAudioAttributes(platformAttributesFor(earpiece = false, music = music))
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        val created = buildPlayer(path)
        player = created
        return created.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
    }

    actual fun start() {
        requestFocus()
        applyRoute()
        val current = player ?: return
        current.setPlaybackSpeed(speed)
        current.play()
    }

    actual fun pause() {
        player?.let {
            lastPosition = it.currentPosition
            it.pause()
        }
        abandonFocus()
    }

    actual fun seekTo(positionMs: Long) {
        lastPosition = positionMs
        player?.seekTo(positionMs)
    }

    /**
     * Rychlost jde na ExoPlayer rovnou, i na pozastavený: na rozdíl od `MediaPlayer.playbackParams`
     * přehrávání nerozjede.
     */
    actual fun setSpeed(speed: Float) {
        this.speed = speed
        player?.setPlaybackSpeed(speed)
    }

    /**
     * Přeskakování ticha (`skipSilenceEnabled` v Media3): pauzy se zkrátí, řeč zůstane, jak byla —
     * nezrychluje se, jen se vynechává ticho. Platí hned, i uprostřed nahrávky.
     */
    actual fun setSkipSilence(enabled: Boolean) {
        skipSilence = enabled
        player?.skipSilenceEnabled = enabled
    }

    /**
     * Přepne mezi reproduktorem a sluchátkem u ucha.
     *
     * Do sluchátka se na Androidu dostane jen zvuk s `USAGE_VOICE_COMMUNICATION`. `MediaPlayer`
     * to uměl jen přestavbou (atributy se mu nastavují před `prepare()`); ExoPlayer atributy mění
     * za běhu — zvukovou stopu si postaví znovu sám, pozice ani rychlost se nemění. Samotné
     * `setCommunicationDevice` nestačí: řídí jen komunikační zvuk, médium by hrálo dál
     * z reproduktoru.
     */
    actual suspend fun setEarpiece(enabled: Boolean) {
        if (enabled == earpiece) return
        val current = player ?: return
        earpiece = enabled
        lastPosition = current.currentPosition
        current.setAudioAttributes(attributesFor(earpiece = enabled, music = music), /* handleAudioFocus = */ false)
        applyRoute()
    }

    /**
     * Jsou připojená sluchátka (drát, Bluetooth, USB, naslouchátko)?
     *
     * Se sluchátky se do sluchátka telefonu nepřepíná — viz [raisedToEar].
     */
    actual val headphonesConnected: Boolean
        get() = audioManager
            ?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            ?.any { it.type in HEADPHONE_TYPES }
            ?: false

    actual val positionMs: Long get() = player?.currentPosition ?: lastPosition

    actual var onCompleted: (() -> Unit)? = null

    actual var onInterrupted: (() -> Unit)? = null

    actual fun release() {
        onCompleted = null
        onInterrupted = null
        abandonFocus()
        clearRoute()
        player?.release()
        player = null
        path = null
    }

    /**
     * Připraví ExoPlayer a počká, až zná délku (`STATE_READY`), nebo selže.
     *
     * Na hlavním vlákně (`VoicePlayer` běží na `Dispatchers.Main`): ExoPlayer se ovládá z vlákna,
     * na kterém vznikl, a čtení souboru si dělá na svém. Audio focus řešíme sami
     * (`handleAudioFocus = false`) — hlasovka chce jiný než hudba a přerušení se musí promítnout
     * do stavu ([onInterrupted]), což by vestavěná obsluha neudělala.
     */
    private suspend fun buildPlayer(source: String): ExoPlayer {
        val created = ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(attributesFor(earpiece, music), /* handleAudioFocus = */ false)
            // Hudba hraje se zhasnutým displejem ze služby; vlákno přehrávače nesmí usnout.
            .setWakeMode(if (music) C.WAKE_MODE_LOCAL else C.WAKE_MODE_NONE)
            .build()
        created.skipSilenceEnabled = skipSilence
        created.setMediaItem(MediaItem.fromUri(Uri.fromFile(File(source))))
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY && continuation.isActive) {
                            created.removeListener(this)
                            continuation.resume(Unit)
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        created.removeListener(this)
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
                created.addListener(listener)
                created.prepare()
                continuation.invokeOnCancellation { created.removeListener(listener) }
            }
        } catch (failure: Throwable) {
            created.release()
            throw failure
        }
        created.addListener(
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState != Player.STATE_ENDED || player !== created) return
                    abandonFocus()
                    clearRoute()
                    onCompleted?.invoke()
                }

                /** Chyba uprostřed přehrávání (vadný konec souboru): zastavit a promítnout do stavu. */
                override fun onPlayerError(error: PlaybackException) {
                    if (player !== created) return
                    logger.w(error) { "Přehrávání zvuku selhalo." }
                    lastPosition = created.currentPosition
                    abandonFocus()
                    clearRoute()
                    onInterrupted?.invoke()
                }
            },
        )
        return created
    }

    /** Nasměruje zvuk podle [earpiece]. Volá se po každé přestavbě i při spuštění. */
    private fun applyRoute() {
        val manager = audioManager ?: return
        if (!earpiece) return clearRoute()
        runCatching {
            routeApplied = true
            manager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                manager.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                    ?.let { manager.setCommunicationDevice(it) }
            } else {
                @Suppress("DEPRECATION")
                manager.isSpeakerphoneOn = false
            }
        }.onFailure { failure -> logger.w(failure) { "Výstup zvuku nejde přepnout do sluchátka." } }
    }

    /**
     * Vrátí zvuk systému tak, jak byl: bez komunikačního režimu a bez vybraného zařízení.
     *
     * Jen když jsme ho sami přepnuli ([routeApplied]) — viz poznámka u toho pole.
     */
    private fun clearRoute() {
        val manager = audioManager ?: return
        if (!routeApplied) return
        routeApplied = false
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) manager.clearCommunicationDevice()
            manager.mode = AudioManager.MODE_NORMAL
        }.onFailure { failure -> logger.w(failure) { "Výstup zvuku nejde vrátit." } }
    }

    private fun requestFocus() {
        focusRequest?.let { audioManager?.requestAudioFocus(it) }
        if (music && !noisyRegistered) {
            ContextCompat.registerReceiver(
                context,
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            noisyRegistered = true
        }
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        if (noisyRegistered) {
            context.unregisterReceiver(noisyReceiver)
            noisyRegistered = false
        }
    }
}

/**
 * Atributy podle toho, kam se hraje.
 *
 * `USAGE_MEDIA` jde do reproduktoru a hlasitost se řídí médii; `USAGE_VOICE_COMMUNICATION` je
 * jediná cesta do sluchátka u ucha a řídí se hlasitostí hovoru — přesně jak to uživatel čeká,
 * když si telefon přiloží k uchu.
 */
private fun attributesFor(earpiece: Boolean, music: Boolean): AudioAttributes = AudioAttributes.Builder()
    .setUsage(if (earpiece) C.USAGE_VOICE_COMMUNICATION else C.USAGE_MEDIA)
    .setContentType(if (music) C.AUDIO_CONTENT_TYPE_MUSIC else C.AUDIO_CONTENT_TYPE_SPEECH)
    .build()

/** Tytéž atributy pro audio focus, který chce systémové `android.media.AudioAttributes`. */
private fun platformAttributesFor(earpiece: Boolean, music: Boolean): android.media.AudioAttributes =
    android.media.AudioAttributes.Builder()
        .setUsage(if (earpiece) android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION else android.media.AudioAttributes.USAGE_MEDIA)
        .setContentType(
            if (music) android.media.AudioAttributes.CONTENT_TYPE_MUSIC else android.media.AudioAttributes.CONTENT_TYPE_SPEECH,
        )
        .build()

/** Přehrávání ticha se dá přeskakovat jen tady — Media3 to umí, AVAudioPlayer ani desktop ne. */
actual val skipSilenceSupported: Boolean = true

/** Výstupy, u kterých nemá smysl přepínat do sluchátka telefonu. */
private val HEADPHONE_TYPES = setOf(
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_HEARING_AID,
)
