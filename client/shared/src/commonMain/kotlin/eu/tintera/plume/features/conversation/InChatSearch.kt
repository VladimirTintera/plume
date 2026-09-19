package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatSearchPage

/**
 * Hledání uvnitř konverzace: dotaz, nalezené zprávy a která z nich je právě ukázaná.
 *
 * [results] jsou id od NEJNOVĚJŠÍ (tak je vrací TDLib), [index] je pozice ukázané zprávy
 * (−1 = žádná). „Starší" je tedy vyšší index, „novější" nižší. Další stránka starších se
 * dotahuje, až k ní uživatel došipkuje ([nextFromMessageId]).
 *
 * Čistá data a přechody bez TDLib, aby šly otestovat — hraniční případy (konec stránky,
 * prázdný výsledek, nový dotaz uprostřed dotahování) se v kompozici neověří.
 */
data class InChatSearch(
    val query: String = "",
    /** Dotaz, ke kterému patří [results]; během psaní se od [query] liší. */
    val resultsQuery: String = "",
    val results: List<Long> = emptyList(),
    val index: Int = -1,
    /** Odhad serveru; bez něj se počítá jen s načtenými. */
    val total: Int? = null,
    val nextFromMessageId: Long? = null,
    val loading: Boolean = false,
) {
    /** Ukázaná zpráva, nebo `null`. */
    val current: Long? get() = results.getOrNull(index)

    /** Celkem výsledků pro „N z M" — odhad serveru, ale nikdy míň, než kolik je načteno. */
    val count: Int get() = maxOf(total ?: 0, results.size)

    val hasOlder: Boolean get() = index + 1 < results.size || nextFromMessageId != null

    val hasNewer: Boolean get() = index > 0

    /** Hledání pro dotaz doběhlo a nic nenašlo. */
    val nothingFound: Boolean get() = !loading && resultsQuery.isNotBlank() && resultsQuery == query.trim() && results.isEmpty()

    /** První stránka pro [forQuery]: ukáže se nejnovější výsledek. */
    fun withFirstPage(forQuery: String, page: ChatSearchPage): InChatSearch = copy(
        resultsQuery = forQuery,
        results = page.messageIds,
        index = if (page.messageIds.isEmpty()) -1 else 0,
        total = page.total,
        nextFromMessageId = page.nextFromMessageId,
        loading = false,
    )

    /** Další stránka starších — jen pro dotaz, ke kterému patří načtené výsledky. */
    fun withNextPage(forQuery: String, page: ChatSearchPage): InChatSearch {
        if (forQuery != resultsQuery) return this
        val known = results.toHashSet()
        return copy(
            results = results + page.messageIds.filter { known.add(it) },
            nextFromMessageId = page.nextFromMessageId,
            loading = false,
        )
    }

    /** O výsledek starší; `null`, když je potřeba napřed dotáhnout stránku (nebo už není kam). */
    fun older(): InChatSearch? = if (index + 1 < results.size) copy(index = index + 1) else null

    fun newer(): InChatSearch? = if (index > 0) copy(index = index - 1) else null
}
