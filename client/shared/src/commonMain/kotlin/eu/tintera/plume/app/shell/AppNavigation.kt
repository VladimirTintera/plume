package eu.tintera.plume.app.shell

import androidx.navigation3.runtime.NavEntry
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.accounts.AccountScope
import eu.tintera.plume.core.accounts.AccountViewModels
import eu.tintera.plume.core.accounts.LocalAccount
import androidx.compose.runtime.CompositionLocalProvider
import eu.tintera.plume.core.ui.rememberRestoringScrollState
import eu.tintera.plume.core.ui.LocalShowAccountBadge
import eu.tintera.plume.core.adaptive.LocalPaneNeighbours
import eu.tintera.plume.core.adaptive.PaneRole
import eu.tintera.plume.core.adaptive.paneInfo
import eu.tintera.plume.features.settings.LocalSettingsListScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.koinInject
import eu.tintera.plume.core.accounts.Accounts
import eu.tintera.plume.core.accounts.AccountSlot
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import eu.tintera.plume.core.ui.HighlightTarget
import org.jetbrains.compose.resources.StringResource
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import eu.tintera.plume.core.navigation.VideoChatRoute
import eu.tintera.plume.core.navigation.SheetRoute
import eu.tintera.plume.core.navigation.ChatFolderEditRoute
import eu.tintera.plume.core.navigation.ChatFoldersRoute
import eu.tintera.plume.core.navigation.CreateChatRoute
import eu.tintera.plume.core.navigation.EditProfileRoute
import eu.tintera.plume.core.navigation.TwoStepRoute
import eu.tintera.plume.core.navigation.LoginEmailRoute
import eu.tintera.plume.core.navigation.PasskeysRoute
import eu.tintera.plume.core.navigation.ChangePhoneRoute
import eu.tintera.plume.core.navigation.NewChatRoute
import eu.tintera.plume.core.navigation.NewGroupMembersRoute
import eu.tintera.plume.core.telegram.AppScreen
import eu.tintera.plume.core.navigation.ChatSearchRoute
import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.ConversationRoute
import eu.tintera.plume.core.navigation.ForumTopicsRoute
import eu.tintera.plume.core.navigation.NavSavedStateConfiguration
import eu.tintera.plume.core.navigation.SAVED_ROUTES_VERSION
import eu.tintera.plume.core.navigation.AccountRoute
import eu.tintera.plume.core.navigation.ProxySettingsRoute
import eu.tintera.plume.core.navigation.SavedTopicsRoute
import eu.tintera.plume.core.navigation.SettingsRoute
import eu.tintera.plume.core.navigation.SettingsSection
import eu.tintera.plume.core.navigation.SettingsSectionRoute
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
    private val settingsAccountState: MutableState<AccountSlot?>,
) {
    /** Vybraná sekce. */
    val section: TopLevelRoute get() = selected.value

    /**
     * Účet, jehož Nastavení jsou otevřená; `null` = výchozí účet (účet shellu). Celý zásobník
     * Nastavení se skládá v kontejneru tohohle účtu (`SettingsAccountScope`), takže sekce
     * i podstránky slot v routě nenesou.
     *
     * **Výchozí účet se tím NEMĚNÍ.** Přepnutí výchozího účtu by složilo celý shell znovu
     * (Nastavení by se zavřela) a seznam chatů by v režimu Všechny účty přešel na jeden účet —
     * a kdo jen kontroluje soukromí druhého účtu, nečeká, že se mu přepne seznam chatů.
     */
    val settingsAccount: AccountSlot? get() = settingsAccountState.value

    /**
     * Nastavení účtu [slot] místo dosavadního. Otevřená sekce (nebo úprava profilu) vpravo
     * zůstane a složí se znovu za nový účet; co leží nad ní, patřilo předchozímu účtu a jde pryč.
     */
    fun selectSettingsAccount(slot: AccountSlot) {
        if (settingsAccountState.value == slot) return
        settingsAccountState.value = slot
        stack(SettingsRoute).keepSettingsDetail()
    }

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
     *
     * Účet je [slot], bez něj výchozí: menu účtu slibuje Nastavení toho účtu, který ukazuje
     * jeho fotka, ne účtu, který tam uživatel vybral minule. Odkaz z chatu (`tg://settings/…`)
     * a složky ze seznamu chatů nesou svůj účet.
     */
    fun openSettings(slot: AccountSlot? = null) {
        settingsAccountState.value = slot
        settingsOpenings++
        settingsHighlight.consume()
        showSettingsRoot()
    }

    /** Volba z hledání, na kterou se otevřená obrazovka Nastavení doscrolluje a zvýrazní ji. */
    val settingsHighlight = HighlightTarget()

    /**
     * Roste s každým otevřením Nastavení zvenku (menu účtu, odkaz). Hledání v seznamu sekcí se
     * podle něj vymaže — dotaz z minulé návštěvy by výsledky zakryl kořen, který menu slibuje.
     * Výsledek hledání ho nemění: zpět z něj vede k týmž výsledkům.
     */
    var settingsOpenings by mutableIntStateOf(0)
        private set

    private fun showSettingsRoot() {
        popToRoot(SettingsRoute)
        selected.value = SettingsRoute
    }

    /**
     * Nastavení proxy zvenčí (nabídka nad chatem od provozovatele proxy): Nastavení → Úložiště →
     * Proxy, takže zpět vede po krocích, kudy by se tam uživatel proklikal sám.
     */
    fun openProxySettings(slot: AccountSlot) = openSettingsAt(slot, SettingsSection.Storage, ProxySettingsRoute)

    /**
     * Odkaz na obrazovku aplikace (`tg://settings/devices`, `tg://new/group`…). Do Nastavení se
     * jde po krocích (kořen → sekce → obrazovka), takže zpět vede, kudy by se tam uživatel
     * proklikal sám. Nová konverzace, skupina a kanál se otevřou nad otevřeným chatem, jako
     * zmínka — zpět vede do něj. Hledání v dvoupanelu ([twoPane]) konverzaci nechá.
     */
    fun openScreen(slot: AccountSlot, screen: AppScreen, twoPane: Boolean) {
        when (screen) {
            AppScreen.Settings -> openSettings(slot)
            AppScreen.Privacy -> openSettingsAt(slot, SettingsSection.Privacy)
            AppScreen.Notifications -> openSettingsAt(slot, SettingsSection.Notifications)
            AppScreen.Appearance -> openSettingsAt(slot, SettingsSection.Appearance)
            AppScreen.Devices -> openSettingsAt(slot, SettingsSection.Devices)
            AppScreen.Storage -> openSettingsAt(slot, SettingsSection.Storage)
            // Úprava profilu je pod kartou účtu v seznamu sekcí, ne v sekci.
            AppScreen.EditProfile -> openSettingsWith(slot, EditProfileRoute)
            AppScreen.TwoStepVerification -> openSettingsAt(slot, SettingsSection.Privacy, TwoStepRoute)
            AppScreen.LoginEmail -> openSettingsAt(slot, SettingsSection.Privacy, LoginEmailRoute)
            AppScreen.Passkeys -> openSettingsAt(slot, SettingsSection.Privacy, PasskeysRoute)
            AppScreen.ChangePhone -> openSettingsWith(slot, EditProfileRoute, ChangePhoneRoute)
            AppScreen.ChatFolders -> openChatFolders(slot)
            AppScreen.NewChat -> openInChats(NewChatRoute(slot))
            AppScreen.NewGroup -> openInChats(NewGroupMembersRoute(slot))
            AppScreen.NewChannel -> openInChats(CreateChatRoute(slot, channel = true))
            AppScreen.Search -> openSearch(slot, "", keepConversation = twoPane)
        }
    }

    /** Nastavení → AI v zařízení (zapnout nebo stáhnout model) — z přehledu kanálů. */
    fun openAiSettings(slot: AccountSlot) = openSettingsAt(slot, SettingsSection.Ai)

    private fun openSettingsAt(slot: AccountSlot, section: SettingsSection, screen: NavKey? = null) =
        openSettingsWith(slot, *listOfNotNull(SettingsSectionRoute(section), screen).toTypedArray())

    /**
     * Výsledek hledání v Nastavení: nad seznamem jen [screens] (sekce a podstránky k volbě).
     * Co bylo otevřené předtím, výsledek nahradí — vybraná vlevo je vpravo jednou, jako u sekcí.
     */
    fun openSettingsSearchResult(screens: List<NavKey>, highlight: StringResource?) {
        settingsHighlight.show(highlight)
        showSettingsRoot()
        stack(SettingsRoute).addAll(screens)
    }

    /** Nastavení účtu [slot] na kořeni a nad ním [screens] po řadě — kudy by se tam uživatel proklikal sám. */
    private fun openSettingsWith(slot: AccountSlot, vararg screens: NavKey) {
        openSettings(slot)
        stack(SettingsRoute).addAll(screens)
    }

    private fun openInChats(route: NavKey) {
        selected.value = ChatsRoute
        val chats = stack(ChatsRoute)
        // Otevřený sheet (koncepty, záložky, QR kód) jde pryč: nad sheetem se nic neotevírá
        // a obrazovka nad ním by ho zpět vrátila (viz `dropBuriedSheets`).
        chats.removeAll { it is SheetRoute }
        chats.addOnce(route)
    }

    /**
     * Složky chatů zvenčí („+" v řádku složek nad seznamem chatů): Nastavení → Chaty → Složky
     * chatů, s [create] rovnou i editor nové složky. Zpět vede po krocích, jako u proxy.
     */
    fun openChatFolders(slot: AccountSlot, create: Boolean = false) {
        openSettingsAt(slot, SettingsSection.Chats, ChatFoldersRoute)
        if (create) stack(SettingsRoute).add(ChatFolderEditRoute(slot, 0))
    }

    /**
     * Editor jedné složky z lišty složek (podržení záložky, prázdná složka): Nastavení → Chaty →
     * Složky chatů → složka [folderId]. Zpět vede přes správu složek, jako u nové složky.
     */
    fun openChatFolder(slot: AccountSlot, folderId: Int) {
        openSettingsAt(slot, SettingsSection.Chats, ChatFoldersRoute)
        stack(SettingsRoute).add(ChatFolderEditRoute(slot, folderId))
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
    fun openChat(slot: AccountSlot, chatId: Long, topicId: Int? = null) {
        selected.value = ChatsRoute
        popToRoot(ChatsRoute)
        val chats = stack(ChatsRoute)
        if (topicId != null) chats.add(ForumTopicsRoute(slot, chatId))
        chats.add(ConversationRoute(slot, chatId, topicId = topicId))
    }

    /**
     * Videochat z pruhu nad aplikací: obrazovka se položí nad to, co je otevřené, takže zpět
     * vede tam, odkud se na pruh ťuklo. Je-li nahoře už on, nic.
     */
    fun openVideoChat(slot: AccountSlot, chatId: Long, groupCallId: Int) {
        selected.value = ChatsRoute
        val chats = stack(ChatsRoute)
        if ((chats.lastOrNull() as? VideoChatRoute)?.groupCallId != groupCallId) chats.add(VideoChatRoute(slot, chatId, groupCallId))
    }

    /**
     * Odkaz zvenčí (jiná aplikace, čtečka QR kódů — [openExternal]): zásobník Chatů se srovná
     * na seznam a [build] nad něj přidá cíl, jako u [openChat]. Odkaz s tím, co bylo v Plume
     * zrovna otevřené, nesouvisí, takže zpět vede na seznam.
     */
    internal fun openOverChats(build: MutableList<NavKey>.() -> Unit) {
        selected.value = ChatsRoute
        popToRoot(ChatsRoute)
        stack(ChatsRoute).build()
    }

    /**
     * Rychlý přepínač v konverzaci (podržení šipky zpět): vybraný chat nahradí všechno nad
     * seznamem chatů, jako by se na něj ťuklo v seznamu. Zpět z něj vede na seznam, ne do chatu,
     * ze kterého se přepnulo — přepínání sem a tam by jinak vršilo zásobník bez konce.
     *
     * Skupina s tématy otevře seznam témat a Uložené zprávy po chatech ([savedTopics]) seznam
     * svých témat, stejně jako ze seznamu chatů.
     */
    fun switchChat(slot: AccountSlot, chatId: Long, forum: Boolean, savedTopics: Boolean) {
        selected.value = ChatsRoute
        popToRoot(ChatsRoute)
        stack(ChatsRoute).add(
            when {
                forum -> ForumTopicsRoute(slot, chatId)
                savedTopics -> SavedTopicsRoute(slot, chatId)
                else -> ConversationRoute(slot, chatId)
            },
        )
    }

    /**
     * Sdílení bez vybraného chatu: obyčejný seznam chatů, nad kterým visí čip „Sdílíte: …".
     * Zásobník Chatů se srovná na seznam jako u [openChat] — sdílený obsah s tím, co bylo
     * v Plume zrovna otevřené, nesouvisí, a obrazovka „Komu: …" předchozího sdílení tím odejde.
     */
    fun showChatList() {
        selected.value = ChatsRoute
        popToRoot(ChatsRoute)
    }

    /**
     * Sdílení do chatu vybraného v systémové nabídce (Direct Share): potvrzovací obrazovka nad
     * srovnaným zásobníkem Chatů, jako [showChatList]. Po odeslání ji nahradí ta konverzace.
     */
    fun openShareTo(slot: AccountSlot, chatId: Long) {
        selected.value = ChatsRoute
        // Otevřená obrazovka zůstává, ze stejného důvodu jako u výběru: její odchod sdílení
        // zahazuje, takže odebrat a vrátit by zahodilo právě ten nový obsah.
        if (stack(ChatsRoute).lastOrNull() == ShareToChatRoute(slot, chatId)) return
        popToRoot(ChatsRoute)
        stack(ChatsRoute).add(ShareToChatRoute(slot, chatId))
    }

    /**
     * Hledání v seznamu chatů účtu [slot] ([ChatSearchRoute]), s dotazem [query] (hashtag
     * z konverzace). Hledání je list panel: na telefonu se zásobník Chatů srovná na kořen
     * a hledání jde nad něj, v dvoupanelu zůstane otevřená konverzace ([keepConversation])
     * a hledání se vloží POD ni — list-detail scéna bere za seznam poslední položku v roli
     * seznamu a za detail jen to, co leží nad ní. Výsledek se pak otevře nad hledáním a hledání
     * vlevo zůstane. Archiv jde pryč v obou případech, hledání má jen hlavní seznam.
     */
    fun openSearch(slot: AccountSlot, query: String, keepConversation: Boolean) {
        selected.value = ChatsRoute
        val stack = stack(ChatsRoute)
        if (keepConversation) {
            stack.retainAll { it is ChatsRoute || it is ConversationRoute }
        } else {
            popToRoot(ChatsRoute)
        }
        stack.add(stack.indexOfFirst { it is ChatsRoute } + 1, ChatSearchRoute(slot, query))
    }

    /**
     * Chat, na který má seznam chatů dojet (vybraný v hledání). Stav, ne událost: seznam v tu
     * chvíli nemusí být složený (na telefonu ho překrývá hledání i konverzace) a vyzvedne si ho,
     * až se složí — viz [consumeChatListScroll].
     */
    var chatListScroll: Long? by mutableStateOf(null)
        private set

    fun scrollChatListTo(chatId: Long) {
        chatListScroll = chatId
    }

    fun consumeChatListScroll() {
        chatListScroll = null
    }

    /**
     * Sheet ([SheetRoute]) navrch zásobníku sekce [section], v níž se otevírá — z Nastavení do
     * zásobníku Nastavení, ze seznamu chatů do Chatů. Navrchu už je (dvojí ťuknutí): nic.
     */
    fun openSheet(section: TopLevelRoute, sheet: SheetRoute) {
        val stack = stack(section)
        if (stack.lastOrNull() != sheet) stack.add(sheet)
    }

    /**
     * Routa sheetu pryč, ať je kdekoli: sheet se zavřel sám (tah dolů, zpět, ťuknutí vedle), nebo
     * vede jinam (koncept, záložka) — pak se zavírá NAPŘED, aby nová obrazovka nestála nad ním
     * a pravidla nad zásobníkem (`openTarget`, `addOnce`) se nerozhodovala podle sheetu navrchu.
     * Sheet neví, ve kterém zásobníku stojí — Můj QR kód jde otevřít z Chatů i z Nastavení.
     */
    fun closeSheet(sheet: SheetRoute) {
        stacks.values.forEach { stack ->
            val index = stack.lastIndexOf(sheet)
            if (index >= 0) stack.removeAt(index)
        }
    }

    /**
     * Sheety mimo sekci [section] pryč. Sheet patří k obrazovce pod sebou; přepnutí sekce (odkaz
     * do Nastavení, zatímco v Chatech visí sheet) by ho nechalo čekat ve skrytém zásobníku
     * a po návratu by vyskočil, nebo by stál uprostřed položek, které se kreslí (Chaty pod
     * Nastavením), kde ho `SheetSceneStrategy` nepozná.
     */
    fun closeSheetsOutside(section: TopLevelRoute) {
        stacks.forEach { (key, stack) -> if (key != section) stack.removeAll { it is SheetRoute } }
    }

    /**
     * Sheety, které nestojí navrchu svého zásobníku, pryč (`dropBuriedSheets`). Pojistka pro
     * cestu, která obrazovku položí nad otevřený sheet, místo aby ho zavřela; ohlásí se do logu,
     * ať se ta cesta najde a opraví.
     */
    fun dropBuriedSheets() {
        stacks.forEach { (section, stack) ->
            val before = stack.toList()
            if (stack.dropBuriedSheets()) {
                logger.w { "Sheet uprostřed zásobníku ${section::class.simpleName}: $before" }
            }
        }
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

private val logger = Logger.withTag("Navigation")

/** Sekce, ve které aplikace začíná a přes kterou se z ní odchází. */
private val START_SECTION: TopLevelRoute = ChatsRoute

@Composable
fun rememberAppNavigation(): AppNavigation {
    // Overload bez konfigurace existuje jen na Androidu; konfigurace navíc MUSÍ znát podtypy
    // NavKey — viz NavSavedStateConfiguration. `key` drží každý zásobník na jeho sekci.
    val stacks = TopLevelDestination.entries.associate { destination ->
        destination.route to key(destination, SAVED_ROUTES_VERSION) {
            rememberNavBackStack(NavSavedStateConfiguration, destination.route)
        }
    }
    val selected = rememberSaveable(saver = SelectedSectionSaver) { mutableStateOf(START_SECTION) }
    val settingsAccount = rememberSaveable(saver = SettingsAccountSaver) { mutableStateOf<AccountSlot?>(null) }
    return remember(selected, settingsAccount) { AppNavigation(selected, stacks, settingsAccount) }
}

/**
 * Účet Nastavení přežije zabití procesu i s otevřenou podstránkou. Ukládá se id slotu (prázdné
 * = výchozí účet); slot, který mezitím zmizel, `SettingsAccountScope` nahradí výchozím.
 */
private val SettingsAccountSaver = Saver<MutableState<AccountSlot?>, String>(
    save = { state -> state.value?.id.orEmpty() },
    restore = { id -> mutableStateOf(id.takeIf { it.isNotEmpty() }?.let(::AccountSlot)) },
)

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

/**
 * Zásobník Nastavení po přepnutí účtu: kořen a otevřený detail (sekce, úprava profilu), nic nad
 * ním. Detail se složí znovu za nový účet; podstránky, sheet ani routa s účtem (editor složky,
 * prohlížeč fotek profilu) by patřily předchozímu.
 */
internal fun MutableList<NavKey>.keepSettingsDetail() {
    val detail = getOrNull(1)
    val keep = if (detail != null && detail !is AccountRoute && detail !is SheetRoute) 2 else 1
    while (size > keep) removeAt(lastIndex)
}

/**
 * Podstránka [subpage] otevřená z obrazovky [opener] (sekce Nastavení, úprava profilu):
 * NAHRADÍ všechno, co nad [opener] leží, nevrší se nad to.
 *
 * Od šířky Medium je sekce vidět vedle své podstránky a dá se v ní ťuknout na jiný řádek.
 * Dokud se podstránka jen přidávala (hlídalo se jen, jestli nahoře neleží tatáž), stavěly se
 * Dvoufázové ověření, Zámek aplikace a další přes sebe a zpět pak procházelo všechny, na které
 * se kdy ťuklo. Je to tentýž vzor jako výběr sekce v seznamu (`replaceSettingsDetail`)
 * a konverzace v seznamu chatů: co je vybrané vlevo, to je vpravo, jednou.
 *
 * Vrší se dál jen to, co se otevírá z PODSTRÁNKY (Soukromí účtu → jedno nastavení, složky →
 * úprava složky) — tam obrazovka pod ní vidět není.
 *
 * Ťuknutí na řádek otevřené podstránky ji nechá (i s jejím stavem) a zavře jen, co leží nad
 * ní. [opener], který už v zásobníku není (dobíhá odchodovou animací), neotevře nic.
 */
internal fun MutableList<NavKey>.openSubpage(opener: NavKey, subpage: NavKey) {
    val index = lastIndexOf(opener)
    if (index < 0) return
    val keep = if (getOrNull(index + 1) == subpage) index + 2 else index + 1
    while (size > keep) removeAt(lastIndex)
    if (size == index + 1) add(subpage)
}

/**
 * Obsah routy s účtem ([AccountRoute]) se skládá v kontejneru toho účtu, takže `koinInject`
 * a `koinViewModel` obrazovky míří do něj. Klíč a metadata položky zůstávají. Ostatní routy se
 * skládají v účtu shellu.
 *
 * Routa účtu, který mezitím zmizel (odhlášený), neukáže nic: na snímek se může stát, že ji shell
 * ještě skládá, než se složí znovu pro jiný výchozí účet.
 */
internal fun ((NavKey) -> NavEntry<NavKey>).inRouteAccount(accounts: Accounts): (NavKey) -> NavEntry<NavKey> = { key ->
    val entry = this(key)
    if (key is AccountRoute) {
        NavEntry(key, entry.contentKey, entry.metadata) { accounts.find(key.slot)?.let { AccountScope(it) { entry.Content() } } }
    } else {
        entry
    }
}

/**
 * Obsah položek zásobníku Nastavení se skládá v kontejneru účtu [AppNavigation.settingsAccount]:
 * `koinInject` a `koinViewModel` sekcí i podstránek míří do něj a `LocalAccount` je on. Routy
 * Nastavení proto slot nenesou a hledání v Nastavení skládá zásobník beze změny.
 */
internal fun ((NavKey) -> NavEntry<NavKey>).inSettingsAccount(navigation: AppNavigation): (NavKey) -> NavEntry<NavKey> = { key ->
    val entry = this(key)
    NavEntry(key, entry.contentKey, entry.metadata) {
        SettingsAccountScope(
            slot = navigation.settingsAccount,
            list = key == SettingsRoute,
            extra = entry.paneInfo?.role == PaneRole.Extra,
        ) { entry.Content() }
    }
}

/**
 * [content] v účtu [slot]; bez něj, nebo když účet mezitím zmizel (odhlášený z vlastních
 * Nastavení), v účtu shellu.
 *
 * Účet čte až uvnitř kompozice, ne při stavbě položky: položky si `rememberDecoratedNavEntries`
 * pamatuje podle klíčů, takže obsah musí na změnu účtu reagovat sám.
 *
 * Jiný účet než shellu dostane vlastní úložiště ViewModelů ([AccountViewModels]) a se změnou
 * se obsah složí od začátku: `koinViewModel()` bez klíče by jinak našel ViewModel předchozího
 * účtu i s jeho repository.
 */
@Composable
private fun SettingsAccountScope(slot: AccountSlot?, list: Boolean, extra: Boolean, content: @Composable () -> Unit) {
    val shell = LocalAccount.current
    val all by koinInject<Accounts>().all.collectAsStateWithLifecycle()
    val account = slot?.let { wanted -> all.firstOrNull { it.slot == wanted } } ?: shell
    // Nad klíčem účtu: seznam [list] po ťuknutí na řádek účtu zůstane, kde byl (viz LocalSettingsListScroll).
    val listScroll = if (list) rememberRestoringScrollState() else null
    /*
     * Fotka účtu v liště (AccountBadgeAction): jen s víc účty, jen když vedle není vidět seznam
     * s kartou účtu, a z panelů vedle sebe jen v PRAVÉM. Se sekcí a podstránkou vedle sebe (seznam
     * schovaný) by jinak byla ve dvou lištách hned vedle sebe; nese ji podstránka (role Extra).
     */
    val neighbours = LocalPaneNeighbours.current
    val badge = !list && all.size > 1 && !neighbours.listBeside && (extra || !neighbours.extraBeside)
    CompositionLocalProvider(
        LocalSettingsListScroll provides listScroll,
        LocalShowAccountBadge provides badge,
    ) {
        if (account.slot == shell.slot) {
            content()
        } else {
            key(account.slot) { AccountScope(account) { AccountViewModels(account.slot, content) } }
        }
    }
}
