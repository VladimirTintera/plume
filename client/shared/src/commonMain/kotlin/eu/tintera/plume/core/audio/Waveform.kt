package eu.tintera.plume.core.audio

/**
 * Rozbalí waveform hlasovky, jak ho posílá Telegram: hodnoty 0–31 po **5 bitech**, sbalené za sebou
 * od nejnižšího bitu. Poslední neúplná pětice se zahazuje.
 */
fun decodeWaveform(packed: ByteArray): List<Int> {
    val count = packed.size * 8 / 5
    return List(count) { index ->
        val bit = index * 5
        val byte = bit / 8
        val low = packed[byte].toInt() and 0xFF
        val high = packed.getOrNull(byte + 1)?.toInt()?.and(0xFF) ?: 0
        ((low or (high shl 8)) shr (bit % 8)) and 0x1F
    }
}

/**
 * Převzorkuje waveform na [bars] sloupců (průměrem, resp. opakováním) a znormuje do 0..1 podle
 * nejvyšší hodnoty. Telegram posílá kolem stovky hodnot; do bubliny se jich vejde méně.
 */
fun resampleWaveform(values: List<Int>, bars: Int): List<Float> {
    if (bars <= 0) return emptyList()
    if (values.isEmpty()) return List(bars) { 0f }
    val resampled = List(bars) { bar ->
        val start = bar * values.size / bars
        val end = maxOf(start + 1, (bar + 1) * values.size / bars)
        values.subList(start, end.coerceAtMost(values.size)).average().toFloat()
    }
    val peak = resampled.max()
    return if (peak <= 0f) resampled.map { 0f } else resampled.map { it / peak }
}
