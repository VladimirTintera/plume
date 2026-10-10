package eu.tintera.plume

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import eu.tintera.plume.app.shell.BubbleShell
import eu.tintera.plume.core.notifications.NotificationIntents

/**
 * Konverzace v bublině (Android 11+), spuštěná z notifikace (`NotificationIntents.bubble`).
 *
 * **Vlastní aktivita, ne [MainActivity].** Ta je `singleTask` a bublina by v ní otevřela celou
 * aplikaci; bublina chce aktivitu, která se dá vložit do okna bubliny (`allowEmbedded`,
 * `resizeableActivity`) a pro každý chat vlastní dokument (`documentLaunchMode="always"`).
 * Obsah je [BubbleShell] pod týmž kořenem jako aplikace ([AppRoot]) — motiv, přihlášení
 * a zámek aplikace platí i tady.
 */
class BubbleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val chatId = intent.getLongExtra(NotificationIntents.EXTRA_CHAT_ID, 0L).takeIf { it != 0L }
            ?: intent.data?.lastPathSegment?.toLongOrNull()
        if (chatId == null) {
            finish()
            return
        }

        setContent {
            AppRoot {
                BubbleShell(
                    chatId = chatId,
                    onOpenInApp = ::openInApp,
                    // Šipka zpět bublinu SBALÍ, nezruší. `finish()` by ukončil úlohu bubliny
                    // a systém by bublinu odebral úplně (`reason=TASK_FINISHED`); přesun úlohy
                    // dozadu je totéž, co od Androidu 12 dělá systémové Zpět na kořeni.
                    onClose = { moveTaskToBack(true) },
                )
            }
        }
    }

    /** Co bublina neumí (profil, přeposlání, jiný chat…), pokračuje v plné aplikaci na tom chatu. */
    private fun openInApp(chatId: Long) {
        NotificationIntents.chatIntent(this, chatId)?.let(::startActivity)
    }
}
