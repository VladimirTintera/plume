package eu.tintera.plume.core.calls

import okio.FileSystem
import okio.Path.Companion.toPath
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CallDetailLogTest {

    private val dir = (FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "plume-detail-${Random.nextLong()}").toString()
    private val details = dir.toPath() / CALL_DETAIL_DIRECTORY

    @AfterTest
    fun cleanUp() = FileSystem.SYSTEM.deleteRecursively(dir.toPath())

    private fun files() = FileSystem.SYSTEM.list(details).map { it.name }.sorted()

    @Test
    fun repeatsAreFilteredAndLinesKept() {
        val log = CallDetailLog.open(dir, callId = 7)!!
        log.write("connection.cc", "Sent STUN BINDING request, id=1", "a")
        log.write("connection.cc", "Sent STUN BINDING request, id=2", "b")
        log.write("p2p_transport_channel.cc", "Connection pruned", "c")
        log.close()
        val name = files().single()
        assertTrue(name.endsWith("-call7.log"), name)
        assertEquals("a\nc\n", FileSystem.SYSTEM.read(details / name) { readUtf8() })
    }

    @Test
    fun onlyTheLastCallsAreKept() {
        FileSystem.SYSTEM.createDirectories(details)
        listOf("20260101-000000-call1.log", "20260102-000000-call2.log", "20260103-000000-call3.log").forEach {
            FileSystem.SYSTEM.write(details / it) { writeUtf8("x") }
        }
        CallDetailLog.open(dir, callId = 4)!!.close()
        val kept = files()
        assertEquals(CallDetailLog.KEEP_FILES, kept.size)
        assertTrue("20260101-000000-call1.log" !in kept, kept.toString())
        assertTrue(kept.any { it.endsWith("-call4.log") }, kept.toString())
    }

    @Test
    fun writeAfterCloseIsIgnored() {
        val log = CallDetailLog.open(dir, callId = 1)!!
        log.close()
        log.write("t", "m", "late")
        assertEquals("", FileSystem.SYSTEM.read(details / files().single()) { readUtf8() })
    }
}
