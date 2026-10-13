package eu.tintera.plume.core.settings

import eu.tintera.plume.core.telegram.AuthStage
import eu.tintera.plume.core.telegram.AutoDownloadPresets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Nad skutečným DataStore a na skutečném čase, ze stejného důvodu jako [DataStoreAppSettingsTest].
 * Každé čekání má limit, aby ze zaseknutí bylo selhání.
 */
class AutoDownloadSyncTest {

    private val server = AutoDownloadSettings(
        wifi = AutoDownloadLevel.Medium,
        mobile = AutoDownloadLevel.Low,
        roaming = AutoDownloadLevel.Off,
    )

    private class FakePresets(var server: AutoDownloadSettings) : AutoDownloadPresets {
        var fail = false
        val saved = mutableListOf<AutoDownloadSettings>()
        var loads = 0
        override suspend fun load(): Result<AutoDownloadSettings> {
            loads++
            return if (fail) Result.failure(IllegalStateException("offline")) else Result.success(server)
        }
        override suspend fun save(settings: AutoDownloadSettings): Result<Unit> {
            if (fail) return Result.failure(IllegalStateException("offline"))
            saved += settings
            server = settings
            return Result.success(Unit)
        }
    }

    private class Env(
        val settings: DataStoreAppSettings,
        val presets: FakePresets,
        val stage: MutableStateFlow<AuthStage>,
        val sync: AutoDownloadSync,
    )

    private fun syncTest(
        stage: AuthStage,
        prepare: suspend (DataStoreAppSettings) -> Unit = {},
        block: suspend Env.() -> Unit,
    ) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val path = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "plume-sync-${Random.nextLong()}.preferences_pb").toString()
            val settings = DataStoreAppSettings(createSettingsDataStore(path), scope)
            prepare(settings)
            val presets = FakePresets(server)
            val stages = MutableStateFlow(stage)
            val sync = AutoDownloadSync(settings, presets, stages, scope)
            sync.start()
            Env(settings, presets, stages, sync).block()
        } finally {
            scope.cancel()
        }
    }

    private suspend fun await(what: String, condition: suspend () -> Boolean) {
        try {
            withTimeout(WAIT_MS) { while (!condition()) delay(10) }
        } catch (timeout: TimeoutCancellationException) {
            fail("Čekání na „$what\" nedoběhlo za $WAIT_MS ms.", timeout)
        }
    }

    @Test
    fun prihlaseniStahneNastaveniZeServeru() = syncTest(AuthStage.WaitPhoneNumber) {
        await("Pull po přihlašovací obrazovce") { settings.autoDownloadSync() == SettingsSync.Pull }
        stage.value = AuthStage.Ready
        await("nastavení ze serveru") { settings.autoDownload.value == server }
        await("Synced") { settings.autoDownloadSync() == SettingsSync.Synced }
        assertTrue(presets.saved.isEmpty(), "Stažené nastavení se nesmí vracet na server: ${presets.saved}")
    }

    /** Přihlášený účet při startu jde rovnou do Ready; po synchronizaci se server už nečte. */
    @Test
    fun dalsiStartNecteServer() = syncTest(
        AuthStage.Connecting,
        prepare = { it.setAutoDownloadSync(SettingsSync.Synced) },
    ) {
        stage.value = AuthStage.Ready
        delay(200)
        assertEquals(0, presets.loads)
        assertEquals(AutoDownloadSettings(), settings.autoDownload.value)
    }

    /**
     * `Synced` se ČEKÁ, ne tvrdí: fake si zápis zapíše do [FakePresets.saved] uvnitř `save`,
     * kdežto `Synced` jde do DataStore až po něm. Mezi tím stav pořád drží `Push`.
     */
    @Test
    fun zmenaJdeNaServer() = syncTest(AuthStage.Ready, prepare = { it.setAutoDownloadSync(SettingsSync.Synced) }) {
        val new = AutoDownloadSettings(wifi = AutoDownloadLevel.Low)
        sync.change(new)
        await("zápis na server") { presets.saved.lastOrNull() == new }
        await("lokálně") { settings.autoDownload.value == new }
        await("Synced") { settings.autoDownloadSync() == SettingsSync.Synced }
    }

    /**
     * Nepovedený zápis se zkusí znovu při dalším Ready (v aplikaci po dalším startu), s lokální
     * hodnotou. Mezi stavy se čeká: `StateFlow` by rychlou dvojici sloučil a Ready by se neopakoval.
     */
    @Test
    fun nepovedenyZapisSeZopakuje() = syncTest(AuthStage.Ready, prepare = { it.setAutoDownloadSync(SettingsSync.Synced) }) {
        presets.fail = true
        val new = AutoDownloadSettings(mobile = AutoDownloadLevel.High)
        sync.change(new)
        await("Push") { settings.autoDownloadSync() == SettingsSync.Push }
        presets.fail = false
        stage.value = AuthStage.Connecting
        delay(100)
        stage.value = AuthStage.Ready
        await("zápis po návratu") { presets.saved.lastOrNull() == new }
        await("Synced") { settings.autoDownloadSync() == SettingsSync.Synced }
    }

    /** Aktualizace z verze bez synchronizace: vlastní volba se nepřepíše, jde na server. */
    @Test
    fun vlastniVolbaPoAktualiziJdeNaServer() {
        val mine = AutoDownloadSettings(roaming = AutoDownloadLevel.High)
        syncTest(AuthStage.Connecting, prepare = { it.setAutoDownload(mine); it.autoDownload.first { v -> v == mine } }) {
            stage.value = AuthStage.Ready
            await("zápis vlastní volby") { presets.saved.lastOrNull() == mine }
            assertEquals(mine, settings.autoDownload.value)
            assertEquals(0, presets.loads)
        }
    }

    @Test
    fun bezVlastniVolbyPoAktualiziStahne() = syncTest(AuthStage.Connecting) {
        stage.value = AuthStage.Ready
        await("nastavení ze serveru") { settings.autoDownload.value == server }
    }

    /** Odhlášení přepne na Pull — další účet (nebo týž po změně jinde) dostane stav ze serveru. */
    @Test
    fun odhlaseniPripraviStazeni() = syncTest(AuthStage.Ready, prepare = { it.setAutoDownloadSync(SettingsSync.Synced) }) {
        stage.value = AuthStage.LoggingOut
        await("Pull") { settings.autoDownloadSync() == SettingsSync.Pull }
    }

    private companion object {
        /** Pojistka proti zaseknutí, ne měřítko rychlosti — viz `DataStoreAppSettingsTest`. */
        const val WAIT_MS = 15_000L
    }
}
