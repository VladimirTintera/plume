package eu.tintera.plume.core.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android: `MediaPlayer`. Hlasovky Telegramu jsou Ogg/Opus, a ten Android přehraje sám.
 *
 * `prepare()` čte hlavičku souboru z disku, proto mimo hlavní vlákno. Atributy říkají systému, že
 * jde o řeč — hlasitost se řídí médii, ne vyzváněním.
 */
actual class AudioEngine(private val context: Context) {

    private val audioManager = context.getSystemService<AudioManager>()
    private val logger = Logger.withTag("AudioEngine")

    private var player: MediaPlayer? = null

    /** Cesta k souboru; drží se kvůli přestavbě přehrávače při přepnutí do sluchátka. */
    private var path: String? = null

    /**
     * Rychlost se drží tady a nastavuje se až při [start].
     *
     * `MediaPlayer.playbackParams` na PŘIPRAVENÉM, ale nespuštěném přehrávači přehrávání ROZJEDE —
     * je to zdokumentované chování, ne chyba. Kdyby se rychlost nastavovala hned při přepnutí
     * čipu, rozehrála by pozastavenou hlasovku.
     */
    private var speed = 1f

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

    /** Poslední známá pozice; po dobu přestavby přehrávače není koho se zeptat. */
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
        player?.takeIf { it.isPlaying }?.let {
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
            .setAudioAttributes(attributesFor(earpiece = false, music = music))
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        val created = buildPlayer(path)
        player = created
        return created.duration.toLong()
    }

    actual fun start() {
        requestFocus()
        applyRoute()
        val current = player ?: return
        // Nastavení rychlosti přehrávání zároveň spustí; `start()` na běžícím přehrávači nevadí.
        current.playbackParams = current.playbackParams.setSpeed(speed)
        current.start()
    }

    actual fun pause() {
        player?.let {
            lastPosition = it.currentPosition.toLong()
            it.pause()
        }
        abandonFocus()
    }

    actual fun seekTo(positionMs: Long) {
        lastPosition = positionMs
        player?.seekTo(positionMs.toInt())
    }

    actual fun setSpeed(speed: Float) {
        this.speed = speed
        // Jen za běhu: na pozastaveném přehrávači by zápis `playbackParams` rozjel přehrávání.
        player?.takeIf { it.isPlaying }?.let { it.playbackParams = it.playbackParams.setSpeed(speed) }
    }

    /**
     * Přepne mezi reproduktorem a sluchátkem u ucha — a **postaví přehrávač znovu**.
     *
     * Jinak to nejde: do sluchátka se na Androidu dostane jen zvuk s `USAGE_VOICE_COMMUNICATION`,
     * a `MediaPlayer.setAudioAttributes` se smí volat jen PŘED `prepare()`. Samotné
     * `setCommunicationDevice` nestačí — to řídí jen komunikační zvuk, kdežto médium by hrálo
     * dál z reproduktoru.
     *
     * Pozice i to, jestli se zrovna hraje, se přenesou, takže přepnutí je slyšet jako krátké
     * zaváhání, ne jako skok na začátek. Soubor je lokální, takže `prepare()` je otázka
     * milisekund — přesto běží mimo hlavní vlákno, jako v [load].
     */
    actual suspend fun setEarpiece(enabled: Boolean) {
        if (enabled == earpiece) return
        val source = path ?: return
        earpiece = enabled

        val previous = player
        val resume = previous?.isPlaying == true
        val position = previous?.currentPosition?.toLong() ?: lastPosition
        lastPosition = position
        player = null
        previous?.setOnCompletionListener(null)
        previous?.release()

        val rebuilt = try {
            buildPlayer(source)
        } catch (failure: Throwable) {
            logger.w(failure) { "Přehrávač po přepnutí výstupu nejde postavit." }
            // Bez přehrávače by hlasovka tiše umřela; vrátí se výstup a stav se promítne ven.
            earpiece = false
            clearRoute()
            onInterrupted?.invoke()
            return
        }
        player = rebuilt
        applyRoute()
        rebuilt.seekTo(position.toInt())
        if (resume) {
            rebuilt.playbackParams = rebuilt.playbackParams.setSpeed(speed)
            rebuilt.start()
        }
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

    actual val positionMs: Long get() = player?.currentPosition?.toLong() ?: lastPosition

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

    private suspend fun buildPlayer(source: String): MediaPlayer {
        val created = MediaPlayer().apply { setAudioAttributes(attributesFor(earpiece, music)) }
        try {
            withContext(Dispatchers.IO) {
                created.setDataSource(source)
                created.prepare()
            }
        } catch (failure: Throwable) {
            created.release()
            throw failure
        }
        created.setOnCompletionListener {
            abandonFocus()
            clearRoute()
            onCompleted?.invoke()
        }
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
    .setUsage(if (earpiece) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA)
    .setContentType(if (music) AudioAttributes.CONTENT_TYPE_MUSIC else AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

/** Výstupy, u kterých nemá smysl přepínat do sluchátka telefonu. */
private val HEADPHONE_TYPES = setOf(
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_HEARING_AID,
)
