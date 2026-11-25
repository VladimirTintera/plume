package eu.tintera.plume.core.ui

import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

/**
 * `AudioManager.STREAM_MUSIC` — hlasitost médií, kterou mění i tlačítka na boku. Oprávnění
 * netřeba; bez příznaku `FLAG_SHOW_UI`, ukazatel kreslí přehrávač sám (pilulka nahoře).
 */
@Composable
actual fun rememberMediaVolume(): MediaVolume? {
    val context = LocalContext.current
    return remember(context) {
        context.getSystemService(AudioManager::class.java)
            ?.takeIf { !it.isVolumeFixed }
            ?.let(::StreamVolume)
    }
}

private class StreamVolume(private val audio: AudioManager) : MediaVolume {
    private val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)

    override var level by mutableFloatStateOf(current())
        private set

    override fun set(level: Float) {
        this.level = level.coerceIn(0f, 1f)
        val index = (this.level * max).roundToInt()
        if (index != audio.getStreamVolume(AudioManager.STREAM_MUSIC)) {
            // Výjimku hlásí jen zvláštní režimy (nerušit u některých výrobců); gesto pak nic neudělá.
            runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, index, 0) }
        }
    }

    override fun sync() {
        level = current()
    }

    private fun current(): Float = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
}
