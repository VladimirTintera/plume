package eu.tintera.plume.core.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android: `MediaPlayer`. Hlasovky Telegramu jsou Ogg/Opus, a ten Android přehraje sám.
 *
 * `prepare()` čte hlavičku souboru z disku, proto mimo hlavní vlákno. Atributy říkají systému, že
 * jde o řeč — hlasitost se řídí médii, ne vyzváněním.
 */
actual class AudioEngine actual constructor() {

    private val player = MediaPlayer().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
    }

    actual suspend fun load(path: String): Long {
        withContext(Dispatchers.IO) {
            player.setDataSource(path)
            player.prepare()
        }
        player.setOnCompletionListener { onCompleted?.invoke() }
        return player.duration.toLong()
    }

    actual fun start() = player.start()

    actual fun pause() = player.pause()

    actual fun seekTo(positionMs: Long) = player.seekTo(positionMs.toInt())

    actual val positionMs: Long get() = player.currentPosition.toLong()

    actual var onCompleted: (() -> Unit)? = null

    actual fun release() {
        onCompleted = null
        player.release()
    }
}
