package eu.tintera.plume.core.language

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AppLanguageTest {

    @Test
    fun prvniHlaseniNeniZmena() = runTest {
        val language = AppLanguage()
        var changes = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) { language.changes.collect { changes++ } }

        language.report("en")
        testScheduler.runCurrent()
        assertEquals(0, changes)

        language.report("en")
        testScheduler.runCurrent()
        assertEquals(0, changes, "Tentýž jazyk podruhé není změna.")

        language.report("cs")
        testScheduler.runCurrent()
        assertEquals(1, changes)
        job.cancel()
    }

    @Test
    fun pozdniOdberatelDostaneJenDalsiZmeny() = runTest {
        val language = AppLanguage()
        language.report("en")
        language.report("cs")

        var changes = 0
        val job = launch(start = CoroutineStart.UNDISPATCHED) { language.changes.collect { changes++ } }
        testScheduler.runCurrent()
        assertEquals(0, changes, "Jazyk platný při odběru už je ten, ve kterém se převádělo.")

        language.report("en")
        testScheduler.runCurrent()
        assertEquals(1, changes)
        job.cancel()
    }
}
