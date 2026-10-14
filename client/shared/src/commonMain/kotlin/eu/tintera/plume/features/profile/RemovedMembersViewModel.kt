package eu.tintera.plume.features.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.MEMBERS_PAGE_SIZE
import eu.tintera.plume.core.telegram.MemberFilter
import eu.tintera.plume.core.telegram.ProfileMember
import eu.tintera.plume.core.telegram.ProfileRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.member_unrestricted
import eu.tintera.plume.resources.moderation_load_failed
import eu.tintera.plume.resources.profile_action_failed
import eu.tintera.plume.resources.removed_members_unbanned
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import kotlin.time.Duration.Companion.milliseconds

/**
 * Omezení ([banned] `false`) nebo vyhození členové skupiny či kanálu — a zrušení obojího.
 *
 * - **Stránkuje se a hledá stejně jako záložka Členové** ([MemberList]): server filtr bere i
 *   s dotazem, takže se hledá jen mezi omezenými / vyhozenými.
 * - **Seznam se po změně ČTE ZNOVU** (`memberUpdates`), stejně jako členové v profilu: zrušit
 *   vyhození mohl i jiný správce a co změna udělala, ví jen server.
 * - **Vyřizovaný člen z řádku hned zmizí** ([processing]) — druhé ťuknutí by poslalo totéž
 *   podruhé, jako u žádostí o vstup.
 */
class RemovedMembersViewModel(
    private val chatId: Long,
    val banned: Boolean,
    private val repository: ProfileRepository,
) : ViewModel() {

    private val logger = Logger.withTag("RemovedMembers")
    private val filter = if (banned) MemberFilter.Banned else MemberFilter.Restricted

    var list by mutableStateOf(MemberList())
        private set
    var loadFailed by mutableStateOf(false)
        private set

    /** Text v poli hledání — může předbíhat [MemberList.query], dokud server neodpoví. */
    var query by mutableStateOf("")
        private set

    /**
     * Hledání se nabízí, jakmile se seznam nevešel na jednu stránku, a pak už zůstane — kdyby
     * zmizelo s výsledky hledání, nešlo by dotaz smazat.
     */
    var searchAvailable by mutableStateOf(false)
        private set

    var processing by mutableStateOf<Set<Long>>(emptySet())
        private set

    /** Jednorázová hláška pro snackbar; obrazovka ji po zobrazení vynuluje. */
    var notice by mutableStateOf<UiText?>(null)

    val shown: List<ProfileMember>? get() = list.members?.filterNot { it.sender.id in processing }

    private var loadJob: Job? = null
    private var moreJob: Job? = null

    init {
        load("")
        viewModelScope.launch {
            repository.memberUpdates(chatId).conflate().collect { reload() }
        }
    }

    fun search(text: String) {
        query = text
        val search = text.trim()
        if (search == list.query && loadJob?.isActive != true) return
        load(search, delayed = true)
    }

    /** Tolik, kolik už bylo vidět — jinak by člověk, se kterým se zrovna něco dělalo, zmizel ze seznamu. */
    fun reload() {
        load(query.trim(), limit = list.reloadLimit)
    }

    private fun load(search: String, delayed: Boolean = false, limit: Int = MEMBERS_PAGE_SIZE) {
        moreJob?.cancel()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (delayed) delay(SEARCH_DELAY)
            val result = repository.members(chatId, search, offset = 0, limit = limit, filter = filter)
            // Repository chytá do `Result` i zrušení; zrušený dotaz stav měnit nesmí.
            ensureActive()
            result
                .onSuccess { page ->
                    list = list.withFirstPage(search, page)
                    loadFailed = false
                    if (search.isEmpty() && page.nextOffset != null) searchAvailable = true
                }
                // Při obnovení zůstane, co už bylo vidět; prázdný seznam by tvrdil, že nikdo není.
                .onFailure { failure ->
                    if (list.members == null) loadFailed = true
                    notice = failure.reported(Res.string.moderation_load_failed)
                }
        }
    }

    fun loadMore() {
        val current = list
        val offset = current.nextOffset
        if (!current.canLoadMore || offset == null) return
        val search = current.query
        list = current.copy(loadingMore = true)
        moreJob = viewModelScope.launch {
            val result = repository.members(chatId, search, offset = offset, filter = filter)
            ensureActive()
            result
                .onSuccess { page ->
                    val before = list.members?.size
                    list = list.withNextPage(search, offset, page)
                    // Stránka bez nikoho nového seznam neprodlouží a další by si sám neřekl.
                    if (list.members?.size == before) loadMore()
                }
                .onFailure { failure ->
                    logger.w(failure) { "Další stránku seznamu chatu $chatId se nepodařilo načíst." }
                    list = list.withFailedPage(search, offset)
                }
        }
    }

    /** Zrušit vyhození, nebo omezení — podle toho, který seznam to je. */
    fun release(member: ProfileMember) {
        val userId = member.sender.id
        if (userId in processing) return
        processing = processing + userId
        viewModelScope.launch {
            val result = if (banned) repository.unbanMember(chatId, userId) else repository.unrestrictMember(chatId, userId)
            result
                .onSuccess {
                    notice = if (banned) {
                        UiText(Res.string.removed_members_unbanned, member.sender.title)
                    } else {
                        UiText(Res.string.member_unrestricted)
                    }
                }
                .onFailure { notice = it.reported(Res.string.profile_action_failed) }
            // Až po novém čtení, jinak by člověk na okamžik naskočil zpátky.
            loadJob?.cancel()
            val search = query.trim()
            repository.members(chatId, search, offset = 0, limit = list.reloadLimit, filter = filter)
                .onSuccess { list = list.withFirstPage(search, it) }
            processing = processing - userId
        }
    }

    /** Surová hláška od TDLib patří do logu, ne na obrazovku — viz `userMessage`. */
    private fun Throwable.reported(fallback: StringResource): UiText {
        logger.w(this) { "Seznam ${if (banned) "vyhozených" else "omezených"} v chatu $chatId: akce selhala." }
        return userMessage(fallback)
    }

    private companion object {
        val SEARCH_DELAY = 300.milliseconds
    }
}
