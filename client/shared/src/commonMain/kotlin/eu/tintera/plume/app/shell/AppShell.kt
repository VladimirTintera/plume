package eu.tintera.plume.app.shell

import eu.tintera.plume.core.accounts.SharedChats
import eu.tintera.plume.features.lockedchats.LockedChatsScreen
import eu.tintera.plume.core.navigation.LockedChatsRoute
import eu.tintera.plume.core.accounts.AccountSwitchNotices
import eu.tintera.plume.core.accounts.Accounts
import eu.tintera.plume.core.accounts.AccountScope
import eu.tintera.plume.core.accounts.AccountViewModels
import eu.tintera.plume.core.accounts.CallAccountScope
import eu.tintera.plume.core.navigation.AccountRoute
import eu.tintera.plume.core.accounts.LocalAccount
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.features.stories.StoryEditTarget
import eu.tintera.plume.features.profile.EventLogScreen
import eu.tintera.plume.core.navigation.ArchiveChatStatisticsRoute
import eu.tintera.plume.core.navigation.ArchiveEventLogRoute
import eu.tintera.plume.core.navigation.ArchivePostStatisticsRoute
import eu.tintera.plume.core.navigation.ChatStatisticsRoute
import eu.tintera.plume.core.navigation.PostStatisticsRoute
import eu.tintera.plume.features.statistics.ChatStatisticsScreen
import eu.tintera.plume.features.statistics.PostStatisticsScreen
import eu.tintera.plume.core.navigation.EventLogRoute
import eu.tintera.plume.features.forward.ShareContactScreen
import eu.tintera.plume.core.navigation.ShareContactRoute
import eu.tintera.plume.core.navigation.AddMembersRoute
import eu.tintera.plume.features.profile.AddMembersScreen
import eu.tintera.plume.features.profile.JoinRequestsScreen
import eu.tintera.plume.features.profile.InviteLinksScreen
import eu.tintera.plume.core.navigation.JoinRequestsRoute
import eu.tintera.plume.core.navigation.RemovedMembersRoute
import eu.tintera.plume.core.navigation.InviteLinksRoute
import eu.tintera.plume.core.navigation.ArchiveJoinRequestsRoute
import eu.tintera.plume.core.navigation.ArchiveRemovedMembersRoute
import eu.tintera.plume.core.navigation.ArchiveInviteLinksRoute
import eu.tintera.plume.features.settings.EditProfileScreen
import eu.tintera.plume.core.navigation.AddBotToGroupRoute
import eu.tintera.plume.core.navigation.SwitchInlineRoute
import eu.tintera.plume.core.navigation.ArchiveEditChatRoute
import eu.tintera.plume.core.navigation.ArchiveMemberRightsRoute
import eu.tintera.plume.core.navigation.MemberRightsRoute
import eu.tintera.plume.core.navigation.EditChatRoute
import eu.tintera.plume.core.navigation.EditProfileRoute
import eu.tintera.plume.core.navigation.AccountPrivacyRoute
import eu.tintera.plume.core.navigation.InstantViewRequests
import eu.tintera.plume.core.navigation.InstantViewRoute
import eu.tintera.plume.core.navigation.NO_STORY
import eu.tintera.plume.core.navigation.DeleteAccountRoute
import eu.tintera.plume.core.navigation.BlockSenderRoute
import eu.tintera.plume.core.navigation.BlockedSendersRoute
import eu.tintera.plume.core.navigation.PrivacySettingRoute
import eu.tintera.plume.features.settings.AccountPrivacyScreen
import eu.tintera.plume.features.settings.PrivacySettingScreen
import eu.tintera.plume.core.navigation.TwoStepRoute
import eu.tintera.plume.core.navigation.LoginEmailRoute
import eu.tintera.plume.core.navigation.NetworkUsageRoute
import eu.tintera.plume.core.navigation.LicensesRoute
import eu.tintera.plume.core.navigation.PasskeysRoute
import eu.tintera.plume.core.navigation.ChangePhoneRoute
import eu.tintera.plume.features.instantview.InstantViewScreen
import eu.tintera.plume.features.settings.ChangePhoneScreen
import eu.tintera.plume.features.settings.NetworkUsageScreen
import eu.tintera.plume.features.settings.LicensesScreen
import eu.tintera.plume.features.settings.PasskeysScreen
import eu.tintera.plume.features.settings.DeleteAccountScreen
import eu.tintera.plume.features.settings.LoginEmailScreen
import eu.tintera.plume.core.navigation.AppLockRoute
import eu.tintera.plume.core.navigation.ChatFolderEditRoute
import eu.tintera.plume.core.navigation.ChatFoldersRoute
import eu.tintera.plume.core.navigation.AiPlaygroundRoute
import eu.tintera.plume.features.aiplayground.AiPlaygroundScreen
import eu.tintera.plume.core.navigation.StickerSetsRoute
import eu.tintera.plume.core.navigation.EmojiSetsRoute
import eu.tintera.plume.core.telegram.StickerSetKind
import eu.tintera.plume.core.telegram.opensAsTopics
import eu.tintera.plume.core.navigation.ProxySettingsRoute
import eu.tintera.plume.features.proxy.ProxySettingsScreen
import eu.tintera.plume.features.settings.ChatFolderEditScreen
import eu.tintera.plume.features.settings.ChatFoldersScreen
import eu.tintera.plume.features.settings.StickerSetsScreen
import eu.tintera.plume.features.settings.BlockSenderScreen
import eu.tintera.plume.features.settings.BlockedSendersScreen
import eu.tintera.plume.features.settings.TwoStepScreen
import eu.tintera.plume.features.settings.AppLockScreen
import eu.tintera.plume.features.conversation.ChatCalendarPaneHost
import eu.tintera.plume.features.conversation.ConversationJumpRequests
import eu.tintera.plume.features.conversation.ConversationReplyRequests
import eu.tintera.plume.features.conversation.SponsoredLinkRequests
import eu.tintera.plume.features.conversation.InlineQueryRequests
import eu.tintera.plume.features.conversation.SwitchInlinePick
import eu.tintera.plume.features.conversation.SwitchInlinePickerScreen
import eu.tintera.plume.features.conversation.ConversationSearchRequests
import eu.tintera.plume.features.profile.ChatProfileScreen
import eu.tintera.plume.features.profile.EditChatScreen
import eu.tintera.plume.features.profile.RemovedMembersScreen
import eu.tintera.plume.features.profile.MemberRightsScreen
import eu.tintera.plume.core.navigation.ChatCalendarRoute
import eu.tintera.plume.core.navigation.ArchiveChatCalendarRoute
import eu.tintera.plume.core.navigation.ChatProfileRoute
import eu.tintera.plume.core.ui.LocalSavedMessagesChatId
import eu.tintera.plume.core.ui.LocalArticleMediaFlight
import eu.tintera.plume.core.ui.LocalPollMediaFlight
import eu.tintera.plume.core.ui.LocalSharedMediaFlight
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.SwitchInlineTarget
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Modifier
import eu.tintera.plume.core.adaptive.Panes
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import eu.tintera.plume.core.adaptive.rememberBookHinge
import eu.tintera.plume.core.adaptive.ContentWidth
import eu.tintera.plume.core.adaptive.PANE_SPACING
import eu.tintera.plume.core.adaptive.LocalPaneNeighbours
import eu.tintera.plume.core.adaptive.PaneCard
import eu.tintera.plume.core.adaptive.PaneNeighbours
import eu.tintera.plume.core.adaptive.PROFILE_CARD_MARGINS
import eu.tintera.plume.core.adaptive.RESERVE_BESIDE_LIST
import eu.tintera.plume.core.adaptive.rememberPaneCardDecorator
import eu.tintera.plume.core.adaptive.shownExtras
import eu.tintera.plume.core.adaptive.LocalProfilePaneResize
import eu.tintera.plume.core.adaptive.LocalListPaneResize
import eu.tintera.plume.core.adaptive.LocalPaneResizeOwners
import eu.tintera.plume.core.adaptive.LocalPaneBand
import eu.tintera.plume.core.adaptive.rememberPaneBand
import eu.tintera.plume.core.adaptive.PaneHandleOverlay
import eu.tintera.plume.core.adaptive.LocalExpandListPane
import eu.tintera.plume.core.adaptive.ListPaneToggle
import eu.tintera.plume.core.adaptive.LocalListPaneToggle
import eu.tintera.plume.core.adaptive.PaneInfo
import eu.tintera.plume.core.adaptive.PaneRole
import eu.tintera.plume.core.adaptive.THREE_PANE_MIN_WIDTH
import eu.tintera.plume.core.adaptive.paneInfo
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import eu.tintera.plume.core.adaptive.TWO_PANE_MIN_WIDTH
import eu.tintera.plume.core.adaptive.contentPaneScaffoldDirective
import eu.tintera.plume.features.calls.CallBackHost
import eu.tintera.plume.features.calls.CallOverlay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import eu.tintera.plume.core.navigation.ChatOpenRequests
import eu.tintera.plume.core.navigation.ArchiveChatProfileRoute
import eu.tintera.plume.core.navigation.ArchiveConversationRoute
import eu.tintera.plume.core.navigation.ArchiveRoute
import eu.tintera.plume.core.telegram.CallRepository
import eu.tintera.plume.features.calls.CallRatingHost
import eu.tintera.plume.features.calls.CallsScreen
import eu.tintera.plume.features.calls.rememberCallSources
import eu.tintera.plume.core.navigation.CallsRoute
import eu.tintera.plume.core.navigation.DownloadsRoute
import eu.tintera.plume.core.navigation.ContinueWatchingRoute
import eu.tintera.plume.features.watching.ContinueWatchingScreen
import eu.tintera.plume.core.video.ContinueWatching
import eu.tintera.plume.core.navigation.MyStoriesRoute
import eu.tintera.plume.features.stories.MyStoriesScreen
import eu.tintera.plume.features.downloads.DownloadsScreen
import eu.tintera.plume.features.downloads.rememberDownloadSources
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.features.chats.ChatSearchNavigation
import eu.tintera.plume.features.chats.ChatSearchScreen
import eu.tintera.plume.features.chats.LocalSearchMorph
import eu.tintera.plume.features.chats.SearchMorph
import eu.tintera.plume.core.adaptive.ListPaneAnchor
import eu.tintera.plume.core.navigation.ChatSearchRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.navigation.ForumTopicsRoute
import eu.tintera.plume.core.navigation.SavedTopicsRoute
import eu.tintera.plume.core.navigation.DirectTopicsRoute
import eu.tintera.plume.features.direct.DirectTopicsScreen
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.features.saved.SavedTopicsScreen
import eu.tintera.plume.core.navigation.ArchiveForumTopicsRoute
import eu.tintera.plume.core.navigation.ArchiveDirectTopicsRoute
import eu.tintera.plume.features.forum.ForumTopicsScreen
import eu.tintera.plume.core.telegram.NO_POLL_MEDIA
import eu.tintera.plume.core.telegram.NO_ARTICLE_MEDIA
import eu.tintera.plume.core.telegram.SharedMediaKind
import eu.tintera.plume.features.profile.ProfileTabRequests
import eu.tintera.plume.core.navigation.ForwardRoute
import eu.tintera.plume.core.navigation.ReplyElsewhereRoute
import eu.tintera.plume.features.forward.ReplyElsewhereScreen
import eu.tintera.plume.core.ui.LocalHighlightTarget
import eu.tintera.plume.core.navigation.PollComposerRoute
import eu.tintera.plume.core.navigation.ChecklistComposerRoute
import eu.tintera.plume.features.conversation.WebAppRequest
import eu.tintera.plume.features.webapp.WebAppScreen
import eu.tintera.plume.core.navigation.WebAppRoute
import eu.tintera.plume.core.telegram.BotChatRequest
import eu.tintera.plume.core.telegram.BotUsersRequest
import eu.tintera.plume.features.conversation.BotShareRequest
import eu.tintera.plume.features.forward.BotShareScreen
import eu.tintera.plume.core.navigation.BotShareRoute
import eu.tintera.plume.core.telegram.BotShareSource
import eu.tintera.plume.core.telegram.PollComposerMode
import eu.tintera.plume.features.conversation.PollComposerScreen
import eu.tintera.plume.features.conversation.ChecklistComposerScreen
import eu.tintera.plume.features.conversation.ChecklistComposerRequest
import eu.tintera.plume.features.conversation.PollComposerRequest
import eu.tintera.plume.core.telegram.ReplyQuote
import eu.tintera.plume.core.navigation.ShareToChatRoute
import eu.tintera.plume.core.navigation.SheetRoute
import eu.tintera.plume.core.navigation.MyQrCodeRoute
import eu.tintera.plume.core.navigation.DraftsRoute
import eu.tintera.plume.core.navigation.BookmarksRoute
import eu.tintera.plume.features.chats.DraftsDestination
import eu.tintera.plume.features.profile.BookmarksDestination
import eu.tintera.plume.features.settings.MyQrCodeSheet
import eu.tintera.plume.core.share.ShareRequests
import eu.tintera.plume.core.share.rememberReturnToCaller
import eu.tintera.plume.core.navigation.MediaRoute
import eu.tintera.plume.core.navigation.OwnedStickerSetRoute
import eu.tintera.plume.core.navigation.StickerComposerRoute
import org.koin.compose.getKoin
import eu.tintera.plume.core.stickers.StickerMaker
import eu.tintera.plume.core.navigation.StoriesRoute
import eu.tintera.plume.core.navigation.VideoChatRoute
import eu.tintera.plume.core.navigation.StoryComposerRoute
import eu.tintera.plume.core.navigation.NewChatRoute
import eu.tintera.plume.core.navigation.NewContactRoute
import eu.tintera.plume.core.navigation.NewGroupMembersRoute
import eu.tintera.plume.core.navigation.CreateChatRoute
import eu.tintera.plume.core.navigation.SettingsRoute
import eu.tintera.plume.core.navigation.SettingsSection
import eu.tintera.plume.core.navigation.SettingsSectionRoute
import eu.tintera.plume.core.navigation.TopLevelRoute
import eu.tintera.plume.core.ui.LocalPanesSideBySide
import eu.tintera.plume.core.ui.ChatContainer
import eu.tintera.plume.app.root.presentation.theme.ChatColorTheme
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.features.chats.ArchiveScreen
import eu.tintera.plume.features.chats.ChatListNavigation
import eu.tintera.plume.features.chats.ChatListScreen
import eu.tintera.plume.features.chats.AddBotToGroupScreen
import eu.tintera.plume.features.chats.NewChatScreen
import eu.tintera.plume.features.chats.NewContactScreen
import eu.tintera.plume.features.chats.NewGroupMembersScreen
import eu.tintera.plume.features.chats.CreateChatScreen
import eu.tintera.plume.features.conversation.ConversationPlaceholder
import eu.tintera.plume.features.conversation.ChatTarget
import eu.tintera.plume.features.conversation.ConversationNavigation
import eu.tintera.plume.features.conversation.ConversationScreen
import eu.tintera.plume.features.forward.ForwardScreen
import eu.tintera.plume.features.share.SharedContentStrip
import eu.tintera.plume.core.share.SharedContent
import eu.tintera.plume.features.share.ShareToChatScreen
import eu.tintera.plume.features.media.MediaViewerScreen
import eu.tintera.plume.features.stickers.OwnedStickerSetScreen
import eu.tintera.plume.features.stickers.StickerComposerScreen
import eu.tintera.plume.features.stories.StoriesScreen
import eu.tintera.plume.features.videochat.VideoChatAccountScope
import eu.tintera.plume.features.videochat.VideoChatOverlay
import eu.tintera.plume.features.videochat.VideoChatScreen
import eu.tintera.plume.features.stories.StoryComposerScreen
import org.koin.core.parameter.parametersOf
import eu.tintera.plume.features.settings.SettingsScreen
import eu.tintera.plume.features.settings.SettingsSectionScreen
import eu.tintera.plume.features.channellimit.ChannelLimitHost
import eu.tintera.plume.features.links.ExternalLinkHost
import org.koin.compose.viewmodel.koinViewModel

/**
 * Kořenový shell přihlášené aplikace: dva zásobníky (Chaty, Nastavení) a list-detail pro chaty.
 *
 * **Sekční navigace není na žádné šířce.** Sekce jsou dvě a navigační lišta i rail mají podle M3
 * smysl od tří. Do Nastavení se chodí z menu účtu ve vyhledávací pilulce seznamu chatů a zpět;
 * spodek obrazovky patří plovoucímu toolbaru seznamu chatů. Rail na širší ploše kdysi zůstal,
 * ale jen zdvojoval menu účtu a ukusoval šířku panelům.
 *
 * **Insety:** shell žádné nekonzumuje, pod žádným chromem shellu nic neleží — každá obrazovka si
 * je řeší sama.
 */
@Composable
fun AppShell(modifier: Modifier = Modifier) {
    // Účet shellu (`AppRoot`): komu patří požadavky zvenčí, dokud nenesou slot.
    val slot = LocalAccount.current.slot
    val accounts = koinInject<Accounts>()
    val navigation = rememberAppNavigation()

    /*
     * Stav položek navigace žije TADY, nad `NavDisplay`: uložený stav obrazovek i jejich
     * ViewModely. Položky, které zrovna nejsou vidět (konverzace otevřená v Chatech, zatímco je
     * uživatel v Nastavení), tak o nic nepřijdou; zruší se až při skutečném odebrání ze
     * zásobníku nebo s celým shellem (odhlášení).
     *
     * Rodičem úložiště ViewModelů je `ViewModelStoreOwner` Activity: Nav3 pod ním drží úložiště
     * pro každou položku, otočení displeje přežije a při opuštění kompozice je vyčistí. Bez
     * tohoto dekorátoru žily ViewModely obrazovek v úložišti Activity napořád a `onCleared` se
     * při odchodu z konverzace nevolal.
     */
    val entryDecorators = listOf<NavEntryDecorator<NavKey>>(
        rememberSaveableStateHolderNavEntryDecorator(),
        rememberViewModelStoreNavEntryDecorator(),
    )

    /*
     * Otevření chatu zvenčí (ťuknutí na notifikaci).
     *
     * Zásobník se skládá znovu od kořene, ne jen přidáním konverzace: požadavek může přijít,
     * když je uživatel v Nastavení, a konverzace otevřená nad nimi by při návratu zpět vrátila
     * do Nastavení, což s notifikací nemá nic společného.
     */
    val chatOpenRequests = koinInject<ChatOpenRequests>()
    val openRequest by chatOpenRequests.request.collectAsStateWithLifecycle()

    val shellSettings = koinInject<AppSettings>()
    val switchNotices = koinInject<AccountSwitchNotices>()
    LaunchedEffect(openRequest) {
        val request = openRequest ?: return@LaunchedEffect
        // Chat jiného účtu: seznam ukazuje výchozí účet, takže se výchozím stane účet chatu
        // a požadavek vyřídí jeho shell (tenhle se se změnou výchozího účtu složí znovu).
        // Společný seznam (režim Vše) ukazuje všechny účty, tam se chat otevře rovnou.
        // Notifikace nebo zástupce můžou přežít odhlášení svého účtu; pak není co otevřít.
        val switchAccount = !shellSettings.chatListAllAccounts.value
        if (switchAccount && request.slot != slot && accounts.all.value.any { it.slot == request.slot }) {
            // Přepnutí je potichu a vede rovnou do konverzace; ta ho ohlásí (`AccountSwitchSnackbar`).
            switchNotices.switchedFor(request.slot, request.chatId)
            accounts.setDefault(request.slot)
            return@LaunchedEffect
        }
        if (accounts.find(request.slot) != null) navigation.openChat(request.slot, request.chatId, request.topicId)
        chatOpenRequests.consume(request)
    }

    /*
     * Sdílení bez vybraného chatu (nabídka „Sdílet" v jiné aplikaci, odkaz `t.me/share`). Stav,
     * ne událost — viz [ShareRequests]: sdílet jde i do aplikace, která teprve naběhla nebo
     * visela na přihlášení. Výběr chatu NENÍ: ukáže se obyčejný seznam chatů a obsah nese čip
     * nahoře ([SharedContentStrip] v `ShellContent`). Jen jednou za sdílení (`arrivalShown`),
     * jinak by obnova aktivity zavřela, co uživatel mezitím otevřel.
     */
    val shareRequests = koinInject<ShareRequests>()
    val incomingShare by shareRequests.incoming.collectAsStateWithLifecycle()
    val shareArrival by shareRequests.arrival.collectAsStateWithLifecycle()

    LaunchedEffect(shareArrival) {
        val id = shareArrival ?: return@LaunchedEffect
        navigation.showChatList()
        shareRequests.arrivalShown(id)
    }

    // Sdílení na čipu vydrží den; aplikace v popředí může běžet déle, než se proces restartuje.
    LifecycleStartEffect(shareRequests) {
        shareRequests.dropExpired()
        onStopOrDispose { }
    }

    /*
     * Direct Share: chat je vybraný už v systémové nabídce, takže se místo výběru otevře
     * potvrzení („Komu: …" s náhledem a popiskem). Klíčem je id sdílení i chat — dvě sdílení
     * po sobě do téhož chatu jsou tatáž obrazovka, do jiného ne.
     */
    // Zástupce může přežít odhlášení svého účtu; sdílení do něj nemá kam dojít.
    val orphanedShare = incomingShare?.takeIf { share -> share.targetSlot?.let { accounts.find(it) == null } == true }
    LaunchedEffect(orphanedShare?.id) {
        orphanedShare?.let { shareRequests.discard(it.id) }
    }

    val confirmShare = incomingShare?.takeIf { it.awaitingConfirmation && it !== orphanedShare }
    val confirmShareKey = confirmShare?.let { it.id to it.targetChatId }

    LaunchedEffect(confirmShareKey) {
        val chatId = confirmShare?.targetChatId ?: return@LaunchedEffect
        // Sdílení do chatu jiného účtu vyřídí shell toho účtu, jako otevření chatu výš.
        val target = confirmShare.targetSlot ?: slot
        if (target != slot && !shellSettings.chatListAllAccounts.value) {
            accounts.setDefault(target)
            return@LaunchedEffect
        }
        navigation.openShareTo(target, chatId)
    }

    /*
     * Odhlášený účet: jeho routy ze zásobníků pryč. Ve společném seznamu v nich můžou ležet chaty
     * jiného účtu než shellu, a ty by po odhlášení zůstaly prázdné (`inRouteAccount` je nesloží).
     */
    val signedIn by accounts.all.collectAsStateWithLifecycle()
    LaunchedEffect(signedIn) {
        val slots = signedIn.map { it.slot }.toSet()
        listOf(ChatsRoute, SettingsRoute).forEach { section ->
            navigation.stack(section).removeAll { it is AccountRoute && it.slot !in slots }
        }
        // Odhlášený z vlastních Nastavení: Nastavení zůstanou otevřená, za účet shellu.
        if (navigation.settingsAccount.let { it != null && it !in slots }) navigation.selectSettingsAccount(slot)
    }

    // Výška pruhu hovoru / videochatu i se stavovou lištou; 0 = pruh není. Naráz je jen jeden
    // (pruh videochatu hovoru ve dvou ustoupí), max je pojistka pro okamžik výměny.
    var callBarHeight by remember { mutableIntStateOf(0) }
    var videoChatBarHeight by remember { mutableIntStateOf(0) }
    val topBarHeight = maxOf(callBarHeight, videoChatBarHeight)
    val density = LocalDensity.current
    Box(modifier.fillMaxSize()) {
        ShellContent(
            navigation = navigation,
            entryDecorators = entryDecorators,
            sharedContent = incomingShare?.takeIf { it.carried },
            shareRequests = shareRequests,
            // Pruh hovoru i videochatu obsah odsune, ne překryje: ležel by přes hlavičku
            // obrazovky i se šipkou zpět a během hovoru by aplikace nešla ovládat. Stavovou
            // lištu má pruh nad sebou, obsah ji proto dostane jako spotřebovanou (jinak
            // dvojitá mezera).
            // Je to kořen shellu, ne položka NavDisplay — měnící se hodnota tu uzel neodpojí
            // (viz Gotchas o insetech v CLAUDE.md).
            modifier = Modifier.fillMaxSize().then(
                if (topBarHeight > 0) {
                    Modifier
                        .consumeWindowInsets(WindowInsets.statusBars)
                        .padding(top = with(density) { topBarHeight.toDp() })
                } else {
                    Modifier
                },
            ),
        )
        // Probíhající hovor nad vším — přes celou obrazovku, nebo zmenšený do pruhu nahoře.
        // Hovor může patřit jinému účtu než shellu; je jen jeden naráz.
        CallAccountScope { CallOverlay(Modifier.align(Alignment.TopCenter), onBarHeight = { callBarHeight = it }) }
        // Videochat, ze kterého uživatel odešel jen z obrazovky — pruh na stejném místě. Může
        // patřit jinému účtu než shellu; běží nejvýš jeden.
        VideoChatAccountScope {
            VideoChatOverlay(
                onOpen = { chatSlot, chatId, groupCallId -> navigation.openVideoChat(chatSlot, chatId, groupCallId) },
                modifier = Modifier.align(Alignment.TopCenter).onSizeChanged { videoChatBarHeight = it.height },
            )
        }
        // Hodnocení po hovoru, o které server požádal; až obrazovka hovoru zmizí.
        CallAccountScope { CallRatingHost() }
        // „Zavolat zpět" z notifikace o zmeškaném hovoru; vytáčí z účtu notifikace.
        CallBackHost()
    }
}

/**
 * Vlastní navigační plocha.
 *
 * Rozvržení panelů se počítá ze šířky **content-oblasti** (`BoxWithConstraints`), ne z okna —
 * scéna má rozhodovat podle místa, které opravdu dostane.
 */
@Composable
private fun ShellContent(
    navigation: AppNavigation,
    entryDecorators: List<NavEntryDecorator<NavKey>>,
    /** Sdílení bez vybraného chatu, které nese čip nahoře; `null`, když žádné není. */
    sharedContent: SharedContent?,
    shareRequests: ShareRequests,
    modifier: Modifier = Modifier,
) {
    val slot = LocalAccount.current.slot
    val accounts = koinInject<Accounts>()
    // Levý okraj plochy scény v okně (px): meze pantu jsou v souřadnicích okna (`rememberBookHinge`).
    var shellLeft by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(modifier.onGloballyPositioned { shellLeft = it.positionInWindow().x }) {
        val base = contentPaneScaffoldDirective(maxWidth, maxHeight)
        val wide = maxWidth >= TWO_PANE_MIN_WIDTH

        val chatsStack = navigation.stack(ChatsRoute)
        val settingsStack = navigation.stack(SettingsRoute)

        /*
         * V dvoupanelu je v Nastavení vždycky otevřená nějaká sekce.
         *
         * Placeholder („vyber sekci") by tu byl k ničemu: prázdný panel vedle dvou řádků vypadá
         * jako nedodělek.
         *
         * Sekce se do zásobníku ZAPISUJE ([AppNavigation.ensureSettingsSection]) — zpět musí
         * odebírat položky, které v zásobníku opravdu jsou. Efekt stojí tady, v shellu, a ptá se
         * na vybranou sekci: dřív byl v obrazovce seznamu sekcí a když ta ještě dobíhala
         * odchodovou animací, přidal sekci nastavení do zásobníku, který už patřil Chatům.
         * Velikost zásobníku je v klíči kvůli ťuknutí na už vybrané Nastavení (návrat na kořen).
         *
         * Než efekt doběhne, sekce se pro kreslení jen dopočítá — jinak by na jeden snímek
         * problikl seznam sekcí přes celou šířku.
         */
        LaunchedEffect(wide, navigation.section, settingsStack.size) {
            if (wide && navigation.section == SettingsRoute) navigation.ensureSettingsSection()
        }
        /*
         * Sheety navrchu ([SheetRoute], `SheetSceneStrategy`) se kreslí NAD scénou ze zbytku
         * zásobníku, takže pravidla o panelech, sekci Nastavení a čipu sdílení se počítají
         * bez nich — jako by tam nebyly. Ke kreslení se přidají zpátky navrch. Sheet uprostřed
         * (obrazovka položená nad otevřený sheet) se nekreslí vůbec a efekt ho ze zásobníku
         * odklidí — stejný vzor jako sekce Nastavení níž: kreslí se rovnou opravený.
         */
        val sheetBuried = settingsStack.hasBuriedSheet() || chatsStack.hasBuriedSheet()
        LaunchedEffect(sheetBuried) { if (sheetBuried) navigation.dropBuriedSheets() }
        val settingsBase = settingsStack.withoutSheets()
        val settingsSheets = settingsStack.trailingSheets()
        val settingsDisplayed: List<NavKey> =
            if (wide && settingsBase.lastOrNull() == SettingsRoute) {
                settingsBase + SettingsSectionRoute(SettingsSection.entries.first())
            } else {
                settingsBase
            }

        /*
         * Na široké ploše jsou dva panely VŽDYCKY, i bez otevřeného chatu — vpravo je pak
         * placeholder (`ConversationPlaceholder`). Dřív dostal seznam bez konverzace celou
         * šířku: vyhledávací pilulka s pevnou maximální šířkou v něm visela a řádky chatů se
         * natáhly přes celý rozložený Fold. Otevření chatu tak navíc nemění rozvržení, jen obsah
         * pravého panelu.
         */
        /*
         * Výjimka: seznam, ze kterého není co vybrat. Prázdný archiv (nebo prázdný hlavní
         * seznam) vedle placeholderu „vyber chat vlevo" by byly dva prázdné stavy vedle sebe
         * a ten pravý by radil něco, co udělat nejde. Scéna proto přejde na jeden panel
         * a prázdný stav seznamu dostane celou plochu. Počítá se jen se seznamem, který je
         * vidět (poslední list panel), a jen bez otevřené konverzace. Načítání prázdné není —
         * jinak by se rozvržení přepínalo tam a zpátky, než seznam dorazí.
         */
        val chatRepository = koinInject<ChatRepository>()
        val shownFolder = chatsStack.shownChatFolder()
        val shownList by remember(shownFolder) { chatRepository.chats(shownFolder) }.collectAsStateWithLifecycle()
        // Otevřená konverzace se hledá jen v TÉ větvi, jejíž seznam je vidět — viz hasOpenConversation.
        val nothingToPick = navigation.section == ChatsRoute &&
            !chatsStack.hasOpenConversation(shownFolder) &&
            (shownList as? ChatListState.Ready)?.chats?.isEmpty() == true

        val twoPane = wide && !nothingToPick

        // Chaty na široké ploše kreslí vlastní scéna (`PaneScene.kt`), ne třípanel z knihovny.
        val sideBesideList = maxWidth >= THREE_PANE_MIN_WIDTH

        /*
         * Zavření obrazovky „Komu: …" (Direct Share) vrací do aplikace, ze které se sdílelo, ne
         * do Plume: chat byl vybraný už v systémové nabídce, takže tu uživatel nic jiného
         * nedělal. Obrazovka se napřed odebere, aby příští otevření Plume začalo tam, kde bylo
         * před sdílením. Sdílení bez chatu (čip) tohle nemá — zpět je tam obyčejná navigace.
         */
        val returnToCaller = rememberReturnToCaller()
        val closeShare = {
            chatsStack.removeAll { it is ShareToChatRoute }
            returnToCaller()
        }
        /*
         * Role panelu se čte z metadat položky (`Panes`), a ty zná jen poskytovatel položek.
         * Ptá se na ni i on sám (seznam chatů: je konverzace vidět?), proto odkaz přes proměnnou.
         * Šířky panelů v metadatech nejsou: Chaty rozvrhuje scéna Chatů (`PaneSceneLayout`).
         */
        lateinit var entryProvider: (NavKey) -> NavEntry<NavKey>
        val paneOf: (NavKey) -> PaneInfo? = { entryProvider(it).paneInfo }
        entryProvider = shellEntryProvider(
            chatsStack = chatsStack,
            settingsStack = settingsStack,
            selectedSettingsSection = settingsDisplayed.filterIsInstance<SettingsSectionRoute>()
                .lastOrNull()?.section,
            wide = wide,
            twoPane = twoPane,
            paneOf = paneOf,
            closeShare = closeShare,
            navigation = navigation,
        ).inRouteAccount(accounts)

        /*
         * Profil ve třetím panelu patří ke konverzaci pod sebou. Otevře-li se nad ním jiná
         * konverzace (jiný chat ze seznamu, zmínka, „Napsat zprávu" členovi skupiny), panel
         * zůstane otevřený a přejde na profil té nové (`followProfile`) — scéna by jinak nechala
         * starý profil vedle cizího chatu. Stejný vzor jako sekce Nastavení výš: efekt zásobník
         * opraví, kreslí se rovnou opravený. Na telefonu ne: tam je profil pod konverzací
         * obyčejná historie a zpět se do něj vrací.
         */
        val chatsBase = chatsStack.withoutSheets()
        val chatsSheets = chatsStack.trailingSheets()
        val chatsDisplayed: List<NavKey> = if (wide) chatsBase.withProfileFollowing(paneOf) else chatsBase
        val profileMoved = chatsDisplayed !== chatsBase
        LaunchedEffect(profileMoved) {
            if (profileMoved) chatsStack.followProfile(paneOf)
        }

        /*
         * Tři panely vedle sebe (seznam | detail | boční sloupec) jen s boční položkou navrchu
         * a od THREE_PANE_MIN_WIDTH; pod ní boční sloupec seznam vytlačí (`PaneScene`). Rozhoduje
         * poslední položka s rolí — obrazovka přes celou plochu (prohlížeč nad profilem) roli nemá.
         * Platí pro zásobník vybrané sekce aplikace: v Chatech profil, v Nastavení podstránka sekce.
         */
        val shownStack = if (navigation.section == SettingsRoute) settingsDisplayed else chatsDisplayed
        val extraShown = shownStack.lastOrNull { paneOf(it) != null }?.let(paneOf)?.role == PaneRole.Extra
        val threePane = twoPane && extraShown && maxWidth >= THREE_PANE_MIN_WIDTH

        /*
         * Pant v pozici kniha (napůl otevřená skládačka, svislý ohyb): hranice dvou viditelných
         * sloupců leží na něm, ať přes pant nevede seznam ani karta. Seznam | detail: seznam končí
         * u levého okraje pantu. Detail | boční sloupec (seznam ustoupil): boční sloupec začíná za
         * pantem. Se třemi sloupci se pant neřeší — nevešly by se. Táhla v pozici kniha nejsou,
         * hranici by z pantu odtáhla. Dřív to dělal knihovní scaffold podle `excludedBounds`.
         */
        val density = LocalDensity.current
        val bookHinge = rememberBookHinge()?.let { hinge ->
            with(density) { (hinge.left - shellLeft).toDp()..(hinge.right - shellLeft).toDp() }
        }?.takeIf { twoPane && !threePane && it.start > 0.dp && it.endInclusive < maxWidth }
        val hingeGap = bookHinge?.let { it.endInclusive - it.start } ?: 0.dp
        // Boční sloupec za pantem: od pravého okraje pantu do kraje plochy.
        val sideBeyondHinge = bookHinge?.let { maxWidth - it.endInclusive }

        /*
         * Táhlo jen mezi HLAVNÍM seznamem chatů a konverzací. Archiv, nová zpráva a Nastavení
         * mají svou výchozí šířku: sbalený archiv by byl jen sloupec avatarů bez vysvětlení,
         * co je to za seznam. Šířku seznamu i profilu dostává scéna Chatů (`PaneSceneLayout`).
         */
        val listExpansion = rememberListPaneExpansion()
        // Pilulka ↔ pole hledání (`SearchMorph`): kde stála pilulka, a návrat z hledání.
        val searchMorph = remember { SearchMorph() }
        // Hledání je seznam chatů v jiné podobě (`docs/navrh-hledani.md`): táhlo i šířka platí
        // dál, jinak by panel při otevření hledání skočil na výchozí šířku.
        val shownListRoute = chatsDisplayed.lastOrNull { paneOf(it)?.role == PaneRole.List }
        val searchShown = navigation.section == ChatsRoute && shownListRoute is ChatSearchRoute
        val mainListShown = navigation.section == ChatsRoute && (shownListRoute == ChatsRoute || searchShown)
        // Táhlo seznamu je vlastní (`rememberListPaneResize`), i ve dvou panelech: Chaty kreslí
        // scéna Chatů, knihovní táhlo tam není. Bez bočního sloupce vedle seznamu zbývá jen konverzace.
        val listResize = rememberListPaneResize(
            listExpansion,
            base.defaultPanePreferredWidth,
            maxWidth,
            reserve = RESERVE_BESIDE_LIST.takeIf { !threePane },
        )
        // Seznam je vidět, dokud ho boční sloupec pod THREE_PANE_MIN_WIDTH nevytlačí.
        val sceneListShown = twoPane && mainListShown && (!extraShown || threePane)
        val listResizeShown = listResize.resize.takeIf { sceneListShown && bookHinge == null }
        val expandList: () -> Unit = { listResize.settleTo(listExpansion.expandedAnchor) }
        // Hledání ve sbaleném sloupci (zkratka, nabídka, hashtag) by bylo nečitelné: panel se rozbalí.
        LaunchedEffect(searchShown, twoPane) {
            if (searchShown && twoPane && listExpansion.anchor == ListPaneAnchor.Collapsed) expandList()
        }
        /*
         * Tlačítko a zkratka sbalení: jen u hlavního seznamu vedle konverzace, ne v hledání (to
         * seznam rozbaluje samo). Dojede táhlo seznamu jako po puštění.
         */
        val listAnchor = listResize.anchor
        val listToggle = if (twoPane && mainListShown && !searchShown) {
            ListPaneToggle {
                val target = if (listAnchor == ListPaneAnchor.Collapsed) listExpansion.expandedAnchor else ListPaneAnchor.Collapsed
                listResize.settleTo(target)
            }
        } else {
            null
        }

        /*
         * Co stojí vedle karty (`PaneCard`): seznam vlevo, profil vpravo. Pod THREE_PANE_MIN_WIDTH
         * profil seznam vytlačí a karta začíná u kraje okna. Bere se zásobník sekce, která je
         * vidět — Nastavení mají vlastní (podstránka sekce je tam třetí panel).
         */
        val extraBeside = twoPane && extraShown
        val paneNeighbours = PaneNeighbours(
            listBeside = twoPane && (!extraBeside || threePane),
            extraBeside = extraBeside,
            // Nezávisle na tom, jestli je profil otevřený: zavíraný odjíždí ještě jako karta.
            extraCard = navigation.section == ChatsRoute,
        )

        /*
         * Šířka profilu vedle konverzace: táhlo v kartě profilu (`ProfilePaneResize`), dva
         * i tři panely; dostává ji scéna Chatů. Za tahu platí šířka tahu, po puštění kotva.
         */
        val profileBeside = navigation.section == ChatsRoute && extraBeside
        val profileResize = rememberProfilePaneResize(
            defaultWidth = base.defaultPanePreferredWidth,
            total = maxWidth,
            listWidth = listResize.width.takeIf { threePane && mainListShown } ?: base.defaultPanePreferredWidth.takeIf { threePane },
        )
        // Za tahu vlastním táhlem a za jeho dojezdu řídí pohyb táhlo, ne pružina scény (`DraggedWidth`).
        val instantPaneBounds = (listResizeShown != null && listResize.resizing) || (profileBeside && profileResize.resizing)
        // Čí karta hlásí polohu táhlům: jen to, co je v Chatech opravdu vidět (`LocalPaneResizeOwners`).
        val paneResizeOwners = setOfNotNull(
            chatsDisplayed.shownExtras(paneOf).lastOrNull()?.takeIf { profileBeside },
            chatsDisplayed.lastOrNull { paneOf(it)?.role == PaneRole.List }?.takeIf { listResizeShown != null },
        ).mapTo(HashSet()) { entryProvider(it).contentKey }

        ShellShortcuts(navigation = navigation, chatsStack = chatsStack, twoPane = twoPane, listToggle = listToggle)
        LaunchedEffect(navigation.section) { navigation.closeSheetsOutside(navigation.section) }

        // Široká plocha se hlásí dolů: obrazovky podle ní poznají, že se nevyměňují,
        // a sdílený prvek nezaloží — viz LocalPanesSideBySide.
        // Id Uložených zpráv pro všechno, co kreslí chat (název, záložka místo fotky).
        val savedMessagesChatId by chatRepository.savedMessagesChatId.collectAsStateWithLifecycle()

        // Plocha okna: leží na ní seznam a vedle něj karta (`PaneCard`).
        if (wide) Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceContainer))

        /*
         * Čip „Sdílíte: …" nahoře: nad seznamem, archivem, konverzací i profilem, ne nad
         * obrazovkou přes celou plochu (prohlížeč) ani v Nastavení. „Vložit sem" jen tam, kde je
         * vidět konverzace. Pruh zabírá vlastní místo a obsahu pod ním se horní inset zkonzumuje
         * — po celou dobu, kdy je pruh ve stromu, i během animace (viz [SharedContentStrip]).
         */
        val chipShown = sharedContent != null &&
            navigation.section == ChatsRoute &&
            chatsDisplayed.showsShareChip(paneOf)
        val chipState = remember { MutableTransitionState(false) }
        chipState.targetState = chipShown
        val chipPresent = chipState.currentState || chipState.targetState
        val topInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top)

        Column(Modifier.fillMaxSize()) {
            if (chipPresent) {
                SharedContentStrip(
                    visibleState = chipState,
                    content = sharedContent,
                    insertChat = chatsDisplayed.shareInsertChat(paneOf, sideBySide = twoPane),
                    onInsert = { chat -> shareRequests.choose(chat.slot, chat.chatId) },
                    onDiscard = shareRequests::discard,
                    // Na široké ploše leží pruh na ploše okna, na telefonu nad obrazovkou jako lišta.
                    background = if (wide) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
                )
            }
            val paneBand = rememberPaneBand()
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .then(if (chipPresent) Modifier.consumeWindowInsets(topInsets) else Modifier),
            ) {
                CompositionLocalProvider(
                    LocalPanesSideBySide provides wide,
                    LocalPaneNeighbours provides paneNeighbours,
                    LocalSavedMessagesChatId provides savedMessagesChatId,
                    // I ve třech panelech: zvolená kotva tam určuje šířku seznamu, takže rozbalení zabere i bez táhla.
                    LocalExpandListPane provides expandList.takeIf { twoPane && mainListShown },
                    LocalSearchMorph provides searchMorph,
                    LocalListPaneToggle provides listToggle,
                    LocalProfilePaneResize provides profileResize.resize.takeIf { profileBeside && bookHinge == null },
                    LocalListPaneResize provides listResizeShown,
                    LocalPaneResizeOwners provides paneResizeOwners,
                    // Jeden pás hlavičky přes konverzaci a boční panel (akce konverzace nad panelem).
                    LocalPaneBand provides paneBand,
                    // Volba z hledání v Nastavení, na kterou se sekce doscrolluje.
                    LocalHighlightTarget provides navigation.settingsHighlight,
                ) {
                    val paneCard = rememberPaneCardDecorator()
                    val decorators = remember(entryDecorators, paneCard) { entryDecorators + paneCard }
                    /*
                     * Položky Chatů se staví samy, ne přes `rememberDecoratedNavEntries(zásobník, …)`:
                     * ta si je pamatuje jen podle klíčů, a šířka seznamu ve třech panelech je v jeho
                     * METADATECH — po změně kotvy by zůstala stará.
                     */
                    // Odjezd bočního sloupce drží scéna panelů sama (`PaneScene`).
                    val chatsDrawn = chatsDisplayed + chatsSheets
                    val settingsDrawn = settingsDisplayed + settingsSheets
                    val chatsEntries = remember(chatsDrawn.toList()) { chatsDrawn.map(entryProvider) }
                    /*
                     * Položky se dekorují pro KAŽDOU sekci, i tu, která není vidět. Zásobník, který se
                     * zrovna nedekoruje, by pro Nav3 vypadal jako odebraný a jeho ViewModely by se zrušily.
                     */
                    val entriesBySection = mapOf<TopLevelRoute, List<NavEntry<NavKey>>>(
                        ChatsRoute to rememberDecoratedNavEntries(chatsEntries, decorators),
                        SettingsRoute to rememberDecoratedNavEntries(settingsDrawn, decorators, entryProvider.inSettingsAccount(navigation)),
                    )

                    val transitions = rememberNavTransitions(chatContainer = !wide)
                    NavDisplay(
                        entries = navigation.visibleSections.flatMap { entriesBySection.getValue(it) },
                        transitionSpec = transitions.forward(),
                        popTransitionSpec = transitions.pop(),
                        predictivePopTransitionSpec = transitions.predictivePop(),
                        sceneDecoratorStrategies = remember { listOf(PredictiveBackCorners()) },
                        onBack = {
                            // Zpět z „Komu: …" je totéž co ✕ — vrátit uživatele tam, odkud sdílel.
                            if (chatsStack.lastOrNull() is ShareToChatRoute) closeShare() else navigation.goBack()
                        },
                        sceneStrategies = listOf(
                            // Sheet navrchu se kreslí nad scénou, kterou z položek pod ním spočítá list-detail.
                            remember { SheetSceneStrategy() },
                            // Chaty, archiv i Nastavení na široké ploše kreslí scéna panelů; na telefonu
                            // vrací null a NavDisplay vyměňuje obrazovky (`SinglePaneScene`).
                            PaneSceneStrategy { scene ->
                                when {
                                    !wide -> null
                                    scene == SETTINGS_SCENE -> PaneSceneSpec(
                                        // Seznam sekcí a podstránka mají výchozí šířku panelu, bez táhla.
                                        layout = PaneSceneLayout(
                                            twoPane = true,
                                            sideBesideList = sideBesideList,
                                            listWidth = bookHinge?.start ?: base.defaultPanePreferredWidth,
                                            sideWidth = sideBeyondHinge ?: base.defaultPanePreferredWidth,
                                            instant = false,
                                            hingeGap = hingeGap,
                                        ),
                                        // Sekce je v širokém Nastavení otevřená vždycky (`ensureSettingsSection`).
                                        detailPlaceholder = null,
                                        conversationActions = false,
                                    )
                                    else -> PaneSceneSpec(
                                        layout = PaneSceneLayout(
                                            twoPane = twoPane,
                                            sideBesideList = sideBesideList,
                                            // Archiv a ostatní seznamy táhlo nemají, stojí na výchozí šířce.
                                            listWidth = bookHinge?.start
                                                ?: if (mainListShown) listResize.width else base.defaultPanePreferredWidth,
                                            sideWidth = sideBeyondHinge
                                                ?: (profileResize.width + PROFILE_CARD_MARGINS),
                                            instant = instantPaneBounds,
                                            hingeGap = hingeGap,
                                        ),
                                        detailPlaceholder = ChatDetailPlaceholder,
                                        conversationActions = true,
                                    )
                                }
                            },
                        ),
                    )
                    // Vlastní táhla nad panely (seznam ve třech panelech, profil) — viz `PaneResize`.
                    PaneHandleOverlay(listOfNotNull(listResizeShown, profileResize.resize.takeIf { profileBeside && bookHinge == null }))
                }
            }
        }

        /*
         * Odkaz zvenčí (`t.me/…` z jiné aplikace, naskenovaný QR kód). Nad celým shellem: přijít
         * může nad kteroukoli obrazovkou a sheet pozvánky nebo hláška nepatří žádné z nich.
         */
        // Čtečka Instant View z náhledu odkazu: konverzace požádá, shell přidá obrazovku nad ni.
        val instantViews = koinInject<InstantViewRequests>()
        val instantView by instantViews.request.collectAsStateWithLifecycle()
        LaunchedEffect(instantView) {
            val request = instantView ?: return@LaunchedEffect
            instantViews.consume(request)
            navigation.stack(ChatsRoute).add(InstantViewRoute(slot, request.url))
        }

        // Za každý účet: zvolí-li uživatel u pozvánky jiný účet, vyřídí ji ViewModel z jeho
        // kontejneru. Odkaz bez účtu (zvenčí) bere výchozí účet, tedy tenhle shell.
        val signedIn by accounts.all.collectAsStateWithLifecycle()
        signedIn.forEach { session ->
            key(session.slot) {
                val host: @Composable () -> Unit = {
                    ExternalLinkHost(
                        viewModel = koinViewModel(),
                        accepts = { request -> request.slot == session.slot || (request.slot == null && session.slot == slot) },
                        onNavigate = { destination -> navigation.openExternal(session.slot, destination, twoPane) },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                if (session.slot == slot) host() else AccountScope(session) { AccountViewModels(session.slot, host) }
            }
        }
        // Limit skupin a kanálů: přijít může ze vstupu i založení kdekoli, sheet patří nad vše.
        ChannelLimitHost()
    }
}

/**
 * Obrazovky obou sekcí. Každá navigace píše do zásobníku SVÉ sekce ([chatsStack],
 * [settingsStack]), ne do toho vybraného — obrazovka může být zrovna na odchodu.
 */
private fun shellEntryProvider(
    chatsStack: MutableList<NavKey>,
    settingsStack: MutableList<NavKey>,
    selectedSettingsSection: SettingsSection?,
    wide: Boolean,
    twoPane: Boolean,
    paneOf: (NavKey) -> PaneInfo?,
    closeShare: () -> Unit,
    navigation: AppNavigation,
): (NavKey) -> NavEntry<NavKey> = entryProvider {

    entry<ChatsRoute>(
        metadata = Panes.list() + ChatListMetadata,
    ) {
        val slot = LocalAccount.current.slot
        val settings = koinInject<AppSettings>()
        val savedMessagesChatId = LocalSavedMessagesChatId.current
        ChatListScreen(
            viewModel = koinViewModel(),
            // Konverzace vedle seznamu má vlastní přehrávač (dock), druhý by ho zdvojil. Profil
            // ve třetím panelu ji nezakrývá, takže se nepočítá.
            conversationOpen = chatsStack.lastOrNull { it !is SheetRoute && paneOf(it)?.role != PaneRole.Extra } is ConversationRoute,
            // Vybraný řádek jen vedle detailu. Na telefonu by se otevíraný řádek přebarvil
            // uprostřed růstu v konverzaci (viz ChatContainer) a nic by tím neoznačil.
            selectedChatId = chatsStack.lastOrNull { it is ConversationRoute || it is ForumTopicsRoute || it is SavedTopicsRoute || it is DirectTopicsRoute }
                ?.let {
                    (it as? ConversationRoute)?.chatId ?: (it as? ForumTopicsRoute)?.chatId
                        ?: (it as? DirectTopicsRoute)?.chatId ?: (it as SavedTopicsRoute).chatId
                }
                ?.takeIf { twoPane },
            // Ve společném seznamu má totéž id chat ve dvou účtech; vybraný je jen ten z účtu routy.
            selectedChatSlot = (chatsStack.lastOrNull { it is ConversationRoute || it is ForumTopicsRoute || it is SavedTopicsRoute || it is DirectTopicsRoute } as? AccountRoute)?.slot,
            navigation = ChatListNavigation(
                onChatClick = { chatId ->
                    // Uložené zprávy po chatech, když si to uživatel zvolil („Zobrazit jako chaty").
                    if (chatId == savedMessagesChatId && settings.savedMessagesAsChats.value) {
                        chatsStack.openSavedTopics(slot, chatId)
                    } else {
                        chatsStack.openConversation(slot, chatId)
                    }
                },
                onForumClick = { chatId -> chatsStack.openForum(slot, chatId) },
                // Řádek jiného účtu ve společném seznamu. Uložené zprávy po chatech jsou volba
                // účtu shellu; Uložené zprávy jiného účtu se otevřou jako konverzace.
                onAccountChatClick = { chatSlot, chatId ->
                    if (chatSlot == slot && chatId == savedMessagesChatId && settings.savedMessagesAsChats.value) {
                        chatsStack.openSavedTopics(slot, chatId)
                    } else {
                        chatsStack.openConversation(chatSlot, chatId)
                    }
                },
                onAccountForumClick = { chatSlot, chatId -> chatsStack.openForum(chatSlot, chatId) },
                // Koncept v tématu z přehledu konceptů: téma a pod ním seznam témat, jako u nalezené zprávy.
                onTopicClick = { chatId, topicId -> chatsStack.openFoundMessage(slot, chatId, messageId = null, topicId = topicId) },
                onThreadClick = { chatId, threadId -> chatsStack.openCommentThread(slot, chatId, threadId) },
                onMessageClick = { chatId, messageId, topicId -> chatsStack.openFoundMessage(slot, chatId, messageId, topicId) },
                onAccountMessageClick = { chatSlot, chatId, messageId, topicId -> chatsStack.openFoundMessage(chatSlot, chatId, messageId, topicId) },
                onOpenArchive = {
                    if (chatsStack.lastOrNull() != ArchiveRoute) chatsStack.add(ArchiveRoute)
                },
                onOpenLockedChats = { if (chatsStack.lastOrNull() !is LockedChatsRoute) chatsStack.add(LockedChatsRoute(slot)) },
                onOpenCalls = { if (chatsStack.lastOrNull() != CallsRoute) chatsStack.add(CallsRoute) }
                    .takeIf { koinInject<CallRepository>().supported },
                onOpenDownloads = { if (chatsStack.lastOrNull() != DownloadsRoute) chatsStack.add(DownloadsRoute) },
                onOpenContinueWatching = { if (chatsStack.lastOrNull() !is ContinueWatchingRoute) chatsStack.add(ContinueWatchingRoute(slot)) },
                onOpenDrafts = { navigation.openSheet(ChatsRoute, DraftsRoute(slot)) },
                onOpenBookmarks = { navigation.openSheet(ChatsRoute, BookmarksRoute(slot)) },
                onOpenMyQrCode = { navigation.openSheet(ChatsRoute, MyQrCodeRoute) },
                onOpenMyStories = { if (chatsStack.lastOrNull() != MyStoriesRoute) chatsStack.add(MyStoriesRoute) },
                onNewChat = {
                    if (chatsStack.lastOrNull() !is NewChatRoute) chatsStack.add(NewChatRoute(slot))
                },
                onNewGroup = { if (chatsStack.lastOrNull() !is NewGroupMembersRoute) chatsStack.add(NewGroupMembersRoute(slot)) },
                onNewChannel = { chatsStack.add(CreateChatRoute(slot, channel = true)) },
                onChatArchived = chatsStack::closeConversationOf,
                onOpenSettings = { navigation.openSettings() },
                onOpenProxySettings = { navigation.openProxySettings(slot) },
                onCreateFolder = { navigation.openChatFolders(slot, create = true) },
                onManageFolders = { navigation.openChatFolders(slot) },
                onEditFolder = { id -> navigation.openChatFolder(slot, id) },
                onOpenScreen = { screen -> navigation.openScreen(slot, screen, twoPane) },
                onOpenSearch = { navigation.openSearch(slot, query = "", keepConversation = twoPane) },
                onOpenStories = { chatId -> chatsStack.openStories(slot, chatId) },
                onComposeStory = { path, video -> chatsStack.add(StoryComposerRoute(slot, path, video)) },
            ),
            scrollToChat = navigation.chatListScroll,
            onScrolledToChat = navigation::consumeChatListScroll,
            modifier = Modifier.listPaneInsets(twoPane),
        )
    }

    /*
     * Hledání v seznamu chatů jako vlastní obrazovka v panelu seznamu (`docs/navrh-hledani.md`).
     * Táž scéna a šířka jako seznam chatů; v zásobníku leží pod konverzací
     * (`AppNavigation.openSearch`), výsledek se otevře nad ním a hledání vlevo zůstane.
     */
    entry<ChatSearchRoute>(
        metadata = Panes.list() + ChatSearchMetadata,
    ) { route ->
        val slot = route.slot
        val settings = koinInject<AppSettings>()
        val savedMessagesChatId = LocalSavedMessagesChatId.current
        ChatSearchScreen(
            slot = slot,
            initialQuery = route.query,
            navigation = ChatSearchNavigation(
                onClose = { chatsStack.remove(route) },
                onChatClick = { chatId, chatSlot, forum ->
                    val target = chatSlot ?: slot
                    // Seznam pod hledáním dojede na vybraný chat, ať je po zavření hledání vidět.
                    if (target == slot) navigation.scrollChatListTo(chatId)
                    when {
                        forum -> chatsStack.openForum(target, chatId)
                        // Uložené zprávy po chatech, když si to uživatel zvolil (jako ze seznamu).
                        target == slot && chatId == savedMessagesChatId && settings.savedMessagesAsChats.value ->
                            chatsStack.openSavedTopics(slot, chatId)
                        else -> chatsStack.openConversation(target, chatId)
                    }
                },
                onMessageClick = { chatId, messageId, topicId, chatSlot ->
                    if ((chatSlot ?: slot) == slot) navigation.scrollChatListTo(chatId)
                    chatsStack.openFoundMessage(chatSlot ?: slot, chatId, messageId, topicId)
                },
            ),
            modifier = Modifier.listPaneInsets(twoPane),
        )
    }

    /*
     * Archiv je samostatná list-detail scéna (ARCHIVE_SCENE). V dvoupanelu má vpravo vlastní
     * detail panel [ArchiveConversationRoute] (nebo placeholder), takže nezasahuje do otevřené
     * konverzace hlavního seznamu; zpět odebere celou archivní větev a vrátí hlavní seznam.
     */
    entry<ArchiveRoute>(
        metadata = Panes.list(ARCHIVE_SCENE) + ChatListMetadata,
    ) {
        val slot = LocalAccount.current.slot
        ArchiveScreen(
            // Klíč se složkou: ViewModel se cachuje podle klíče a Koin by u cachované instance
            // parametr ignoroval (viz konverzace).
            viewModel = koinViewModel(
                key = "chats-archive",
                parameters = { parametersOf(ChatFolder.Archive) },
            ),
            selectedChatId = chatsStack.lastOrNull { it is ArchiveConversationRoute || it is ArchiveForumTopicsRoute || it is ArchiveDirectTopicsRoute }
                ?.let {
                    (it as? ArchiveConversationRoute)?.chatId ?: (it as? ArchiveForumTopicsRoute)?.chatId
                        ?: (it as ArchiveDirectTopicsRoute).chatId
                }
                ?.takeIf { twoPane },
            onChatClick = { chatId -> chatsStack.openArchiveConversation(slot, chatId) },
            onForumClick = { chatId -> chatsStack.openArchiveForum(slot, chatId) },
            onBack = chatsStack::closeArchive,
            onChatArchived = chatsStack::closeConversationOf,
            modifier = Modifier.listPaneInsets(twoPane),
            onOpenStories = { chatId -> chatsStack.openStories(slot, chatId) },
            // Společný archiv (režim Všechny účty): chat se otevírá v účtu řádku.
            selectedChatSlot = (chatsStack.lastOrNull { it is ArchiveConversationRoute || it is ArchiveForumTopicsRoute || it is ArchiveDirectTopicsRoute } as? AccountRoute)?.slot,
            onAccountChatClick = { chatSlot, chatId -> chatsStack.openArchiveConversation(chatSlot, chatId) },
            onAccountForumClick = { chatSlot, chatId -> chatsStack.openArchiveForum(chatSlot, chatId) },
        )
    }

    /*
     * Zahájení nové konverzace. List panel jako Chaty a Archiv: vlevo nahradí seznam chatů,
     * po výběru kontaktu se odebere a otevře se konverzace.
     */
    // Historie hovorů: seznam vlevo, konverzace z ní vedle (jako nová konverzace).
    entry<CallsRoute>(
        metadata = Panes.list(),
    ) {
        CallsScreen(
            sources = rememberCallSources(),
            onBack = { chatsStack.remove(CallsRoute) },
            onOpenChat = { slot, chatId -> chatsStack.openConversation(slot, chatId) },
        )
    }

    // Zamčené chaty: seznam vlevo, konverzace vedle v hlavním zásobníku (jako historie hovorů).
    // Konverzaci pustí brána (`LockedChatGate`), protože je sezení po odemčení seznamu odemčené.
    entry<LockedChatsRoute>(
        metadata = Panes.list(),
    ) { route ->
        val settings = koinInject<AppSettings>()
        val savedMessagesChatId = LocalSavedMessagesChatId.current
        LockedChatsScreen(
            onBack = { chatsStack.remove(route) },
            onOpenChat = { chat ->
                when {
                    chat.opensAsTopics -> chatsStack.openForum(route.slot, chat.id)
                    chat.id == savedMessagesChatId && settings.savedMessagesAsChats.value -> chatsStack.openSavedTopics(route.slot, chat.id)
                    else -> chatsStack.openConversation(route.slot, chat.id)
                }
            },
        )
    }

    // Rozkoukaná videa: seznam vlevo, video přes celou plochu v prohlížeči, který sám pokračuje
    // z pamatované pozice; „Zobrazit v chatu" jako nalezená zpráva.
    entry<ContinueWatchingRoute>(
        metadata = Panes.list(),
    ) { route ->
        ContinueWatchingScreen(
            viewModel = koinViewModel(key = "continue-watching-${route.slot.id}"),
            onBack = { chatsStack.remove(route) },
            onOpenVideo = { chatId, messageId -> chatsStack.add(MediaRoute(route.slot, chatId, messageId)) },
            onShowInChat = { chatId, messageId -> chatsStack.openFoundMessage(route.slot, chatId, messageId, topicId = null) },
        )
    }

    // Stahování: seznam vlevo, zpráva se souborem vedle (jako nalezená zpráva); stažené video
    // a obrázek přes celou plochu v prohlížeči.
    entry<DownloadsRoute>(
        metadata = Panes.list(),
    ) {
        DownloadsScreen(
            sources = rememberDownloadSources(),
            onBack = { chatsStack.remove(DownloadsRoute) },
            onOpenMessage = { slot, chatId, messageId -> chatsStack.openFoundMessage(slot, chatId, messageId, topicId = null) },
            onOpenMedia = { slot, chatId, messageId -> chatsStack.add(MediaRoute(slot, chatId, messageId)) },
        )
    }

    // Moje příběhy: mřížka přes list panel, prohlížeč z ní přes celou plochu (jako z profilu).
    entry<MyStoriesRoute>(
        metadata = Panes.list(),
    ) {
        val slot = LocalAccount.current.slot
        MyStoriesScreen(
            viewModel = koinViewModel(),
            onBack = { chatsStack.remove(MyStoriesRoute) },
            onOpenStories = { chatId, storyIds, start -> chatsStack.openStories(slot, chatId, storyIds, start) },
        )
    }

    entry<NewChatRoute>(
        metadata = Panes.list(),
    ) { route ->
        val slot = route.slot
        NewChatScreen(
            // Klíč s účtem: po změně účtu je to jiná routa, ale ať se ViewModel jiného účtu
            // nepotká s tímhle ani mimo úložiště položky.
            viewModel = koinViewModel(key = "new-chat-${slot.id}"),
            onBack = { chatsStack.remove(route) },
            onChatSelected = { chatId ->
                chatsStack.remove(route)
                chatsStack.openConversation(slot, chatId)
            },
            onNewGroup = { if (chatsStack.lastOrNull() !is NewGroupMembersRoute) chatsStack.add(NewGroupMembersRoute(slot)) },
            onNewChannel = { chatsStack.add(CreateChatRoute(slot, channel = true)) },
            onNewContact = { if (chatsStack.lastOrNull() !is NewContactRoute) chatsStack.add(NewContactRoute(slot)) },
            // Jiný účet = jiná routa na tomtéž místě zásobníku; obrazovka se složí v jeho kontejneru.
            onSwitchAccount = { other ->
                val index = chatsStack.indexOf(route)
                if (index >= 0 && other != slot) chatsStack[index] = NewChatRoute(other)
            },
            modifier = Modifier.listPaneInsets(twoPane),
        )
    }

    /*
     * Nový kontakt. Formulář přes celou plochu jako založení skupiny; po přidání se odebere
     * i „Nová zpráva" pod ním a otevře se konverzace — zpět z ní vede do seznamu.
     */
    entry<NewContactRoute> { route ->
        val slot = route.slot
        NewContactScreen(
            viewModel = koinViewModel(),
            onBack = { chatsStack.remove(route) },
            onAdded = { chatId ->
                chatsStack.removeAll { it is NewContactRoute || it is NewChatRoute }
                chatsStack.openConversation(slot, chatId)
            },
        )
    }

    /*
     * Založení skupiny a kanálu. Bez metadat scény, jako přeposílání: formulář jde přes celou
     * plochu i na široké obrazovce. Po založení se celá větev (nová zpráva, členové, formulář)
     * odebere a otevře se nový chat — zpět z něj vede do seznamu, ne zpátky do formuláře.
     */
    entry<NewGroupMembersRoute> { route ->
        val slot = route.slot
        NewGroupMembersScreen(
            // Vlastní klíč: výběr členů nesmí sdílet hledání s „Novou zprávou" pod sebou.
            viewModel = koinViewModel(key = "new-group-members-${slot.id}"),
            onBack = { chatsStack.remove(route) },
            onNext = { members -> chatsStack.add(CreateChatRoute(slot, channel = false, memberChatIds = members)) },
        )
    }

    entry<CreateChatRoute> { route ->
        val slot = LocalAccount.current.slot
        CreateChatScreen(
            viewModel = koinViewModel(
                key = "create-chat-${route.channel}-${route.memberChatIds.joinToString(",")}",
                parameters = { parametersOf(route.channel, route.memberChatIds) },
            ),
            onBack = { chatsStack.pop(route) },
            onCreated = { chatId ->
                chatsStack.removeAll { it is CreateChatRoute || it is NewGroupMembersRoute || it is NewChatRoute }
                chatsStack.openConversation(slot, chatId)
            },
        )
    }

    // Vlákno komentářů vedle kanálu je boční sloupec (`ConversationRoute.beside`), jinak detail.
    entry<ConversationRoute>(
        metadata = { route ->
            (if (route.beside) Panes.extra() else Panes.detail()) + Panes.ownSurface() + Panes.headerOutside() + ChatScreenMetadata
        },
    ) { route ->
        val slot = LocalAccount.current.slot
        val sharedChats = koinInject<SharedChats>()
        ColoredChatContainer(route.chatId, fromList = remember(route) { chatsStack.liesOnChatList(route) }, onBack = { chatsStack.pop(route) }) {
            val settings = koinInject<AppSettings>()
            val sideBySide = LocalPanesSideBySide.current
            ConversationScreen(
                // Klíč MUSÍ obsahovat chatId. ViewModel se cachuje podle klíče, takže
                // konstantní klíč znamená, že druhá otevřená konverzace dostane ViewModel
                // té první — i se zprávami. Koin navíc u cachované instance ignoruje
                // nové `parameters`, takže se to neprojeví ani chybou, jen špatným obsahem.
                viewModel = koinViewModel(
                    key = "conversation-${route.chatId}-${route.messageId}-${route.threadId}-${route.topicId}-${route.savedTopicId}-${route.directTopicId}",
                    parameters = {
                        parametersOf(route.chatId, route.messageId, route.threadId, route.topicId, route.savedTopicId, route.directTopicId)
                    },
                ),
                navigation = ConversationNavigation(
                    // Odebírá SVOU položku, ne poslední — viz `pop`. Vedle seznamu zavře celou větev nad ním.
                    onBack = { chatsStack.closeConversation(route, list = chatsStack.mainListBelow(route), sideBySide = sideBySide) },
                    onChatGone = { chatsStack.closeChat(route.slot, route.chatId) },
                    onOpenMedia = { messageId ->
                        chatsStack.add(MediaRoute(slot, route.chatId, messageId))
                    },
                    onOpenMediaAt = { messageId, seconds ->
                        chatsStack.add(MediaRoute(slot, route.chatId, messageId, startSeconds = seconds))
                    },
                    onOpenPollMedia = { messageId, index ->
                        chatsStack.add(MediaRoute(slot, route.chatId, messageId, pollMedia = index))
                    },
                    onOpenArticleMedia = { messageId, index ->
                        chatsStack.add(MediaRoute(slot, route.chatId, messageId, articleMedia = index))
                    },
                    onOpenSponsoredMedia = { messageId ->
                        chatsStack.add(MediaRoute(slot, route.chatId, messageId, sponsored = true))
                    },
                    onOpenStory = { story ->
                        chatsStack.add(MediaRoute(slot, story.posterChatId, messageId = 0, storyId = story.storyId))
                    },
                    onOpenVideoChat = { groupCallId -> chatsStack.openVideoChat(slot, route.chatId, groupCallId) },
                    onForward = { messageIds ->
                        chatsStack.add(ForwardRoute(slot, route.chatId, messageIds))
                    },
                    onReplyElsewhere = { messageId -> chatsStack.add(ReplyElsewhereRoute(slot, route.chatId, messageId)) },
                    // Zmínka i odkaz otevře chat NAD touhle konverzací (ne místo ní), takže zpět vede sem.
                    // Odkaz na zprávu nese její id a konverzace na ni po otevření skočí.
                    // Komentáře jsou vlákno diskusní skupiny nad kanálem, téma jde rovnou do tématu
                    // a skupina s tématy do seznamu témat (`openTarget`).
                    // Na široké ploše jdou komentáře do bočního sloupce vedle kanálu (`openCommentsBeside`).
                    onOpenChat = { target -> chatsStack.openTarget(slot, target, archive = false, from = route, beside = twoPane) },
                    onSearch = { query -> navigation.openSearch(slot, query, keepConversation = twoPane) },
                    onOpenProfile = { chatsStack.toggleProfile(route, ChatProfileRoute(slot, route.chatId), sideBySide = wide) },
                    // Kalendář zpráv jako třetí panel (na telefonu přes celou obrazovku) — tam, kde jde
                    // skok na datum (ne vlákno komentářů, ne téma skupiny).
                    onOpenCalendar = if (route.threadId == null && route.topicId == null) {
                        { anchor ->
                            chatsStack.toggleCalendar(
                                route,
                                ChatCalendarRoute(slot, route.chatId, savedTopicId = route.savedTopicId, directTopicId = route.directTopicId, anchor = anchor),
                            )
                        }
                    } else {
                        null
                    },
                    onOpenUserProfile = { chatId -> chatsStack.add(ChatProfileRoute(slot, chatId)) },
                    onOpenPostStatistics = { messageId -> chatsStack.addOnce(PostStatisticsRoute(slot, route.chatId, messageId)) },
                    onCreatePoll = { request -> chatsStack.add(pollComposerRoute(slot, route.chatId, request)) },
                    onCreateChecklist = { request -> chatsStack.add(checklistComposerRoute(slot, route.chatId, request)) },
                    onOpenWebApp = { request -> chatsStack.add(webAppRoute(slot, route.chatId, request, archive = false)) },
                    onOpenBotShare = { request -> chatsStack.add(botShareRoute(slot, route.chatId, request)) },
                    onAddBotToGroup = { link -> chatsStack.add(AddBotToGroupRoute(slot, link.url, link.botChatId, link.asAdmin)) },
                    onOpenScreen = { screen -> navigation.openScreen(slot, screen, twoPane) },
                    onSwitchInline = { pick -> chatsStack.add(pick.toRoute(slot)) },
                    onAddMembers = { chatsStack.addOnce(AddMembersRoute(slot, route.chatId)) },
                    onShareContact = { userId -> chatsStack.addOnce(ShareContactRoute(slot, userId)) },
                    onEditChat = { chatsStack.addOnce(EditChatRoute(slot, route.chatId)) },
                    onOpenJoinRequests = { chatsStack.addOnce(JoinRequestsRoute(slot, route.chatId)) },
                    // Uložené zprávy po chatech: volba se pamatuje a seznam témat nahradí konverzaci.
                    onViewAsChats = {
                        settings.setSavedMessagesAsChats(true)
                        chatsStack.openSavedTopics(slot, route.chatId)
                    },
                    // Skupina s tématy otevřená jako zprávy: volbu uloží ViewModel, seznam témat nahradí konverzaci.
                    onViewAsTopics = { chatsStack.openForum(slot, route.chatId) },
                    // Spravované přímé zprávy kanálu: konverzaci nahradí seznam vláken na TÉMŽE místě.
                    onViewAsDirectTopics = { chatsStack.replaceRoute(route, DirectTopicsRoute(slot, route.chatId)) },
                    onSwitchChat = rememberSwitchChat(navigation),
                    // Spojený chat jiným účtem: táž konverzace na TÉMŽE místě (bez skoku na zprávu
                    // z hledání) a seznam ji tím účtem otevře příště. Jiné id (Uložené zprávy) =
                    // jiná konverzace, téma Uložených zpráv z prvního účtu tam nepatří.
                    onSwitchAccount = { target, chatId ->
                        sharedChats.prefer(chatId, target)
                        chatsStack.replaceRoute(route, route.switchedTo(target, chatId))
                    },
                ),
            )
        }
    }

    /*
     * Témata Uložených zpráv stojí na místě konverzace (detail panel), jako témata skupiny;
     * téma se otevře jako konverzace nad nimi.
     */
    entry<SavedTopicsRoute>(metadata = Panes.detail() + ChatScreenMetadata) { route ->
        val slot = LocalAccount.current.slot
        ColoredChatContainer(route.chatId, fromList = remember(route) { chatsStack.liesOnChatList(route) }, onBack = { chatsStack.pop(route) }) {
            SavedTopicsScreen(
                viewModel = koinViewModel(key = "saved-topics-${route.chatId}", parameters = { parametersOf(route.chatId) }),
                onBack = { chatsStack.pop(route) },
                onOpenTopic = { topicId ->
                    if ((chatsStack.lastOrNull() as? ConversationRoute)?.savedTopicId != topicId) {
                        chatsStack.add(ConversationRoute(slot, route.chatId, savedTopicId = topicId))
                    }
                },
                onViewAsMessages = { chatsStack.openConversation(slot, route.chatId) },
            )
        }
    }

    /*
     * Přímé zprávy kanálu, jak je vidí správce: vlákna po odběratelích na místě konverzace;
     * vlákno se otevře jako konverzace nad nimi.
     */
    entry<DirectTopicsRoute>(metadata = Panes.detail() + ChatScreenMetadata) { route ->
        val slot = LocalAccount.current.slot
        ColoredChatContainer(route.chatId, fromList = remember(route) { chatsStack.liesOnChatList(route) }, onBack = { chatsStack.pop(route) }) {
            DirectTopicsScreen(
                viewModel = koinViewModel(key = "direct-topics-${route.chatId}", parameters = { parametersOf(route.chatId) }),
                onBack = { chatsStack.pop(route) },
                onOpenTopic = { topicId ->
                    if ((chatsStack.lastOrNull() as? ConversationRoute)?.directTopicId != topicId) {
                        chatsStack.add(ConversationRoute(slot, route.chatId, directTopicId = topicId))
                    }
                },
                onOpenProfile = { chatsStack.toggleProfile(route, ChatProfileRoute(slot, route.chatId), sideBySide = wide) },
            )
        }
    }

    /*
     * Témata skupiny s tématy stojí na místě konverzace (detail panel); téma se otevře jako
     * konverzace NAD nimi, takže zpět vede do seznamu témat.
     */
    entry<ForumTopicsRoute>(metadata = Panes.detail() + ChatScreenMetadata) { route ->
        val slot = LocalAccount.current.slot
        ColoredChatContainer(route.chatId, fromList = remember(route) { chatsStack.liesOnChatList(route) }, onBack = { chatsStack.pop(route) }) {
            ForumTopicsScreen(
                viewModel = koinViewModel(key = "forum-${route.chatId}", parameters = { parametersOf(route.chatId) }),
                onBack = { chatsStack.pop(route) },
                onOpenTopic = { topicId ->
                    if ((chatsStack.lastOrNull() as? ConversationRoute)?.topicId != topicId) {
                        chatsStack.add(ConversationRoute(slot, route.chatId, topicId = topicId))
                    }
                },
                onOpenProfile = { chatsStack.toggleProfile(route, ChatProfileRoute(slot, route.chatId), sideBySide = wide) },
                // Volba se pamatuje (na serveru) a konverzace seznam témat nahradí, jako u Uložených zpráv.
                onViewAsMessages = { chatsStack.openConversation(slot, route.chatId) },
                onOpenJoinRequests = { chatsStack.addOnce(JoinRequestsRoute(slot, route.chatId)) },
            )
        }
    }

    entry<ArchiveDirectTopicsRoute>(metadata = Panes.detail(ARCHIVE_SCENE) + ChatScreenMetadata) { route ->
        val slot = LocalAccount.current.slot
        ColoredChatContainer(route.chatId, fromList = remember(route) { chatsStack.liesOnChatList(route) }, onBack = { chatsStack.pop(route) }) {
            DirectTopicsScreen(
                viewModel = koinViewModel(key = "direct-topics-${route.chatId}", parameters = { parametersOf(route.chatId) }),
                onBack = { chatsStack.pop(route) },
                onOpenTopic = { topicId ->
                    if ((chatsStack.lastOrNull() as? ArchiveConversationRoute)?.directTopicId != topicId) {
                        chatsStack.add(ArchiveConversationRoute(slot, route.chatId, directTopicId = topicId))
                    }
                },
                onOpenProfile = { chatsStack.toggleProfile(route, ArchiveChatProfileRoute(slot, route.chatId), sideBySide = wide) },
            )
        }
    }

    entry<ArchiveForumTopicsRoute>(metadata = Panes.detail(ARCHIVE_SCENE) + ChatScreenMetadata) { route ->
        val slot = LocalAccount.current.slot
        ColoredChatContainer(route.chatId, fromList = remember(route) { chatsStack.liesOnChatList(route) }, onBack = { chatsStack.pop(route) }) {
            ForumTopicsScreen(
                viewModel = koinViewModel(key = "forum-${route.chatId}", parameters = { parametersOf(route.chatId) }),
                onBack = { chatsStack.pop(route) },
                onOpenTopic = { topicId ->
                    if ((chatsStack.lastOrNull() as? ArchiveConversationRoute)?.topicId != topicId) {
                        chatsStack.add(ArchiveConversationRoute(slot, route.chatId, topicId = topicId))
                    }
                },
                onOpenProfile = { chatsStack.toggleProfile(route, ArchiveChatProfileRoute(slot, route.chatId), sideBySide = wide) },
                onViewAsMessages = { chatsStack.openArchiveConversation(slot, route.chatId) },
                onOpenJoinRequests = { chatsStack.addOnce(ArchiveJoinRequestsRoute(slot, route.chatId)) },
            )
        }
    }

    // Článek je detail jako konverzace: na širokém rozvržení ji v pravém panelu nahradí.
    entry<InstantViewRoute>(metadata = Panes.detail() + Panes.ownSurface()) { route ->
        InstantViewScreen(
            viewModel = koinViewModel(key = "instant-view-${route.url}", parameters = { parametersOf(route.url) }),
            onBack = { chatsStack.pop(route) },
        )
    }

    /*
     * Kalendář zpráv jako třetí panel (extra), jako profil: na velké ploše vedle seznamu
     * a konverzace, na střední vedle konverzace (seznam se schová). Šířku sdílí s profilem —
     * třetí panel je jeden a táhlo profilu ji nastavuje. Jiná konverzace otevřená vedle ho
     * vystřídá kalendářem té nové (`followProfile`).
     */
    entry<ChatCalendarRoute>(metadata = Panes.extra() + Panes.headerOutside()) { route ->
        ChatColorTheme(route.chatId, onBack = { chatsStack.pop(route) }) {
            ChatCalendarPaneHost(route.chatId, route.savedTopicId, route.directTopicId, route.anchor, onClose = { chatsStack.pop(route) })
        }
    }

    entry<ArchiveChatCalendarRoute>(metadata = Panes.extra(ARCHIVE_SCENE) + Panes.headerOutside()) { route ->
        ChatColorTheme(route.chatId, onBack = { chatsStack.pop(route) }) {
            ChatCalendarPaneHost(route.chatId, savedTopicId = null, route.directTopicId, route.anchor, onClose = { chatsStack.pop(route) })
        }
    }

    /*
     * Profil je třetí panel (extra): na velké ploše se otevře vedle seznamu a konverzace, na
     * střední vedle konverzace (seznam se schová), na telefonu ji nahradí. Totéž obrazovky, které
     * se z něj otevírají — jako detail by nahradily konverzaci uprostřed. Jiná konverzace
     * otevřená nad profilem ho na široké ploše vystřídá profilem té nové (`followProfile`).
     */
    entry<ChatProfileRoute>(metadata = Panes.extra() + Panes.headerOutside()) { route ->
        val slot = LocalAccount.current.slot
        val searchRequests = koinInject<ConversationSearchRequests>()
        val jumpRequests = koinInject<ConversationJumpRequests>()
        ChatColorTheme(route.chatId, onBack = { chatsStack.pop(route) }) {
            ChatProfileScreen(
                viewModel = koinViewModel(key = "profile-${route.chatId}", parameters = { parametersOf(route.chatId) }),
                onBack = { chatsStack.pop(route) },
                onOpenStories = { storyIds, start -> chatsStack.openStories(slot, route.chatId, storyIds, start) },
                // Hledat patří konverzaci: profil se zavře a konverzace pod ním otevře hledání.
                // Profil odesílatele otevřený ze skupiny má pod sebou SKUPINU — pak se místo ní
                // otevře konverzace s ním, jinak by hledání čekalo na chat, který není vidět.
                onSearch = {
                    searchRequests.request(route.chatId)
                    chatsStack.pop(route)
                    if (!chatsStack.isOpenConversation(slot, route.chatId)) chatsStack.add(ConversationRoute(slot, route.chatId))
                },
                onOpenChat = { chatId ->
                    if (!chatsStack.isOpenConversation(slot, chatId)) chatsStack.add(ConversationRoute(slot, chatId))
                },
                // Z profilu letí fotka z mřížky sdílených médií, ne z bubliny — jiný klíč sdíleného prvku.
                onOpenMedia = { messageId, articleMedia ->
                    chatsStack.add(MediaRoute(slot, route.chatId, messageId, fromSharedMedia = true, articleMedia = articleMedia))
                },
                onOpenPhotos = { chatsStack.add(MediaRoute(slot, route.chatId, messageId = 0L, profilePhotos = true)) },
                onEdit = { if (chatsStack.lastOrNull() != EditChatRoute(slot, route.chatId)) chatsStack.add(EditChatRoute(slot, route.chatId)) },
                onShareContact = { userId -> chatsStack.addOnce(ShareContactRoute(slot, userId)) },
                onAddMembers = { chatsStack.addOnce(AddMembersRoute(slot, route.chatId)) },
                onOpenMemberRights = { userId, admin ->
                    chatsStack.add(MemberRightsRoute(slot, route.chatId, userId, admin))
                },
                // Soukromý chat s botem má id bota, takže chat je zároveň bot.
                onOpenMainWebApp = {
                    chatsStack.add(WebAppRoute(slot, route.chatId, route.chatId, url = "", launch = false, archive = false, main = true))
                },
                onOpenMessage = { messageId -> chatsStack.openMessageFromProfile(jumpRequests, slot, route.chatId, messageId, archive = false) },
                onOpenBookmarks = { navigation.openSheet(ChatsRoute, BookmarksRoute(slot, route.chatId)) },
                paneRoot = LocalPanesSideBySide.current && chatsStack.standsOnDetail(route),
                besideOwnConversation = LocalPanesSideBySide.current && chatsStack.standsOnOwnConversation(route),
            )
        }
    }

    // Úprava se vrší nad profil, ve třetím panelu ho nahradí.
    entry<EditChatRoute>(metadata = Panes.extra() + Panes.headerOutside()) { route ->
        val slot = LocalAccount.current.slot
        EditChatScreen(
            viewModel = koinViewModel(key = "edit-chat-${route.chatId}", parameters = { parametersOf(route.chatId) }),
            onBack = { chatsStack.pop(route) },
            onOpenInviteLinks = { chatsStack.addOnce(InviteLinksRoute(slot, route.chatId)) },
            onOpenJoinRequests = { chatsStack.addOnce(JoinRequestsRoute(slot, route.chatId)) },
            onOpenRemoved = { banned -> chatsStack.addOnce(RemovedMembersRoute(slot, route.chatId, banned)) },
            onOpenEventLog = { chatsStack.addOnce(EventLogRoute(slot, route.chatId)) },
            onOpenStatistics = { chatsStack.addOnce(ChatStatisticsRoute(slot, route.chatId)) },
            onDeleted = { chatsStack.closeChat(route.slot, route.chatId) },
            onUpgraded = { old, new -> chatsStack.replaceUpgradedChat(route.slot, old, new) },
        )
    }

    entry<ArchiveEditChatRoute>(metadata = Panes.extra(ARCHIVE_SCENE) + Panes.headerOutside()) { route ->
        val slot = LocalAccount.current.slot
        EditChatScreen(
            viewModel = koinViewModel(key = "edit-chat-${route.chatId}", parameters = { parametersOf(route.chatId) }),
            onBack = { chatsStack.pop(route) },
            onOpenInviteLinks = { chatsStack.addOnce(ArchiveInviteLinksRoute(slot, route.chatId)) },
            onOpenJoinRequests = { chatsStack.addOnce(ArchiveJoinRequestsRoute(slot, route.chatId)) },
            onOpenRemoved = { banned -> chatsStack.addOnce(ArchiveRemovedMembersRoute(slot, route.chatId, banned)) },
            onOpenEventLog = { chatsStack.addOnce(ArchiveEventLogRoute(slot, route.chatId)) },
            onOpenStatistics = { chatsStack.addOnce(ArchiveChatStatisticsRoute(slot, route.chatId)) },
            onDeleted = { chatsStack.closeChat(route.slot, route.chatId) },
            onUpgraded = { old, new -> chatsStack.replaceUpgradedChat(route.slot, old, new) },
        )
    }

    // Pozvánky a žádosti se vrší nad úpravu, taky ve třetím panelu.
    entry<InviteLinksRoute>(metadata = Panes.extra() + Panes.headerOutside()) { route ->
        InviteLinksScreen(
            viewModel = koinViewModel(key = "invite-links-${route.chatId}", parameters = { parametersOf(route.chatId) }),
            onBack = { chatsStack.pop(route) },
        )
    }

    entry<ArchiveInviteLinksRoute>(metadata = Panes.extra(ARCHIVE_SCENE) + Panes.headerOutside()) { route ->
        InviteLinksScreen(
            viewModel = koinViewModel(key = "invite-links-${route.chatId}", parameters = { parametersOf(route.chatId) }),
            onBack = { chatsStack.pop(route) },
        )
    }

    entry<JoinRequestsRoute>(metadata = Panes.extra() + Panes.headerOutside()) { route ->
        JoinRequestsScreen(
            viewModel = koinViewModel(key = "join-requests-${route.chatId}", parameters = { parametersOf(route.chatId) }),
            onBack = { chatsStack.pop(route) },
        )
    }

    entry<ArchiveJoinRequestsRoute>(metadata = Panes.extra(ARCHIVE_SCENE) + Panes.headerOutside()) { route ->
        JoinRequestsScreen(
            viewModel = koinViewModel(key = "join-requests-${route.chatId}", parameters = { parametersOf(route.chatId) }),
            onBack = { chatsStack.pop(route) },
        )
    }

    entry<EventLogRoute>(metadata = Panes.extra() + Panes.headerOutside()) { route ->
        EventLogScreen(
            viewModel = koinViewModel(key = "event-log-${route.chatId}", parameters = { parametersOf(route.chatId) }),
            onBack = { chatsStack.pop(route) },
        )
    }

    entry<ArchiveEventLogRoute>(metadata = Panes.extra(ARCHIVE_SCENE) + Panes.headerOutside()) { route ->
        EventLogScreen(
            viewModel = koinViewModel(key = "event-log-${route.chatId}", parameters = { parametersOf(route.chatId) }),
            onBack = { chatsStack.pop(route) },
        )
    }

    // Statistiky se vrší nad úpravu, statistika příspěvku nad ně nebo nad konverzaci.
    entry<ChatStatisticsRoute>(metadata = Panes.extra() + Panes.headerOutside()) { route ->
        val slot = LocalAccount.current.slot
        ChatStatisticsScreen(
            viewModel = koinViewModel(key = "chat-statistics-${route.chatId}", parameters = { parametersOf(route.chatId) }),
            onBack = { chatsStack.pop(route) },
            onOpenPost = { messageId -> chatsStack.addOnce(PostStatisticsRoute(slot, route.chatId, messageId)) },
        )
    }

    entry<ArchiveChatStatisticsRoute>(metadata = Panes.extra(ARCHIVE_SCENE) + Panes.headerOutside()) { route ->
        val slot = LocalAccount.current.slot
        ChatStatisticsScreen(
            viewModel = koinViewModel(key = "chat-statistics-${route.chatId}", parameters = { parametersOf(route.chatId) }),
            onBack = { chatsStack.pop(route) },
            onOpenPost = { messageId -> chatsStack.addOnce(ArchivePostStatisticsRoute(slot, route.chatId, messageId)) },
        )
    }

    entry<PostStatisticsRoute>(metadata = Panes.extra() + Panes.headerOutside()) { route ->
        val slot = LocalAccount.current.slot
        PostStatisticsScreen(
            viewModel = koinViewModel(
                key = "post-statistics-${route.chatId}-${route.messageId}",
                parameters = { parametersOf(route.chatId, route.messageId) },
            ),
            onBack = { chatsStack.pop(route) },
            onOpenMessage = { chatId, messageId -> chatsStack.openTarget(slot, ChatTarget(chatId, messageId), archive = false) },
        )
    }

    entry<ArchivePostStatisticsRoute>(metadata = Panes.extra(ARCHIVE_SCENE) + Panes.headerOutside()) { route ->
        val slot = LocalAccount.current.slot
        PostStatisticsScreen(
            viewModel = koinViewModel(
                key = "post-statistics-${route.chatId}-${route.messageId}",
                parameters = { parametersOf(route.chatId, route.messageId) },
            ),
            onBack = { chatsStack.pop(route) },
            onOpenMessage = { chatId, messageId -> chatsStack.openTarget(slot, ChatTarget(chatId, messageId), archive = true) },
        )
    }

    /*
     * Omezení a vyhození se vrší nad úpravu. Klíč nese i druh seznamu: ViewModel se cachuje
     * podle klíče a Koin u cachované instance ignoruje nové parametry.
     */
    entry<RemovedMembersRoute>(metadata = Panes.extra() + Panes.headerOutside()) { route ->
        val slot = LocalAccount.current.slot
        RemovedMembersScreen(
            viewModel = koinViewModel(
                key = "removed-members-${route.chatId}-${route.banned}",
                parameters = { parametersOf(route.chatId, route.banned) },
            ),
            onBack = { chatsStack.pop(route) },
            onOpenRestriction = { userId -> chatsStack.addOnce(MemberRightsRoute(slot, route.chatId, userId, admin = false)) },
        )
    }

    entry<ArchiveRemovedMembersRoute>(metadata = Panes.extra(ARCHIVE_SCENE) + Panes.headerOutside()) { route ->
        val slot = LocalAccount.current.slot
        RemovedMembersScreen(
            viewModel = koinViewModel(
                key = "removed-members-${route.chatId}-${route.banned}",
                parameters = { parametersOf(route.chatId, route.banned) },
            ),
            onBack = { chatsStack.pop(route) },
            onOpenRestriction = { userId -> chatsStack.addOnce(ArchiveMemberRightsRoute(slot, route.chatId, userId, admin = false)) },
        )
    }

    /*
     * Práva člena se vrší nad profil stejně jako úprava skupiny a ve třetím panelu ho
     * nahradí. Klíč nese i `userId` a druh práv: ViewModel se cachuje
     * podle klíče a Koin u cachované instance ignoruje nové parametry.
     */
    entry<MemberRightsRoute>(metadata = Panes.extra() + Panes.headerOutside()) { route ->
        MemberRightsScreen(
            viewModel = koinViewModel(
                key = "member-rights-${route.chatId}-${route.userId}-${route.admin}",
                parameters = { parametersOf(route.chatId, route.userId, route.admin) },
            ),
            onBack = { chatsStack.pop(route) },
        )
    }

    entry<ArchiveMemberRightsRoute>(metadata = Panes.extra(ARCHIVE_SCENE) + Panes.headerOutside()) { route ->
        MemberRightsScreen(
            viewModel = koinViewModel(
                key = "member-rights-${route.chatId}-${route.userId}-${route.admin}",
                parameters = { parametersOf(route.chatId, route.userId, route.admin) },
            ),
            onBack = { chatsStack.pop(route) },
        )
    }

    entry<ArchiveConversationRoute>(
        metadata = { route ->
            (if (route.beside) Panes.extra(ARCHIVE_SCENE) else Panes.detail(ARCHIVE_SCENE)) + Panes.ownSurface() + Panes.headerOutside() +
                ChatScreenMetadata
        },
    ) { route ->
        val slot = LocalAccount.current.slot
        val sharedChats = koinInject<SharedChats>()
        ColoredChatContainer(route.chatId, fromList = remember(route) { chatsStack.liesOnChatList(route) }, onBack = { chatsStack.pop(route) }) {
            val sideBySide = LocalPanesSideBySide.current
            ConversationScreen(
                viewModel = koinViewModel(
                    key = "conversation-${route.chatId}-${route.messageId}-${route.threadId}-${route.topicId}-${route.directTopicId}",
                    // Pátý parametr (téma Uložených zpráv) v archivu není — Uložené archivovat nejdou.
                    parameters = {
                        parametersOf(route.chatId, route.messageId, route.threadId, route.topicId, null, route.directTopicId)
                    },
                ),
                navigation = ConversationNavigation(
                    onBack = { chatsStack.closeConversation(route, list = ArchiveRoute, sideBySide = sideBySide) },
                    onChatGone = { chatsStack.closeChat(route.slot, route.chatId) },
                    onOpenMedia = { messageId ->
                        chatsStack.add(MediaRoute(slot, route.chatId, messageId))
                    },
                    onOpenMediaAt = { messageId, seconds ->
                        chatsStack.add(MediaRoute(slot, route.chatId, messageId, startSeconds = seconds))
                    },
                    onOpenPollMedia = { messageId, index ->
                        chatsStack.add(MediaRoute(slot, route.chatId, messageId, pollMedia = index))
                    },
                    onOpenArticleMedia = { messageId, index ->
                        chatsStack.add(MediaRoute(slot, route.chatId, messageId, articleMedia = index))
                    },
                    onOpenSponsoredMedia = { messageId ->
                        chatsStack.add(MediaRoute(slot, route.chatId, messageId, sponsored = true))
                    },
                    onOpenStory = { story ->
                        chatsStack.add(MediaRoute(slot, story.posterChatId, messageId = 0, storyId = story.storyId))
                    },
                    onOpenVideoChat = { groupCallId -> chatsStack.openVideoChat(slot, route.chatId, groupCallId) },
                    onForward = { messageIds ->
                        chatsStack.add(ForwardRoute(slot, route.chatId, messageIds))
                    },
                    onReplyElsewhere = { messageId -> chatsStack.add(ReplyElsewhereRoute(slot, route.chatId, messageId)) },
                    onOpenChat = { target -> chatsStack.openTarget(slot, target, archive = true, from = route, beside = twoPane) },
                    onSearch = { query -> navigation.openSearch(slot, query, keepConversation = false) },
                    onOpenProfile = { chatsStack.toggleProfile(route, ArchiveChatProfileRoute(slot, route.chatId), sideBySide = wide) },
                    onOpenCalendar = if (route.threadId == null && route.topicId == null) {
                        { anchor ->
                            chatsStack.toggleCalendar(route, ArchiveChatCalendarRoute(slot, route.chatId, directTopicId = route.directTopicId, anchor = anchor))
                        }
                    } else {
                        null
                    },
                    onOpenUserProfile = { chatId -> chatsStack.add(ArchiveChatProfileRoute(slot, chatId)) },
                    onOpenPostStatistics = { messageId -> chatsStack.addOnce(ArchivePostStatisticsRoute(slot, route.chatId, messageId)) },
                    onUnarchived = { chatsStack.moveConversationToMain(route.slot, route.chatId) },
                    onCreatePoll = { request -> chatsStack.add(pollComposerRoute(slot, route.chatId, request)) },
                    onCreateChecklist = { request -> chatsStack.add(checklistComposerRoute(slot, route.chatId, request)) },
                    onOpenWebApp = { request -> chatsStack.add(webAppRoute(slot, route.chatId, request, archive = true)) },
                    onOpenBotShare = { request -> chatsStack.add(botShareRoute(slot, route.chatId, request)) },
                    onAddBotToGroup = { link -> chatsStack.add(AddBotToGroupRoute(slot, link.url, link.botChatId, link.asAdmin)) },
                    onOpenScreen = { screen -> navigation.openScreen(slot, screen, twoPane = false) },
                    onSwitchInline = { pick -> chatsStack.add(pick.toRoute(slot)) },
                    onAddMembers = { chatsStack.addOnce(AddMembersRoute(slot, route.chatId)) },
                    onShareContact = { userId -> chatsStack.addOnce(ShareContactRoute(slot, userId)) },
                    onEditChat = { chatsStack.addOnce(ArchiveEditChatRoute(slot, route.chatId)) },
                    onOpenJoinRequests = { chatsStack.addOnce(ArchiveJoinRequestsRoute(slot, route.chatId)) },
                    onViewAsTopics = { chatsStack.openArchiveForum(slot, route.chatId) },
                    onViewAsDirectTopics = { chatsStack.replaceRoute(route, ArchiveDirectTopicsRoute(slot, route.chatId)) },
                    onSwitchChat = rememberSwitchChat(navigation),
                    onSwitchAccount = { target, chatId ->
                        sharedChats.prefer(chatId, target)
                        chatsStack.replaceRoute(route, route.copy(slot = target, chatId = chatId, messageId = null))
                    },
                ),
            )
        }
    }

    entry<ArchiveChatProfileRoute>(metadata = Panes.extra(ARCHIVE_SCENE) + Panes.headerOutside()) { route ->
        val slot = LocalAccount.current.slot
        val searchRequests = koinInject<ConversationSearchRequests>()
        val jumpRequests = koinInject<ConversationJumpRequests>()
        ChatColorTheme(route.chatId, onBack = { chatsStack.pop(route) }) {
            ChatProfileScreen(
                viewModel = koinViewModel(key = "profile-${route.chatId}", parameters = { parametersOf(route.chatId) }),
                onBack = { chatsStack.pop(route) },
                onOpenStories = { storyIds, start -> chatsStack.openStories(slot, route.chatId, storyIds, start) },
                onSearch = {
                    searchRequests.request(route.chatId)
                    chatsStack.pop(route)
                    if (!chatsStack.isOpenArchiveConversation(slot, route.chatId)) {
                        chatsStack.add(ArchiveConversationRoute(slot, route.chatId))
                    }
                },
                onOpenChat = { chatId ->
                    if (!chatsStack.isOpenArchiveConversation(slot, chatId)) {
                        chatsStack.add(ArchiveConversationRoute(slot, chatId))
                    }
                },
                // Z profilu letí fotka z mřížky sdílených médií, ne z bubliny — jiný klíč sdíleného prvku.
                onOpenMedia = { messageId, articleMedia ->
                    chatsStack.add(MediaRoute(slot, route.chatId, messageId, fromSharedMedia = true, articleMedia = articleMedia))
                },
                onOpenPhotos = { chatsStack.add(MediaRoute(slot, route.chatId, messageId = 0L, profilePhotos = true)) },
                onEdit = {
                    if (chatsStack.lastOrNull() != ArchiveEditChatRoute(slot, route.chatId)) {
                        chatsStack.add(ArchiveEditChatRoute(slot, route.chatId))
                    }
                },
                onShareContact = { userId -> chatsStack.addOnce(ShareContactRoute(slot, userId)) },
                onAddMembers = { chatsStack.addOnce(AddMembersRoute(slot, route.chatId)) },
                onOpenMemberRights = { userId, admin ->
                    chatsStack.add(ArchiveMemberRightsRoute(slot, route.chatId, userId, admin))
                },
                // Soukromý chat s botem má id bota, takže chat je zároveň bot.
                onOpenMainWebApp = {
                    chatsStack.add(WebAppRoute(slot, route.chatId, route.chatId, url = "", launch = false, archive = true, main = true))
                },
                onOpenMessage = { messageId -> chatsStack.openMessageFromProfile(jumpRequests, slot, route.chatId, messageId, archive = true) },
                onOpenBookmarks = { navigation.openSheet(ChatsRoute, BookmarksRoute(slot, route.chatId, archive = true)) },
                paneRoot = LocalPanesSideBySide.current && chatsStack.standsOnDetail(route),
                besideOwnConversation = LocalPanesSideBySide.current && chatsStack.standsOnOwnConversation(route),
            )
        }
    }

    /*
     * Prohlížeč nemá metadata list-detail scény, takže ho scéna nepřevezme
     * a `NavDisplay` ho vykreslí přes celou plochu — i na širokém rozvržení,
     * kde by panel vedle seznamu chatů neměl co dělat.
     */
    entry<MediaRoute>(metadata = Panes.ownSurface()) { route ->
        val slot = LocalAccount.current.slot
        val replyRequests = koinInject<ConversationReplyRequests>()
        val sponsoredLinks = koinInject<SponsoredLinkRequests>()
        val jumpRequests = koinInject<ConversationJumpRequests>()
        val tabRequests = koinInject<ProfileTabRequests>()
        val continueWatching = koinInject<ContinueWatching>()
        /*
         * Klíč sdíleného prvku fotky nese, ODKUD se letí: táž fotka je v bublině i v mřížce
         * sdílených médií v profilu a se společným klíčem se ty dvě spárovaly mezi sebou —
         * při otevření profilu fotka letěla přes celou obrazovku (viz `photoKey`).
         */
        CompositionLocalProvider(
            LocalSharedMediaFlight provides route.fromSharedMedia,
            LocalPollMediaFlight provides route.pollMedia,
            LocalArticleMediaFlight provides route.articleMedia,
        ) {
            MediaViewerScreen(
                // Klíč musí rozlišovat zprávu ze stejného důvodu jako u konverzace:
                // ViewModel se cachuje podle klíče a Koin u cachované instance
                // ignoruje nové parametry. U ankety i přílohu — obrázků je pod jedním id víc.
                viewModel = koinViewModel(
                    key = "media-${route.chatId}-${route.messageId}-${route.pollMedia}-${route.profilePhotos}-${route.articleMedia}-${route.storyId}-${route.sponsored}",
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
                // Profilovky vlastního účtu se otevírají z Nastavení, všechno ostatní z Chatů.
                onBack = { chatsStack.pop(route) || settingsStack.pop(route) },
                onForward = { messageId ->
                    chatsStack.add(ForwardRoute(slot, route.chatId, listOf(messageId)))
                },
                // Samolepka z fotky: nad prohlížeč, zpět z ní vede k fotce.
                onMakeSticker = { path, width, height ->
                    val stack = if (chatsStack.lastOrNull() == route) chatsStack else settingsStack
                    stack.add(StickerComposerRoute(slot, path, width, height))
                },
                // Odpovědět jde jen tehdy, když prohlížeč leží přímo nad konverzací téhož chatu:
                // z profilu (mřížka médií) nebo ankety by nebylo kam se vrátit.
                onReply = conversationBelow(chatsStack, route)?.let { chatId ->
                    { messageId: Long ->
                        replyRequests.request(chatId, messageId)
                        chatsStack.pop(route)
                    }
                },
                startSeconds = route.startSeconds,
                // Návrat z okénka obrazu v obraze (desktop): prohlížeč znovu, na téže zprávě v čase.
                // Příběh a reklama ne — příběh má vlastní prohlížeč a reklamu otevírá jen konverzace.
                onReopenAt = if (route.storyId == NO_STORY && !route.sponsored) {
                    val stack = if (chatsStack.lastOrNull() == route) chatsStack else settingsStack
                    { messageId: Long, seconds: Int -> stack.add(route.copy(messageId = messageId, startSeconds = seconds)) }
                } else {
                    null
                },
                // Video reklamy se otevírá jen z konverzace, takže je pod prohlížečem a odkaz otevře ona.
                onOpenSponsoredLink = { url ->
                    sponsoredLinks.request(route.chatId, url)
                    chatsStack.pop(route)
                },
                // Jen média ze zpráv chatu: profilovky, příběh a reklama zprávou nejsou, příloha
                // ankety a článku se otvírá z té zprávy a stránky nesou její id.
                onShowInChat = if (route.showsChatMessages) {
                    { messageId: Long -> chatsStack.showInChat(route, messageId, jumpRequests) }
                } else {
                    null
                },
                onShowAllMedia = if (route.showsChatMessages) {
                    { chatsStack.showAllMedia(route, tabRequests) }
                } else {
                    null
                },
                // Rozkoukaná videa otevírají zprávu znovu, takže jen média ze zpráv chatu.
                positionSource = if (route.showsChatMessages) {
                    { messageId: Long -> continueWatching.sourceFor(route.chatId, messageId) }
                } else {
                    null
                },
            )
        }
    }

    /*
     * Prohlížeč příběhů. Stejně jako prohlížeč médií bez metadat list-detail scény, takže ho
     * `NavDisplay` vykreslí přes celou plochu i na širokém rozvržení.
     */
    entry<StoryComposerRoute>(metadata = Panes.ownSurface()) { route ->
        StoryComposerScreen(
            viewModel = koinViewModel(
                key = "story-composer-${route.path}-${route.editStoryId}-${route.editCoverOnly}",
                parameters = {
                    val edit = route.editStoryId?.let { id ->
                        route.editPosterChatId?.let { poster -> StoryEditTarget(poster, id, route.editCaption, coverOnly = route.editCoverOnly) }
                    }
                    parametersOf(route.path, route.video, edit)
                },
            ),
            onBack = { chatsStack.pop(route) },
            onPosted = { chatsStack.pop(route) },
        )
    }

    entry<VideoChatRoute>(metadata = Panes.ownSurface()) { route ->
        VideoChatScreen(
            viewModel = koinViewModel(
                key = "video-chat-${route.groupCallId}",
                parameters = { parametersOf(route.chatId, route.groupCallId) },
            ),
            onBack = { chatsStack.pop(route) },
        )
    }

    entry<StickerComposerRoute>(metadata = Panes.ownSurface()) { route ->
        StickerComposerScreen(
            viewModel = koinViewModel(
                key = "sticker-composer-${route.path}",
                parameters = { parametersOf(route.path, route.width, route.height, route.targetSetId) },
            ),
            onBack = { chatsStack.pop(route) || settingsStack.pop(route) },
        )
    }

    entry<StoriesRoute>(metadata = Panes.ownSurface()) { route ->
        val slot = LocalAccount.current.slot
        StoriesScreen(
            // Klíč s autorem ze stejného důvodu jako u prohlížeče médií (cachovaný ViewModel).
            viewModel = koinViewModel(
                key = "stories-${route.chatId}-${route.storyIds.hashCode()}-${route.startStoryId}",
                parameters = { parametersOf(route.chatId, route.storyIds, route.startStoryId) },
            ),
            onBack = { chatsStack.pop(route) },
            onForward = { posterChatId, storyId -> chatsStack.add(ForwardRoute(slot, posterChatId, emptyList(), storyId)) },
            onReplaceMedia = { target, path, video ->
                chatsStack.add(
                    StoryComposerRoute(slot, 
                        path = path,
                        video = video,
                        editPosterChatId = target.posterChatId,
                        editStoryId = target.storyId,
                        editCaption = target.caption,
                        editCoverOnly = target.coverOnly,
                    ),
                )
            },
        )
    }

    /*
     * Výběr cílového chatu. Po přeposlání se výběr ze zásobníku zahodí a otevře
     * se cílový chat — uživatel tak skončí tam, kam zprávu poslal, a zpět ho
     * vrátí do původní konverzace.
     */
    // Mini aplikace bota. Odkaz na chat z ní zavře ji a otevře chat nad konverzací pod ní.
    entry<WebAppRoute>(metadata = Panes.ownSurface()) { route ->
        val slot = LocalAccount.current.slot
        WebAppScreen(
            viewModel = koinViewModel(
                key = "web-app-${route.chatId}-${route.botUserId}-${route.url.hashCode()}-${route.main}-${route.shortName}-" +
                    "${route.startParameter}-${route.guardQueryId}",
                parameters = {
                    parametersOf(
                        route.chatId, route.botUserId, route.url, route.launch, route.keyboardButtonText,
                        route.main, route.startParameter, route.shortName, route.guardQueryId,
                    )
                },
            ),
            onClose = { chatsStack.pop(route) },
            onOpenChat = { target ->
                chatsStack.pop(route)
                chatsStack.openTarget(slot, target, archive = route.archive)
            },
            onOpenProfile = { chatId ->
                chatsStack.pop(route)
                chatsStack.add(if (route.archive) ArchiveChatProfileRoute(slot, chatId) else ChatProfileRoute(slot, chatId))
            },
        )
    }

    // Nová anketa. Po odeslání odejde — pod ní je konverzace, do které anketa přibyla.
    entry<PollComposerRoute> { route ->
        val mode = when {
            route.quizOnly -> PollComposerMode.QuizOnly
            route.regularOnly -> PollComposerMode.RegularOnly
            else -> PollComposerMode.Any
        }
        PollComposerScreen(
            viewModel = koinViewModel(
                key = "poll-composer-${route.chatId}-$mode-${route.replyToMessageId}",
                parameters = {
                    parametersOf(
                        route.chatId,
                        mode,
                        route.replyToMessageId,
                        route.quoteText?.let { ReplyQuote(text = it, position = route.quotePosition) },
                    )
                },
            ),
            onClose = { chatsStack.pop(route) },
        )
    }

    // Nový seznam úkolů. Po odeslání odejde, jako formulář ankety.
    entry<ChecklistComposerRoute> { route ->
        ChecklistComposerScreen(
            viewModel = koinViewModel(
                key = "checklist-composer-${route.chatId}-${route.replyToMessageId}-${route.editMessageId}",
                parameters = {
                    parametersOf(
                        route.chatId,
                        route.replyToMessageId,
                        route.quoteText?.let { ReplyQuote(text = it, position = route.quotePosition) },
                        route.editMessageId,
                    )
                },
            ),
            onClose = { chatsStack.pop(route) },
        )
    }

    /*
     * Výběr skupiny pro bota ze startovacího odkazu (`t.me/bot?startgroup=…`). Po přidání ho
     * nahradí skupina, kde bot právě odpověděl — jako u přeposílání.
     */
    entry<AddBotToGroupRoute> { route ->
        val slot = LocalAccount.current.slot
        AddBotToGroupScreen(
            viewModel = koinViewModel(
                key = "add-bot-${route.url}-${route.asAdmin}",
                parameters = { parametersOf(route.url, route.botChatId, route.asAdmin) },
            ),
            onBack = { chatsStack.pop(route) },
            onAdded = { chatId ->
                if (chatsStack.pop(route) && !chatsStack.isOpenConversation(slot, chatId)) {
                    chatsStack.add(ConversationRoute(slot, chatId))
                }
            },
        )
    }

    /*
     * Výběr chatu pro inline dotaz z tlačítka bota. Po výběru ho nahradí vybraná konverzace
     * s `@bot dotaz` v poli; dotaz si vyzvedne sama (`InlineQueryRequests`), i když je to ta
     * pod výběrem.
     */
    entry<SwitchInlineRoute> { route ->
        val slot = LocalAccount.current.slot
        val inlineQueryRequests = koinInject<InlineQueryRequests>()
        SwitchInlinePickerScreen(
            viewModel = koinViewModel(
                key = "switch-inline-${route.bot}-${route.users}${route.bots}${route.groups}${route.channels}",
                parameters = {
                    parametersOf(SwitchInlineTarget.Chosen(route.users, route.bots, route.groups, route.channels))
                },
            ),
            bot = route.bot,
            onBack = { chatsStack.pop(route) },
            onPick = { chatId ->
                inlineQueryRequests.request(chatId, route.bot, route.query)
                if (chatsStack.pop(route) && !chatsStack.isOpenConversation(slot, chatId)) {
                    chatsStack.add(ConversationRoute(slot, chatId))
                }
            },
        )
    }

    // Výběr lidí / chatu pro tlačítko bota. Po sdílení se vrátí do konverzace, kde tlačítko bylo.
    entry<BotShareRoute> { route ->
        val users = route.usersRequest()
        val chat = route.chatRequest()
        BotShareScreen(
            viewModel = koinViewModel(
                key = "bot-share-${route.chatId}-${route.messageId}-${route.buttonId}",
                parameters = { parametersOf(BotShareSource.Message(route.chatId, route.messageId), users, chat) },
            ),
            users = users,
            chat = chat,
            onBack = { chatsStack.pop(route) },
            onShared = { chatsStack.pop(route) },
        )
    }

    entry<AddMembersRoute> { route ->
        AddMembersScreen(
            viewModel = koinViewModel(key = "add-members-${route.chatId}", parameters = { parametersOf(route.chatId) }),
            onBack = { chatsStack.pop(route) },
            onDone = { chatsStack.pop(route) },
        )
    }

    entry<ShareContactRoute> { route ->
        val slot = LocalAccount.current.slot
        ShareContactScreen(
            viewModel = koinViewModel(key = "share-contact-${route.userId}", parameters = { parametersOf(route.userId) }),
            onBack = { chatsStack.pop(route) },
            // Jako po přeposlání: otevře se první vybraný chat, je vidět, že kontakt dorazil.
            onSent = { chatId ->
                if (chatsStack.pop(route) && !chatsStack.isOpenConversation(slot, chatId)) {
                    chatsStack.add(ConversationRoute(slot, chatId))
                }
            },
        )
    }

    // Odpovědět v jiném chatu: výběr cíle zmizí a otevře se vybraný chat s rozepsanou odpovědí
    // (`ConversationReplyRequests`); zpět vede do původní konverzace, jako po přeposlání.
    entry<ReplyElsewhereRoute> { route ->
        val slot = LocalAccount.current.slot
        val replyRequests = koinInject<ConversationReplyRequests>()
        ReplyElsewhereScreen(
            viewModel = koinViewModel(key = "reply-elsewhere-${route.fromChatId}-${route.messageId}", parameters = { parametersOf(route.fromChatId) }),
            onBack = { chatsStack.pop(route) },
            onPicked = { chatId ->
                replyRequests.request(chatId, route.messageId, fromChatId = route.fromChatId)
                if (chatsStack.pop(route) && !chatsStack.isOpenConversation(slot, chatId)) {
                    chatsStack.add(ConversationRoute(slot, chatId))
                }
            },
        )
    }

    entry<ForwardRoute> { route ->
        val slot = LocalAccount.current.slot
        ForwardScreen(
            viewModel = koinViewModel(
                key = "forward-${route.fromChatId}-${route.messageIds.joinToString("-")}-${route.storyId}",
                parameters = { parametersOf(route.fromChatId, route.messageIds, route.storyId) },
            ),
            onBack = { chatsStack.pop(route) },
            onForwarded = { chatId ->
                // Výběr už v zásobníku není (odchází) — cíl otevřel předchozí volání.
                if (chatsStack.pop(route) && !chatsStack.isOpenConversation(slot, chatId)) {
                    chatsStack.add(ConversationRoute(slot, chatId))
                }
            },
        )
    }

    /*
     * Potvrzení sdílení do chatu vybraného v systémové nabídce (Direct Share). Po odeslání ho
     * nahradí ta konverzace — je tím vidět, že zpráva dorazila, jako u přeposílání.
     */
    entry<ShareToChatRoute> { route ->
        val slot = LocalAccount.current.slot
        ShareToChatScreen(
            viewModel = koinViewModel(
                key = "share-to-chat-${'$'}{route.chatId}",
                parameters = { parametersOf(route.chatId) },
            ),
            onClose = closeShare,
            onSent = {
                chatsStack.removeAll { it is ShareToChatRoute }
                if (!chatsStack.isOpenConversation(slot, route.chatId)) {
                    chatsStack.add(ConversationRoute(slot, route.chatId))
                }
            },
        )
    }

    /*
     * Sheety jako navigační destinace ([SheetRoute], `SheetSceneStrategy`): kreslí se nad scénou
     * z položek pod nimi, v zásobníku sekce, ze které se otevřely. Zavřený sheet (tah, zpět)
     * routu odebere sám; vede-li jinam, odebere se NAPŘED, ať nová obrazovka nestojí nad ním.
     * `ownSurface`: sheet si barvy bere z tématu, ne ze stránky s bloky řádků.
     */
    entry<MyQrCodeRoute>(metadata = SheetSceneStrategy.sheet() + Panes.ownSurface()) {
        MyQrCodeSheet(onDismiss = { navigation.closeSheet(MyQrCodeRoute) })
    }

    entry<DraftsRoute>(metadata = SheetSceneStrategy.sheet() + Panes.ownSurface()) { route ->
        val slot = LocalAccount.current.slot
        val settings = koinInject<AppSettings>()
        val savedMessagesChatId = LocalSavedMessagesChatId.current
        DraftsDestination(
            viewModel = koinViewModel(),
            onOpen = { item ->
                navigation.closeSheet(route)
                val chatId = item.chat.id
                val topic = item.topic
                val thread = item.thread
                // Kam vede koncept, stejně jako ťuknutí v seznamu chatů (i Uložené zprávy po chatech).
                when {
                    thread != null -> chatsStack.openCommentThread(slot, chatId, thread.id)
                    topic != null -> chatsStack.openFoundMessage(slot, chatId, messageId = null, topicId = topic.id)
                    item.chat.opensAsTopics -> chatsStack.openForum(slot, chatId)
                    chatId == savedMessagesChatId && settings.savedMessagesAsChats.value -> chatsStack.openSavedTopics(slot, chatId)
                    else -> chatsStack.openConversation(slot, chatId)
                }
            },
            onDismiss = { navigation.closeSheet(route) },
        )
    }

    entry<BookmarksRoute>(metadata = SheetSceneStrategy.sheet() + Panes.ownSurface()) { route ->
        val slot = LocalAccount.current.slot
        val jumpRequests = koinInject<ConversationJumpRequests>()
        val chatId = route.chatId
        BookmarksDestination(
            viewModel = koinViewModel(parameters = { if (chatId == null) parametersOf() else parametersOf(chatId) }),
            onOpen = { bookmark ->
                navigation.closeSheet(route)
                if (chatId == null) {
                    // Ze všech chatů: jako nalezená zpráva v hledání — chat se otevře a skočí na ni.
                    chatsStack.openFoundMessage(slot, bookmark.chatId, bookmark.messageId, topicId = null)
                } else {
                    chatsStack.openMessageFromProfile(jumpRequests, slot, chatId, bookmark.messageId, route.archive)
                }
            },
            onDismiss = { navigation.closeSheet(route) },
        )
    }

    entry<SettingsRoute>(metadata = Panes.list(SETTINGS_SCENE)) {
        val slot = LocalAccount.current.slot
        SettingsScreen(
            selectedSection = selectedSettingsSection,
            accountSelected = settingsStack.getOrNull(1) == EditProfileRoute,
            // Otevření jiné sekce ji NAHRADÍ, nevrší se zásobník — stejně jako
            // u konverzací. Úprava profilu z karty účtu stojí na místě sekce, stejně tak.
            onSectionClick = { section -> settingsStack.replaceSettingsDetail(SettingsSectionRoute(section)) },
            onEditProfile = { settingsStack.replaceSettingsDetail(EditProfileRoute) },
            // Sem se chodí z menu účtu na každé šířce, takže šipka zpět do Chatů je vždycky.
            // Zavírá Nastavení celá — v dvoupanelu je sekce otevřená pořád, viz `closeSettings`.
            onBack = navigation::closeSettings,
            onOpenChat = { chatId -> navigation.openChat(slot, chatId) },
            onOpenSearchResult = navigation::openSettingsSearchResult,
            searchResetKey = navigation.settingsOpenings,
            onMyQrCode = { navigation.openSheet(SettingsRoute, MyQrCodeRoute) },
            onSelectAccount = navigation::selectSettingsAccount,
            modifier = Modifier.listPaneInsets(twoPane),
        )
    }

    /*
     * Nastavení jako chaty s profilem: seznam | sekce (detail) | podstránka sekce (extra). Od
     * Large tři panely, pod ním sekce s podstránkou a seznam se schová, na telefonu po jedné
     * obrazovce. Podstránky se vrší nad sebou ve třetím panelu (Soukromí účtu → jedno
     * nastavení) a zpět vede po nich. Úprava profilu je detail — stojí na místě sekce, protože
     * se otevírá z karty v seznamu; změna čísla nad ní je zase podstránka.
     */
    entry<SettingsSectionRoute>(
        // Zpět ze sekce vede z Nastavení pryč: v širokém okně se sekce otevírá sama a obyčejné
        // zpět by ji efekt hned vrátil (`PaneSceneStrategy`). Na telefonu nemá metadata vliv.
        metadata = Panes.detail(SETTINGS_SCENE) + Panes.contentWidth(ContentWidth.Reading) + Panes.backLeavesScene(),
    ) { route ->
        // V dvoupanelu je seznam sekcí pořád vidět vedle, takže šipka zpět nemá
        // kam vést; na telefonu je sekce samostatná obrazovka a zpět se z ní chodí.
        // Podstránky ze sekce se navzájem NAHRAZUJÍ (`openSubpage`): od Medium je sekce vidět
        // vedle podstránky a ťuknutí na další řádek je dřív vršilo přes sebe.
        val popSection: () -> Unit = { settingsStack.pop(route) }

        SettingsSectionScreen(
            section = route.section,
            onBack = popSection.takeUnless { LocalPanesSideBySide.current },
            onOpenBlocked = { settingsStack.openSubpage(route, BlockedSendersRoute) },
            onOpenFolders = { settingsStack.openSubpage(route, ChatFoldersRoute) },
            onOpenStickerSets = { settingsStack.openSubpage(route, StickerSetsRoute) },
            onOpenEmojiSets = { settingsStack.openSubpage(route, EmojiSetsRoute) },
            onOpenProxy = { settingsStack.openSubpage(route, ProxySettingsRoute) },
            onOpenAccountPrivacy = { settingsStack.openSubpage(route, AccountPrivacyRoute) },
            onOpenTwoStep = { settingsStack.openSubpage(route, TwoStepRoute) },
            onOpenLoginEmail = { settingsStack.openSubpage(route, LoginEmailRoute) },
            onOpenPasskeys = { settingsStack.openSubpage(route, PasskeysRoute) },
            onOpenNetworkUsage = { settingsStack.openSubpage(route, NetworkUsageRoute) },
            onOpenAppLock = { settingsStack.openSubpage(route, AppLockRoute) },
            onOpenAiPlayground = { settingsStack.openSubpage(route, AiPlaygroundRoute) },
            onOpenLicenses = { settingsStack.openSubpage(route, LicensesRoute) },
        )
    }

    // Vyzkoušet model nad sekcí AI v zařízení.
    entry<AiPlaygroundRoute>(
        metadata = Panes.extra(SETTINGS_SCENE) + Panes.contentWidth(ContentWidth.Reading),
    ) { route ->
        AiPlaygroundScreen(viewModel = koinViewModel(), onBack = { settingsStack.pop(route) })
    }

    // Soukromí účtu nad sekcí Soukromí, jedno nastavení s výjimkami nad ním.
    entry<AccountPrivacyRoute>(
        metadata = Panes.extra(SETTINGS_SCENE) + Panes.contentWidth(ContentWidth.Reading),
    ) { route ->
        AccountPrivacyScreen(
            viewModel = koinViewModel(),
            onBack = { settingsStack.pop(route) },
            onOpen = { setting -> if (settingsStack.lastOrNull() !is PrivacySettingRoute) settingsStack.add(PrivacySettingRoute(setting)) },
            onDeleteAccount = { if (settingsStack.lastOrNull() != DeleteAccountRoute) settingsStack.add(DeleteAccountRoute) },
        )
    }

    entry<DeleteAccountRoute>(
        metadata = Panes.extra(SETTINGS_SCENE) + Panes.contentWidth(ContentWidth.Reading),
    ) { route ->
        DeleteAccountScreen(viewModel = koinViewModel(), onBack = { settingsStack.pop(route) })
    }

    entry<PrivacySettingRoute>(
        metadata = Panes.extra(SETTINGS_SCENE) + Panes.contentWidth(ContentWidth.Reading),
    ) { route ->
        PrivacySettingScreen(
            viewModel = koinViewModel(key = "privacy-${route.setting}", parameters = { parametersOf(route.setting) }),
            onBack = { settingsStack.pop(route) },
        )
    }

    entry<ProxySettingsRoute>(
        metadata = Panes.extra(SETTINGS_SCENE) + Panes.contentWidth(ContentWidth.Reading),
    ) { route ->
        ProxySettingsScreen(viewModel = koinViewModel(), onBack = { settingsStack.pop(route) })
    }

    // Složky chatů nad sekcí Chaty; editor složky nad nimi.
    entry<ChatFoldersRoute>(
        metadata = Panes.extra(SETTINGS_SCENE) + Panes.contentWidth(ContentWidth.Reading),
    ) { route ->
        val slot = LocalAccount.current.slot
        ChatFoldersScreen(
            viewModel = koinViewModel(),
            onBack = { settingsStack.pop(route) },
            onEdit = { id -> if (settingsStack.lastOrNull() !is ChatFolderEditRoute) settingsStack.add(ChatFolderEditRoute(slot, id)) },
        )
    }

    // Sady samolepek nad sekcí Chaty, jako složky chatů.
    entry<StickerSetsRoute>(
        metadata = Panes.extra(SETTINGS_SCENE) + Panes.contentWidth(ContentWidth.Reading),
    ) { route ->
        val slot = LocalAccount.current.slot
        val stickersCanBeMade = getKoin().getOrNull<StickerMaker>() != null
        StickerSetsScreen(
            kind = StickerSetKind.Stickers,
            viewModel = koinViewModel(key = "sticker-sets") { parametersOf(StickerSetKind.Stickers) },
            onBack = { settingsStack.pop(route) },
            // Jen kde samolepku z fotky jde vyrobit (Android).
            onCreateSticker = if (stickersCanBeMade) {
                { path, width, height -> settingsStack.add(StickerComposerRoute(slot, path, width, height)) }
            } else {
                null
            },
            onEditSet = { setId -> settingsStack.add(OwnedStickerSetRoute(slot, setId)) },
        )
    }

    // Úprava vlastní sady samolepek, nad seznamem sad.
    entry<OwnedStickerSetRoute>(
        metadata = Panes.extra(SETTINGS_SCENE) + Panes.contentWidth(ContentWidth.Reading),
    ) { route ->
        val slot = LocalAccount.current.slot
        val stickersCanBeMade = getKoin().getOrNull<StickerMaker>() != null
        OwnedStickerSetScreen(
            viewModel = koinViewModel(key = "owned-sticker-set-${route.setId}") { parametersOf(route.setId) },
            onBack = { settingsStack.pop(route) },
            onAddSticker = if (stickersCanBeMade) {
                { path, width, height, setId -> settingsStack.add(StickerComposerRoute(slot, path, width, height, targetSetId = setId)) }
            } else {
                null
            },
        )
    }

    // Sady vlastních emoji: táž obrazovka, jiný seznam.
    entry<EmojiSetsRoute>(
        metadata = Panes.extra(SETTINGS_SCENE) + Panes.contentWidth(ContentWidth.Reading),
    ) { route ->
        StickerSetsScreen(
            kind = StickerSetKind.Emoji,
            viewModel = koinViewModel(key = "emoji-sets") { parametersOf(StickerSetKind.Emoji) },
            onBack = { settingsStack.pop(route) },
        )
    }

    entry<ChatFolderEditRoute>(
        metadata = Panes.extra(SETTINGS_SCENE) + Panes.contentWidth(ContentWidth.Reading),
    ) { route ->
        ChatFolderEditScreen(
            viewModel = koinViewModel(key = "chat-folder-${route.folderId}", parameters = { parametersOf(route.folderId) }),
            onBack = { settingsStack.pop(route) },
        )
    }

    // Seznam blokovaných nad sekcí Soukromí.
    entry<BlockedSendersRoute>(
        metadata = Panes.extra(SETTINGS_SCENE) + Panes.contentWidth(ContentWidth.Reading),
    ) { route ->
        BlockedSendersScreen(
            viewModel = koinViewModel(),
            onBack = { settingsStack.pop(route) },
            onBlockUser = { if (settingsStack.lastOrNull() != BlockSenderRoute) settingsStack.add(BlockSenderRoute) },
        )
    }

    // Dvoufázové ověření nad sekcí Soukromí, jako seznam blokovaných.
    entry<TwoStepRoute>(
        metadata = Panes.extra(SETTINGS_SCENE) + Panes.contentWidth(ContentWidth.Reading),
    ) { route ->
        TwoStepScreen(viewModel = koinViewModel(), onBack = { settingsStack.pop(route) })
    }

    // Přihlašovací e-mail nad sekcí Soukromí, jako dvoufázové ověření.
    entry<LoginEmailRoute>(
        metadata = Panes.extra(SETTINGS_SCENE) + Panes.contentWidth(ContentWidth.Reading),
    ) { route ->
        LoginEmailScreen(viewModel = koinViewModel(), onBack = { settingsStack.pop(route) })
    }

    // Přenesená data nad sekcí Úložiště.
    entry<NetworkUsageRoute>(
        metadata = Panes.extra(SETTINGS_SCENE) + Panes.contentWidth(ContentWidth.Reading),
    ) { route ->
        NetworkUsageScreen(viewModel = koinViewModel(), onBack = { settingsStack.pop(route) })
    }

    // Licence knihoven nad sekcí O aplikaci.
    entry<LicensesRoute>(
        metadata = Panes.extra(SETTINGS_SCENE) + Panes.contentWidth(ContentWidth.Reading),
    ) { route ->
        LicensesScreen(onBack = { settingsStack.pop(route) })
    }

    // Přístupové klíče nad sekcí Soukromí, jako dvoufázové ověření.
    entry<PasskeysRoute>(
        metadata = Panes.extra(SETTINGS_SCENE) + Panes.contentWidth(ContentWidth.Reading),
    ) { route ->
        PasskeysScreen(viewModel = koinViewModel(), onBack = { settingsStack.pop(route) })
    }

    // Zámek aplikace nad sekcí Soukromí, jako dvoufázové ověření.
    entry<AppLockRoute>(
        metadata = Panes.extra(SETTINGS_SCENE) + Panes.contentWidth(ContentWidth.Reading),
    ) { route ->
        AppLockScreen(viewModel = koinViewModel(), onBack = { settingsStack.pop(route) })
    }

    // Výběr k zablokování nad seznamem blokovaných; po zablokování se vrací do něj.
    entry<BlockSenderRoute>(
        metadata = Panes.extra(SETTINGS_SCENE) + Panes.contentWidth(ContentWidth.Reading),
    ) { route ->
        BlockSenderScreen(
            viewModel = koinViewModel(),
            onBack = { settingsStack.pop(route) },
        )
    }

    // Úprava profilu z karty účtu, místo sekce. V dvoupanelu bez šipky zpět (seznam je vedle), na telefonu se z ní chodí zpět.
    entry<EditProfileRoute>(
        metadata = Panes.detail(SETTINGS_SCENE) + Panes.contentWidth(ContentWidth.Reading),
    ) { route ->
        val slot = LocalAccount.current.slot
        val close: () -> Unit = { settingsStack.pop(route) }
        EditProfileScreen(
            viewModel = koinViewModel(),
            onBack = close.takeUnless { LocalPanesSideBySide.current },
            // Prohlížeč se vrší nad Nastavení; zavírá se z toho zásobníku, ve kterém leží.
            onOpenPhotos = { userId -> settingsStack.add(MediaRoute(slot, userId, messageId = 0L, profilePhotos = true)) },
            onChangePhone = { settingsStack.openSubpage(route, ChangePhoneRoute) },
        )
    }

    // Změna čísla nad úpravou profilu.
    entry<ChangePhoneRoute>(
        metadata = Panes.extra(SETTINGS_SCENE) + Panes.contentWidth(ContentWidth.Reading),
    ) { route ->
        ChangePhoneScreen(viewModel = koinViewModel(), onBack = { settingsStack.pop(route) })
    }
}

/**
 * Je na vrcholu zásobníku konverzace [chatId]? Porovnává se jen chat — routa může nést i zprávu
 * ze hledání, a rovnost celé routy by pak přidala tutéž konverzaci podruhé.
 */
private fun List<NavKey>.isOpenConversation(slot: AccountSlot, chatId: Long): Boolean =
    (lastOrNull() as? ConversationRoute)?.let { it.slot == slot && it.chatId == chatId && !it.beside } == true

/**
 * Rychlý přepínač v konverzaci: chat jako po ťuknutí v seznamu — i s Uloženými zprávami po
 * chatech, když si je tak uživatel zvolil.
 */
@Composable
private fun rememberSwitchChat(navigation: AppNavigation): (ChatSummary) -> Unit {
    val slot = LocalAccount.current.slot
    val settings = koinInject<AppSettings>()
    val savedMessagesChatId = LocalSavedMessagesChatId.current
    return remember(navigation, slot, settings, savedMessagesChatId) {
        { chat ->
            navigation.switchChat(slot, 
                chatId = chat.id,
                forum = chat.opensAsTopics,
                savedTopics = chat.id == savedMessagesChatId && settings.savedMessagesAsChats.value,
            )
        }
    }
}

/**
 * Otevření chatu z hlavního seznamu NAHRADÍ otevřenou konverzaci, nevrší se zásobník — stejně
 * jako sekce v Nastavení.
 */
private fun MutableList<NavKey>.openConversation(slot: AccountSlot, chatId: Long, messageId: Long? = null) {
    removeAll { it is ConversationRoute || it is ForumTopicsRoute || it is SavedTopicsRoute || it is DirectTopicsRoute }
    add(ConversationRoute(slot, chatId, messageId))
}

/** Skupina s tématy: místo konverzace seznam témat, jinak totéž co [openConversation]. */
private fun MutableList<NavKey>.openForum(slot: AccountSlot, chatId: Long) {
    removeAll { it is ConversationRoute || it is ForumTopicsRoute || it is SavedTopicsRoute || it is DirectTopicsRoute }
    add(ForumTopicsRoute(slot, chatId))
}

/** Uložené zprávy po chatech: místo konverzace seznam témat, jinak totéž co [openConversation]. */
private fun MutableList<NavKey>.openSavedTopics(slot: AccountSlot, chatId: Long) {
    removeAll { it is ConversationRoute || it is ForumTopicsRoute || it is SavedTopicsRoute || it is DirectTopicsRoute }
    add(SavedTopicsRoute(slot, chatId))
}

/** Videochat nad konverzací; druhé ťuknutí na pruh ho nezdvojí. */
private fun MutableList<NavKey>.openVideoChat(slot: AccountSlot, chatId: Long, groupCallId: Int) {
    if (lastOrNull() !is VideoChatRoute) add(VideoChatRoute(slot, chatId, groupCallId))
}

/** Prohlížeč příběhů nad čímkoli; druhé ťuknutí (dvojité) ho nezdvojí. */
private fun MutableList<NavKey>.openStories(slot: AccountSlot, chatId: Long, storyIds: List<Int> = emptyList(), startStoryId: Int = 0) {
    if (lastOrNull() !is StoriesRoute) add(StoriesRoute(slot, chatId, storyIds, startStoryId))
}

/** Totéž co [isOpenConversation], ale pro větev archivu. */
private fun List<NavKey>.isOpenArchiveConversation(slot: AccountSlot, chatId: Long): Boolean =
    (lastOrNull() as? ArchiveConversationRoute)?.let { it.slot == slot && it.chatId == chatId } == true

/**
 * Zpráva z profilu chatu [chatId] (záložka, sdílený odkaz): profil se zavře a konverzace pod ním
 * skočí na zprávu (`ConversationJumpRequests`); bez konverzace pod profilem se otevře rovnou na
 * ní. [archive] = profil a konverzace z větve archivu. Volá ho profil i sheet jeho záložek.
 */
private fun MutableList<NavKey>.openMessageFromProfile(
    jumpRequests: ConversationJumpRequests,
    slot: AccountSlot,
    chatId: Long,
    messageId: Long,
    archive: Boolean,
) {
    jumpRequests.request(chatId, messageId)
    if (archive) {
        pop(ArchiveChatProfileRoute(slot, chatId))
        if (!isOpenArchiveConversation(slot, chatId)) add(ArchiveConversationRoute(slot, chatId, messageId))
    } else {
        pop(ChatProfileRoute(slot, chatId))
        if (!isOpenConversation(slot, chatId)) add(ConversationRoute(slot, chatId, messageId))
    }
}

/**
 * Totéž co [openConversation], ale pro větev archivu: nahrazuje se jen archivní konverzace,
 * takže chat otevřený z hlavního seznamu zůstane, kde byl.
 */
private fun MutableList<NavKey>.openArchiveConversation(slot: AccountSlot, chatId: Long, messageId: Long? = null) {
    removeAll { it is ArchiveConversationRoute || it is ArchiveForumTopicsRoute || it is ArchiveDirectTopicsRoute }
    add(ArchiveConversationRoute(slot, chatId, messageId))
}

/** Totéž co [openForum], ale pro větev archivu. */
private fun MutableList<NavKey>.openArchiveForum(slot: AccountSlot, chatId: Long) {
    removeAll { it is ArchiveConversationRoute || it is ArchiveForumTopicsRoute || it is ArchiveDirectTopicsRoute }
    add(ArchiveForumTopicsRoute(slot, chatId))
}

/**
 * Seznam vlevo v dvoupanelu nestojí u pravého okraje, takže tamní inset (výřez kamery,
 * navigační lišta naležato) mu nepatří — patří panelu vpravo. Bez konzumace se o něj zúžila
 * vyhledávací pilulka nad seznamem a přestala lícovat s chaty pod ní.
 */
@Composable
private fun Modifier.listPaneInsets(twoPane: Boolean): Modifier =
    if (twoPane) consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.End)) else this

/**
 * Klíč scény list-detail pro archiv.
 *
 * Archiv a jeho otevřená konverzace tvoří samostatnou list-detail scénu. Bez vlastního klíče
 * by v dvoupanelovém rozvržení archiv sdílel scénu s hlavním seznamem a pravý panel by ukazoval
 * cizí otevřenou konverzaci z hlavního seznamu.
 */
private const val ARCHIVE_SCENE = "archive"

/**
 * Klíč scény list-detail pro nastavení.
 *
 * Chaty i nastavení jsou dvě různé list-detail dvojice v jednom `NavDisplay`. Bez vlastního klíče
 * by obě skládaly scénu se stejnou identitou (výchozí `Unit`) a přepnutí sekce by pro NavDisplay
 * nebyla výměna scény, ale změna obsahu té stávající.
 */
private const val SETTINGS_SCENE = "settings"

/** Pravý panel Chatů bez otevřeného chatu („vyber chat vlevo") pro scénu Chatů. */
private val ChatDetailPlaceholder: @Composable () -> Unit = {
    PaneCard(PaneRole.Detail, ownSurface = true, headerOutside = true) { ConversationPlaceholder() }
}

private fun botShareRoute(slot: AccountSlot, chatId: Long, request: BotShareRequest) = BotShareRoute(slot, 
    chatId = chatId,
    messageId = request.messageId,
    buttonId = request.users?.buttonId ?: request.chat?.buttonId ?: 0,
    users = request.users != null,
    bots = request.users?.bots,
    maxQuantity = request.users?.maxQuantity ?: 1,
    channel = request.chat?.channel ?: false,
    forum = request.chat?.forum,
    botIsMember = request.chat?.botIsMember ?: false,
    botAdministrator = request.chat?.botAdministrator ?: false,
)

private fun BotShareRoute.usersRequest() = BotUsersRequest(buttonId, bots, maxQuantity).takeIf { users }

private fun BotShareRoute.chatRequest() =
    BotChatRequest(buttonId, channel, forum, botIsMember, botAdministrator).takeUnless { users }

internal fun webAppRoute(slot: AccountSlot, chatId: Long, request: WebAppRequest, archive: Boolean) = WebAppRoute(slot, 
    chatId = chatId,
    botUserId = request.botUserId,
    url = request.url,
    launch = request.launch,
    keyboardButtonText = request.keyboardButtonText,
    archive = archive,
    main = request.main,
    startParameter = request.startParameter,
    shortName = request.shortName,
    guardQueryId = request.guardQueryId,
)

private fun SwitchInlinePick.toRoute(slot: AccountSlot) = SwitchInlineRoute(slot, 
    bot = bot,
    query = query,
    users = target.users,
    bots = target.bots,
    groups = target.groups,
    channels = target.channels,
)

private fun checklistComposerRoute(slot: AccountSlot, chatId: Long, request: ChecklistComposerRequest) = ChecklistComposerRoute(slot, 
    chatId = chatId,
    replyToMessageId = request.replyToMessageId,
    quoteText = request.quote?.text,
    quotePosition = request.quote?.position ?: 0,
    editMessageId = request.editMessageId,
)

private fun pollComposerRoute(slot: AccountSlot, chatId: Long, request: PollComposerRequest) = PollComposerRoute(slot, 
    chatId = chatId,
    quizOnly = request.mode == PollComposerMode.QuizOnly,
    regularOnly = request.mode == PollComposerMode.RegularOnly,
    replyToMessageId = request.replyToMessageId,
    quoteText = request.quote?.text,
    quotePosition = request.quote?.position ?: 0,
)


/**
 * Zobrazit zprávu [messageId] z prohlížeče [route] v konverzaci: prohlížeč se zavře a konverzace
 * skočí na zprávu (`ConversationJumpRequests`, stejně jako záložka v profilu). Prohlížeč otevřený
 * z mřížky médií v profilu zavře i profil — konverzace leží pod ním. Bez konverzace toho chatu
 * nahoře se otevře rovnou na zprávě, v archivní větvi archivní.
 */
internal fun MutableList<NavKey>.showInChat(route: MediaRoute, messageId: Long, jumps: ConversationJumpRequests) {
    if (!pop(route)) return
    val below = lastOrNull()
    val archive = below is ArchiveChatProfileRoute
    val profileBelow = (below as? ChatProfileRoute)?.chatId == route.chatId || (below as? ArchiveChatProfileRoute)?.chatId == route.chatId
    if (profileBelow && below?.belongsTo(route.slot) == true) {
        pop(below)
    }
    jumps.request(route.chatId, messageId)
    when {
        isOpenConversation(route.slot, route.chatId) || isOpenArchiveConversation(route.slot, route.chatId) -> Unit
        archive -> add(ArchiveConversationRoute(route.slot, route.chatId, messageId))
        else -> add(ConversationRoute(route.slot, route.chatId, messageId))
    }
}

/**
 * Všechna média chatu z prohlížeče [route]: prohlížeč se zavře a profil chatu se ukáže na
 * záložce Média (`ProfileTabRequests`). Otevřel-li se prohlížeč z mřížky v profilu, profil pod
 * ním už je; jinak se nad konverzaci přidá (na široké ploše jako panel vedle ní), nad archivní
 * konverzací archivní.
 */
internal fun MutableList<NavKey>.showAllMedia(route: MediaRoute, tabs: ProfileTabRequests) {
    if (!pop(route)) return
    tabs.request(route.chatId, SharedMediaKind.Media)
    when (val below = lastOrNull()?.takeIf { it.belongsTo(route.slot) }) {
        is ChatProfileRoute -> if (below.chatId == route.chatId) return
        is ArchiveChatProfileRoute -> if (below.chatId == route.chatId) return
        is ArchiveConversationRoute -> if (below.chatId == route.chatId) {
            add(ArchiveChatProfileRoute(route.slot, route.chatId))
            return
        }
        else -> Unit
    }
    add(ChatProfileRoute(route.slot, route.chatId))
}

/** Stránky prohlížeče jsou zprávy chatu [MediaRoute.chatId] — jde na ně skočit v konverzaci. */
internal val MediaRoute.showsChatMessages: Boolean
    get() = !profilePhotos && storyId == NO_STORY && !sponsored &&
        pollMedia == NO_POLL_MEDIA && articleMedia == NO_ARTICLE_MEDIA

/**
 * Chat konverzace, která leží v zásobníku přímo pod prohlížečem [route], je-li to konverzace
 * téhož chatu (hlavní seznam i archiv); jinak `null`. Pod prohlížečem otevřeným z profilu leží
 * profil a odpověď by neměla kam dopadnout.
 */
private fun conversationBelow(stack: List<NavKey>, route: MediaRoute): Long? {
    val index = stack.lastIndexOf(route)
    val chatId = when (val below = stack.getOrNull(index - 1)) {
        is ConversationRoute -> below.chatId
        is ArchiveConversationRoute -> below.chatId
        else -> null
    }
    return chatId?.takeIf { it == route.chatId && !route.fromSharedMedia && route.pollMedia == NO_POLL_MEDIA }
}

/** Obrazovky, které se vrší nad sekci Nastavení: úprava profilu, blokovaní, dvoufázové ověření, složky chatů. */
private fun NavKey?.isOverSettingsSection(): Boolean =
    this == EditProfileRoute || this == BlockedSendersRoute || this == BlockSenderRoute || this == TwoStepRoute ||
        this == LoginEmailRoute || this == ChangePhoneRoute || this == PasskeysRoute || this == NetworkUsageRoute || this == LicensesRoute ||
        this == AppLockRoute || this == ChatFoldersRoute || this == AiPlaygroundRoute || this is ChatFolderEditRoute || this == ProxySettingsRoute ||
        this == StickerSetsRoute || this == EmojiSetsRoute ||
        this == AccountPrivacyRoute || this is PrivacySettingRoute || this == DeleteAccountRoute

/**
 * Kontejner obrazovky chatu (konverzace, seznam témat) v barvě chatu (`ChatColorTheme`).
 * [onBack] zavře obrazovku z brány zamčeného chatu (šipka, a když se chat právě zamkl).
 */
@Composable
private fun ColoredChatContainer(chatId: Long, fromList: Boolean, onBack: () -> Unit, content: @Composable () -> Unit) {
    ChatContainer(chatId, fromList) { ChatColorTheme(chatId, onBack, content) }
}

/**
 * Výběr v seznamu Nastavení: sekce, nebo úprava profilu z karty účtu. NAHRADÍ, co je otevřené
 * nad seznamem, i s obrazovkami, které se vrší nad sekci — jinak by zůstaly pod novou sekcí.
 * Opětovné ťuknutí na otevřené nic nemění.
 */
private fun MutableList<NavKey>.replaceSettingsDetail(detail: NavKey) {
    if (getOrNull(1) == detail && size == 2) return
    removeAll { it is SettingsSectionRoute || it.isOverSettingsSection() }
    add(detail)
}

/**
 * Konverzace [this] otevřená účtem [slot], ve kterém má chat id [chatId] (`onSwitchAccount`).
 * Bez skoku na zprávu; u jiného id (Uložené zprávy) i bez tématu Uložených zpráv — témata patří
 * účtu a v druhém by to id znamenalo jiné téma nebo žádné.
 */
private fun ConversationRoute.switchedTo(slot: AccountSlot, chatId: Long): ConversationRoute =
    copy(slot = slot, chatId = chatId, messageId = null, savedTopicId = savedTopicId.takeIf { chatId == this.chatId })
