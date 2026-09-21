package eu.tintera.plume

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.notifications.NotificationIntents
import eu.tintera.plume.core.notifications.NotificationReplies
import eu.tintera.plume.core.notifications.ReplyState
import eu.tintera.plume.core.telegram.NotificationPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Odpověď napsaná přímo v notifikaci.
 *
 * **Proces může být mrtvý nebo zmrazený** — akce z notifikace ho probudí stejně jako push, takže
 * TDLib se teprve rozbíhá (`startKoin` z `MainApplication`) a na přihlášení se čeká. Po dobu
 * `goAsync` systém proces drží naživu; co se do toho okna nevejde, dočeká
 * [ReplyDeliveryWorker]. Zpráva sama se neztratí ani tak: TDLib ji má v databázi a došle ji.
 */
class NotificationReplyReceiver : BroadcastReceiver() {

    private val logger = Logger.withTag("NotificationReply")

    override fun onReceive(context: Context, intent: Intent) {
        val text = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(NotificationIntents.KEY_REPLY_TEXT)
            ?.toString()
            ?.trim()
        val chatId = intent.getLongExtra(NotificationIntents.EXTRA_CHAT_ID, 0L)
        val groupId = intent.getIntExtra(NotificationIntents.EXTRA_GROUP_ID, 0)
        if (text.isNullOrEmpty() || chatId == 0L) return

        val koin = GlobalContext.getOrNull() ?: return
        val presenter = koin.get<NotificationPresenter>()
        val replies = koin.get<NotificationReplies>()
        val appContext = context.applicationContext

        val pending = goAsync()
        scope.launch {
            try {
                val started = TimeSource.Monotonic.markNow()
                // Hned: dokud se notifikace nepřekreslí, točí se v ní kolečko.
                presenter.showReply(groupId, text)

                val reply = replies.send(chatId, text, READY_TIMEOUT).getOrElse {
                    presenter.showReplyFailed(groupId, text)
                    return@launch
                }
                when (replies.await(reply, BUDGET - started.elapsedNow())) {
                    ReplyState.Sent -> logger.d { "Odpověď z notifikace odešla za ${started.elapsedNow().inWholeMilliseconds} ms." }
                    ReplyState.Failed -> presenter.showReplyFailed(groupId, text)
                    ReplyState.Pending -> {
                        logger.i { "Odpověď se nestihla potvrdit, dočkám se v úloze." }
                        ReplyDeliveryWorker.enqueue(appContext, reply, groupId, text)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

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
