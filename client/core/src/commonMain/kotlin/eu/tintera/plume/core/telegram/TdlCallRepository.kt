package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.dto.Call
import dev.g000sha256.tdl.dto.CallProtocol
import dev.g000sha256.tdl.dto.CallServerTypeTelegramReflector
import dev.g000sha256.tdl.dto.CallServerTypeWebrtc
import dev.g000sha256.tdl.dto.CallDiscardReason
import dev.g000sha256.tdl.dto.CallDiscardReasonDeclined
import dev.g000sha256.tdl.dto.CallDiscardReasonDisconnected
import dev.g000sha256.tdl.dto.CallDiscardReasonEmpty
import dev.g000sha256.tdl.dto.CallDiscardReasonHungUp
import dev.g000sha256.tdl.dto.CallDiscardReasonMissed
import dev.g000sha256.tdl.dto.CallDiscardReasonUpgradeToGroupCall
import dev.g000sha256.tdl.dto.CallStateDiscarded
import dev.g000sha256.tdl.dto.CallProblemDistortedSpeech
import dev.g000sha256.tdl.dto.CallProblemDistortedVideo
import dev.g000sha256.tdl.dto.CallProblemDropped
import dev.g000sha256.tdl.dto.CallProblemEcho
import dev.g000sha256.tdl.dto.CallProblemInterruptions
import dev.g000sha256.tdl.dto.CallProblemNoise
import dev.g000sha256.tdl.dto.CallProblemPixelatedVideo
import dev.g000sha256.tdl.dto.CallProblemSilentLocal
import dev.g000sha256.tdl.dto.CallProblemSilentRemote
import dev.g000sha256.tdl.dto.InputCallDiscarded
import dev.g000sha256.tdl.dto.InputFileLocal
import dev.g000sha256.tdl.dto.CallStateError
import dev.g000sha256.tdl.dto.CallStateExchangingKeys
import dev.g000sha256.tdl.dto.CallStateHangingUp
import dev.g000sha256.tdl.dto.CallStatePending
import dev.g000sha256.tdl.dto.CallStateReady
import dev.g000sha256.tdl.toResult
import eu.tintera.plume.core.calls.CallAudioOutput
import eu.tintera.plume.core.calls.CallJournal
import eu.tintera.plume.core.calls.CallStatsSampler
import eu.tintera.plume.core.calls.CallConnection
import eu.tintera.plume.core.calls.CallMedia
import eu.tintera.plume.core.calls.CallMediaEvents
import eu.tintera.plume.core.calls.CallMediaServer
import eu.tintera.plume.core.calls.CallMediaSession
import eu.tintera.plume.core.calls.CallPointerDisplay
import eu.tintera.plume.core.calls.CallSideChannel
import eu.tintera.plume.core.calls.CallSideMessage
import eu.tintera.plume.core.calls.sideChannelKey
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.call_bar_failed
import eu.tintera.plume.resources.call_busy_other_account
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import eu.tintera.plume.core.connectivity.NetworkKind
import eu.tintera.plume.core.connectivity.NetworkStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.TimeSource

/**
 * Hovory nad TDLib s přenosem přes [media]. Odebírá `updateCall` a signalizaci od protějšku;
 * po `CallStateReady` spustí přenos s klíčem a servery, které dal TDLib, a jeho signalizační
 * pakety posílá zpátky `sendCallSignalingData`.
 *
 * **Přijímá se jen s mediální vrstvou** — oficiální klient počítá délku hovoru od `Ready`
 * i bez přenosu, takže přijetí, které nic nespojí, by protějšku zapsalo uskutečněný hovor.
 */
class TdlCallRepository(
    private val holder: TelegramClientHolder,
    private val media: CallMedia?,
    private val scope: CoroutineScope,
    /** Stav sítě telefonu — kvůli „Čekám na připojení" při výpadku za hovoru. */
    network: Flow<NetworkStatus>? = null,
    /** Kde ukázat ukazovátko protistrany, když sdílíme obrazovku (Android); `null` = nikde. */
    private val pointerDisplay: CallPointerDisplay? = null,
    /** Deník hovorů pro diagnostiku (Nastavení → Pomoc); `null` = nezapisuje se. */
    private val journal: CallJournal? = null,
    /** Popředí aplikace — do deníku, jestli se hovor zadrhl se zhasnutou obrazovkou. */
    foreground: Flow<Boolean>? = null,
    /** Verze aplikace do ladicích dat pro Telegram. */
    private val applicationVersion: String = "",
    /**
     * Má hovor jiný účet v aplikaci? Hovor je přes všechny účty jeden, jako u telefonu: média
     * (mikrofon, přenos) jsou v procesu jedna a dva hovory by se o ně přetahovaly. Příchozí hovor
     * se pak odmítne (obsazeno nastavit nejde, viz [declineBusy]) a odchozí se nevytočí.
     */
    private val busyElsewhere: () -> Boolean = { false },
) : CallRepository {

    /** Telefon je bez sítě; nový hovor si stav převezme. */
    private var networkLost = false

    private val logger = Logger.withTag("Calls")

    private val _active = MutableStateFlow<ActiveCall?>(null)
    override val active: StateFlow<ActiveCall?> = _active.asStateFlow()

    private val _ratingRequest = MutableStateFlow<CallRatingRequest?>(null)
    override val ratingRequest: StateFlow<CallRatingRequest?> = _ratingRequest.asStateFlow()

    override val supported: Boolean get() = media != null

    override val supportsVideo: Boolean get() = media?.supportsVideo == true

    override val canSwitchCamera: Boolean get() = media?.canSwitchCamera == true

    override val previewsBeforeConnect: Boolean get() = media?.previewsBeforeConnect == true

    override val supportsScreenSharing: Boolean get() = media?.supportsScreenSharing == true

    /** Hovor, kterému už běží přenos — `Ready` chodí i opakovaně, spustit se má jednou. */
    private var startedCallId: Int? = null

    /** Signalizace protějšku, která dorazila dřív, než u nás běžel přenos (podle hovoru). */
    private val pendingSignaling = mutableMapOf<Int, MutableList<ByteArray>>()

    /** Signalizace a spuštění přenosu jdou z různých odběrů; paket nesmí uvíznout mezi nimi. */
    private val signalingLock = Mutex()

    init {
        network?.map { it.kind == NetworkKind.None }?.distinctUntilChanged()?.onEach { lost ->
            networkLost = lost
            if (lost) logger.i { "Síť za hovoru chybí." }
            _active.update { it?.copy(networkLost = lost) }
        }?.launchIn(scope)
        // Do deníku jen za hovoru — mimo něj by změny sítě a popředí jen zabíraly místo.
        network?.distinctUntilChanged()?.onEach { status ->
            if (inCall()) note("Network: ${status.kind} (#${status.generation})")
        }?.launchIn(scope)
        foreground?.distinctUntilChanged()?.onEach { visible ->
            if (inCall()) note(if (visible) "App in foreground" else "App in background")
        }?.launchIn(scope)
        holder.updates { callUpdates }
            .onEach { onCall(it.call) }
            .launchIn(scope)
        holder.updates { newCallSignalingDataUpdates }
            .onEach { update ->
                signalingLock.withLock { onSignaling(update.callId, update.data) }
            }
            .launchIn(scope)
    }

    /** Pod [signalingLock]. */
    private fun onSignaling(callId: Int, data: ByteArray) {
        // Naše zprávy Plume ↔ Plume (ukazovátko) do knihovny nepatří — vyloví se tady.
        if (CallSideChannel.isOurs(data)) {
            onSideMessage(callId, data)
            return
        }
        if (callId == startedCallId) {
            media?.receiveSignaling(data)
            return
        }
        // Přenos u nás ještě neběží (náš `Ready` se může zpracovat až po první signalizaci
        // protějšku). Zahodit ji nejde: nese `InitialSetup` a `NegotiateChannels`, bez kterých
        // se kanály nedohodnou. Vlastní fronta ntgcalls pomáhá až od chvíle, kdy přenos
        // existuje; tahle drží, co přišlo před ním.
        val queue = pendingSignaling.getOrPut(callId) { mutableListOf() }
        queue += data
        logger.i { "Signalizace hovoru $callId před spuštěním přenosu, čeká (${queue.size})." }
    }

    /** Klíč postranního kanálu běžícího hovoru (z klíče hovoru), `null` = přenos neběží. */
    private var sideKey: ByteArray? = null
    private var sideCallId: Int? = null

    private fun onSideMessage(callId: Int, data: ByteArray) {
        val key = sideKey?.takeIf { sideCallId == callId } ?: return
        val message = CallSideChannel.open(key, data) ?: run {
            logger.w { "Zprávu Plume v signalizaci nejde přečíst." }
            return
        }
        when (message) {
            is CallSideMessage.Hello -> {
                logger.i { "Protějšek je Plume, ukazovátko: ${message.pointer}." }
                _active.update { it?.copy(remotePointer = message.pointer) }
            }
            // Ukazovátko patří na obrazovku, kterou právě sdílíme; jindy nemá kde být.
            is CallSideMessage.Pointer -> if (_active.value?.screenSharing == true) {
                pointerDisplay?.show(message.x.coerceIn(0f, 1f), message.y.coerceIn(0f, 1f), message.down)
            }
        }
    }

    private fun sendSide(message: CallSideMessage) {
        val key = sideKey ?: return
        val callId = sideCallId ?: return
        scope.launch {
            holder.client.sendCallSignalingData(callId = callId, data = CallSideChannel.seal(key, message)).toResult()
                .onFailure { logger.w(it) { "Zprávu Plume nejde poslat." } }
        }
    }

    /** Mimo hlavní vlákno: první dotaz nahrává knihovnu přenosu (desítky MB). */
    private suspend fun protocol(): CallProtocol? = withContext(Dispatchers.Default) { protocolNow() }

    private fun protocolNow(): CallProtocol? {
        val supported = media?.protocol ?: return null
        return CallProtocol(
            udpP2p = true,
            udpReflector = true,
            minLayer = supported.minLayer,
            maxLayer = supported.maxLayer,
            libraryVersions = supported.versions.toTypedArray(),
        )
    }

    override suspend fun call(userId: Long, video: Boolean): Result<Unit> {
        // Desktop umí jen zvuk: videohovor se tam vytočí jako hlasový.
        val withVideo = video && supportsVideo
        if (busyElsewhere()) {
            note("Not dialing: a call is running in another account", callId = null)
            showFailed(userId, withVideo, UiText.Resource(Res.string.call_busy_other_account))
            return Result.failure(IllegalStateException("A call is running in another account."))
        }
        val protocol = protocol() ?: return Result.failure(UnsupportedOperationException("Hovory tu nejsou."))
        logger.i { "Protokol: ${protocol.libraryVersions.joinToString()} vrstvy ${protocol.minLayer}..${protocol.maxLayer}" }
        // Zvuk napřed: tón protistrany a reproduktor fungují už při vytáčení.
        startAudio()
        // Hovor (`Pending`) přijde až po `createCall`; kamera se mu předá při založení.
        outgoingCamera = withVideo
        if (withVideo) {
            media?.setCamera(on = true, front = true)
            // Videohovor se drží v ruce před sebou, ne u ucha.
            media?.setOutput(CallAudioOutput.Speaker)
        }
        note("Dialing${if (withVideo) " (video)" else ""}", callId = null)
        return holder.client.createCall(userId = userId, protocol = protocol, isVideo = withVideo).toResult().map { }
            .onFailure { failure ->
                logger.w(failure) { "Hovor se nepodařilo zahájit." }
                note("createCall failed: ${failure.message}", callId = null)
                outgoingCamera = false
                media?.stop()
                showFailed(userId, withVideo, failure.userMessage(Res.string.call_bar_failed))
            }
    }

    /** Kamera pro odchozí hovor, který ještě nemá stav (čeká se na `Pending`). */
    private var outgoingCamera = false

    /** Výstupy zvuku z poslední zvukové relace — hovor (`Pending`) může přijít až po nich. */
    private var lastOutputs: Pair<List<CallAudioOutput>, CallAudioOutput>? = null

    private fun startAudio() {
        lastOutputs = null
        media?.startAudio { available, current ->
            // Jen za hovoru: po zavěšení se zvuk vrací a řádek za koncem hovoru by byl šum.
            if (lastOutputs?.second?.kind != current.kind && inCall()) note("Audio output: ${current.kind}")
            lastOutputs = available to current
            _active.update { it?.copy(outputs = available, output = current) }
        }
    }

    override suspend fun accept(camera: Boolean): Result<Unit> {
        val call = _active.value?.takeIf { it.phase == CallPhase.Ringing }
            ?: return Result.failure(IllegalStateException("Žádný hovor nezvoní."))
        val protocol = protocol() ?: return Result.failure(UnsupportedOperationException("Hovory tu nejsou."))
        startAudio()
        _active.update { it?.copy(failure = null, preview = false) }
        if (camera && supportsVideo) {
            setCamera(true)
        } else if (call.preview) {
            // Přijato bez kamery (oprávnění odepřené): náhled ze zvonění pryč.
            media?.setCamera(on = false, front = call.frontCamera)
        }
        note("Accepted${if (camera && supportsVideo) " with camera" else ""}", call.id)
        return holder.client.acceptCall(callId = call.id, protocol = protocol).toResult().map { }
            .onFailure { failure ->
                logger.w(failure) { "Hovor se nepodařilo přijmout." }
                note("acceptCall failed: ${failure.message}", call.id)
                media?.stop()
                _active.update { active ->
                    active?.takeIf { it.id == call.id }?.copy(phase = CallPhase.Failed, failure = failure.userMessage(Res.string.call_bar_failed)) ?: active
                }
                finish(call.id, CallPhase.Failed)
            }
    }

    override suspend fun hangUp() = hangUp(disconnected = false)

    /** Příchozí hovory odmítnuté kvůli hovoru v jiném účtu; jejich další stavy se zahodí. */
    private val busyDeclined = mutableSetOf<Int>()

    /**
     * Odmítne příchozí hovor kvůli hovoru v jiném účtu. Volající ho uvidí jako odmítnutý, ne
     * obsazený: `discardCall` důvod nastavit nedovolí a nepřijatý příchozí hovor TDLib odešle jako
     * odmítnutý (ověřeno na zařízení, `Discarded: declined`). **Bez `media.stop()`**, na rozdíl od [hangUp]: média
     * jsou v procesu jedna a běží v nich hovor jiného účtu, který by se tím přerušil. Odmítnutý
     * hovor média nikdy nespustil, takže zastavovat není co.
     */
    private suspend fun declineBusy(call: Call) {
        note("Declined as busy: a call is running in another account", call.id)
        holder.client.discardCall(
            callId = call.id,
            isDisconnected = false,
            inviteLink = "",
            duration = 0,
            isVideo = call.isVideo,
            connectionId = 0L,
        ).toResult().onFailure { logger.w(it) { "Hovor ${call.id} se nepodařilo odmítnout." } }
    }

    /**
     * [disconnected] = hovor ukončil výpadek přenosu, ne člověk. TDLib to pošle serveru
     * (`isDisconnected`) a protějšek se to dozví jako přerušený hovor, ne jako zavěšení.
     */
    private suspend fun hangUp(disconnected: Boolean) {
        val call = _active.value ?: return
        val connectedAt = call.connectedAt
        val duration = connectedAt?.let { ((Clock.System.now().toEpochMilliseconds() - it) / 1000).toInt() } ?: 0
        note(if (disconnected) "Ending: transport failed (duration $duration s)" else "Hung up (duration $duration s)", call.id)
        media?.stop()
        holder.client.discardCall(
            callId = call.id,
            isDisconnected = disconnected,
            inviteLink = "",
            duration = duration,
            isVideo = call.isVideo || call.camera,
            connectionId = 0L,
        ).toResult().onFailure { logger.w(it) { "Hovor se nepodařilo ukončit." } }
    }

    override fun setOutput(output: CallAudioOutput) {
        media?.setOutput(output)
        _active.update { it?.copy(output = output) }
    }

    private var resumeHandler: (suspend () -> Boolean)? = null

    override fun setResumeHandler(handler: (suspend () -> Boolean)?) {
        resumeHandler = handler
    }

    override suspend fun resume() {
        val handler = resumeHandler
        if (handler == null || handler()) setHeld(false)
    }

    override fun setHeld(held: Boolean) {
        note(if (held) "Held by the system" else "Resumed")
        media?.setHeld(held)
        _active.update { it?.copy(held = held) }
    }

    override fun setCamera(on: Boolean) {
        if (on && !supportsVideo) return
        val call = _active.value ?: return
        // Kamera a obrazovka se v hovoru ve dvou nemíchají: kamera sdílení ukončí.
        if (on && call.screenSharing) setScreenSharing(false)
        media?.setCamera(on = on, front = call.frontCamera)
        if (on != call.camera) note(if (on) "Camera on" else "Camera off")
        _active.update { it?.copy(camera = on) }
        // Kdo zapne kameru, drží telefon před sebou — ze sluchátka u ucha by nic neslyšel.
        if (on && call.output.kind == CallAudioOutput.Kind.Earpiece) setOutput(CallAudioOutput.Speaker)
    }

    override fun setScreenSharing(on: Boolean) {
        if (on && !supportsScreenSharing) return
        val call = _active.value?.takeIf { it.screenSharing != on } ?: return
        if (on && call.camera) {
            media?.setCamera(on = false, front = call.frontCamera)
            _active.update { it?.copy(camera = false) }
        }
        // Napřed stav (služba podle něj přidá typ `mediaProjection`), pak knihovna — ta na
        // službu počká, Android 14 bez ní snímání obrazovky nepustí.
        _active.update { it?.copy(screenSharing = on) }
        media?.setScreenSharing(on)
        // Protějšek Plume se dozví, jestli mu na sdílené obrazovce ukážeme ukazovátko.
        // Oficiální klient zprávu nerozšifruje a zahodí.
        if (on) {
            val pointer = pointerDisplay?.available == true
            logger.i { "Sdílím obrazovku, ukazovátko protistrany: $pointer (zobrazení ${if (pointerDisplay == null) "není" else "je"})." }
            sendSide(CallSideMessage.Hello(pointer = pointer))
        } else {
            pointerDisplay?.hide()
        }
        if (on && call.output.kind == CallAudioOutput.Kind.Earpiece) setOutput(CallAudioOutput.Speaker)
    }

    override fun sendPointer(x: Float, y: Float, down: Boolean) {
        if (_active.value?.remotePointer != true) return
        sendSide(CallSideMessage.Pointer(x, y, down))
    }

    override fun previewCamera(on: Boolean) {
        if (on && (!supportsVideo || !previewsBeforeConnect)) return
        val call = _active.value?.takeIf { it.phase == CallPhase.Ringing && it.preview != on } ?: return
        media?.setCamera(on = on, front = call.frontCamera)
        _active.update { it?.copy(preview = on) }
    }

    override fun switchCamera() {
        val call = _active.value ?: return
        val front = !call.frontCamera
        if (call.camera) media?.setCamera(on = true, front = front)
        _active.update { it?.copy(frontCamera = front) }
    }

    override fun reportFailure(userId: Long, video: Boolean, reason: UiText) {
        val current = _active.value
        if (current != null && current.userId == userId && current.phase !in ENDED) {
            _active.value = current.copy(failure = reason)
            return
        }
        showFailed(userId, video, reason)
    }

    /**
     * Neúspěšný hovor, který nikdy nezačal (`createCall` selhal, chybí oprávnění): krátce se
     * ukáže s důvodem, ať vytočení beze slova nezmizí. Probíhající hovor nepřepisuje.
     */
    private fun showFailed(userId: Long, video: Boolean, reason: UiText) {
        val current = _active.value
        if (current != null && current.phase !in ENDED) return
        _active.value = ActiveCall(
            id = FAILED_BEFORE_START_ID,
            userId = userId,
            isOutgoing = true,
            phase = CallPhase.Failed,
            isVideo = video,
            failure = reason,
        )
        finish(FAILED_BEFORE_START_ID, CallPhase.Failed)
    }

    override fun setMuted(muted: Boolean) {
        note(if (muted) "Muted" else "Unmuted")
        media?.setMuted(muted)
        _active.update { it?.copy(muted = muted) }
    }

    private suspend fun onCall(call: Call) {
        val current = _active.value
        // Druhý hovor během hovoru neřešíme — server ho druhé straně ohlásí jako obsazeno.
        if (current != null && current.id != call.id && current.phase !in ENDED) return
        // Hovor v jiném účtu: server o něm neví, obsazeno neohlásí, takže se příchozí odmítne.
        // Hovor se vůbec neukáže — nezazvoní, nevznikne notifikace ani systémový hovor.
        if (call.id in busyDeclined) return
        if (current?.id != call.id && !call.isOutgoing && call.state is CallStatePending && busyElsewhere()) {
            busyDeclined += call.id
            declineBusy(call)
            return
        }
        noteState(call, current)
        when (val state = call.state) {
            // `Pending` chodí víckrát (vytvořen, doručen) — stav téhož hovoru se nezakládá znovu,
            // jen se z něj převezme, jestli už hovor došel protějšku (`isReceived` = tam zvoní).
            is CallStatePending -> _active.value = current?.takeIf { it.id == call.id }?.copy(received = state.isReceived)
                ?: ActiveCall(
                    id = call.id,
                    userId = call.userId,
                    isOutgoing = call.isOutgoing,
                    phase = if (call.isOutgoing) CallPhase.Calling else CallPhase.Ringing,
                    received = state.isReceived,
                    networkLost = networkLost,
                    isVideo = call.isVideo,
                    camera = call.isOutgoing && outgoingCamera,
                ).let { new ->
                    lastOutputs?.takeIf { call.isOutgoing }?.let { (available, output) -> new.copy(outputs = available, output = output) } ?: new
                }
            is CallStateExchangingKeys -> _active.update { it?.copy(phase = CallPhase.Connecting) }
            is CallStateReady -> {
                _active.update { it?.copy(phase = it.phase.takeIf { p -> p == CallPhase.Active } ?: CallPhase.Connecting, emojis = state.emojis.toList()) }
                signalingLock.withLock {
                    if (startedCallId != call.id) {
                        startedCallId = call.id
                        startMedia(call, state)
                    }
                }
            }
            is CallStateHangingUp -> Unit
            is CallStateDiscarded -> {
                // TDLib pošle skončený hovor i znovu (po hovoru s `needDebugInformation`, 6 minut
                // poté) — konec se vyřizuje jednou, o diagnostiku server žádá třeba až teď.
                if (!endedCalls.add(call.id)) {
                    if (state.needDebugInformation || state.needLog) sendDiagnostics(call.id, debug = state.needDebugInformation, log = state.needLog)
                    return
                }
                // Server hodnocení chce jen někdy; ptáme se jen po hovoru, ve kterém se mluvilo —
                // zrušený nebo nepřijatý hovor nemá co hodnotit.
                val spoke = _active.value?.takeIf { it.id == call.id }?.connectedAt != null
                if (state.needRating && spoke) {
                    _ratingRequest.value = CallRatingRequest(callId = call.id, userId = call.userId, isVideo = call.isVideo)
                }
                finish(call.id, CallPhase.Ended)
                if (state.needDebugInformation || state.needLog) {
                    sendDiagnostics(call.id, debug = state.needDebugInformation, log = state.needLog)
                }
            }
            is CallStateError -> {
                if (!endedCalls.add(call.id)) return
                logger.w { "Hovor skončil chybou: ${state.error.code} ${state.error.message}" }
                val reason = errorText(state.error.message, Res.string.call_bar_failed)
                val known = _active.value?.takeIf { it.id == call.id }
                if (known != null) {
                    _active.value = known.copy(phase = CallPhase.Failed, failure = reason)
                } else if (call.isOutgoing) {
                    // Chyba hned po `createCall` (soukromí, omezení počtu hovorů) chodí dřív než
                    // `Pending` — bez tohohle by se obrazovka hovoru vůbec neotevřela a vytočení
                    // by beze slova nic neudělalo.
                    _active.value = ActiveCall(
                        id = call.id,
                        userId = call.userId,
                        isOutgoing = true,
                        phase = CallPhase.Failed,
                        isVideo = call.isVideo,
                        failure = reason,
                    )
                }
                finish(call.id, CallPhase.Failed)
            }
            else -> Unit
        }
    }

    private fun startMedia(call: Call, state: CallStateReady) {
        val media = media ?: return
        logger.i { "Přenos: verze ${state.protocol.libraryVersions.joinToString()}, serverů ${state.servers.size}" }
        note(
            "Ready: version ${state.protocol.libraryVersions.joinToString()}, " +
                "${state.servers.count { it.type is CallServerTypeTelegramReflector }} reflector + " +
                "${state.servers.count { it.type is CallServerTypeWebrtc }} WebRTC servers, p2p ${state.allowP2p}",
            call.id,
        )
        startSampling(call.id)
        sideKey = sideChannelKey(state.encryptionKey)
        sideCallId = call.id
        media.start(
            CallMediaSession(
                callId = call.id,
                isOutgoing = call.isOutgoing,
                encryptionKey = state.encryptionKey,
                versions = state.protocol.libraryVersions.toList(),
                servers = state.servers.mapNotNull { server ->
                    when (val type = server.type) {
                        is CallServerTypeTelegramReflector -> CallMediaServer(
                            id = server.id, ipv4 = server.ipAddress, ipv6 = server.ipv6Address, port = server.port,
                            username = "", password = "", turn = false, stun = false, tcp = type.isTcp,
                            peerTag = type.peerTag,
                        )
                        is CallServerTypeWebrtc -> CallMediaServer(
                            id = server.id, ipv4 = server.ipAddress, ipv6 = server.ipv6Address, port = server.port,
                            username = type.username, password = type.password, turn = type.supportsTurn,
                            stun = type.supportsStun, tcp = false, peerTag = null,
                        )
                        else -> null
                    }
                },
                allowP2p = state.allowP2p,
                // ntgcalls je parsuje jako JSON a prázdný řetězec (TDLib ho tak posílá, když server
                // nic nepřidá) nativní kód shodí — výjimka z boost::json shodila celý proces.
                customParameters = state.customParameters.ifBlank { "{}" },
            ),
            object : CallMediaEvents {
                override fun onSignaling(data: ByteArray) {
                    scope.launch {
                        holder.client.sendCallSignalingData(callId = call.id, data = data).toResult()
                            .onFailure { logger.w(it) { "Signalizaci se nepodařilo odeslat." } }
                    }
                }

                override fun onConnection(state: CallConnection) {
                    note("Transport: $state", call.id)
                    when (state) {
                        CallConnection.Connected -> {
                            _active.update { active ->
                                active?.takeIf { it.id == call.id }?.copy(
                                    phase = CallPhase.Active,
                                    connectedAt = active.connectedAt ?: Clock.System.now().toEpochMilliseconds(),
                                ) ?: active
                            }
                        }
                        // Přenos se nespojil, nebo se po výpadku sítě do 20 s neobnovil (ntgcalls
                        // výpadek do té doby jen přečkává a nic nehlásí).
                        CallConnection.Failed -> scope.launch {
                            logger.w { "Přenos hovoru selhal, ukončuji." }
                            _active.update { active -> active?.takeIf { it.id == call.id }?.copy(phase = CallPhase.Failed) ?: active }
                            hangUp(disconnected = true)
                        }
                        else -> Unit
                    }
                }

                override fun onRemoteVideo(active: Boolean) {
                    _active.update { current -> current?.takeIf { it.id == call.id }?.copy(remoteVideo = active) ?: current }
                }

                override fun onScreenSharingStopped() {
                    _active.update { current -> current?.takeIf { it.id == call.id }?.copy(screenSharing = false) ?: current }
                    pointerDisplay?.hide()
                }

                override fun onRemoteScreen(active: Boolean) {
                    _active.update { current ->
                        // Konec sdílení u protějšku: ukazovátko jeho další sdílení ohlásí znovu.
                        current?.takeIf { it.id == call.id }?.copy(remoteScreen = active, remotePointer = current.remotePointer && active) ?: current
                    }
                }
            },
        )
        // Co protějšek poslal dřív, než se přenos spustil — v původním pořadí.
        pendingSignaling.remove(call.id)?.let { early ->
            logger.i { "Předávám ${early.size} dřívějších signalizačních paketů." }
            early.forEach(media::receiveSignaling)
        }
    }

    private fun finish(callId: Int, phase: CallPhase) {
        if (sampledCallId == callId) {
            sampling?.cancel()
            sampling = null
            sampledCallId = null
        }
        note("Over: ${if (_active.value?.takeIf { it.id == callId }?.phase == CallPhase.Failed) CallPhase.Failed else phase}", callId)
        if (sideCallId == callId) {
            sideKey = null
            sideCallId = null
            pointerDisplay?.hide()
        }
        // Zastavit i hovor, který se nespojil: zvuková relace běží od vytáčení / přijetí.
        pendingSignaling.remove(callId)
        outgoingCamera = false
        if (startedCallId == callId || _active.value?.id == callId) media?.stop()
        if (startedCallId == callId) startedCallId = null
        _active.update { active -> active?.takeIf { it.id == callId }?.copy(phase = if (active.phase == CallPhase.Failed) CallPhase.Failed else phase) ?: active }
        // Konec chvíli ukázat, ať se hovor jen beze slova neztratí; důvod selhání déle — je to věta.
        val visible = if (_active.value?.takeIf { it.id == callId }?.failure != null) FAILURE_VISIBLE_MS else ENDED_VISIBLE_MS
        scope.launch {
            delay(visible)
            _active.update { active -> active?.takeIf { it.id != callId || it.phase !in ENDED } }
        }
    }

    override suspend fun rate(request: CallRatingRequest, stars: Int, comment: String, problems: Set<CallProblem>): Result<Unit> {
        _ratingRequest.compareAndSet(request, null)
        note("Rated: $stars stars, ${problems.size} problems", request.callId)
        return holder.client.sendCallRating(
            callId = InputCallDiscarded(callId = request.callId),
            rating = stars.coerceIn(1, 5),
            // Komentář a problémy jen u horšího než plného hodnocení — tak je TDLib bere.
            comment = if (stars < 5) comment.trim() else "",
            problems = if (stars < 5) problems.map { it.toTdl() }.toTypedArray() else emptyArray(),
        ).toResult().map { }
            .onFailure { logger.w(it) { "Hodnocení hovoru ${request.callId} se nepodařilo odeslat." } }
    }

    /**
     * Ladicí data a log, o které server po hovoru požádal (`needDebugInformation`, `needLog`).
     * Jen technické údaje: řádky deníku k tomuhle hovoru (stavy, verze, servery, přenos, síť —
     * žádná jména, čísla ani obsah, viz [CallJournal]) a podrobný výpis přenosu se zamaskovanými
     * adresami (`CallDetailLog`). Posílá se chvilku po konci, až výpis doběhne a zavře se.
     */
    private fun sendDiagnostics(callId: Int, debug: Boolean, log: Boolean) {
        val journal = journal ?: return
        scope.launch {
            delay(DIAGNOSTICS_SETTLE_MS)
            if (debug) {
                val info = callDebugInformation(applicationVersion, journal.lastCallLines(callId))
                holder.client.sendCallDebugInformation(callId = InputCallDiscarded(callId = callId), debugInformation = info).toResult()
                    .onSuccess { note("Sent debug information to Telegram", callId) }
                    .onFailure { logger.w(it) { "Ladicí data hovoru $callId se nepodařilo odeslat." } }
            }
            if (log) {
                val path = journal.detailLog(callId)
                if (path == null) {
                    note("No detail log to send to Telegram", callId)
                } else {
                    holder.client.sendCallLog(callId = InputCallDiscarded(callId = callId), logFile = InputFileLocal(path = path)).toResult()
                        .onSuccess { note("Sent call log to Telegram", callId) }
                        .onFailure { logger.w(it) { "Log hovoru $callId se nepodařilo odeslat." } }
                }
            }
        }
    }

    override fun dismissRating() {
        _ratingRequest.value = null
    }

    private fun note(text: String, callId: Int? = _active.value?.id) {
        journal?.record(text, callId)
    }

    private fun inCall(): Boolean = _active.value?.let { it.phase !in ENDED } == true

    /** Přechod stavu z `UpdateCall` do deníku — jen změny, `Pending` a `Ready` chodí opakovaně. */
    private fun noteState(call: Call, current: ActiveCall?) {
        val known = current?.takeIf { it.id == call.id }
        val text = when (val state = call.state) {
            is CallStatePending -> when {
                known == null -> "${if (call.isOutgoing) "Outgoing" else "Incoming"} call${if (call.isVideo) " (video)" else ""}"
                state.isReceived && !known.received -> "Ringing on the other side"
                else -> null
            }
            is CallStateExchangingKeys -> "Exchanging keys"
            is CallStateHangingUp -> "Hanging up"
            // Jméno třídy ne: v buildu z Play ho R8 přejmenuje („Discarded: ue1").
            is CallStateDiscarded -> if (call.id in endedCalls) null else "Discarded: ${state.reason.journalName()}"
            is CallStateError -> "Error ${state.error.code}: ${state.error.message}"
            else -> null
        } ?: return
        note(text, call.id)
    }

    /** Hovory, jejichž konec už je vyřízený — TDLib je umí poslat znovu. */
    private val endedCalls = mutableSetOf<Int>()

    /** Vzorkování přenosu do deníku, jednou za vteřinu, dokud hovor neskončí ([finish]). */
    private var sampling: Job? = null
    private var sampledCallId: Int? = null

    private fun startSampling(callId: Int) {
        val media = media ?: return
        val journal = journal ?: return
        sampling?.cancel()
        sampledCallId = callId
        sampling = scope.launch(Dispatchers.Default) {
            val sampler = CallStatsSampler { journal.record(it, callId) }
            val started = TimeSource.Monotonic.markNow()
            while (isActive) {
                media.stats()?.let { sampler.sample(it, started.elapsedNow().inWholeMilliseconds) }
                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }

    private companion object {
        val ENDED = setOf(CallPhase.Ended, CallPhase.Failed)

        const val SAMPLE_INTERVAL_MS = 1_000L
        const val ENDED_VISIBLE_MS = 2_000L
        const val FAILURE_VISIBLE_MS = 5_000L

        /** Id hovoru, který nikdy nezačal (TDLib čísluje hovory od 1). */
        const val FAILED_BEFORE_START_ID = 0
    }
}

private fun CallProblem.toTdl(): dev.g000sha256.tdl.dto.CallProblem = when (this) {
    CallProblem.Echo -> CallProblemEcho()
    CallProblem.Noise -> CallProblemNoise()
    CallProblem.Interruptions -> CallProblemInterruptions()
    CallProblem.DistortedSpeech -> CallProblemDistortedSpeech()
    CallProblem.SilentLocal -> CallProblemSilentLocal()
    CallProblem.SilentRemote -> CallProblemSilentRemote()
    CallProblem.Dropped -> CallProblemDropped()
    CallProblem.DistortedVideo -> CallProblemDistortedVideo()
    CallProblem.PixelatedVideo -> CallProblemPixelatedVideo()
}

/** Jak dlouho po konci hovoru počkat s ladicími daty: výpis přenosu se zavírá až se zastavením médií. */
private const val DIAGNOSTICS_SETTLE_MS = 3_000L

/**
 * Ladicí data hovoru pro Telegram (`sendCallDebugInformation`, formát je na aplikaci): JSON
 * s klientem, verzí, telefonem, systémem a řádky deníku k hovoru.
 */
internal fun callDebugInformation(applicationVersion: String, journal: List<String>): String =
    buildJsonObject {
        put("client", JsonPrimitive("Plume"))
        put("version", JsonPrimitive(applicationVersion))
        put("device", JsonPrimitive(platformDeviceModel))
        put("system", JsonPrimitive(platformSystemVersion))
        put("journal", JsonArray(journal.map { JsonPrimitive(it) }))
    }.toString()

/** Důvod konce do deníku; `when` bez `else`, ať nový důvod v TDLib rozbije překlad, ne deník. */
private fun CallDiscardReason.journalName(): String = when (this) {
    is CallDiscardReasonHungUp -> "hung up"
    is CallDiscardReasonDeclined -> "declined"
    is CallDiscardReasonMissed -> "missed"
    is CallDiscardReasonDisconnected -> "disconnected"
    is CallDiscardReasonEmpty -> "no reason (accepted on another device, or cancelled before connecting)"
    is CallDiscardReasonUpgradeToGroupCall -> "upgraded to a group call"
}
