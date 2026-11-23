package eu.tintera.plume.core.logging

import eu.tintera.plume.core.platform.DesktopOs
import eu.tintera.plume.core.platform.desktopDataDirectory
import eu.tintera.plume.core.platform.desktopOs
import java.io.File
import java.io.FileOutputStream
import java.io.Writer
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Záznamy o chodu desktopové aplikace ve složce `logs` v datech aplikace — desktop nemá
 * Crashlytics a zabalená aplikace nemá konzoli, takže pád jinak nezanechal žádnou stopu.
 * Nikam se neodesílají; uživatel je přiloží k hlášení chyby sám (O aplikaci → Záznamy aplikace).
 *
 * - **`plume.log`** — log Kermitu od `Info` výš (tytéž řádky, jaké jdou na Androidu do
 *   Crashlytics jako kontext) a stack trace u `logger.e` s výjimkou. Nad [MAX_LOG_BYTES] se
 *   přesune do `plume.1.log`.
 * - **`crash-<čas>.txt`** — nezachycená výjimka (i ta z okna Compose, viz [install]): verze,
 *   systém, vlákno, stack trace a posledních [RECENT_LINES] řádků logu. Samostatný soubor, ať se
 *   dá poslat jeden.
 * - **`tdlib.log`** — log TDLib (varování a chyby) a v zabalené aplikaci i celý standardní
 *   chybový výstup procesu (`redirect_stderr`): fatální hláška TDLib před `abort()`, výpis JVM
 *   o nativním pádu. Obojí jinak končilo v konzoli, která u zabalené aplikace není.
 * - **`hs_err_pid*.log`** — nativní pád JVM (TDLib, Skia, FFmpeg). JVM ho zapíše do pracovního
 *   adresáře nebo do dočasného adresáře systému; při dalším startu se přestěhuje sem
 *   ([collectNativeCrashReports]). Na macOS se sem kopírují i systémová hlášení `Plume-*.ips`.
 */
object DesktopLogs {

    /** Složka se záznamy; vzniká při prvním použití. */
    val directory: File by lazy { File(desktopDataDirectory, "logs").apply { mkdirs() } }

    private val logFile: File get() = File(directory, LOG_FILE)

    private val lock = Any()
    private var writer: Writer? = null
    private var written = 0L
    private val recent = ArrayDeque<String>()
    private var installed = false

    /**
     * Zapne zachytávání pádů. Volá se ze vstupního bodu co nejdřív, ale až po zámku jediné
     * instance — druhá instance by psala do týchž souborů — a před `startKoin`, který spouští
     * TDLib (její log se musí přesměrovat dřív, než vznikne první klient).
     *
     * **Výjimka z okna Compose sem dojde taky.** Výchozí `WindowExceptionHandler` ukáže dialog
     * s chybou, okno zavře a výjimku hodí dál; na vlákně AWT ji pak dostane výchozí
     * `UncaughtExceptionHandler` — a ten bez konzole nebyl nikde vidět.
     */
    fun install() {
        synchronized(lock) {
            if (installed) return
            installed = true
        }
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrashReport(thread, throwable) }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                System.err.print("Exception in thread \"${thread.name}\" ")
                throwable.printStackTrace()
            }
        }
        append("=== Start: $environment, pid ${ProcessHandle.current().pid()} ===")
        runCatching { collectNativeCrashReports() }.onFailure { append("Collecting native crash reports failed: $it") }
        prune()
    }

    /**
     * Kam přesměrovat log TDLib (`configureTdlibLog` s prvním klientem); `null` bez [install] —
     * testy a druhá instance nemají na soubory sahat.
     */
    internal val tdlibLogFile: File?
        get() = synchronized(lock) { if (installed) File(directory, TDLIB_LOG_FILE) else null }

    /** Hlásič pro Kermit (`CrashlyticsLogWriter`), viz `createCrashReporter`. */
    internal val reporter: CrashReporter = object : CrashReporter {
        override fun log(message: String) = append(message)

        override fun recordException(throwable: Throwable) = append(throwable.stackTraceToString().trimEnd())
    }

    /**
     * Co ukázat uživateli ve složce: nejnovější záznam o pádu, a když žádný není, log aplikace.
     * Složka se otevře s tímhle souborem vybraným.
     */
    fun fileToReveal(): File = reports().maxByOrNull { it.lastModified() } ?: logFile.takeIf { it.exists() } ?: directory

    private fun append(message: String) {
        val line = "${LocalDateTime.now().format(TIME)} $message"
        synchronized(lock) {
            recent.addLast(line)
            while (recent.size > RECENT_LINES) recent.removeFirst()
            runCatching {
                val out = writer ?: open()
                out.write(line)
                out.write(System.lineSeparator())
                // Bez flush by poslední řádky před nativním pádem zůstaly v bufferu.
                out.flush()
                written += line.length + 1
                if (written > MAX_LOG_BYTES) rotate()
            }
        }
    }

    private fun open(): Writer {
        if (logFile.length() > MAX_LOG_BYTES) {
            Files.move(logFile.toPath(), File(directory, PREVIOUS_LOG_FILE).toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        written = logFile.length()
        return FileOutputStream(logFile, true).bufferedWriter().also { writer = it }
    }

    private fun rotate() {
        writer?.close()
        writer = null
        Files.move(logFile.toPath(), File(directory, PREVIOUS_LOG_FILE).toPath(), StandardCopyOption.REPLACE_EXISTING)
        written = 0
    }

    private fun writeCrashReport(thread: Thread, throwable: Throwable) {
        val trace = throwable.stackTraceToString().trimEnd()
        append("FATAL: uncaught exception in thread ${thread.name}\n$trace")
        val tail = synchronized(lock) { recent.toList() }
        val file = File(directory, "crash-${LocalDateTime.now().format(FILE_TIME)}.txt")
        file.writeText(
            buildString {
                appendLine("Plume: uncaught exception")
                appendLine(environment)
                appendLine("Thread: ${thread.name}")
                appendLine()
                appendLine(trace)
                appendLine()
                appendLine("--- Last ${tail.size} log lines ---")
                tail.forEach(::appendLine)
            },
        )
    }

    /**
     * Přestěhuje sem nativní pády JVM z minulých běhů. Kam JVM `hs_err_pid<pid>.log` zapíše, se
     * dopředu nastavit nedá (`-XX:ErrorFile` cestu k datům uživatele nezná a proměnné prostředí
     * nerozbalí): zkouší pracovní adresář (u zabalené aplikace na macOS `/`, kam nesmí, na
     * Windows složka instalace) a pak dočasný adresář systému. Tam leží i pády jiných Java
     * aplikací, takže se bere jen soubor, který o Plume mluví (cesta k aplikaci, třída, data).
     */
    private fun collectNativeCrashReports() {
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(NATIVE_REPORT_MAX_AGE_DAYS)
        val candidates = listOfNotNull(
            System.getProperty("user.dir"),
            System.getProperty("java.io.tmpdir"),
            "/tmp".takeIf { desktopOs != DesktopOs.Windows },
        ).map(::File).distinct()
        for (folder in candidates) {
            val files = folder.listFiles { file -> file.name.startsWith("hs_err_pid") && file.name.endsWith(".log") } ?: continue
            for (file in files) {
                if (file.lastModified() < since || !mentionsPlume(file)) continue
                val target = File(directory, file.name)
                runCatching { Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
                    .recoverCatching { file.copyTo(target, overwrite = true) }
                    .onSuccess { append("Native crash from a previous run: ${target.name} (from ${folder.path})") }
            }
        }
        // macOS: pád bez hs_err (abort z nativní knihovny) zapíše systém jako hlášení procesu
        // Plume. Patří systému, takže kopie, ne přesun.
        if (desktopOs == DesktopOs.MacOs) {
            val reports = File(System.getProperty("user.home"), "Library/Logs/DiagnosticReports")
            reports.listFiles { file -> file.name.startsWith("Plume") && file.name.endsWith(".ips") }
                ?.filter { it.lastModified() >= since }
                ?.forEach { file ->
                    val target = File(directory, file.name)
                    if (!target.exists() && runCatching { file.copyTo(target) }.isSuccess) {
                        append("System crash report from a previous run: ${target.name}")
                    }
                }
        }
    }

    private fun mentionsPlume(file: File): Boolean = runCatching {
        file.inputStream().use { input ->
            val head = String(input.readNBytes(NATIVE_REPORT_SCAN_BYTES), Charsets.ISO_8859_1)
            head.contains("plume", ignoreCase = true)
        }
    }.getOrDefault(false)

    private fun reports(): List<File> = directory.listFiles { file ->
        (file.name.startsWith("crash-") && file.name.endsWith(".txt")) ||
            file.name.startsWith("hs_err_pid") ||
            file.name.endsWith(".ips")
    }?.toList().orEmpty()

    /** Nejvýš [MAX_REPORTS] záznamů o pádu; nejstarší se mažou. */
    private fun prune() {
        reports().sortedByDescending { it.lastModified() }.drop(MAX_REPORTS).forEach { it.delete() }
    }

    private val environment: String by lazy {
        val version = System.getProperty("jpackage.app-version") ?: "dev"
        val system = "${System.getProperty("os.name")} ${System.getProperty("os.version")} (${System.getProperty("os.arch")})"
        "Plume $version, $system, Java ${System.getProperty("java.runtime.version")}"
    }

    private const val LOG_FILE = "plume.log"
    private const val PREVIOUS_LOG_FILE = "plume.1.log"
    private const val TDLIB_LOG_FILE = "tdlib.log"
    private const val MAX_LOG_BYTES = 2L * 1024 * 1024
    private const val RECENT_LINES = 300
    private const val MAX_REPORTS = 20
    private const val NATIVE_REPORT_MAX_AGE_DAYS = 30L
    private const val NATIVE_REPORT_SCAN_BYTES = 512 * 1024
    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val FILE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
}
