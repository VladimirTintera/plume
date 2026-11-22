package eu.tintera.plume.core.update

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.accounts.Accounts
import eu.tintera.plume.core.platform.desktopArm64
import eu.tintera.plume.core.platform.desktopDataDirectory
import eu.tintera.plume.core.platform.desktopDebugBuild
import eu.tintera.plume.core.platform.desktopOs
import eu.tintera.plume.core.platform.desktopTrainingRun
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Aktualizace desktopu z GitHub Release (`desktop-release.yml`): obchod tu žádný není, balíčky
 * leží v přílohách vydání. Stejné stavy a táž karta v seznamu chatů jako u Play na Androidu —
 * nabídka, stahování na pozadí, „Restartovat".
 *
 * - **Ptá se nejvýš jednou za [CHECK_INTERVAL]**, i když `check` chodí s každým návratem na
 *   seznam chatů: API GitHubu pouští bez přihlášení 60 dotazů za hodinu z jedné adresy.
 * - **Jen zabalená aplikace** (`jpackage.app-version`), ne ladicí běh ani tréninkový běh při
 *   balení. Lokální balíček bez `-PdesktopVersion` je 1.0.0 a novější vydání nabídne — to je
 *   v pořádku, je to pravda.
 * - **Stahuje se do dat aplikace** (`updates/`) a ověřuje proti SHA-256, který GitHub
 *   k příloze uvádí (`digest`). Stažený balíček přežije restart: další `check` ho najde a karta
 *   rovnou nabídne Restartovat.
 * - **Instalaci dělá samostatný proces, který počká na konec aplikace** ([DesktopInstaller]):
 *   běžící aplikace sama sebe přepsat nemůže.
 *
 * Viz „Aktualizace z GitHub Release" v `docs/poznatky/desktop.md`.
 */
actual class AppUpdates(private val accounts: () -> Accounts) {

    private val logger = Logger.withTag("AppUpdates")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<AppUpdateState>(AppUpdateState.None)
    actual val state: StateFlow<AppUpdateState> = _state.asStateFlow()

    private val current: DesktopVersion? = DesktopVersion.parse(System.getProperty("jpackage.app-version"))
        .takeUnless { desktopDebugBuild || desktopTrainingRun }

    private val directory: File get() = File(desktopDataDirectory, "updates")

    private var lastCheck: TimeMark? = null
    private var release: DesktopRelease? = null
    private var work: Job? = null

    actual fun check() {
        if (current == null) return
        if (work?.isActive == true || _state.value is AppUpdateState.Downloaded) return
        if (lastCheck?.let { it.elapsedNow() < CHECK_INTERVAL } == true) return
        lastCheck = TimeSource.Monotonic.markNow()
        work = scope.launch { refresh() }
    }

    actual val canCheck: Boolean get() = current != null

    /**
     * Ruční kontrola: bez [CHECK_INTERVAL] (ťuknutí je jeden dotaz, limit 60 za hodinu to
     * unese), ale ne souběžně s rozběhnutou prací — počká na ni a řekne, jak to vypadá.
     * Rozběhnuté stahování nebo stažený balíček jsou „našlo se", ptát se znovu netřeba.
     */
    actual suspend fun checkNow(): UpdateCheckResult {
        if (current == null) return UpdateCheckResult.Unsupported
        work?.takeIf { it.isActive }?.join()
        if (_state.value is AppUpdateState.Downloading || _state.value is AppUpdateState.Downloaded) {
            return UpdateCheckResult.Found
        }
        lastCheck = TimeSource.Monotonic.markNow()
        val check = scope.async { refresh() }
        work = check
        return check.await()
    }

    /** Dotaz na GitHub a podle výsledku nový [state]. */
    private fun refresh(): UpdateCheckResult {
        val current = current ?: return UpdateCheckResult.Unsupported
        val latest = runCatching { fetchLatest() }
            .onFailure { logger.i(it) { "Vydání na GitHubu se nepodařilo zjistit." } }
            .getOrElse { return UpdateCheckResult.Failed }
        if (latest == null || latest.version <= current) {
            // Po aktualizaci tu leží balíček, ze kterého se instalovalo.
            directory.deleteRecursively()
            _state.value = AppUpdateState.None
            return UpdateCheckResult.UpToDate
        }
        release = latest
        val file = target(latest)
        _state.value = if (file.isFile && verified(file, latest.asset)) {
            AppUpdateState.Downloaded(latest.version.code)
        } else {
            AppUpdateState.Available(latest.version.code)
        }
        logger.i { "Na GitHubu je verze ${latest.version} (běží $current)." }
        return UpdateCheckResult.Found
    }

    /** Stažení balíčku — „Aktualizovat" na kartě. */
    internal fun download() {
        val latest = release ?: return
        if (work?.isActive == true) return
        work = scope.launch {
            val code = latest.version.code
            _state.value = AppUpdateState.Downloading(code, null)
            val result = runCatching {
                directory.listFiles()?.forEach { it.deleteRecursively() }
                download(latest.asset, target(latest)) { progress ->
                    _state.value = AppUpdateState.Downloading(code, progress)
                }
            }
            _state.value = result.fold(
                onSuccess = { AppUpdateState.Downloaded(code) },
                onFailure = {
                    logger.w(it) { "Aktualizaci ${latest.version} se nepodařilo stáhnout." }
                    AppUpdateState.Available(code)
                },
            )
        }
    }

    /**
     * Restart: spustí instalátor, zavře klienty TDLib a ukončí aplikaci; instalátor počká, až
     * proces skončí, nainstaluje a aplikaci spustí znovu. Když instalátor nejde spustit
     * (aplikace na místě, kam se nedá psát), otevře se balíček v systému a aplikace běží dál.
     */
    actual fun complete() {
        val latest = release ?: return
        val file = target(latest).takeIf { it.isFile } ?: return
        scope.launch {
            // Znovu těsně před spuštěním: mezi stažením a Restartovat můžou uběhnout dny
            // (balíček přežívá restart aplikace) a instalátor spustí, co v souboru zrovna leží.
            if (!runCatching { verified(file, latest.asset) }.getOrDefault(false)) {
                logger.w { "Stažená aktualizace ${latest.version} neodpovídá kontrolnímu součtu, mažu ji." }
                file.delete()
                _state.value = AppUpdateState.Available(latest.version.code)
                return@launch
            }
            val started = runCatching { DesktopInstaller.start(file, directory) }
                .onFailure { logger.w(it) { "Instalátor aktualizace nejde spustit." } }
                .getOrDefault(false)
            if (!started) {
                DesktopInstaller.openManually(file)
                return@launch
            }
            logger.i { "Instaluji ${latest.version}, aplikace končí." }
            val sessions = accounts().let { it.all.value + listOfNotNull(it.adding.value) }
            withTimeoutOrNull(SHUTDOWN_TIMEOUT) { sessions.forEach { it.session.shutdown(destroy = false) } }
            exitProcess(0)
        }
    }

    private fun target(release: DesktopRelease) = File(directory, release.fileName)

    private fun fetchLatest(): DesktopRelease? {
        val connection = open(RELEASES_URL)
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("GitHub odpověděl ${connection.responseCode}")
            }
            val body = connection.inputStream.use { it.readBytes().decodeToString() }
            return latestDesktopRelease(body, desktopOs, desktopArm64)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun download(asset: ReleaseAsset, file: File, onProgress: (Float?) -> Unit) {
        directory.mkdirs()
        val partial = File(file.path + ".part")
        val connection = open(asset.url)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Stažení skončilo kódem ${connection.responseCode}")
            }
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: asset.size
            val digest = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var reported = -1
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != reported) {
                                reported = percent
                                onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
            if (digest.digest().toHex() != asset.sha256) {
                partial.delete()
                throw IOException("Kontrolní součet stažené aktualizace nesedí.")
            }
            if (!partial.renameTo(file)) throw IOException("Staženou aktualizaci nejde přejmenovat.")
        } finally {
            connection.disconnect()
            partial.delete()
        }
    }

    private fun verified(file: File, asset: ReleaseAsset): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex() == asset.sha256
    }

    private fun open(url: String): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            // GitHub bez User-Agent odpoví 403. Obecné jméno, žádné údaje o uživateli.
            setRequestProperty("User-Agent", "Plume-Desktop/${current ?: "dev"}")
        }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val RELEASES_URL = "https://api.github.com/repos/VladimirTintera/plume/releases?per_page=30"
        val CHECK_INTERVAL = 6.hours
        val SHUTDOWN_TIMEOUT = 5.seconds
        const val TIMEOUT_MS = 30_000
        const val BUFFER_SIZE = 64 * 1024
    }
}

@Composable
actual fun rememberStartUpdate(updates: AppUpdates): () -> Unit = remember(updates) { { updates.download() } }
