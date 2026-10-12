package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.ui.UiText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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
    /** Téma založil přihlášený účet — pak ho smí upravit, uzavřít i smazat bez práv správce. */
    val isOwn: Boolean = false,
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
    /** Text zprávy, nebo náš popis (Fotka); přeloží se až v řádku. */
    val text: UiText,
    val entities: List<MessageEntity> = emptyList(),
    /** Kdo psal; `null` u vlastní zprávy (řádek řekne „Vy") a u anonymního správce. */
    val senderName: String?,
    val isOutgoing: Boolean,
    val timestamp: Int,
)

/** Kde pokračuje další stránka témat — TDLib ji určuje trojicí z poslední stránky. */
data class ForumTopicsOffset(val date: Int, val messageId: Long, val topicId: Int)

data class ForumTopicsPage(
    val topics: List<ForumTopic>,
    val next: ForumTopicsOffset?,
    /**
     * Obecné téma, i skryté; `null` = na stránce nebylo. Skryté se v seznamu nekreslí
     * ([sortedTopics]), a správce ho přitom musí mít jak vrátit.
     */
    val general: ForumTopic? = null,
    /**
     * Připnutá témata v pořadí, v jakém je vrátil server (nahoře první). **`order` od TDLib
     * pořadí připnutých NENESE**: po `setPinnedForumTopics` zůstane u každého tématu stejné,
     * zatímco server vrací témata už v novém pořadí. Platí jen pro první stránku.
     */
    val pinnedOrder: List<Int> = emptyList(),
)

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

    /** Témata, jejichž název obsahuje [query] — hledá server, jen první stránka. */
    suspend fun search(chatId: Long, query: String): Result<List<ForumTopic>>

    /** Co smí přihlášený účet s tématy v téhle skupině ([topicRights]). */
    suspend fun rights(chatId: Long): Result<TopicRights>

    /**
     * [rights] napřed jednou, pak znovu při každé změně postavení nebo výchozích práv skupiny.
     * Správce může člena omezit, povýšit nebo skupině zakázat témata za běhu — práva přečtená
     * jen při otevření by pak nabízela tlačítko, které skončí chybou, nebo ho schovávala.
     */
    fun rightsUpdates(chatId: Long): Flow<TopicRights> = flow { rights(chatId).onSuccess { emit(it) } }

    /** Založí téma; vrací jeho id. [color] je jedna z [TOPIC_COLORS]. */
    suspend fun create(chatId: Long, name: String, color: Int): Result<Int>

    suspend fun rename(chatId: Long, topicId: Int, name: String): Result<Unit>

    suspend fun setClosed(chatId: Long, topicId: Int, closed: Boolean): Result<Unit>

    suspend fun setPinned(chatId: Long, topicId: Int, pinned: Boolean): Result<Unit>

    /** Nové pořadí připnutých témat, první nahoře. Musí obsahovat všechna připnutá. */
    suspend fun setPinnedOrder(chatId: Long, topicIds: List<Int>): Result<Unit>

    /** Smaže téma se všemi zprávami — nevratně, pro všechny. */
    suspend fun delete(chatId: Long, topicId: Int): Result<Unit>

    suspend fun setGeneralHidden(chatId: Long, hidden: Boolean): Result<Unit>

    /**
     * Zapne nebo vypne témata ve skupině. Smí jen vlastník supergroupy, a diskusní skupina
     * kanálu témata mít nemůže.
     */
    suspend fun setForum(chatId: Long, forum: Boolean): Result<Unit>
}

/**
 * Barvy ikony nového tématu — TDLib jiné nepřijme (dokumentace `createForumTopic`). Pořadí
 * je to, v jakém je nabízejí oficiální klienti.
 */
val TOPIC_COLORS: List<Int> = listOf(0x6FB9F0, 0xFFD67E, 0xCB86DB, 0x8EEE98, 0xFF93B2, 0xFB6F5F)

/** Delší název TDLib nepřijme. */
const val TOPIC_NAME_MAX = 128

/** Co smí přihlášený účet s tématy skupiny. */
data class TopicRights(
    /** Založit téma. */
    val canCreate: Boolean = false,
    /** Spravovat cizí témata: připnout, uzavřít, přejmenovat, skrýt obecné (`canManageTopics`). */
    val canManage: Boolean = false,
    /** Smazat cizí téma (`canDeleteMessages`). */
    val canDeleteAny: Boolean = false,
)

/**
 * Práva k tématům podle vlastního postavení. Čistá funkce kvůli testům, jako [writeAccess].
 *
 * - Vlastník smí všechno.
 * - Správce, co mu dovolují práva; zakládat témata smí správce s `canManageTopics` vždy,
 *   jinak jako člen.
 * - Člen jen zakládat, a jen když to skupina dovolí (`ChatPermissions.canCreateTopics`).
 * - Omezený člen taky jen zakládat, ale podle SVÝCH práv — výchozí právo skupiny pro něj
 *   neplatí. Omezení se ukládá celým objektem práv, takže „omezený" může mít dovoleno všechno.
 * - Kdo ve skupině není, nebo neznámé postavení: nic. Chybějící tlačítko nového tématu je
 *   menší škoda než tlačítko, po kterém přijde chyba.
 */
internal fun topicRights(status: OwnMemberStatus?, membersCanCreate: Boolean): TopicRights = when (status) {
    is OwnMemberStatus.Creator -> if (status.isMember) TopicRights(canCreate = true, canManage = true, canDeleteAny = true) else TopicRights()
    is OwnMemberStatus.Admin -> TopicRights(
        canCreate = status.canManageTopics || membersCanCreate,
        canManage = status.canManageTopics,
        canDeleteAny = status.canDeleteMessages,
    )
    OwnMemberStatus.Member -> TopicRights(canCreate = membersCanCreate)
    is OwnMemberStatus.Restricted -> TopicRights(canCreate = status.isMember && status.canCreateTopics)
    else -> TopicRights()
}

/** Co nabídnout nad jedním tématem. */
data class TopicActions(
    val pin: Boolean = false,
    val close: Boolean = false,
    val rename: Boolean = false,
    val delete: Boolean = false,
    val hideGeneral: Boolean = false,
)

/**
 * Akce nad tématem. Vlastní téma smí autor upravit, uzavřít i smazat; cizí jen správce.
 * **Obecné téma smazat nejde nikdy** (TDLib ho nemaže) a místo toho ho správce může skrýt.
 * Ztlumit smí každý — týká se jen jeho, proto tu není.
 */
fun topicActions(topic: ForumTopic, rights: TopicRights): TopicActions = TopicActions(
    pin = rights.canManage,
    close = rights.canManage || (topic.isOwn && !topic.isGeneral),
    rename = rights.canManage || (topic.isOwn && !topic.isGeneral),
    delete = !topic.isGeneral && (rights.canDeleteAny || topic.isOwn),
    hideGeneral = topic.isGeneral && rights.canManage,
)

/**
 * Seznam témat, jak se ukazuje: sestupně podle pořadí od TDLib, bez skrytého obecného tématu
 * (Telegram ho schová, když ho správce skryje) a každé téma jednou.
 */
fun List<ForumTopic>.sortedTopics(pinnedOrder: List<Int> = emptyList()): List<ForumTopic> =
    filterNot { it.isHidden }.distinctBy { it.id }.sortedWith(
        compareByDescending<ForumTopic> { it.isPinned }
            // Připnutá podle serveru; neznámé (právě připnuté) nahoru, kam je server dává taky.
            .thenBy { if (it.isPinned) pinnedOrder.indexOf(it.id) else 0 }
            .thenByDescending { it.order },
    )

/**
 * Seznam s tématem nahrazeným (nebo přidaným) podle [topic]. Změna nese celé téma, takže se
 * nic neslučuje — nahradí se a seznam se znovu seřadí (nová zpráva téma posune nahoru).
 * Téma, které se právě skrylo, ze seznamu zmizí.
 */
fun List<ForumTopic>.withTopic(topic: ForumTopic, pinnedOrder: List<Int> = emptyList()): List<ForumTopic> =
    (filterNot { it.id == topic.id } + topic).sortedTopics(pinnedOrder)

/** Připnutá témata v pořadí, v jakém je seznam ukazuje (nahoře první). */
fun List<ForumTopic>.pinnedTopics(pinnedOrder: List<Int> = emptyList()): List<ForumTopic> =
    sortedTopics(pinnedOrder).filter { it.isPinned }

/** Pořadí jde měnit jen s právem připínat a jen když je co přeskládat. */
fun canReorderPinned(topics: List<ForumTopic>, rights: TopicRights): Boolean =
    rights.canManage && topics.pinnedTopics().size >= 2

/**
 * Seznam s tématem [fromId] přesunutým na místo tématu [toId] (ostatní se posunou). Neznámé
 * id nechá seznam, jak je — tah mohl skončit nad položkou, která mezitím zmizela.
 */
fun List<ForumTopic>.moved(fromId: Int, toId: Int): List<ForumTopic> {
    val from = indexOfFirst { it.id == fromId }
    val to = indexOfFirst { it.id == toId }
    if (from < 0 || to < 0 || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/** Seznam bez tématu [topicId] — smazané téma (dočíst už nejde). */
fun List<ForumTopic>.withoutTopic(topicId: Int): List<ForumTopic> = filterNot { it.id == topicId }

/**
 * Přidá stránku k načteným tématům. Tentýž seznam se mezitím mohl změnit updatem, takže se
 * téma, které už je, NEPŘEPISUJE starší verzí ze stránky.
 */
fun List<ForumTopic>.withPage(page: List<ForumTopic>, pinnedOrder: List<Int> = emptyList()): List<ForumTopic> {
    val known = mapTo(HashSet()) { it.id }
    return (this + page.filterNot { it.id in known }).sortedTopics(pinnedOrder)
}
