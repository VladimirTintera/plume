package eu.tintera.plume

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.accounts.account
import eu.tintera.plume.core.notifications.NotificationIntents
import eu.tintera.plume.core.notifications.NotificationActions
import eu.tintera.plume.core.notifications.ReplyState
import eu.tintera.plume.core.notifications.NotificationPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Akce z notifikace: odpověď, označení za přečtené, ztlumení, odhrnutí a odmítnutí hovoru
 * (i se zprávou).
 *
 * **Proces může být mrtvý nebo zmrazený** — akce z notifikace ho probudí stejně jako push, takže
 * TDLib se teprve rozbíhá (`startKoin` z `MainApplication`) a na přihlášení se čeká. Po dobu
 * `goAsync` systém proces drží naživu; co se do toho okna nevejde, dočeká
 * [ReplyDeliveryWorker]. Zpráva sama se neztratí ani tak: TDLib ji má v databázi a došle ji.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    private val logger = Logger.withTag("NotificationAction")

    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getLongExtra(NotificationIntents.EXTRA_CHAT_ID, 0L)
        val groupId = intent.getIntExtra(NotificationIntents.EXTRA_GROUP_ID, 0)
        if (chatId == 0L) return
        // Účet z notifikace, ne výchozí: id chatu i skupiny znamená něco jen v něm. Mezitím
        // odhlášený účet už nemá komu odpovědět.
        val slot = NotificationIntents.accountOf(intent) ?: return
        val koin = GlobalContext.getOrNull()?.account(slot) ?: return

        when (intent.action) {
            NotificationIntents.ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(NotificationIntents.KEY_REPLY_TEXT)
                    ?.toString()
                    ?.trim()
                if (text.isNullOrEmpty()) return
                val replyTo = intent.longExtra(NotificationIntents.EXTRA_REPLY_TO_MESSAGE_ID)
                val topicId = intent.intExtra(NotificationIntents.EXTRA_TOPIC_ID)
                runAsync { reply(context.applicationContext, koin, slot, chatId, groupId, text, replyTo, topicId) }
            }

            NotificationIntents.ACTION_MARK_READ -> {
                val messageId = intent.longExtra(NotificationIntents.EXTRA_MESSAGE_ID)
                runAsync {
                    koin.get<NotificationActions>().markRead(chatId, messageId, READY_TIMEOUT)
                        .onSuccess { logger.d { "Chat $chatId z notifikace přečten." } }
                }
            }

            NotificationIntents.ACTION_DECLINE_CALL -> {
                val callId = intent.getIntExtra(NotificationIntents.EXTRA_CALL_ID, 0)
                val isVideo = intent.getBooleanExtra(NotificationIntents.EXTRA_IS_VIDEO, false)
                if (callId == 0) return
                runAsync {
                    koin.get<NotificationActions>().declineCall(callId, isVideo, READY_TIMEOUT)
                        // Notifikaci stáhne TDLib: hovor skončil.
                        .onSuccess { logger.d { "Hovor $callId z notifikace odmítnut." } }
                }
            }

            NotificationIntents.ACTION_DECLINE_CALL_WITH_MESSAGE -> {
                val callId = intent.getIntExtra(NotificationIntents.EXTRA_CALL_ID, 0)
                val isVideo = intent.getBooleanExtra(NotificationIntents.EXTRA_IS_VIDEO, false)
                if (callId == 0) return
                val text = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(NotificationIntents.KEY_REPLY_TEXT)
                    ?.toString()
                    ?.trim()
                runAsync { declineWithMessage(context.applicationContext, koin, slot, chatId, groupId, callId, isVideo, text) }
            }

            NotificationIntents.ACTION_DISMISS -> {
                val maxNotificationId = intent.intExtra(NotificationIntents.EXTRA_MAX_NOTIFICATION_ID)
                runAsync {
                    koin.get<NotificationPresenter>().dismissed(groupId)
                    // Bez id od TDLib (jen vlastní odpovědi, řádky po restartu) nemá TDLib co odebrat.
                    if (maxNotificationId != null) {
                        koin.get<NotificationActions>().dismiss(groupId, maxNotificationId, READY_TIMEOUT)
                            .onSuccess { logger.d { "Skupina $groupId odhrnuta po notifikaci $maxNotificationId." } }
                    }
                }
            }

            NotificationIntents.ACTION_MUTE -> runAsync {
                koin.get<NotificationActions>().mute(chatId, READY_TIMEOUT)
                    // Notifikaci stáhne TDLib sám: ztlumený chat z aktivních notifikací vyřadí.
                    .onSuccess { logger.d { "Chat $chatId z notifikace ztlumen na ${NotificationActions.MUTE_DURATION}." } }
            }
        }
    }

    /** Proces drží naživu, dokud [block] neskončí (nejvýš okno `goAsync`). */
    private fun runAsync(block: suspend () -> Unit) {
        val pending = goAsync()
        scope.launch {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun reply(
        context: Context,
        koin: Koin,
        slot: AccountSlot,
        chatId: Long,
        groupId: Int,
        text: String,
        replyTo: Long?,
        topicId: Int?,
    ) {
        val presenter = koin.get<NotificationPresenter>()
        val actions = koin.get<NotificationActions>()
        val started = TimeSource.Monotonic.markNow()
        // Hned: dokud se notifikace nepřekreslí, točí se v ní kolečko.
        presenter.showReply(groupId, text)

        val reply = actions.send(chatId, text, replyTo, READY_TIMEOUT, topicId).getOrElse {
            presenter.showReplyFailed(groupId, text)
            return
        }
        when (actions.await(reply, BUDGET - started.elapsedNow())) {
            ReplyState.Sent -> logger.d { "Odpověď z notifikace odešla za ${started.elapsedNow().inWholeMilliseconds} ms." }
            ReplyState.Failed -> presenter.showReplyFailed(groupId, text)
            ReplyState.Pending -> {
                logger.i { "Odpověď se nestihla potvrdit, dočkám se v úloze." }
                ReplyDeliveryWorker.enqueue(context, slot, reply, groupId, text)
            }
        }
    }

    /**
     * Odmítne hovor a pošle volajícímu [text]. Napřed odmítnutí: volající má přestat čekat hned,
     * zpráva může chvíli trvat. Prázdný text = jen odmítnutí.
     *
     * Notifikace hovoru zmizí s koncem hovoru (stáhne ji TDLib), takže selhání odeslání nemá kde
     * ukázat — jde do logu. Co se nestihne potvrdit, dočká [ReplyDeliveryWorker] jako odpověď.
     */
    private suspend fun declineWithMessage(
        context: Context,
        koin: Koin,
        slot: AccountSlot,
        chatId: Long,
        groupId: Int,
        callId: Int,
        isVideo: Boolean,
        text: String?,
    ) {
        val actions = koin.get<NotificationActions>()
        val started = TimeSource.Monotonic.markNow()
        actions.declineCall(callId, isVideo, READY_TIMEOUT)
            .onSuccess { logger.d { "Hovor $callId z notifikace odmítnut se zprávou." } }
        // Po přímé odpovědi ji Android 15+ nepustí jen tak (viz `dismissRepliedCall`).
        koin.get<NotificationPresenter>().dismissRepliedCall(groupId)
        if (text.isNullOrEmpty()) return
        val reply = actions.send(chatId, text, replyToMessageId = null, READY_TIMEOUT).getOrElse { return }
        when (actions.await(reply, BUDGET - started.elapsedNow())) {
            ReplyState.Sent -> logger.d { "Zpráva k odmítnutému hovoru odešla." }
            ReplyState.Failed -> logger.w { "Zprávu k odmítnutému hovoru server odmítl." }
            ReplyState.Pending -> ReplyDeliveryWorker.enqueue(context, slot, reply, groupId, text)
        }
    }

    private fun Intent.longExtra(key: String): Long? = getLongExtra(key, 0L).takeIf { it != 0L }

    private fun Intent.intExtra(key: String): Int? = if (hasExtra(key)) getIntExtra(key, 0) else null

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /** Na rozběhnutí TDLib po probuzení mrtvého procesu. */
        val READY_TIMEOUT = 5.seconds

        /**
         * Celé okno `goAsync`. Systém dává příjemci 10 s, pak aplikaci ukončí jako
         * neodpovídající — rezerva je na překreslení notifikace a zařazení úlohy.
         */
        val BUDGET = 8.seconds
    }
}
