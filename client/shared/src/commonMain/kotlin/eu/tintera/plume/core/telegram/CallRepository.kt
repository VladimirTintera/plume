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

    /** Podržet / uvolnit hovor — volá systém, když přijde jiný (GSM) hovor. */
    fun setHeld(held: Boolean)

    /**
     * Uživatel chce podržený hovor zpátky. Systém ho po GSM hovoru sám nevrátí — požádat musí
     * aplikace (Telecom `setActive`), a teprve když systém svolí, zvuk zase teče.
     */
    suspend fun resume()

    /** Kdo umí podržený hovor vrátit u systému (Android Telecom); `null` = vrátí se hned. */
    fun setResumeHandler(handler: (suspend () -> Boolean)?)

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
    /**
     * Telefon je právě bez sítě (přepíná se Wi-Fi a data, výpadek). Přenos to přečká, když se
     * síť do 20 s vrátí (pak zavěsí sám), a tak dlouho se místo času ukazuje, že se čeká.
     */
    val networkLost: Boolean = false,
    /** Podržený kvůli jinému hovoru (GSM); zvuk neteče oběma směry. */
    val held: Boolean = false,
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
