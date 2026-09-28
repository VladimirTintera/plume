package eu.tintera.plume.core.notifications

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

/**
 * Akce přímo z notifikace: odpověď, označení za přečtené a ztlumení.
 *
 * Platformní část (akce v notifikaci, příjem textu, udržení procesu naživu) je jinde; tady je
 * jen to, co se děje v TDLib.
 *
 * Akce z notifikace umí probudit mrtvý proces; TDLib se tedy může teprve spouštět, a proto se
 * u každé nejdřív čeká na přihlášení.
 *
 * **Chat se označí za přečtený až po potvrzení od serveru.** Přečtení notifikaci stáhne
 * (TDLib pošle `removedNotificationIds`) — kdyby se to stalo hned a odeslání pak selhalo,
 * nezbylo by nic, na čem by se to dalo ukázat.
 */
class NotificationActions(
    private val session: TelegramSession,
    private val conversations: ConversationRepository,
    private val chats: ChatRepository,
) {

    private val logger = Logger.withTag("NotificationReply")

    /**
     * Odešle [text] a vrátí rozepsanou zprávu. TDLib ji má uloženou v databázi a došle ji sám,
     * i kdyby proces mezitím zamrzl — potvrzení hlídá [await].
     *
     * [replyToMessageId] je citovaná zpráva — ve skupině nejnovější z notifikace, jinak `null`.
     *
     * [topicId] je téma skupiny s tématy — bez něj by odpověď skončila v obecném tématu.
     *
     * Na přihlášení se čeká nejvýš [timeout].
     */
    suspend fun send(
        chatId: Long,
        text: String,
        replyToMessageId: Long?,
        timeout: Duration,
        topicId: Int? = null,
    ): Result<PendingReply> {
        if (!awaitReady(timeout)) return Result.failure(IllegalStateException("TDLib není přihlášený do $timeout."))

        // Nejnovější zpráva PŘED odesláním: po něm by jí byla naše odpověď, a přečtení vlastní
        // zprávy nic neoznačí.
        val readUpTo = newestMessageId(chatId)

        val target = topicId?.let { conversations.inForumTopic(it) } ?: conversations
        return target.send(chatId = chatId, text = text, replyToMessageId = replyToMessageId)
            .map { PendingReply(chatId = chatId, messageId = it.id, readUpTo = readUpTo) }
            .onFailure { logger.w(it) { "Odpověď z notifikace se nepodařilo odeslat." } }
    }

    /**
     * Počká, až server odpověď potvrdí nebo odmítne, nejvýš [timeout].
     *
     * **Zjišťuje se dotazem na zprávu, ne odběrem updatů.** Úloha, která na potvrzení čeká po
     * vypršení okna příjemce akce, začíná pozdě a update o úspěchu mohl dávno proletět. Stav
     * zprávy se naopak dá přečíst kdykoli: TDLib ji po potvrzení přečísluje, takže dočasné id
     * přestane existovat — a nikdo jiný než my ji mezitím smazat nemohl.
     */
    suspend fun await(reply: PendingReply, timeout: Duration): ReplyState {
        val state = withTimeoutOrNull(timeout) { settled(reply) } ?: ReplyState.Pending

        if (state == ReplyState.Sent) {
            reply.readUpTo?.let { messageId ->
                conversations.markRead(reply.chatId, listOf(messageId))
                    .onFailure { logger.w(it) { "Chat se po odpovědi nepodařilo označit za přečtený." } }
            }
        }
        return state
    }

    /**
     * Označí chat za přečtený až po [messageId] — nejnovější zprávu, kterou notifikace ukazovala.
     * Ne „až po nejnovější v chatu": co mezitím přišlo, uživatel neviděl a notifikace o tom
     * přijde znovu.
     *
     * `null` = notifikace z minulého běhu procesu, jejíž řádky id zpráv nenesou; pak se čte po
     * nejnovější zprávu v chatu — v tu chvíli je to skoro vždycky táž.
     *
     * TDLib notifikaci po přečtení stáhne sám (`removedNotificationIds`), rušit ji není potřeba.
     * Přečtení drží TDLib v databázi a na server ho pošle, až bude spojení, takže se na nic
     * dalšího nečeká.
     */
    suspend fun markRead(chatId: Long, messageId: Long?, timeout: Duration): Result<Unit> {
        if (!awaitReady(timeout)) return Result.failure(IllegalStateException("TDLib není přihlášený do $timeout."))
        val upTo = messageId ?: newestMessageId(chatId)
            ?: return Result.failure(IllegalStateException("V chatu $chatId není co přečíst."))
        return conversations.markRead(chatId, listOf(upTo))
            .onFailure { logger.w(it) { "Chat se z notifikace nepodařilo označit za přečtený." } }
    }

    /**
     * Ztlumí chat na [MUTE_DURATION]. Kratší ztlumení je z notifikace to, co člověk chce: skupina
     * se zrovna rozjela a za hodinu už nevadí. Natrvalo jde ztlumit v aplikaci; tady by se
     * snadno zapomnělo.
     */
    suspend fun mute(chatId: Long, timeout: Duration): Result<Unit> {
        if (!awaitReady(timeout)) return Result.failure(IllegalStateException("TDLib není přihlášený do $timeout."))
        return chats.muteFor(chatId, MUTE_DURATION)
            .onFailure { logger.w(it) { "Chat se z notifikace nepodařilo ztlumit." } }
    }

    private suspend fun awaitReady(timeout: Duration): Boolean =
        withTimeoutOrNull(timeout) { session.authStage.first { it == AuthStage.Ready } } != null

    private suspend fun newestMessageId(chatId: Long): Long? =
        conversations.history(chatId = chatId, fromMessageId = 0, limit = 1).getOrNull()?.firstOrNull()?.id

    private suspend fun settled(reply: PendingReply): ReplyState {
        while (true) {
            val message = conversations.message(reply.chatId, reply.messageId).getOrNull()
            when (message?.sendState) {
                SendState.Sending -> delay(POLL_INTERVAL)
                SendState.Failed -> return ReplyState.Failed
                // Nenalezená = přečíslovaná, tedy potvrzená.
                SendState.Sent, null -> return ReplyState.Sent
            }
        }
    }

    companion object {
        private val POLL_INTERVAL = 500.milliseconds

        /** Na jak dlouho ztlumí akce z notifikace. Popisek akce ji musí pojmenovat. */
        val MUTE_DURATION = 1.hours
    }
}

/** Odeslaná, ještě nepotvrzená odpověď. [readUpTo] = zpráva, po kterou se chat označí za přečtený. */
data class PendingReply(val chatId: Long, val messageId: Long, val readUpTo: Long?)

enum class ReplyState {
    Sent,
    Failed,

    /** Server se do vypršení čekání neozval; TDLib zprávu dál drží a došle ji. */
    Pending,
}
