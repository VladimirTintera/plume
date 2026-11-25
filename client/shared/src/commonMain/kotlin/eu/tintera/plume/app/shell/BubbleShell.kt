package eu.tintera.plume.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import eu.tintera.plume.app.root.presentation.theme.ChatColorTheme
import eu.tintera.plume.features.conversation.ConversationReplyRequests
import eu.tintera.plume.features.conversation.SponsoredLinkRequests
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.navigation.MediaRoute
import eu.tintera.plume.core.navigation.NavSavedStateConfiguration
import eu.tintera.plume.core.navigation.SAVED_ROUTES_VERSION
import androidx.compose.runtime.key
import eu.tintera.plume.core.accounts.Accounts
import eu.tintera.plume.core.accounts.AccountScope
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.features.conversation.ConversationNavigation
import eu.tintera.plume.features.conversation.ConversationScreen
import eu.tintera.plume.core.ui.LocalArticleMediaFlight
import eu.tintera.plume.core.ui.LocalPollMediaFlight
import eu.tintera.plume.features.media.MediaViewerScreen
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Jedna konverzace v bublině (Android 11+, `BubbleActivity`): konverzace a nad ní prohlížeč médií.
 *
 * **Vlastní malý zásobník, ne [AppShell].** Konverzace má přes dvacet napojení na shell (profil,
 * přeposlání, mini aplikace, ankety, odkazy…) a navigační singletony (`ChatOpenRequests`,
 * `KeyboardShortcuts`) jsou na proces — druhá kopie shellu v bublině by si s hlavní aktivitou
 * přebírala požadavky. V bublině proto zůstává jen to, co k rychlé odpovědi patří: zprávy,
 * psaní, přílohy a média. Všechno ostatní otevře plnou aplikaci ([onOpenInApp]) — u akcí nad
 * konverzací (profil, přeposlání…) na této konverzaci, u odkazu na jiný chat na něm.
 *
 * `NavDisplay` tu je kvůli sdíleným přechodům: konverzace i prohlížeč čtou
 * `LocalNavAnimatedContentScope`, který mimo něj neexistuje.
 */
@Composable
fun BubbleShell(slot: AccountSlot, chatId: Long, onOpenInApp: (chatId: Long) -> Unit, onClose: () -> Unit) {
    val accounts = koinInject<Accounts>()
    // Účet mezitím odhlášený: bublina systému dožije, není co v ní ukázat.
    val account = accounts.find(slot) ?: return
    AccountScope(account) { BubbleContent(accounts, slot, chatId, onOpenInApp, onClose) }
}

@Composable
private fun BubbleContent(accounts: Accounts, slot: AccountSlot, chatId: Long, onOpenInApp: (chatId: Long) -> Unit, onClose: () -> Unit) {
    val backStack = key(SAVED_ROUTES_VERSION) {
        rememberNavBackStack(NavSavedStateConfiguration, ConversationRoute(slot, chatId))
    }
    val replyRequests = koinInject<ConversationReplyRequests>()
    val sponsoredLinks = koinInject<SponsoredLinkRequests>()
    val decorators = listOf<NavEntryDecorator<NavKey>>(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    )
    val provider = entryProvider<NavKey> {
        entry<ConversationRoute> { route ->
            ChatColorTheme(route.chatId, onBack = onClose) {
                ConversationScreen(
                    viewModel = koinViewModel(
                        key = "bubble-conversation-${route.chatId}",
                        parameters = { parametersOf(route.chatId, route.messageId, route.threadId, route.topicId, route.savedTopicId) },
                    ),
                    navigation = ConversationNavigation(
                        // Šipka zpět v bublině: konverzace je kořen, pod ní nic není — bublinu sbalí.
                        onBack = onClose,
                        onOpenMedia = { messageId -> backStack.add(MediaRoute(route.slot, route.chatId, messageId)) },
                        onOpenMediaAt = { messageId, seconds -> backStack.add(MediaRoute(route.slot, route.chatId, messageId, startSeconds = seconds)) },
                        onOpenPollMedia = { messageId, index -> backStack.add(MediaRoute(route.slot, route.chatId, messageId, pollMedia = index)) },
                        onOpenArticleMedia = { messageId, index -> backStack.add(MediaRoute(route.slot, route.chatId, messageId, articleMedia = index)) },
                        onOpenSponsoredMedia = { messageId -> backStack.add(MediaRoute(route.slot, route.chatId, messageId, sponsored = true)) },
                        onForward = { onOpenInApp(route.chatId) },
                        onOpenChat = { target -> onOpenInApp(target.chatId) },
                        onSearch = { onOpenInApp(route.chatId) },
                        onOpenProfile = { onOpenInApp(route.chatId) },
                        onOpenUserProfile = { userChatId -> onOpenInApp(userChatId) },
                        onCreatePoll = { onOpenInApp(route.chatId) },
                        onCreateChecklist = { onOpenInApp(route.chatId) },
                        onOpenWebApp = { onOpenInApp(route.chatId) },
                        onOpenBotShare = { onOpenInApp(route.chatId) },
                        onAddBotToGroup = { onOpenInApp(route.chatId) },
                        onOpenScreen = { onOpenInApp(route.chatId) },
                        onSwitchInline = { onOpenInApp(route.chatId) },
                        onAddMembers = { onOpenInApp(route.chatId) },
                        onShareContact = { onOpenInApp(route.chatId) },
                        onEditChat = { onOpenInApp(route.chatId) },
                        onOpenJoinRequests = { onOpenInApp(route.chatId) },
                    ),
                )
            }
        }
        entry<MediaRoute> { route ->
            CompositionLocalProvider(
                LocalPollMediaFlight provides route.pollMedia,
                LocalArticleMediaFlight provides route.articleMedia,
            ) {
                MediaViewerScreen(
                    viewModel = koinViewModel(
                        key = "bubble-media-${route.chatId}-${route.messageId}-${route.pollMedia}-${route.articleMedia}-${route.sponsored}",
                        parameters = {
                            parametersOf(
                                route.chatId,
                                route.messageId,
                                route.pollMedia,
                                route.profilePhotos,
                                route.articleMedia,
                                route.storyId,
                                route.sponsored,
                            )
                        },
                    ),
                    onBack = { backStack.remove(route) },
                    onForward = { onOpenInApp(route.chatId) },
                    // Prohlížeč leží vždy přímo nad konverzací téhož chatu, takže je kam odpovědět.
                    onReply = { messageId ->
                        replyRequests.request(route.chatId, messageId)
                        backStack.remove(route)
                    },
                    startSeconds = route.startSeconds,
                    onOpenSponsoredLink = { url ->
                        sponsoredLinks.request(route.chatId, url)
                        backStack.remove(route)
                    },
                )
            }
        }
    }.inRouteAccount(accounts)
    val transitions = rememberNavTransitions(chatContainer = false)
    NavDisplay(
        entries = rememberDecoratedNavEntries(backStack, decorators, provider),
        transitionSpec = transitions.forward(),
        popTransitionSpec = transitions.pop(),
        predictivePopTransitionSpec = transitions.predictivePop(),
        onBack = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) else onClose() },
    )
}
