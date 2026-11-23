package eu.tintera.plume.core.platform

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.accounts.Accounts
import eu.tintera.plume.core.accounts.AccountSession
import eu.tintera.plume.core.calls.DesktopCallMedia
import eu.tintera.plume.core.telegram.CallPhase
import eu.tintera.plume.core.telegram.CallRepository
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Ukončení aplikace (Cmd+Q, Ukončit z Docku nebo z ikony v oznamovací oblasti, odhlášení
 * systému, SIGTERM): jeden hook JVM napřed zavěsí hovor a pak zavře klienty TDLib všech účtů.
 *
 * - **Bez zavření TDLib proces při ukončení spadne.** `System.exit` po hookách volá `exit()`
 *   a ten spustí statické destruktory knihovny TDLib (`LanguagePackManager`, `ClientManager`),
 *   zatímco její vlákna ještě běží: `~ConcurrentScheduler` → `~Session` → `CHECK`
 *   v `td::PollableFdInfo::~PollableFdInfo` → `abort()`. Crash report po každém Cmd+Q, ladicí
 *   běh po `pkill` padal pokaždé (`docs/poznatky/desktop.md`, „Ukončení aplikace a TDLib").
 * - **Bez zavěšení spadne taky.** Vlákna WebRTC (`AudioEncoderQueue`) dál posílala zvuk, zatímco
 *   JVM končil a uvolňoval paměť — `EXC_BAD_ACCESS` v `ChannelSend::SendData`. A protistrana by
 *   o konci nevěděla, dokud nevyprší spojení; `discardCall` jí to přes TDLib řekne hned.
 * - **Jeden hook, ne dva.** Hooky JVM běží souběžně a v nedefinovaném pořadí, takže zavěšení
 *   (jde přes TDLib) by se s jejím zavřením předbíhalo.
 * - **Všechno s časovým limitem** — ukončení aplikace nesmí viset na síti ani na zaseknuté
 *   knihovně; zavření TDLib je místní (zápis databáze), běžně pod vteřinu.
 * - Účty se zavírají souběžně, včetně přidávaného ([Accounts.adding]).
 *
 * @param accounts líně: hook se instaluje při startu Koinu, účty se čtou až při ukončení.
 * @param media knihovna hovoru, jen když na desktopu je ([DesktopCallMedia.available]); líně,
 *   ať ji kvůli ukončení bez hovoru nikdo nezakládá.
 */
internal class DesktopShutdown(
    private val accounts: () -> Accounts,
    private val media: () -> DesktopCallMedia?,
) {
    private val logger = Logger.withTag("DesktopShutdown")

    fun install() {
        Runtime.getRuntime().addShutdownHook(Thread(::run, "plume-shutdown"))
    }

    private fun run() {
        val started = TimeSource.Monotonic.markNow()
        val sessions = runCatching { accounts().let { it.all.value + listOfNotNull(it.adding.value) } }
            .onFailure { logger.w(it) { "Účty se při ukončení nepodařilo přečíst." } }
            .getOrDefault(emptyList())
        runCatching { hangUp(sessions) }.onFailure { logger.w(it) { "Hovor se při ukončení nepodařilo zavěsit." } }
        val closed = runBlocking {
            withTimeoutOrNull(CLOSE_TIMEOUT) {
                sessions.map { account -> async { account.session.shutdown(destroy = false) } }.awaitAll()
            }
        }
        if (closed == null) {
            logger.w { "Klienti TDLib se do $CLOSE_TIMEOUT nezavřeli, aplikace končí i tak." }
        } else {
            logger.i { "Klienti TDLib zavření (${sessions.size}) za ${started.elapsedNow().inWholeMilliseconds} ms." }
        }
    }

    private fun hangUp(sessions: List<AccountSession>) {
        val calls = sessions.firstNotNullOfOrNull { account ->
            account.koin.getOrNull<CallRepository>()
                ?.takeIf { calls -> calls.active.value?.phase.let { it != null && it != CallPhase.Ended && it != CallPhase.Failed } }
        } ?: return
        logger.i { "Aplikace končí uprostřed hovoru ${calls.active.value?.id}, zavěšuji." }
        runBlocking { withTimeoutOrNull(HANG_UP_TIMEOUT) { calls.hangUp() } }
            ?: logger.w { "Zavěšení se do $HANG_UP_TIMEOUT nepotvrdilo." }
        media()?.shutdown(STOP_TIMEOUT)
    }

    private companion object {
        val HANG_UP_TIMEOUT = 1_500.milliseconds
        val STOP_TIMEOUT = 2_000.milliseconds

        /**
         * Strop na zavření všech klientů TDLib naráz. Kratší než `SHUTDOWN_TIMEOUT` v session
         * (10 s): odhlášení systému na ukončení aplikace čeká jen pár vteřin a ukončení visící
         * na zaseknutém klientovi by bylo horší než pád, kterému má předejít.
         */
        val CLOSE_TIMEOUT = 3_000.milliseconds
    }
}
