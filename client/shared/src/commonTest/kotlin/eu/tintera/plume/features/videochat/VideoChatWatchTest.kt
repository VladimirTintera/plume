package eu.tintera.plume.features.videochat

import eu.tintera.plume.core.calls.CallConnection
import eu.tintera.plume.core.calls.LiveStreamHost
import eu.tintera.plume.core.calls.LiveStreamMedia
import eu.tintera.plume.core.telegram.LiveVideo
import eu.tintera.plume.core.telegram.LiveVideoQuality
import eu.tintera.plume.core.telegram.VideoChatCall
import eu.tintera.plume.core.telegram.VideoChatParticipant
import eu.tintera.plume.core.telegram.VideoChatParticipantUpdate
import eu.tintera.plume.core.telegram.VideoChatRepository
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
class VideoChatWatchTest {

    private class FakeRepository : VideoChatRepository {
        var active = true
        val leaves = mutableListOf<Int>()
        var loads = 0
        override val callUpdates = MutableSharedFlow<VideoChatCall>()
        override val participantUpdates = MutableSharedFlow<VideoChatParticipantUpdate>()

        override suspend fun join(groupCallId: Int, payload: String, audioSourceId: Int) = Result.success("response")

        override suspend fun leave(groupCallId: Int) {
            leaves += groupCallId
        }

        override suspend fun call(groupCallId: Int) =
            Result.success(VideoChatCall(groupCallId, active, participantCount = 4, title = "Porada", isRtmp = false))

        override suspend fun loadParticipants(groupCallId: Int) {
            loads++
        }

        override suspend fun streamTimestamp(groupCallId: Int) = Result.success(0L)

        override suspend fun segment(groupCallId: Int, timeMs: Long, channelId: Int, quality: LiveVideoQuality?) =
            Result.success(ByteArray(0))

        override suspend fun chatTitle(chatId: Long): String? = "Skupina"
    }

    private class FakeMedia : LiveStreamMedia {
        var host: LiveStreamHost? = null
        val started = mutableListOf<Long>()
        val stopped = mutableListOf<Long>()
        val added = mutableListOf<String>()
        val removed = mutableListOf<String>()

        override fun start(callId: Long, host: LiveStreamHost) {
            started += callId
            this.host = host
        }

        override fun addVideo(callId: Long, participantId: Long, video: LiveVideo) {
            added += video.endpoint
        }

        override fun removeVideo(callId: Long, endpoint: String) {
            removed += endpoint
        }

        override fun stop(callId: Long) {
            stopped += callId
        }
    }

    private fun TestScope.watch(repository: FakeRepository, media: FakeMedia) =
        VideoChatWatch(repository, media, scope = this, appScope = this)

    private fun participant(
        id: Long,
        order: String,
        camera: String? = null,
        screen: String? = null,
        me: Boolean = false,
    ) = VideoChatParticipant(
        id = id,
        name = "Účastník $id",
        avatarFileId = null,
        camera = camera?.let { LiveVideo(it, emptyList()) },
        screen = screen?.let { LiveVideo(it, emptyList()) },
        isMuted = true,
        isHandRaised = false,
        isCurrentUser = me,
        order = order,
    )

    @Test
    fun joinsAndLoadsParticipants() = runTest {
        val repository = FakeRepository()
        val media = FakeMedia()
        val watch = watch(repository, media)
        watch.watch(5)
        advanceUntilIdle()
        assertEquals(listOf(5L), media.started)
        assertEquals("Porada", watch.state.value?.title)
        val host = media.host!!
        assertEquals("response", host.join("payload", 1))
        host.onJoined()
        advanceUntilIdle()
        assertEquals(1, repository.loads, "po připojení se účastníci dočtou")

        repository.participantUpdates.emit(VideoChatParticipantUpdate(5, participant(1, order = "2")))
        repository.participantUpdates.emit(VideoChatParticipantUpdate(5, participant(9, order = "3", me = true)))
        repository.participantUpdates.emit(VideoChatParticipantUpdate(6, participant(2, order = "1")))
        advanceUntilIdle()
        assertEquals(listOf(9L, 1L), watch.state.value?.participants?.map { it.id }, "seřazení podle pořadí, jiný hovor ne")

        host.onConnection(CallConnection.Connected)
        assertEquals(VideoChatPhase.Live, watch.state.value?.phase)

        watch.stop()
        advanceUntilIdle()
        assertNull(watch.state.value)
        assertEquals(listOf(5L), media.stopped)
        assertEquals(listOf(5), repository.leaves)
    }

    @Test
    fun screenShareWinsAndVideoFollowsParticipants() = runTest {
        val repository = FakeRepository()
        val media = FakeMedia()
        val watch = watch(repository, media)
        watch.watch(5)
        advanceUntilIdle()
        repository.participantUpdates.emit(VideoChatParticipantUpdate(5, participant(1, order = "3", camera = "cam1")))
        advanceUntilIdle()
        assertEquals(listOf("cam1"), media.added)
        assertEquals(1L, watch.state.value?.mainVideoOf)

        repository.participantUpdates.emit(VideoChatParticipantUpdate(5, participant(2, order = "1", screen = "screen2")))
        advanceUntilIdle()
        assertEquals(listOf("cam1", "screen2"), media.added, "sdílená obrazovka má přednost")
        assertEquals(listOf("cam1"), media.removed)
        assertEquals(2L, watch.state.value?.mainVideoOf)

        repository.participantUpdates.emit(VideoChatParticipantUpdate(5, participant(2, order = "", screen = "screen2")))
        advanceUntilIdle()
        assertEquals(listOf(1L), watch.state.value?.participants?.map { it.id }, "prázdné pořadí = odešel")
        assertEquals(listOf("cam1", "screen2", "cam1"), media.added, "zpátky kamera prvního")
        watch.stop()
    }

    @Test
    fun endedCallStopsAndInactiveIsNotJoined() = runTest {
        val repository = FakeRepository()
        val media = FakeMedia()
        val watch = watch(repository, media)
        watch.watch(5)
        advanceUntilIdle()
        repository.callUpdates.emit(VideoChatCall(5, isActive = false, participantCount = 0, title = "", isRtmp = false))
        advanceUntilIdle()
        assertEquals(VideoChatPhase.Ended, watch.state.value?.phase)
        assertEquals(listOf(5L), media.stopped)
        watch.stop()

        val inactive = FakeRepository().apply { active = false }
        val otherMedia = FakeMedia()
        val second = watch(inactive, otherMedia)
        second.watch(7)
        advanceUntilIdle()
        assertEquals(VideoChatPhase.Ended, second.state.value?.phase)
        assertTrue(otherMedia.started.isEmpty())
        second.stop()
        advanceUntilIdle()
        assertTrue(inactive.leaves.isEmpty(), "nepřipojený posluchač neodchází")
    }
}
