package eu.tintera.plume

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.accounts.primaryAccount
import eu.tintera.plume.core.calls.CallJournal
import eu.tintera.plume.core.telegram.NotificationRepository
import org.koin.core.context.GlobalContext
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Dočká se notifikace, kterou TDLib po pushi ODLOŽIL.
 *
 * TDLib notifikaci nevydá hned: počká `notification_default_delay_ms` (1,5 s), a když byl účet
 * v posledních `online_cloud_timeout_ms` (5 min) online na jakémkoli zařízení, dokonce
 * `notification_cloud_delay_ms` (30 s) — kdyby si uživatel zprávu mezitím přečetl jinde,
 * notifikace se vůbec neukáže. Tolik času služba pushe nemá; po jejím skončení systém proces
 * zmrazí a odložená notifikace se vydá až při příštím probuzení. Ověřeno na zařízení.
 *
 * Dokud běží úloha, proces zmrazený není. **Expedited jen od Androidu 12** — starší WorkManager
 * spouští expedited úlohu jako foreground službu s vlastní notifikací; tam poběží jako běžná
 * úloha, klidně později, což je pořád lepší než nikdy.
 */
class NotificationDeliveryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val logger = Logger.withTag("Push")

    override suspend fun doWork(): Result {
        val repository = GlobalContext.getOrNull()?.primaryAccount()?.getOrNull<NotificationRepository>()
            ?: return Result.success()

        val started = TimeSource.Monotonic.markNow()
        // Hovor napřed musí přijít od serveru; notifikaci k němu vydá TDLib až potom.
        if (inputData.getBoolean(KEY_CALL, false)) {
            val journal = GlobalContext.getOrNull()?.primaryAccount()?.getOrNull<CallJournal>()
            if (!repository.awaitIncomingCall(TIMEOUT)) {
                logger.w { "Hovor z pushe nepřišel do $TIMEOUT." }
                journal?.record("Background job: no call within $TIMEOUT")
                return Result.success()
            }
            journal?.record("Background job: call known after ${started.elapsedNow().inWholeMilliseconds} ms")
        }
        if (repository.awaitDelivered(TIMEOUT - started.elapsedNow())) {
            logger.d { "Odložená notifikace vyřízena za ${started.elapsedNow().inWholeMilliseconds} ms." }
        } else {
            logger.w { "Odložená notifikace se nevyřídila do $TIMEOUT." }
        }
        return Result.success()
    }

    companion object {

        /** S rezervou nad `notification_cloud_delay_ms` (30 s). */
        private val TIMEOUT = 60.seconds

        private const val WORK_NAME = "notification-delivery"

        private const val CALL_WORK_NAME = "notification-delivery-call"

        private const val KEY_CALL = "call"

        /**
         * `REPLACE`: nový push znamená novou odloženou notifikaci, takže se čeká znovu od začátku.
         * Stav, na který se čeká, je společný, takže zrušená úloha nic neztratí. [call] = čekat
         * napřed na hovor z pushe ([NotificationRepository.awaitIncomingCall]).
         */
        fun enqueue(context: Context, call: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<NotificationDeliveryWorker>()
                .setInputData(workDataOf(KEY_CALL to call))
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    }
                }
                .build()
            // Hovor má vlastní jméno, ať čekání na něj nezruší push obyčejné zprávy, který přijde hned po něm.
            val name = if (call) CALL_WORK_NAME else WORK_NAME
            WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
