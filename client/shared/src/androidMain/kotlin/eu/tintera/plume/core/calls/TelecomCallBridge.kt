package eu.tintera.plume.core.calls

import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import java.util.concurrent.Executors
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
 * - **Výstup zvuku vybírá systém** (sluchátko, reproduktor, Bluetooth, kabel): výstupy a volba
 *   jdou přes `CallControlScope` ([audio]); systému je ale musí říct Plume jako vlastník
 *   režimu hovoru (viz `TelecomAudioRoutes`). `CallAudioRouter` zůstává pro hovor, který
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
            // Typ jde zadat jen při přihlášení; kamera zapnutá později ho nezmění (a nevadí to).
            callType = if (call.isVideo) CallAttributesCompat.CALL_TYPE_VIDEO_CALL else CallAttributesCompat.CALL_TYPE_AUDIO_CALL,
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
                    if (call.isOutgoing) setActive() else answer(if (call.isVideo) CallAttributesCompat.CALL_TYPE_VIDEO_CALL else CallAttributesCompat.CALL_TYPE_AUDIO_CALL)
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
 * **Vlastníkem režimu hovoru je Plume, ne Telecom.** U hovoru přes `CallsManager` Telecom
 * `MODE_IN_COMMUNICATION` nenastavuje (v historii `dumpsys audio` je jen náš `setMode` —
 * nastavuje se od vytáčení kvůli tónu protistrany) a systém bere volbu výstupu jen od
 * vlastníka režimu. Volba Telecomu (`setCommunicationDevice` z system_serveru) se proto
 * ignoruje — „Computed Preferred communication device: null" a zvuk ve sluchátku, zatímco
 * Telecom hlásí reproduktor. Telecom se pak podle hlášení „reproduktor vypnut" sám vrátí na
 * sluchátko (`SPEAKER_OFF` z přijímače změn reproduktoru); videohovor tak šel zhruba
 * v každém pátém pokusu k uchu. Odtud dvě pravidla:
 * - **Naši volbu nastaví Plume hned sám** ([applyLocally]) a Telecomu ji jen oznámí
 *   (`requestEndpointChange`); ten se podle hlášení „reproduktor zapnut" přidá.
 * - **Volbu Telecomu Plume zrcadlí** (připojená souprava, volba v systémovém UI) — kromě
 *   krátké chvíle po vlastní volbě ([OWN_CHOICE_MS]), kdy Telecom ještě hlásí stav před ní.
 */
internal class TelecomAudioRoutes(private val audio: AudioManager) : CallAudioRoutes {
    private val logger = Logger.withTag("TelecomAudio")
    private var onChange: (List<CallAudioOutput>, CallAudioOutput) -> Unit = { _, _ -> }
    private var control: CallControlScope? = null
    private var endpoints: List<CallEndpointCompat> = emptyList()
    private var fallback: CallAudioRouter? = null
    private var started = false

    /** Volání `AudioManager` trvají stovky milisekund; v pořadí, mimo hlavní vlákno. */
    private val audioThread = Executors.newSingleThreadExecutor { Thread(it, "call-audio") }

    /** Naše volba, která ještě platí přednostně před tím, co hlásí Telecom (viz [follow]). */
    private var chosen: CallAudioOutput? = null

    /** Odkdy se na potvrzení [chosen] čeká (`elapsedRealtime`); po přihlášení znovu od začátku. */
    private var chosenAt = 0L

    override fun start(onChange: (List<CallAudioOutput>, CallAudioOutput) -> Unit) {
        this.onChange = onChange
        started = true
        // Stejným vláknem jako vrácení v `stop` — rychle po sobě jdoucí hovory se nepřeskočí.
        audioThread.execute { runCatching { audio.mode = AudioManager.MODE_IN_COMMUNICATION } }
    }

    fun attach(scope: CallControlScope) {
        control = scope
        chosenAt = SystemClock.elapsedRealtime()
        scope.launch {
            combine(scope.availableEndpoints, scope.currentCallEndpoint) { available, current -> available to current }
                .collect { (available, current) ->
                    endpoints = available
                    val shown = follow(scope, available, current) ?: return@collect
                    val outputs = available.mapNotNull { it.output() }.distinct()
                    if (started) onChange(outputs.ifEmpty { listOf(shown) }, shown)
                }
        }
    }

    /**
     * Srovná výstup s tím, co hlásí Telecom, a vrátí, který ukázat.
     *
     * Naše volba platí celých [OWN_CHOICE_MS] (od volby, nebo od přihlášení hovoru) — i když
     * ji Telecom mezitím potvrdí: pár milisekund po potvrzení se umí vrátit (opožděné
     * `SPEAKER_OFF`) a zrcadlení by návrat zkopírovalo. Kdykoli se v té lhůtě odchýlí, oznámí
     * se mu volba znovu. Až potom se zrcadlí, co hlásí.
     */
    private fun follow(scope: CallControlScope, available: List<CallEndpointCompat>, current: CallEndpointCompat): CallAudioOutput? {
        val reported = current.output()
        val wanted = chosen
        if (wanted != null) {
            if (SystemClock.elapsedRealtime() - chosenAt <= OWN_CHOICE_MS) {
                available.firstOrNull { it.output() == wanted }?.takeIf { reported != wanted }?.let { request(scope, it) }
                return wanted
            }
            chosen = null
        }
        reported?.let { applyLocally(it, force = false) }
        return reported
    }

    private fun request(scope: CallControlScope, endpoint: CallEndpointCompat) {
        scope.launch {
            runCatching { scope.requestEndpointChange(endpoint) }
                .onFailure { logger.w(it) { "Telecom výstup nepřepnul." } }
        }
    }

    /**
     * Výstup nastaví Plume jako vlastník režimu hovoru (od Androidu 12; dřív vede zvuk
     * Telecom). [force] u naší volby: nastaví se, i když systém hlásí, že už platí — hlášení
     * může pocházet od klienta Telecomu, kterého systém za chvíli přestane brát.
     */
    private fun applyLocally(output: CallAudioOutput, force: Boolean) {
        if (!started || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        audioThread.execute {
            runCatching {
                val devices = audio.availableCommunicationDevices
                val device = devices.firstOrNull { device ->
                    val candidate = device.output() ?: return@firstOrNull false
                    candidate.kind == output.kind && (output.kind != CallAudioOutput.Kind.Bluetooth || output.name == null || candidate.name == output.name)
                } ?: devices.firstOrNull { it.output()?.kind == output.kind }
                if (device != null && (force || audio.communicationDevice?.id != device.id)) audio.setCommunicationDevice(device)
            }.onFailure { logger.w(it) { "Výstup zvuku nejde nastavit." } }
        }
    }

    fun detach() {
        control = null
        endpoints = emptyList()
        chosen = null
    }

    /** Telecom hovor nepřijal: zvuk převezme Plume sám. */
    fun fallBack() {
        if (!started || fallback != null) return
        fallback = CallAudioRouter(audio).also { router ->
            router.start(onChange)
            chosen?.let(router::select)
            chosen = null
        }
    }

    override fun select(output: CallAudioOutput) {
        fallback?.let { it.select(output); return }
        chosen = output
        chosenAt = SystemClock.elapsedRealtime()
        applyLocally(output, force = true)
        val scope = control ?: return
        endpoints.firstOrNull { it.output() == output }?.let { request(scope, it) }
    }

    override fun stop() {
        fallback?.stop()
        fallback = null
        if (started) {
            audioThread.execute {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audio.clearCommunicationDevice()
                    audio.mode = AudioManager.MODE_NORMAL
                }
            }
        }
        started = false
        chosen = null
    }

    private fun CallEndpointCompat.output(): CallAudioOutput? = when (type) {
        CallEndpointCompat.TYPE_EARPIECE -> CallAudioOutput.Earpiece
        CallEndpointCompat.TYPE_SPEAKER -> CallAudioOutput.Speaker
        CallEndpointCompat.TYPE_WIRED_HEADSET -> CallAudioOutput(CallAudioOutput.Kind.Headset)
        CallEndpointCompat.TYPE_BLUETOOTH -> CallAudioOutput(CallAudioOutput.Kind.Bluetooth, name.toString().takeIf { it.isNotBlank() })
        else -> null
    }

    private companion object {
        /** Jak dlouho po vlastní volbě se nezrcadlí jiný výstup, který Telecom ještě hlásí. */
        const val OWN_CHOICE_MS = 3_000L
    }
}
