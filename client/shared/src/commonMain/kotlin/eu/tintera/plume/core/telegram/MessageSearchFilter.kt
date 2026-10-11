package eu.tintera.plume.core.telegram

/**
 * Zúžení hledání zpráv napříč chaty — obojí umí `searchMessages` na serveru
 * (`chatTypeFilter`, `minDate` / `maxDate`), takže se nefiltruje nad stránkou, ale hledá rovnou
 * v tom, co uživatel chce. Filtr nad načtenou stránkou by z padesáti výsledků nechal pár a zbytek
 * by se dotahoval naprázdno.
 */
data class MessageSearchFilter(
    /** Jen soukromé chaty / skupiny / kanály; `null` = všechny. */
    val chatKind: ChatKind? = null,
    /** Jen zprávy z období; `null` = kdykoli. */
    val period: SearchPeriod? = null,
    /**
     * U hashtagového dotazu veřejné příspěvky kanálů místo zpráv z vlastních chatů
     * (`searchPublicMessagesByTag`). Druh chatu ani období se na ně nevztahují — jsou to vždycky
     * kanály a období API nebere —, proto s nimi UI ty dva filtry schová.
     */
    val publicPosts: Boolean = false,
    /**
     * Jen zprávy s tímhle obsahem (fotky, soubory…); `null` = jakékoli. Zatím ho vyplňuje jen
     * hledání přirozeným jazykem — čip nad výsledky ho ukáže a dovolí zrušit.
     */
    val content: SearchContent? = null,
) {
    /** Zužuje filtr výsledky? Volba veřejných příspěvků nic nezužuje, jen mění zdroj. */
    val isEmpty: Boolean get() = chatKind == null && period == null && content == null
}

/** Druh obsahu hledaných zpráv (`SearchMessagesFilter*` v TDLib). */
enum class SearchContent { Photos, Videos, Files, Links, Voice, Music }

sealed interface SearchPeriod {
    data object Week : SearchPeriod
    data object Month : SearchPeriod
    data object Year : SearchPeriod

    /** Vlastní rozsah z výběru dat: celé dny od [fromEpochSeconds] do konce dne [toEpochSeconds]. */
    data class Custom(val fromEpochSeconds: Long, val toEpochSeconds: Long) : SearchPeriod
}

/**
 * Rozsah `minDate` / `maxDate` pro TDLib v unixových sekundách; nula = bez meze. Konec
 * vlastního rozsahu se posouvá na konec dne — „do 20. 9." má zahrnout i zprávy z 20. 9. večer.
 */
fun SearchPeriod?.dateRange(nowEpochSeconds: Long): Pair<Int, Int> = when (this) {
    null -> 0 to 0
    SearchPeriod.Week -> (nowEpochSeconds - 7 * DAY).toInt() to 0
    SearchPeriod.Month -> (nowEpochSeconds - 30 * DAY).toInt() to 0
    SearchPeriod.Year -> (nowEpochSeconds - 365 * DAY).toInt() to 0
    is SearchPeriod.Custom -> fromEpochSeconds.toInt() to (toEpochSeconds + DAY - 1).toInt()
}

private const val DAY = 24 * 60 * 60L
