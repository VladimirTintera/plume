package eu.tintera.plume.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.navigation.ArchiveConversationRoute
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.ui.KeyboardShortcut
import eu.tintera.plume.core.ui.KeyboardShortcuts
import eu.tintera.plume.features.conversation.ConversationSearchRequests
import org.koin.compose.koinInject

/**
 * Zkratky hledání z klávesnice ([KeyboardShortcuts]). Shell, protože zná zásobník Chatů:
 * - **Ctrl+K** otevře hledání v seznamu chatů stejnou cestou jako hashtag z konverzace —
 *   v dvoupanelu zůstane konverzace vpravo, na telefonu se zásobník srovná na seznam.
 * - **Ctrl+F** otevře hledání v konverzaci, která je navrchu (i v archivu), stejnou cestou
 *   jako „Hledat" z profilu. Bez otevřené konverzace nedělá nic.
 *
 * Přepínání chatů (Alt+šipky) řeší seznam chatů — jen on zná pořadí, jak ho uživatel vidí.
 */
@Composable
internal fun ShellShortcuts(navigation: AppNavigation, chatsStack: List<NavKey>, twoPane: Boolean) {
    val shortcuts = koinInject<KeyboardShortcuts>()
    val searchRequests = koinInject<ConversationSearchRequests>()
    val currentStack by rememberUpdatedState(chatsStack)
    val currentTwoPane by rememberUpdatedState(twoPane)

    LaunchedEffect(shortcuts) {
        shortcuts.shortcuts.collect { shortcut ->
            when (shortcut) {
                KeyboardShortcut.SearchChats -> navigation.openSearch(query = "", keepConversation = currentTwoPane)
                KeyboardShortcut.SearchInChat -> {
                    if (navigation.section != ChatsRoute) return@collect
                    currentStack.topConversation()?.let(searchRequests::request)
                }
                KeyboardShortcut.NextChat, KeyboardShortcut.PreviousChat -> Unit
            }
        }
    }
}

/** Konverzace navrchu zásobníku Chatů; profil nebo jiná obrazovka nad ní ji neschová. */
private fun List<NavKey>.topConversation(): Long? = asReversed().firstNotNullOfOrNull {
    (it as? ConversationRoute)?.chatId ?: (it as? ArchiveConversationRoute)?.chatId
}
