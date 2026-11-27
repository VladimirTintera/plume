package eu.tintera.plume.features.stories

import eu.tintera.plume.core.stories.StoryKey
import eu.tintera.plume.core.calls.CallConnection
import eu.tintera.plume.core.calls.LiveStreamHost
import eu.tintera.plume.core.calls.LiveStreamMedia
import eu.tintera.plume.core.telegram.LiveCall
import eu.tintera.plume.core.telegram.LiveMessage
import eu.tintera.plume.core.telegram.LiveMessageEvent
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LiveStoryWatchTest {

    private class FakeRepository : LiveStoryRepository {
        var active = true
        var joinFails = false
        var canSend = true
        var messageStars = 0L
        val sent = mutableListOf<String>()
        val deleted = mutableListOf<Pair<Int, Boolean>>()
        override val messageEvents = MutableSharedFlow<LiveMessageEvent>()
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

        override suspend fun call(groupCallId: Int) = Result.success(
            LiveCall(groupCallId, active, participantCount = 12, isRtmp = false, canSendMessages = canSend, messageStarCount = messageStars),
        )

        override suspend fun streamer(groupCallId: Int) =
            Result.success<LiveStreamer?>(LiveStreamer(participantId = 7, camera = LiveVideo("cam", emptyList()), screen = null))

        override suspend fun streamTimestamp(groupCallId: Int) = Result.success(0L)

        override suspend fun segment(groupCallId: Int, timeMs: Long, channelId: Int, quality: LiveVideoQuality?) =
            Result.success(ByteArray(0))

        override suspend fun sendMessage(groupCallId: Int, text: String): Result<Unit> {
            sent += text
            return Result.success(Unit)
        }

        override suspend fun deleteMessage(groupCallId: Int, messageId: Int, reportSpam: Boolean): Result<Unit> {
            deleted += messageId to reportSpam
            return Result.success(Unit)
        }
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
    private fun TestScope.watch(repository: FakeRepository, media: FakeMedia, onSendFailed: () -> Unit = {}) =
        LiveStoryWatch(repository, media, scope = this, appScope = this, onSendFailed = onSendFailed)

    private fun message(id: Int, text: String = "ahoj") = LiveMessage(
        id = id,
        senderId = 1,
        senderName = "Jana",
        avatarFileId = null,
        text = text,
        starCount = 0,
        isFromOwner = false,
        canBeDeleted = true,
    )

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

    @Test
    fun chatCollectsMessagesOfWatchedCallOnly() = runTest {
        val repository = FakeRepository()
        var failures = 0
        val watch = watch(repository, FakeMedia()) { failures++ }
        watch.watch(key, call)
        advanceUntilIdle()
        assertTrue(watch.state.value?.canSend == true)
        repository.messageEvents.emit(LiveMessageEvent.New(5, message(1)))
        repository.messageEvents.emit(LiveMessageEvent.New(6, message(2)))
        repository.messageEvents.emit(LiveMessageEvent.New(5, message(3)))
        advanceUntilIdle()
        assertEquals(listOf(1, 3), watch.state.value?.messages?.map { it.id }, "zprávy jiného vysílání ne")

        repository.messageEvents.emit(LiveMessageEvent.SendFailed(5, 3))
        advanceUntilIdle()
        assertEquals(listOf(1), watch.state.value?.messages?.map { it.id })
        assertEquals(1, failures, "neodeslanou zprávu uživatel pozná")

        assertTrue(watch.send("hezké").isSuccess)
        assertTrue(watch.delete(1, reportSpam = true).isSuccess)
        assertEquals(listOf("hezké"), repository.sent)
        assertEquals(listOf(1 to true), repository.deleted)
        assertEquals(emptyList(), watch.state.value?.messages, "smazaná zmizí hned, bez čekání na update")
        watch.stop()
        assertTrue(watch.send("po konci").isFailure, "bez sledování se nic neposílá")
    }

    @Test
    fun paidCommentsAreNotOffered() = runTest {
        val repository = FakeRepository().apply { messageStars = 10 }
        val watch = watch(repository, FakeMedia())
        watch.watch(key, call)
        advanceUntilIdle()
        assertFalse(watch.state.value!!.canSend, "Plume hvězdami neplatí")
        assertTrue(watch.state.value!!.paidOnly)
        watch.stop()
    }

    @Test
    fun messageEventsUpdateState() {
        val state = LiveStoryState(key)
        val duplicated = state
            .withMessage(LiveMessageEvent.New(5, message(1, "první")))
            .withMessage(LiveMessageEvent.New(5, message(2)))
            .withMessage(LiveMessageEvent.New(5, message(1, "znovu")))
        assertEquals(listOf(2, 1), duplicated.messages.map { it.id }, "táž zpráva se nahradí, nezdvojí")
        assertEquals("znovu", duplicated.messages.last().text)

        val full = (1..MAX_LIVE_MESSAGES + 5).fold(state) { acc, id -> acc.withMessage(LiveMessageEvent.New(5, message(id))) }
        assertEquals(MAX_LIVE_MESSAGES, full.messages.size)
        assertEquals(6, full.messages.first().id, "nejstarší odpadají")

        val deleted = duplicated.withMessage(LiveMessageEvent.Deleted(5, setOf(1, 2)))
        assertTrue(deleted.messages.isEmpty())

        val reacted = state
            .withMessage(LiveMessageEvent.PaidReaction(5, "Jana", 50))
            .withMessage(LiveMessageEvent.PaidReaction(5, "Jana", 50))
        assertEquals(2, reacted.reaction?.id, "i stejná reakce proletí znovu")
        assertEquals(50, reacted.reaction?.starCount)
    }
}
