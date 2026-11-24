package eu.tintera.plume.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.adaptive.ListPaneToggle
import eu.tintera.plume.core.navigation.ArchiveConversationRoute
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.ui.KeyboardShortcut
import eu.tintera.plume.core.ui.KeyboardShortcuts
import eu.tintera.plume.features.conversation.ConversationSearchRequests
import org.koin.compose.koinInject
import eu.tintera.plume.core.accounts.LocalAccount

/**
 * Zkratky hledání z klávesnice ([KeyboardShortcuts]). Shell, protože zná zásobník Chatů:
 * - **Ctrl+K** otevře hledání v seznamu chatů stejnou cestou jako hashtag z konverzace —
 *   v dvoupanelu zůstane konverzace vpravo, na telefonu se zásobník srovná na seznam.
 * - **Ctrl+F** otevře hledání v konverzaci, která je navrchu (i v archivu), stejnou cestou
 *   jako „Hledat" z profilu. Bez otevřené konverzace nedělá nic.
 * - **Ctrl+Shift+S** (macOS ⌃⌘S) sbalí nebo rozbalí seznam chatů vedle konverzace
 *   ([listToggle], totéž co tlačítko u pilulky). Jinde nedělá nic.
 *
 * Přepínání chatů (Alt+šipky) řeší seznam chatů — jen on zná pořadí, jak ho uživatel vidí.
 */
@Composable
internal fun ShellShortcuts(navigation: AppNavigation, chatsStack: List<NavKey>, twoPane: Boolean, listToggle: ListPaneToggle?) {
    val shortcuts = koinInject<KeyboardShortcuts>()
    // Hledání v účtu shellu (seznam chatů je jeho).
    val slot = LocalAccount.current.slot
    val searchRequests = koinInject<ConversationSearchRequests>()
    val currentStack by rememberUpdatedState(chatsStack)
    val currentTwoPane by rememberUpdatedState(twoPane)
    val currentListToggle by rememberUpdatedState(listToggle)

    LaunchedEffect(shortcuts) {
        shortcuts.shortcuts.collect { shortcut ->
            when (shortcut) {
                KeyboardShortcut.SearchChats -> navigation.openSearch(slot, query = "", keepConversation = currentTwoPane)
                KeyboardShortcut.SearchInChat -> {
                    if (navigation.section != ChatsRoute) return@collect
                    currentStack.topConversation()?.let(searchRequests::request)
                }
                KeyboardShortcut.ToggleChatList -> currentListToggle?.toggle?.invoke()
                KeyboardShortcut.NextChat, KeyboardShortcut.PreviousChat -> Unit
            }
        }
    }
}

/** Konverzace navrchu zásobníku Chatů; profil nebo jiná obrazovka nad ní ji neschová. */
private fun List<NavKey>.topConversation(): Long? = asReversed().firstNotNullOfOrNull {
    (it as? ConversationRoute)?.chatId ?: (it as? ArchiveConversationRoute)?.chatId
}
