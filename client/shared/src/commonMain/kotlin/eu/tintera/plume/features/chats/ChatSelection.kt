package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.canBeArchived
import eu.tintera.plume.core.telegram.ChatFolderTab
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.isPrivate

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
    /** `true` = připnout, `false` = odepnout. */
    val pin: Boolean = true,
    /**
     * Smazat / opustit jde, jen když ve výběru nejsou Uložené zprávy: jejich smazání by vymazalo
     * všechno uložené, a to hromadně, mimo nabídku nad nimi samými. Zšedne, nezmizí — jako archivace.
     */
    val canDelete: Boolean = true,
    /** Ve výběru je soukromý chat (smaže se u nás) — potvrzení to musí říct. */
    val deletesPrivate: Boolean = false,
    /** Ve výběru je skupina nebo kanál (z nich se odchází) — potvrzení to musí říct. */
    val leavesGroups: Boolean = false,
    /** Složky, do kterých jde výběr přidat; prázdné = položka se nenabízí. */
    val folders: List<ChatFolderTab> = emptyList(),
)

fun chatSelectionActions(
    selected: List<ChatSummary>,
    savedMessagesChatId: Long?,
    folders: List<ChatFolderTab> = emptyList(),
): ChatSelectionActions =
    ChatSelectionActions(
        archive = !selected.all { it.isArchived },
        canArchive = selected.isNotEmpty() &&
            (selected.all { it.isArchived } || selected.all { it.canBeArchived(savedMessagesChatId) }),
        mute = !selected.all { it.isMuted },
        read = selected.any { it.hasUnread },
        pin = !selected.all { it.isPinned },
        canDelete = selected.isNotEmpty() && selected.none { it.id == savedMessagesChatId },
        deletesPrivate = selected.any { it.isPrivate },
        leavesGroups = selected.any { !it.isPrivate },
        folders = folders,
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

/**
 * Připínají se v pořadí výběru, takže nahoře skončí ten vybraný naposled — jako by je uživatel
 * připínal po jednom z nabídky.
 */
fun ChatSelectionActions.pinTargets(selected: List<ChatSummary>): List<ChatSummary> =
    selected.filter { it.isPinned != pin }

/**
 * Všechny chaty v seznamu, které jde vybrat ([isSelectable]) — „Vybrat vše". Jen načtené: seznam
 * se dotahuje po stránkách a vybrat chaty, které uživatel neviděl, by bylo překvapení.
 */
fun ChatSelection.withAll(chats: List<ChatSummary>): ChatSelection =
    ChatSelection(ids + chats.filter { it.isSelectable() }.map { it.id })

/** Jsou vybrané všechny chaty, které jde vybrat? Pak „Vybrat vše" zšedne. */
fun ChatSelection.hasAll(chats: List<ChatSummary>): Boolean =
    chats.filter { it.isSelectable() }.all { it.id in ids }
