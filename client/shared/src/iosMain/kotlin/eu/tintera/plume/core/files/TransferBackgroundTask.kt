package eu.tintera.plume.core.files

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid

/**
 * Prodlouží běh aplikace na pozadí, dokud se nahrávají nebo stahují soubory ([ActiveTransfers])
 * — obdoba služby v popředí na Androidu (`UploadKeepAliveWorker`). iOS dá úloze na pozadí zhruba
 * půl minuty; fotky, krátká videa a menší soubory to stihnou, delší přenos dokončí TDLib po
 * návratu do popředí.
 *
 * - **Úloha začíná už v popředí, s prvním přenosem**, ne až s odchodem do pozadí: odpočet
 *   běží teprve na pozadí a nezávisí se na tom, jestli oznámení o odchodu dorazí dřív, než
 *   iOS aplikaci uspí.
 * - **Po vypršení se do návratu do popředí znovu nezačíná.** iOS by na pozadí dal nanejvýš
 *   pár vteřin a opakované žádosti jen zkracují čas, který aplikace dostane příště.
 *
 * Musí běžet na hlavním vlákně (`UIApplication`).
 */
class TransferBackgroundTask(
    private val activity: StateFlow<Transfers>,
    private val foreground: Flow<Boolean>,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("Transfers")

    private var task: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid
    private var expired = false

    fun start() {
        scope.launch {
            combine(activity.map { !it.isIdle }.distinctUntilChanged(), foreground) { transferring, inForeground ->
                transferring to inForeground
            }.collect { (transferring, inForeground) ->
                if (inForeground) expired = false
                when {
                    transferring && task == UIBackgroundTaskInvalid && !expired -> begin()
                    !transferring && task != UIBackgroundTaskInvalid -> end()
                }
            }
        }
    }

    private fun begin() {
        val application = UIApplication.sharedApplication
        task = application.beginBackgroundTaskWithName("Plume transfers") {
            logger.i { "Úloha na pozadí pro přenosy vypršela, přenos dokončí návrat do popředí." }
            expired = true
            end()
        }
    }

    private fun end() {
        val ending = task
        if (ending == UIBackgroundTaskInvalid) return
        task = UIBackgroundTaskInvalid
        UIApplication.sharedApplication.endBackgroundTask(ending)
    }
}
