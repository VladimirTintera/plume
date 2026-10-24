package eu.tintera.plume.wear

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Přehrávání hlasovek na hodinkách: nejvýš jedna naráz, systémový [MediaPlayer]. Ogg/Opus, ve
 * kterém Telegram hlasovky posílá, přehraje Android sám od verze 10 (hodinky mají 11+); Media3
 * z telefonu sem nejde. Hodinky bez reproduktoru hrají do připojených sluchátek, jako každý zvuk.
 */
class VoicePlayback {

    /** Zpráva, jejíž hlasovka právě hraje (`null` = nic). */
    var playingId by mutableStateOf<Long?>(null)
        private set

    private var player: MediaPlayer? = null

    // `duration` a `currentPosition` před přípravou hlásí chybu stavu (a do logu `MediaPlayer`).
    private var prepared = false

    /**
     * Kolik z hlasovky už dohrálo (0..1), a kolik vteřin. Čte se ve smyčce po snímcích, dokud
     * hlasovka hraje — `MediaPlayer` průběh neohlašuje, jen se dá zjistit.
     */
    fun progress(): Float {
        val current = player?.takeIf { prepared } ?: return 0f
        val duration = current.duration.takeIf { it > 0 } ?: return 0f
        return (current.currentPosition.toFloat() / duration).coerceIn(0f, 1f)
    }

    fun elapsedSeconds(): Int = player?.takeIf { prepared }?.currentPosition?.div(1000) ?: 0

    fun play(messageId: Long, path: String, onError: (Throwable?) -> Unit) {
        stop()
        playingId = messageId
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                setDataSource(path)
                setOnPreparedListener {
                    prepared = true
                    it.start()
                }
                // `this@VoicePlayback`: uvnitř `apply` by holé `stop()` bylo MediaPlayer.stop() —
                // přehrávač by se zastavil, ale stav by zůstal na „hraje" (ověřeno na emulátoru).
                setOnCompletionListener { this@VoicePlayback.stop() }
                setOnErrorListener { _, what, extra ->
                    this@VoicePlayback.stop()
                    onError(IllegalStateException("MediaPlayer chyba $what/$extra"))
                    true
                }
                prepareAsync()
            }
        }.onFailure {
            playingId = null
            onError(it)
        }.getOrNull()
    }

    fun stop() {
        prepared = false
        player?.release()
        player = null
        playingId = null
    }
}
