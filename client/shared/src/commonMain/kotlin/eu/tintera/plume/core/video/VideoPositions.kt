package eu.tintera.plume.core.video

import eu.tintera.plume.core.settings.AppSettings
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Kde uživatel přestal video sledovat — nebo poslouchat dlouhou nahrávku (hlasovku, audio,
 * podcast; viz [MIN_RESUME_AUDIO_DURATION_MS]). [key] je trvalé id souboru na serveru
 * (`RemoteFile.uniqueId`), ne id zprávy: přeposlané video je týž soubor a pokračuje se v něm
 * odkudkoli. [durationMs] kvůli proužku „zhlédnuto" v bublině.
 */
@Serializable
data class VideoPosition(val key: String, val positionMs: Long, val durationMs: Long) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * Pozice, od které stojí za to příště pokračovat; `null` = zapomenout.
 *
 * - **Jen delší videa** ([minDurationMs], u videa [MIN_RESUME_DURATION_MS], u zvuku
 *   [MIN_RESUME_AUDIO_DURATION_MS]): krátké se líp pustí znovu celé a pamatovat si každý klip by
 *   z bublin udělalo samé proužky.
 * - **Začátek ne** ([MIN_RESUME_POSITION_MS]): pár vteřin je spíš omylem otevřené video.
 * - **Konec ne** ([END_MARGIN_MS]): kdo dokoukal (titulky), chce příště od začátku, ne poslední
 *   vteřiny.
 */
fun resumePosition(positionMs: Long, durationMs: Long, minDurationMs: Long = MIN_RESUME_DURATION_MS): Long? = positionMs.takeIf {
    durationMs >= minDurationMs &&
        positionMs >= MIN_RESUME_POSITION_MS &&
        positionMs <= durationMs - END_MARGIN_MS
}

/**
 * Seznam s novou pozicí pro [key]: nejnovější vpředu, nejvýš [MAX_VIDEO_POSITIONS] (nejstarší
 * odpadají), a když pozici pamatovat nemá cenu ([resumePosition]), záznam zmizí.
 */
fun List<VideoPosition>.withPosition(
    key: String,
    positionMs: Long,
    durationMs: Long,
    minDurationMs: Long = MIN_RESUME_DURATION_MS,
): List<VideoPosition> {
    val others = filterNot { it.key == key }
    val resume = resumePosition(positionMs, durationMs, minDurationMs) ?: return others
    return (listOf(VideoPosition(key, resume, durationMs)) + others).take(MAX_VIDEO_POSITIONS)
}

/**
 * Pamatované pozice videí a dlouhých nahrávek (uložené v [AppSettings]), přes všechny účty — klíč
 * je soubor. Zvuk a video sdílí jeden seznam: klíč je soubor, takže se nepotkají, a druhý seznam
 * by byl táž věc podruhé.
 */
class VideoPositions(private val settings: AppSettings) {

    val all: StateFlow<List<VideoPosition>> get() = settings.videoPositions

    fun of(key: String): VideoPosition? = settings.videoPositions.value.firstOrNull { it.key == key }

    /** Zapíše pozici, nebo ji zapomene (dohráno, začátek, krátké video) — viz [resumePosition]. */
    fun record(key: String, positionMs: Long, durationMs: Long, minDurationMs: Long = MIN_RESUME_DURATION_MS) {
        settings.updateVideoPositions { it.withPosition(key, positionMs, durationMs, minDurationMs) }
    }
}

const val MIN_RESUME_DURATION_MS = 2 * 60 * 1000L

/**
 * Od jaké délky si pamatovat pozici ve zvuku (hlasovka, audio). Delší než u videa: dvouminutová
 * hlasovka se pustí znovu celá, kdežto u přednášky nebo podcastu se čeká, že se k ní člověk vrátí.
 */
const val MIN_RESUME_AUDIO_DURATION_MS = 10 * 60 * 1000L
const val MIN_RESUME_POSITION_MS = 10 * 1000L
const val END_MARGIN_MS = 15 * 1000L
const val MAX_VIDEO_POSITIONS = 200
