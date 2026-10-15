package eu.tintera.plume.core.video

import eu.tintera.plume.core.settings.AppSettings
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Kde uživatel přestal video sledovat. [key] je trvalé id souboru na serveru
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
 * - **Jen delší videa** ([MIN_RESUME_DURATION_MS]): krátké se líp pustí znovu celé a pamatovat
 *   si každý klip by z bublin udělalo samé proužky.
 * - **Začátek ne** ([MIN_RESUME_POSITION_MS]): pár vteřin je spíš omylem otevřené video.
 * - **Konec ne** ([END_MARGIN_MS]): kdo dokoukal (titulky), chce příště od začátku, ne poslední
 *   vteřiny.
 */
fun resumePosition(positionMs: Long, durationMs: Long): Long? = positionMs.takeIf {
    durationMs >= MIN_RESUME_DURATION_MS &&
        positionMs >= MIN_RESUME_POSITION_MS &&
        positionMs <= durationMs - END_MARGIN_MS
}

/**
 * Seznam s novou pozicí pro [key]: nejnovější vpředu, nejvýš [MAX_VIDEO_POSITIONS] (nejstarší
 * odpadají), a když pozici pamatovat nemá cenu ([resumePosition]), záznam zmizí.
 */
fun List<VideoPosition>.withPosition(key: String, positionMs: Long, durationMs: Long): List<VideoPosition> {
    val others = filterNot { it.key == key }
    val resume = resumePosition(positionMs, durationMs) ?: return others
    return (listOf(VideoPosition(key, resume, durationMs)) + others).take(MAX_VIDEO_POSITIONS)
}

/** Pamatované pozice videí (uložené v [AppSettings]), přes všechny účty — klíč je soubor. */
class VideoPositions(private val settings: AppSettings) {

    val all: StateFlow<List<VideoPosition>> get() = settings.videoPositions

    fun of(key: String): VideoPosition? = settings.videoPositions.value.firstOrNull { it.key == key }

    /** Zapíše pozici, nebo ji zapomene (dohráno, začátek, krátké video) — viz [resumePosition]. */
    fun record(key: String, positionMs: Long, durationMs: Long) {
        settings.updateVideoPositions { it.withPosition(key, positionMs, durationMs) }
    }
}

const val MIN_RESUME_DURATION_MS = 2 * 60 * 1000L
const val MIN_RESUME_POSITION_MS = 10 * 1000L
const val END_MARGIN_MS = 15 * 1000L
const val MAX_VIDEO_POSITIONS = 200
