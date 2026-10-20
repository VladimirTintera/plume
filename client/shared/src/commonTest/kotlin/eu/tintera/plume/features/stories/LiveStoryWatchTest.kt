package eu.tintera.plume.features.stories

import eu.tintera.plume.core.calls.CallConnection
import eu.tintera.plume.core.calls.LiveStreamHost
import eu.tintera.plume.core.calls.LiveStreamMedia
import eu.tintera.plume.core.telegram.LiveCall
import eu.tintera.plume.core.telegram.LiveParticipantUpdate
import eu.tintera.plume.core.telegram.LiveStoryCall
import eu.tintera.plume.core.telegram.LiveStoryRepository
import eu.tintera.plume.core.telegram.LiveStreamer
import eu.tintera.plume.core.telegram.LiveVideo
import eu.tintera.plume.core.telegram.LiveVideoQuality
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LiveStoryWatchTest {

    private class FakeRepository : LiveStoryRepository {
        var active = true
        var joinFails = false
        val joins = mutableListOf<Int>()
        val leaves = mutableListOf<Int>()
        override val callUpdates = MutableSharedFlow<LiveCall>()
        override val participantUpdates = MutableSharedFlow<LiveParticipantUpdate>()

        override suspend fun join(groupCallId: Int, payload: String, audioSourceId: Int): Result<String> {
            joins += groupCallId
            return if (joinFails) Result.failure(IllegalStateException("GROUPCALL_FORBIDDEN")) else Result.success("response")
        }

        override suspend fun leave(groupCallId: Int) {
            leaves += groupCallId
        }

        override suspend fun call(groupCallId: Int) = Result.success(LiveCall(groupCallId, active, participantCount = 12, isRtmp = false))

        override suspend fun streamer(groupCallId: Int) =
            Result.success<LiveStreamer?>(LiveStreamer(participantId = 7, camera = LiveVideo("cam", emptyList()), screen = null))

        override suspend fun streamTimestamp(groupCallId: Int) = Result.success(0L)

        override suspend fun segment(groupCallId: Int, timeMs: Long, channelId: Int, quality: LiveVideoQuality?) =
            Result.success(ByteArray(0))
    }

    private class FakeMedia : LiveStreamMedia {
        var host: LiveStreamHost? = null
        val started = mutableListOf<Long>()
        val stopped = mutableListOf<Long>()
        val videos = mutableListOf<String>()

        override fun start(callId: Long, host: LiveStreamHost) {
            started += callId
            this.host = host
        }

        override fun addVideo(callId: Long, participantId: Long, video: LiveVideo) {
            videos += video.endpoint
        }

        override fun removeVideo(callId: Long, endpoint: String) = Unit

        override fun stop(callId: Long) {
            stopped += callId
        }
    }

    private val key = StoryKey(7, 1)
    private val call = LiveStoryCall(groupCallId = 5, isRtmp = false)

    /** Na plánovači testu; odběry updatů běží, dokud test sledování nezastaví. */
    private fun TestScope.watch(repository: FakeRepository, media: FakeMedia) =
        LiveStoryWatch(repository, media, scope = this, appScope = this)

    @Test
    fun joinsConnectsAndAddsStreamerVideoOnce() = runTest {
        val repository = FakeRepository()
        val media = FakeMedia()
        val watch = watch(repository, media)
        watch.watch(key, call)
        advanceUntilIdle()
        assertEquals(listOf(5L), media.started)
        assertEquals(12, watch.state.value?.viewers)
        val host = media.host!!
        assertEquals("response", host.join("payload", 1))
        host.onJoined()
        advanceUntilIdle()
        host.onJoined()
        advanceUntilIdle()
        assertEquals(listOf("cam"), media.videos, "obraz vysílajícího jen jednou")
        host.onConnection(CallConnection.Connected)
        assertEquals(LivePhase.Live, watch.state.value?.phase)
        host.onVideo(true)
        assertTrue(watch.state.value?.hasVideo == true)

        watch.stop()
        advanceUntilIdle()
        assertNull(watch.state.value)
        assertEquals(listOf(5L), media.stopped)
        assertEquals(listOf(5), repository.leaves, "po připojení se z hovoru odchází")
    }

    @Test
    fun firstFrameMeansLiveForSegmentStreams() = runTest {
        val media = FakeMedia()
        val watch = watch(FakeRepository(), media)
        watch.watch(key, call.copy(isRtmp = true))
        advanceUntilIdle()
        media.host!!.onVideo(true)
        assertEquals(LivePhase.Live, watch.state.value?.phase, "stream po segmentech spojení nehlásí")
        watch.stop()
    }

    @Test
    fun endedCallStopsMedia() = runTest {
        val repository = FakeRepository()
        val media = FakeMedia()
        val watch = watch(repository, media)
        watch.watch(key, call)
        advanceUntilIdle()
        repository.callUpdates.emit(LiveCall(5, isActive = false, participantCount = 3, isRtmp = false))
        advanceUntilIdle()
        assertEquals(LivePhase.Ended, watch.state.value?.phase)
        assertEquals(3, watch.state.value?.viewers)
        assertEquals(listOf(5L), media.stopped)
        media.host!!.onConnection(CallConnection.Failed)
        assertEquals(LivePhase.Ended, watch.state.value?.phase, "konec nepřebije nezdar spojení")
        watch.stop()
    }

    @Test
    fun inactiveCallIsEndedWithoutJoining() = runTest {
        val repository = FakeRepository().apply { active = false }
        val media = FakeMedia()
        val watch = watch(repository, media)
        watch.watch(key, call)
        advanceUntilIdle()
        assertEquals(LivePhase.Ended, watch.state.value?.phase)
        assertTrue(media.started.isEmpty())
        watch.stop()
        advanceUntilIdle()
        assertTrue(repository.leaves.isEmpty(), "nepřipojený divák z hovoru neodchází")
    }

    @Test
    fun failedJoinOfEndedCallSaysEnded() = runTest {
        val repository = FakeRepository().apply { joinFails = true }
        val media = FakeMedia()
        val watch = watch(repository, media)
        watch.watch(key, call)
        advanceUntilIdle()
        repository.active = false
        assertNull(media.host!!.join("payload", 1))
        assertEquals(LivePhase.Ended, watch.state.value?.phase, "vysílání skončilo dřív, než se divák připojil")
        watch.stop()

        val other = FakeRepository().apply { joinFails = true }
        val otherMedia = FakeMedia()
        val second = watch(other, otherMedia)
        second.watch(key, call)
        advanceUntilIdle()
        assertNull(otherMedia.host!!.join("payload", 1))
        otherMedia.host!!.onConnection(CallConnection.Failed)
        assertEquals(LivePhase.Failed, second.state.value?.phase, "běžící vysílání, ke kterému se nejde připojit")
        second.stop()
    }
}
