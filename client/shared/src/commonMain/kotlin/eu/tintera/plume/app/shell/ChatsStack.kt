package eu.tintera.plume.app.shell

import eu.tintera.plume.core.navigation.MediaRoute
import eu.tintera.plume.core.navigation.ChatProfileRoute
import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.navigation.ArchiveChatProfileRoute
import eu.tintera.plume.core.navigation.ArchiveConversationRoute
import eu.tintera.plume.core.navigation.ArchiveRoute
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.MemberRightsRoute
import eu.tintera.plume.core.navigation.EditChatRoute
import eu.tintera.plume.core.navigation.ArchiveMemberRightsRoute
import eu.tintera.plume.core.navigation.ArchiveEditChatRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.navigation.ArchiveForumTopicsRoute
import eu.tintera.plume.core.navigation.ForumTopicsRoute
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
    // Seznam témat stojí na místě konverzace (skupina s tématy se otevírá jím).
    ChatFolder.Main -> any { it is ConversationRoute || it is ForumTopicsRoute }
    ChatFolder.Archive -> any { it is ArchiveConversationRoute || it is ArchiveForumTopicsRoute }
}

/**
 * Zpět z archivu odebere CELOU jeho větev, ne jen seznam: archiv má vlastní detail panel,
 * takže by po něm v zásobníku zůstala konverzace bez seznamu, ke kterému patří. Hlavní seznam
 * se tím vrátí i s tím, co v něm bylo otevřené.
 */
internal fun MutableList<NavKey>.closeArchive() {
    removeAll {
        it is ArchiveRoute || it is ArchiveConversationRoute || it is ArchiveForumTopicsRoute ||
            it is ArchiveChatProfileRoute
    }
}

/**
 * Chat změnil složku, takže jeho otevřená konverzace v té OPUŠTĚNÉ se zavře — jinak by
 * v archivu zůstal viset chat, který v něm už není (a naopak). Kam se přesunul, říká
 * [ArchiveChange.archived]; zavírá se vždycky ta druhá větev, takže jedna funkce obslouží
 * hlavní seznam i archiv.
 */
internal fun MutableList<NavKey>.closeConversationOf(change: ArchiveChange) {
    if (change.archived) {
        removeAll {
            (it as? ConversationRoute)?.chatId == change.chatId || (it as? ForumTopicsRoute)?.chatId == change.chatId
        }
    } else {
        removeAll {
            (it as? ArchiveConversationRoute)?.chatId == change.chatId ||
                (it as? ArchiveForumTopicsRoute)?.chatId == change.chatId
        }
    }
}

/**
 * Chat otevřený z archivu se vrátil mezi chaty (pruh nad konverzací u automaticky
 * archivovaného chatu), takže se s ním PŘESTĚHUJE i konverzace: archivní větev se zavře
 * a chat se otevře v hlavním seznamu. Zavřít ji jen jako u [closeConversationOf] by uživatele
 * vyhodilo z chatu, kterému právě dal najevo, že o něj stojí; nechat ji v archivu by znamenalo
 * konverzaci vedle seznamu, ve kterém chat už není.
 *
 * Konverzace otevřená v hlavním seznamu nahradí tu, která tam byla — stejně jako výběr chatu
 * ze seznamu.
 */
internal fun MutableList<NavKey>.moveConversationToMain(chatId: Long) {
    if (none { (it as? ArchiveConversationRoute)?.chatId == chatId }) return
    removeAll {
        when (it) {
            // Celá archivní větev, i úpravy a práva členů nad profilem.
            is ArchiveRoute, is ArchiveConversationRoute, is ArchiveForumTopicsRoute, is ArchiveChatProfileRoute,
            is ArchiveEditChatRoute, is ArchiveMemberRightsRoute -> true
            // Detail hlavního seznamu patřil jinému chatu; nový chat ho nahradí celý, jinak by
            // zpět z něj vedlo do profilu toho předchozího.
            is ConversationRoute, is ForumTopicsRoute, is ChatProfileRoute, is EditChatRoute, is MemberRightsRoute -> true
            else -> false
        }
    }
    add(ConversationRoute(chatId))
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
            is ForumTopicsRoute -> it.chatId == chatId
            is ArchiveForumTopicsRoute -> it.chatId == chatId
            is ChatProfileRoute -> it.chatId == chatId
            is ArchiveChatProfileRoute -> it.chatId == chatId
            is MediaRoute -> it.chatId == chatId
            else -> false
        }
    }
}
