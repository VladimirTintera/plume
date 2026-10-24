package eu.tintera.plume.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.di.APP_SCOPE
import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.telegram.TelegramSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.context.GlobalContext
import org.koin.core.qualifier.named
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val logger = Logger.withTag("WearNotificationActions")

/** `goAsync` dává přijímači zhruba deset vteřin; s rezervou na úklid. */
private val ACTION_TIMEOUT = 9.seconds

private val CONFIRM_POLL = 300.milliseconds

/**
 * Akce z notifikace na hodinkách. Probudí i mrtvý proces (jako push): `WearApplication` rozběhne
 * TDLib a akce počká na `AuthStage.Ready`.
 *
 * - **Odpovědět:** vlastní řádek do notifikace hned, odeslání, a **přečtení až po potvrzení od
 *   serveru** — kdyby se četlo hned a odeslání selhalo, notifikace by zmizela a nebylo by kde
 *   ukázat „Neodesláno". Čte se zpráva nejnovější v notifikaci PŘED odesláním. Potvrzení se zjistí
 *   dotazem na zprávu: TDLib ji po potvrzení přečísluje, takže nenalezené dočasné id = odesláno.
 * - **Přečteno:** přečte po nejnovější zprávu notifikace; notifikaci stáhne TDLib, ručně se neruší.
 *
 * Telefon má na pomalé potvrzení úlohu WorkManageru; hodinky zatím ne — co se nestihne do
 * [ACTION_TIMEOUT], zůstane odeslané, ale nepřečtené (notifikace s vlastním řádkem zůstane).
 */
class WearNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val koin = GlobalContext.getOrNull() ?: return
        val groupId = intent.getIntExtra(EXTRA_GROUP_ID, 0)
        val chatId = intent.getLongExtra(EXTRA_CHAT_ID, 0L)
        val newestMessageId = intent.getLongExtra(EXTRA_MESSAGE_ID, 0L)
        val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(EXTRA_REPLY)?.toString()?.trim()
        if (chatId == 0L) return

        val pending = goAsync()
        koin.get<CoroutineScope>(named(APP_SCOPE)).launch {
            try {
                withTimeoutOrNull(ACTION_TIMEOUT) {
                    koin.get<TelegramSession>().authStage.first { it == AuthStage.Ready }
                    val conversations = koin.get<ConversationRepository>()
                    when (intent.action) {
                        ACTION_REPLY -> if (!reply.isNullOrEmpty()) reply(koin.get(), conversations, groupId, chatId, newestMessageId, reply)
                        ACTION_READ -> if (newestMessageId != 0L) {
                            conversations.markRead(chatId, listOf(newestMessageId))
                                .onFailure { logger.w(it) { "Přečtení chatu $chatId z notifikace neprošlo." } }
                        }
                    }
                } ?: logger.i { "Akce z notifikace (${intent.action}) se nestihla do $ACTION_TIMEOUT." }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun reply(
        notifications: WearNotifications,
        conversations: ConversationRepository,
        groupId: Int,
        chatId: Long,
        newestMessageId: Long,
        text: String,
    ) {
        notifications.showReply(groupId, text)
        val sent = conversations.send(chatId, text, replyToMessageId = null).getOrElse {
            logger.w(it) { "Odpověď z notifikace do chatu $chatId neodešla." }
            notifications.showReplyFailed(groupId, text)
            return
        }
        // Potvrzení dotazem: přečíslovaná (nenalezená) dočasná zpráva = odesláno.
        while (true) {
            val current = conversations.message(chatId, sent.id).getOrNull()
            when {
                current == null || current.sendState == SendState.Sent -> break
                current.sendState == SendState.Failed -> {
                    notifications.showReplyFailed(groupId, text)
                    return
                }
            }
            delay(CONFIRM_POLL)
        }
        if (newestMessageId != 0L) {
            conversations.markRead(chatId, listOf(newestMessageId))
                .onFailure { logger.w(it) { "Přečtení chatu $chatId po odpovědi neprošlo." } }
        }
    }

    companion object {
        const val ACTION_REPLY = "eu.tintera.plume.wear.REPLY"
        const val ACTION_READ = "eu.tintera.plume.wear.READ"
        const val EXTRA_REPLY = "reply"
        private const val EXTRA_GROUP_ID = "groupId"
        private const val EXTRA_CHAT_ID = "chatId"
        private const val EXTRA_MESSAGE_ID = "messageId"

        fun intent(context: Context, action: String, groupId: Int, chatId: Long, newestMessageId: Long): Intent =
            Intent(context, WearNotificationReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_GROUP_ID, groupId)
                .putExtra(EXTRA_CHAT_ID, chatId)
                .putExtra(EXTRA_MESSAGE_ID, newestMessageId)
    }
}
