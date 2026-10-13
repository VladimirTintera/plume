package eu.tintera.plume.core.calls

/**
 * Mediální vrstva hovoru — zvuk a obraz mezi dvěma zařízeními.
 *
 * TDLib obstará signalizaci a klíč (`createCall`, `CallStateReady`), přenos ne; ten dělá
 * knihovna nad WebRTC (`docs/poznatky/hovory.md`). Tohle rozhraní je hranice mezi nimi:
 * `core/telegram` sem podá, co přišlo v `CallStateReady`, a přeposílá signalizaci oběma
 * směry. Implementace je jen na Androidu (ntgcalls); jinde se v Koinu nezaregistruje
 * a hovory tam nejsou.
 */
interface CallMedia {
    /** Co knihovna umí — tím se hovor nabízí v `createCall` / `acceptCall`. */
    val protocol: CallMediaProtocol

    /** Umí obraz (kamera, obraz protistrany); desktop zatím jen zvuk. */
    val supportsVideo: Boolean get() = true

    /**
     * Zvuková relace hovoru: režim hovoru a výstup (sluchátko, reproduktor, Bluetooth).
     * Spouští se UŽ PŘI VYTÁČENÍ A PŘIJETÍ, ne až se spojením — tón protistrany potřebuje
     * režim hovoru (mimo něj ho telefon na vibracích ztlumí) a přepnout reproduktor má jít
     * hned. [onOutputs] hlásí, kam jde zvuk a kam jde přepnout, při každé změně zařízení.
     * Končí s [stop].
     */
    fun startAudio(onOutputs: (available: List<CallAudioOutput>, current: CallAudioOutput) -> Unit)

    /** Spustí přenos. Volá se jednou na hovor, po `CallStateReady`. */
    fun start(session: CallMediaSession, events: CallMediaEvents)

    /** Signalizační paket od protějšku (`updateNewCallSignalingData`). */
    fun receiveSignaling(data: ByteArray)

    fun setMuted(muted: Boolean)

    /**
     * Podržení: systém dal přednost jinému hovoru (GSM). Mikrofon ani přehrávání neběží,
     * po uvolnění se vrátí, jak byly (ztlumení uživatelem zůstane).
     */
    fun setHeld(held: Boolean)

    /** Kam jde zvuk hovoru: sluchátko, reproduktor, sluchátka s kabelem, nebo Bluetooth. */
    fun setOutput(output: CallAudioOutput)

    /**
     * Kamera: [on] posílá obraz z přední ([front]), nebo zadní kamery, `false` ho zastaví.
     * Jde zapnout i v hovoru, který začal jako hlasový, a před spuštěním přenosu (vytáčený
     * videohovor) — pak se použije, až přenos naběhne. Náhled i obraz protistrany kreslí
     * platforma (`CallVideo`).
     */
    fun setCamera(on: Boolean, front: Boolean)

    /** Zastaví přenos a vrátí zvuk telefonu, jak byl. Bezpečné volat opakovaně. */
    fun stop()
}

/**
 * Verze protokolu a vrstvy, které knihovna podporuje. **Nabízet se smí jen tyhle** — server
 * vybere společnou a druhá strana na ni přistoupí; verze, kterou neumíme, by skončila hovorem,
 * který protějšek počítá jako uskutečněný, ale zvuk neteče.
 */
data class CallMediaProtocol(
    val minLayer: Int,
    val maxLayer: Int,
    val versions: List<String>,
)

/** Všechno, co přenos potřebuje z `CallStateReady`. */
class CallMediaSession(
    val callId: Int,
    val isOutgoing: Boolean,
    /** Sdílený klíč z výměny, kterou provedl TDLib (256 bajtů). */
    val encryptionKey: ByteArray,
    /** Dohodnutá verze — server vrací jednu. */
    val versions: List<String>,
    val servers: List<CallMediaServer>,
    val allowP2p: Boolean,
    val customParameters: String,
)

/** Server pro přenos: TURN/STUN Telegramu (se jménem a heslem), nebo reflector ([peerTag]). */
class CallMediaServer(
    val id: Long,
    val ipv4: String,
    val ipv6: String,
    val port: Int,
    val username: String,
    val password: String,
    val turn: Boolean,
    val stun: Boolean,
    val tcp: Boolean,
    /** Jen u reflectoru Telegramu; u TURN/STUN `null`. */
    val peerTag: ByteArray?,
)

/** Co přenos hlásí zpátky. Volá se z vláken knihovny, ne z hlavního. */
interface CallMediaEvents {
    /** Signalizační paket pro protějšek → `sendCallSignalingData`. */
    fun onSignaling(data: ByteArray)

    fun onConnection(state: CallConnection)

    /** Protistrana zapnula ([active]), nebo vypnula / pozastavila kameru. */
    fun onRemoteVideo(active: Boolean)
}

/** Který obraz hovoru se kreslí: náš z kamery (náhled), nebo protistrany. */
enum class CallVideoSource { Local, Remote }

/** Výstup zvuku hovoru. [name] jen u Bluetooth (jméno soupravy), jinak `null`. */
data class CallAudioOutput(val kind: Kind, val name: String? = null) {
    enum class Kind { Earpiece, Speaker, Headset, Bluetooth }

    companion object {
        val Earpiece = CallAudioOutput(Kind.Earpiece)
        val Speaker = CallAudioOutput(Kind.Speaker)
    }
}

enum class CallConnection { Connecting, Connected, Failed, Closed }
