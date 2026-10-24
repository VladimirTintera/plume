package eu.tintera.plume.core.telegram

import kotlin.math.abs

/** Zpráva v řídkých pozicích: kolikátá od nejnovější ([position], od 0) a kdy přišla. */
data class MediaPosition(val position: Int, val messageId: Long, val timestamp: Int)

/**
 * Řídké pozice sdíleného obsahu (`getChatSparseMessagePositions`): celkový počet a VÝBĚR zpráv
 * s jejich pořadím, seřazený od nejnovější. Server vydá nejvýš pár tisíc bodů rozprostřených
 * po celé historii, takže mezi dvěma body bývají desítky zpráv — přesné místo dá až načtené okno.
 */
data class SharedMediaPositions(val totalCount: Int, val positions: List<MediaPosition>) {

    /** Bod nejblíž pozici [position] (0 = nejnovější); `null`, když server žádný nevydal. */
    fun nearest(position: Int): MediaPosition? = positions.minByOrNull { abs(it.position - position) }

    /**
     * Kolikátá od nejnovější je zpráva [messageId] — odhad z okolních bodů. Id zpráv rostou
     * s časem, takže se mezi dvěma sousedními body interpoluje podle id; zpráva novější než
     * první bod je na jeho pozici nebo před ní, starší než poslední za ním. `null` bez bodů.
     *
     * Je to odhad pro polohu úchytu při běžném rolování, ne přesné pořadí: mezi body jde
     * o desítky zpráv a úchyt na pár pixelech rozdíl nepozná.
     */
    fun estimatePosition(messageId: Long): Int? {
        if (positions.isEmpty()) return null
        val newer = positions.lastOrNull { it.messageId >= messageId } ?: return positions.first().position
        val older = positions.firstOrNull { it.messageId <= messageId } ?: return positions.last().position
        if (newer.messageId == older.messageId) return newer.position
        val fraction = (newer.messageId - messageId).toDouble() / (newer.messageId - older.messageId)
        return (newer.position + fraction * (older.position - newer.position)).toInt()
    }
}
