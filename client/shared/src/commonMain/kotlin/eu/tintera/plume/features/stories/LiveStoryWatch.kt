package eu.tintera.plume.features.stories

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.calls.CallConnection
import eu.tintera.plume.core.calls.LiveStreamHost
import eu.tintera.plume.core.calls.LiveStreamMedia
import eu.tintera.plume.core.telegram.LiveStoryCall
import eu.tintera.plume.core.telegram.LiveStoryRepository
import eu.tintera.plume.core.telegram.LiveStreamer
import eu.tintera.plume.core.telegram.LiveVideoQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

/** Jak je na tom sledovaný živý příběh. */
enum class LivePhase { Connecting, Live, Ended, Failed }

data class LiveStoryState(
    val key: StoryKey,
    val phase: LivePhase = LivePhase.Connecting,
    /** Kolik lidí vysílání sleduje (`participantCount`); 0 = zatím neznámo. */
    val viewers: Int = 0,
    /** Přišel obraz — do té doby se čeká (vysílající ještě nezapnul kameru, stream se plní). */
    val hasVideo: Boolean = false,
)

/**
 * Sledování živého příběhu v prohlížeči: připojení jako divák, obraz vysílajícího, počet
 * diváků a konec vysílání. Najednou běží jen jeden — přechod na jiný příběh ho odpojí.
 *
 * Odchod z hovoru jde přes [appScope]: zavřený prohlížeč už `viewModelScope` nemá a server
 * by diváka jinak počítal, dokud ho sám nevyhodí.
 */
class LiveStoryWatch(
    private val repository: LiveStoryRepository,
    private val media: LiveStreamMedia,
    private val scope: CoroutineScope,
    private val appScope: CoroutineScope,
) {
    private val logger = Logger.withTag("LiveStory")

    private val _state = MutableStateFlow<LiveStoryState?>(null)
    val state: StateFlow<LiveStoryState?> = _state.asStateFlow()

    private var job: Job? = null
    @Volatile private var watching: LiveStoryCall? = null

    /** Server diváka přijal — jen pak má smysl z hovoru odcházet. */
    @Volatile private var joined = false

    /** Obraz, který knihovna přijímá (endpoint), ať se nepřidává dvakrát. */
    private val endpoints = mutableSetOf<String>()

    fun watch(key: StoryKey, call: LiveStoryCall) {
        if (watching == call && _state.value?.key == key) return
        stop()
        watching = call
        joined = false
        _state.value = LiveStoryState(key)
        val id = call.groupCallId
        job = scope.launch {
            launch {
                repository.callUpdates.filter { it.groupCallId == id }.collect { update ->
                    _state.update { it?.copy(viewers = update.participantCount) }
                    if (!update.isActive) ended()
                }
            }
            if (!call.isRtmp) {
                launch {
                    repository.participantUpdates.filter { it.groupCallId == id }.collect { addVideo(it.streamer) }
                }
            }
            val info = repository.call(id).getOrNull()
            if (info != null) {
                _state.update { it?.copy(viewers = info.participantCount) }
                if (!info.isActive) {
                    ended()
                    return@launch
                }
            }
            media.start(id.toLong(), Host(call))
        }
    }

    fun stop() {
        val call = watching ?: return
        watching = null
        job?.cancel()
        job = null
        endpoints.clear()
        _state.value = null
        media.stop(call.groupCallId.toLong())
        if (joined) appScope.launch { repository.leave(call.groupCallId) }
        joined = false
    }

    private fun ended() {
        _state.update { it?.copy(phase = LivePhase.Ended) }
        watching?.let { media.stop(it.groupCallId.toLong()) }
    }

    private fun addVideo(streamer: LiveStreamer) {
        val call = watching ?: return
        // Kamera, a když ji vysílající nemá, sdílená obrazovka; knihovna kreslí, co přijde.
        listOfNotNull(streamer.camera, streamer.screen).forEach { video ->
            if (endpoints.add(video.endpoint)) media.addVideo(call.groupCallId.toLong(), streamer.participantId, video)
        }
    }

    private inner class Host(private val call: LiveStoryCall) : LiveStreamHost {
        private val id = call.groupCallId

        override suspend fun join(payload: String, audioSourceId: Int): String? {
            val response = repository.join(id, payload, audioSourceId).getOrNull()
            if (response != null) {
                if (watching == call) joined = true
                return response
            }
            // Vysílání mezitím skončilo (server pak odpoví `GROUPCALL_FORBIDDEN`): to není
            // nezdar připojení, ale konec — a prohlížeč má říct to druhé.
            val info = repository.call(id).getOrNull()
            if (watching == call && (info == null || !info.isActive)) ended()
            return null
        }

        override suspend fun timestamp(): Long = repository.streamTimestamp(id).getOrDefault(0L)

        override suspend fun segment(timeMs: Long, channelId: Int, quality: LiveVideoQuality?): ByteArray? =
            repository.segment(id, timeMs, channelId, quality).getOrNull()

        override fun onJoined() {
            // RTMP vysílá server, ne účastník: knihovna si stream obstará sama.
            if (call.isRtmp) return
            scope.launch {
                repository.streamer(id).onSuccess { streamer -> streamer?.let(::addVideo) }
            }
        }

        override fun onConnection(state: CallConnection) {
            logger.i { "Živý příběh $id: $state" }
            _state.update { current ->
                when {
                    current == null || watching != call || current.phase == LivePhase.Ended -> current
                    state == CallConnection.Connected -> current.copy(phase = LivePhase.Live)
                    state == CallConnection.Failed -> current.copy(phase = LivePhase.Failed)
                    else -> current
                }
            }
        }

        override fun onVideo(active: Boolean) {
            _state.update { current ->
                when {
                    current == null || watching != call -> current
                    // Stream po segmentech spojení nehlásí; první snímek znamená, že vysílání běží.
                    active && current.phase == LivePhase.Connecting -> current.copy(hasVideo = true, phase = LivePhase.Live)
                    else -> current.copy(hasVideo = active)
                }
            }
        }
    }
}
