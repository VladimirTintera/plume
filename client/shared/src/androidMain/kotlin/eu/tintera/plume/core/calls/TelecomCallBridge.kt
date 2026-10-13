package eu.tintera.plume.core.calls

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.telecom.DisconnectCause
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallEndpointCompat
import androidx.core.telecom.CallsManager
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ActiveCall
import eu.tintera.plume.core.telegram.CallPhase
import eu.tintera.plume.core.telegram.CallRepository
import eu.tintera.plume.core.telegram.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Hovory Plume přihlášené do systémového telefonu (Telecom přes Jetpack `core-telecom`,
 * pod Androidem 14 `ConnectionService`).
 *
 * Co tím hovor získá:
 * - **Souběh s GSM hovorem**: systém hovor Plume podrží (`onSetInactive` → [CallRepository.setHeld])
 *   a po GSM hovoru vrátí; bez Telecomu by oba hovory braly mikrofon naráz.
 * - **Přijetí a zavěšení odjinud** — auto (Android Auto), hodinky, tlačítko na sluchátkách.
 * - **Výstup zvuku vede systém** (sluchátko, reproduktor, Bluetooth, kabel): Telecom si cestu
 *   hlídá sám a vlastní `setCommunicationDevice` by se s ním přetahoval. Proto [audio]
 *   (výstupy z `CallControlScope`) místo `CallAudioRouter`; ten zůstává jen pro hovor, který
 *   Telecom nepřijal.
 *
 * Hovor se přihlásí, jakmile začne (vytáčí / zvoní), a odhlásí, když skončí. Stav drží
 * [CallRepository]; tady se jen zrcadlí oběma směry.
 */
class TelecomCallBridge(
    private val context: Context,
    private val calls: CallRepository,
    private val chats: ChatRepository,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("TelecomCalls")
    private val manager = CallsManager(context)
    private val audio = TelecomAudioRoutes(context.getSystemService(AudioManager::class.java))

    /** Aplikace je v Telecomu zaregistrovaná; jinak hovory jedou bez něj. */
    @Volatile private var registered = false

    fun start() {
        registered = runCatching { manager.registerAppWithTelecom(CallsManager.CAPABILITY_BASELINE) }
            .onFailure { logger.w(it) { "Telecom aplikaci nepřijal, hovory pojedou bez systému." } }
            .isSuccess
        if (!registered) return
        scope.launch {
            calls.active
                .map { call -> call?.takeIf { it.phase !in ENDED }?.id }
                .distinctUntilChanged()
                .collect { id ->
                    val call = calls.active.value?.takeIf { it.id == id } ?: return@collect
                    launch { track(call) }
                }
        }
    }

    /** Výstup zvuku pro mediální vrstvu: přes Telecom, když je k dispozici. */
    internal fun routes(): CallAudioRoutes = if (registered) audio else CallAudioRouter(context.getSystemService(AudioManager::class.java))

    private suspend fun track(call: ActiveCall) {
        val name = chats.summaries(listOf(call.userId)).firstOrNull()?.title ?: ""
        val attributes = CallAttributesCompat(
            displayName = name,
            address = Uri.fromParts(URI_SCHEME, call.userId.toString(), null),
            direction = if (call.isOutgoing) CallAttributesCompat.DIRECTION_OUTGOING else CallAttributesCompat.DIRECTION_INCOMING,
            callType = CallAttributesCompat.CALL_TYPE_AUDIO_CALL,
            callCapabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE,
        )
        runCatching {
            manager.addCall(
                attributes,
                // Přijetí odjinud (auto, hodinky); z aplikace jde přijetí přes `CallRepository`.
                onAnswer = { _ ->
                    calls.accept().onFailure { logger.w(it) { "Přijetí z Telecomu selhalo." } }
                    Unit
                },
                onDisconnect = { _ -> if (calls.active.value?.id == call.id) calls.hangUp() },
                onSetActive = { calls.setHeld(false) },
                onSetInactive = { calls.setHeld(true) },
            ) {
                logger.i { "Hovor ${call.id} je v Telecomu." }
                audio.attach(this)
                // Podržený hovor (kvůli GSM) vrací uživatel — systém sám ne.
                val control = this
                calls.setResumeHandler {
                    runCatching { control.setActive() }
                        .onFailure { logger.w(it) { "Telecom hovor neobnovil." } }
                        .getOrNull() is CallControlResult.Success
                }
                launch { mirror(call) }
            }
        }.onFailure { failure ->
            // Bez Telecomu hovor jede dál, jen zvuk povede Plume sám.
            logger.w(failure) { "Telecom hovor ${call.id} nepřijal." }
            audio.fallBack()
        }
        audio.detach()
        calls.setResumeHandler(null)
    }

    /** Stav hovoru z Plume do Telecomu: spojeno → aktivní, konec → odhlásit. */
    private suspend fun CallControlScope.mirror(call: ActiveCall) {
        var reported = false
        calls.active.first { current ->
            val ours = current?.takeIf { it.id == call.id }
            if (ours == null || ours.phase in ENDED) {
                val cause = if (ours?.phase == CallPhase.Failed) DisconnectCause.ERROR else DisconnectCause.LOCAL
                runCatching { disconnect(DisconnectCause(cause)) }
                return@first true
            }
            if (!reported && ours.phase in CONNECTED) {
                reported = true
                // Příchozí hovor přijatý v aplikaci Telecomu ohlásí `answer`, odchozí `setActive`.
                val result = runCatching {
                    if (call.isOutgoing) setActive() else answer(CallAttributesCompat.CALL_TYPE_AUDIO_CALL)
                }
                logger.i { "Telecom: hovor ${call.id} aktivní (${result.getOrNull() ?: result.exceptionOrNull()?.message})" }
            }
            false
        }
    }

    private companion object {
        val ENDED = setOf(CallPhase.Ended, CallPhase.Failed)
        val CONNECTED = setOf(CallPhase.Connecting, CallPhase.Active)
        const val URI_SCHEME = "plume"
    }
}

/**
 * Výstup zvuku přes Telecom. Zvuková relace začíná dřív, než je hovor v Telecomu (vytáčení),
 * proto se výstupy dodají, až se [attach] připojí; když Telecom hovor nepřijme ([fallBack]),
 * převezme zvuk `CallAudioRouter`.
 *
 * **Režim hovoru (`MODE_IN_COMMUNICATION`) nastavuje i tak Plume** — od vytáčení, kvůli tónu
 * protistrany, který Telecom u hovoru ve vytáčení sám do režimu hovoru nedá.
 */
internal class TelecomAudioRoutes(private val audio: AudioManager) : CallAudioRoutes {
    private val logger = Logger.withTag("TelecomAudio")
    private var onChange: (List<CallAudioOutput>, CallAudioOutput) -> Unit = { _, _ -> }
    private var control: CallControlScope? = null
    private var endpoints: List<CallEndpointCompat> = emptyList()
    private var fallback: CallAudioRouter? = null
    private var started = false

    override fun start(onChange: (List<CallAudioOutput>, CallAudioOutput) -> Unit) {
        this.onChange = onChange
        started = true
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    fun attach(scope: CallControlScope) {
        control = scope
        scope.launch {
            combine(scope.availableEndpoints, scope.currentCallEndpoint) { available, current -> available to current }
                .collect { (available, current) ->
                    endpoints = available
                    val outputs = available.mapNotNull { it.output() }.distinct()
                    current.output()?.let { if (started) onChange(outputs.ifEmpty { listOf(it) }, it) }
                }
        }
    }

    fun detach() {
        control = null
        endpoints = emptyList()
    }

    /** Telecom hovor nepřijal: zvuk převezme Plume sám. */
    fun fallBack() {
        if (!started || fallback != null) return
        fallback = CallAudioRouter(audio).also { it.start(onChange) }
    }

    override fun select(output: CallAudioOutput) {
        fallback?.let { it.select(output); return }
        val scope = control ?: return
        val endpoint = endpoints.firstOrNull { it.output() == output } ?: return
        scope.launch {
            runCatching { scope.requestEndpointChange(endpoint) }
                .onFailure { logger.w(it) { "Telecom výstup nepřepnul." } }
        }
    }

    override fun stop() {
        fallback?.stop()
        fallback = null
        if (started) runCatching { audio.mode = AudioManager.MODE_NORMAL }
        started = false
    }

    private fun CallEndpointCompat.output(): CallAudioOutput? = when (type) {
        CallEndpointCompat.TYPE_EARPIECE -> CallAudioOutput.Earpiece
        CallEndpointCompat.TYPE_SPEAKER -> CallAudioOutput.Speaker
        CallEndpointCompat.TYPE_WIRED_HEADSET -> CallAudioOutput(CallAudioOutput.Kind.Headset)
        CallEndpointCompat.TYPE_BLUETOOTH -> CallAudioOutput(CallAudioOutput.Kind.Bluetooth, name.toString().takeIf { it.isNotBlank() })
        else -> null
    }
}
