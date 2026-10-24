package eu.tintera.plume.core.calls

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

class CallJournalTest {

    private fun journalTest(block: suspend (CallJournal, String) -> Unit) = runBlocking {
        val dir = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "plume-journal-${Random.nextLong()}").toString()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            block(CallJournal(dir, scope, now = { Instant.parse("2026-10-19T16:50:38.109Z") }, timeZone = { TimeZone.UTC }), dir)
        } finally {
            scope.cancel()
            FileSystem.SYSTEM.deleteRecursively(dir.toPath())
        }
    }

    @Test
    fun exportHasHeaderAndLinesInOrder() = journalTest { journal, _ ->
        assertFalse(journal.hasEntries.value)
        journal.record("Call push, sent 900 ms ago")
        journal.record("Incoming call", callId = 4)
        val exported = FileSystem.SYSTEM.read(journal.export("Plume 0.3.90, Pixel 5")!!.toPath()) { readUtf8() }
        assertEquals(
            "Plume 0.3.90, Pixel 5\n\n" +
                "2026-10-19 16:50:38.109 Call push, sent 900 ms ago\n" +
                "2026-10-19 16:50:38.109 [4] Incoming call\n",
            exported,
        )
        assertTrue(journal.hasEntries.first { it })
    }

    @Test
    fun oldPartIsKeptAfterRotation() = journalTest { journal, dir ->
        val line = "x".repeat(1_000)
        repeat((CallJournal.MAX_FILE_BYTES / 1_000).toInt() + 5) { journal.record(line) }
        journal.record("last")
        val exported = FileSystem.SYSTEM.read(journal.export("h")!!.toPath()) { readUtf8() }
        assertTrue(FileSystem.SYSTEM.exists(dir.toPath() / "calls.1.log"))
        assertTrue(exported.endsWith(" last\n"))
        // Starší díl je před novějším a nic se neztratilo.
        assertEquals((CallJournal.MAX_FILE_BYTES / 1_000).toInt() + 5, exported.lines().count { it.endsWith(line) })
    }

    @Test
    fun stampIsLocalTimeWithMilliseconds() {
        assertEquals("2026-01-05 07:03:09.004", CallJournal.stamp(LocalDateTime(2026, 1, 5, 7, 3, 9, 4_000_000)))
    }
}
