package eu.tintera.plume

import eu.tintera.plume.core.accounts.AccountSlot
import android.os.Bundle
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import eu.tintera.plume.core.locale.LegacyAppLanguage
import android.content.Context
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

    // Jazyk aplikace do Androidu 12 (od 13 ho aktivitě dává systém). Viz LegacyAppLanguage.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        LegacyAppLanguage.overrideConfiguration(newBase)?.let(::applyOverrideConfiguration)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Změna jazyka do Androidu 12: přeloží se až nově vytvořená aktivita (od 13 to dělá systém).
        lifecycleScope.launch { LegacyAppLanguage.changes.collect { recreate() } }

        val slot = NotificationIntents.accountOf(intent)
        val chatId = intent.getLongExtra(NotificationIntents.EXTRA_CHAT_ID, 0L).takeIf { it != 0L }
            ?: intent.data?.lastPathSegment?.toLongOrNull()
        if (chatId == null || slot == null) {
            finish()
            return
        }

        setContent {
            AppRoot {
                BubbleShell(
                    slot = slot,
                    chatId = chatId,
                    onOpenInApp = { openInApp(slot, it) },
                    // Šipka zpět bublinu SBALÍ, nezruší. `finish()` by ukončil úlohu bubliny
                    // a systém by bublinu odebral úplně (`reason=TASK_FINISHED`); přesun úlohy
                    // dozadu je totéž, co od Androidu 12 dělá systémové Zpět na kořeni.
                    onClose = { moveTaskToBack(true) },
                )
            }
        }
    }

    /** Co bublina neumí (profil, přeposlání, jiný chat…), pokračuje v plné aplikaci na tom chatu. */
    private fun openInApp(slot: AccountSlot, chatId: Long) {
        NotificationIntents.chatIntent(this, slot, chatId)?.let(::startActivity)
    }
}
