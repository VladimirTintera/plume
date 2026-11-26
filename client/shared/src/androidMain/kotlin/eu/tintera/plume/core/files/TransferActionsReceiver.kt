package eu.tintera.plume.core.files

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.accounts.Accounts
import eu.tintera.plume.core.telegram.DownloadsRepository
import eu.tintera.plume.core.telegram.TransferMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Tlačítka notifikace přenosů ([UploadKeepAliveWorker]), vždy ve všech účtech — notifikace je
 * jedna přes všechny:
 * - „Zrušit odesílání" smaže rozesílané zprávy s nahrávanými soubory, jako křížek u zprávy
 *   ([TransferMonitor.cancelUploads]).
 * - „Pozastavit stahování" pozastaví seznam Stahování, jako „Pozastavit vše" na obrazovce
 *   Stahování; pokračovat jde odtamtud.
 *
 * Přenos tím skončí a úloha s notifikací doběhne sama.
 *
 * Neexportovaný (`AndroidManifest.xml` vstupního bodu): broadcast posílá jen náš `PendingIntent`.
 */
class TransferActionsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        logger.i { "Akce z notifikace přenosů: $action" }
        val accounts = GlobalContext.getOrNull()?.getOrNull<Accounts>() ?: return
        val pending = goAsync()
        scope.launch {
            try {
                accounts.all.value.forEach { account ->
                    when (action) {
                        ACTION_CANCEL_UPLOADS -> account.get<TransferMonitor>().cancelUploads()
                        ACTION_PAUSE_DOWNLOADS -> account.get<DownloadsRepository>().setAllPaused(true)
                            .onFailure { logger.w(it) { "Stahování z notifikace nejde pozastavit." } }
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        private const val ACTION_CANCEL_UPLOADS = "eu.tintera.plume.action.CANCEL_UPLOADS"
        private const val ACTION_PAUSE_DOWNLOADS = "eu.tintera.plume.action.PAUSE_DOWNLOADS"

        private val logger = Logger.withTag("Transfers")
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun cancelUploads(context: Context): PendingIntent = intent(context, ACTION_CANCEL_UPLOADS, requestCode = 1)

        fun pauseDownloads(context: Context): PendingIntent = intent(context, ACTION_PAUSE_DOWNLOADS, requestCode = 2)

        private fun intent(context: Context, action: String, requestCode: Int): PendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, TransferActionsReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
