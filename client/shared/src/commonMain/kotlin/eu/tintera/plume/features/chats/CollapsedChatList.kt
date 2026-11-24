package eu.tintera.plume.features.chats

import eu.tintera.plume.core.ui.CountdownSnackbarHost
import androidx.compose.foundation.background
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.ShapeDefaults
import eu.tintera.plume.core.ui.contextClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import eu.tintera.plume.core.accounts.AccountBadge
import eu.tintera.plume.core.accounts.AccountScope
import eu.tintera.plume.core.accounts.AccountSession
import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.accounts.AccountSlot
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.RollingNumber
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.chatTitle
import eu.tintera.plume.core.ui.isSavedMessages
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.core.ui.WithTooltip
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_new_story
import eu.tintera.plume.resources.photo_camera_24dp
import eu.tintera.plume.resources.chats_search
import eu.tintera.plume.resources.search_24dp
import eu.tintera.plume.resources.cake_24dp
import eu.tintera.plume.resources.error_24dp
import eu.tintera.plume.resources.folder_24dp
import eu.tintera.plume.resources.lightbulb_24dp
import eu.tintera.plume.resources.shield_person_24dp
import eu.tintera.plume.resources.system_update_24dp
import eu.tintera.plume.resources.chats_pending_freeze
import eu.tintera.plume.resources.chats_pending_login
import eu.tintera.plume.resources.chats_pending_birthdays
import eu.tintera.plume.resources.chats_pending_suggestion
import eu.tintera.plume.resources.chats_pending_folder_chats
import eu.tintera.plume.resources.chats_pending_update
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import eu.tintera.plume.core.adaptive.ContentWidth
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.RootOverlay
import eu.tintera.plume.resources.campaign_24dp
import eu.tintera.plume.resources.chats_create
import eu.tintera.plume.resources.chats_new
import eu.tintera.plume.resources.chats_selection_close
import eu.tintera.plume.resources.chats_selection_count
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.group_24dp
import eu.tintera.plume.resources.new_chat_new_channel
import eu.tintera.plume.resources.new_chat_new_group
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import eu.tintera.plume.core.adaptive.belowWindowControls
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.LaunchedEffect

/**
 * Seznam chatů sbalený táhlem na úzký sloupec (`ListPaneAnchor.Collapsed`): jen avatary
 * s počtem nepřečtených, nahoře účet, „Vytvořit" a hledání. Jako sbalený seznam v Telegram
 * Desktop — konverzace dostane skoro celou plochu a přepínat chaty jde dál jedním ťuknutím.
 *
 * - **Pořadí a skupiny jsou tytéž jako v plném seznamu** ([chatGroups]), jen bez předělů:
 *   rozbalením se nic nepřeskládá.
 * - **Hledání seznam napřed rozbalí** ([onSearch]) — pilulka hledání se do sloupce nevejde
 *   a výsledky by v něm byly nečitelné.
 * - **Jméno chatu ukáže bublina po najetí myší** ([WithTooltip], ne holý `TooltipBox`; na dotykové
 *   ploše podržení); čtečka ho dostane jako popis.
 * - **Karty nad seznamem** (aktualizace, nové přihlášení, narozeniny…) se do sloupce nevejdou.
 *   Že nějaká čeká, ukazuje tlačítko s tečkou pod lupou ([pendingCard]); ťuknutí seznam
 *   rozbalí a odjede nahoru na ni. Bez něj by se o nepotvrzeném přihlášení člověk se sbaleným
 *   seznamem nedozvěděl, dokud ho nerozbalí.
 * - **„Vytvořit" je FAB nahoře pod účtem**, jako FAB v navigačním railu M3 (sloupec má šířku
 *   railu). FAB menu plného seznamu vyjíždí s popisky nad FAB a do sloupce by se nevešlo, takže
 *   tu tytéž tři položky nabídne nabídka vedle tlačítka ([CreateButton]).
 * - **Výběr víc chatů jde i tady**: podržení (pravé tlačítko) otevře nabídku nad chatem
 *   s „Vybrat", jako v plném seznamu. Ve výběru je nahoře ✕ s počtem, u avatarů značka výběru
 *   a dole svislá plovoucí lišta s akcemi (vodorovná se do sloupce nevejde). Výběr je stav
 *   ViewModelu, takže přežije rozbalení i sbalení táhlem.
 * - **Snackbar (archivace s „Vrátit") stojí z kořene okna vlevo dole** ([CollapsedSnackbar]):
 *   do sloupce se nevejde a hostitel plného seznamu tu není.
 * - **V režimu Všechny účty jsou ve sloupci chaty všech účtů** ([CollapsedAccounts.sources]),
 *   ve stejném pořadí jako ve společném seznamu ([mergedChatGroups]) a s odznakem účtu na
 *   avataru. Výběr víc chatů patří jednomu účtu: po dobu výběru sloupec ukazuje jen jeho chaty
 *   ([viewModel] je pak ViewModel toho účtu).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollapsedChatList(
    /** ViewModel účtu, jehož chaty sloupec ukazuje mimo společný seznam (i výběr víc chatů). */
    viewModel: ChatsViewModel,
    /** Sheety nad chaty [viewModel] (podržení chatu); účty společného seznamu mají vlastní. */
    sheets: ChatSheets,
    /** Pozice sloupce; drží ji obrazovka, aby přežila rozbalení a nové sbalení. */
    listState: LazyListState,
    /** Chat, na který sloupec při sbalení dojede (`firstVisibleChatKey`); `null` = beze změny. */
    anchorKey: Any?,
    accounts: CollapsedAccounts,
    onSearch: () -> Unit,
    onNewChat: () -> Unit,
    onNewGroup: () -> Unit,
    onNewChannel: () -> Unit,
    onNewStory: (() -> Unit)?,
    snackbarHostState: SnackbarHostState,
    account: @Composable () -> Unit,
    /** Nejdůležitější karta, která v plném seznamu nahoře čeká; `null` = žádná. */
    pendingCard: PendingCard?,
    /** Rozbalit seznam a ukázat karty nahoře. */
    onShowCards: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hiddenSavedMessagesChatId by viewModel.hiddenSavedMessagesChatId.collectAsStateWithLifecycle()
    val snoozes by viewModel.snoozeState.collectAsStateWithLifecycle()
    val selection by viewModel.selection.collectAsStateWithLifecycle()
    val selectionActions by viewModel.selectionActions.collectAsStateWithLifecycle()
    // Zpět výběr ukončí, jako v plném seznamu (tam ho hlídá `ChatListContent`, který tu není).
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = selection != null,
        onBackCompleted = viewModel::closeSelection,
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start))
            // Desktop: sloupec je užší než semafory, avatar účtu jde pod ně.
            .belowWindowControls(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(vertical = 12.dp),
        ) {
            val current = selection
            if (current != null) {
                SelectionHeader(count = current.count, onClose = viewModel::closeSelection)
            } else {
                account()
                CreateButton(onNewChat = onNewChat, onNewGroup = onNewGroup, onNewChannel = onNewChannel, onNewStory = onNewStory)
                IconButton(onClick = onSearch, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(Res.drawable.search_24dp), contentDescription = stringResource(Res.string.chats_search), modifier = Modifier.listCollapseElement("search"))
                }
                pendingCard?.let { PendingCardButton(it, onClick = onShowCards) }
            }
        }

        if (accounts.sources.isNotEmpty()) {
            MergedCollapsedChats(accounts, listState, anchorKey)
        } else AccountScope(accounts.listAccount) {
        val listSlot = accounts.listAccount.slot
        when (val current = state) {
            is ChatListState.Ready -> {
                val chats = remember(current.chats, hiddenSavedMessagesChatId, snoozes.due) {
                    chatGroups(current.chats, hiddenSavedMessagesChatId, snoozes.due).flatten()
                }
                val picked = selection?.ids
                LaunchedEffect(anchorKey) {
                    val index = chats.indexOfFirst { it.id == anchorKey }
                    if (index >= 0) listState.requestScrollToItem(index)
                }
                Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        // Ve výběru se poslední chat musí dát odscrollovat nad lištu akcí.
                        contentPadding = PaddingValues(bottom = if (picked != null) SELECTION_TOOLBAR_CLEARANCE else 16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                    ) {
                        items(chats, key = { it.id }) { chat ->
                            val selecting = picked != null
                            val mark = picked?.contains(chat.id)?.takeIf { chat.isSelectable() }
                            CollapsedChat(
                                chat = chat,
                                // Ve výběru zvýrazňuje výběr, ne chat otevřený vedle (jako plný seznam).
                                selected = if (selecting) mark == true else accounts.selected == listSlot to chat.id,
                                selectionMark = mark,
                                onClick = {
                                    if (selecting) {
                                        if (chat.isSelectable()) viewModel.toggleSelected(chat)
                                    } else {
                                        accounts.onOpen(listSlot, chat)
                                    }
                                },
                                // Ve výběru i podržení jen přepíná — nabídka nad jedním chatem by z výběru vytrhla.
                                onLongClick = {
                                    if (selecting) {
                                        if (chat.isSelectable()) viewModel.toggleSelected(chat)
                                    } else {
                                        sheets.actions = chat
                                    }
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                    ChatSelectionToolbar(
                        actions = selectionActions,
                        callbacks = viewModel.selectionCallbacks,
                        count = selection?.count ?: 0,
                        vertical = true,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                    )
                }
            }
            ChatListState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
            // Chybu i s tlačítkem „Zkusit znovu" ukáže plný seznam; do sloupce se nevejde.
            is ChatListState.Failed -> Unit
        }
        }
    }

    CollapsedSnackbar(snackbarHostState)
}

/**
 * Účty sbaleného sloupce: společný seznam víc účtů ([sources], prázdný mimo režim Všechny účty
 * a po dobu výběru víc chatů), účet seznamu mimo něj ([listAccount]: výchozí, nebo ten
 * s výběrem) a otevřený chat s účtem — totéž id má chat ve dvou účtech.
 */
@Immutable
internal data class CollapsedAccounts(
    val sources: List<ChatSource>,
    val listAccount: AccountSession,
    val selected: Pair<AccountSlot, Long>?,
    /** Otevře chat (nebo témata) v účtu řádku. */
    val onOpen: (AccountSlot, ChatSummary) -> Unit,
)

/**
 * Sloupec v režimu Všechny účty: avatary všech účtů v pořadí společného seznamu, každý
 * v kontejneru svého účtu (fotky z jeho souborů) a s odznakem účtu. Podržení otevře nabídku
 * nad chatem v ViewModelu účtu řádku; tu kreslí `MergedChatActionsHosts` nad sloupcem.
 */
@Composable
private fun MergedCollapsedChats(accounts: CollapsedAccounts, listState: LazyListState, anchorKey: Any?) {
    val sources = accounts.sources
    val states = sources.map { source -> key(source.account.slot) { source.viewModel.state.collectAsStateWithLifecycle().value } }
    val hidden = sources.map { source -> key(source.account.slot) { source.viewModel.hiddenSavedMessagesChatId.collectAsStateWithLifecycle().value } }
    val due = sources.map { source -> key(source.account.slot) { source.viewModel.snoozeState.collectAsStateWithLifecycle().value.due } }
    val ready = states.map { (it as? ChatListState.Ready)?.chats }
    if (ready.all { it == null }) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
        return
    }
    val chats = remember(ready, hidden, due) {
        mergedChatGroups(ready.mapIndexed { index, chats -> MergeSource(chats.orEmpty(), hidden[index], due[index]) }).flatten()
    }
    LaunchedEffect(anchorKey) {
        val index = chats.indexOfFirst { it.key == anchorKey }
        if (index >= 0) listState.requestScrollToItem(index)
    }
    LazyColumn(
        state = listState,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(bottom = 16.dp),
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
    ) {
        items(chats, key = { it.key }) { merged ->
            val source = sources[merged.source]
            val slot = source.account.slot
            AccountScope(source.account) {
                CollapsedChat(
                    chat = merged.chat,
                    selected = accounts.selected == slot to merged.chat.id,
                    selectionMark = null,
                    onClick = { accounts.onOpen(slot, merged.chat) },
                    onLongClick = { source.sheets.actions = merged.chat },
                    account = source.info,
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

/**
 * „Vytvořit" ve sbaleném sloupci: FAB, jako v navigačním railu, a vedle něj nabídka se stejnými
 * položkami jako FAB menu plného seznamu. Nabídka je okno, takže ji ořez panelu neusekne.
 */
@Composable
private fun CreateButton(onNewChat: () -> Unit, onNewGroup: () -> Unit, onNewChannel: () -> Unit, onNewStory: (() -> Unit)?) {
    var expanded by remember { mutableStateOf(false) }
    val label = stringResource(Res.string.chats_create)
    Box(Modifier.padding(vertical = 4.dp)) {
        WithTooltip(label = label, position = TooltipAnchorPosition.Right) {
            FloatingActionButton(onClick = { expanded = true }, modifier = Modifier.listCollapseElement("create")) {
                Icon(painterResource(Res.drawable.edit_24dp), contentDescription = label)
            }
        }
        // Nová zpráva nahoře, nejblíž tlačítku — nejčastější, jako u palce ve FAB menu.
        ExpressiveMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            popupPositionProvider = MenuDefaults.rememberDropdownMenuPopupPositionProvider(MenuAnchorPosition.End),
            groups = listOfNotNull(
                listOf(
                    MenuAction(stringResource(Res.string.chats_new), Res.drawable.edit_24dp, onClick = onNewChat),
                    MenuAction(stringResource(Res.string.new_chat_new_group), Res.drawable.group_24dp, onClick = onNewGroup),
                    MenuAction(stringResource(Res.string.new_chat_new_channel), Res.drawable.campaign_24dp, onClick = onNewChannel),
                ),
                // Příběh je jiný druh tvorby než chat — vlastní skupina.
                onNewStory?.let { listOf(MenuAction(stringResource(Res.string.chats_new_story), Res.drawable.photo_camera_24dp, onClick = it)) },
            ),
        )
    }
}

/**
 * Hlavička sloupce ve výběru: ✕ a pod ním počet vybraných. Čtečce jde počet celou větou
 * („3 vybrané chaty"), samotné číslo by nic neřeklo.
 */
@Composable
private fun SelectionHeader(count: Int, onClose: () -> Unit) {
    val closeLabel = stringResource(Res.string.chats_selection_close)
    WithTooltip(label = closeLabel, position = TooltipAnchorPosition.Right) {
        IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(Res.drawable.close_24dp), contentDescription = closeLabel)
        }
    }
    val countLabel = pluralStringResource(Res.plurals.chats_selection_count, count, count)
    RollingNumber(
        count,
        style = MaterialTheme.typography.titleLarge.tabularNumbers(),
        modifier = Modifier.clearAndSetSemantics { contentDescription = countLabel },
    )
}

/**
 * Snackbar sbaleného seznamu z kořene okna, vlevo dole: přes sloupec i konverzaci vedle. Do
 * sloupce 96 dp se nevejde a ořez panelu by ho usekl, proto okno ([Popup]) — a to z kořene
 * kompozice ([RootOverlay]), aby se polohovalo k rohu OKNA, ne sloupce. Dřív to byla i pojistka
 * proti pádu na desktopu (sloupec mizí uprostřed měření, když tah táhlem přepne na plný seznam);
 * ten Compose Multiplatform 1.13 opravuje.
 * Kreslí se jen s hláškou, prázdné okno by tam viselo zbytečně.
 */
@Composable
private fun CollapsedSnackbar(state: SnackbarHostState) {
    if (state.currentSnackbarData == null) return
    // Insety se čtou tady, v okně aplikace; okno snackbaru je samo nemá.
    val insets = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Start).asPaddingValues()
    RootOverlay {
        // Zpět ani ťuknutí vedle okno nezavírá: zpět patří navigaci (jinak by ho okno na čtyři
        // vteřiny spolklo) a snackbar mizí sám.
        Popup(
            alignment = Alignment.BottomStart,
            properties = PopupProperties(focusable = false, dismissOnBackPress = false, dismissOnClickOutside = false),
        ) {
            SnackbarHost(
                state,
                Modifier
                    .padding(insets)
                    .padding(12.dp)
                    .widthIn(max = ContentWidth.Pane),
            ) { CountdownSnackbarHost(it) }
        }
    }
}

/**
 * Avatar chatu ve sbaleném seznamu. Vybraný chat má pod avatarem plochu `secondaryContainer`,
 * stejnou roli jako vybraný řádek plného seznamu (navigační stav, ne zvýraznění). Ve výběru
 * víc chatů nese roh avataru značku výběru ([selectionMark]), jako v plném seznamu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollapsedChat(
    chat: ChatSummary,
    selected: Boolean,
    selectionMark: Boolean?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Účet chatu ve společném seznamu víc účtů: odznak vlevo dole na avataru. */
    account: AccountInfo? = null,
) {
    val title = chatTitle(chat.id, chat.title)
    val container = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    val content = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    /*
     * `TooltipBox` místo `WithTooltip` kvůli stavu: na dotyku bublinu ukáže i podržení,
     * a to teď otevírá nabídku nad chatem — bublina se jménem by pak nad sloupcem visela,
     * dokud se uživatel nedotkne jinde (ověřeno na emulátoru). Jméno nese titulek nabídky.
     */
    val tooltipState = rememberTooltipState()
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Right),
        tooltip = { PlainTooltip { Text(title) } },
        state = tooltipState,
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(COLLAPSED_ITEM_SIZE)
                .clip(COLLAPSED_ITEM_SHAPE)
                .then(if (selected) Modifier.background(container) else Modifier)
                .contextClickable(
                    onClick = onClick,
                    onLongClick = {
                        onLongClick()
                        // Bublinu ukazuje kotva na týž dlouhý stisk; pořadí obou obsluh není
                        // dané, proto zavřít až po ní.
                        scope.launch {
                            delay(TOOLTIP_AFTER_LONG_PRESS_MS)
                            tooltipState.dismiss()
                        }
                    },
                )
                .semantics {
                    contentDescription = title
                    this.selected = selected
                },
        ) {
            Box {
                Avatar(
                    chatId = chat.avatarColorId,
                    title = chat.title,
                    avatarFileId = chat.avatarFileId,
                    size = COLLAPSED_AVATAR_SIZE,
                    isSavedMessages = isSavedMessages(chat.id),
                    shape = chat.kind.avatarShape(),
                    modifier = Modifier.collapseAvatarFlight(collapseAvatarKey(chat.id)),
                )
                if (chat.unreadCount > 0) {
                    val muted = chat.isMuted
                    Badge(
                        containerColor = if (muted) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.primary,
                        contentColor = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp),
                    ) {
                        RollingNumber(chat.unreadCount, style = LocalTextStyle.current.tabularNumbers())
                    }
                }
                if (account != null) {
                    AccountBadge(account, rim = container, modifier = Modifier.align(Alignment.BottomStart))
                }
                if (selectionMark != null) {
                    SelectionMark(
                        selected = selectionMark,
                        rim = container,
                        contentColor = content,
                        modifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Karta nahoře v plném seznamu chatů, kterou sbalený sloupec nemá kam dát. Pořadí je pořadí
 * karet v seznamu — první čekající je ta nejdůležitější. [urgent] karty (zmrazený účet, cizí
 * přihlášení) jsou v chybové barvě: týkají se bezpečnosti účtu, ne pohodlí.
 */
internal enum class PendingCard(val icon: DrawableResource, val label: StringResource, val urgent: Boolean) {
    Freeze(Res.drawable.error_24dp, Res.string.chats_pending_freeze, urgent = true),
    Login(Res.drawable.shield_person_24dp, Res.string.chats_pending_login, urgent = true),
    Birthdays(Res.drawable.cake_24dp, Res.string.chats_pending_birthdays, urgent = false),
    Suggestion(Res.drawable.lightbulb_24dp, Res.string.chats_pending_suggestion, urgent = false),
    FolderChats(Res.drawable.folder_24dp, Res.string.chats_pending_folder_chats, urgent = false),
    Update(Res.drawable.system_update_24dp, Res.string.chats_pending_update, urgent = false),
}

/**
 * Tlačítko s tečkou: že nahoře v seznamu čeká [card]. Ikona a bublina říkají která, tečka
 * jen to, že něco — stejný odznak jako nepřečtené. Bublina přes [WithTooltip] (viz výš).
 */
@Composable
private fun PendingCardButton(card: PendingCard, onClick: () -> Unit) {
    val label = stringResource(card.label)
    val colors = if (card.urgent) {
        IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        )
    } else {
        IconButtonDefaults.filledTonalIconButtonColors()
    }
    WithTooltip(label = label, position = TooltipAnchorPosition.Right) {
        BadgedBox(
            badge = {
                Badge(containerColor = if (card.urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            },
        ) {
            FilledTonalIconButton(onClick = onClick, colors = colors, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(card.icon), contentDescription = label)
            }
        }
    }
}

private val COLLAPSED_AVATAR_SIZE = 52.dp
private val COLLAPSED_ITEM_SIZE = 72.dp
private val COLLAPSED_ITEM_SHAPE = ShapeDefaults.LargeIncreased

/** Za jak dlouho po podržení zavřít bublinu, kterou ukázala kotva (viz [CollapsedChat]). */
private const val TOOLTIP_AFTER_LONG_PRESS_MS = 100L

/** Svislá lišta výběru: tři tlačítka, odsazení lišty a odstup od spodku. */
private val SELECTION_TOOLBAR_CLEARANCE = 216.dp
