package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatSummary

/**
 * Režim uspořádání připnutých chatů jedné složky: [chats] v pořadí, které uživatel právě skládá.
 * Složka se drží zvlášť — připnutí patří složce, a co se uloží, musí jít do té, ve které se
 * skládalo.
 */
data class PinnedReorder(
    val folder: ChatFolder,
    val chats: List<ChatSummary>,
    val busy: Boolean = false,
)

/**
 * Připnuté chaty seznamu v pořadí od TDLib. Sponzorovaný chat mezi ně nepatří — stojí nad nimi
 * zvlášť a připnutý není (viz [chatGroups]).
 */
fun pinnedChats(chats: List<ChatSummary>): List<ChatSummary> =
    chats.filter { it.isPinned && it.sponsor == null }

/** Pořadí jde měnit, jen když je co přeskládat. */
fun canReorderPinned(chats: List<ChatSummary>): Boolean = pinnedChats(chats).size >= 2

/**
 * Seznam s chatem [fromId] přesunutým na místo chatu [toId] (ostatní se posunou). Neznámé id
 * nechá seznam, jak je — tah mohl skončit nad položkou, která mezitím zmizela.
 */
fun List<ChatSummary>.movedChat(fromId: Long, toId: Long): List<ChatSummary> {
    val from = indexOfFirst { it.id == fromId }
    val to = indexOfFirst { it.id == toId }
    if (from < 0 || to < 0 || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/**
 * Pořadí k odeslání (`setPinnedChats`). TDLib bere seznam jako NOVÝ STAV připnutých: chat, který
 * v něm chybí, odepne, a chat navíc připne. Mezitím se ale připnuté mohly změnit jinde (jiné
 * zařízení), takže se skládané pořadí srovná s tím, co je připnuté TEĎ ([current]): odepnuté
 * vypadnou, nově připnuté zůstanou připnuté a jdou nahoru, kam je dává i server.
 */
fun pinnedOrderToSave(reordered: List<Long>, current: List<Long>): List<Long> {
    val kept = reordered.filter { it in current }
    return current.filterNot { it in kept } + kept
}
