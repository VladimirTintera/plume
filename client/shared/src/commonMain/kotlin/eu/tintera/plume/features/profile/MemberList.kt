package eu.tintera.plume.features.profile

import eu.tintera.plume.core.telegram.MEMBERS_PAGE_SIZE
import eu.tintera.plume.core.telegram.MAX_MEMBERS_REQUEST
import eu.tintera.plume.core.telegram.MemberPage
import eu.tintera.plume.core.telegram.ProfileMember

/**
 * Načtení členové záložky profilu a odkud číst dál.
 *
 * Čistá data a přechody — pořadí a hranice stránek se na zařízení poznají až dvakrát ukázaným
 * člověkem nebo pádem `LazyColumn` na stejném klíči, tedy pozdě.
 */
data class MemberList(
    /** `null` = první stránka se načítá; prázdné = nikdo (nebo nic nenalezeno). */
    val members: List<ProfileMember>? = null,
    /** Dotaz, ke kterému [members] patří — ne ten, který se právě píše. */
    val query: String = "",
    /** Posun další stránky; `null` = konec. */
    val nextOffset: Int? = null,
    val loadingMore: Boolean = false,
) {
    val canLoadMore: Boolean get() = members != null && nextOffset != null && !loadingMore

    /** Kolik členů si server už vydal — tolik se čte znovu, když se seznam obnovuje. */
    val reloadLimit: Int
        get() = (nextOffset ?: members?.size ?: 0).coerceIn(MEMBERS_PAGE_SIZE, MAX_MEMBERS_REQUEST)

    /** První stránka nahradí všechno, i rozdělané dotahování. */
    fun withFirstPage(query: String, page: MemberPage): MemberList =
        MemberList(members = page.members.distinctBy { it.sender.id }, query = query, nextOffset = page.nextOffset)

    /**
     * Další stránka se připojí, jen když pořád patří TOMUHLE seznamu — stejný dotaz, stejný posun.
     * Pomalá odpověď na předchozí dotaz by jinak přimíchala cizí výsledky.
     *
     * **Tentýž člověk na dvou stránkách zůstane jednou.** Správci jsou na první stránce zvlášť
     * a server je vrací i mezi nedávnými; a kdo se mezitím přidal, posune celou řadu o jedno
     * místo. Dva stejné klíče by `LazyColumn` shodily.
     */
    fun withNextPage(query: String, offset: Int, page: MemberPage): MemberList {
        if (query != this.query || offset != nextOffset || members == null) return this
        return copy(
            members = (members + page.members).distinctBy { it.sender.id },
            nextOffset = page.nextOffset,
            loadingMore = false,
        )
    }

    /** Stránka se nepodařila: dotahování skončí, aby se nezkoušelo pořád dokola při každém scrollu. */
    fun withFailedPage(query: String, offset: Int): MemberList =
        if (query != this.query || offset != nextOffset) this else copy(nextOffset = null, loadingMore = false)
}
