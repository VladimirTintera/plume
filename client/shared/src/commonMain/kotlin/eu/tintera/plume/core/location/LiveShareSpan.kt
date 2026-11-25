package eu.tintera.plume.core.location

import eu.tintera.plume.core.telegram.ActiveLiveLocation

/**
 * Doba sdílení živé polohy pro průběh v notifikaci (Live Update na Androidu 16): od začátku do
 * konce sdílení, které skončí POSLEDNÍ — notifikace mluví o sdílení jako celku a „Zastavit"
 * zastaví všechna. `null`, když se nesdílí, nebo když některé sdílení trvá „dokud nevypnu":
 * pak konec není a průběh by lhal.
 */
data class LiveShareSpan(val startSeconds: Long, val endSeconds: Long) {

    /** Uběhlá část sdílení v [max] dílcích (pro `ProgressStyle`), v mezích 0..[max]. */
    fun progress(nowSeconds: Long, max: Int): Int {
        val total = (endSeconds - startSeconds).coerceAtLeast(1)
        val elapsed = (nowSeconds - startSeconds).coerceIn(0, total)
        return (elapsed * max / total).toInt()
    }
}

fun liveShareSpan(active: List<ActiveLiveLocation>): LiveShareSpan? {
    if (active.isEmpty() || active.any { it.until == Long.MAX_VALUE }) return null
    val last = active.maxBy { it.until }
    return LiveShareSpan(startSeconds = last.until - last.livePeriod, endSeconds = last.until)
}
