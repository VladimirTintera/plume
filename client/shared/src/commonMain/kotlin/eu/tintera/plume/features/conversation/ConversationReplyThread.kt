package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.reply_thread_failed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * **Vlákno odpovědí** ve skupině bez témat (a v soukromém chatu): jen řetěz odpovědí kolem
 * zprávy, nahoru k té, kterou to celé začalo, a dolů ke všem, kdo na ni (nebo na odpověď na ni)
 * odpověděl. Telegram nic takového nemá — rozhovor dvou lidí se v rušné skupině rozpadne mezi
 * stovky zpráv.
 *
 * - **Nahoru se jde po citacích** ([ChatMessage.replyToMessageId]): z načteného seznamu, a co
 *   v něm není, po jedné přes [ConversationRepository.message]. Citovaná zpráva, která už
 *   neexistuje, vlákno uzavře ([ReplyThreadSheet.rootMissing]).
 * - **Dolů server odpovědi neindexuje** (`getMessageThread` je jen pro komentáře a témata),
 *   takže se historie prochází OD KOŘENE k přítomnosti a ke vláknu se připojí každá zpráva,
 *   která odpovídá na některou zprávu vlákna ([extendThread]). Prochází se po stránkách
 *   a nejvýš [SCAN_BUDGET] zpráv naráz; pak se procházení zastaví a sheet nabídne „Hledat dál"
 *   ([ReplyThreadSheet.pausedAt]) — kořen ze starého měsíce v živé skupině by jinak znamenal
 *   desítky tisíc zpráv.
 * - **Sheet se plní průběžně** po každé stránce, takže nejbližší odpovědi jsou vidět hned.
 *
 * Část [ConversationViewModel] — ten na ni deleguje.
 */
internal class ConversationReplyThread(
    private val chatId: Long,
    private val state: MutableStateFlow<ConversationState>,
    private val scope: CoroutineScope,
    private val repository: ConversationRepository,
    /**
     * Skok na zprávu v konverzaci; `returnTo` je zpráva, ze které se vlákno otevřelo — tlačítko
     * dolů k ní pak vrátí jako po skoku z citace (`JumpReturns.kt`).
     */
    private val jumpTo: (messageId: Long, returnTo: Long?) -> Unit,
    private val logger: Logger,
) {
    private var job: Job? = null

    /** Zprávy vlákna podle id; i ty, které sheet skryje jako další fotky téhož alba. */
    private val members = mutableMapOf<Long, ChatMessage>()

    /** Nejnovější prošlá zpráva — odsud pokračuje „Hledat dál". */
    private var cursor: ChatMessage? = null

    /** Nabídka nad zprávou → „Zobrazit vlákno". */
    fun openThread(messageId: Long) {
        val origin = state.value.messages.firstOrNull { it.id == messageId } ?: return
        job?.cancel()
        members.clear()
        members[origin.id] = origin
        cursor = null
        state.update {
            it.copy(messageMenu = null, sheet = ReplyThreadSheet(originId = messageId, messages = listOf(origin), scanning = true))
        }
        job = scope.launch {
            val (chain, rootMissing) = ancestors(origin)
            chain.forEach { members[it.id] = it }
            cursor = chain.first()
            publish(scanning = true, rootMissing = rootMissing)
            scan(messageId)
        }
    }

    /** „Hledat dál" po zastavení na stropu (nebo po chybě). */
    fun scanMore() {
        val sheet = state.value.sheet<ReplyThreadSheet>() ?: return
        if (sheet.scanning || cursor == null) return
        job?.cancel()
        publish(scanning = true)
        job = scope.launch { scan(sheet.originId) }
    }

    fun closeThread() {
        job?.cancel()
        state.update { it.closingSheet<ReplyThreadSheet>() }
    }

    /**
     * Ťuknutí na zprávu ve vlákně: zavřít sheet a skočit na ni v konverzaci. Bod návratu je
     * zpráva, ze které se vlákno otevřelo — kromě ní samotné, tam není kam se vracet.
     */
    fun pickThreadMessage(messageId: Long) {
        val originId = state.value.sheet<ReplyThreadSheet>()?.originId
        closeThread()
        jumpTo(messageId, originId?.takeIf { it != messageId })
    }

    /**
     * Řetěz citací od kořene po [origin] (včetně) a jestli citace na začátku vede na zprávu,
     * která už neexistuje. Nejvýš [MAX_ANCESTORS] kroků — kruh citací nevznikne, ale strop nic nestojí.
     */
    private suspend fun ancestors(origin: ChatMessage): Pair<List<ChatMessage>, Boolean> {
        val chain = ArrayDeque<ChatMessage>().apply { add(origin) }
        var rootMissing = false
        while (chain.size <= MAX_ANCESTORS) {
            val parentId = chain.first().replyToMessageId ?: break
            val parent = state.value.messages.firstOrNull { it.id == parentId }
                ?: repository.message(chatId, parentId).getOrNull()
            if (parent == null) {
                rootMissing = true
                break
            }
            chain.addFirst(parent)
        }
        return chain.toList() to rootMissing
    }

    private suspend fun scan(originId: Long) {
        var scanned = 0
        while (scanned < SCAN_BUDGET) {
            val from = cursor ?: return
            // `fromMessageId` je ve výsledku taky, proto o jednu víc (jako `loadNewer`).
            val page = repository.history(chatId = chatId, fromMessageId = from.id, limit = PAGE_SIZE + 1, offset = -PAGE_SIZE)
                .getOrElse { failure ->
                    state.update { it.copy(error = failure.reported(logger, Res.string.reply_thread_failed)) }
                    publish(scanning = false, pausedAt = from.timestamp)
                    return
                }
            val newer = page.filter { it.id > from.id }.sortedBy { it.id }
            if (newer.isEmpty()) {
                publish(scanning = false)
                return
            }
            extendThread(members, newer)
            cursor = newer.last()
            scanned += newer.size
            if (state.value.sheet<ReplyThreadSheet>()?.originId != originId) return
            publish(scanning = true)
        }
        publish(scanning = false, pausedAt = cursor?.timestamp)
    }

    /** Přepíše otevřený sheet podle [members]; jiný (nebo zavřený) sheet nechá být. */
    private fun publish(scanning: Boolean, pausedAt: Int? = null, rootMissing: Boolean? = null) {
        val messages = threadRows(members.values)
        state.update { current ->
            current.updatingSheet<ReplyThreadSheet> { open ->
                open.copy(
                    messages = messages,
                    scanning = scanning,
                    pausedAt = pausedAt,
                    rootMissing = rootMissing ?: open.rootMissing,
                )
            }
        }
    }
}

/**
 * Připojí k vláknu [members] zprávy z [page] (seřazené od nejstarší), které odpovídají na
 * některou zprávu vlákna — i na tu, která se připojila o pár řádků výš v téže stránce. Zprávu,
 * která ve vlákně už je, nahradí novější podobou (úprava, reakce).
 */
internal fun extendThread(members: MutableMap<Long, ChatMessage>, page: List<ChatMessage>) {
    for (message in page) {
        if (message.id in members || message.replyToMessageId in members) members[message.id] = message
    }
}

/**
 * Řádky sheetu: od nejstarší a z alba jen první zpráva — ostatní fotky téhož alba odpovídají
 * na totéž a v seznamu by byly jen opakovaným „[Fotka]".
 */
internal fun threadRows(members: Collection<ChatMessage>): List<ChatMessage> =
    members.sortedBy { it.id }.distinctBy { if (it.mediaAlbumId != 0L) "album-${it.mediaAlbumId}" else "id-${it.id}" }

/** Kolik zpráv projde jedno kolo procházení, než se zastaví a nabídne „Hledat dál". */
internal const val SCAN_BUDGET = 1000

/**
 * Novějších zpráv na stránku. TDLib bere `limit` nejvýš 100 a `offset` větší než -100
 * („Parameter offset must be greater than -100"), a `fromMessageId` je ve výsledku navíc —
 * 99 + 1 je tedy nejvíc, co jedno volání unese.
 */
private const val PAGE_SIZE = 99

/** Nejvíc kroků po citacích nahoru. */
private const val MAX_ANCESTORS = 100
