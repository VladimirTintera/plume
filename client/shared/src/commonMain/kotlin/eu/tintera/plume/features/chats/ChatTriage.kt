package eu.tintera.plume.features.chats

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.ai.OnDeviceAiStatus
import eu.tintera.plume.core.ai.TriageAnswer
import eu.tintera.plume.core.ai.generateAnswer
import eu.tintera.plume.core.ai.parseTriageVerdict
import eu.tintera.plume.core.ai.triagePrompt
import eu.tintera.plume.core.ai.triageVerdictOf
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.Verification
import eu.tintera.plume.core.ui.resolveNow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Posuzuje se chat vůbec? Jen nepřečtený soukromý chat, ve kterém poslední zprávu poslal
 * protějšek — kde odpověď dluží uživatel. Ne Uložené zprávy ([savedMessagesChatId]), ztlumené
 * chaty (o ty uživatel sám nestojí), ověřené účty (Telegram, služby) a sponzorované chaty.
 *
 * Skupiny vědomě ne: tam se z nepřečtených nepozná, KOHO se otázka týká, a „čeká na odpověď"
 * u skupiny, kde se někdo ptal všech, by byl šum. Zmínku ve skupině nese odznak „@".
 */
fun isTriageCandidate(chat: ChatSummary, savedMessagesChatId: Long?): Boolean =
    chat.kind == ChatKind.Private &&
        chat.unreadCount > 0 &&
        chat.lastMessageId != null &&
        !chat.lastMessageOutgoing &&
        !chat.isMuted &&
        chat.sponsor == null &&
        chat.id != savedMessagesChatId &&
        chat.verification !is Verification.Verified

/**
 * Triáž chatů: které nepřečtené soukromé chaty čekají na odpověď uživatele — posouzení modelem
 * v zařízení. V seznamu chatů z toho je odznak.
 *
 * - **Obsah zpráv nikam neodchází**: posuzuje model v telefonu.
 * - **Jen se zapnutou volbou, zapnutou AI a modelem, který je PŘIPRAVENÝ.** Kvůli triáži se nic
 *   nestahuje.
 * - **Výsledek patří POSLEDNÍ ZPRÁVĚ**, ne chatu ([awaiting] = chat → id posouzené zprávy).
 *   Nová zpráva znamená nové posouzení; seznam ukazuje odznak, jen když id sedí. Přečtení
 *   chatu ho schová samo — nepřečtený chat přestane být kandidátem.
 * - **Model se ptá po jednom chatu**, frontou v [appScope] — jako u [eu.tintera.plume.features.conversation.ScamWarnings].
 *   Seznam se mění při každé zprávě, takže [check] je levný a opakované volání nic nestojí.
 * - **Výsledek se drží v paměti procesu.** Po restartu se nepřečtené posoudí znovu.
 * - **Chyba modelu = žádný odznak.**
 */
class ChatTriage(
    private val ai: OnDeviceAi,
    private val settings: AppSettings,
    private val conversations: ConversationRepository,
    appScope: CoroutineScope,
) {
    private val logger = Logger.withTag("ChatTriage")

    private val _awaiting = MutableStateFlow<Map<Long, Long>>(emptyMap())

    /** Chaty čekající na odpověď: chat → id poslední zprávy, nad kterou to model řekl. */
    val awaiting: StateFlow<Map<Long, Long>> = _awaiting.asStateFlow()

    /**
     * Zařazené nebo posouzené zprávy (chat, poslední zpráva), aby se model neptal dvakrát.
     * `StateFlow` kvůli atomické změně: [check] volá ViewModel, [judge] běží ve frontě.
     */
    private val seen = MutableStateFlow<Set<Pair<Long, Long>>>(emptySet())
    private val queue = Channel<Request>(Channel.UNLIMITED)

    private class Request(val chatId: Long, val lastMessageId: Long, val unreadCount: Int)

    init {
        appScope.launch {
            for (request in queue) judge(request)
        }
    }

    /** Zařadí kandidáty ze seznamu (v jeho pořadí, tedy nejčerstvější první). Levné. */
    fun check(chats: List<ChatSummary>, savedMessagesChatId: Long?) {
        if (!enabled()) return
        for (chat in chats) {
            if (!isTriageCandidate(chat, savedMessagesChatId)) continue
            val lastMessageId = chat.lastMessageId ?: continue
            val key = chat.id to lastMessageId
            var added = false
            seen.update { current ->
                added = key !in current
                current + key
            }
            if (added) queue.trySend(Request(chat.id, lastMessageId, chat.unreadCount))
        }
    }

    private fun enabled(): Boolean = settings.chatTriageEnabled.value && settings.onDeviceAiEnabled.value

    private suspend fun judge(request: Request) {
        val key = request.chatId to request.lastMessageId
        val variant = settings.onDeviceAiVariant.value
        val ready = enabled() && runCatching { ai.status(variant) == OnDeviceAiStatus.Ready }.getOrDefault(false)
        if (!ready) {
            // Model zatím není: chat se posoudí při další změně seznamu s hotovým modelem.
            logger.d { "Model ${variant.provider} není připravený, chat ${request.chatId} počká" }
            seen.update { it - key }
            return
        }
        val waits = try {
            val texts = unreadTexts(request) ?: run {
                // Není z čeho soudit (jen fotky bez popisku), nebo seznam předběhl historii:
                // počet nepřečtených dorazí dřív než nová poslední zpráva. Další změna seznamu
                // chat zařadí znovu.
                seen.update { it - key }
                return
            }
            withTimeoutOrNull(TIMEOUT_MS) {
                ai.generateAnswer(
                    variant,
                    TriageAnswer::class,
                    prompt = { structured -> triagePrompt(texts, structured) },
                    fromStructured = ::triageVerdictOf,
                    fromText = ::parseTriageVerdict,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.i(e) { "Chat se nepodařilo posoudit" }
            null
        }
        logger.d { "Chat ${request.chatId}: čeká na odpověď = $waits" }
        _awaiting.update { current ->
            if (waits == true) current + (request.chatId to request.lastMessageId) else current - request.chatId
        }
    }

    /**
     * Nepřečtené příchozí zprávy od nejstarší, jako text (popis u zprávy bez textu: „Fotka").
     * Nejvýš [MAX_MESSAGES] — rozhoduje konec rozhovoru. `null` = není z čeho soudit.
     */
    private suspend fun unreadTexts(request: Request): List<String>? {
        val limit = request.unreadCount.coerceIn(1, MAX_MESSAGES)
        val messages = conversations.history(request.chatId, fromMessageId = 0, limit = limit).getOrNull() ?: return null
        // TDLib vrací od nejnovější; poslední zpráva musí být ta, kvůli které se posuzuje.
        if (messages.firstOrNull()?.id != request.lastMessageId) {
            logger.d { "Chat ${request.chatId}: poslední zpráva se mezitím změnila" }
            return null
        }
        return messages.take(limit)
            .takeWhile { !it.isOutgoing }
            .filterNot { it.isService }
            .reversed()
            .mapNotNull { message -> message.text.ifBlank { message.description?.resolveNow().orEmpty() }.takeIf { it.isNotBlank() } }
            .takeIf { it.isNotEmpty() }
    }

    private companion object {
        const val TIMEOUT_MS = 60_000L

        /** Kolik posledních nepřečtených zpráv model dostane. */
        const val MAX_MESSAGES = 6
    }
}
