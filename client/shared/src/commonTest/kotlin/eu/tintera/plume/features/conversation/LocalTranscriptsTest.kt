package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.settings.DataStoreAppSettings
import eu.tintera.plume.core.settings.createSettingsDataStore
import eu.tintera.plume.core.telegram.SpeechTranscript
import eu.tintera.plume.features.chats.FakeChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.FileSystem
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Přepisy z telefonu nad skutečným úložištěm nastavení (jako `ChatSnoozesTest`). */
class LocalTranscriptsTest {

    private fun transcriptsTest(block: suspend (path: String, scope: CoroutineScope) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            block((FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "plume-transcripts-${Random.nextLong()}.preferences_pb").toString(), scope)
        } finally {
            scope.cancel()
        }
    }

    /** Hotový přepis přežije „restart" (nová instance nad týmž souborem), rozpracovaný ne. */
    @Test
    fun hotovyPrezijeRestart() = transcriptsTest { path, scope ->
        val settings = DataStoreAppSettings(createSettingsDataStore(path), scope)
        val transcripts = LocalTranscripts(settings, FakeChatRepository())
        transcripts.set(1, 10, SpeechTranscript.Done("Ahoj"))
        transcripts.set(1, 11, SpeechTranscript.Pending("Rozpraco"))
        withTimeout(WAIT_MS) { settings.storedTranscripts.first { it.isNotEmpty() } }

        val restarted = LocalTranscripts(settings, FakeChatRepository())
        val state = withTimeout(WAIT_MS) { restarted.state.first { it.isNotEmpty() } }
        assertEquals(SpeechTranscript.Done("Ahoj"), state[1L to 10L])
        assertNull(state[1L to 11L])
        assertEquals(SpeechTranscript.Done("Ahoj"), restarted.get(1, 10))
    }

    /** Rozpracovaný v paměti přebíjí uložený (opakovaný přepis téže zprávy). */
    @Test
    fun pametPrebijiUlozeny() = transcriptsTest { path, scope ->
        val settings = DataStoreAppSettings(createSettingsDataStore(path), scope)
        val transcripts = LocalTranscripts(settings, FakeChatRepository())
        transcripts.set(1, 10, SpeechTranscript.Done("Starý"))
        withTimeout(WAIT_MS) { settings.storedTranscripts.first { it.isNotEmpty() } }
        transcripts.set(1, 10, SpeechTranscript.Pending("Nov"))
        assertEquals(SpeechTranscript.Pending("Nov"), transcripts.get(1, 10))
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
