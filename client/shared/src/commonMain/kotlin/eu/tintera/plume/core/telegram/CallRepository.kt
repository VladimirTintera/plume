package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.calls.CallAudioOutput
import kotlinx.coroutines.flow.StateFlow

/**
 * Hovor ve dvou: TDLib dělá signalizaci a klíč, přenos [eu.tintera.plume.core.calls.CallMedia].
 * Bez mediální vrstvy (iOS, desktop) je [supported] `false` a hovory se nenabízejí — příchozí
 * dál jen ohlásí notifikace.
 */
interface CallRepository {
    val supported: Boolean

    /** Probíhající hovor, nebo `null`. Najednou je jen jeden, jako u telefonu. */
    val active: StateFlow<ActiveCall?>

    suspend fun call(userId: Long): Result<Unit>

    /** Přijme příchozí hovor ([CallPhase.Ringing]). */
    suspend fun accept(): Result<Unit>

    /** Zavěsí, odmítne, nebo zruší — podle toho, v jaké fázi hovor je. */
    suspend fun hangUp()

    fun setMuted(muted: Boolean)

    fun setOutput(output: CallAudioOutput)
}

data class ActiveCall(
    val id: Int,
    val userId: Long,
    val isOutgoing: Boolean,
    val phase: CallPhase,
    /** Čtyři emoji k ověření; stejné na obou stranách = nikdo mezi nimi nesedí. Prázdné do `Ready`. */
    val emojis: List<String> = emptyList(),
    /** Kdy se spojil přenos (epoch ms); `null`, dokud se nespojil. Od něj běží délka hovoru. */
    val connectedAt: Long? = null,
    val muted: Boolean = false,
    /** Kam jde zvuk; ve sluchátku zhasíná obrazovku u ucha senzor přiblížení. */
    val output: CallAudioOutput = CallAudioOutput.Earpiece,
    /** Kam jde přepnout. Jen sluchátko a reproduktor = stačí přepínač, víc = výběr. */
    val outputs: List<CallAudioOutput> = listOf(CallAudioOutput.Earpiece, CallAudioOutput.Speaker),
)

enum class CallPhase {
    /** Příchozí hovor zvoní. */
    Ringing,
    /** Odchozí hovor čeká, až protějšek zvedne. */
    Calling,
    /** Zvednuto, domlouvá se klíč a spojuje přenos. */
    Connecting,
    /** Teče zvuk. */
    Active,
    /** Skončil; za chvíli zmizí. */
    Ended,
    /** Přenos se nespojil, nebo hovor skončil chybou. */
    Failed,
}
