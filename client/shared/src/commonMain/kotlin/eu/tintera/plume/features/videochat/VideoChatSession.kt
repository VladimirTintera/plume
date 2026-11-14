package eu.tintera.plume.features.videochat

import eu.tintera.plume.core.calls.LiveStreamMedia
import eu.tintera.plume.core.telegram.VideoChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Videochat, ve kterém posluchač je, s tím, co o něm potřebuje pruh a notifikace. */
data class ActiveVideoChat(
    val chatId: Long,
    val groupCallId: Int,
    /** Název chatu; nadpis, když správce videochat nepojmenoval. */
    val chatTitle: String,
    val state: VideoChatState,
) {
    /** Nadpis pruhu a notifikace: název videochatu, jinak chatu. */
    val title: String get() = state.title.ifBlank { chatTitle }

    /** Spojuje se nebo běží — jen tehdy má smysl pruh nad aplikací a služba v popředí. */
    val running: Boolean get() = state.phase == VideoChatPhase.Connecting || state.phase == VideoChatPhase.Live
}

/**
 * Videochat na úrovni aplikace, ne obrazovky: posluchač v něm zůstává, i když obrazovku
 * videochatu zavře a jde jinam (psát do skupiny), nebo aplikaci schová. Z hovoru odchází
 * jen [leave] — tlačítko Opustit na obrazovce, v pruhu nad aplikací a v notifikaci.
 *
 * - **Jeden videochat naráz.** [join] jiného hovoru ten předchozí opustí (`VideoChatWatch`
 *   sleduje jen jeden), téhož nic nedělá — obrazovka se k běžícímu jen připojí.
 * - **Skončený videochat zůstává ve stavu**, dokud ho uživatel nezavře nebo neotevře jiný:
 *   obrazovka pak řekne „skončil", pruh i služba podle [ActiveVideoChat.running] zmizí samy.
 * - **Pruh nad aplikací se schová, dokud je obrazovka videochatu vidět** ([screenShown]).
 * - **Jeden videochat i přes účty**: sezení má každý účet vlastní, přenos je ale v procesu
 *   jeden — [join] napřed opustí videochat v ostatních účtech ([leaveElsewhere], [VideoChats]).
 */
class VideoChatSession(
    private val repository: VideoChatRepository,
    media: LiveStreamMedia?,
    private val appScope: CoroutineScope,
    /** Opustí videochat v ostatních účtech; volá se před připojením k jinému. */
    private val leaveElsewhere: () -> Unit = {},
) {
    private val watch = media?.let { VideoChatWatch(repository, it, appScope, appScope) }

    /** Přenos tu je (Android); jinak se videochat připojit nedá. */
    val supported: Boolean get() = watch != null

    private data class Target(val chatId: Long, val groupCallId: Int, val chatTitle: String = "")

    private val target = MutableStateFlow<Target?>(null)

    val active: StateFlow<ActiveVideoChat?> =
        combine(target, watch?.state ?: flowOf(null)) { joined, state ->
            if (joined == null || state == null) null else ActiveVideoChat(joined.chatId, joined.groupCallId, joined.chatTitle, state)
        }.stateIn(appScope, SharingStarted.Eagerly, null)

    private val shownScreens = MutableStateFlow(0)

    /** Obrazovka videochatu je složená — pruh nad aplikací by ležel přes ni. */
    val screenShown: StateFlow<Boolean> =
        shownScreens.map { it > 0 }.stateIn(appScope, SharingStarted.Eagerly, false)

    fun screenVisible(visible: Boolean) = shownScreens.update { (it + if (visible) 1 else -1).coerceAtLeast(0) }

    fun join(chatId: Long, groupCallId: Int) {
        val watch = watch ?: return
        if (target.value?.groupCallId == groupCallId && watch.state.value != null) return
        leaveElsewhere()
        target.value = Target(chatId, groupCallId)
        watch.watch(groupCallId)
        appScope.launch {
            val title = repository.chatTitle(chatId) ?: return@launch
            target.update { it?.takeIf { joined -> joined.groupCallId == groupCallId }?.copy(chatTitle = title) }
        }
    }

    fun leave() {
        watch?.stop()
        target.value = null
    }

    suspend fun setMuted(muted: Boolean): Result<Unit> =
        watch?.setMuted(muted) ?: Result.failure(IllegalStateException("No video chat"))

    suspend fun setHandRaised(raised: Boolean): Result<Unit> =
        watch?.setHandRaised(raised) ?: Result.failure(IllegalStateException("No video chat"))
}
