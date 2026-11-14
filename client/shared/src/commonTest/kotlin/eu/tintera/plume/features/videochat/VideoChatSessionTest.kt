package eu.tintera.plume.features.videochat

import eu.tintera.plume.core.calls.CallConnection
import eu.tintera.plume.core.calls.LiveStreamHost
import eu.tintera.plume.core.calls.LiveStreamMedia
import eu.tintera.plume.core.telegram.LiveVideo
import eu.tintera.plume.core.telegram.LiveVideoQuality
import eu.tintera.plume.core.telegram.VideoChatCall
import eu.tintera.plume.core.telegram.VideoChatParticipantUpdate
import eu.tintera.plume.core.telegram.VideoChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VideoChatSessionTest {

    private class FakeRepository : VideoChatRepository {
        val leaves = mutableListOf<Int>()
        override val callUpdates = MutableSharedFlow<VideoChatCall>()
        override val participantUpdates = MutableSharedFlow<VideoChatParticipantUpdate>()
        override suspend fun join(groupCallId: Int, payload: String, audioSourceId: Int) = Result.success("response")
        override suspend fun leave(groupCallId: Int) {
            leaves += groupCallId
        }
        override suspend fun call(groupCallId: Int) =
            Result.success(VideoChatCall(groupCallId, isActive = true, participantCount = 3, title = "", isRtmp = false))
        override suspend fun loadParticipants(groupCallId: Int) = Unit
        override suspend fun streamTimestamp(groupCallId: Int) = Result.success(0L)
        override suspend fun segment(groupCallId: Int, timeMs: Long, channelId: Int, quality: LiveVideoQuality?) = Result.success(ByteArray(0))
        override suspend fun setMuted(groupCallId: Int, participantId: Long, muted: Boolean) = Result.success(Unit)
        override suspend fun setHandRaised(groupCallId: Int, participantId: Long, raised: Boolean) = Result.success(Unit)
        override suspend fun chatTitle(chatId: Long): String? = "Skupina $chatId"
    }

    private class FakeMedia : LiveStreamMedia {
        var host: LiveStreamHost? = null
        val started = mutableListOf<Long>()
        val stopped = mutableListOf<Long>()
        override fun start(callId: Long, host: LiveStreamHost) {
            started += callId
            this.host = host
        }
        override fun addVideo(callId: Long, participantId: Long, video: LiveVideo) = Unit
        override fun removeVideo(callId: Long, endpoint: String) = Unit
        override fun stop(callId: Long) {
            stopped += callId
        }
    }

    @Test
    fun sessionOutlivesScreenAndLeavesOnlyOnLeave() = runTest {
        val app = CoroutineScope(coroutineContext + Job())
        val repository = FakeRepository()
        val media = FakeMedia()
        val session = VideoChatSession(repository, media, appScope = app)
        session.join(chatId = -100, groupCallId = 5)
        advanceUntilIdle()
        assertEquals(listOf(5L), media.started)
        assertEquals("Skupina -100", session.active.value?.title, "bez názvu videochatu nadpis chatu")
        assertEquals("response", media.host!!.join("payload", 1))
        media.host!!.onConnection(CallConnection.Connected)
        advanceUntilIdle()
        assertTrue(session.active.value!!.running)

        // Obrazovka se zavře a znovu otevře: k běžícímu se jen naváže.
        session.join(chatId = -100, groupCallId = 5)
        advanceUntilIdle()
        assertEquals(listOf(5L), media.started, "týž videochat se znovu nepřipojuje")

        session.leave()
        advanceUntilIdle()
        assertNull(session.active.value)
        assertEquals(listOf(5), repository.leaves)
        app.cancel()
    }

    @Test
    fun joiningAnotherVideoChatLeavesThePrevious() = runTest {
        val app = CoroutineScope(coroutineContext + Job())
        val repository = FakeRepository()
        val media = FakeMedia()
        val session = VideoChatSession(repository, media, appScope = app)
        session.join(chatId = -100, groupCallId = 5)
        advanceUntilIdle()
        media.host!!.join("payload", 1)
        session.join(chatId = -200, groupCallId = 7)
        advanceUntilIdle()
        assertEquals(listOf(5), repository.leaves, "jeden videochat naráz")
        assertEquals(7, session.active.value?.groupCallId)
        assertEquals(-200, session.active.value?.chatId)
        session.leave()
        app.cancel()
    }

    @Test
    fun screenVisibilityCounts() = runTest {
        val app = CoroutineScope(coroutineContext + Job())
        val session = VideoChatSession(FakeRepository(), FakeMedia(), appScope = app)
        session.screenVisible(true)
        session.screenVisible(true)
        session.screenVisible(false)
        advanceUntilIdle()
        assertTrue(session.screenShown.value, "dvě obrazovky, jedna zmizela")
        session.screenVisible(false)
        advanceUntilIdle()
        assertFalse(session.screenShown.value)
        app.cancel()
    }

    /** Přenos je přes účty jeden: připojení napřed opustí videochat v ostatních účtech, téhož ne. */
    @Test
    fun joiningLeavesVideoChatElsewhere() = runTest {
        val app = CoroutineScope(coroutineContext + Job())
        var left = 0
        val session = VideoChatSession(FakeRepository(), FakeMedia(), appScope = app, leaveElsewhere = { left++ })
        session.join(chatId = -100, groupCallId = 5)
        advanceUntilIdle()
        assertEquals(1, left)
        session.join(chatId = -100, groupCallId = 5)
        advanceUntilIdle()
        assertEquals(1, left, "k témuž videochatu se jen připojuje obrazovka")
        session.join(chatId = -200, groupCallId = 6)
        advanceUntilIdle()
        assertEquals(2, left)
        session.leave()
        app.cancel()
    }

    @Test
    fun withoutMediaNothingJoins() = runTest {
        val app = CoroutineScope(coroutineContext + Job())
        val session = VideoChatSession(FakeRepository(), media = null, appScope = app)
        assertFalse(session.supported)
        session.join(chatId = -100, groupCallId = 5)
        advanceUntilIdle()
        assertNull(session.active.value)
        app.cancel()
    }
}
