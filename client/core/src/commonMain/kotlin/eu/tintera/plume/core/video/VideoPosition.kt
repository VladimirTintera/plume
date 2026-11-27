package eu.tintera.plume.core.video

import kotlinx.serialization.Serializable

/*
 * Pamatované pozice přehrávání jako data a čisté funkce. V `core`, protože je sdílí telefon
 * (`VideoPositions` nad `AppSettings` ve `shared`) s televizí, a tvar [VideoPosition] je i formát
 * synchronizace mezi nimi (`CloudGroups.kt`).
 */

/**
 * Kde uživatel přestal video sledovat — nebo poslouchat dlouhou nahrávku (hlasovku, audio,
 * podcast; viz [MIN_RESUME_AUDIO_DURATION_MS]). [key] je trvalé id souboru na serveru
 * (`RemoteFile.uniqueId`), ne id zprávy: přeposlané video je týž soubor a pokračuje se v něm
 * odkudkoli. [durationMs] kvůli proužku „zhlédnuto" v bublině.
 */
@Serializable
data class VideoPosition(
    val key: String,
    val positionMs: Long,
    val durationMs: Long,
    /**
     * Odkud se video naposledy pouštělo (účet, chat, zpráva) — pro seznam Rozkoukaná videa.
     * `null` u zvuku, u videa mimo zprávu chatu (příběh, reklama, anketa, článek) a u záznamů
     * starších než ten seznam: z id souboru zprávu dohledat nejde, TDLib na to nemá dotaz.
     */
    val source: VideoSource? = null,
    /**
     * Kdy se pozice naposledy zapsala (ms od epochy); `0` u záznamů starších než synchronizace.
     * Při slučování dvou zařízení vyhraje novější zápis téhož souboru.
     */
    val at: Long = 0,
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * Zpráva s videem. [accountId] je id uživatele účtu (Uložené zprávy, jako u záložek), ne slot:
 * slot se po odhlášení přidělí dalšímu účtu, id uživatele ne.
 */
@Serializable
data class VideoSource(val accountId: Long, val chatId: Long, val messageId: Long)

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
 *
 * Bez [source] zůstane ten, který záznam už měl: týž soubor se dá pustit i odjinud (bublina
 * přeposlané zprávy v anketě, článek), a seznam by o video přišel.
 */
fun List<VideoPosition>.withPosition(
    key: String,
    positionMs: Long,
    durationMs: Long,
    minDurationMs: Long = MIN_RESUME_DURATION_MS,
    source: VideoSource? = null,
    at: Long = 0,
): List<VideoPosition> {
    val previous = firstOrNull { it.key == key }
    val others = filterNot { it.key == key }
    val resume = resumePosition(positionMs, durationMs, minDurationMs) ?: return others
    return (listOf(VideoPosition(key, resume, durationMs, source ?: previous?.source, at)) + others).take(MAX_VIDEO_POSITIONS)
}

/** Zapomenutá pozice a kde v seznamu stála. */
data class RemovedPosition(val position: VideoPosition, val index: Int)

/** Seznam s vrácenou pozicí na původním místě; novější zápis téhož souboru vyhrává. */
fun List<VideoPosition>.withRestored(removed: RemovedPosition): List<VideoPosition> {
    if (any { it.key == removed.position.key }) return this
    val index = removed.index.coerceIn(0, size)
    return (take(index) + removed.position + drop(index)).take(MAX_VIDEO_POSITIONS)
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
