package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.calls.CallAudioOutput
import eu.tintera.plume.core.ui.UiText
import kotlinx.coroutines.flow.StateFlow

/**
 * Hovor ve dvou: TDLib dělá signalizaci a klíč, přenos [eu.tintera.plume.core.calls.CallMedia].
 * Bez mediální vrstvy (iOS, desktop bez přibalené knihovny) je [supported] `false` a hovory se
 * nenabízejí — příchozí dál jen ohlásí notifikace.
 */
interface CallRepository {
    val supported: Boolean

    /** Hovory umí i obraz (bez něj se videohovor nenabízí a přijme se bez kamery). */
    val supportsVideo: Boolean

    /** Kamera jde přepnout (přední / zadní); na desktopu ne. */
    val canSwitchCamera: Boolean get() = true

    /** Náhled kamery je vidět už při vytáčení; na desktopu až od spojení. */
    val previewsBeforeConnect: Boolean get() = true

    /** Jde sdílet obrazovku (Android). */
    val supportsScreenSharing: Boolean get() = false

    /** Probíhající hovor, nebo `null`. Najednou je jen jeden, jako u telefonu. */
    val active: StateFlow<ActiveCall?>

    /** [video] = videohovor: kamera běží od vytáčení a zvuk jde do reproduktoru. */
    suspend fun call(userId: Long, video: Boolean = false): Result<Unit>

    /**
     * Přijme příchozí hovor ([CallPhase.Ringing]). [camera] = se zapnutou kamerou (videohovor
     * přijatý s oprávněním ke kameře); bez ní protistranu dál vidí, jen ona nevidí nás.
     */
    suspend fun accept(camera: Boolean = false): Result<Unit>

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

    /** Zapne / vypne kameru; zapnutí přepne zvuk ze sluchátka u ucha na reproduktor. */
    fun setCamera(on: Boolean)

    /**
     * Náhled kamery u zvonícího příchozího videohovoru: člověk vidí, jak bude vypadat, než
     * zvedne. Obraz se neposílá (přenos ještě neběží) a zvuk se nepřepíná. Zastaví ho
     * přijetí bez kamery, odmítnutí i konec hovoru; přijetí s kamerou ho převezme.
     */
    fun previewCamera(on: Boolean)

    /** Přední ↔ zadní kamera. */
    fun switchCamera()

    /**
     * Sdílení obrazovky místo kamery: zapnutí kameru vypne (v hovoru ve dvou jde jen jedno
     * z nich), zapnutí kamery zase vypne sdílení. Souhlas se snímáním obrazovky musí být
     * vyřízený předem (`rememberScreenSharePermission`).
     */
    fun setScreenSharing(on: Boolean) = Unit

    /**
     * Ukazovátko na obrazovce, kterou sdílí protistrana: místo v poměru k jejímu obrazu
     * (0–1), [down] = prst je dole. Jde jen protějšku Plume, který ukazovátko ohlásil
     * ([ActiveCall.remotePointer]).
     */
    fun sendPointer(x: Float, y: Float, down: Boolean) = Unit

    /**
     * Hovor nejde zahájit ani přijmout z důvodu mimo přenos (chybí oprávnění). Zvonící hovor
     * dostane [reason] místo stavu, jinak se krátce ukáže neúspěšný hovor s [reason].
     */
    fun reportFailure(userId: Long, video: Boolean, reason: UiText)
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
    /** Hovor začal jako videohovor (TDLib `call.isVideo`); kamera jde zapnout v každém. */
    val isVideo: Boolean = false,
    /** Posíláme obraz z kamery. */
    val camera: Boolean = false,
    /** Náhled kamery u zvonícího videohovoru ([CallRepository.previewCamera]); obraz nejde ven. */
    val preview: Boolean = false,
    /** Posíláme obrazovku (místo kamery). */
    val screenSharing: Boolean = false,
    /** Protistrana sdílí obrazovku (místo kamery). */
    val remoteScreen: Boolean = false,
    /** Protistrana (Plume) umí na své sdílené obrazovce ukázat naše ukazovátko. */
    val remotePointer: Boolean = false,
    /** Přední kamera (náhled se zrcadlí), jinak zadní. */
    val frontCamera: Boolean = true,
    /** Protistrana posílá obraz. */
    val remoteVideo: Boolean = false,
    /**
     * Proč hovor nejde (soukromí protějšku, omezení počtu hovorů, chybějící oprávnění); obrazovka
     * ho ukáže místo obecného „nepodařilo se spojit". `null` = důvod nevíme, nebo není.
     */
    val failure: UiText? = null,
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
