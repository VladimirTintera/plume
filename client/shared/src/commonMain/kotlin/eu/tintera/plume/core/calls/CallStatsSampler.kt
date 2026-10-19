package eu.tintera.plume.core.calls

/**
 * Kumulativní čísla přenosu, jak je umí platforma. `null` = tohle číslo platforma nezná.
 *
 * [rxBytes] / [txBytes] jsou přenesená data celé aplikace (`TrafficStats` pro vlastní uid),
 * ne jen hovoru — TDLib si mezitím může něco stáhnout. Hlas má kolem 5 kB/s; zaseknutí se
 * pozná jako propad příjmu skoro k nule, stahování ho nanejvýš zakryje, nikdy nevyrobí.
 * [playedSeconds] / [capturedSeconds] je čas proudu podle ntgcalls (`NTgCalls.time`): když
 * stojí, zvuk netekl, ať ukazuje síť cokoli.
 */
data class CallMediaStats(
    val rxBytes: Long?,
    val txBytes: Long?,
    val playedSeconds: Long?,
    val capturedSeconds: Long?,
)

/**
 * Z čísel po vteřinách dělá řádky deníku (anglicky — je pro vývojáře a `verifyNoHardcodedText`
 * pouští český text jen v lozích): souhrn za [SUMMARY_SECONDS] s a hned událost, když
 * se příjem zasekne (aspoň [STALL_SECONDS] s pod [STALL_BYTES_PER_SECOND]) a když se rozjede.
 * Mezera mezi vzorky přes [GAP_MS] znamená, že proces stál — zmrazený, nebo přetížený — a to
 * je stopa sama o sobě (podezření „zhasnutá obrazovka hovor zastaví").
 *
 * Čistá logika bez hodin a souborů — čas a zápis dodává volající, takže jde testovat.
 */
internal class CallStatsSampler(private val write: (String) -> Unit) {

    private var last: CallMediaStats? = null
    private var lastAtMs = 0L

    private var stalledFor = 0
    private var stallReported = false

    private var seconds = 0
    private var rxSum = 0L
    private var rxMin = Long.MAX_VALUE
    private var txSum = 0L
    private var played = 0L
    private var captured = 0L
    private var knowsPlayback = false

    /** Další vzorek; [atMs] je monotónní čas od začátku přenosu. */
    fun sample(stats: CallMediaStats, atMs: Long) {
        val previous = last
        last = stats
        val elapsed = atMs - lastAtMs
        lastAtMs = atMs
        if (previous == null) return
        if (elapsed > GAP_MS) write("Sampling stalled for $elapsed ms (process paused?)")
        // Na vteřinu, ať mezera nevypadá jako nával dat.
        val perSecond = elapsed.coerceAtLeast(1) / 1000.0
        val rx = delta(previous.rxBytes, stats.rxBytes)?.let { (it / perSecond).toLong() }
        val tx = delta(previous.txBytes, stats.txBytes)?.let { (it / perSecond).toLong() }
        val playedNow = delta(previous.playedSeconds, stats.playedSeconds)
        val capturedNow = delta(previous.capturedSeconds, stats.capturedSeconds)

        val stalled = rx != null && rx < STALL_BYTES_PER_SECOND
        if (stalled) {
            stalledFor++
            if (stalledFor >= STALL_SECONDS && !stallReported) {
                stallReported = true
                write("STALL: receiving $rx B/s${if (playedNow == 0L) ", playback stopped" else ""}")
            }
        } else {
            if (stallReported) write("Stall over after $stalledFor s, receiving $rx B/s")
            stalledFor = 0
            stallReported = false
        }

        seconds++
        rx?.let {
            rxSum += it
            rxMin = minOf(rxMin, it)
        }
        tx?.let { txSum += it }
        playedNow?.let {
            played += it
            knowsPlayback = true
        }
        capturedNow?.let { captured += it }
        if (seconds >= SUMMARY_SECONDS) {
            val parts = buildList {
                if (rx != null) add("rx ${rxSum / seconds} B/s (min $rxMin)")
                if (tx != null) add("tx ${txSum / seconds} B/s")
                if (knowsPlayback) add("played +$played s, captured +$captured s")
            }
            if (parts.isNotEmpty()) write("${seconds} s: ${parts.joinToString(", ")}")
            seconds = 0
            rxSum = 0
            rxMin = Long.MAX_VALUE
            txSum = 0
            played = 0
            captured = 0
        }
    }

    private fun delta(before: Long?, after: Long?): Long? =
        if (before == null || after == null || after < before) null else after - before

    companion object {
        const val SUMMARY_SECONDS = 5
        const val STALL_SECONDS = 2
        const val STALL_BYTES_PER_SECOND = 1_000L
        const val GAP_MS = 2_500L
    }
}
