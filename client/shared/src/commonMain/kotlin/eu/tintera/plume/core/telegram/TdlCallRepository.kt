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
import eu.tintera.plume.core.calls.CallConnection
import eu.tintera.plume.core.calls.CallMedia
import eu.tintera.plume.core.calls.CallMediaEvents
import eu.tintera.plume.core.calls.CallMediaServer
import eu.tintera.plume.core.calls.CallMediaSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
) : CallRepository {

    private val logger = Logger.withTag("Calls")

    private val _active = MutableStateFlow<ActiveCall?>(null)
    override val active: StateFlow<ActiveCall?> = _active.asStateFlow()

    override val supported: Boolean get() = media != null

    /** Hovor, kterému už běží přenos — `Ready` chodí i opakovaně, spustit se má jednou. */
    private var startedCallId: Int? = null

    init {
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
        return holder.client.createCall(userId = userId, protocol = protocol, isVideo = false).toResult().map { }
    }

    override suspend fun accept(): Result<Unit> {
        val call = _active.value?.takeIf { it.phase == CallPhase.Ringing }
            ?: return Result.failure(IllegalStateException("Žádný hovor nezvoní."))
        val protocol = protocol() ?: return Result.failure(UnsupportedOperationException("Hovory tu nejsou."))
        return holder.client.acceptCall(callId = call.id, protocol = protocol).toResult().map { }
    }

    override suspend fun hangUp() {
        val call = _active.value ?: return
        val connectedAt = call.connectedAt
        val duration = connectedAt?.let { ((Clock.System.now().toEpochMilliseconds() - it) / 1000).toInt() } ?: 0
        media?.stop()
        holder.client.discardCall(
            callId = call.id,
            isDisconnected = false,
            inviteLink = "",
            duration = duration,
            isVideo = false,
            connectionId = 0L,
        ).toResult().onFailure { logger.w(it) { "Hovor se nepodařilo ukončit." } }
    }

    override fun setSpeaker(on: Boolean) {
        media?.setSpeaker(on)
        _active.update { it?.copy(speaker = on) }
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
            is CallStatePending -> _active.value = ActiveCall(
                id = call.id,
                userId = call.userId,
                isOutgoing = call.isOutgoing,
                phase = if (call.isOutgoing) CallPhase.Calling else CallPhase.Ringing,
            )
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
                        CallConnection.Failed -> scope.launch {
                            _active.update { active -> active?.takeIf { it.id == call.id }?.copy(phase = CallPhase.Failed) ?: active }
                            hangUp()
                        }
                        else -> Unit
                    }
                }
            },
        )
    }

    private fun finish(callId: Int, phase: CallPhase) {
        if (startedCallId == callId) {
            media?.stop()
            startedCallId = null
        }
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
