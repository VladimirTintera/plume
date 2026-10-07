package eu.tintera.plume.core.ui

/**
 * Úsek videa, který se stane animovanou profilovkou, a snímek, ze kterého server vyrobí její
 * statickou podobu (`mainFrameTimestamp`). Všechno v milisekundách od začátku zdrojového videa.
 *
 * Úsek má pevnou délku [segmentMs] (nejvýš [PROFILE_VIDEO_MAX_SECONDS] s, kratší video celé)
 * a posouvá se jako celek; snímek leží vždycky UVNITŘ úseku — mimo něj by ho v nahrané
 * animaci nebylo z čeho vzít.
 */
data class VideoTrim(
    val durationMs: Long,
    val startMs: Long = 0,
    val coverMs: Long = 0,
) {
    val segmentMs: Long get() = minOf(durationMs, PROFILE_VIDEO_MAX_SECONDS * 1000L).coerceAtLeast(0)

    val endMs: Long get() = startMs + segmentMs

    /** Úsek jde posouvat, jen když je video delší než strop. */
    val canMove: Boolean get() = durationMs > segmentMs

    /**
     * Nejpozdější snímek úseku. Kousek před jeho koncem: přesně na konci už v animaci snímek být
     * nemusí (poslední snímek začíná dřív, než video skončí).
     */
    val lastCoverMs: Long get() = maxOf(startMs, endMs - LAST_FRAME_MARGIN_MS)

    /**
     * Úsek posunutý na [start]. Snímek jede s ním — kdo si vybral třetí vteřinu úseku, chce ji
     * i po posunu —, a když by vypadl ven, přitáhne se k okraji.
     */
    fun movedTo(start: Long): VideoTrim {
        val clamped = start.coerceIn(0, (durationMs - segmentMs).coerceAtLeast(0))
        val moved = copy(startMs = clamped)
        return moved.copy(coverMs = (coverMs + clamped - startMs).coerceIn(clamped, moved.lastCoverMs))
    }

    /** Snímek pro statickou fotku, sevřený do úseku. */
    fun withCover(cover: Long): VideoTrim = copy(coverMs = cover.coerceIn(startMs, lastCoverMs))

    /** Poloha snímku v nahrané animaci (ta začíná na [startMs]) — tak ji chce TDLib. */
    val mainFrameSeconds: Double get() = (coverMs - startMs) / 1000.0

    private companion object {
        const val LAST_FRAME_MARGIN_MS = 100L
    }
}
