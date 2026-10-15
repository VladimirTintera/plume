package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.canBeArchived
import eu.tintera.plume.core.telegram.ChatSummary

/**
 * Výběr víc chatů v seznamu: [ids] vybraných v pořadí, v jakém je uživatel vybíral. Režim
 * výběru běží, dokud je výběr neprázdný — odebrání posledního chatu ho ukončí, stejně jako
 * u výběru zpráv v konverzaci.
 */
data class ChatSelection(val ids: Set<Long>) {
    val count: Int get() = ids.size

    /** Přepne [chatId]; `null` = vybráno nic, režim končí. */
    fun toggled(chatId: Long): ChatSelection? {
        val next = if (chatId in ids) ids - chatId else ids + chatId
        return next.takeIf { it.isNotEmpty() }?.let(::ChatSelection)
    }

    /**
     * Jen chaty, které seznam pořád ukazuje. Chat mohl mezitím odejít (archivace na jiném
     * zařízení, opuštěná skupina) a akce nad ním by mířila mimo to, co uživatel vidí.
     */
    fun within(chats: List<ChatSummary>): ChatSelection? {
        val shown = chats.mapTo(HashSet()) { it.id }
        return ids.filter { it in shown }.toSet().takeIf { it.isNotEmpty() }?.let(::ChatSelection)
    }
}

/**
 * Vybrat jde jen chat, se kterým akce lišty něco udělají: sponzorovaný chat (od proxy, oznámení)
 * uživatelův není a má vlastní nabídku.
 */
fun ChatSummary.isSelectable(): Boolean = sponsor == null

/**
 * Co lišta výběru udělá. Každá akce je přepínač podle stavu VŠECH vybraných — stejně jako
 * v Gmailu: když jsou všechny ztlumené, nabídne se zrušení ztlumení, jinak ztlumení (i těch, co
 * ztlumené už jsou, to nic nezmění).
 */
data class ChatSelectionActions(
    /** `true` = do archivu, `false` = zpátky mezi chaty. */
    val archive: Boolean,
    /**
     * Uložené zprávy a chat Telegramu archivovat nejdou (TDLib to odmítne, viz `canBeArchived`),
     * takže s nimi ve výběru archivace zšedne. Vracení z archivu zůstává.
     */
    val canArchive: Boolean,
    /** `true` = ztlumit, `false` = zrušit ztlumení. */
    val mute: Boolean,
    /** `true` = přečíst, `false` = označit jako nepřečtené. */
    val read: Boolean,
)

fun chatSelectionActions(selected: List<ChatSummary>, savedMessagesChatId: Long?): ChatSelectionActions =
    ChatSelectionActions(
        archive = !selected.all { it.isArchived },
        canArchive = selected.isNotEmpty() &&
            (selected.all { it.isArchived } || selected.all { it.canBeArchived(savedMessagesChatId) }),
        mute = !selected.all { it.isMuted },
        read = selected.any { it.hasUnread },
    )

/**
 * Chaty, kterých se akce opravdu týká. Posílat archivaci chatu, který v archivu už je, nebo číst
 * přečtený, by byla jen volání navíc — a snackbar s „Vrátit" by pak vracel i to, co se nezměnilo.
 */
fun ChatSelectionActions.archiveTargets(selected: List<ChatSummary>): List<ChatSummary> =
    selected.filter { it.isArchived != archive }

fun ChatSelectionActions.muteTargets(selected: List<ChatSummary>): List<ChatSummary> =
    selected.filter { it.isMuted != mute }

fun ChatSelectionActions.readTargets(selected: List<ChatSummary>): List<ChatSummary> =
    selected.filter { it.hasUnread == read }
