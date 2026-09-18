package eu.tintera.plume.app.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material3.Icon
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import org.jetbrains.compose.resources.painterResource
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
import eu.tintera.plume.core.adaptive.isCompact
import eu.tintera.plume.core.adaptive.singlePane
import eu.tintera.plume.core.adaptive.twoPane
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import eu.tintera.plume.core.navigation.ChatOpenRequests
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.navigation.ForwardRoute
import eu.tintera.plume.core.navigation.MediaRoute
import eu.tintera.plume.core.navigation.SettingsRoute
import eu.tintera.plume.core.navigation.SettingsSection
import eu.tintera.plume.core.navigation.SettingsSectionRoute
import eu.tintera.plume.core.navigation.TopLevelRoute
import eu.tintera.plume.core.ui.LocalTwoPane
import eu.tintera.plume.features.chats.ChatListScreen
import eu.tintera.plume.features.conversation.ConversationPlaceholder
import eu.tintera.plume.features.conversation.ConversationScreen
import eu.tintera.plume.features.forward.ForwardScreen
import eu.tintera.plume.features.media.MediaViewerScreen
import org.koin.core.parameter.parametersOf
import eu.tintera.plume.features.settings.SettingsScreen
import eu.tintera.plume.features.settings.SettingsSectionScreen
import org.koin.compose.viewmodel.koinViewModel
import org.jetbrains.compose.resources.stringResource

/**
 * Kořenový shell přihlášené aplikace: sekční navigace + list-detail pro chaty.
 *
 * **Rozdělení odpovědnosti za insety:** shell vlastní spodní navigaci, takže na compactu
 * zkonzumuje `navigationBars` inset za obsah — žádná obrazovka o spodním baru nemusí vědět.
 * Horní (status bar) inset shell nekonzumuje, protože nahoře žádný chrome nekreslí; řeší ho
 * každá obrazovka svým top barem.
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

    val currentTopLevel = navigation.section
    val compact = isCompact()

    /*
     * V konverzaci se spodní bar na telefonu skrývá.
     *
     * Na compactu zabírá konverzace celou obrazovku, takže sekční navigace nedává smysl — ven
     * se odtud chodí zpět, ne přepnutím sekce. Na širokém rozvržení je konverzace jen panel
     * vedle seznamu, tam navigace smysl dává a rail zůstává.
     */
    val inDetail = navigation.currentStack.any { it is ConversationRoute || it is MediaRoute }
    val showBottomBar = compact && !inDetail

    // Obsah drží stabilní instanci přes přepnutí rail↔bar. Bez movableContent by se při změně
    // breakpointu celý podstrom odmountoval a namountoval znovu — ztratil by se scroll, stav
    // a rozjely by se animace.
    val content = remember {
        movableContentOf {
            ShellContent(
                navigation = navigation,
                entryDecorators = entryDecorators,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    Row(modifier.fillMaxSize()) {
        if (!compact) {
            /*
             * Insety jsou výchozí (stavová a navigační lišta, výřez na straně). Dřív tu byly
             * nulové a první položka se lepila pod stavovou lištu.
             *
             * Položky jsou na STŘEDU: M3 u tabletů doporučuje střed, protože se na něj líp
             * dosáhne palcem. Nahoru patří jen menu a FAB, a ty rail nemá.
             */
            WideNavigationRail(arrangement = Arrangement.Center) {
                TopLevelDestination.entries.forEach { dest ->
                    val selected = currentTopLevel == dest.route
                    WideNavigationRailItem(
                        selected = selected,
                        onClick = { navigation.selectSection(dest.route) },
                        icon = { DestinationIcon(dest, selected) },
                        label = { Text(stringResource(dest.label)) },
                        railExpanded = false,
                    )
                }
            }
        }

        Column(Modifier.weight(1f).fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    // Inset spodní navigace se konzumuje JEN když se bar opravdu kreslí.
                    // Kdyby se konzumoval vždycky, vstupní pole konverzace by si na skrytém
                    // baru myslelo, že inset už někdo vyřešil, a zalezlo by pod systémovou lištu.
                    .then(
                        if (showBottomBar) Modifier.consumeWindowInsets(WindowInsets.navigationBars)
                        else Modifier,
                    ),
            ) {
                content()
            }

            AnimatedVisibility(
                visible = showBottomBar,
                /*
                 * Bar odjíždí dolů, odkud přišel — skokové zmizení by při otevření konverzace
                 * trhlo celým obsahem.
                 *
                 * `shrinkVertically` je tam nutně navíc: samotný `slideOutVertically` obsah jen
                 * POSUNE, ale jeho místo v layoutu drží po celou animaci. Vstupní pole konverzace
                 * proto celou dobu viselo nad prázdným pruhem a doskočilo dolů až na konci.
                 * Se `shrinkVertically` se zmenšuje i to místo, takže obsah roste současně
                 * s odjezdem baru.
                 */
                enter = slideInVertically { it } + expandVertically(expandFrom = Alignment.Top) +
                    fadeIn(),
                exit = slideOutVertically { it } + shrinkVertically(shrinkTowards = Alignment.Top) +
                    fadeOut(),
            ) {
                ShortNavigationBar {
                    TopLevelDestination.entries.forEach { dest ->
                        val selected = currentTopLevel == dest.route
                        ShortNavigationBarItem(
                            selected = selected,
                            onClick = { navigation.selectSection(dest.route) },
                            icon = { DestinationIcon(dest, selected) },
                            label = { Text(stringResource(dest.label)) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Vlastní navigační plocha.
 *
 * `directive` se počítá ze šířky **content-oblasti** (`BoxWithConstraints`), ne z okna — vlevo
 * ukusuje rail a scaffold by jinak počítal se šířkou, kterou nemá.
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
         * jako nedodělek. A bez sekce by directive dvoupanel nezapnul a seznam sekcí by se
         * roztáhl přes celou šířku.
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

        val currentDisplayed = if (navigation.section == SettingsRoute) settingsDisplayed else navigation.currentStack
        // Detail má konverzace i otevřená sekce nastavení — obojí je pravý panel téže scény.
        val hasDetail = currentDisplayed.any { it is ConversationRoute || it is SettingsSectionRoute }

        val directive = when {
            // Bez otevřené konverzace nemá smysl rezervovat druhý panel s placeholderem —
            // seznam dostane celou šířku.
            !hasDetail -> base.singlePane()
            wide -> base.twoPane()
            else -> base.singlePane()
        }

        // Dvoupanelový stav se hlásí dolů: obrazovky podle něj poznají, že se nevyměňují,
        // a sdílený prvek nezaloží — viz LocalTwoPane.
        CompositionLocalProvider(LocalTwoPane provides wide) {
            val entryProvider = shellEntryProvider(
                chatsStack = chatsStack,
                settingsStack = settingsStack,
                selectedSettingsSection = settingsDisplayed.filterIsInstance<SettingsSectionRoute>()
                    .lastOrNull()?.section,
                wide = wide,
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
                onBack = navigation::goBack,
                sceneStrategies = listOf(
                    // `shouldHandleSinglePaneLayout = wide`: na široké ploše nechá i jednopanelový stav
                    // řešit scaffold, takže „jen seznam" a „seznam + konverzace" jsou TÁŽ scéna a přechod
                    // mezi nimi je plynulý. Na compactu vrací null → standardní swap obrazovek.
                    rememberListDetailSceneStrategy(
                        shouldHandleSinglePaneLayout = wide,
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
            onChatClick = { chatId ->
                // Otevření jiného chatu nahradí detail, nevrší se zásobník.
                chatsStack.removeAll { it is ConversationRoute }
                chatsStack.add(ConversationRoute(chatId))
            },
        )
    }

    entry<ConversationRoute>(metadata = ListDetailSceneStrategy.detailPane()) { route ->
        ConversationScreen(
            // Klíč MUSÍ obsahovat chatId. ViewModel se cachuje podle klíče, takže
            // konstantní klíč znamená, že druhá otevřená konverzace dostane ViewModel
            // té první — i se zprávami. Koin navíc u cachované instance ignoruje
            // nové `parameters`, takže se to neprojeví ani chybou, jen špatným obsahem.
            viewModel = koinViewModel(
                key = "conversation-${route.chatId}",
                parameters = { parametersOf(route.chatId) },
            ),
            onBack = { chatsStack.removeLastOrNull() },
            onOpenMedia = { messageId ->
                chatsStack.add(MediaRoute(route.chatId, messageId))
            },
            onForward = { messageIds ->
                chatsStack.add(ForwardRoute(route.chatId, messageIds))
            },
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
            onBack = { chatsStack.removeLastOrNull() },
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
            onBack = { chatsStack.removeLastOrNull() },
            onForwarded = { chatId ->
                chatsStack.removeLastOrNull()
                if (chatsStack.lastOrNull() != ConversationRoute(chatId)) {
                    chatsStack.add(ConversationRoute(chatId))
                }
            },
        )
    }

    entry<SettingsRoute>(metadata = ListDetailSceneStrategy.listPane(SETTINGS_SCENE)) {
        SettingsScreen(
            selectedSection = selectedSettingsSection,
            // Otevření jiné sekce ji NAHRADÍ, nevrší se zásobník — stejně jako
            // u konverzací.
            onSectionClick = { section ->
                settingsStack.removeAll { it is SettingsSectionRoute }
                settingsStack.add(SettingsSectionRoute(section))
            },
        )
    }

    entry<SettingsSectionRoute>(
        metadata = ListDetailSceneStrategy.detailPane(SETTINGS_SCENE),
    ) { route ->
        // V dvoupanelu je seznam sekcí pořád vidět vedle, takže šipka zpět nemá
        // kam vést; na telefonu je sekce samostatná obrazovka a zpět se z ní chodí.
        val popSection: () -> Unit = { settingsStack.removeLastOrNull() }

        SettingsSectionScreen(
            section = route.section,
            onBack = if (wide) null else popSection,
        )
    }
}

/**
 * Klíč scény list-detail pro nastavení.
 *
 * Chaty i nastavení jsou dvě různé list-detail dvojice v jednom `NavDisplay`. Bez vlastního klíče
 * by obě skládaly scénu se stejnou identitou (výchozí `Unit`) a přepnutí sekce by pro NavDisplay
 * nebyla výměna scény, ale změna obsahu té stávající.
 */
private const val SETTINGS_SCENE = "settings"

/** Vyplněná varianta při výběru — M3 konvence. Crossfade, ať přepnutí neproblikne. */
@Composable
private fun DestinationIcon(destination: TopLevelDestination, selected: Boolean) {
    Crossfade(selected, label = "nav-icon") { isSelected ->
        Icon(
            painter = painterResource(
                if (isSelected) destination.selectedIcon else destination.icon,
            ),
            contentDescription = null,
        )
    }
}
