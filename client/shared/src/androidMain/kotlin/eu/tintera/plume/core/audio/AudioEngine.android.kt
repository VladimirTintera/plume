package eu.tintera.plume.core.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import androidx.core.content.getSystemService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android: `MediaPlayer`. Hlasovky Telegramu jsou Ogg/Opus, a ten Android přehraje sám.
 *
 * `prepare()` čte hlavičku souboru z disku, proto mimo hlavní vlákno. Atributy říkají systému, že
 * jde o řeč — hlasitost se řídí médii, ne vyzváněním.
 */
actual class AudioEngine(context: Context) {

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val player = MediaPlayer().apply { setAudioAttributes(attributes) }

    private val audioManager = context.getSystemService<AudioManager>()

    /**
     * Rychlost se drží tady a nastavuje se až při [start].
     *
     * `MediaPlayer.playbackParams` na PŘIPRAVENÉM, ale nespuštěném přehrávači přehrávání ROZJEDE —
     * je to zdokumentované chování, ne chyba. Kdyby se rychlost nastavovala hned při přepnutí
     * čipu, rozehrála by pozastavenou hlasovku.
     */
    private var speed = 1f

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
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
            -> if (player.isPlaying) {
                player.pause()
                onInterrupted?.invoke()
            }
        }
    }

    /** `AudioFocusRequest` je od API 26, tedy od `minSdk` projektu — starší cesta tu není potřeba. */
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener(focusListener)
        .build()

    actual suspend fun load(path: String): Long {
        withContext(Dispatchers.IO) {
            player.setDataSource(path)
            player.prepare()
        }
        player.setOnCompletionListener {
            abandonFocus()
            onCompleted?.invoke()
        }
        return player.duration.toLong()
    }

    actual fun start() {
        requestFocus()
        // Nastavení rychlosti přehrávání zároveň spustí; `start()` na běžícím přehrávači nevadí.
        player.playbackParams = player.playbackParams.setSpeed(speed)
        player.start()
    }

    actual fun pause() {
        player.pause()
        abandonFocus()
    }

    actual fun seekTo(positionMs: Long) = player.seekTo(positionMs.toInt())

    actual fun setSpeed(speed: Float) {
        this.speed = speed
        // Jen za běhu: na pozastaveném přehrávači by zápis `playbackParams` rozjel přehrávání.
        if (player.isPlaying) player.playbackParams = player.playbackParams.setSpeed(speed)
    }

    actual val positionMs: Long get() = player.currentPosition.toLong()

    actual var onCompleted: (() -> Unit)? = null

    actual var onInterrupted: (() -> Unit)? = null

    actual fun release() {
        onCompleted = null
        onInterrupted = null
        abandonFocus()
        player.release()
    }

    private fun requestFocus() {
        audioManager?.requestAudioFocus(focusRequest)
    }

    private fun abandonFocus() {
        audioManager?.abandonAudioFocusRequest(focusRequest)
    }
}
