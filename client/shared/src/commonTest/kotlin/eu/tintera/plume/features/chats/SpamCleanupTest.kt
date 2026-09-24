package eu.tintera.plume.features.chats

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpamCleanupTest {

    private class Recorder {
        val steps = mutableListOf<String>()
        var failOn: String? = null

        fun step(name: String): suspend () -> Result<Unit> = {
            steps += name
            if (failOn == name) Result.failure(RuntimeException(name)) else Result.success(Unit)
        }
    }

    private suspend fun run(cleanup: SpamCleanup, recorder: Recorder): Result<Unit> = cleanUpSpam(
        cleanup = cleanup,
        block = recorder.step("block"),
        delete = recorder.step("delete"),
    )

    @Test
    fun bezVolebSeJenSmaze() = runTest {
        val recorder = Recorder()
        val result = run(SpamCleanup(), recorder)

        assertTrue(result.isSuccess)
        assertEquals(listOf("delete"), recorder.steps)
    }

    @Test
    fun jenZablokovatSmazeAzPoBlokovani() = runTest {
        val recorder = Recorder()

        run(SpamCleanup(block = true), recorder)

        assertEquals(listOf("block", "delete"), recorder.steps)
    }

    /** Kdyby se chat smazal i po selhaném blokování, bot by dál psal a nebylo by kde to opravit. */
    @Test
    fun selhaneBlokovaniChatNESMAZE() = runTest {
        val recorder = Recorder().apply { failOn = "block" }

        val result = run(SpamCleanup(block = true), recorder)

        assertTrue(result.isFailure)
        assertEquals(listOf("block"), recorder.steps)
    }

    @Test
    fun selhaneSmazaniSeOhlasi() = runTest {
        val recorder = Recorder().apply { failOn = "delete" }

        val result = run(SpamCleanup(block = true), recorder)

        assertTrue(result.isFailure)
        assertEquals(listOf("block", "delete"), recorder.steps)
    }
}
