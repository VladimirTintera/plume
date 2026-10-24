package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow

/**
 * Přímé zprávy kanálu: kanál si může zapnout skupinu přímých zpráv (`Supergroup.isDirectMessagesGroup`),
 * do které odběratelé píšou jako do soukromého chatu. Správce ji vidí rozdělenou na vlákna, jedno
 * na každého, kdo kanálu napsal (`DirectMessagesChatTopic`) — podobně jako témata Uložených zpráv.
 */
data class DirectTopic(
    val chatId: Long,
    val id: Long,
    /** Kdo kanálu píše: uživatel, nebo chat (psaní jménem kanálu či skupiny). */
    val senderId: Long,
    val title: String,
    val avatarFileId: Int? = null,
    /** Pořadí od TDLib, vyšší nahoře; 0 = vlákno v seznamu není. */
    val order: Long = 0,
    val unreadCount: Int = 0,
    val isMarkedAsUnread: Boolean = false,
    val lastMessage: TopicMessagePreview? = null,
)

/** Hlavička konverzace zúžené na jedno vlákno přímých zpráv. */
data class DirectTopicHeader(
    val id: Long,
    val senderId: Long,
    val title: String,
    val avatarFileId: Int? = null,
)

val DirectTopic.header: DirectTopicHeader get() = DirectTopicHeader(id, senderId, title, avatarFileId)

/**
 * Vlákna přímých zpráv kanálu, jak je vidí správce. Hranice kolem `DirectMessagesChatTopic`
 * v TDLib, obdoba [SavedTopicsRepository] — jen pro víc chatů najednou.
 */
interface DirectTopicsRepository {

    /**
     * Vlákna chatu [chatId] v pořadí od TDLib; `null`, dokud se poprvé nenačtou. Chodí JEN updaty
     * (`UpdateDirectMessagesChatTopic`), seznam se vyžádá [load].
     */
    fun topics(chatId: Long): Flow<List<DirectTopic>?>

    /** Dotáhne všechna vlákna (`loadDirectMessagesChatTopics` až do konce). */
    suspend fun load(chatId: Long): Result<Unit>

    /** Jedno vlákno, jak ho TDLib zná teď (`getDirectMessagesChatTopic`). */
    suspend fun topic(chatId: Long, topicId: Long): DirectTopic?

    /** Smaže celé vlákno — všechny zprávy toho, kdo kanálu psal, i odpovědi na ně. Nevratně. */
    suspend fun deleteHistory(chatId: Long, topicId: Long): Result<Unit>

    /** Ručně označit vlákno jako nepřečtené (nebo značku zrušit). */
    suspend fun setMarkedAsUnread(chatId: Long, topicId: Long, unread: Boolean): Result<Unit>
}

/** Co seznam ukazuje: vlákna s nenulovým pořadím, nahoře nejvyšší (jako u chatů). */
fun Collection<DirectTopic>.shownDirectTopics(): List<DirectTopic> =
    filter { it.order != 0L }.sortedWith(compareByDescending<DirectTopic> { it.order }.thenByDescending { it.id })
