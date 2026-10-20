package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow

/**
 * Živý příběh z pohledu diváka. Je to skupinový hovor ([groupCallId] z `StoryContentLive`),
 * ke kterému se připojuje mediální vrstva (ntgcalls): ta vyrobí přihlašovací payload, TDLib ho
 * pošle serveru ([join]) a odpověď vrátí zpátky knihovně. Obraz a zvuk pak tečou buď přes
 * WebRTC, nebo — u velkého publika a u RTMP — po segmentech, které si knihovna říká přes
 * [streamTimestamp] a [segment].
 */
interface LiveStoryRepository {
    /** Připojí se jako divák (ztlumený, bez kamery). Vrací odpověď serveru pro knihovnu. */
    suspend fun join(groupCallId: Int, payload: String, audioSourceId: Int): Result<String>

    suspend fun leave(groupCallId: Int)

    suspend fun call(groupCallId: Int): Result<LiveCall>

    /** Změny hovoru (počet diváků, konec vysílání) pro všechny hovory; filtruje volající. */
    val callUpdates: Flow<LiveCall>

    /** Kdo vysílá a jaký obraz posílá; u RTMP `null` (vysílá server, ne účastník). */
    suspend fun streamer(groupCallId: Int): Result<LiveStreamer?>

    /** Změny účastníků (zapnutá a vypnutá kamera) pro všechny hovory; filtruje volající. */
    val participantUpdates: Flow<LiveParticipantUpdate>

    /** Kde stream právě končí (unixové milisekundy); 0, dokud žádný není. */
    suspend fun streamTimestamp(groupCallId: Int): Result<Long>

    /** Segment streamu od [timeMs]: kanál 0 je zvuk, ostatní obraz účastníků. */
    suspend fun segment(groupCallId: Int, timeMs: Long, channelId: Int, quality: LiveVideoQuality?): Result<ByteArray>
}

data class LiveCall(
    val groupCallId: Int,
    val isActive: Boolean,
    val participantCount: Int,
    val isRtmp: Boolean,
)

/** Vysílající: id uživatele nebo chatu, kterým účastník vystupuje, a jeho obraz. */
data class LiveStreamer(
    val participantId: Long,
    val camera: LiveVideo?,
    val screen: LiveVideo?,
)

data class LiveParticipantUpdate(val groupCallId: Int, val streamer: LiveStreamer)

/** Obrazový kanál účastníka, jak ho chce knihovna (`addIncomingVideo`). */
data class LiveVideo(val endpoint: String, val sourceGroups: List<LiveVideoSourceGroup>)

data class LiveVideoSourceGroup(val semantics: String, val sourceIds: List<Int>)

enum class LiveVideoQuality { Thumbnail, Medium, Full }
