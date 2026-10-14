package eu.tintera.plume.core.calls

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.CallPhase
import eu.tintera.plume.core.telegram.CallRepository
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Ukončení aplikace uprostřed hovoru (Cmd+Q, nabídka ikony, SIGTERM): hook JVM hovor zavěsí
 * a počká, až se knihovna hovoru zastaví.
 *
 * - **Bez toho JVM spadne.** Vlákna WebRTC (`AudioEncoderQueue`) dál posílala zvuk, zatímco
 *   JVM končil a uvolňoval paměť — `EXC_BAD_ACCESS` v `ChannelSend::SendData`, crash report
 *   po každém ukončení s běžícím hovorem.
 * - **A protistrana by o konci nevěděla**: hovor jí běžel dál, dokud nevypršelo spojení.
 *   `discardCall` přes TDLib to řekne serveru hned; TDLib v hooku ještě běží (nic ho při
 *   ukončení nezavírá).
 * - Obojí s časovým limitem — ukončení aplikace nesmí viset na síti.
 */
internal class DesktopCallShutdown(
    private val calls: CallRepository,
    private val media: DesktopCallMedia,
) {
    private val logger = Logger.withTag("DesktopCallShutdown")

    fun install() {
        Runtime.getRuntime().addShutdownHook(Thread(::run, "call-shutdown"))
    }

    private fun run() {
        val call = calls.active.value?.takeIf { it.phase != CallPhase.Ended && it.phase != CallPhase.Failed } ?: return
        logger.i { "Aplikace končí uprostřed hovoru ${call.id}, zavěšuji." }
        runBlocking { withTimeoutOrNull(HANG_UP_TIMEOUT) { calls.hangUp() } }
            ?: logger.w { "Zavěšení se do ${HANG_UP_TIMEOUT} nepotvrdilo." }
        media.shutdown(STOP_TIMEOUT)
    }

    private companion object {
        val HANG_UP_TIMEOUT = 1_500.milliseconds
        val STOP_TIMEOUT = 2_000.milliseconds
    }
}
