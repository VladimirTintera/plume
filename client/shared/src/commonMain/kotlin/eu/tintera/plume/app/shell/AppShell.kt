package eu.tintera.plume.app.shell

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
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.animation.Crossfade
import org.jetbrains.compose.resources.painterResource
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import eu.tintera.plume.core.adaptive.TWO_PANE_MIN_WIDTH
import eu.tintera.plume.core.adaptive.contentPaneScaffoldDirective
import eu.tintera.plume.core.adaptive.isCompact
import eu.tintera.plume.core.adaptive.singlePane
import eu.tintera.plume.core.adaptive.twoPane
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.NavSavedStateConfiguration
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.navigation.SettingsRoute
import eu.tintera.plume.core.navigation.TopLevelRoute
import eu.tintera.plume.features.chats.ChatListScreen
import eu.tintera.plume.features.conversation.ConversationPlaceholder
import eu.tintera.plume.features.conversation.ConversationScreen
import org.koin.core.parameter.parametersOf
import eu.tintera.plume.features.settings.SettingsScreen
import org.koin.compose.viewmodel.koinViewModel

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
    // Dvě věci najednou: overload bez konfigurace existuje jen na Androidu (v commonMain se musí
    // předat explicitně), a konfigurace MUSÍ mít zaregistrované podtypy NavKey — viz
    // NavSavedStateConfiguration. S DEFAULT to spadne při první kompozici.
    val backStack = rememberNavBackStack(NavSavedStateConfiguration, ChatsRoute)

    val selectTopLevel: (TopLevelRoute) -> Unit = { route ->
        // Zjednodušení fáze 1: jeden zásobník pro všechny sekce, přepnutí ho srovná na kořen.
        // Sekce si tím nedrží vlastní historii. Pro Chaty/Nastavení to nevadí; až přibude sekce
        // s hlubší navigací, patří sem multi-stack (vzor: MultiStackNavigationState v mlogu).
        if (backStack.lastOrNull() != route || backStack.size > 1) {
            backStack.clear()
            backStack.add(route)
        }
    }

    val currentTopLevel = backStack.firstOrNull() as? TopLevelRoute ?: ChatsRoute
    val compact = isCompact()

    // Obsah drží stabilní instanci přes přepnutí rail↔bar. Bez movableContent by se při změně
    // breakpointu celý podstrom odmountoval a namountoval znovu — ztratil by se scroll, stav
    // a rozjely by se animace.
    val content = remember {
        movableContentOf {
            ShellContent(
                backStack = backStack,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    Row(modifier.fillMaxSize()) {
        if (!compact) {
            WideNavigationRail(windowInsets = WindowInsets()) {
                TopLevelDestination.entries.forEach { dest ->
                    val selected = currentTopLevel == dest.route
                    WideNavigationRailItem(
                        selected = selected,
                        onClick = { selectTopLevel(dest.route) },
                        icon = { DestinationIcon(dest, selected) },
                        label = { Text(dest.label) },
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
                    .then(
                        if (compact) Modifier.consumeWindowInsets(WindowInsets.navigationBars)
                        else Modifier,
                    ),
            ) {
                content()
            }

            if (compact) {
                ShortNavigationBar {
                    TopLevelDestination.entries.forEach { dest ->
                        val selected = currentTopLevel == dest.route
                        ShortNavigationBarItem(
                            selected = selected,
                            onClick = { selectTopLevel(dest.route) },
                            icon = { DestinationIcon(dest, selected) },
                            label = { Text(dest.label) },
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
    backStack: MutableList<NavKey>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val base = contentPaneScaffoldDirective(maxWidth, maxHeight)
        val hasDetail = backStack.any { it is ConversationRoute }
        val wide = maxWidth >= TWO_PANE_MIN_WIDTH

        val directive = when {
            // Bez otevřené konverzace nemá smysl rezervovat druhý panel s placeholderem —
            // seznam dostane celou šířku.
            !hasDetail -> base.singlePane()
            wide -> base.twoPane()
            else -> base.singlePane()
        }

        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            sceneStrategies = listOf(
                // `shouldHandleSinglePaneLayout = wide`: na široké ploše nechá i jednopanelový stav
                // řešit scaffold, takže „jen seznam" a „seznam + konverzace" jsou TÁŽ scéna a přechod
                // mezi nimi je plynulý. Na compactu vrací null → standardní swap obrazovek.
                rememberListDetailSceneStrategy(
                    shouldHandleSinglePaneLayout = wide,
                    directive = directive,
                ),
            ),
            entryProvider = entryProvider {
                entry<ChatsRoute>(
                    metadata = ListDetailSceneStrategy.listPane(
                        detailPlaceholder = { ConversationPlaceholder() },
                    ),
                ) {
                    ChatListScreen(
                        viewModel = koinViewModel(),
                        selectedChatId = backStack.filterIsInstance<ConversationRoute>()
                            .lastOrNull()?.chatId,
                        onChatClick = { chatId ->
                            // Otevření jiného chatu nahradí detail, nevrší se zásobník.
                            backStack.removeAll { it is ConversationRoute }
                            backStack.add(ConversationRoute(chatId))
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
                        onBack = { backStack.removeLastOrNull() },
                    )
                }

                entry<SettingsRoute> {
                    SettingsScreen(viewModel = koinViewModel())
                }
            },
        )
    }
}

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
