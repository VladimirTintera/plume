package eu.tintera.plume.core.accounts

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Supergroupa nebo kanál podle id chatu (`-100…`). Jen tyhle mají v každém účtu tutéž historii
 * i tatáž id zpráv, takže se dají ve společném seznamu spojit do jednoho řádku a přečtení
 * i notifikace jdou přenést mezi účty. Obyčejná skupina má id zpráv v každém účtu jiná,
 * soukromý chat (i s botem) a Uložené zprávy jsou v každém účtu jiná konverzace.
 *
 * Meze podle TDLib: supergroupa je `-1 000 000 000 000 - id` s id pod `10^12 - 2^31`, tajný
 * chat leží kolem `-2 000 000 000 000` (± 2^31). Obě rozmezí se nepotkají.
 */
fun isSharedHistoryChat(chatId: Long): Boolean = chatId in SUPERGROUP_MIN_ID..SUPERGROUP_MAX_ID

private const val SUPERGROUP_MAX_ID = -1_000_000_000_001L
private const val SUPERGROUP_MIN_ID = -2_000_000_000_000L + (1L shl 31)

/**
 * Chaty, které má víc účtů, v režimu Všechny účty (`docs/navrh-vice-uctu.md`, kroky 24 a 25):
 * jeden řádek v seznamu a výběr účtu v poli zprávy. U společné skupiny nebo kanálu (tatáž
 * historie) navíc přečtení za všechny účty a jedna notifikace na zprávu; u soukromého chatu
 * a Uložených zpráv (v každém účtu jiná historie) jen „Označit jako přečtené" za všechny.
 * Mimo režim (nebo s jedním účtem) nedělá nic — spojení je vlastnost společného seznamu.
 *
 * Jedno na proces: přenáší mezi kontejnery účtů, které o sobě nevědí.
 */
class SharedChats(
    private val accountsProvider: () -> Accounts,
    private val settings: AppSettings,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("SharedChats")
    private val accounts: Accounts by lazy(accountsProvider)

    /** Spojuje se: režim Všechny účty a víc než jeden účet. */
    val active: Boolean get() = settings.chatListAllAccounts.value && accounts.all.value.size > 1

    private val _preferred = MutableStateFlow<Map<Long, AccountSlot>>(emptyMap())

    /**
     * Kterým účtem uživatel ve spojeném chatu naposledy psal (výběr v poli zprávy): tím se chat
     * ze seznamu otevře příště. Jen do konce procesu — volba patří rozhovoru, ne nastavení.
     */
    val preferred: StateFlow<Map<Long, AccountSlot>> = _preferred.asStateFlow()

    fun prefer(chatId: Long, slot: AccountSlot) {
        _preferred.update { it + (chatId to slot) }
    }

    /**
     * Tentýž chat v ostatních účtech: účet a id chatu v něm. Prázdné mimo režim. Jen místní
     * databáze TDLib, bez sítě.
     *
     * - **Uložené zprávy** ([chatId] je vlastní id účtu [slot]): Uložené zprávy ostatních účtů,
     *   tedy JINÉ id chatu (vlastní id toho účtu).
     * - **Chat s jiným vlastním účtem** (id je user id jiného přihlášeného účtu): žádný. V tom
     *   druhém účtu je totéž id jeho Uložených zpráv, ne chat s námi.
     * - **Supergroupa a kanál** ([isSharedHistoryChat]) a **soukromý chat** (kladné id, i bot):
     *   ostatní účty, které v chatu jsou (`ChatRepository.takesPartIn`), s týmž id.
     * - Ostatní (obyčejná skupina, tajný chat): žádný.
     */
    suspend fun counterparts(slot: AccountSlot, chatId: Long): List<ChatCounterpart> {
        if (!active) return emptyList()
        val all = accounts.all.value
        val own = all.firstOrNull { it.slot == slot } ?: return emptyList()
        val others = all.filter { it.slot != slot }
        val ownIds = all.mapNotNull { it.userId.value }
        return when {
            chatId == own.userId.value -> others.mapNotNull { other -> other.userId.value?.let { ChatCounterpart(other, it) } }
            chatId in ownIds -> emptyList()
            // Dokud není známé id všech účtů, nejde chat s vlastním účtem poznat: nic.
            ownIds.size < all.size -> emptyList()
            isSharedHistoryChat(chatId) || chatId > 0 -> others
                .filter { other -> other.koin.getOrNull<ChatRepository>()?.takesPartIn(chatId) == true }
                .map { ChatCounterpart(it, chatId) }
            else -> emptyList()
        }
    }

    /**
     * Uživatel v účtu [from] přečetl chat [chatId] po zprávu [messageId]: přečte se i v ostatních.
     * Jen u společné historie ([isSharedHistoryChat]) — v soukromém chatu jsou v každém účtu jiné
     * zprávy a „po zprávu X" tam nic neznamená.
     */
    fun readUpTo(from: AccountSlot, chatId: Long, messageId: Long) {
        if (!isSharedHistoryChat(chatId)) return
        spread(from, chatId) { repository, _ -> repository.readUpTo(chatId, messageId) }
    }

    /**
     * „Označit jako přečtené" v účtu [from]: chat se přečte celý i v ostatních. Platí pro každý
     * spojený řádek, i soukromý chat a Uložené zprávy: řádek ukazuje nepřečtené všech účtů,
     * takže „přečteno" musí platit za všechny.
     */
    fun readAll(from: AccountSlot, chatId: Long) {
        spread(from, chatId) { repository, counterpart -> repository.readChat(counterpart) }
    }

    private fun spread(from: AccountSlot, chatId: Long, read: suspend (ChatRepository, Long) -> Result<Unit>) {
        if (!active) return
        scope.launch {
            counterparts(from, chatId).forEach { counterpart ->
                read(counterpart.account.koin.get(), counterpart.chatId)
                    .onFailure { logger.w(it) { "Přečtení spojeného chatu se do druhého účtu nepřeneslo." } }
            }
        }
    }

    private val claims = NotificationClaims()

    /**
     * Smí účet [slot] ukázat notifikaci zprávy [messageId] v chatu [chatId]? Ve společné skupině
     * ji ohlásí oba účty; ukáže se jen od toho, který ji ohlásil první. Jeho přečtení pak
     * přečte i druhý účet ([readUpTo]), takže zpráva nezůstane nikde viset.
     */
    suspend fun mayNotify(slot: AccountSlot, chatId: Long, messageId: Long?): Boolean {
        if (messageId == null || !active || !isSharedHistoryChat(chatId)) return true
        return claims.claim(slot, chatId, messageId)
    }
}

/** Tentýž chat v jiném účtu ([SharedChats.counterparts]): Uložené zprávy tam mají jiné id. */
data class ChatCounterpart(val account: AccountSession, val chatId: Long)

/**
 * Kdo první ohlásil kterou zprávu ([SharedChats.mayNotify]). Pamatuje si posledních [capacity]
 * zpráv: náhrada dočasné notifikace z pushe skutečnou zprávou přijde do pár vteřin a drží
 * týž účet, takže starší záznamy už nikdo nepotřebuje.
 */
internal class NotificationClaims(private val capacity: Int = 512) {
    private val mutex = Mutex()
    private val owners = LinkedHashMap<Pair<Long, Long>, AccountSlot>()

    suspend fun claim(slot: AccountSlot, chatId: Long, messageId: Long): Boolean = mutex.withLock {
        val owner = owners.getOrPut(chatId to messageId) { slot }
        while (owners.size > capacity) owners.remove(owners.keys.first())
        owner == slot
    }
}
