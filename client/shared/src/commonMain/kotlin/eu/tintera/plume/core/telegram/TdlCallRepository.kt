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
) : CallRepository {

    /** Telefon je bez sítě; nový hovor si stav převezme. */
    private var networkLost = false

    private val logger = Logger.withTag("Calls")

    private val _active = MutableStateFlow<ActiveCall?>(null)
    override val active: StateFlow<ActiveCall?> = _active.asStateFlow()

    override val supported: Boolean get() = media != null

    /** Hovor, kterému už běží přenos — `Ready` chodí i opakovaně, spustit se má jednou. */
    private var startedCallId: Int? = null

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
                if (update.callId == startedCallId) media?.receiveSignaling(update.data)
            }
            .launchIn(scope)
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

    override suspend fun call(userId: Long): Result<Unit> {
        val protocol = protocol() ?: return Result.failure(UnsupportedOperationException("Hovory tu nejsou."))
        logger.i { "Protokol: ${protocol.libraryVersions.joinToString()} vrstvy ${protocol.minLayer}..${protocol.maxLayer}" }
        // Zvuk napřed: tón protistrany a reproduktor fungují už při vytáčení.
        startAudio()
        return holder.client.createCall(userId = userId, protocol = protocol, isVideo = false).toResult().map { }
            .onFailure { media?.stop() }
    }

    /** Výstupy zvuku z poslední zvukové relace — hovor (`Pending`) může přijít až po nich. */
    private var lastOutputs: Pair<List<CallAudioOutput>, CallAudioOutput>? = null

    private fun startAudio() {
        lastOutputs = null
        media?.startAudio { available, current ->
            lastOutputs = available to current
            _active.update { it?.copy(outputs = available, output = current) }
        }
    }

    override suspend fun accept(): Result<Unit> {
        val call = _active.value?.takeIf { it.phase == CallPhase.Ringing }
            ?: return Result.failure(IllegalStateException("Žádný hovor nezvoní."))
        val protocol = protocol() ?: return Result.failure(UnsupportedOperationException("Hovory tu nejsou."))
        startAudio()
        return holder.client.acceptCall(callId = call.id, protocol = protocol).toResult().map { }
            .onFailure { media?.stop() }
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
            isVideo = false,
            connectionId = 0L,
        ).toResult().onFailure { logger.w(it) { "Hovor se nepodařilo ukončit." } }
    }

    override fun setOutput(output: CallAudioOutput) {
        media?.setOutput(output)
        _active.update { it?.copy(output = output) }
    }

    override fun setMuted(muted: Boolean) {
        media?.setMuted(muted)
        _active.update { it?.copy(muted = muted) }
    }

    private fun onCall(call: Call) {
        val current = _active.value
        // Druhý hovor během hovoru neřešíme — server ho druhé straně ohlásí jako obsazeno.
        if (current != null && current.id != call.id && current.phase !in ENDED) return
        when (val state = call.state) {
            // `Pending` chodí víckrát (vytvořen, doručen) — stav téhož hovoru se nezakládá znovu.
            is CallStatePending -> _active.value = current?.takeIf { it.id == call.id }
                ?: ActiveCall(
                    id = call.id,
                    userId = call.userId,
                    isOutgoing = call.isOutgoing,
                    phase = if (call.isOutgoing) CallPhase.Calling else CallPhase.Ringing,
                    networkLost = networkLost,
                ).let { new ->
                    lastOutputs?.takeIf { call.isOutgoing }?.let { (available, output) -> new.copy(outputs = available, output = output) } ?: new
                }
            is CallStateExchangingKeys -> _active.update { it?.copy(phase = CallPhase.Connecting) }
            is CallStateReady -> {
                _active.update { it?.copy(phase = it.phase.takeIf { p -> p == CallPhase.Active } ?: CallPhase.Connecting, emojis = state.emojis.toList()) }
                if (startedCallId != call.id) {
                    startedCallId = call.id
                    startMedia(call, state)
                }
            }
            is CallStateHangingUp -> Unit
            is CallStateDiscarded -> finish(call.id, CallPhase.Ended)
            is CallStateError -> {
                logger.w { "Hovor skončil chybou: ${state.error.code} ${state.error.message}" }
                finish(call.id, CallPhase.Failed)
            }
            else -> Unit
        }
    }

    private fun startMedia(call: Call, state: CallStateReady) {
        val media = media ?: return
        logger.i { "Přenos: verze ${state.protocol.libraryVersions.joinToString()}, serverů ${state.servers.size}" }
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
                        CallConnection.Connected -> _active.update { active ->
                            active?.takeIf { it.id == call.id }?.copy(
                                phase = CallPhase.Active,
                                connectedAt = active.connectedAt ?: Clock.System.now().toEpochMilliseconds(),
                            ) ?: active
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

            },
        )
    }

    private fun finish(callId: Int, phase: CallPhase) {
        // Zastavit i hovor, který se nespojil: zvuková relace běží od vytáčení / přijetí.
        if (startedCallId == callId || _active.value?.id == callId) media?.stop()
        if (startedCallId == callId) startedCallId = null
        _active.update { active -> active?.takeIf { it.id == callId }?.copy(phase = if (active.phase == CallPhase.Failed) CallPhase.Failed else phase) ?: active }
        // Konec chvíli ukázat, ať se hovor jen beze slova neztratí.
        scope.launch {
            delay(ENDED_VISIBLE_MS)
            _active.update { active -> active?.takeIf { it.id != callId || it.phase !in ENDED } }
        }
    }

    private companion object {
        val ENDED = setOf(CallPhase.Ended, CallPhase.Failed)
        const val ENDED_VISIBLE_MS = 2_000L
    }
}
