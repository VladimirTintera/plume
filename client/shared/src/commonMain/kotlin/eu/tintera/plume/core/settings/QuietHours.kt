package eu.tintera.plume.core.settings

import eu.tintera.plume.core.telegram.NotificationChannelKind

/**
 * Tiché hodiny: v nastaveném čase upozorní jen zmínky a odpovědi, ostatní notifikace přijdou
 * potichu (bez zvuku, vibrace a vyskočení). Na liště zůstanou, takže se nic neztratí.
 *
 * Čas je v minutách od půlnoci, v místním čase zařízení. Úsek přes půlnoc (22:00–7:00) je
 * obvyklý případ, ne výjimka. [start] == [end] je prázdný úsek: „celý den" by se nastavoval
 * vypnutím všech upozornění, ne tichými hodinami.
 *
 * Platí jen v zařízení — ostatní zařízení o nich nevědí (notifikace kreslí aplikace sama).
 */
data class QuietHours(
    val enabled: Boolean = false,
    val start: Int = 22 * 60,
    val end: Int = 7 * 60,
) {

    /** Je [minuteOfDay] uvnitř úseku? Začátek se počítá, konec ne. */
    fun contains(minuteOfDay: Int): Boolean = when {
        start == end -> false
        start < end -> minuteOfDay in start until end
        else -> minuteOfDay >= start || minuteOfDay < end
    }

    /**
     * Má notifikace z kanálu [channel] přijít potichu? Zmínky a odpovědi ne — kvůli nim tiché
     * hodiny existují. Kanál, ne druh chatu: zmínka ve ztlumené skupině jde kanálem zmínek.
     */
    fun silences(channel: NotificationChannelKind, minuteOfDay: Int): Boolean =
        enabled && channel != NotificationChannelKind.Mentions && contains(minuteOfDay)
}

/** Minut za den; čas tichých hodin je vždy v `0 until MINUTES_PER_DAY`. */
const val MINUTES_PER_DAY: Int = 24 * 60
