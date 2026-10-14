package eu.tintera.plume.features.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.BlockedSender
import eu.tintera.plume.core.telegram.BlockedSendersPage
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.userMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.foldDiacritics
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.settings_blocked_failed
import eu.tintera.plume.resources.settings_blocked_load_failed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Kolik blokovaných se čte naráz; víc `getBlockedMessageSenders` nevezme. */
internal const val BLOCKED_PAGE_SIZE = 100

/** Pojistka proti nekonečnému čtení, kdyby server hlásil počet, který nikdy nedočteme. */
private const val MAX_BLOCKED_PAGES = 50

/**
 * Přečte celý seznam blokovaných po stránkách.
 *
 * **Konec určuje `totalCount` od serveru, ne délka stránky**: kdo se nedá dočíst (repository ho
 * ze stránky vypustí), zkrátil by stránku a stránkování by skončilo předčasně. Tentýž odesílatel
 * na dvou stránkách (seznam se mezi čteními posunul) zůstane jednou — dva stejné klíče by
 * `LazyColumn` shodily. Selhání kterékoli stránky je selhání celku: půlka seznamu by tvrdila, že
 * zbytek blokovaný není.
 */
internal suspend fun collectBlockedSenders(
    fetch: suspend (offset: Int) -> Result<BlockedSendersPage>,
): Result<List<BlockedSender>> {
    val senders = mutableListOf<BlockedSender>()
    var offset = 0
    repeat(MAX_BLOCKED_PAGES) {
        val page = fetch(offset).getOrElse { return Result.failure(it) }
        senders += page.senders
        offset += BLOCKED_PAGE_SIZE
        if (offset >= page.totalCount) return Result.success(senders.distinctBy { it.id })
    }
    return Result.success(senders.distinctBy { it.id })
}

data class BlockedSendersState(
    /** `null`, dokud se seznam nenačte. */
    val senders: List<BlockedSender>? = null,
    val query: String = "",
    /** Odesílatel s otevřenou nabídkou; `null` = nabídka zavřená. */
    val target: BlockedSender? = null,
    /** Jednorázová událost pro snackbar s „Vrátit" po odblokování. */
    val unblocked: BlockedSender? = null,
    val error: UiText? = null,
) {
    /** Blokovaní podle hledání — bez ohledu na velikost písmen a diakritiku, jako hledání chatů. */
    val visible: List<BlockedSender>
        get() {
            val all = senders.orEmpty()
            val needle = foldDiacritics(query.trim())
            if (needle.isEmpty()) return all
            return all.filter { foldDiacritics(it.title).contains(needle, ignoreCase = true) }
        }
}

/**
 * Seznam blokovaných v Nastavení → Soukromí. Jediné místo, kde jde zrušit blokování někoho, s kým
 * uživatel chat smazal — nabídka nad chatem i profil stojí na chatu.
 *
 * Blokování jde přes [ChatRepository], jako všude jinde v aplikaci (viz `docs/poznatky/spam-a-blokovani.md`, „Blokování
 * a nahlášení spamu").
 */
class BlockedSendersViewModel(private val chats: ChatRepository) : ViewModel() {

    private val logger = Logger.withTag("Blocked")

    private val _state = MutableStateFlow(BlockedSendersState())
    val state: StateFlow<BlockedSendersState> = _state.asStateFlow()

    /**
     * Odblokovaní v téhle relaci. Server je hned po změně ještě vracel mezi blokovanými (na
     * emulátoru: odblokovaný bot po přečtení seznamu v něm zůstal, počet v Soukromí přitom už
     * seděl) — takže se z výsledků vyřazují, dokud uživatel nedá „Vrátit".
     */
    private val unblockedIds = mutableSetOf<Long>()

    // Seznam se nečte v `init`: obě místa, která ho ukazují (řádek v Soukromí, seznam
    // blokovaných), ho čtou samy při zobrazení — a v init by se tak četl dvakrát.

    /**
     * Seznam znovu od serveru. Volá se i po každém (od)blokování: co změna udělala, ví jen
     * server — stejná zásada jako u zařízení v Nastavení. Načtený seznam mezitím zůstává, aby se
     * pod prstem nevyměnil za kolečko.
     */
    fun reload() {
        viewModelScope.launch {
            collectBlockedSenders { offset -> chats.blockedSenders(offset = offset, limit = BLOCKED_PAGE_SIZE) }
                .onSuccess { senders -> _state.update { it.copy(senders = senders.filter { s -> s.id !in unblockedIds }) } }
                .onFailure { failure ->
                    logger.w(failure) { "Seznam blokovaných se nepodařilo načíst." }
                    _state.update {
                        it.copy(senders = it.senders ?: emptyList(), error = failure.userMessage(Res.string.settings_blocked_load_failed))
                    }
                }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    /** Ťuknutí na řádek: nabídka s „Odblokovat", jako akce nad chatem v seznamu. */
    fun openActions(senderId: Long) {
        _state.update { state -> state.copy(target = state.senders?.firstOrNull { it.id == senderId }) }
    }

    fun dismissActions() {
        _state.update { it.copy(target = null) }
    }

    /**
     * Bez potvrzení: odblokování je vratné a „Vrátit" ve snackbaru je lepší než dialog, který
     * před vratnou akcí jen zdržuje — stejně jako zablokování z nabídky nad chatem.
     */
    fun unblock(sender: BlockedSender) {
        // Ze seznamu hned, ne až po odpovědi: řádek, na který se právě ťuklo, nemá chvíli viset.
        unblockedIds += sender.id
        _state.update { state -> state.copy(target = null, senders = state.senders?.filter { it.id != sender.id }) }
        change(sender, blocked = false)
    }

    /** „Vrátit" ve snackbaru. Oprava omylu, takže se na ni už další „Vrátit" nenabízí. */
    fun undoUnblock(sender: BlockedSender) {
        unblockedIds -= sender.id
        change(sender, blocked = true)
    }

    fun consumeUnblocked() {
        _state.update { it.copy(unblocked = null) }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }

    private fun change(sender: BlockedSender, blocked: Boolean) {
        viewModelScope.launch {
            chats.setSenderBlocked(sender.id, blocked)
                .onSuccess {
                    if (!blocked) _state.update { it.copy(unblocked = sender) }
                    reload()
                }
                .onFailure { failure ->
                    logger.w(failure) { "Blokování odesílatele ${sender.id} se nepodařilo změnit." }
                    // Neprošlo, takže odesílatel pořád je, kde byl — seznam se přečte znovu.
                    if (blocked) unblockedIds += sender.id else unblockedIds -= sender.id
                    reload()
                    _state.update { it.copy(error = failure.userMessage(Res.string.settings_blocked_failed)) }
                }
        }
    }
}
