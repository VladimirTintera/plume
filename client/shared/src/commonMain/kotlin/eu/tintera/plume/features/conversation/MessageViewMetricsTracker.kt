package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.MessageViewMetrics
import kotlin.math.max
import kotlin.math.min

/**
 * Zpráva v jednom snímku seznamu: klíč položky a kde leží, v pixelech ve stejné soustavě jako
 * viditelná část seznamu ([MessageViewMetricsTracker.frame]).
 */
internal data class VisibleMessage(val key: Long, val start: Int, val end: Int)

/**
 * Počítá metriky zobrazení zpráv pro TDLib `sendMessageViewMetrics` a říká, KDY je poslat.
 *
 * TDLib volání přikazuje, když zpráva zmizí z obrazovky na víc než [hiddenGraceMs], viewport
 * zanikne ([flush]), nebo zobrazení přesáhne [maxViewMs] — pak se nahlásí a počítá se nové.
 * Krátké zmizení (průlet při scrollu tam a zpět) zobrazení nepřeruší.
 *
 * Čistá třída s časem zvenčí (monotónní milisekundy), aby šla testovat bez kompozice. Volá ji
 * `ConversationViewReporting` z hlavního vlákna, takže zámky nepotřebuje.
 *
 * - **Čas se připisuje mezi snímky**: co bylo vidět v předchozím snímku, bylo vidět až do
 *   tohoto. Seznam v klidu žádné snímky nevydává, proto obrazovka volá i [tick].
 * - **Aktivní čas** je ta část, která leží do [activeWindowMs] od poslední akce uživatele
 *   ([userAction]) — tak to TDLib definuje.
 * - **Viděný rozsah** je obal všech viděných úseků zprávy. Scroll je spojitý, takže díry mezi
 *   nimi v praxi nevznikají.
 */
internal class MessageViewMetricsTracker(
    private val hiddenGraceMs: Long = HIDDEN_GRACE_MS,
    private val maxViewMs: Long = MAX_VIEW_MS,
    private val activeWindowMs: Long = ACTIVE_WINDOW_MS,
) {
    private class Viewing {
        var visibleMs = 0L
        var activeMs = 0L
        var height = 0
        var viewport = 1
        var seenStart = Int.MAX_VALUE
        var seenEnd = Int.MIN_VALUE
        var visible = true
        var hiddenSince = 0L
    }

    private val viewings = LinkedHashMap<Long, Viewing>()
    private var lastAt: Long? = null
    private var lastActionAt: Long? = null

    /** Uživatel s obrazovkou něco dělá (posouvá seznam). */
    fun userAction(now: Long) {
        advance(now)
        lastActionAt = now
    }

    /**
     * Nový snímek seznamu: [visible] jsou zprávy, ze kterých je vidět aspoň pixel, viditelná část
     * seznamu je `[viewportStart, viewportEnd)`. Vrací metriky, které je potřeba poslat teď.
     */
    fun frame(now: Long, visible: List<VisibleMessage>, viewportStart: Int, viewportEnd: Int): List<MessageViewMetrics> {
        advance(now)
        val viewportHeight = (viewportEnd - viewportStart).coerceAtLeast(1)
        val seen = HashSet<Long>(visible.size)
        visible.forEach { message ->
            val height = message.end - message.start
            // Položka mimo viditelnou část (pod průsvitnou lištou, v rezervě seznamu) se nepočítá.
            if (height <= 0 || message.end <= viewportStart || message.start >= viewportEnd) return@forEach
            seen += message.key
            val viewing = viewings.getOrPut(message.key) { Viewing() }
            viewing.visible = true
            viewing.height = height
            viewing.viewport = viewportHeight
            viewing.seenStart = min(viewing.seenStart, max(viewportStart, message.start) - message.start)
            viewing.seenEnd = max(viewing.seenEnd, min(viewportEnd, message.end) - message.start)
        }
        viewings.forEach { (key, viewing) ->
            if (viewing.visible && key !in seen) {
                viewing.visible = false
                viewing.hiddenSince = now
            }
        }
        return due(now)
    }

    /** Uplynul čas bez nového snímku. Vrací metriky, které je potřeba poslat teď. */
    fun tick(now: Long): List<MessageViewMetrics> {
        advance(now)
        return due(now)
    }

    /** Viewport zaniká (zavřená obrazovka, aplikace v pozadí): všechno rozpracované jde ven. */
    fun flush(now: Long): List<MessageViewMetrics> {
        advance(now)
        val all = viewings.map { (key, viewing) -> viewing.metrics(key) }
        viewings.clear()
        lastAt = null
        return all.filter { it.timeInViewMs > 0 }
    }

    private fun advance(now: Long) {
        val last = lastAt
        lastAt = now
        if (last == null || now <= last) return
        val elapsed = now - last
        val active = lastActionAt?.let { action ->
            (min(now, action + activeWindowMs) - max(last, action)).coerceAtLeast(0)
        } ?: 0L
        viewings.values.forEach { viewing ->
            if (viewing.visible) {
                viewing.visibleMs += elapsed
                viewing.activeMs += active
            }
        }
    }

    private fun due(now: Long): List<MessageViewMetrics> {
        if (viewings.isEmpty()) return emptyList()
        val out = ArrayList<MessageViewMetrics>()
        val iterator = viewings.entries.iterator()
        while (iterator.hasNext()) {
            val (key, viewing) = iterator.next()
            when {
                !viewing.visible && now - viewing.hiddenSince > hiddenGraceMs -> {
                    out += viewing.metrics(key)
                    iterator.remove()
                }
                viewing.visibleMs >= maxViewMs -> {
                    out += viewing.metrics(key)
                    // Zpráva je vidět dál: nové zobrazení začíná teď. Viděný rozsah zůstává —
                    // seznam v klidu nový snímek nevydá, a zpráva leží pořád tam, kde ležela.
                    viewing.visibleMs = 0
                    viewing.activeMs = 0
                }
            }
        }
        return out
    }

    private fun Viewing.metrics(key: Long) = MessageViewMetrics(
        messageId = key,
        timeInViewMs = visibleMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        activeTimeInViewMs = activeMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        heightToViewportRatioPerMille = (height.toLong() * PER_MILLE / viewport).toInt(),
        seenRangeRatioPerMille = if (height <= 0 || seenEnd <= seenStart) 0
        else ((seenEnd - seenStart).toLong() * PER_MILLE / height).toInt().coerceIn(0, PER_MILLE),
    )

    companion object {
        /** Po jak dlouhém zmizení se zobrazení nahlásí (TDLib: „víc než 300 ms"). */
        const val HIDDEN_GRACE_MS = 300L

        /** Nejdelší zobrazení, po kterém se nahlásí, i když zpráva zůstává (TDLib: 5 minut). */
        const val MAX_VIEW_MS = 5 * 60_000L

        /** Jak dlouho po akci uživatele se čas počítá jako aktivní (TDLib: 15 s). */
        const val ACTIVE_WINDOW_MS = 15_000L

        private const val PER_MILLE = 1000
    }
}
