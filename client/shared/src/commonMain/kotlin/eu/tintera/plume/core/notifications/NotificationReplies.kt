package eu.tintera.plume.core.notifications

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Odpověď přímo z notifikace.
 *
 * Platformní část (akce v notifikaci, příjem textu, udržení procesu naživu) je jinde; tady je
 * jen to, co se děje v TDLib: odeslat a dočkat se potvrzení.
 *
 * **Chat se označí za přečtený až po potvrzení od serveru.** Přečtení notifikaci stáhne
 * (TDLib pošle `removedNotificationIds`) — kdyby se to stalo hned a odeslání pak selhalo,
 * nezbylo by nic, na čem by se to dalo ukázat.
 */
class NotificationReplies(
    private val session: TelegramSession,
    private val conversations: ConversationRepository,
) {

    private val logger = Logger.withTag("NotificationReply")

    /**
     * Odešle [text] a vrátí rozepsanou zprávu. TDLib ji má uloženou v databázi a došle ji sám,
     * i kdyby proces mezitím zamrzl — potvrzení hlídá [await].
     *
     * [replyToMessageId] je citovaná zpráva — ve skupině nejnovější z notifikace, jinak `null`.
     *
     * Akce z notifikace umí probudit mrtvý proces; TDLib se tedy může teprve spouštět, a proto
     * se nejdřív čeká na přihlášení (nejvýš [timeout]).
     */
    suspend fun send(chatId: Long, text: String, replyToMessageId: Long?, timeout: Duration): Result<PendingReply> {
        val ready = withTimeoutOrNull(timeout) { session.authStage.first { it == AuthStage.Ready } }
        if (ready == null) return Result.failure(IllegalStateException("TDLib není přihlášený do $timeout."))

        // Nejnovější zpráva PŘED odesláním: po něm by jí byla naše odpověď, a přečtení vlastní
        // zprávy nic neoznačí.
        val readUpTo = conversations.history(chatId = chatId, fromMessageId = 0, limit = 1)
            .getOrNull()
            ?.firstOrNull()
            ?.id

        return conversations.send(chatId = chatId, text = text, replyToMessageId = replyToMessageId)
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

    private companion object {
        val POLL_INTERVAL = 500.milliseconds
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
