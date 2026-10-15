package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.dto.Call
import dev.g000sha256.tdl.dto.CallProtocol
import dev.g000sha256.tdl.dto.CallServerTypeTelegramReflector
import dev.g000sha256.tdl.dto.CallServerTypeWebrtc
import dev.g000sha256.tdl.dto.CallStateDiscarded
import dev.g000sha256.tdl.dto.CallStateError
import dev.g000sha256.tdl.dto.CallStateExchangingKeys
import dev.g000sha256.tdl.dto.CallStateHangingUp
import dev.g000sha256.tdl.dto.CallStatePending
import dev.g000sha256.tdl.dto.CallStateReady
import dev.g000sha256.tdl.toResult
import eu.tintera.plume.core.calls.CallAudioOutput
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
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

/**
 * Hovory nad TDLib s přenosem přes [media]. Odebírá `updateCall` a signalizaci od protějšku;
 * po `CallStateReady` spustí přenos s klíčem a servery, které dal TDLib, a jeho signalizační
 * pakety posílá zpátky `sendCallSignalingData`.
 *
 * **Přijímá se jen s mediální vrstvou** — oficiální klient počítá délku hovoru od `Ready`
 * i bez přenosu, takže přijetí, které nic nespojí, by protějšku zapsalo uskutečněný hovor.
 */
internal class TdlCallRepository(
    private val holder: TelegramClientHolder,
    private val media: CallMedia?,
    private val scope: CoroutineScope,
    /** Stav sítě telefonu — kvůli „Čekám na připojení" při výpadku za hovoru. */
    network: Flow<NetworkStatus>? = null,
    /** Kde ukázat ukazovátko protistrany, když sdílíme obrazovku (Android); `null` = nikde. */
    private val pointerDisplay: CallPointerDisplay? = null,
) : CallRepository {

    /** Telefon je bez sítě; nový hovor si stav převezme. */
    private var networkLost = false

    private val logger = Logger.withTag("Calls")

    private val _active = MutableStateFlow<ActiveCall?>(null)
    override val active: StateFlow<ActiveCall?> = _active.asStateFlow()

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
        return holder.client.createCall(userId = userId, protocol = protocol, isVideo = withVideo).toResult().map { }
            .onFailure { failure ->
                logger.w(failure) { "Hovor se nepodařilo zahájit." }
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
        return holder.client.acceptCall(callId = call.id, protocol = protocol).toResult().map { }
            .onFailure { failure ->
                logger.w(failure) { "Hovor se nepodařilo přijmout." }
                media?.stop()
                _active.update { active ->
                    active?.takeIf { it.id == call.id }?.copy(phase = CallPhase.Failed, failure = failure.userMessage(Res.string.call_bar_failed)) ?: active
                }
                finish(call.id, CallPhase.Failed)
            }
    }

    override suspend fun hangUp() = hangUp(disconnected = false)

    /**
     * [disconnected] = hovor ukončil výpadek přenosu, ne člověk. TDLib to pošle serveru
     * (`isDisconnected`) a protějšek se to dozví jako přerušený hovor, ne jako zavěšení.
     */
    private suspend fun hangUp(disconnected: Boolean) {
        val call = _active.value ?: return
        val connectedAt = call.connectedAt
        val duration = connectedAt?.let { ((Clock.System.now().toEpochMilliseconds() - it) / 1000).toInt() } ?: 0
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
        media?.setHeld(held)
        _active.update { it?.copy(held = held) }
    }

    override fun setCamera(on: Boolean) {
        if (on && !supportsVideo) return
        val call = _active.value ?: return
        // Kamera a obrazovka se v hovoru ve dvou nemíchají: kamera sdílení ukončí.
        if (on && call.screenSharing) setScreenSharing(false)
        media?.setCamera(on = on, front = call.frontCamera)
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
        media?.setMuted(muted)
        _active.update { it?.copy(muted = muted) }
    }

    private suspend fun onCall(call: Call) {
        val current = _active.value
        // Druhý hovor během hovoru neřešíme — server ho druhé straně ohlásí jako obsazeno.
        if (current != null && current.id != call.id && current.phase !in ENDED) return
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
            is CallStateDiscarded -> finish(call.id, CallPhase.Ended)
            is CallStateError -> {
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

    private companion object {
        val ENDED = setOf(CallPhase.Ended, CallPhase.Failed)
        const val ENDED_VISIBLE_MS = 2_000L
        const val FAILURE_VISIBLE_MS = 5_000L

        /** Id hovoru, který nikdy nezačal (TDLib čísluje hovory od 1). */
        const val FAILED_BEFORE_START_ID = 0
    }
}
