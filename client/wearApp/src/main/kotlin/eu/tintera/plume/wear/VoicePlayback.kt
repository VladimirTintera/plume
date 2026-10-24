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
                setOnPreparedListener { it.start() }
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
        player?.release()
        player = null
        playingId = null
    }
}
