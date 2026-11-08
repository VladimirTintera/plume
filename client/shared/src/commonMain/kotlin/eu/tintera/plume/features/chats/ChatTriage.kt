package eu.tintera.plume.features.chats

import eu.tintera.plume.core.ai.proactiveVariant
import eu.tintera.plume.core.ai.provider
import eu.tintera.ai.core.NoDeviceLoad
import eu.tintera.ai.core.DeviceLoad
import eu.tintera.plume.core.ai.AiTask
import co.touchlab.kermit.Logger
import eu.tintera.ai.core.OnDeviceAi
import eu.tintera.ai.core.OnDeviceAiStatus
import eu.tintera.plume.core.ai.TriageAnswer
import eu.tintera.ai.core.generateAnswer
import eu.tintera.plume.core.ai.parseTriageVerdict
import eu.tintera.plume.core.ai.triagePrompt
import eu.tintera.plume.core.ai.triageVerdictOf
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.StoredTriageVerdict
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatMessage
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
import kotlin.time.Clock

/**
 * Posuzuje se chat vůbec? Nepřečtený chat, kde odpověď dluží uživatel:
 *
 * - **soukromý**, ve kterém poslední zprávu poslal protějšek (ne ztlumený — o ten uživatel
 *   sám nestojí),
 * - **skupina s nepřečtenou zmínkou nebo odpovědí na vlastní zprávu** (`unreadMentionCount`),
 *   i ztlumená: zmínky Telegram hlásí i ze ztlumené skupiny. Bez zmínky ne — z ostatních
 *   nepřečtených se nepozná, KOHO se otázka týká, a odznak u skupiny, kde se někdo ptal všech,
 *   by byl šum.
 *
 * Kanály, Uložené zprávy ([savedMessagesChatId]), ověřené účty (Telegram, služby)
 * a sponzorované chaty nikdy.
 */
fun isTriageCandidate(chat: ChatSummary, savedMessagesChatId: Long?): Boolean {
    if (chat.unreadCount == 0 || chat.lastMessageId == null || chat.sponsor != null) return false
    if (chat.id == savedMessagesChatId || chat.verification is Verification.Verified) return false
    return when (chat.kind) {
        ChatKind.Private -> !chat.lastMessageOutgoing && !chat.isMuted
        ChatKind.Group -> chat.unreadMentionCount > 0
        ChatKind.Channel -> false
    }
}

/**
 * Chaty na kartu „Čeká na tvou odpověď", v pořadí seznamu: nepřečtené, u kterých triáž řekla
 * „čeká" nad jejich DNEŠNÍ poslední zprávou ([awaiting] = chat → id posouzené zprávy).
 * Prázdné, když jsou všechny mezi [hidden] (křížek na kartě) — přibude-li chat, který tam není,
 * karta se vrátí celá.
 */
fun awaitingReplyChats(
    chats: List<ChatSummary>,
    awaiting: Map<Long, Long>,
    hidden: Set<Pair<Long, Long>>,
): List<ChatSummary> {
    val waiting = chats.filter { chat ->
        chat.unreadCount > 0 && chat.lastMessageId != null && awaiting[chat.id] == chat.lastMessageId
    }
    return if (waiting.all { (it.id to it.lastMessageId) in hidden }) emptyList() else waiting
}

/**
 * Triáž chatů: které nepřečtené chaty čekají na odpověď uživatele (soukromé a skupiny se
 * zmínkou, [isTriageCandidate]) — posouzení modelem v zařízení. V seznamu chatů z toho je odznak.
 *
 * - **Obsah zpráv nikam neodchází**: posuzuje model v telefonu.
 * - **Jen se zapnutou volbou, zapnutou AI a modelem, který je PŘIPRAVENÝ.** Kvůli triáži se nic
 *   nestahuje.
 * - **Výsledek patří POSLEDNÍ ZPRÁVĚ**, ne chatu ([awaiting] = chat → id posouzené zprávy).
 *   Nová zpráva znamená nové posouzení; seznam ukazuje odznak, jen když id sedí. Přečtení
 *   chatu ho schová samo — nepřečtený chat přestane být kandidátem.
 * - **Model se ptá po jednom chatu**, frontou v [appScope] — jako u [eu.tintera.plume.features.conversation.ScamWarnings].
 *   Seznam se mění při každé zprávě, takže [check] je levný a opakované volání nic nestojí.
 * - **Posudky se ukládají** ([StoredTriageVerdict] v nastavení, po účtech, nejvýš [MAX_STORED]):
 *   po restartu se použijí bez modelu a posuzuje se jen to, co přibylo.
 * - **Chyba modelu = žádný odznak.**
 */
class ChatTriage(
    private val ai: OnDeviceAi,
    private val settings: AppSettings,
    private val conversations: ConversationRepository,
    appScope: CoroutineScope,
    /** Úsporný režim a teplota: Gemma pod nimi sama od sebe nic nepočítá ([proactiveVariant]). */
    private val load: DeviceLoad = NoDeviceLoad,
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

    /** [account] = id Uložených zpráv přihlášeného účtu (klíč uložených posudků). */
    private class Request(val chatId: Long, val lastMessageId: Long, val unreadCount: Int, val group: Boolean, val account: Long?)

    /** Co se posuzuje: zpráva, ke které posudek patří, a texty pro model. */
    private class Subject(val messageId: Long, val texts: List<String>)

    init {
        appScope.launch {
            for (request in queue) judge(request)
        }
    }

    /**
     * Zařadí kandidáty ze seznamu (v jeho pořadí, tedy nejčerstvější první). Levné. Soukromý
     * chat s uloženým posudkem poslední zprávy se nezařadí vůbec — posudek se použije rovnou.
     */
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
            if (!added) continue
            val group = chat.kind == ChatKind.Group
            val stored = if (group) null else storedVerdict(savedMessagesChatId, chat.id, lastMessageId)
            if (stored != null) {
                apply(chat.id, lastMessageId, stored)
            } else {
                queue.trySend(Request(chat.id, lastMessageId, chat.unreadCount, group, savedMessagesChatId))
            }
        }
    }

    private fun enabled(): Boolean = settings.chatTriageEnabled.value && settings.onDeviceAiEnabled.value

    private suspend fun judge(request: Request) {
        val key = request.chatId to request.lastMessageId
        val subject = try {
            if (request.group) mentionSubject(request) else privateSubject(request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.i(e) { "Zprávy chatu ${request.chatId} se nepodařilo načíst" }
            null
        }
        if (subject == null) {
            // Není z čeho soudit (jen fotky bez popisku), nebo seznam předběhl historii: počet
            // nepřečtených dorazí dřív než nová poslední zpráva. Další změna seznamu chat zařadí.
            seen.update { it - key }
            return
        }
        // Ve skupině patří posudek nejnovější zmínce: běžný provoz ve skupině model znovu
        // nepouští, dokud nepřibude zmínka.
        storedVerdict(request.account, request.chatId, subject.messageId)?.let { stored ->
            apply(request.chatId, request.lastMessageId, stored)
            return
        }
        val variant = ai.proactiveVariant(AiTask.Triage, settings, load)
        val ready = variant != null && enabled() && runCatching { ai.status(variant) == OnDeviceAiStatus.Ready }.getOrDefault(false)
        if (variant == null || !ready) {
            // Model zatím není (nebo je telefon pod zátěží a model je Gemma): chat se posoudí
            // při další změně seznamu.
            logger.d { "Model ${variant?.provider ?: "pod zátěží"} není připravený, chat ${request.chatId} počká" }
            seen.update { it - key }
            return
        }
        val waits = try {
            withTimeoutOrNull(TIMEOUT_MS) {
                ai.generateAnswer(
                    variant,
                    TriageAnswer::class,
                    prompt = { structured -> triagePrompt(subject.texts, structured, group = request.group) },
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
        // Chyba modelu (null) se neukládá: po restartu se chat zkusí znovu.
        if (waits != null) store(request.account, request.chatId, subject.messageId, waits)
        apply(request.chatId, request.lastMessageId, waits == true)
    }

    private fun apply(chatId: Long, lastMessageId: Long, waits: Boolean) {
        _awaiting.update { current -> if (waits) current + (chatId to lastMessageId) else current - chatId }
    }

    private fun storedVerdict(account: Long?, chatId: Long, messageId: Long): Boolean? {
        account ?: return null
        return settings.storedTriageVerdicts.value
            .firstOrNull { it.accountId == account && it.chatId == chatId && it.messageId == messageId }
            ?.waits
    }

    private fun store(account: Long?, chatId: Long, messageId: Long, waits: Boolean) {
        account ?: return
        val verdict = StoredTriageVerdict(account, chatId, messageId, waits, Clock.System.now().toEpochMilliseconds())
        settings.setStoredTriageVerdicts(
            (settings.storedTriageVerdicts.value.filterNot { it.accountId == account && it.chatId == chatId } + verdict)
                .sortedBy { it.savedAt }
                .takeLast(MAX_STORED),
        )
    }

    /**
     * Soukromý chat: nepřečtené příchozí zprávy od nejstarší, jako text (popis u zprávy bez
     * textu: „Fotka"). Nejvýš [MAX_MESSAGES] — rozhoduje konec rozhovoru. Posudek patří
     * poslední zprávě. `null` = není z čeho soudit.
     */
    private suspend fun privateSubject(request: Request): Subject? {
        val limit = request.unreadCount.coerceIn(1, MAX_MESSAGES)
        val messages = conversations.history(request.chatId, fromMessageId = 0, limit = limit).getOrNull() ?: return null
        // TDLib vrací od nejnovější; poslední zpráva musí být ta, kvůli které se posuzuje.
        if (messages.firstOrNull()?.id != request.lastMessageId) {
            logger.d { "Chat ${request.chatId}: poslední zpráva se mezitím změnila" }
            return null
        }
        val texts = messages.take(limit)
            .takeWhile { !it.isOutgoing }
            .filterNot { it.isService }
            .reversed()
            .mapNotNull { message -> message.readableText() }
            .takeIf { it.isNotEmpty() } ?: return null
        return Subject(request.lastMessageId, texts)
    }

    /**
     * Skupina: nepřečtené zmínky a odpovědi na vlastní zprávy (nejvýš [MAX_MESSAGES]), od
     * nejstarší, se jménem odesílatele. Posudek patří nejnovější z nich.
     */
    private suspend fun mentionSubject(request: Request): Subject? {
        val mentions = conversations.unreadMentions(request.chatId, MAX_MESSAGES).getOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        val texts = mentions
            .filterNot { it.isService || it.isOutgoing }
            .reversed()
            .mapNotNull { message ->
                message.readableText()?.let { text -> message.senderName?.ifBlank { null }?.let { "$it: $text" } ?: text }
            }
            .takeIf { it.isNotEmpty() } ?: return null
        return Subject(mentions.first().id, texts)
    }

    private suspend fun ChatMessage.readableText(): String? =
        text.ifBlank { description?.resolveNow().orEmpty() }.takeIf { it.isNotBlank() }

    private companion object {
        const val TIMEOUT_MS = 60_000L

        /** Kolik posledních nepřečtených zpráv (zmínek) model dostane. */
        const val MAX_MESSAGES = 6

        /** Kolik posudků se pamatuje přes restart; víc nepřečtených chatů nikdo nemá. */
        const val MAX_STORED = 300
    }
}
