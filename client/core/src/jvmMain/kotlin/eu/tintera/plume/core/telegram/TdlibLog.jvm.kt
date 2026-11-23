package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.LogStreamFile
import eu.tintera.plume.core.logging.DesktopLogs
import eu.tintera.plume.core.platform.desktopDebugBuild
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Přesměruje log TDLib do `tdlib.log` ve složce záznamů ([DesktopLogs]). TDLib ho sama ořezává:
 * nad [MAX_FILE_BYTES] ho přejmenuje na `.old` a začne znovu.
 *
 * - **Wrapper log TDLib nenastavuje**, takže TDLib psala na standardní chybový výstup — u zabalené
 *   aplikace do prázdna. Fatální chyba TDLib (`CHECK`, `LOG(FATAL)`) přitom končí `abort()`em
 *   bez stack trace v Javě i bez `hs_err` souboru; jediná stopa je její poslední řádek v logu.
 * - **`redirectStderr`** přesměruje celý `stderr` procesu (deskriptor 2), tedy i výpisy JVM
 *   o nativním pádu a stack trace, které tiskne Java. Proto jen u zabalené aplikace, ne
 *   v ladicím běhu, kde je vývojář čte v konzoli.
 * - **Úroveň 2 = varování a chyby.** Výchozí 5 (ladicí výpisy) by soubor zaplnila za pár minut.
 * - **Jako běžný požadavek prvního klienta, ne statickým `JsonClient.execute`.** To bylo první
 *   řešení (dřív, než klient vznikne) a TDLib 1.8.6x pak při vytvoření prvního klienta SPOLEHLIVĚ
 *   padala na SIGSEGV v `td::MultiTd::create` → `ActorInfo::set_context` — i se samotným
 *   `setLogVerbosityLevel`. Log je nastavení celého procesu, takže stačí jednou; řádky z prvních
 *   milisekund před odpovědí jdou ještě na `stderr`.
 */
internal actual fun configureTdlibLog(client: TdlClient) {
    val file = DesktopLogs.tdlibLogFile ?: return
    if (!configured.compareAndSet(false, true)) return
    CoroutineScope(Dispatchers.IO).launch {
        val results = listOf(
            client.setLogVerbosityLevel(newVerbosityLevel = LOG_VERBOSITY),
            client.setLogStream(
                logStream = LogStreamFile(
                    path = file.absolutePath,
                    maxFileSize = MAX_FILE_BYTES,
                    redirectStderr = !desktopDebugBuild,
                ),
            ),
        )
        results.filterIsInstance<TdlResult.Failure>().firstOrNull()?.let { failure ->
            Logger.withTag("TdlibLog").w { "Log TDLib nejde přesměrovat do souboru: ${failure.code} ${failure.message}" }
        }
    }
}

private val configured = AtomicBoolean(false)

private const val LOG_VERBOSITY = 2
private const val MAX_FILE_BYTES = 5L * 1024 * 1024
