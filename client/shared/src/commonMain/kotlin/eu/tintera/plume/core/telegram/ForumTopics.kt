package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow

/**
 * Téma ve skupině s tématy (fórum). Skupina je pak seznam témat a každé téma vlastní konverzace
 * — zprávy do něj jdou s `MessageTopicForum`, jinak by skončily v obecném tématu.
 */
data class ForumTopic(
    val chatId: Long,
    val id: Int,
    val name: String,
    val icon: TopicIcon,
    /** Obecné téma: to, kam padá všechno, co téma nemá. Má vlastní ikonu (#). */
    val isGeneral: Boolean,
    /** Uzavřené téma: psát do něj smí jen správce s právem spravovat témata. */
    val isClosed: Boolean,
    val isPinned: Boolean,
    /** Pořadí od TDLib; témata se řadí sestupně (viz [sortedTopics]). */
    val order: Long,
    val unreadCount: Int = 0,
    val unreadMentionCount: Int = 0,
    val unreadReactionCount: Int = 0,
    val isMuted: Boolean = false,
    val lastMessage: TopicMessagePreview? = null,
    val draft: ChatDraft? = null,
    /** Skryté obecné téma — správce ho schoval, v seznamu se nekreslí. */
    val isHidden: Boolean = false,
) {
    val hasUnread: Boolean get() = unreadCount > 0
}

/**
 * Ikona tématu: vlastní emoji, a bez něj barevný tvar s počátečním písmenem. [color] je RGB
 * (24 bitů) z palety, kterou vybírá server.
 */
data class TopicIcon(val color: Int, val customEmojiId: Long?)

/** Náhled poslední zprávy tématu, jako v seznamu chatů — jen s odesílatelem, protože píše víc lidí. */
data class TopicMessagePreview(
    val text: String,
    val entities: List<MessageEntity> = emptyList(),
    /** Kdo psal; `null` u vlastní zprávy (řádek řekne „Vy") a u anonymního správce. */
    val senderName: String?,
    val isOutgoing: Boolean,
    val timestamp: Int,
)

/** Kde pokračuje další stránka témat — TDLib ji určuje trojicí z poslední stránky. */
data class ForumTopicsOffset(val date: Int, val messageId: Long, val topicId: Int)

data class ForumTopicsPage(val topics: List<ForumTopic>, val next: ForumTopicsOffset?)

/** Hlavička konverzace tématu: co se v ní ukáže místo názvu a fotky skupiny. */
data class TopicHeader(
    val id: Int,
    val name: String,
    val icon: TopicIcon,
    val isGeneral: Boolean,
    val isClosed: Boolean,
)

val ForumTopic.header: TopicHeader get() = TopicHeader(id, name, icon, isGeneral, isClosed)

/**
 * Témata skupiny. Hranice kolem fór v TDLib, stejně jako [ChatRepository] kolem seznamu chatů.
 */
interface ForumRepository {

    /** Stránka témat od nejnovějšího; [offset] `null` = první. */
    suspend fun topics(chatId: Long, offset: ForumTopicsOffset? = null): Result<ForumTopicsPage>

    /** Jedno téma, aktuálně — po každé změně z [topicChanges]. */
    suspend fun topic(chatId: Long, topicId: Int): Result<ForumTopic>

    /**
     * Id témat, která se změnila: nová zpráva, přečtení, přejmenování, uzavření, připnutí.
     * Nese jen id — co se změnilo, se přečte znovu ([topic]), jako u připnutých zpráv.
     */
    fun topicChanges(chatId: Long): Flow<Int>
}

/**
 * Seznam témat, jak se ukazuje: sestupně podle pořadí od TDLib, bez skrytého obecného tématu
 * (Telegram ho schová, když ho správce skryje) a každé téma jednou.
 */
fun List<ForumTopic>.sortedTopics(): List<ForumTopic> =
    filterNot { it.isHidden }.distinctBy { it.id }.sortedByDescending { it.order }

/**
 * Seznam s tématem nahrazeným (nebo přidaným) podle [topic]. Změna nese celé téma, takže se
 * nic neslučuje — nahradí se a seznam se znovu seřadí (nová zpráva téma posune nahoru).
 * Téma, které se právě skrylo, ze seznamu zmizí.
 */
fun List<ForumTopic>.withTopic(topic: ForumTopic): List<ForumTopic> =
    (filterNot { it.id == topic.id } + topic).sortedTopics()

/** Seznam bez tématu [topicId] — smazané téma (dočíst už nejde). */
fun List<ForumTopic>.withoutTopic(topicId: Int): List<ForumTopic> = filterNot { it.id == topicId }

/**
 * Přidá stránku k načteným tématům. Tentýž seznam se mezitím mohl změnit updatem, takže se
 * téma, které už je, NEPŘEPISUJE starší verzí ze stránky.
 */
fun List<ForumTopic>.withPage(page: List<ForumTopic>): List<ForumTopic> {
    val known = mapTo(HashSet()) { it.id }
    return (this + page.filterNot { it.id in known }).sortedTopics()
}
