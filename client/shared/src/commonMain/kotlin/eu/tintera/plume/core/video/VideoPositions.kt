package eu.tintera.plume.core.video

import eu.tintera.plume.core.settings.AppSettings
import kotlin.time.Clock
import kotlinx.coroutines.flow.StateFlow

/**
 * Pamatované pozice videí a dlouhých nahrávek (uložené v [AppSettings]), přes všechny účty — klíč
 * je soubor. Zvuk a video sdílí jeden seznam: klíč je soubor, takže se nepotkají, a druhý seznam
 * by byl táž věc podruhé.
 */
class VideoPositions(private val settings: AppSettings) {

    val all: StateFlow<List<VideoPosition>> get() = settings.videoPositions

    fun of(key: String): VideoPosition? = settings.videoPositions.value.firstOrNull { it.key == key }

    /** Zapíše pozici, nebo ji zapomene (dohráno, začátek, krátké video) — viz [resumePosition]. */
    fun record(
        key: String,
        positionMs: Long,
        durationMs: Long,
        minDurationMs: Long = MIN_RESUME_DURATION_MS,
        source: VideoSource? = null,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        settings.updateVideoPositions { it.withPosition(key, positionMs, durationMs, minDurationMs, source, now) }
    }

    /** Zapomene pozici (Rozkoukaná videa → Odebrat); vrací ji i s pořadím kvůli „Vrátit". */
    fun remove(key: String): RemovedPosition? {
        val list = settings.videoPositions.value
        val index = list.indexOfFirst { it.key == key }
        if (index < 0) return null
        settings.updateVideoPositions { current -> current.filterNot { it.key == key } }
        return RemovedPosition(list[index], index)
    }

    /** Vrátí odebranou pozici na její místo — jen když se mezitím nezapsala nová. */
    fun restore(removed: RemovedPosition) {
        settings.updateVideoPositions { it.withRestored(removed) }
    }
}
