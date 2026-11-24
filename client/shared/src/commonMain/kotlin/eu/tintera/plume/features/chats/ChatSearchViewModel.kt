package eu.tintera.plume.features.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import eu.tintera.plume.core.bookmarks.MessageBookmark
import eu.tintera.plume.core.bookmarks.MessageBookmarks
import eu.tintera.plume.core.settings.StoredPhotoDescription
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.SponsoredSearchChat
import eu.tintera.plume.features.media.PhotoDescriptions
import eu.tintera.plume.features.report.ReportFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * Hledání v seznamu chatů: chaty, veřejné chaty, sponzorované chaty a zprávy napříč chaty
 * ([ChatSearch]), hledání přirozeným jazykem a hlášení sponzorovaných výsledků.
 *
 * Dřív bylo součástí `ChatsViewModel`. Vlastní ViewModel je první krok k hledání jako navigační
 * destinaci (`docs/navrh-hledani.md`): obrazovka hledání bude mít vlastní položku zásobníku
 * a s ní vlastní úložiště ViewModelů, takže stav hledání nesmí patřit seznamu chatů.
 *
 * Patří do kontejneru účtu (`appAccountModule`): hledá v chatech jednoho účtu. Režim Všechny
 * účty bere ViewModel z kontejneru každého účtu (`ChatSource.search`) a výsledky slévá
 * (`mergedSearchState`).
 */
class ChatSearchViewModel(
    private val repository: ChatRepository,
    /** Hledání přirozeným jazykem; bez něj se „Hledat s AI" nenabízí. */
    private val naturalSearch: NaturalSearchModel? = null,
    /** Záložky zpráv; ty, jejichž text sedí na dotaz, mají ve výsledcích vlastní blok. */
    bookmarks: MessageBookmarks? = null,
    /** Fotky popsané modelem v zařízení; hledání je najde podle popisu. */
    photoDescriptions: PhotoDescriptions? = null,
) : ViewModel() {

    /** Uložené zprávy: TDLib je zná pod jménem účtu, výsledky je hledají podle přeloženého názvu. */
    val savedMessagesChatId: StateFlow<Long?> = repository.savedMessagesChatId

    /** Záložky přihlášeného účtu ze všech chatů (hledání v zařízení). */
    val bookmarks: StateFlow<List<MessageBookmark>> = (bookmarks?.all ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Popsané fotky (popis modelem v zařízení) — hledání je najde podle obsahu. */
    val describedPhotos: StateFlow<List<StoredPhotoDescription>> = (photoDescriptions?.all ?: flowOf(emptyList()))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Názvy chatů se záložkami a popsanými fotkami. Dotahují se zvlášť (`summaries`, lokální),
     * protože takový chat nemusí být v načteném seznamu — archivovaný, opuštěný.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val foundChatTitles: StateFlow<Map<Long, String>> = combine(this.bookmarks, describedPhotos) { marks, photos ->
        (marks.map { it.chatId } + photos.map { it.chatId }).distinct()
    }
        .distinctUntilChanged()
        .mapLatest { ids -> repository.summaries(ids).associate { it.id to it.title } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Hledání v hlavičce seznamu (chaty, veřejné chaty i zprávy, se sponzorovanými chaty). */
    val search = ChatSearch(repository, viewModelScope, everywhere = true, sponsored = true)

    /** Sponzorované chaty, jejichž zhlédnutí už odešlo — každé se hlásí jednou, ne při každém posunu. */
    private val seenSponsoredChats = HashSet<Long>()

    /** Sponzorovaný chat ve výsledcích hledání byl na obrazovce celý (`viewSponsoredChat`). */
    fun sponsoredChatSeen(uniqueId: Long) {
        if (!seenSponsoredChats.add(uniqueId)) return
        viewModelScope.launch { repository.reportSponsoredChatView(uniqueId) }
    }

    /**
     * Ťuknutí na sponzorovaný chat (`openSponsoredChat`). Seznam chatů zůstává v zásobníku pod
     * otevřeným chatem, takže hlášení doběhne i po přechodu.
     */
    fun sponsoredChatOpened(uniqueId: Long) {
        viewModelScope.launch { repository.reportSponsoredChatOpen(uniqueId) }
    }

    /**
     * Průvodce nahlášením sponzorovaného chatu — tentýž jako u reklamy v kanálu, jen jiné volání.
     * Id výsledku (ne chatu) jede jako jediná „zpráva" požadavku.
     */
    val sponsoredChatReportFlow = ReportFlow(viewModelScope) { _, request ->
        repository.reportSponsoredChat(request.messageIds.single(), request.optionId)
    }

    fun reportSponsoredChat(sponsored: SponsoredSearchChat) {
        sponsoredChatReportFlow.start(sponsored.chat.id, messageIds = listOf(sponsored.uniqueId)) {
            search.dropSponsored(sponsored.uniqueId)
        }
    }

    /** Umí telefon hledat přirozeným jazykem (zapnutá AI, připravený model)? */
    val naturalSearchAvailable: StateFlow<Boolean> = (naturalSearch?.available ?: flowOf(false))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Věta z pole hledání → slova a filtry; `null`, když z ní model nic nevyčetl. */
    suspend fun parseSearch(query: String): NaturalSearch? {
        val timeZone = TimeZone.currentSystemDefault()
        return naturalSearch?.parse(query, Clock.System.todayIn(timeZone), timeZone)
    }
}
