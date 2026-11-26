package eu.tintera.plume.core.files

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.accounts.Accounts
import eu.tintera.plume.core.telegram.DownloadsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * „Pozastavit stahování" z notifikace přenosů ([UploadKeepAliveWorker]): pozastaví seznam
 * Stahování ve všech účtech, jako „Pozastavit vše" na obrazovce Stahování. Stahování tím
 * přestane být aktivní a úloha s notifikací skončí sama; pokračovat jde ze Stahování.
 *
 * Neexportovaný (`AndroidManifest.xml` vstupního bodu): broadcast posílá jen náš `PendingIntent`.
 */
class PauseDownloadsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val accounts = GlobalContext.getOrNull()?.getOrNull<Accounts>() ?: return
        val pending = goAsync()
        scope.launch {
            try {
                accounts.all.value.forEach { account ->
                    account.get<DownloadsRepository>().setAllPaused(true)
                        .onFailure { logger.w(it) { "Stahování z notifikace nejde pozastavit." } }
                }
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        private val logger = Logger.withTag("Transfers")
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun intent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            UploadKeepAliveWorker.NOTIFICATION_ID,
            Intent(context, PauseDownloadsReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
