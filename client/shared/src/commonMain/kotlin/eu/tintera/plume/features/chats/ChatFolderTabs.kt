package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.ArchiveUnread
import eu.tintera.plume.core.telegram.ChatFolders
import eu.tintera.plume.core.telegram.MessageEntity

/**
 * Položka řádku složek nad seznamem chatů: celý seznam ([id] `null`, název dodá UI), nebo složka
 * Telegramu. [unread] je počet nepřečtených NEZTLUMENÝCH chatů — jako u oficiálních klientů:
 * složku plnou ztlumených kanálů by jinak číslo zdobilo napořád.
 */
data class FolderTab(
    val id: Int?,
    val title: String = "",
    val titleEntities: List<MessageEntity> = emptyList(),
    val unread: Int = 0,
    val selected: Boolean = false,
)

/**
 * Vybraná složka, jen pokud pořád existuje. Smazaná (třeba na jiném zařízení) = celý seznam.
 */
internal fun selectedFolderId(selected: Int?, folders: ChatFolders): Int? =
    selected?.takeIf { id -> folders.folders.any { it.id == id } }

/**
 * Řádek složek. Bez složek je prázdný a nekreslí se — „Všechny chaty" samo o sobě nic nevybírá.
 * Celý seznam stojí tam, kam ho uživatel v Telegramu přesunul ([ChatFolders.mainPosition]; bez
 * Premium vždycky první).
 */
internal fun folderTabs(folders: ChatFolders, unread: Map<Int, ArchiveUnread>, selected: Int?): List<FolderTab> {
    if (folders.folders.isEmpty()) return emptyList()
    val tabs = folders.folders.map { folder ->
        FolderTab(
            id = folder.id,
            title = folder.title,
            titleEntities = folder.titleEntities,
            unread = unread[folder.id]?.unmutedChats ?: 0,
            selected = folder.id == selected,
        )
    }
    val all = FolderTab(id = null, selected = selected == null)
    val at = folders.mainPosition.coerceIn(0, tabs.size)
    return tabs.take(at) + all + tabs.drop(at)
}
