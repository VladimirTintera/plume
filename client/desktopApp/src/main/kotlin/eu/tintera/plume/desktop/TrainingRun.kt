package eu.tintera.plume.desktop

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.accounts.Accounts
import org.koin.core.context.GlobalContext
import java.lang.management.ManagementFactory
import java.io.File
import java.nio.file.Files
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tréninkový běh při balení (`desktopTrainingRun`, Compose 1.13 `aot`): plugin spustí zabalenou
 * aplikaci a čeká, až SAMA skončí s kódem 0 — žádný časový limit ani pomocné API nemá. Při konci
 * JVM zapíše AOT cache s třídami, které se načetly, a ta jde do balíčku.
 *
 * - **Vlastní dočasný adresář dat.** Bez něj by běh vzal data nainstalované aplikace: narazil by
 *   na její zámek jediné instance (a jen ji požádal o okno — cache by byla skoro prázdná), nebo
 *   by otevřel její databázi.
 * - **Konec až chvíli po prvním snímku**: start TDLib, přihlašovací obrazovka a QR kód načtou
 *   většinu tříd, které běžný start potřebuje. Seznam chatů na CI nevznikne (není přihlášený
 *   účet) — jeho třídy se načtou postaru, za běhu.
 * - **Před koncem se zavře TDLib** (`shutdown`). `exitProcess` s běžícím klientem shodil proces
 *   v nativním kódu — statické objekty knihovny zanikly, zatímco její vlákno ještě uklízelo
 *   (`td::CountryInfoManager::tear_down` → „mutex lock failed", SIGABRT, kód 134) — a plugin
 *   bere jen kód 0. Cache v tu chvíli už zapsaná byla, build přesto spadl.
 * - **Pojistka s kódem 1**: kdyby se okno neukázalo, build spadne, místo aby visel. Rozbitá
 *   aplikace nemá vyjít.
 */
internal object TrainingRun {

    private val logger = Logger.withTag("TrainingRun")

    /** Dočasný adresář dat, který založil [prepare] (podstrčený `plume.dataDir` se nemaže). */
    private var temporary: File? = null

    /** Volat jako první v `main`, před čímkoli, co sahá na data. */
    fun prepare() {
        if (System.getProperty(DATA_DIR_PROPERTY).isNullOrBlank()) {
            temporary = Files.createTempDirectory("plume-aot-training").toFile()
            System.setProperty(DATA_DIR_PROPERTY, temporary!!.path)
        }
        logger.i { "Tréninkový běh pro AOT cache, data v ${System.getProperty(DATA_DIR_PROPERTY)}." }
        thread(isDaemon = true, name = "aot-training-watchdog") {
            Thread.sleep(WATCHDOG_MS)
            logger.e { "Tréninkový běh: okno se do ${WATCHDOG_MS / 1000} s neukázalo." }
            exitProcess(1)
        }
    }

    /** Z kompozice okna, po prvním snímku. */
    suspend fun finishAfterStartup() {
        delay(SETTLE_MS)
        val sessions = GlobalContext.get().get<Accounts>().let { it.all.value + listOfNotNull(it.adding.value) }
        withTimeoutOrNull(SHUTDOWN_MS) { sessions.forEach { it.session.shutdown(destroy = false) } }
        temporary?.deleteRecursively()
        logger.i { "Tréninkový běh hotový po ${ManagementFactory.getRuntimeMXBean().uptime} ms." }
        exitProcess(0)
    }

    private const val DATA_DIR_PROPERTY = "plume.dataDir"
    private const val SETTLE_MS = 15_000L
    private const val SHUTDOWN_MS = 10_000L
    private const val WATCHDOG_MS = 180_000L
}
