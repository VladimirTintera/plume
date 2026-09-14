package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow

/**
 * Přístup k obsahu jedné konverzace.
 *
 * Rozhraní je **záměrně bezstavové**. Stránkovací stav (kde jsme v historii, co už je načtené)
 * drží `ConversationViewModel`, ne tohle — jednak je ta logika tím testovatelná bez TDLib,
 * jednak je stav přirozeně vázaný na životnost obrazovky, ne na aplikaci.
 *
 * Protějšek je [ChatRepository], který je naopak stavový: seznam chatů je jeden pro celou
 * aplikaci a musí žít i když na něj nikdo nekouká.
 */
interface ConversationRepository {

    /**
     * Načte [limit] zpráv **starších** než [fromMessageId].
     *
     * `fromMessageId = 0` znamená „od nejnovější". Prázdný výsledek znamená konec historie —
     * TDLib nevrací žádný explicitní příznak, pozná se to jen podle toho, že nic nepřišlo.
     */
    suspend fun history(chatId: Long, fromMessageId: Long, limit: Int = 30): Result<List<ChatMessage>>

    /** Nové zprávy v tomhle chatu, jak přicházejí. */
    fun newMessages(chatId: Long): Flow<ChatMessage>

    /** Id smazaných zpráv. */
    fun deletedMessages(chatId: Long): Flow<List<Long>>

    /** Zprávy, jimž se změnil obsah (editace). */
    fun editedMessages(chatId: Long): Flow<ChatMessage>

    suspend fun send(chatId: Long, text: String): Result<Unit>

    /** Označí zprávy za přečtené. Bez tohohle zůstane odznak nepřečtených viset napořád. */
    suspend fun markRead(chatId: Long, messageIds: List<Long>): Result<Unit>

    suspend fun chatInfo(chatId: Long): Result<ChatInfo>
}

/** Hlavička konverzace. */
data class ChatInfo(
    val id: Long,
    val title: String,
    /**
     * U skupin a kanálů se nad zprávami zobrazuje jméno odesílatele; v soukromém chatu by to byl
     * šum, protože odesílatelé jsou právě dva.
     */
    val isGroup: Boolean,
)

/**
 * Zpráva v konverzaci.
 *
 * [text] je pro fázi 2 jediná nesená podoba obsahu — u médií je to popisek nebo název typu
 * (stejný mapper jako v náhledu seznamu chatů). Skutečné vykreslování médií přijde později.
 */
data class ChatMessage(
    val id: Long,
    val isOutgoing: Boolean,
    /** `null` u odchozích zpráv a v soukromých chatech, kde jméno nedává smysl zobrazovat. */
    val senderName: String?,
    val senderId: Long,
    val text: String,
    /** Unixový čas v sekundách, jak ho dává TDLib. */
    val timestamp: Int,
    val isEdited: Boolean,
)
