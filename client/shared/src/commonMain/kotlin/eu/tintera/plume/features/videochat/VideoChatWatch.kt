package eu.tintera.plume.features.videochat

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.calls.CallConnection
import eu.tintera.plume.core.calls.LiveStreamHost
import eu.tintera.plume.core.calls.LiveStreamMedia
import eu.tintera.plume.core.telegram.LiveVideo
import eu.tintera.plume.core.telegram.LiveVideoQuality
import eu.tintera.plume.core.telegram.VideoChatCall
import eu.tintera.plume.core.telegram.VideoChatParticipant
import eu.tintera.plume.core.telegram.VideoChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.concurrent.Volatile

enum class VideoChatPhase { Connecting, Live, Ended, Failed }

data class VideoChatState(
    val phase: VideoChatPhase = VideoChatPhase.Connecting,
    /** Název od správce; prázdný = bez názvu (obrazovka vezme název chatu). */
    val title: String = "",
    val participantCount: Int = 0,
    /** Seřazení jako v TDLib (větší `order` výš); sebe uživatel vidí taky. */
    val participants: List<VideoChatParticipant> = emptyList(),
    /** Čí obraz se kreslí ([mainVideoFor]); `null` = nikdo obraz neposílá. */
    val mainVideoOf: Long? = null,
    /** Přišel obraz — do té doby se kreslí jen seznam. */
    val hasVideo: Boolean = false,
    /** Vysílání přes RTMP: obraz posílá server, účastníci jsou jen posluchači. */
    val isRtmp: Boolean = false,
    /** Vlastní mikrofon je v přenosu zapnutý (mluvím). */
    val micOn: Boolean = false,
) {
    /** Já mezi účastníky; dokud se seznam nedočte, `null`. */
    val me: VideoChatParticipant? get() = participants.firstOrNull { it.isCurrentUser }

    /** Mluvit jde (ne v RTMP vysílání) a správce mě neztlumil — stačí zapnout mikrofon. */
    val canTalk: Boolean get() = !isRtmp && me?.let { !it.isMuted || it.canUnmuteSelf } == true

    /** Ztlumil mě správce: zbývá zvednout ruku. */
    val mutedByAdmin: Boolean get() = !isRtmp && me?.let { it.isMuted && !it.canUnmuteSelf } == true
}

/**
 * Poslech a sledování videochatu: připojení ztlumeně a bez kamery, účastníci, zvuk všech
 * a obraz jednoho z nich. Stejná cesta jako živý příběh (`LiveStoryWatch`), jen s účastníky:
 *
 * - **Účastníci chodí jen updaty** — po připojení se dočtou (`loadParticipants`), změny
 *   přijdou samy. Prázdné pořadí znamená odchod, ale i vypadnutí z dočtené části seznamu
 *   (ověřeno: ztlumený správcem ho dostane hned); proto se po něm dočítá znovu a sebe
 *   ze seznamu nevyhazuje nikdy — z videochatu odchází jen [stop].
 * - **Zvuk všech mluvících knihovna přehrává sama** — přiřazení zdrojů zvuku účastníkům
 *   (`updateAudioSsrcMappings`) běžný videochat ani RTMP nebere („Invalid call type", ověřeno),
 *   patří jen konferenčním hovorům.
 * - **Obraz se kreslí jeden** ([mainVideoFor]): sdílená obrazovka má přednost před kamerou.
 * - **Mluví se jen s mikrofonem v přenosu** ([setMuted]): napřed svolení serveru
 *   (`toggleGroupCallParticipantIsMuted`), pak mikrofon do přenosu. Ztlumí-li mě správce,
 *   mikrofon jde z přenosu pryč sám.
 *
 * Odchod z hovoru jde přes [appScope], jako u živého příběhu: zavřená obrazovka už svůj
 * scope nemá a server by posluchače jinak počítal, dokud ho sám nevyhodí.
 */
class VideoChatWatch(
    private val repository: VideoChatRepository,
    private val media: LiveStreamMedia,
    private val scope: CoroutineScope,
    private val appScope: CoroutineScope,
) {
    private val logger = Logger.withTag("VideoChat")

    private val _state = MutableStateFlow<VideoChatState?>(null)
    val state: StateFlow<VideoChatState?> = _state.asStateFlow()

    private var job: Job? = null
    @Volatile private var watching: Int? = null
    @Volatile private var joined = false

    /** Obraz, který knihovna právě přijímá. */
    private var video: LiveVideo? = null

    fun watch(groupCallId: Int) {
        if (watching == groupCallId) return
        stop()
        watching = groupCallId
        joined = false
        _state.value = VideoChatState()
        job = scope.launch {
            launch {
                repository.callUpdates.filter { it.groupCallId == groupCallId }.collect { call ->
                    _state.update { it?.withCall(call) }
                    if (!call.isActive) ended()
                }
            }
            launch {
                repository.participantUpdates.filter { it.groupCallId == groupCallId }.collect { update ->
                    _state.update { it?.withParticipant(update.participant) }
                    participantsChanged()
                    // Prázdné pořadí má i ten, kdo jen vypadl z dočtené části seznamu (ztlumený
                    // přestal mluvit) — dočíst, ať nezmizí, kdo ve videochatu pořád je.
                    if (update.participant.order.isEmpty() && !update.participant.isCurrentUser) {
                        launch { repository.loadParticipants(groupCallId) }
                    }
                }
            }
            val call = repository.call(groupCallId).getOrNull()
            if (call != null) {
                _state.update { it?.withCall(call) }
                if (!call.isActive) {
                    ended()
                    return@launch
                }
            }
            media.start(groupCallId.toLong(), Host(groupCallId))
        }
    }

    fun stop() {
        val id = watching ?: return
        watching = null
        job?.cancel()
        job = null
        video = null
        _state.value = null
        media.stop(id.toLong())
        if (joined) appScope.launch { repository.leave(id) }
        joined = false
    }

    private fun ended() {
        _state.update { it?.copy(phase = VideoChatPhase.Ended) }
        watching?.let { media.stop(it.toLong()) }
    }

    /**
     * Zapne, nebo vypne mluvení. Zapnutí napřed u serveru (bez svolení by hlas nikdo neslyšel)
     * a teprve pak mikrofon; vypnutí opačně, ať mikrofon nesvítí ani chvíli navíc.
     */
    suspend fun setMuted(muted: Boolean): Result<Unit> {
        val id = watching ?: return Result.failure(IllegalStateException("No video chat"))
        val me = _state.value?.me ?: return Result.failure(IllegalStateException("Not joined yet"))
        if (muted) {
            microphone(id, on = false)
            return repository.setMuted(id, me.id, muted = true)
        }
        return repository.setMuted(id, me.id, muted = false).onSuccess {
            if (watching == id) microphone(id, on = true)
        }
    }

    suspend fun setHandRaised(raised: Boolean): Result<Unit> {
        val id = watching ?: return Result.failure(IllegalStateException("No video chat"))
        val me = _state.value?.me ?: return Result.failure(IllegalStateException("Not joined yet"))
        return repository.setHandRaised(id, me.id, raised)
    }

    private fun microphone(id: Int, on: Boolean) {
        if (_state.value?.micOn == on) return
        _state.update { it?.copy(micOn = on) }
        media.setMicrophone(id.toLong(), on)
    }

    /** Výběr obrazu po každé změně seznamu; ztlumí-li mě správce, mikrofon pryč. */
    private fun participantsChanged() {
        val id = watching ?: return
        val current = _state.value ?: return
        if (current.micOn && current.me?.isMuted == true) microphone(id, on = false)
        val main = mainVideoFor(current.participants)
        if (main?.second?.endpoint != video?.endpoint) {
            video?.let { media.removeVideo(id.toLong(), it.endpoint) }
            video = main?.second
            main?.let { (participantId, next) -> media.addVideo(id.toLong(), participantId, next) }
            _state.update { it?.copy(mainVideoOf = main?.first, hasVideo = if (main == null) false else it.hasVideo) }
        }
    }

    private inner class Host(private val groupCallId: Int) : LiveStreamHost {

        override suspend fun join(payload: String, audioSourceId: Int): String? {
            val response = repository.join(groupCallId, payload, audioSourceId).getOrNull()
            if (response != null) {
                if (watching == groupCallId) joined = true
                return response
            }
            // Videochat mezitím skončil — to není nezdar připojení, ale konec.
            val call = repository.call(groupCallId).getOrNull()
            if (watching == groupCallId && (call == null || !call.isActive)) ended()
            return null
        }

        override suspend fun timestamp(): Long = repository.streamTimestamp(groupCallId).getOrDefault(0L)

        override suspend fun segment(timeMs: Long, channelId: Int, quality: LiveVideoQuality?): ByteArray? =
            repository.segment(groupCallId, timeMs, channelId, quality).getOrNull()

        override fun onJoined() {
            scope.launch { if (watching == groupCallId) repository.loadParticipants(groupCallId) }
        }

        override fun onConnection(state: CallConnection) {
            logger.i { "Videochat $groupCallId: $state" }
            _state.update { current ->
                when {
                    current == null || watching != groupCallId || current.phase == VideoChatPhase.Ended -> current
                    state == CallConnection.Connected -> current.copy(phase = VideoChatPhase.Live)
                    state == CallConnection.Failed -> current.copy(phase = VideoChatPhase.Failed)
                    else -> current
                }
            }
        }

        override fun onVideo(active: Boolean) {
            _state.update { current ->
                when {
                    current == null || watching != groupCallId -> current
                    // Stream po segmentech spojení nehlásí; první snímek znamená, že běží.
                    active && current.phase == VideoChatPhase.Connecting -> current.copy(hasVideo = true, phase = VideoChatPhase.Live)
                    else -> current.copy(hasVideo = active)
                }
            }
        }

    }
}

internal fun VideoChatState.withCall(call: VideoChatCall) = copy(
    title = call.title,
    participantCount = call.participantCount,
    isRtmp = call.isRtmp,
)

/**
 * Účastník přibude, změní se, nebo (prázdné pořadí) odejde; seznam zůstane seřazený. Sebe
 * ne: prázdné pořadí u sebe znamená jen, že nejsem v dočtené části seznamu.
 */
internal fun VideoChatState.withParticipant(participant: VideoChatParticipant): VideoChatState {
    val others = participants.filterNot { it.id == participant.id }
    val next = if (participant.order.isEmpty() && !participant.isCurrentUser) others else others + participant
    return copy(participants = next.sortedByDescending { it.order })
}

/**
 * Čí obraz kreslit: sdílená obrazovka má přednost (kvůli ní se do videochatu obvykle dívá),
 * jinak kamera prvního v pořadí. Vlastní obraz ne — posluchač žádný neposílá.
 */
internal fun mainVideoFor(participants: List<VideoChatParticipant>): Pair<Long, LiveVideo>? {
    val others = participants.filterNot { it.isCurrentUser }
    others.firstNotNullOfOrNull { participant -> participant.screen?.let { participant.id to it } }?.let { return it }
    return others.firstNotNullOfOrNull { participant -> participant.camera?.let { participant.id to it } }
}
