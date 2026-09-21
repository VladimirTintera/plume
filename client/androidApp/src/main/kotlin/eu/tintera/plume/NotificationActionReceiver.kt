package eu.tintera.plume

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.notifications.NotificationIntents
import eu.tintera.plume.core.notifications.NotificationActions
import eu.tintera.plume.core.notifications.ReplyState
import eu.tintera.plume.core.telegram.NotificationPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.Koin
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Akce z notifikace: odpověď a označení za přečtené.
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
        val koin = GlobalContext.getOrNull() ?: return

        when (intent.action) {
            NotificationIntents.ACTION_REPLY -> {
                val text = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(NotificationIntents.KEY_REPLY_TEXT)
                    ?.toString()
                    ?.trim()
                if (text.isNullOrEmpty()) return
                val replyTo = intent.longExtra(NotificationIntents.EXTRA_REPLY_TO_MESSAGE_ID)
                runAsync { reply(context.applicationContext, koin, chatId, groupId, text, replyTo) }
            }

            NotificationIntents.ACTION_MARK_READ -> {
                val messageId = intent.longExtra(NotificationIntents.EXTRA_MESSAGE_ID)
                runAsync {
                    koin.get<NotificationActions>().markRead(chatId, messageId, READY_TIMEOUT)
                        .onSuccess { logger.d { "Chat $chatId z notifikace přečten." } }
                }
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

    private suspend fun reply(context: Context, koin: Koin, chatId: Long, groupId: Int, text: String, replyTo: Long?) {
        val presenter = koin.get<NotificationPresenter>()
        val actions = koin.get<NotificationActions>()
        val started = TimeSource.Monotonic.markNow()
        // Hned: dokud se notifikace nepřekreslí, točí se v ní kolečko.
        presenter.showReply(groupId, text)

        val reply = actions.send(chatId, text, replyTo, READY_TIMEOUT).getOrElse {
            presenter.showReplyFailed(groupId, text)
            return
        }
        when (actions.await(reply, BUDGET - started.elapsedNow())) {
            ReplyState.Sent -> logger.d { "Odpověď z notifikace odešla za ${started.elapsedNow().inWholeMilliseconds} ms." }
            ReplyState.Failed -> presenter.showReplyFailed(groupId, text)
            ReplyState.Pending -> {
                logger.i { "Odpověď se nestihla potvrdit, dočkám se v úloze." }
                ReplyDeliveryWorker.enqueue(context, reply, groupId, text)
            }
        }
    }

    private fun Intent.longExtra(key: String): Long? = getLongExtra(key, 0L).takeIf { it != 0L }

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
