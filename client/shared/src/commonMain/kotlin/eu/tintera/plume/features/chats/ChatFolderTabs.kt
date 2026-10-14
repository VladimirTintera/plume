package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.ArchiveUnread
import eu.tintera.plume.core.telegram.ChatFolderMembership
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
    /** Ikona složky od TDLib — jen pro prázdný stav vybrané složky, řádek ikony nekreslí. */
    val iconName: String? = null,
)

/**
 * Vybraná složka, jen pokud pořád existuje. Smazaná (třeba na jiném zařízení) = celý seznam.
 */
internal fun selectedFolderId(selected: Int?, folders: ChatFolders): Int? =
    selected?.takeIf { id -> folders.folders.any { it.id == id } }

/**
 * Řádek složek. Celý seznam stojí tam, kam ho uživatel v Telegramu přesunul
 * ([ChatFolders.mainPosition]; bez Premium vždycky první). **Bez složek je řádek prázdný a nekreslí
 * se**: samotné „Všechny chaty" není volba, jen místo navíc nad seznamem. Doporučené složky
 * zůstávají v Nastavení → Chaty → Složky chatů, a jakmile tam uživatel nějakou přidá, řádek
 * se objeví i s „+".
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
            iconName = folder.iconName,
        )
    }
    val all = FolderTab(id = null, selected = selected == null)
    val at = folders.mainPosition.coerceIn(0, tabs.size)
    return tabs.take(at) + all + tabs.drop(at)
}

/**
 * Složky pro „Přidat do složky": ty, ve kterých chat je, a ty, kam ho jde přidat, v pořadí
 * složek. Do ostatních by ho server nepustil (typicky tajný chat do sdílené složky).
 */
internal fun folderChoices(folders: ChatFolders, membership: ChatFolderMembership?): List<FolderChoice> {
    membership ?: return emptyList()
    return folders.folders
        .filter { it.id in membership.member || it.id in membership.canAdd }
        .map { FolderChoice(it.id, it.title, member = it.id in membership.member) }
}
