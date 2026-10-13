package eu.tintera.plume.core.calls

/**
 * Kdo vede zvuk hovoru: systémový Telecom (`TelecomCallBridge`), nebo Plume sám přes
 * `AudioManager` (`CallAudioRouter`), když hovor do Telecomu přihlásit nejde.
 */
internal interface CallAudioRoutes {
    /** Začátek zvukové relace; [onChange] hlásí výstupy k dispozici a ten současný. */
    fun start(onChange: (available: List<CallAudioOutput>, current: CallAudioOutput) -> Unit)

    fun select(output: CallAudioOutput)

    fun stop()
}
