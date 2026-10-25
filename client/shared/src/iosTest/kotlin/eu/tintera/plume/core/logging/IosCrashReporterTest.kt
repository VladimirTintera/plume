package eu.tintera.plume.core.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Co jde přes most do Crashlytics: jméno, zpráva s řetězem příčin a adresy stacku. */
class IosCrashReporterTest {

    private class RecordingBridge : CrashlyticsBridge {
        val logs = mutableListOf<String>()
        val exceptions = mutableListOf<Triple<String, String, List<Long>>>()
        override fun log(message: String) {
            logs += message
        }
        override fun recordException(name: String, reason: String, stackAddresses: List<Long>) {
            exceptions += Triple(name, reason, stackAddresses)
        }
    }

    @Test
    fun exceptionCarriesNameCausesAndStackAddresses() {
        val bridge = RecordingBridge()
        val failure = IllegalStateException("Sadu nejde načíst.", IllegalArgumentException("STICKERSET_INVALID"))
        IosCrashReporter(bridge).recordException(failure)

        val (name, reason, stack) = bridge.exceptions.single()
        assertEquals("kotlin.IllegalStateException", name)
        assertEquals("Sadu nejde načíst.\nCaused by: kotlin.IllegalArgumentException: STICKERSET_INVALID", reason)
        assertTrue(stack.isNotEmpty(), "bez adres by Crashlytics nemělo co symbolizovat")
    }

    private class AppFailure(message: String) : IllegalStateException(message)

    @Test
    @OptIn(kotlin.experimental.ExperimentalNativeApi::class)
    fun stackStartsAtThrowSiteNotInConstructors() {
        val failure = AppFailure("x")
        val all = failure.getStackTraceAddresses()
        val fromThrowSite = failure.stackFromThrowSite()
        val names = failure.getStackTrace()
        val constructors = names.takeWhile { "#<init>(" in it }.size
        assertTrue(constructors >= 4, "Throwable, Exception, RuntimeException, IllegalState, AppFailure: ${names.take(6)}")
        assertEquals(all.drop(constructors), fromThrowSite)
        assertTrue("#<init>(" !in names[constructors], "nahoře je místo vzniku: ${names[constructors]}")
    }

    @Test
    fun logLinesGoThroughUnchanged() {
        val bridge = RecordingBridge()
        IosCrashReporter(bridge).log("I/App: Start")
        assertEquals(listOf("I/App: Start"), bridge.logs)
        assertTrue(bridge.exceptions.isEmpty(), "řádek logu není problém")
    }

    @Test
    fun deepCauseChainIsCut() {
        val deep = (1..10).fold(RuntimeException("0")) { cause, level -> RuntimeException("$level", cause) }
        val reason = deep.reasonWithCauses()
        assertEquals(6, reason.lines().size, "zpráva a nejvýš pět příčin: $reason")
    }
}
