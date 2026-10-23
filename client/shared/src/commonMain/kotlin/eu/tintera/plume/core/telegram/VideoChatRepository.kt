package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow

/**
 * Videochat skupiny (v kanálu živé vysílání) z pohledu posluchače. Je to tentýž skupinový
 * hovor jako u živého příběhu ([LiveStoryRepository]) a tentýž přenos (ntgcalls), jen se
 * připojuje jinou funkcí a má účastníky, kteří mluví a posílají obraz každý zvlášť.
 */
interface VideoChatRepository {
    /** Připojí se ztlumený a bez kamery; vrací odpověď serveru pro knihovnu. */
    suspend fun join(groupCallId: Int, payload: String, audioSourceId: Int): Result<String>

    suspend fun leave(groupCallId: Int)

    suspend fun call(groupCallId: Int): Result<VideoChatCall>

    /** Změny hovoru (počet účastníků, konec) pro všechny hovory; filtruje volající. */
    val callUpdates: Flow<VideoChatCall>

    /** Dočte účastníky — přijdou v [participantUpdates]. Jen v připojeném hovoru. */
    suspend fun loadParticipants(groupCallId: Int)

    /** Účastníci přicházejí a mění se jen updaty, pro všechny hovory; filtruje volající. */
    val participantUpdates: Flow<VideoChatParticipantUpdate>

    /** Kde stream právě končí (unixové milisekundy); 0, dokud žádný není. */
    suspend fun streamTimestamp(groupCallId: Int): Result<Long>

    /** Segment streamu od [timeMs]: kanál 0 je zvuk, ostatní obraz účastníků. */
    suspend fun segment(groupCallId: Int, timeMs: Long, channelId: Int, quality: LiveVideoQuality?): Result<ByteArray>

    /** Název chatu, kterému videochat patří — nadpis, když správce videochat nepojmenoval. */
    suspend fun chatTitle(chatId: Long): String?
}

data class VideoChatCall(
    val groupCallId: Int,
    val isActive: Boolean,
    val participantCount: Int,
    /** Název od správce; prázdný = bez názvu. */
    val title: String,
    /** Živé vysílání přes RTMP — vysílá server, účastníci jen poslouchají. */
    val isRtmp: Boolean,
)

/** Účastník videochatu, jak ho potřebuje seznam i přenos (obraz podle endpointu). */
data class VideoChatParticipant(
    val id: Long,
    val name: String,
    val avatarFileId: Int?,
    val camera: LiveVideo?,
    val screen: LiveVideo?,
    /** Ztlumený pro všechny — sám, nebo správcem. */
    val isMuted: Boolean,
    val isHandRaised: Boolean,
    val isCurrentUser: Boolean,
    /** Pořadí v seznamu; porovnává se jako text, větší výš. */
    val order: String,
)

/** Změna účastníka; prázdné [VideoChatParticipant.order] znamená, že ze seznamu odešel. */
data class VideoChatParticipantUpdate(val groupCallId: Int, val participant: VideoChatParticipant)
