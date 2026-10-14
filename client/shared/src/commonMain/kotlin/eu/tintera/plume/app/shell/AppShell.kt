package eu.tintera.plume.app.shell

import eu.tintera.plume.features.forward.ShareContactScreen
import eu.tintera.plume.core.navigation.ShareContactRoute
import eu.tintera.plume.core.navigation.AddMembersRoute
import eu.tintera.plume.features.profile.AddMembersScreen
import eu.tintera.plume.features.profile.JoinRequestsScreen
import eu.tintera.plume.features.profile.InviteLinksScreen
import eu.tintera.plume.core.navigation.JoinRequestsRoute
import eu.tintera.plume.core.navigation.InviteLinksRoute
import eu.tintera.plume.core.navigation.ArchiveJoinRequestsRoute
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
import eu.tintera.plume.core.navigation.DeleteAccountRoute
import eu.tintera.plume.core.navigation.BlockSenderRoute
import eu.tintera.plume.core.navigation.BlockedSendersRoute
import eu.tintera.plume.core.navigation.PrivacySettingRoute
import eu.tintera.plume.features.settings.AccountPrivacyScreen
import eu.tintera.plume.features.settings.PrivacySettingScreen
import eu.tintera.plume.core.navigation.TwoStepRoute
import eu.tintera.plume.core.navigation.LoginEmailRoute
import eu.tintera.plume.core.navigation.NetworkUsageRoute
import eu.tintera.plume.core.navigation.PasskeysRoute
import eu.tintera.plume.core.navigation.ChangePhoneRoute
import eu.tintera.plume.features.instantview.InstantViewScreen
import eu.tintera.plume.features.settings.ChangePhoneScreen
import eu.tintera.plume.features.settings.NetworkUsageScreen
import eu.tintera.plume.features.settings.PasskeysScreen
import eu.tintera.plume.features.settings.DeleteAccountScreen
import eu.tintera.plume.features.settings.LoginEmailScreen
import eu.tintera.plume.core.navigation.AppLockRoute
import eu.tintera.plume.core.navigation.ChatFolderEditRoute
import eu.tintera.plume.core.navigation.ChatFoldersRoute
import eu.tintera.plume.core.navigation.StickerSetsRoute
import eu.tintera.plume.core.navigation.EmojiSetsRoute
import eu.tintera.plume.core.telegram.StickerSetKind
import eu.tintera.plume.core.navigation.ProxySettingsRoute
import eu.tintera.plume.features.proxy.ProxySettingsScreen
import eu.tintera.plume.features.settings.ChatFolderEditScreen
import eu.tintera.plume.features.settings.ChatFoldersScreen
import eu.tintera.plume.features.settings.StickerSetsScreen
import eu.tintera.plume.features.settings.BlockSenderScreen
import eu.tintera.plume.features.settings.BlockedSendersScreen
import eu.tintera.plume.features.settings.TwoStepScreen
import eu.tintera.plume.features.settings.AppLockScreen
import eu.tintera.plume.features.conversation.ConversationJumpRequests
import eu.tintera.plume.features.conversation.ConversationReplyRequests
import eu.tintera.plume.features.conversation.InlineQueryRequests
import eu.tintera.plume.features.conversation.SwitchInlinePick
import eu.tintera.plume.features.conversation.SwitchInlinePickerScreen
import eu.tintera.plume.features.conversation.ConversationSearchRequests
import eu.tintera.plume.features.profile.ChatProfileScreen
import eu.tintera.plume.features.profile.EditChatScreen
import eu.tintera.plume.features.profile.MemberRightsScreen
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Modifier
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import eu.tintera.plume.core.adaptive.Panes
import eu.tintera.plume.core.adaptive.ContentWidth
import eu.tintera.plume.core.adaptive.PANE_SPACING
import eu.tintera.plume.core.adaptive.LocalPaneNeighbours
import eu.tintera.plume.core.adaptive.PaneCard
import eu.tintera.plume.core.adaptive.PaneNeighbours
import eu.tintera.plume.core.adaptive.listWidthBesideProfile
import eu.tintera.plume.core.adaptive.rememberPaneCardDecorator
import eu.tintera.plume.core.adaptive.rememberWithLeavingExtras
import eu.tintera.plume.core.adaptive.LocalProfilePaneResize
import eu.tintera.plume.core.adaptive.LocalExpandListPane
import eu.tintera.plume.core.adaptive.PaneInfo
import eu.tintera.plume.core.adaptive.PaneRole
import eu.tintera.plume.core.adaptive.THREE_PANE_MIN_WIDTH
import eu.tintera.plume.core.adaptive.paneInfo
import eu.tintera.plume.core.adaptive.threePane
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
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
import eu.tintera.plume.features.calls.CallOverlay
import eu.tintera.plume.core.adaptive.singlePane
import eu.tintera.plume.core.adaptive.twoPane
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import eu.tintera.plume.core.navigation.ChatOpenRequests
import eu.tintera.plume.core.navigation.ArchiveChatProfileRoute
import eu.tintera.plume.core.navigation.ArchiveConversationRoute
import eu.tintera.plume.core.navigation.ArchiveRoute
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.navigation.ForumTopicsRoute
import eu.tintera.plume.core.navigation.SavedTopicsRoute
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.features.saved.SavedTopicsScreen
import eu.tintera.plume.core.navigation.ArchiveForumTopicsRoute
import eu.tintera.plume.features.forum.ForumTopicsScreen
import eu.tintera.plume.core.telegram.NO_POLL_MEDIA
import eu.tintera.plume.core.navigation.ForwardRoute
import eu.tintera.plume.core.navigation.PollComposerRoute
import eu.tintera.plume.features.conversation.WebAppRequest
import eu.tintera.plume.features.webapp.WebAppScreen
import eu.tintera.plume.core.navigation.WebAppRoute
import eu.tintera.plume.core.telegram.BotChatRequest
import eu.tintera.plume.core.telegram.BotUsersRequest
import eu.tintera.plume.features.conversation.BotShareRequest
import eu.tintera.plume.features.forward.BotShareScreen
import eu.tintera.plume.core.navigation.BotShareRoute
import eu.tintera.plume.core.telegram.PollComposerMode
import eu.tintera.plume.features.conversation.PollComposerScreen
import eu.tintera.plume.features.conversation.PollComposerRequest
import eu.tintera.plume.core.telegram.ReplyQuote
import eu.tintera.plume.core.navigation.ShareRoute
import eu.tintera.plume.core.navigation.ShareToChatRoute
import eu.tintera.plume.core.share.ShareRequests
import eu.tintera.plume.core.share.rememberReturnToCaller
import eu.tintera.plume.core.navigation.MediaRoute
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
import eu.tintera.plume.features.chats.ChatListScreen
import eu.tintera.plume.features.chats.AddBotToGroupScreen
import eu.tintera.plume.features.chats.NewChatScreen
import eu.tintera.plume.features.chats.NewContactScreen
import eu.tintera.plume.features.chats.NewGroupMembersScreen
import eu.tintera.plume.features.chats.CreateChatScreen
import eu.tintera.plume.features.conversation.ConversationPlaceholder
import eu.tintera.plume.features.conversation.ConversationNavigation
import eu.tintera.plume.features.conversation.ConversationScreen
import eu.tintera.plume.features.forward.ForwardScreen
import eu.tintera.plume.features.share.ShareScreen
import eu.tintera.plume.features.share.ShareToChatScreen
import eu.tintera.plume.features.media.MediaViewerScreen
import org.koin.core.parameter.parametersOf
import eu.tintera.plume.features.settings.SettingsScreen
import eu.tintera.plume.features.settings.SettingsSectionScreen
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

    LaunchedEffect(openRequest) {
        val request = openRequest ?: return@LaunchedEffect
        navigation.openChat(request.chatId, request.topicId)
        chatOpenRequests.consume(request)
    }

    /*
     * Sdílení ze systému (nabídka „Sdílet" v jiné aplikaci). Stav, ne událost — viz
     * [ShareRequests]: sdílet jde i do aplikace, která teprve naběhla nebo visela na přihlášení.
     * Klíčem je id sdílení, ne celý obsah: průběh přípravy souborů výběr znovu otevírat nemá.
     * Výběr se otevírá jen pro sdílení bez vybraného chatu — to s vybraným už čeká v konverzaci.
     */
    val shareRequests = koinInject<ShareRequests>()
    val incomingShare by shareRequests.incoming.collectAsStateWithLifecycle()
    val pendingShareId = incomingShare?.takeIf { it.targetChatId == null }?.id

    LaunchedEffect(pendingShareId) {
        if (pendingShareId != null) navigation.openShare()
    }

    /*
     * Direct Share: chat je vybraný už v systémové nabídce, takže se místo výběru otevře
     * potvrzení („Komu: …" s náhledem a popiskem). Klíčem je id sdílení i chat — dvě sdílení
     * po sobě do téhož chatu jsou tatáž obrazovka, do jiného ne.
     */
    val confirmShare = incomingShare?.takeIf { it.awaitingConfirmation }
    val confirmShareKey = confirmShare?.let { it.id to it.targetChatId }

    LaunchedEffect(confirmShareKey) {
        val chatId = confirmShare?.targetChatId ?: return@LaunchedEffect
        navigation.openShareTo(chatId)
    }

    Box(modifier.fillMaxSize()) {
        ShellContent(
            navigation = navigation,
            entryDecorators = entryDecorators,
            modifier = Modifier.fillMaxSize(),
        )
        // Probíhající hovor nad vším — přes celou obrazovku, nebo zmenšený do pruhu nahoře.
        CallOverlay(Modifier.align(Alignment.TopCenter))
    }
}

/**
 * Vlastní navigační plocha.
 *
 * `directive` se počítá ze šířky **content-oblasti** (`BoxWithConstraints`), ne z okna — scaffold
 * má rozhodovat podle místa, které opravdu dostane.
 */
@Composable
private fun ShellContent(
    navigation: AppNavigation,
    entryDecorators: List<NavEntryDecorator<NavKey>>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
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
        val settingsDisplayed: List<NavKey> =
            if (wide && settingsStack.lastOrNull() == SettingsRoute) {
                settingsStack + SettingsSectionRoute(SettingsSection.entries.first())
            } else {
                settingsStack
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

        /*
         * Zavření výběru pro sdílení vrací do aplikace, ze které se sdílelo, ne do Plume.
         * Návrat do seznamu chatů by vypadal, že se sdílený obsah ztratil — a ztratil by se,
         * protože bez volby chatu nemá kam jít. Výběr se napřed odebere, aby příští
         * otevření Plume začalo tam, kde bylo před sdílením.
         */
        val returnToCaller = rememberReturnToCaller()
        val closeShare = {
            chatsStack.remove(ShareRoute)
            chatsStack.removeAll { it is ShareToChatRoute }
            returnToCaller()
        }
        /*
         * Role panelu se čte z metadat položky (`Panes`), a ty zná jen poskytovatel položek.
         * Ptá se na ni i on sám (seznam chatů: je konverzace vidět?), proto odkaz přes proměnnou.
         * Poskytovatel se staví dvakrát: šířka seznamu ve třech panelech (níž) se počítá z rolí
         * a sama jde do metadat seznamu. Role na ní nezávisí, takže na první stačí bez ní.
         */
        lateinit var entryProvider: (NavKey) -> NavEntry<NavKey>
        val paneOf: (NavKey) -> PaneInfo? = { entryProvider(it).paneInfo }
        val buildEntryProvider = { listPaneWidth: Dp?, profilePaneWidth: Dp? ->
            shellEntryProvider(
                chatsStack = chatsStack,
                settingsStack = settingsStack,
                selectedSettingsSection = settingsDisplayed.filterIsInstance<SettingsSectionRoute>()
                    .lastOrNull()?.section,
                wide = wide,
                twoPane = twoPane,
                paneOf = paneOf,
                listPaneWidth = listPaneWidth,
                profilePaneWidth = profilePaneWidth,
                closeShare = closeShare,
                navigation = navigation,
            )
        }
        entryProvider = buildEntryProvider(null, null)

        /*
         * Profil ve třetím panelu patří ke konverzaci pod sebou. Otevře-li se nad ním jiná
         * konverzace (jiný chat ze seznamu, zmínka, „Napsat zprávu" členovi skupiny), panel
         * zůstane otevřený a přejde na profil té nové (`followProfile`) — scéna by jinak nechala
         * starý profil vedle cizího chatu. Stejný vzor jako sekce Nastavení výš: efekt zásobník
         * opraví, kreslí se rovnou opravený. Na telefonu ne: tam je profil pod konverzací
         * obyčejná historie a zpět se do něj vrací.
         */
        val chatsDisplayed: List<NavKey> = if (wide) chatsStack.withProfileFollowing(paneOf) else chatsStack
        val profileMoved = chatsDisplayed !== chatsStack
        LaunchedEffect(profileMoved) {
            if (profileMoved) chatsStack.followProfile(paneOf)
        }

        /*
         * Třetí panel jen tehdy, když v něm něco je: scaffold se třemi oddíly by ho rozbalil
         * i prázdný. Rozhoduje poslední položka scény — obrazovka přes celou plochu (prohlížeč
         * nad profilem) roli nemá a pod ní se má scéna kreslit tak, jak ji uživatel nechal.
         * Pod THREE_PANE_MIN_WIDTH zůstanou dva oddíly a scaffold podle historie ukáže
         * konverzaci s profilem (sekci s podstránkou), seznam schová. Platí pro zásobník
         * vybrané sekce aplikace: v Chatech profil, v Nastavení podstránka sekce.
         */
        val shownStack = if (navigation.section == SettingsRoute) settingsDisplayed else chatsDisplayed
        val extraShown = shownStack.lastOrNull { paneOf(it) != null }?.let(paneOf)?.role == PaneRole.Extra
        val threePane = twoPane && extraShown && maxWidth >= THREE_PANE_MIN_WIDTH
        val directive = when {
            !twoPane -> base.singlePane()
            threePane -> base.threePane()
            else -> base.twoPane()
        }

        /*
         * Táhlo jen mezi HLAVNÍM seznamem chatů a konverzací. Archiv, nová zpráva a Nastavení
         * mají svou výchozí šířku: sbalený archiv by byl jen sloupec avatarů bez vysvětlení,
         * co je to za seznam. Mezi konverzací a profilem (pod Large) taky ne — stav táhla
         * počítá šířku PRVNÍHO panelu, takže by konverzaci zmáčkl na šířku sbaleného seznamu.
         * Ve třech panelech táhlo knihovna nekreslí vůbec; zvolená šířka seznamu tam jde
         * metadaty ([listWidthBesideProfile]).
         */
        val listExpansion = rememberListPaneExpansion(base.defaultPanePreferredWidth)
        val mainListShown = navigation.section == ChatsRoute &&
            chatsDisplayed.lastOrNull { paneOf(it)?.role == PaneRole.List } == ChatsRoute
        val draggable = twoPane && mainListShown && !extraShown
        val listPaneWidth = if (threePane && mainListShown) {
            listWidthBesideProfile(listExpansion.anchor, maxWidth, base.defaultPanePreferredWidth, PANE_SPACING)
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
        )

        /*
         * Šířka profilu vedle konverzace: táhlo v kartě profilu (`ProfilePaneResize`), dva
         * i tři panely. Jde metadaty profilu, protože táhlo knihovny ve třech panelech není
         * a ve dvou nastavuje šířku konverzace. Za tahu platí šířka tahu, po puštění kotva.
         */
        val profileBeside = navigation.section == ChatsRoute && extraBeside
        val profileResize = rememberProfilePaneResize(
            defaultWidth = base.defaultPanePreferredWidth,
            total = maxWidth,
            listWidth = if (threePane) listPaneWidth ?: base.defaultPanePreferredWidth else null,
        )
        val profilePaneWidth = profileResize.width.takeIf { profileBeside }
        if (listPaneWidth != null || profilePaneWidth != null) {
            entryProvider = buildEntryProvider(listPaneWidth, profilePaneWidth)
        }

        ShellShortcuts(navigation = navigation, chatsStack = chatsStack, twoPane = twoPane)

        // Široká plocha se hlásí dolů: obrazovky podle ní poznají, že se nevyměňují,
        // a sdílený prvek nezaloží — viz LocalPanesSideBySide.
        // Id Uložených zpráv pro všechno, co kreslí chat (název, záložka místo fotky).
        val savedMessagesChatId by chatRepository.savedMessagesChatId.collectAsStateWithLifecycle()

        // Plocha okna: leží na ní seznam a vedle něj karta (`PaneCard`).
        if (wide) Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceContainer))

        CompositionLocalProvider(
            LocalPanesSideBySide provides wide,
            LocalPaneNeighbours provides paneNeighbours,
            LocalSavedMessagesChatId provides savedMessagesChatId,
            // I ve třech panelech: zvolená kotva tam určuje šířku seznamu, takže rozbalení zabere i bez táhla.
            LocalExpandListPane provides listExpansion.expand.takeIf { twoPane && mainListShown },
            LocalProfilePaneResize provides profileResize.resize.takeIf { profileBeside },
        ) {
            val paneCard = rememberPaneCardDecorator()
            val decorators = remember(entryDecorators, paneCard) { entryDecorators + paneCard }
            /*
             * Položky Chatů se staví samy, ne přes `rememberDecoratedNavEntries(zásobník, …)`:
             * ta si je pamatuje jen podle klíčů, a šířka seznamu ve třech panelech je v jeho
             * METADATECH — po změně kotvy by zůstala stará.
             */
            /*
             * Zavřený třetí panel (profil, podstránka Nastavení) ještě chvíli ve scéně, ať odjede
             * animací, místo aby problikl — viz `rememberWithLeavingExtras`. Jen pro kreslení
             * a jen s panely vedle sebe: na telefonu je to celá obrazovka s vlastním přechodem.
             */
            val chatsWithLeaving = rememberWithLeavingExtras(chatsDisplayed, paneOf)
            val settingsWithLeaving = rememberWithLeavingExtras(settingsDisplayed, paneOf)
            val chatsDrawn = if (twoPane) chatsWithLeaving else chatsDisplayed
            val settingsDrawn = if (twoPane) settingsWithLeaving else settingsDisplayed
            val chatsEntries = remember(chatsDrawn.toList(), listPaneWidth, profilePaneWidth) { chatsDrawn.map(entryProvider) }
            /*
             * Položky se dekorují pro KAŽDOU sekci, i tu, která není vidět. Zásobník, který se
             * zrovna nedekoruje, by pro Nav3 vypadal jako odebraný a jeho ViewModely by se zrušily.
             */
            val entriesBySection = mapOf<TopLevelRoute, List<NavEntry<NavKey>>>(
                ChatsRoute to rememberDecoratedNavEntries(chatsEntries, decorators),
                SettingsRoute to rememberDecoratedNavEntries(settingsDrawn, decorators, entryProvider),
            )

            val transitions = rememberNavTransitions(chatContainer = !wide)
            NavDisplay(
                entries = navigation.visibleSections.flatMap { entriesBySection.getValue(it) },
                transitionSpec = transitions.forward(),
                popTransitionSpec = transitions.pop(),
                predictivePopTransitionSpec = transitions.predictivePop(),
                sceneDecoratorStrategies = remember { listOf(PredictiveBackCorners()) },
                // Zpět z výběru pro sdílení je totéž co ✕ — vrátit uživatele, odkud sdílel.
                onBack = {
                    // Zpět ze sdílení je totéž co ✕ — vrátit uživatele tam, odkud sdílel.
                    val share = chatsStack.lastOrNull().let { it == ShareRoute || it is ShareToChatRoute }
                    if (share) closeShare() else navigation.goBack()
                },
                sceneStrategies = listOf(
                    // `shouldHandleSinglePaneLayout = wide`: na široké ploše nechá i jednopanelový stav
                    // řešit scaffold, takže „jen seznam" a „seznam + konverzace" jsou TÁŽ scéna a přechod
                    // mezi nimi je plynulý. Na compactu vrací null → standardní swap obrazovek.
                    rememberListDetailSceneStrategy(
                        shouldHandleSinglePaneLayout = wide,
                        /*
                         * Zpět v Chatech zavře konverzaci a vrátí placeholder. Výchozí
                         * `PopUntilScaffoldValueChange` přeskakuje položky, které nemění
                         * rozložení panelů — a „seznam + placeholder" i „seznam + chat" jsou
                         * oba dvoupanel, takže zpět z konverzace odešel rovnou z aplikace.
                         * V Nastavení je výchozí chování správné: sekce je v dvoupanelu
                         * otevřená vždycky, takže zpět vede ze Nastavení pryč, do Chatů.
                         * Výjimka je úprava profilu a seznam blokovaných nad sekcí: dvoupanel
                         * zůstává dvoupanelem, takže by zpět přeskočilo i sekci pod ní a odešlo
                         * z Nastavení.
                         */
                        backNavigationBehavior = if (
                            navigation.section == ChatsRoute ||
                            settingsStack.lastOrNull().isOverSettingsSection()
                        ) {
                            BackNavigationBehavior.PopUntilContentChange
                        } else {
                            BackNavigationBehavior.PopUntilScaffoldValueChange
                        },
                        directive = directive,
                        paneExpansionDragHandle = ListPaneDragHandle.takeIf { draggable },
                        paneExpansionState = listExpansion.state.takeIf { draggable },
                    ),
                ),
            )
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
            navigation.stack(ChatsRoute).add(InstantViewRoute(request.url))
        }

        ExternalLinkHost(
            viewModel = koinViewModel(),
            onNavigate = { destination -> navigation.openExternal(destination, twoPane) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
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
    /** Šířka hlavního seznamu ve třech panelech (zvolená kotva); jinak `null` = podle táhla. */
    listPaneWidth: Dp?,
    /** Šířka profilu vedle konverzace (táhlo profilu); `null` = výchozí. */
    profilePaneWidth: Dp?,
    closeShare: () -> Unit,
    navigation: AppNavigation,
): (NavKey) -> NavEntry<NavKey> = entryProvider {

    entry<ChatsRoute>(
        metadata = Panes.list(
            detailPlaceholder = { PaneCard(PaneRole.Detail) { ConversationPlaceholder() } },
        ) + ChatListMetadata + Panes.width(listPaneWidth),
    ) {
        val settings = koinInject<AppSettings>()
        val savedMessagesChatId = LocalSavedMessagesChatId.current
        ChatListScreen(
            viewModel = koinViewModel(),
            // Konverzace vedle seznamu má vlastní přehrávač (dock), druhý by ho zdvojil. Profil
            // ve třetím panelu ji nezakrývá, takže se nepočítá.
            conversationOpen = chatsStack.lastOrNull { paneOf(it)?.role != PaneRole.Extra } is ConversationRoute,
            // Vybraný řádek jen vedle detailu. Na telefonu by se otevíraný řádek přebarvil
            // uprostřed růstu v konverzaci (viz ChatContainer) a nic by tím neoznačil.
            selectedChatId = chatsStack.lastOrNull { it is ConversationRoute || it is ForumTopicsRoute || it is SavedTopicsRoute }
                ?.let { (it as? ConversationRoute)?.chatId ?: (it as? ForumTopicsRoute)?.chatId ?: (it as SavedTopicsRoute).chatId }
                ?.takeIf { twoPane },
            onChatClick = { chatId ->
                // Uložené zprávy po chatech, když si to uživatel zvolil („Zobrazit jako chaty").
                if (chatId == savedMessagesChatId && settings.savedMessagesAsChats.value) {
                    chatsStack.openSavedTopics(chatId)
                } else {
                    chatsStack.openConversation(chatId)
                }
            },
            onForumClick = { chatId -> chatsStack.openForum(chatId) },
            // Koncept v tématu z přehledu konceptů: téma a pod ním seznam témat, jako u nalezené zprávy.
            onTopicClick = { chatId, topicId -> chatsStack.openFoundMessage(chatId, messageId = null, topicId = topicId) },
            onMessageClick = { chatId, messageId, topicId -> chatsStack.openFoundMessage(chatId, messageId, topicId) },
            searchRequest = navigation.searchRequest,
            onSearchRequestHandled = navigation::consumeSearchRequest,
            onOpenArchive = {
                if (chatsStack.lastOrNull() != ArchiveRoute) chatsStack.add(ArchiveRoute)
            },
            onNewChat = {
                if (chatsStack.lastOrNull() != NewChatRoute) chatsStack.add(NewChatRoute)
            },
            onNewGroup = { if (chatsStack.lastOrNull() != NewGroupMembersRoute) chatsStack.add(NewGroupMembersRoute) },
            onNewChannel = { chatsStack.add(CreateChatRoute(channel = true)) },
            onChatArchived = chatsStack::closeConversationOf,
            onOpenSettings = navigation::openSettings,
            onOpenProxySettings = navigation::openProxySettings,
            onCreateFolder = { navigation.openChatFolders(create = true) },
            onManageFolders = { navigation.openChatFolders() },
            onOpenScreen = { screen -> navigation.openScreen(screen, twoPane) },
            onOpenSearch = { navigation.openSearch(query = "", keepConversation = twoPane) },
            modifier = Modifier.listPaneInsets(twoPane),
        )
    }

    /*
     * Archiv je samostatná list-detail scéna (ARCHIVE_SCENE). V dvoupanelu má vpravo vlastní
     * detail panel [ArchiveConversationRoute] (nebo placeholder), takže nezasahuje do otevřené
     * konverzace hlavního seznamu; zpět odebere celou archivní větev a vrátí hlavní seznam.
     */
    entry<ArchiveRoute>(
        metadata = Panes.list(
            ARCHIVE_SCENE,
            detailPlaceholder = { PaneCard(PaneRole.Detail) { ConversationPlaceholder() } },
        ) + ChatListMetadata,
    ) {
        ArchiveScreen(
            // Klíč se složkou: ViewModel se cachuje podle klíče a Koin by u cachované instance
            // parametr ignoroval (viz konverzace).
            viewModel = koinViewModel(
                key = "chats-archive",
                parameters = { parametersOf(ChatFolder.Archive) },
            ),
            selectedChatId = chatsStack.lastOrNull { it is ArchiveConversationRoute || it is ArchiveForumTopicsRoute }
                ?.let { (it as? ArchiveConversationRoute)?.chatId ?: (it as ArchiveForumTopicsRoute).chatId }
                ?.takeIf { twoPane },
            onChatClick = { chatId -> chatsStack.openArchiveConversation(chatId) },
            onForumClick = { chatId -> chatsStack.openArchiveForum(chatId) },
            onBack = chatsStack::closeArchive,
            onChatArchived = chatsStack::closeConversationOf,
            modifier = Modifier.listPaneInsets(twoPane),
        )
    }

    /*
     * Zahájení nové konverzace. List panel jako Chaty a Archiv: vlevo nahradí seznam chatů,
     * po výběru kontaktu se odebere a otevře se konverzace.
     */
    entry<NewChatRoute>(
        metadata = Panes.list(
            detailPlaceholder = { PaneCard(PaneRole.Detail) { ConversationPlaceholder() } },
        ),
    ) {
        NewChatScreen(
            viewModel = koinViewModel(),
            onBack = { chatsStack.remove(NewChatRoute) },
            onChatSelected = { chatId ->
                chatsStack.remove(NewChatRoute)
                chatsStack.openConversation(chatId)
            },
            onNewGroup = { if (chatsStack.lastOrNull() != NewGroupMembersRoute) chatsStack.add(NewGroupMembersRoute) },
            onNewChannel = { chatsStack.add(CreateChatRoute(channel = true)) },
            onNewContact = { if (chatsStack.lastOrNull() != NewContactRoute) chatsStack.add(NewContactRoute) },
            modifier = Modifier.listPaneInsets(twoPane),
        )
    }

    /*
     * Nový kontakt. Formulář přes celou plochu jako založení skupiny; po přidání se odebere
     * i „Nová zpráva" pod ním a otevře se konverzace — zpět z ní vede do seznamu.
     */
    entry<NewContactRoute> {
        NewContactScreen(
            viewModel = koinViewModel(),
            onBack = { chatsStack.remove(NewContactRoute) },
            onAdded = { chatId ->
                chatsStack.removeAll { it == NewContactRoute || it == NewChatRoute }
                chatsStack.openConversation(chatId)
            },
        )
    }

    /*
     * Založení skupiny a kanálu. Bez metadat scény, jako přeposílání: formulář jde přes celou
     * plochu i na široké obrazovce. Po založení se celá větev (nová zpráva, členové, formulář)
     * odebere a otevře se nový chat — zpět z něj vede do seznamu, ne zpátky do formuláře.
     */
    entry<NewGroupMembersRoute> {
        NewGroupMembersScreen(
            // Vlastní klíč: výběr členů nesmí sdílet hledání s „Novou zprávou" pod sebou.
            viewModel = koinViewModel(key = "new-group-members"),
            onBack = { chatsStack.remove(NewGroupMembersRoute) },
            onNext = { members -> chatsStack.add(CreateChatRoute(channel = false, memberChatIds = members)) },
        )
    }

    entry<CreateChatRoute> { route ->
        CreateChatScreen(
            viewModel = koinViewModel(
                key = "create-chat-${route.channel}-${route.memberChatIds.joinToString(",")}",
                parameters = { parametersOf(route.channel, route.memberChatIds) },
            ),
            onBack = { chatsStack.pop(route) },
            onCreated = { chatId ->
                chatsStack.removeAll { it is CreateChatRoute || it == NewGroupMembersRoute || it == NewChatRoute }
                chatsStack.openConversation(chatId)
            },
        )
    }

    entry<ConversationRoute>(metadata = Panes.detail() + ChatScreenMetadata) { route ->
        ColoredChatContainer(route.chatId, fromList = remember(route) { chatsStack.liesOnChatList(route) }) {
            val settings = koinInject<AppSettings>()
            val sideBySide = LocalPanesSideBySide.current
            ConversationScreen(
                // Klíč MUSÍ obsahovat chatId. ViewModel se cachuje podle klíče, takže
                // konstantní klíč znamená, že druhá otevřená konverzace dostane ViewModel
                // té první — i se zprávami. Koin navíc u cachované instance ignoruje
                // nové `parameters`, takže se to neprojeví ani chybou, jen špatným obsahem.
                viewModel = koinViewModel(
                    key = "conversation-${route.chatId}-${route.messageId}-${route.threadId}-${route.topicId}-${route.savedTopicId}",
                    parameters = { parametersOf(route.chatId, route.messageId, route.threadId, route.topicId, route.savedTopicId) },
                ),
                navigation = ConversationNavigation(
                    // Odebírá SVOU položku, ne poslední — viz `pop`. Vedle seznamu zavře celou větev nad ním.
                    onBack = { chatsStack.closeConversation(route, list = ChatsRoute, sideBySide = sideBySide) },
                    onChatGone = { chatsStack.closeChat(route.chatId) },
                    onOpenMedia = { messageId ->
                        chatsStack.add(MediaRoute(route.chatId, messageId))
                    },
                    onOpenMediaAt = { messageId, seconds ->
                        chatsStack.add(MediaRoute(route.chatId, messageId, startSeconds = seconds))
                    },
                    onOpenPollMedia = { messageId, index ->
                        chatsStack.add(MediaRoute(route.chatId, messageId, pollMedia = index))
                    },
                    onOpenArticleMedia = { messageId, index ->
                        chatsStack.add(MediaRoute(route.chatId, messageId, articleMedia = index))
                    },
                    onOpenStory = { story ->
                        chatsStack.add(MediaRoute(story.posterChatId, messageId = 0, storyId = story.storyId))
                    },
                    onForward = { messageIds ->
                        chatsStack.add(ForwardRoute(route.chatId, messageIds))
                    },
                    // Zmínka i odkaz otevře chat NAD touhle konverzací (ne místo ní), takže zpět vede sem.
                    // Odkaz na zprávu nese její id a konverzace na ni po otevření skočí.
                    // Komentáře jsou vlákno diskusní skupiny nad kanálem, téma jde rovnou do tématu
                    // a skupina s tématy do seznamu témat (`openTarget`).
                    onOpenChat = { target -> chatsStack.openTarget(target, archive = false) },
                    onSearch = { query -> navigation.openSearch(query, keepConversation = twoPane) },
                    onOpenProfile = { chatsStack.toggleProfile(route, ChatProfileRoute(route.chatId), sideBySide = wide) },
                    onOpenUserProfile = { chatId -> chatsStack.add(ChatProfileRoute(chatId)) },
                    onCreatePoll = { request -> chatsStack.add(pollComposerRoute(route.chatId, request)) },
                    onOpenWebApp = { request -> chatsStack.add(webAppRoute(route.chatId, request, archive = false)) },
                    onOpenBotShare = { request -> chatsStack.add(botShareRoute(route.chatId, request)) },
                    onAddBotToGroup = { link -> chatsStack.add(AddBotToGroupRoute(link.url, link.botChatId, link.asAdmin)) },
                    onOpenScreen = { screen -> navigation.openScreen(screen, twoPane) },
                    onSwitchInline = { pick -> chatsStack.add(pick.toRoute()) },
                    onAddMembers = { chatsStack.addOnce(AddMembersRoute(route.chatId)) },
                    onOpenJoinRequests = { chatsStack.addOnce(JoinRequestsRoute(route.chatId)) },
                    // Uložené zprávy po chatech: volba se pamatuje a seznam témat nahradí konverzaci.
                    onViewAsChats = {
                        settings.setSavedMessagesAsChats(true)
                        chatsStack.openSavedTopics(route.chatId)
                    },
                    onSwitchChat = rememberSwitchChat(navigation),
                ),
            )
        }
    }

    /*
     * Témata Uložených zpráv stojí na místě konverzace (detail panel), jako témata skupiny;
     * téma se otevře jako konverzace nad nimi.
     */
    entry<SavedTopicsRoute>(metadata = Panes.detail() + ChatScreenMetadata) { route ->
        ColoredChatContainer(route.chatId, fromList = remember(route) { chatsStack.liesOnChatList(route) }) {
            SavedTopicsScreen(
                viewModel = koinViewModel(key = "saved-topics-${route.chatId}", parameters = { parametersOf(route.chatId) }),
                onBack = { chatsStack.pop(route) },
                onOpenTopic = { topicId ->
                    if ((chatsStack.lastOrNull() as? ConversationRoute)?.savedTopicId != topicId) {
                        chatsStack.add(ConversationRoute(route.chatId, savedTopicId = topicId))
                    }
                },
                onViewAsMessages = { chatsStack.openConversation(route.chatId) },
            )
        }
    }

    /*
     * Témata skupiny s tématy stojí na místě konverzace (detail panel); téma se otevře jako
     * konverzace NAD nimi, takže zpět vede do seznamu témat.
     */
    entry<ForumTopicsRoute>(metadata = Panes.detail() + ChatScreenMetadata) { route ->
        ColoredChatContainer(route.chatId, fromList = remember(route) { chatsStack.liesOnChatList(route) }) {
            ForumTopicsScreen(
                viewModel = koinViewModel(key = "forum-${route.chatId}", parameters = { parametersOf(route.chatId) }),
                onBack = { chatsStack.pop(route) },
                onOpenTopic = { topicId ->
                    if ((chatsStack.lastOrNull() as? ConversationRoute)?.topicId != topicId) {
                        chatsStack.add(ConversationRoute(route.chatId, topicId = topicId))
                    }
                },
                onOpenProfile = { chatsStack.toggleProfile(route, ChatProfileRoute(route.chatId), sideBySide = wide) },
                onViewAsMessages = { chatsStack.add(ConversationRoute(route.chatId)) },
                onOpenJoinRequests = { chatsStack.addOnce(JoinRequestsRoute(route.chatId)) },
            )
        }
    }

    entry<ArchiveForumTopicsRoute>(metadata = Panes.detail(ARCHIVE_SCENE) + ChatScreenMetadata) { route ->
        ColoredChatContainer(route.chatId, fromList = remember(route) { chatsStack.liesOnChatList(route) }) {
            ForumTopicsScreen(
                viewModel = koinViewModel(key = "forum-${route.chatId}", parameters = { parametersOf(route.chatId) }),
                onBack = { chatsStack.pop(route) },
                onOpenTopic = { topicId ->
                    if ((chatsStack.lastOrNull() as? ArchiveConversationRoute)?.topicId != topicId) {
                        chatsStack.add(ArchiveConversationRoute(route.chatId, topicId = topicId))
                    }
                },
                onOpenProfile = { chatsStack.toggleProfile(route, ArchiveChatProfileRoute(route.chatId), sideBySide = wide) },
                onViewAsMessages = { chatsStack.add(ArchiveConversationRoute(route.chatId)) },
                onOpenJoinRequests = { chatsStack.addOnce(ArchiveJoinRequestsRoute(route.chatId)) },
            )
        }
    }

    // Článek je detail jako konverzace: na širokém rozvržení ji v pravém panelu nahradí.
    entry<InstantViewRoute>(metadata = Panes.detail()) { route ->
        InstantViewScreen(
            viewModel = koinViewModel(key = "instant-view-${route.url}", parameters = { parametersOf(route.url) }),
            onBack = { chatsStack.pop(route) },
        )
    }

    /*
     * Profil je třetí panel (extra): na velké ploše se otevře vedle seznamu a konverzace, na
     * střední vedle konverzace (seznam se schová), na telefonu ji nahradí. Totéž obrazovky, které
     * se z něj otevírají — jako detail by nahradily konverzaci uprostřed. Jiná konverzace
     * otevřená nad profilem ho na široké ploše vystřídá profilem té nové (`followProfile`).
     */
    entry<ChatProfileRoute>(metadata = Panes.extra() + Panes.width(profilePaneWidth)) { route ->
        val searchRequests = koinInject<ConversationSearchRequests>()
        val jumpRequests = koinInject<ConversationJumpRequests>()
        ChatColorTheme(route.chatId) {
            ChatProfileScreen(
                viewModel = koinViewModel(key = "profile-${route.chatId}", parameters = { parametersOf(route.chatId) }),
                onBack = { chatsStack.pop(route) },
                // Hledat patří konverzaci: profil se zavře a konverzace pod ním otevře hledání.
                // Profil odesílatele otevřený ze skupiny má pod sebou SKUPINU — pak se místo ní
                // otevře konverzace s ním, jinak by hledání čekalo na chat, který není vidět.
                onSearch = {
                    searchRequests.request(route.chatId)
                    chatsStack.pop(route)
                    if (!chatsStack.isOpenConversation(route.chatId)) chatsStack.add(ConversationRoute(route.chatId))
                },
                onOpenChat = { chatId ->
                    if (!chatsStack.isOpenConversation(chatId)) chatsStack.add(ConversationRoute(chatId))
                },
                // Z profilu letí fotka z mřížky sdílených médií, ne z bubliny — jiný klíč sdíleného prvku.
                onOpenMedia = { messageId, articleMedia ->
                    chatsStack.add(MediaRoute(route.chatId, messageId, fromSharedMedia = true, articleMedia = articleMedia))
                },
                onOpenPhotos = { chatsStack.add(MediaRoute(route.chatId, messageId = 0L, profilePhotos = true)) },
                onEdit = { if (chatsStack.lastOrNull() != EditChatRoute(route.chatId)) chatsStack.add(EditChatRoute(route.chatId)) },
                onShareContact = { userId -> chatsStack.addOnce(ShareContactRoute(userId)) },
                onAddMembers = { chatsStack.addOnce(AddMembersRoute(route.chatId)) },
                onOpenMemberRights = { userId, admin ->
                    chatsStack.add(MemberRightsRoute(route.chatId, userId, admin))
                },
                // Soukromý chat s botem má id bota, takže chat je zároveň bot.
                onOpenMainWebApp = {
                    chatsStack.add(WebAppRoute(route.chatId, route.chatId, url = "", launch = false, archive = false, main = true))
                },
                // Záložka: profil se zavře a konverzace pod ním skočí na zprávu (`ConversationJumpRequests`).
                // Bez konverzace pod profilem se otevře rovnou na té zprávě.
                onOpenMessage = { messageId ->
                    jumpRequests.request(route.chatId, messageId)
                    chatsStack.pop(route)
                    if (!chatsStack.isOpenConversation(route.chatId)) chatsStack.add(ConversationRoute(route.chatId, messageId))
                },
                closeIcon = LocalPanesSideBySide.current,
            )
        }
    }

    // Úprava se vrší nad profil, ve třetím panelu ho nahradí.
    entry<EditChatRoute>(metadata = Panes.extra() + Panes.width(profilePaneWidth)) { route ->
        EditChatScreen(
            viewModel = koinViewModel(key = "edit-chat-${route.chatId}", parameters = { parametersOf(route.chatId) }),
            onBack = { chatsStack.pop(route) },
            onOpenInviteLinks = { chatsStack.addOnce(InviteLinksRoute(route.chatId)) },
            onOpenJoinRequests = { chatsStack.addOnce(JoinRequestsRoute(route.chatId)) },
            onUpgraded = { old, new -> chatsStack.replaceUpgradedChat(old, new) },
        )
    }

    entry<ArchiveEditChatRoute>(metadata = Panes.extra(ARCHIVE_SCENE) + Panes.width(profilePaneWidth)) { route ->
        EditChatScreen(
            viewModel = koinViewModel(key = "edit-chat-${route.chatId}", parameters = { parametersOf(route.chatId) }),
            onBack = { chatsStack.pop(route) },
            onOpenInviteLinks = { chatsStack.addOnce(ArchiveInviteLinksRoute(route.chatId)) },
            onOpenJoinRequests = { chatsStack.addOnce(ArchiveJoinRequestsRoute(route.chatId)) },
            onUpgraded = { old, new -> chatsStack.replaceUpgradedChat(old, new) },
        )
    }

    // Pozvánky a žádosti se vrší nad úpravu, taky ve třetím panelu.
    entry<InviteLinksRoute>(metadata = Panes.extra() + Panes.width(profilePaneWidth)) { route ->
        InviteLinksScreen(
            viewModel = koinViewModel(key = "invite-links-${route.chatId}", parameters = { parametersOf(route.chatId) }),
            onBack = { chatsStack.pop(route) },
        )
    }

    entry<ArchiveInviteLinksRoute>(metadata = Panes.extra(ARCHIVE_SCENE) + Panes.width(profilePaneWidth)) { route ->
        InviteLinksScreen(
            viewModel = koinViewModel(key = "invite-links-${route.chatId}", parameters = { parametersOf(route.chatId) }),
            onBack = { chatsStack.pop(route) },
        )
    }

    entry<JoinRequestsRoute>(metadata = Panes.extra() + Panes.width(profilePaneWidth)) { route ->
        JoinRequestsScreen(
            viewModel = koinViewModel(key = "join-requests-${route.chatId}", parameters = { parametersOf(route.chatId) }),
            onBack = { chatsStack.pop(route) },
        )
    }

    entry<ArchiveJoinRequestsRoute>(metadata = Panes.extra(ARCHIVE_SCENE) + Panes.width(profilePaneWidth)) { route ->
        JoinRequestsScreen(
            viewModel = koinViewModel(key = "join-requests-${route.chatId}", parameters = { parametersOf(route.chatId) }),
            onBack = { chatsStack.pop(route) },
        )
    }

    /*
     * Práva člena se vrší nad profil stejně jako úprava skupiny a ve třetím panelu ho
     * nahradí. Klíč nese i `userId` a druh práv: ViewModel se cachuje
     * podle klíče a Koin u cachované instance ignoruje nové parametry.
     */
    entry<MemberRightsRoute>(metadata = Panes.extra() + Panes.width(profilePaneWidth)) { route ->
        MemberRightsScreen(
            viewModel = koinViewModel(
                key = "member-rights-${route.chatId}-${route.userId}-${route.admin}",
                parameters = { parametersOf(route.chatId, route.userId, route.admin) },
            ),
            onBack = { chatsStack.pop(route) },
        )
    }

    entry<ArchiveMemberRightsRoute>(metadata = Panes.extra(ARCHIVE_SCENE) + Panes.width(profilePaneWidth)) { route ->
        MemberRightsScreen(
            viewModel = koinViewModel(
                key = "member-rights-${route.chatId}-${route.userId}-${route.admin}",
                parameters = { parametersOf(route.chatId, route.userId, route.admin) },
            ),
            onBack = { chatsStack.pop(route) },
        )
    }

    entry<ArchiveConversationRoute>(metadata = Panes.detail(ARCHIVE_SCENE) + ChatScreenMetadata) { route ->
        ColoredChatContainer(route.chatId, fromList = remember(route) { chatsStack.liesOnChatList(route) }) {
            val sideBySide = LocalPanesSideBySide.current
            ConversationScreen(
                viewModel = koinViewModel(
                    key = "conversation-${route.chatId}-${route.messageId}-${route.threadId}-${route.topicId}",
                    parameters = { parametersOf(route.chatId, route.messageId, route.threadId, route.topicId) },
                ),
                navigation = ConversationNavigation(
                    onBack = { chatsStack.closeConversation(route, list = ArchiveRoute, sideBySide = sideBySide) },
                    onChatGone = { chatsStack.closeChat(route.chatId) },
                    onOpenMedia = { messageId ->
                        chatsStack.add(MediaRoute(route.chatId, messageId))
                    },
                    onOpenMediaAt = { messageId, seconds ->
                        chatsStack.add(MediaRoute(route.chatId, messageId, startSeconds = seconds))
                    },
                    onOpenPollMedia = { messageId, index ->
                        chatsStack.add(MediaRoute(route.chatId, messageId, pollMedia = index))
                    },
                    onOpenArticleMedia = { messageId, index ->
                        chatsStack.add(MediaRoute(route.chatId, messageId, articleMedia = index))
                    },
                    onOpenStory = { story ->
                        chatsStack.add(MediaRoute(story.posterChatId, messageId = 0, storyId = story.storyId))
                    },
                    onForward = { messageIds ->
                        chatsStack.add(ForwardRoute(route.chatId, messageIds))
                    },
                    onOpenChat = { target -> chatsStack.openTarget(target, archive = true) },
                    onSearch = { query -> navigation.openSearch(query, keepConversation = false) },
                    onOpenProfile = { chatsStack.toggleProfile(route, ArchiveChatProfileRoute(route.chatId), sideBySide = wide) },
                    onOpenUserProfile = { chatId -> chatsStack.add(ArchiveChatProfileRoute(chatId)) },
                    onUnarchived = { chatsStack.moveConversationToMain(route.chatId) },
                    onCreatePoll = { request -> chatsStack.add(pollComposerRoute(route.chatId, request)) },
                    onOpenWebApp = { request -> chatsStack.add(webAppRoute(route.chatId, request, archive = true)) },
                    onOpenBotShare = { request -> chatsStack.add(botShareRoute(route.chatId, request)) },
                    onAddBotToGroup = { link -> chatsStack.add(AddBotToGroupRoute(link.url, link.botChatId, link.asAdmin)) },
                    onOpenScreen = { screen -> navigation.openScreen(screen, twoPane = false) },
                    onSwitchInline = { pick -> chatsStack.add(pick.toRoute()) },
                    onAddMembers = { chatsStack.addOnce(AddMembersRoute(route.chatId)) },
                    onOpenJoinRequests = { chatsStack.addOnce(ArchiveJoinRequestsRoute(route.chatId)) },
                    onSwitchChat = rememberSwitchChat(navigation),
                ),
            )
        }
    }

    entry<ArchiveChatProfileRoute>(metadata = Panes.extra(ARCHIVE_SCENE) + Panes.width(profilePaneWidth)) { route ->
        val searchRequests = koinInject<ConversationSearchRequests>()
        val jumpRequests = koinInject<ConversationJumpRequests>()
        ChatColorTheme(route.chatId) {
            ChatProfileScreen(
                viewModel = koinViewModel(key = "profile-${route.chatId}", parameters = { parametersOf(route.chatId) }),
                onBack = { chatsStack.pop(route) },
                onSearch = {
                    searchRequests.request(route.chatId)
                    chatsStack.pop(route)
                    if (!chatsStack.isOpenArchiveConversation(route.chatId)) {
                        chatsStack.add(ArchiveConversationRoute(route.chatId))
                    }
                },
                onOpenChat = { chatId ->
                    if (!chatsStack.isOpenArchiveConversation(chatId)) {
                        chatsStack.add(ArchiveConversationRoute(chatId))
                    }
                },
                // Z profilu letí fotka z mřížky sdílených médií, ne z bubliny — jiný klíč sdíleného prvku.
                onOpenMedia = { messageId, articleMedia ->
                    chatsStack.add(MediaRoute(route.chatId, messageId, fromSharedMedia = true, articleMedia = articleMedia))
                },
                onOpenPhotos = { chatsStack.add(MediaRoute(route.chatId, messageId = 0L, profilePhotos = true)) },
                onEdit = {
                    if (chatsStack.lastOrNull() != ArchiveEditChatRoute(route.chatId)) {
                        chatsStack.add(ArchiveEditChatRoute(route.chatId))
                    }
                },
                onShareContact = { userId -> chatsStack.addOnce(ShareContactRoute(userId)) },
                onAddMembers = { chatsStack.addOnce(AddMembersRoute(route.chatId)) },
                onOpenMemberRights = { userId, admin ->
                    chatsStack.add(ArchiveMemberRightsRoute(route.chatId, userId, admin))
                },
                // Soukromý chat s botem má id bota, takže chat je zároveň bot.
                onOpenMainWebApp = {
                    chatsStack.add(WebAppRoute(route.chatId, route.chatId, url = "", launch = false, archive = true, main = true))
                },
                onOpenMessage = { messageId ->
                    jumpRequests.request(route.chatId, messageId)
                    chatsStack.pop(route)
                    if (!chatsStack.isOpenArchiveConversation(route.chatId)) {
                        chatsStack.add(ArchiveConversationRoute(route.chatId, messageId))
                    }
                },
                closeIcon = LocalPanesSideBySide.current,
            )
        }
    }

    /*
     * Prohlížeč nemá metadata list-detail scény, takže ho scéna nepřevezme
     * a `NavDisplay` ho vykreslí přes celou plochu — i na širokém rozvržení,
     * kde by panel vedle seznamu chatů neměl co dělat.
     */
    entry<MediaRoute> { route ->
        val replyRequests = koinInject<ConversationReplyRequests>()
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
                    key = "media-${route.chatId}-${route.messageId}-${route.pollMedia}-${route.profilePhotos}-${route.articleMedia}-${route.storyId}",
                    parameters = {
                        parametersOf(route.chatId, route.messageId, route.pollMedia, route.profilePhotos, route.articleMedia, route.storyId)
                    },
                ),
                // Profilovky vlastního účtu se otevírají z Nastavení, všechno ostatní z Chatů.
                onBack = { chatsStack.pop(route) || settingsStack.pop(route) },
                onForward = { messageId ->
                    chatsStack.add(ForwardRoute(route.chatId, listOf(messageId)))
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
            )
        }
    }

    /*
     * Výběr cílového chatu. Po přeposlání se výběr ze zásobníku zahodí a otevře
     * se cílový chat — uživatel tak skončí tam, kam zprávu poslal, a zpět ho
     * vrátí do původní konverzace.
     */
    // Mini aplikace bota. Odkaz na chat z ní zavře ji a otevře chat nad konverzací pod ní.
    entry<WebAppRoute> { route ->
        WebAppScreen(
            viewModel = koinViewModel(
                key = "web-app-${route.chatId}-${route.botUserId}-${route.url.hashCode()}-${route.main}-${route.shortName}-${route.startParameter}",
                parameters = {
                    parametersOf(
                        route.chatId, route.botUserId, route.url, route.launch, route.keyboardButtonText,
                        route.main, route.startParameter, route.shortName,
                    )
                },
            ),
            onClose = { chatsStack.pop(route) },
            onOpenChat = { target ->
                chatsStack.pop(route)
                chatsStack.openTarget(target, archive = route.archive)
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

    /*
     * Výběr skupiny pro bota ze startovacího odkazu (`t.me/bot?startgroup=…`). Po přidání ho
     * nahradí skupina, kde bot právě odpověděl — jako u přeposílání.
     */
    entry<AddBotToGroupRoute> { route ->
        AddBotToGroupScreen(
            viewModel = koinViewModel(
                key = "add-bot-${route.url}-${route.asAdmin}",
                parameters = { parametersOf(route.url, route.botChatId, route.asAdmin) },
            ),
            onBack = { chatsStack.pop(route) },
            onAdded = { chatId ->
                if (chatsStack.pop(route) && !chatsStack.isOpenConversation(chatId)) {
                    chatsStack.add(ConversationRoute(chatId))
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
                if (chatsStack.pop(route) && !chatsStack.isOpenConversation(chatId)) {
                    chatsStack.add(ConversationRoute(chatId))
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
                parameters = { parametersOf(route.chatId, route.messageId, users, chat) },
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
        ShareContactScreen(
            viewModel = koinViewModel(key = "share-contact-${route.userId}", parameters = { parametersOf(route.userId) }),
            onBack = { chatsStack.pop(route) },
            // Jako po přeposlání: otevře se první vybraný chat, je vidět, že kontakt dorazil.
            onSent = { chatId ->
                if (chatsStack.pop(route) && !chatsStack.isOpenConversation(chatId)) {
                    chatsStack.add(ConversationRoute(chatId))
                }
            },
        )
    }

    entry<ForwardRoute> { route ->
        ForwardScreen(
            viewModel = koinViewModel(
                key = "forward-${route.fromChatId}-${route.messageIds.joinToString("-")}",
                parameters = { parametersOf(route.fromChatId, route.messageIds) },
            ),
            onBack = { chatsStack.pop(route) },
            onForwarded = { chatId ->
                // Výběr už v zásobníku není (odchází) — cíl otevřel předchozí volání.
                if (chatsStack.pop(route) && !chatsStack.isOpenConversation(chatId)) {
                    chatsStack.add(ConversationRoute(chatId))
                }
            },
        )
    }

    /*
     * Výběr chatu pro obsah sdílený ze systému. Po výběru ho nahradí vybraný chat a obsah čeká
     * nad vstupním polem; ✕ i zpět sdílení zahodí a vrátí uživatele do aplikace, ze které sdílel.
     */
    /*
     * Potvrzení sdílení do chatu vybraného v systémové nabídce (Direct Share). Po odeslání ho
     * nahradí ta konverzace — je tím vidět, že zpráva dorazila, jako u přeposílání.
     */
    entry<ShareToChatRoute> { route ->
        ShareToChatScreen(
            viewModel = koinViewModel(
                key = "share-to-chat-${'$'}{route.chatId}",
                parameters = { parametersOf(route.chatId) },
            ),
            onClose = closeShare,
            onSent = {
                chatsStack.removeAll { it is ShareToChatRoute }
                if (!chatsStack.isOpenConversation(route.chatId)) {
                    chatsStack.add(ConversationRoute(route.chatId))
                }
            },
        )
    }

    entry<ShareRoute> {
        ShareScreen(
            viewModel = koinViewModel(),
            onClose = closeShare,
            // Vybraný chat výběr NAHRADÍ — zpět z něj vede na seznam, ne do výběru.
            onChosen = { chatId ->
                chatsStack.remove(ShareRoute)
                if (!chatsStack.isOpenConversation(chatId)) chatsStack.add(ConversationRoute(chatId))
            },
        )
    }

    entry<SettingsRoute>(metadata = Panes.list(SETTINGS_SCENE)) {
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
            onOpenChat = { chatId -> navigation.openChat(chatId) },
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
        metadata = Panes.detail(SETTINGS_SCENE) + Panes.contentWidth(ContentWidth.Reading),
    ) { route ->
        // V dvoupanelu je seznam sekcí pořád vidět vedle, takže šipka zpět nemá
        // kam vést; na telefonu je sekce samostatná obrazovka a zpět se z ní chodí.
        val popSection: () -> Unit = { settingsStack.pop(route) }

        SettingsSectionScreen(
            section = route.section,
            onBack = popSection.takeUnless { LocalPanesSideBySide.current },
            onOpenBlocked = { if (settingsStack.lastOrNull() != BlockedSendersRoute) settingsStack.add(BlockedSendersRoute) },
            onOpenFolders = { if (settingsStack.lastOrNull() != ChatFoldersRoute) settingsStack.add(ChatFoldersRoute) },
            onOpenStickerSets = { if (settingsStack.lastOrNull() != StickerSetsRoute) settingsStack.add(StickerSetsRoute) },
            onOpenEmojiSets = { if (settingsStack.lastOrNull() != EmojiSetsRoute) settingsStack.add(EmojiSetsRoute) },
            onOpenProxy = { if (settingsStack.lastOrNull() != ProxySettingsRoute) settingsStack.add(ProxySettingsRoute) },
            onOpenAccountPrivacy = { if (settingsStack.lastOrNull() != AccountPrivacyRoute) settingsStack.add(AccountPrivacyRoute) },
            onOpenTwoStep = { if (settingsStack.lastOrNull() != TwoStepRoute) settingsStack.add(TwoStepRoute) },
            onOpenLoginEmail = { if (settingsStack.lastOrNull() != LoginEmailRoute) settingsStack.add(LoginEmailRoute) },
            onOpenPasskeys = { if (settingsStack.lastOrNull() != PasskeysRoute) settingsStack.add(PasskeysRoute) },
            onOpenNetworkUsage = { if (settingsStack.lastOrNull() != NetworkUsageRoute) settingsStack.add(NetworkUsageRoute) },
            onOpenAppLock = { if (settingsStack.lastOrNull() != AppLockRoute) settingsStack.add(AppLockRoute) },
        )
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
        ChatFoldersScreen(
            viewModel = koinViewModel(),
            onBack = { settingsStack.pop(route) },
            onEdit = { id -> if (settingsStack.lastOrNull() !is ChatFolderEditRoute) settingsStack.add(ChatFolderEditRoute(id)) },
        )
    }

    // Sady samolepek nad sekcí Chaty, jako složky chatů.
    entry<StickerSetsRoute>(
        metadata = Panes.extra(SETTINGS_SCENE) + Panes.contentWidth(ContentWidth.Reading),
    ) { route ->
        StickerSetsScreen(
            kind = StickerSetKind.Stickers,
            viewModel = koinViewModel(key = "sticker-sets") { parametersOf(StickerSetKind.Stickers) },
            onBack = { settingsStack.pop(route) },
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
        val close: () -> Unit = { settingsStack.pop(route) }
        EditProfileScreen(
            viewModel = koinViewModel(),
            onBack = close.takeUnless { LocalPanesSideBySide.current },
            // Prohlížeč se vrší nad Nastavení; zavírá se z toho zásobníku, ve kterém leží.
            onOpenPhotos = { userId -> settingsStack.add(MediaRoute(userId, messageId = 0L, profilePhotos = true)) },
            onChangePhone = { if (settingsStack.lastOrNull() != ChangePhoneRoute) settingsStack.add(ChangePhoneRoute) },
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
private fun List<NavKey>.isOpenConversation(chatId: Long): Boolean =
    (lastOrNull() as? ConversationRoute)?.chatId == chatId

/**
 * Rychlý přepínač v konverzaci: chat jako po ťuknutí v seznamu — i s Uloženými zprávami po
 * chatech, když si je tak uživatel zvolil.
 */
@Composable
private fun rememberSwitchChat(navigation: AppNavigation): (ChatSummary) -> Unit {
    val settings = koinInject<AppSettings>()
    val savedMessagesChatId = LocalSavedMessagesChatId.current
    return remember(navigation, settings, savedMessagesChatId) {
        { chat ->
            navigation.switchChat(
                chatId = chat.id,
                forum = chat.isForum,
                savedTopics = chat.id == savedMessagesChatId && settings.savedMessagesAsChats.value,
            )
        }
    }
}

/**
 * Otevření chatu z hlavního seznamu NAHRADÍ otevřenou konverzaci, nevrší se zásobník — stejně
 * jako sekce v Nastavení.
 */
private fun MutableList<NavKey>.openConversation(chatId: Long, messageId: Long? = null) {
    removeAll { it is ConversationRoute || it is ForumTopicsRoute || it is SavedTopicsRoute }
    add(ConversationRoute(chatId, messageId))
}

/** Skupina s tématy: místo konverzace seznam témat, jinak totéž co [openConversation]. */
private fun MutableList<NavKey>.openForum(chatId: Long) {
    removeAll { it is ConversationRoute || it is ForumTopicsRoute || it is SavedTopicsRoute }
    add(ForumTopicsRoute(chatId))
}

/** Uložené zprávy po chatech: místo konverzace seznam témat, jinak totéž co [openConversation]. */
private fun MutableList<NavKey>.openSavedTopics(chatId: Long) {
    removeAll { it is ConversationRoute || it is ForumTopicsRoute || it is SavedTopicsRoute }
    add(SavedTopicsRoute(chatId))
}

/** Totéž co [isOpenConversation], ale pro větev archivu. */
private fun List<NavKey>.isOpenArchiveConversation(chatId: Long): Boolean =
    (lastOrNull() as? ArchiveConversationRoute)?.chatId == chatId

/**
 * Totéž co [openConversation], ale pro větev archivu: nahrazuje se jen archivní konverzace,
 * takže chat otevřený z hlavního seznamu zůstane, kde byl.
 */
private fun MutableList<NavKey>.openArchiveConversation(chatId: Long, messageId: Long? = null) {
    removeAll { it is ArchiveConversationRoute || it is ArchiveForumTopicsRoute }
    add(ArchiveConversationRoute(chatId, messageId))
}

/** Totéž co [openForum], ale pro větev archivu. */
private fun MutableList<NavKey>.openArchiveForum(chatId: Long) {
    removeAll { it is ArchiveConversationRoute || it is ArchiveForumTopicsRoute }
    add(ArchiveForumTopicsRoute(chatId))
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

private fun botShareRoute(chatId: Long, request: BotShareRequest) = BotShareRoute(
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

internal fun webAppRoute(chatId: Long, request: WebAppRequest, archive: Boolean) = WebAppRoute(
    chatId = chatId,
    botUserId = request.botUserId,
    url = request.url,
    launch = request.launch,
    keyboardButtonText = request.keyboardButtonText,
    archive = archive,
    main = request.main,
    startParameter = request.startParameter,
    shortName = request.shortName,
)

private fun SwitchInlinePick.toRoute() = SwitchInlineRoute(
    bot = bot,
    query = query,
    users = target.users,
    bots = target.bots,
    groups = target.groups,
    channels = target.channels,
)

private fun pollComposerRoute(chatId: Long, request: PollComposerRequest) = PollComposerRoute(
    chatId = chatId,
    quizOnly = request.mode == PollComposerMode.QuizOnly,
    regularOnly = request.mode == PollComposerMode.RegularOnly,
    replyToMessageId = request.replyToMessageId,
    quoteText = request.quote?.text,
    quotePosition = request.quote?.position ?: 0,
)


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
        this == LoginEmailRoute || this == ChangePhoneRoute || this == PasskeysRoute || this == NetworkUsageRoute ||
        this == AppLockRoute || this == ChatFoldersRoute || this is ChatFolderEditRoute || this == ProxySettingsRoute ||
        this == StickerSetsRoute || this == EmojiSetsRoute ||
        this == AccountPrivacyRoute || this is PrivacySettingRoute || this == DeleteAccountRoute

/** Kontejner obrazovky chatu (konverzace, seznam témat) v barvě chatu (`ChatColorTheme`). */
@Composable
private fun ColoredChatContainer(chatId: Long, fromList: Boolean, content: @Composable () -> Unit) {
    ChatContainer(chatId, fromList) { ChatColorTheme(chatId, content) }
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
