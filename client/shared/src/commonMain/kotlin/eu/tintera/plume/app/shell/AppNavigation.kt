package eu.tintera.plume.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.navigation.NavSavedStateConfiguration
import eu.tintera.plume.core.navigation.SettingsRoute
import eu.tintera.plume.core.navigation.SettingsSection
import eu.tintera.plume.core.navigation.SettingsSectionRoute
import eu.tintera.plume.core.navigation.TopLevelRoute

/**
 * Navigační stav přihlášené aplikace: **vlastní zásobník pro každou sekci** a vybraná sekce.
 *
 * Vzniká jednou, nahoře v [AppShell], a dolů se podává. Přepnutí sekce zásobníky nemaže — jen
 * vybere jiný, takže návrat do Chatů vrátí i otevřenou konverzaci. Dokud byl zásobník jeden pro
 * celou aplikaci, přepnutí ho srovnalo na kořen a sekce si nepamatovaly nic.
 *
 * **Vidět je zásobník výchozí sekce a nad ním zásobník vybrané** ([visibleSections]). Zpět
 * z kořene Nastavení je tak obyčejný pop do Chatů, i s predictive back — žádný vlastní
 * handler. Vzor: „concatenated back stack" z Navigation3.
 *
 * Oba zásobníky přežijí zabití procesu (`rememberNavBackStack`), vybraná sekce taky
 * (`rememberSaveable` podle názvu [TopLevelDestination]).
 */
@Stable
class AppNavigation internal constructor(
    private val selected: MutableState<TopLevelRoute>,
    private val stacks: Map<TopLevelRoute, NavBackStack<NavKey>>,
) {
    /** Vybraná sekce. */
    val section: TopLevelRoute get() = selected.value

    /** Zásobník konkrétní sekce. Obrazovka navigující „u sebe" ho má brát podle sekce, ne [section]. */
    fun stack(section: TopLevelRoute): NavBackStack<NavKey> = stacks.getValue(section)

    /** Zásobník vybrané sekce. */
    val currentStack: NavBackStack<NavKey> get() = stack(section)

    /** Sekce, jejichž zásobníky se kreslí: výchozí vespod, nad ní vybraná. */
    val visibleSections: List<TopLevelRoute>
        get() = if (section == START_SECTION) listOf(START_SECTION) else listOf(START_SECTION, section)

    /**
     * Ťuknutí na sekci v railu / baru. Jiná sekce se jen vybere se vším, co v ní bylo otevřené;
     * ťuknutí na tu, ve které uživatel už je, ji vrátí na kořen (M3 konvence).
     */
    fun selectSection(target: TopLevelRoute) {
        if (target == section) popToRoot(target) else selected.value = target
    }

    /**
     * Zpět o JEDNU položku: nejdřív v zásobníku vybrané sekce, z jejího kořene do výchozí.
     * Z kořene výchozí sekce se nevolá — `NavDisplay` tam zpět nechá systému (odchod z aplikace).
     *
     * `NavDisplay` tohle volá jednou za každou položku, kterou gesto odebírá: u dvoupanelové
     * scény (seznam + detail) dvakrát. Každé volání proto musí odpovídat právě jedné položce
     * v tom, co se kreslí — viz [ensureSettingsSection].
     */
    fun goBack() {
        val stack = currentStack
        if (stack.size > 1) stack.removeAt(stack.lastIndex) else if (section != START_SECTION) selected.value = START_SECTION
    }

    /**
     * Otevření chatu zvenčí (notifikace). Zásobník Chatů se srovná na seznam s tou konverzací:
     * zpět z ní musí vést na seznam, ne do toho, co bylo otevřené předtím.
     */
    fun openChat(chatId: Long) {
        selected.value = ChatsRoute
        popToRoot(ChatsRoute)
        stack(ChatsRoute).add(ConversationRoute(chatId))
    }

    /**
     * V dvoupanelu je v Nastavení vždycky otevřená nějaká sekce, a musí být i V ZÁSOBNÍKU, ne
     * jen dopočítaná pro kreslení. Zpět ze dvoupanelové scény odebírá obě položky naráz (dvě
     * volání [goBack]); s dopočítanou sekcí by první volání odešlo do Chatů a druhé tam
     * zavřelo otevřenou konverzaci.
     */
    fun ensureSettingsSection() {
        val stack = stack(SettingsRoute)
        if (stack.size == 1) stack.add(SettingsSectionRoute(SettingsSection.entries.first()))
    }

    private fun popToRoot(section: TopLevelRoute) {
        val stack = stack(section)
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}

/** Sekce, ve které aplikace začíná a přes kterou se z ní odchází. */
private val START_SECTION: TopLevelRoute = ChatsRoute

@Composable
fun rememberAppNavigation(): AppNavigation {
    // Overload bez konfigurace existuje jen na Androidu; konfigurace navíc MUSÍ znát podtypy
    // NavKey — viz NavSavedStateConfiguration. `key` drží každý zásobník na jeho sekci.
    val stacks = TopLevelDestination.entries.associate { destination ->
        destination.route to key(destination) {
            rememberNavBackStack(NavSavedStateConfiguration, destination.route)
        }
    }
    val selected = rememberSaveable(saver = SelectedSectionSaver) { mutableStateOf(START_SECTION) }
    return remember(selected) { AppNavigation(selected, stacks) }
}

/** Ukládá se název sekce — přidání nové doprostřed nezmění význam uloženého stavu. */
private val SelectedSectionSaver = Saver<MutableState<TopLevelRoute>, String>(
    save = { state -> TopLevelDestination.entries.first { it.route == state.value }.name },
    restore = { name ->
        val route = TopLevelDestination.entries.firstOrNull { it.name == name }?.route ?: START_SECTION
        mutableStateOf(route)
    },
)
