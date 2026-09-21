package eu.tintera.plume.app.shell

import eu.tintera.plume.features.settings.EditProfileScreen
import eu.tintera.plume.core.navigation.EditProfileRoute
import eu.tintera.plume.features.conversation.ConversationSearchRequests
import eu.tintera.plume.features.profile.ChatProfileScreen
import eu.tintera.plume.core.navigation.ChatProfileRoute
import eu.tintera.plume.core.ui.LocalSavedMessagesChatId
import eu.tintera.plume.core.telegram.ChatRepository
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
import androidx.compose.ui.Modifier
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
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
import eu.tintera.plume.core.navigation.ForwardRoute
import eu.tintera.plume.core.navigation.ShareRoute
import eu.tintera.plume.core.share.ShareRequests
import eu.tintera.plume.core.share.rememberReturnToCaller
import eu.tintera.plume.core.navigation.MediaRoute
import eu.tintera.plume.core.navigation.NewChatRoute
import eu.tintera.plume.core.navigation.SettingsRoute
import eu.tintera.plume.core.navigation.SettingsSection
import eu.tintera.plume.core.navigation.SettingsSectionRoute
import eu.tintera.plume.core.navigation.TopLevelRoute
import eu.tintera.plume.core.ui.LocalTwoPane
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.features.chats.ArchiveScreen
import eu.tintera.plume.features.chats.ChatListScreen
import eu.tintera.plume.features.chats.NewChatScreen
import eu.tintera.plume.features.conversation.ConversationPlaceholder
import eu.tintera.plume.features.conversation.ConversationScreen
import eu.tintera.plume.features.forward.ForwardScreen
import eu.tintera.plume.features.share.ShareScreen
import eu.tintera.plume.features.media.MediaViewerScreen
import org.koin.core.parameter.parametersOf
import eu.tintera.plume.features.settings.SettingsScreen
import eu.tintera.plume.features.settings.SettingsSectionScreen
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
    val requestedChatId by chatOpenRequests.chatId.collectAsStateWithLifecycle()

    LaunchedEffect(requestedChatId) {
        val chatId = requestedChatId ?: return@LaunchedEffect
        navigation.openChat(chatId)
        chatOpenRequests.consume(chatId)
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

    ShellContent(
        navigation = navigation,
        entryDecorators = entryDecorators,
        modifier = modifier.fillMaxSize(),
    )
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
        val directive = if (twoPane) base.twoPane() else base.singlePane()

        // Dvoupanelový stav se hlásí dolů: obrazovky podle něj poznají, že se nevyměňují,
        // a sdílený prvek nezaloží — viz LocalTwoPane.
        // Id Uložených zpráv pro všechno, co kreslí chat (název, záložka místo fotky).
        val savedMessagesChatId by chatRepository.savedMessagesChatId.collectAsStateWithLifecycle()

        CompositionLocalProvider(LocalTwoPane provides wide, LocalSavedMessagesChatId provides savedMessagesChatId) {
            /*
             * Zavření výběru pro sdílení vrací do aplikace, ze které se sdílelo, ne do Plume.
             * Návrat do seznamu chatů by vypadal, že se sdílený obsah ztratil — a ztratil by se,
             * protože bez volby chatu nemá kam jít. Výběr se napřed odebere, aby příští
             * otevření Plume začalo tam, kde bylo před sdílením.
             */
            val returnToCaller = rememberReturnToCaller()
            val closeShare = {
                chatsStack.remove(ShareRoute)
                returnToCaller()
            }
            val entryProvider = shellEntryProvider(
                chatsStack = chatsStack,
                settingsStack = settingsStack,
                selectedSettingsSection = settingsDisplayed.filterIsInstance<SettingsSectionRoute>()
                    .lastOrNull()?.section,
                wide = wide,
                twoPane = twoPane,
                closeShare = closeShare,
                navigation = navigation,
            )
            /*
             * Položky se dekorují pro KAŽDOU sekci, i tu, která není vidět. Zásobník, který se
             * zrovna nedekoruje, by pro Nav3 vypadal jako odebraný a jeho ViewModely by se zrušily.
             */
            val entriesBySection = mapOf<TopLevelRoute, List<NavEntry<NavKey>>>(
                ChatsRoute to rememberDecoratedNavEntries(chatsStack, entryDecorators, entryProvider),
                SettingsRoute to rememberDecoratedNavEntries(settingsDisplayed, entryDecorators, entryProvider),
            )

            NavDisplay(
                entries = navigation.visibleSections.flatMap { entriesBySection.getValue(it) },
                // Zpět z výběru pro sdílení je totéž co ✕ — vrátit uživatele, odkud sdílel.
                onBack = { if (chatsStack.lastOrNull() == ShareRoute) closeShare() else navigation.goBack() },
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
                         * Výjimka je úprava profilu nad sekcí: dvoupanel zůstává dvoupanelem,
                         * takže by zpět přeskočilo i sekci pod ní a odešlo z Nastavení.
                         */
                        backNavigationBehavior = if (navigation.section == ChatsRoute || settingsStack.lastOrNull() == EditProfileRoute) {
                            BackNavigationBehavior.PopUntilContentChange
                        } else {
                            BackNavigationBehavior.PopUntilScaffoldValueChange
                        },
                        directive = directive,
                    ),
                ),
            )
        }
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
    closeShare: () -> Unit,
    navigation: AppNavigation,
): (NavKey) -> NavEntry<NavKey> = entryProvider {

    entry<ChatsRoute>(
        metadata = ListDetailSceneStrategy.listPane(
            detailPlaceholder = { ConversationPlaceholder() },
        ),
    ) {
        ChatListScreen(
            viewModel = koinViewModel(),
            selectedChatId = chatsStack.filterIsInstance<ConversationRoute>()
                .lastOrNull()?.chatId,
            onChatClick = { chatId -> chatsStack.openConversation(chatId) },
            onMessageClick = { chatId, messageId -> chatsStack.openConversation(chatId, messageId) },
            searchRequest = navigation.searchRequest,
            onSearchRequestHandled = navigation::consumeSearchRequest,
            onOpenArchive = {
                if (chatsStack.lastOrNull() != ArchiveRoute) chatsStack.add(ArchiveRoute)
            },
            onNewChat = {
                if (chatsStack.lastOrNull() != NewChatRoute) chatsStack.add(NewChatRoute)
            },
            onChatArchived = chatsStack::closeConversationOf,
            onOpenSettings = navigation::openSettings,
            modifier = Modifier.listPaneInsets(twoPane),
        )
    }

    /*
     * Archiv je samostatná list-detail scéna (ARCHIVE_SCENE). V dvoupanelu má vpravo vlastní
     * detail panel [ArchiveConversationRoute] (nebo placeholder), takže nezasahuje do otevřené
     * konverzace hlavního seznamu; zpět odebere celou archivní větev a vrátí hlavní seznam.
     */
    entry<ArchiveRoute>(
        metadata = ListDetailSceneStrategy.listPane(
            ARCHIVE_SCENE,
            detailPlaceholder = { ConversationPlaceholder() },
        ),
    ) {
        ArchiveScreen(
            // Klíč se složkou: ViewModel se cachuje podle klíče a Koin by u cachované instance
            // parametr ignoroval (viz konverzace).
            viewModel = koinViewModel(
                key = "chats-archive",
                parameters = { parametersOf(ChatFolder.Archive) },
            ),
            selectedChatId = chatsStack.filterIsInstance<ArchiveConversationRoute>()
                .lastOrNull()?.chatId,
            onChatClick = { chatId -> chatsStack.openArchiveConversation(chatId) },
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
        metadata = ListDetailSceneStrategy.listPane(
            detailPlaceholder = { ConversationPlaceholder() },
        ),
    ) {
        NewChatScreen(
            viewModel = koinViewModel(),
            onBack = { chatsStack.remove(NewChatRoute) },
            onChatSelected = { chatId ->
                chatsStack.remove(NewChatRoute)
                chatsStack.openConversation(chatId)
            },
            modifier = Modifier.listPaneInsets(twoPane),
        )
    }

    entry<ConversationRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
        ConversationScreen(
            // Klíč MUSÍ obsahovat chatId. ViewModel se cachuje podle klíče, takže
            // konstantní klíč znamená, že druhá otevřená konverzace dostane ViewModel
            // té první — i se zprávami. Koin navíc u cachované instance ignoruje
            // nové `parameters`, takže se to neprojeví ani chybou, jen špatným obsahem.
            viewModel = koinViewModel(
                key = "conversation-${route.chatId}-${route.messageId}",
                parameters = { parametersOf(route.chatId, route.messageId) },
            ),
            // Odebírá SVOU položku, ne poslední — viz `pop`.
            onBack = { chatsStack.pop(route) },
            onOpenMedia = { messageId ->
                chatsStack.add(MediaRoute(route.chatId, messageId))
            },
            onForward = { messageIds ->
                chatsStack.add(ForwardRoute(route.chatId, messageIds))
            },
            // Zmínka i odkaz otevře chat NAD touhle konverzací (ne místo ní), takže zpět vede sem.
            // Odkaz na zprávu nese její id a konverzace na ni po otevření skočí.
            onOpenChat = { chatId, messageId ->
                if (!chatsStack.isOpenConversation(chatId)) chatsStack.add(ConversationRoute(chatId, messageId))
            },
            onSearch = { query -> navigation.openSearch(query, keepConversation = twoPane) },
            onOpenProfile = { chatsStack.add(ChatProfileRoute(route.chatId)) },
            onOpenUserProfile = { chatId -> chatsStack.add(ChatProfileRoute(chatId)) },
        )
    }

    // Profil je detail jako konverzace: na širokém rozvržení ji v pravém panelu nahradí.
    entry<ChatProfileRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
        val searchRequests = koinInject<ConversationSearchRequests>()
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
            onOpenMedia = { messageId -> chatsStack.add(MediaRoute(route.chatId, messageId)) },
            onChatGone = { chatsStack.closeChat(route.chatId) },
        )
    }

    entry<ArchiveConversationRoute>(metadata = ListDetailSceneStrategy.detailPane(ARCHIVE_SCENE)) { route ->
        ConversationScreen(
            viewModel = koinViewModel(
                key = "conversation-${route.chatId}-${route.messageId}",
                parameters = { parametersOf(route.chatId, route.messageId) },
            ),
            onBack = { chatsStack.pop(route) },
            onOpenMedia = { messageId ->
                chatsStack.add(MediaRoute(route.chatId, messageId))
            },
            onForward = { messageIds ->
                chatsStack.add(ForwardRoute(route.chatId, messageIds))
            },
            onOpenChat = { chatId, messageId ->
                if (!chatsStack.isOpenArchiveConversation(chatId)) {
                    chatsStack.add(ArchiveConversationRoute(chatId, messageId))
                }
            },
            onSearch = { query -> navigation.openSearch(query, keepConversation = false) },
            onOpenProfile = { chatsStack.add(ArchiveChatProfileRoute(route.chatId)) },
            onOpenUserProfile = { chatId -> chatsStack.add(ArchiveChatProfileRoute(chatId)) },
        )
    }

    entry<ArchiveChatProfileRoute>(metadata = ListDetailSceneStrategy.detailPane(ARCHIVE_SCENE)) { route ->
        val searchRequests = koinInject<ConversationSearchRequests>()
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
            onOpenMedia = { messageId -> chatsStack.add(MediaRoute(route.chatId, messageId)) },
            onChatGone = { chatsStack.closeChat(route.chatId) },
        )
    }

    /*
     * Prohlížeč nemá metadata list-detail scény, takže ho scéna nepřevezme
     * a `NavDisplay` ho vykreslí přes celou plochu — i na širokém rozvržení,
     * kde by panel vedle seznamu chatů neměl co dělat.
     */
    entry<MediaRoute> { route ->
        MediaViewerScreen(
            // Klíč musí rozlišovat zprávu ze stejného důvodu jako u konverzace:
            // ViewModel se cachuje podle klíče a Koin u cachované instance
            // ignoruje nové parametry.
            viewModel = koinViewModel(
                key = "media-${route.chatId}-${route.messageId}",
                parameters = { parametersOf(route.chatId, route.messageId) },
            ),
            onBack = { chatsStack.pop(route) },
            onForward = { messageId ->
                chatsStack.add(ForwardRoute(route.chatId, listOf(messageId)))
            },
        )
    }

    /*
     * Výběr cílového chatu. Po přeposlání se výběr ze zásobníku zahodí a otevře
     * se cílový chat — uživatel tak skončí tam, kam zprávu poslal, a zpět ho
     * vrátí do původní konverzace.
     */
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

    entry<SettingsRoute>(metadata = ListDetailSceneStrategy.listPane(SETTINGS_SCENE)) {
        SettingsScreen(
            selectedSection = selectedSettingsSection,
            // Otevření jiné sekce ji NAHRADÍ, nevrší se zásobník — stejně jako
            // u konverzací.
            onSectionClick = { section ->
                // I s úpravou profilu, která se vrší nad sekci — jinak by zůstala pod novou sekcí.
                settingsStack.removeAll { it is SettingsSectionRoute || it is EditProfileRoute }
                settingsStack.add(SettingsSectionRoute(section))
            },
            // Sem se chodí z menu účtu na každé šířce, takže šipka zpět do Chatů je vždycky.
            // Zavírá Nastavení celá — v dvoupanelu je sekce otevřená pořád, viz `closeSettings`.
            onBack = navigation::closeSettings,
            modifier = Modifier.listPaneInsets(twoPane),
        )
    }

    entry<SettingsSectionRoute>(
        metadata = ListDetailSceneStrategy.detailPane(SETTINGS_SCENE),
    ) { route ->
        // V dvoupanelu je seznam sekcí pořád vidět vedle, takže šipka zpět nemá
        // kam vést; na telefonu je sekce samostatná obrazovka a zpět se z ní chodí.
        val popSection: () -> Unit = { settingsStack.pop(route) }

        SettingsSectionScreen(
            section = route.section,
            onBack = if (wide) null else popSection,
            onEditProfile = { if (settingsStack.lastOrNull() != EditProfileRoute) settingsStack.add(EditProfileRoute) },
        )
    }

    // Úprava profilu nad sekcí Obecné; šipka zpět je i v dvoupanelu, vede zpátky do sekce.
    entry<EditProfileRoute>(
        metadata = ListDetailSceneStrategy.detailPane(SETTINGS_SCENE),
    ) { route ->
        EditProfileScreen(
            viewModel = koinViewModel(),
            onBack = { settingsStack.pop(route) },
        )
    }
}

/**
 * Je na vrcholu zásobníku konverzace [chatId]? Porovnává se jen chat — routa může nést i zprávu
 * ze hledání, a rovnost celé routy by pak přidala tutéž konverzaci podruhé.
 */
private fun List<NavKey>.isOpenConversation(chatId: Long): Boolean =
    (lastOrNull() as? ConversationRoute)?.chatId == chatId

/**
 * Otevření chatu z hlavního seznamu NAHRADÍ otevřenou konverzaci, nevrší se zásobník — stejně
 * jako sekce v Nastavení.
 */
private fun MutableList<NavKey>.openConversation(chatId: Long, messageId: Long? = null) {
    removeAll { it is ConversationRoute }
    add(ConversationRoute(chatId, messageId))
}

/** Totéž co [isOpenConversation], ale pro větev archivu. */
private fun List<NavKey>.isOpenArchiveConversation(chatId: Long): Boolean =
    (lastOrNull() as? ArchiveConversationRoute)?.chatId == chatId

/**
 * Totéž co [openConversation], ale pro větev archivu: nahrazuje se jen archivní konverzace,
 * takže chat otevřený z hlavního seznamu zůstane, kde byl.
 */
private fun MutableList<NavKey>.openArchiveConversation(chatId: Long, messageId: Long? = null) {
    removeAll { it is ArchiveConversationRoute }
    add(ArchiveConversationRoute(chatId, messageId))
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
