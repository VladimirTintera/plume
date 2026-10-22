package eu.tintera.plume.features.media

import eu.tintera.plume.core.telegram.VideoAd
import eu.tintera.plume.core.telegram.VideoAds

/**
 * Kdy ukázat kterou reklamu nad videem, podle [specifikace](https://core.telegram.org/api/sponsored-messages):
 *
 * - první po `startDelay` přehrávání, další `betweenDelay` po skrytí předchozí, v pořadí od serveru;
 * - každá nejvýš `maxDisplayDuration`, pak se skryje sama; zavřít ji uživatel smí až po
 *   `minDisplayDuration`;
 * - **počítá se jen čas přehrávání** — volající sem posílá ([advance]) jen úseky, kdy video hrálo.
 *   Pozastavení, galerie, zavřené ovládání tedy časovače zastaví samy.
 *
 * Plán patří celému prohlížeči, ne jednomu videu: odlistování na další video téhož kanálu ho
 * nezačíná znovu (tak to specifikace chce).
 */
class VideoAdSchedule(private val ads: VideoAds) {
    /** Reklama, která je na řadě (zobrazená, nebo čekající). */
    private var index = 0
    private var showing = false
    /** Kolik ms přehrávání uběhlo v současné fázi (čekání, nebo zobrazení). */
    private var elapsedMs = 0L
    private var waitMs = ads.startDelaySeconds * 1000L

    /** Zobrazená reklama; `null` = teď žádná. */
    val current: VideoAd? get() = ads.ads.getOrNull(index)?.takeIf { showing }

    /** Smí ji uživatel zavřít (uběhlo `minDisplayDuration`)? */
    val canClose: Boolean get() = current?.let { elapsedMs >= it.minDisplaySeconds * 1000L } == true

    /** Všechny reklamy už byly. */
    val finished: Boolean get() = index >= ads.ads.size

    /** Uběhlo [playedMs] přehrávání. */
    fun advance(playedMs: Long) {
        var left = playedMs
        while (left > 0 && !finished) {
            if (!showing) {
                val remaining = waitMs - elapsedMs
                if (left < remaining) {
                    elapsedMs += left
                    return
                }
                left -= remaining
                showing = true
                elapsedMs = 0
            } else {
                val remaining = ads.ads[index].maxDisplaySeconds * 1000L - elapsedMs
                if (left < remaining) {
                    elapsedMs += left
                    return
                }
                left -= remaining
                hide()
            }
        }
    }

    /** Uživatel reklamu zavřel; jde to až po `minDisplayDuration` ([canClose]). */
    fun close(): Boolean {
        if (!canClose) return false
        hide()
        return true
    }

    /** Nahlášená reklama zmizí hned, bez ohledu na minimum — je to odpověď na akci uživatele. */
    fun drop(uniqueId: Long) {
        if (current?.uniqueId == uniqueId) hide()
    }

    private fun hide() {
        showing = false
        index++
        elapsedMs = 0
        waitMs = ads.betweenDelaySeconds * 1000L
    }
}
