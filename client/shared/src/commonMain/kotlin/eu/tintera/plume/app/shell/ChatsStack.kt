package eu.tintera.plume.app.shell

import eu.tintera.plume.core.navigation.MediaRoute
import eu.tintera.plume.core.navigation.ChatProfileRoute
import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.navigation.ArchiveChatProfileRoute
import eu.tintera.plume.core.navigation.ArchiveConversationRoute
import eu.tintera.plume.core.navigation.ArchiveRoute
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.features.chats.ArchiveChange

/*
 * Pravidla nad zásobníkem Chatů jako čisté funkce, ne lambdy v `AppShell`.
 *
 * Zásobník nese dvě list-detail větve naráz — hlavní seznam a archiv (viz ARCHIVE_SCENE) —
 * takže skoro každá operace musí vědět, do KTERÉ z nich patří. Uvnitř lambdy v `entryProvider`
 * se to neotestuje a hraniční kombinace (prázdný archiv nad otevřenou konverzací hlavního
 * seznamu, odarchivování právě otevřeného chatu) se na zařízení poznají až špatným rozvržením.
 * Testy jsou v `ChatsStackTest`.
 */

/**
 * Složka, jejíž seznam je vlevo vidět: rozhoduje POSLEDNÍ list panel v zásobníku, protože
 * scéna kreslí vlevo ten.
 */
internal fun List<NavKey>.shownChatFolder(): ChatFolder =
    if (lastOrNull { it == ChatsRoute || it == ArchiveRoute } == ArchiveRoute) {
        ChatFolder.Archive
    } else {
        ChatFolder.Main
    }

/**
 * Je otevřená konverzace v [folder]?
 *
 * Ptát se na obě větve naráz nejde: otevřená konverzace hlavního seznamu o archivu nic neříká.
 * Nad prázdným archivem by kvůli ní zůstal dvoupanel a vedle prázdného stavu by stál
 * placeholder „vyber chat vlevo" — dva prázdné stavy, z nichž pravý radí něco, co udělat nejde
 * (viz `nothingToPick` v `AppShell`).
 */
internal fun List<NavKey>.hasOpenConversation(folder: ChatFolder): Boolean = when (folder) {
    ChatFolder.Main -> any { it is ConversationRoute }
    ChatFolder.Archive -> any { it is ArchiveConversationRoute }
}

/**
 * Zpět z archivu odebere CELOU jeho větev, ne jen seznam: archiv má vlastní detail panel,
 * takže by po něm v zásobníku zůstala konverzace bez seznamu, ke kterému patří. Hlavní seznam
 * se tím vrátí i s tím, co v něm bylo otevřené.
 */
internal fun MutableList<NavKey>.closeArchive() {
    removeAll { it is ArchiveRoute || it is ArchiveConversationRoute || it is ArchiveChatProfileRoute }
}

/**
 * Chat změnil složku, takže jeho otevřená konverzace v té OPUŠTĚNÉ se zavře — jinak by
 * v archivu zůstal viset chat, který v něm už není (a naopak). Kam se přesunul, říká
 * [ArchiveChange.archived]; zavírá se vždycky ta druhá větev, takže jedna funkce obslouží
 * hlavní seznam i archiv.
 */
internal fun MutableList<NavKey>.closeConversationOf(change: ArchiveChange) {
    if (change.archived) {
        removeAll { (it as? ConversationRoute)?.chatId == change.chatId }
    } else {
        removeAll { (it as? ArchiveConversationRoute)?.chatId == change.chatId }
    }
}

/**
 * Chat je pryč (smazaný, opuštěný): zavře se všechno, co ho ukazuje — konverzace, profil
 * a prohlížeč jeho médií, v hlavním seznamu i v archivu. Zůstat by po něm nemělo nic, co by
 * šlo zpátky otevřít jen proto, aby se ukázalo prázdné.
 */
internal fun MutableList<NavKey>.closeChat(chatId: Long) {
    removeAll {
        when (it) {
            is ConversationRoute -> it.chatId == chatId
            is ArchiveConversationRoute -> it.chatId == chatId
            is ChatProfileRoute -> it.chatId == chatId
            is ArchiveChatProfileRoute -> it.chatId == chatId
            is MediaRoute -> it.chatId == chatId
            else -> false
        }
    }
}
