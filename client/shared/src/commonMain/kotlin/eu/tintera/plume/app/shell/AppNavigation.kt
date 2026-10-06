package eu.tintera.plume.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import eu.tintera.plume.core.navigation.ChatFolderEditRoute
import eu.tintera.plume.core.navigation.ChatFoldersRoute
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.navigation.ForumTopicsRoute
import eu.tintera.plume.core.navigation.NavSavedStateConfiguration
import eu.tintera.plume.core.navigation.ProxySettingsRoute
import eu.tintera.plume.core.navigation.SettingsRoute
import eu.tintera.plume.core.navigation.SettingsSection
import eu.tintera.plume.core.navigation.SettingsSectionRoute
import eu.tintera.plume.core.navigation.ShareRoute
import eu.tintera.plume.core.navigation.ShareToChatRoute
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
     * Nastavení z menu účtu v seznamu chatů — jediný vstup, rail ani lišta nejsou. Proto
     * vždycky na kořen: menu slibuje „Nastavení", ne sekci, kterou tam kdysi uživatel nechal
     * otevřenou. Zpět pak vede do Chatů.
     */
    fun openSettings() {
        popToRoot(SettingsRoute)
        selected.value = SettingsRoute
    }

    /**
     * Nastavení proxy zvenčí (nabídka nad chatem od provozovatele proxy): Nastavení → Obecné →
     * Proxy, takže zpět vede po krocích, kudy by se tam uživatel proklikal sám.
     */
    fun openProxySettings() {
        openSettings()
        val settings = stack(SettingsRoute)
        settings.add(SettingsSectionRoute(SettingsSection.General))
        settings.add(ProxySettingsRoute)
    }

    /**
     * Složky chatů zvenčí („+" v řádku složek nad seznamem chatů): Nastavení → Obecné → Složky
     * chatů, s [create] rovnou i editor nové složky. Zpět vede po krocích, jako u proxy.
     */
    fun openChatFolders(create: Boolean = false) {
        openSettings()
        val settings = stack(SettingsRoute)
        settings.add(SettingsSectionRoute(SettingsSection.General))
        settings.add(ChatFoldersRoute)
        if (create) settings.add(ChatFolderEditRoute(0))
    }

    /**
     * Šipka v hlavičce Nastavení: z Nastavení pryč, do Chatů. Ne [goBack] — ten odebírá jednu
     * položku, a v dvoupanelu je to otevřená sekce, kterou [ensureSettingsSection] hned vrátí;
     * šipka by pak nedělala nic. Zásobník se srovná na kořen, stejně jako ho najde [openSettings].
     */
    fun closeSettings() {
        popToRoot(SettingsRoute)
        selected.value = START_SECTION
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
     *
     * S [topicId] se pod konverzaci tématu vloží seznam témat skupiny, takže zpět z tématu vede
     * do skupiny, jako když se do něj uživatel proklikal sám.
     */
    fun openChat(chatId: Long, topicId: Int? = null) {
        selected.value = ChatsRoute
        popToRoot(ChatsRoute)
        val chats = stack(ChatsRoute)
        if (topicId != null) chats.add(ForumTopicsRoute(chatId))
        chats.add(ConversationRoute(chatId, topicId = topicId))
    }

    /**
     * Sdílení ze systému: výběr chatu nad srovnaným zásobníkem Chatů, stejně jako [openChat].
     * Vybraný chat pak výběr nahradí a zpět z něj vede na seznam.
     */
    fun openShare() {
        selected.value = ChatsRoute
        // Výběr už otevřený zůstává a nový obsah se do něj jen propíše. Odebrat a vrátit by ho
        // zlikvidovalo — a odchod výběru sdílení zahazuje, takže by zahodil právě to nové.
        if (stack(ChatsRoute).lastOrNull() == ShareRoute) return
        popToRoot(ChatsRoute)
        stack(ChatsRoute).add(ShareRoute)
    }

    /**
     * Sdílení do chatu vybraného v systémové nabídce (Direct Share): potvrzovací obrazovka nad
     * srovnaným zásobníkem Chatů, jako [openShare]. Po odeslání ji nahradí ta konverzace.
     */
    fun openShareTo(chatId: Long) {
        selected.value = ChatsRoute
        // Otevřená obrazovka zůstává, ze stejného důvodu jako u výběru: její odchod sdílení
        // zahazuje, takže odebrat a vrátit by zahodilo právě ten nový obsah.
        if (stack(ChatsRoute).lastOrNull() == ShareToChatRoute(chatId)) return
        popToRoot(ChatsRoute)
        stack(ChatsRoute).add(ShareToChatRoute(chatId))
    }

    /**
     * Dotaz, se kterým se má otevřít hledání v seznamu chatů (hashtag ťuknutý v konverzaci).
     * Stav, ne událost: seznam chatů v tu chvíli nemusí být složený (na telefonu ho konverzace
     * překrývá) a vyzvedne si ho, až se složí — viz [consumeSearchRequest].
     */
    var searchRequest: String? by mutableStateOf(null)
        private set

    /**
     * Hledání v seznamu chatů s dotazem. Seznam musí být vidět: na telefonu se zásobník Chatů
     * srovná na kořen, v dvoupanelu zůstane otevřená konverzace ([keepConversation]) — hledání
     * je vlevo vedle ní. Archiv jde pryč v obou případech, hledání má jen hlavní seznam.
     */
    fun openSearch(query: String, keepConversation: Boolean) {
        selected.value = ChatsRoute
        val stack = stack(ChatsRoute)
        if (keepConversation) {
            stack.retainAll { it is ChatsRoute || it is ConversationRoute }
        } else {
            popToRoot(ChatsRoute)
        }
        searchRequest = query
    }

    fun consumeSearchRequest() {
        searchRequest = null
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

/**
 * Zavření obrazovky [route] z její vlastní šipky zpět: odebere PRÁVĚ TU položku, ne poslední,
 * a kořen (index 0) nikdy. Vrací, jestli se něco odebralo.
 *
 * `removeLastOrNull()` tu dřív shodil aplikaci („NavDisplay entries cannot be empty"). Obrazovka
 * po odebrání ještě dobíhá odchodovou animací a její tlačítka dál reagují — druhé ťuknutí na
 * šipku (rychlý dvojklik, nebo mezera z klávesnice, když má šipka fokus) už nemělo co zavřít
 * a odebralo seznam chatů pod ní. Obrazovka, která není v zásobníku, nesmí odebrat nic.
 */
internal fun MutableList<NavKey>.pop(route: NavKey): Boolean {
    val index = lastIndexOf(route)
    if (index <= 0) return false
    removeAt(index)
    return true
}
