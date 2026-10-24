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
 * Převzorkuje waveform na [bars] sloupců (průměrem, resp. opakováním) a převede na 0..1 podle
 * [WAVEFORM_MAX]. Telegram posílá kolem stovky hodnot; do bubliny se jich vejde méně.
 *
 * **Neškáluje se na špičku té konkrétní hlasovky.** Dřív se ano — každá se roztáhla tak, aby její
 * nejhlasitější místo sáhlo na strop —, jenže tím měla každá hlasovka v konverzaci jinak vysoké
 * sloupce: hlasitá vyšla jako souvislý blok u stropu, tichá jako členitý tvar, a z waveformu se
 * nedalo přečíst nic než „tahle je jiná než ta nad ní". Hodnoty 0–31 od Telegramu jsou absolutní,
 * takže stačí je vydělit stropem a hlasovky jsou porovnatelné mezi sebou.
 *
 * Na straně NAHRÁVÁNÍ se normalizuje dál ([encodeWaveform]): mikrofon vrací úroveň v měřítku,
 * které si každá platforma určuje sama (Android `maxAmplitude`, iOS `averagePower` v dB), takže
 * absolutní hodnota z něj nic neznamená — a nenormovaná tichá nahrávka by odešla jako čára.
 */
fun resampleWaveform(values: List<Int>, bars: Int): List<Float> {
    if (bars <= 0) return emptyList()
    if (values.isEmpty()) return List(bars) { 0f }
    return List(bars) { bar ->
        val start = bar * values.size / bars
        val end = maxOf(start + 1, (bar + 1) * values.size / bars)
        val average = values.subList(start, end.coerceAtMost(values.size)).average().toFloat()
        (average / WAVEFORM_MAX).coerceIn(0f, 1f)
    }
}

/**
 * Zabalí úrovně hlasitosti z nahrávání (0..1) do formátu Telegramu: [WAVEFORM_VALUES] hodnot
 * 0–31 po 5 bitech — opak [decodeWaveform]. Úrovně se převzorkují a znormují podle špičky,
 * aby i tichá nahrávka měla čitelný tvar.
 */
fun encodeWaveform(levels: List<Float>): ByteArray {
    val values = resampleLevels(levels, WAVEFORM_VALUES)
    val peak = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val packed = ByteArray((WAVEFORM_VALUES * 5 + 7) / 8)
    values.forEachIndexed { index, level ->
        val value = (level / peak * WAVEFORM_MAX).toInt().coerceIn(0, WAVEFORM_MAX.toInt())
        val bit = index * 5
        val byte = bit / 8
        val shifted = value shl (bit % 8)
        packed[byte] = (packed[byte].toInt() or (shifted and 0xFF)).toByte()
        if (shifted > 0xFF) packed[byte + 1] = (packed[byte + 1].toInt() or (shifted shr 8)).toByte()
    }
    return packed
}

private fun resampleLevels(levels: List<Float>, count: Int): List<Float> {
    if (levels.isEmpty()) return List(count) { 0f }
    return List(count) { index ->
        val start = index * levels.size / count
        val end = maxOf(start + 1, (index + 1) * levels.size / count).coerceAtMost(levels.size)
        levels.subList(start.coerceAtMost(levels.size - 1), end).max()
    }
}

/** Kolik hodnot waveformu posílají oficiální klienti. */
const val WAVEFORM_VALUES = 100

/** Nejvyšší hodnota waveformu; Telegram je posílá po 5 bitech, tedy 0–31. */
const val WAVEFORM_MAX = 31f
