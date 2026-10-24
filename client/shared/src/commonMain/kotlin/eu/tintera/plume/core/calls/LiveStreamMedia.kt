package eu.tintera.plume.core.calls

import eu.tintera.plume.core.telegram.LiveVideo
import eu.tintera.plume.core.telegram.LiveVideoQuality

/**
 * Přenos skupinového hovoru k posluchači (živý příběh, videochat) — příjem, a ve videochatu
 * na požádání i vlastní hlas ([setMicrophone]). Jen Android (ntgcalls); jinde se
 * nezaregistruje a obrazovka řekne, že to neumí.
 *
 * Všechno jde přes [host]: knihovna si řekne o připojení i o segmenty streamu a hostitel jí je
 * obstará z TDLib. Obraz se kreslí přes `CallVideo(CallVideoSource.Live)`.
 */
interface LiveStreamMedia {
    /** Spustí příjem hovoru [callId]; předchozí příjem (jiný příběh) zastaví. */
    fun start(callId: Long, host: LiveStreamHost)

    /** Obraz vysílajícího ([participantId] = jeho id); knihovna si ho jinak nepřijme. */
    fun addVideo(callId: Long, participantId: Long, video: LiveVideo)

    fun removeVideo(callId: Long, endpoint: String)

    /**
     * Zapne, nebo vypne vlastní mikrofon. Vypnutý mikrofon je mimo zdroje úplně, ne jen
     * ztlumený — posluchač nemá mít rozsvícený indikátor mikrofonu. Oprávnění řeší volající.
     */
    fun setMicrophone(callId: Long, on: Boolean) = Unit

    fun stop(callId: Long)
}

/** Co knihovna potřebuje zvenku. Volá se mimo hlavní vlákno; odpověď smí přijít později. */
interface LiveStreamHost {
    /** Pošle přihlašovací [payload] serveru; vrací jeho odpověď, `null` = nepovedlo se. */
    suspend fun join(payload: String, audioSourceId: Int): String?

    /** Kde stream právě končí (unixové milisekundy); 0 = zatím nic. */
    suspend fun timestamp(): Long

    /** Segment streamu; `null` = zatím není (knihovna se zeptá znovu). */
    suspend fun segment(timeMs: Long, channelId: Int, quality: LiveVideoQuality?): ByteArray?

    /** Knihovna dostala odpověď serveru; teď má smysl přidat obraz vysílajícího. */
    fun onJoined()

    fun onConnection(state: CallConnection)

    /** Přišel první snímek obrazu (`true`), nebo obraz zmizel. */
    fun onVideo(active: Boolean)
}
