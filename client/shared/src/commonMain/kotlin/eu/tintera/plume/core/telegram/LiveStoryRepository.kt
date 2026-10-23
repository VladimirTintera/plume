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

    /**
     * Zprávy a placené reakce ve vysílání pro všechny hovory; filtruje volající. Historii
     * TDLib nevydává — chodí jen to, co přijde za sledování (i vlastní odeslaná zpráva).
     */
    val messageEvents: Flow<LiveMessageEvent>

    /** Pošle zprávu zdarma; placené zprávy (hvězdy) Plume neposílá. Text bez zalomení řádků. */
    suspend fun sendMessage(groupCallId: Int, text: String): Result<Unit>

    /** Smaže zprávu (vlastní, nebo jako správce); [reportSpam] ji zároveň nahlásí. */
    suspend fun deleteMessage(groupCallId: Int, messageId: Int, reportSpam: Boolean): Result<Unit>
}

data class LiveCall(
    val groupCallId: Int,
    val isActive: Boolean,
    val participantCount: Int,
    val isRtmp: Boolean,
    /** Psát smí i tenhle divák (`canSendMessages`); vysílající je může zakázat všem. */
    val canSendMessages: Boolean = false,
    /** Kolik hvězd stojí jedna zpráva řadového diváka; 0 = zdarma. */
    val messageStarCount: Long = 0,
    /** Smí mazat cizí zprávy (vysílající a správci). */
    val canDeleteMessages: Boolean = false,
)

/** Zpráva v chatu živého vysílání. Prázdný [text] = placená reakce bez textu. */
data class LiveMessage(
    val id: Int,
    val senderId: Long,
    val senderName: String,
    val avatarFileId: Int?,
    val text: String,
    /** Kolik hvězd odesílatel za zprávu zaplatil; placené se zvýrazňují. */
    val starCount: Long,
    /** Od vysílajícího — kreslí se jako zpráva nejvyšší úrovně. */
    val isFromOwner: Boolean,
    val canBeDeleted: Boolean,
)

sealed interface LiveMessageEvent {
    val groupCallId: Int

    data class New(override val groupCallId: Int, val message: LiveMessage) : LiveMessageEvent

    data class Deleted(override val groupCallId: Int, val messageIds: Set<Int>) : LiveMessageEvent

    /** Odeslání vlastní zprávy selhalo — zpráva z chatu zmizí. */
    data class SendFailed(override val groupCallId: Int, val messageId: Int) : LiveMessageEvent

    /** Placená reakce (hvězdy) od diváka — přes vysílání proletí. */
    data class PaidReaction(override val groupCallId: Int, val senderName: String?, val starCount: Long) : LiveMessageEvent
}

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
