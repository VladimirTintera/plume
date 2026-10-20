package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.StateFlow

/**
 * Témata Uložených zpráv: Telegram je dělí podle toho, ODKUD se zpráva uložila (přeposlaná
 * z chatu, od skrytého autora), a vlastní zprávy jsou „Moje poznámky".
 */
sealed interface SavedTopicKind {
    /** Vlastní zprávy — jediné téma, do kterého se dá psát. */
    data object MyNotes : SavedTopicKind

    /** Přeposláno od uživatele, který si v soukromí zakázal odkaz na účet. */
    data object AuthorHidden : SavedTopicKind

    /** Uloženo z chatu [chatId] (soukromý chat, skupina, kanál). */
    data class FromChat(val chatId: Long) : SavedTopicKind
}

data class SavedTopic(
    val id: Long,
    val kind: SavedTopicKind,
    /** Název zdrojového chatu; u [SavedTopicKind.MyNotes] a skrytého autora `null` (pojmenuje UI). */
    val title: String? = null,
    val avatarFileId: Int? = null,
    /** Druh zdrojového chatu kvůli tvaru avataru; `null` mimo [SavedTopicKind.FromChat]. */
    val chatKind: ChatKind? = null,
    val isPinned: Boolean = false,
    /** Pořadí od TDLib, vyšší nahoře; 0 = téma v seznamu není. */
    val order: Long = 0,
    val lastMessage: TopicMessagePreview? = null,
)

/** Hlavička konverzace zúžené na téma Uložených zpráv. */
data class SavedTopicHeader(
    val id: Long,
    val kind: SavedTopicKind,
    val title: String? = null,
    val avatarFileId: Int? = null,
    val chatKind: ChatKind? = null,
)

val SavedTopic.header: SavedTopicHeader get() = SavedTopicHeader(id, kind, title, avatarFileId, chatKind)

/**
 * Témata Uložených zpráv. Hranice kolem `SavedMessagesTopic` v TDLib, jako [ForumRepository]
 * u skupin s tématy.
 */
interface SavedTopicsRepository {

    /**
     * Témata v pořadí od TDLib (připnutá nahoře); `null`, dokud se poprvé nenačtou.
     *
     * Chodí JEN updaty (`UpdateSavedMessagesTopic`) — TDLib nemá dotaz na seznam ani na jedno
     * téma, jen „načti další" ([load]).
     */
    val topics: StateFlow<List<SavedTopic>?>

    /** Dotáhne všechna témata (`loadSavedMessagesTopics` až do konce). */
    suspend fun load(): Result<Unit>

    suspend fun setPinned(topicId: Long, pinned: Boolean): Result<Unit>

    /**
     * Nové pořadí připnutých témat jedním voláním (`setPinnedSavedMessagesTopics`). Seznam je
     * NOVÝ STAV připnutých — co v něm chybí, TDLib odepne. Přeskládání potvrdí updaty pořadí.
     */
    suspend fun setPinnedOrder(topicIds: List<Long>): Result<Unit> = Result.failure(UnsupportedOperationException())

    /** Smaže všechny zprávy tématu z Uložených zpráv — nevratně. */
    suspend fun deleteHistory(topicId: Long): Result<Unit>
}

/**
 * Co seznam ukazuje: témata s nenulovým pořadím, nahoře nejvyšší. Nulové pořadí znamená, že
 * téma v seznamu není (prázdné po smazání, ještě nenačtené) — stejně jako `order` u chatů.
 */
fun Collection<SavedTopic>.shownSavedTopics(): List<SavedTopic> =
    filter { it.order != 0L }.sortedWith(compareByDescending<SavedTopic> { it.order }.thenByDescending { it.id })

/** Připnutá témata v pořadí seznamu. Přeskládat má smysl až od dvou. */
fun List<SavedTopic>.pinnedSavedTopics(): List<SavedTopic> = filter { it.isPinned }

/** Seznam s tématem [fromId] na místě tématu [toId]; neznámé id nechá seznam, jak je. */
fun List<SavedTopic>.movedSavedTopic(fromId: Long, toId: Long): List<SavedTopic> {
    val from = indexOfFirst { it.id == fromId }
    val to = indexOfFirst { it.id == toId }
    if (from < 0 || to < 0 || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * Pořadí k odeslání: skládané pořadí srovnané s tím, co je připnuté TEĎ ([current]). Mezitím
 * mohl jiné zařízení něco odepnout (vypadne) nebo připnout (zůstane připnuté a jde nahoru),
 * jako u připnutých chatů — TDLib by jinak téma chybějící v seznamu odepnul.
 */
fun savedPinnedOrderToSave(reordered: List<Long>, current: List<Long>): List<Long> {
    val kept = reordered.filter { it in current }
    return current.filterNot { it in kept } + kept
}
