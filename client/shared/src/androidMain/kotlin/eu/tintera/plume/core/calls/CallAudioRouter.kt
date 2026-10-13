package eu.tintera.plume.core.calls

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import co.touchlab.kermit.Logger

/**
 * Kam jde zvuk hovoru: sluchátko u ucha, reproduktor, sluchátka s kabelem (i USB), nebo
 * Bluetooth souprava.
 *
 * - **Samo od sebe vede přednost Bluetooth, pak sluchátka, pak sluchátko telefonu.** Kdo má
 *   v uších soupravu, nechce hovor slyšet z telefonu — a dřív ho Plume na začátku hovoru
 *   natvrdo poslal do sluchátka telefonu (`setCommunicationDevice(EARPIECE)`) a připojenou
 *   soupravu tím přebil.
 * - **Připojení soupravy nebo sluchátek za hovoru na ně zvuk přepne**, i když si uživatel
 *   předtím vybral reproduktor — tak se chovají systémový telefon i oficiální klient. Když
 *   vybrané zařízení zmizí (vybitá souprava), vrátí se k automatické volbě.
 * - **Od Androidu 12 přes `setCommunicationDevice`**, do té doby postaru: Bluetooth přes
 *   `startBluetoothSco`, reproduktor přes `isSpeakerphoneOn` (kabelová sluchátka si systém
 *   vezme sám).
 *
 * Volá se z vlákna přenosu; změny zařízení chodí na hlavní vlákno (callback), stav je proto
 * `@Volatile` a na pořadí mezi nimi nezáleží — vždycky se znovu přečte, co je k dispozici.
 */
internal class CallAudioRouter(
    private val audio: AudioManager,
) : CallAudioRoutes {
    private var onChange: (available: List<CallAudioOutput>, current: CallAudioOutput) -> Unit = { _, _ -> }
    private val logger = Logger.withTag("CallAudioRouter")
    private val main = Handler(Looper.getMainLooper())

    /** Výslovná volba uživatele; `null` = automaticky (Bluetooth > sluchátka > sluchátko). */
    @Volatile private var chosen: CallAudioOutput? = null

    private var started = false

    private val devices = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            // Nová souprava nebo sluchátka: zvuk jde tam, i když byl vybraný reproduktor.
            if (addedDevices.any { it.isSink && it.output()?.kind in PERSONAL }) chosen = null
            refresh()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }

    override fun start(onChange: (available: List<CallAudioOutput>, current: CallAudioOutput) -> Unit) {
        if (started) return
        this.onChange = onChange
        started = true
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        chosen = null
        audio.registerAudioDeviceCallback(devices, main)
        refresh()
    }

    override fun select(output: CallAudioOutput) {
        chosen = output
        refresh()
    }

    override fun stop() {
        if (!started) return
        started = false
        audio.unregisterAudioDeviceCallback(devices)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audio.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audio.isSpeakerphoneOn = false
                @Suppress("DEPRECATION")
                audio.stopBluetoothSco()
                @Suppress("DEPRECATION")
                audio.isBluetoothScoOn = false
            }
            // Vždycky normální režim, ne „ten předtím": kdyby relace začala nad jinou, ještě
            // neukončenou (dva hovory rychle po sobě), vrácení „předchozího" by telefon nechalo
            // v režimu hovoru i po zavěšení.
            audio.mode = AudioManager.MODE_NORMAL
        }.onFailure { logger.w(it) { "Zvuk se po hovoru nepodařilo vrátit." } }
    }

    /** Znovu přečte zařízení, vybere výstup, nastaví ho a ohlásí. */
    private fun refresh() {
        if (!started) return
        runCatching {
            val available = available()
            val wanted = chosen?.takeIf { it in available.keys }
                ?: automatic(available.keys).also { chosen = null }
            apply(wanted, available[wanted])
            onChange(available.keys.toList(), wanted)
        }.onFailure { logger.w(it) { "Výstup zvuku se nepodařilo nastavit." } }
    }

    private fun automatic(available: Set<CallAudioOutput>): CallAudioOutput =
        available.firstOrNull { it.kind == CallAudioOutput.Kind.Bluetooth }
            ?: available.firstOrNull { it.kind == CallAudioOutput.Kind.Headset }
            ?: CallAudioOutput.Earpiece

    /** Výstupy k dispozici; u Androidu 12+ i se zařízením, které se pak předá systému. */
    private fun available(): Map<CallAudioOutput, AudioDeviceInfo?> {
        val found = linkedMapOf<CallAudioOutput, AudioDeviceInfo?>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audio.availableCommunicationDevices.forEach { device ->
                device.output()?.let { found.putIfAbsent(it, device) }
            }
        } else {
            found[CallAudioOutput.Earpiece] = null
            found[CallAudioOutput.Speaker] = null
            audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).forEach { device ->
                device.output()?.takeIf { it.kind in PERSONAL }?.let { found.putIfAbsent(it, device) }
            }
        }
        // Telefon bez sluchátka (tablet): zbude reproduktor.
        return found.ifEmpty { linkedMapOf(CallAudioOutput.Speaker to null) }
    }

    private fun apply(output: CallAudioOutput, device: AudioDeviceInfo?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (device != null) audio.setCommunicationDevice(device) else audio.clearCommunicationDevice()
            return
        }
        @Suppress("DEPRECATION")
        when (output.kind) {
            CallAudioOutput.Kind.Bluetooth -> {
                audio.isSpeakerphoneOn = false
                audio.startBluetoothSco()
                audio.isBluetoothScoOn = true
            }
            CallAudioOutput.Kind.Speaker -> {
                audio.stopBluetoothSco()
                audio.isBluetoothScoOn = false
                audio.isSpeakerphoneOn = true
            }
            CallAudioOutput.Kind.Earpiece, CallAudioOutput.Kind.Headset -> {
                audio.stopBluetoothSco()
                audio.isBluetoothScoOn = false
                audio.isSpeakerphoneOn = false
            }
        }
    }

    private fun AudioDeviceInfo.output(): CallAudioOutput? = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> CallAudioOutput.Earpiece
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> CallAudioOutput.Speaker
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        -> CallAudioOutput(CallAudioOutput.Kind.Headset)
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        TYPE_BLE_HEADSET,
        -> CallAudioOutput(CallAudioOutput.Kind.Bluetooth, productName?.toString()?.takeIf { it.isNotBlank() })
        else -> null
    }

    private companion object {
        /** `AudioDeviceInfo.TYPE_BLE_HEADSET` (API 31); číslem, ať jde přeložit i pro starší. */
        const val TYPE_BLE_HEADSET = 26

        val PERSONAL = setOf(CallAudioOutput.Kind.Headset, CallAudioOutput.Kind.Bluetooth)
    }
}
