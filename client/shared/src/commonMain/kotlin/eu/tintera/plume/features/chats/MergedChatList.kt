package eu.tintera.plume.features.chats

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.accounts.AccountScope
import eu.tintera.plume.core.accounts.AccountSession
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.settings.ChatSwipeActions
import eu.tintera.plume.core.settings.ThemeColor
import eu.tintera.plume.core.snooze.ChatSnoozeState
import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.telegram.ActiveStories
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.adaptive.LIST_CONTENT_INSET
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.LocalChatRowGaps
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.accounts_filter_all
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinInternalApi

/**
 * Co patří účtu řádku seznamu chatů ([ChatListItem]): jeho ViewModel (akce nad chatem jdou do
 * něj), výběr, odložení, barvy a příběhy. Ve společném seznamu má každý účet vlastní.
 */
@Immutable
internal data class ChatRowsContext(
    val viewModel: ChatsViewModel,
    val selection: ChatSelection?,
    val savedMessagesChatId: Long?,
    val swipeActions: ChatSwipeActions,
    val snoozes: ChatSnoozeState,
    val awaitingReply: Map<Long, Long>,
    val storyRings: Map<Long, ActiveStories>,
    val chatColors: Map<Long, ThemeColor>,
    val chatAccents: Map<ThemeColor, ColorScheme>,
    val rowGaps: Boolean,
    /** Účet řádku pro odznak u avataru; `null` = seznam jednoho účtu, odznak není. */
    val account: AccountInfo? = null,
)

/** Kde řádek v bloku stojí: pořadí ve skupině, velikost skupiny a pořadí skupiny. */
@Immutable
internal data class GroupPosition(val index: Int, val size: Int, val groupIndex: Int)

/**
 * Účet ve společném seznamu: jeho kontejner, ViewModel hlavního seznamu a jméno s fotkou (filtr
 * a odznak u řádku).
 */
@Immutable
internal data class ChatSource(
    val account: AccountSession,
    val viewModel: ChatsViewModel,
    val info: AccountInfo?,
)

/**
 * ViewModely hlavního seznamu všech [accounts], každý z kontejneru svého účtu. Klíč se slotem:
 * úložiště ViewModelů je tu jedno (účtu shellu) a bez klíče by si účty ViewModel sdílely.
 * Vlastní ViewModel má i účet shellu, ne ten obrazovky: obrazovka může mít vybranou složku,
 * kdežto společný seznam je vždycky hlavní seznam (složky jsou na serveru po účtech).
 *
 * Kořenový scope kontejneru je interní API Koinu, ale jiná cesta, jak dostat ViewModel z cizího
 * kontejneru jako hodnotu, není: `AccountScope` ho podá jen obsahu uvnitř sebe. Kdyby ho Koin
 * schoval, ukáže to překladač.
 */
@OptIn(KoinInternalApi::class)
@Composable
internal fun rememberChatSources(accounts: List<AccountEntry>): List<ChatSource> =
    accounts.map { entry ->
        key(entry.account.slot) {
            ChatSource(
                account = entry.account,
                viewModel = koinViewModel(key = "chats-all-${entry.account.slot.id}", scope = entry.account.koin.scopeRegistry.rootScope),
                info = entry.info,
            )
        }
    }

/**
 * Chat ve společném seznamu: [source] je pořadí účtu v seznamu účtů. Klíč položky nese účet,
 * protože totéž id má chat ve dvou účtech (společná skupina, soukromý chat s týmž člověkem).
 */
internal data class MergedChat(val source: Int, val chat: ChatSummary) {
    val key: String get() = "$source:${chat.id}"
}

/** Vstup [mergedChatGroups] za jeden účet: jeho chaty a co z nich nevidět nebo vynořit. */
internal data class MergeSource(
    val chats: List<ChatSummary>,
    val hiddenSavedMessagesChatId: Long? = null,
    val due: Set<Long> = emptySet(),
)

/**
 * Skupiny společného seznamu chatů víc účtů (návrh `docs/navrh-vice-uctu.md`, „Společný seznam
 * chatů"), stejné jako [chatGroups] jednoho účtu:
 *
 * - **Sponzorované a vynořené připomínky** nahoře, účet po účtu.
 * - **Připnuté všech účtů** pod nimi, napřed podle pořadí účtů a uvnitř účtu, jak je seřadil
 *   TDLib: co si uživatel připnul, nesmí zmizet jen proto, že se dívá na všechny účty.
 * - **Ostatní podle data poslední zprávy.** `ChatPosition.order` je číslo uvnitř jednoho účtu
 *   a mezi účty se porovnat nedá; chat bez zprávy jde na konec, při shodě rozhoduje `order`.
 * - **Stejný chat ve dvou účtech je dvakrát**: liší se stavem přečtení i historií, sloučit nejde.
 */
internal fun mergedChatGroups(sources: List<MergeSource>): List<List<MergedChat>> {
    val sponsored = mutableListOf<MergedChat>()
    val reminded = mutableListOf<MergedChat>()
    val pinned = mutableListOf<MergedChat>()
    val rest = mutableListOf<MergedChat>()
    sources.forEachIndexed { index, source ->
        chatGroups(source.chats, source.hiddenSavedMessagesChatId, source.due).flatten().forEach { chat ->
            val merged = MergedChat(index, chat)
            when {
                chat.sponsor != null -> sponsored += merged
                chat.id in source.due -> reminded += merged
                chat.isPinned -> pinned += merged
                else -> rest += merged
            }
        }
    }
    rest.sortWith(compareByDescending<MergedChat> { it.chat.lastMessageTimestamp ?: Int.MIN_VALUE }.thenByDescending { it.chat.order })
    return listOf(sponsored, reminded, pinned, rest).filter { it.isNotEmpty() }
}

/**
 * Společný seznam chatů víc účtů (režim Vše): řádky všech [sources] v jednom seznamu, každý
 * s odznakem svého účtu a s akcemi do ViewModelu svého účtu. Skládá se v kontejneru účtu řádku,
 * takže fotky chatů jdou z jeho souborů.
 *
 * Proti seznamu jednoho účtu ([ChatListContent]) tu není výběr víc chatů ani uspořádání
 * připnutých: obojí patří jednomu účtu a obrazovka se na ně přepne do seznamu toho účtu.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MergedChatListContent(
    sources: List<ChatSource>,
    /** Chat otevřený v detailu vedle seznamu, s účtem. */
    selectedChat: Pair<AccountSlot, Long>?,
    onOpen: (AccountSlot, ChatSummary) -> Unit,
    /** Příběhy jen u účtu obrazovky ([storiesSlot]); jinde avatar otevírá chat. */
    onOpenStories: ((Long) -> Unit)?,
    storiesSlot: AccountSlot,
    padding: PaddingValues,
    listState: LazyListState,
    empty: @Composable BoxScope.() -> Unit,
    header: LazyListScope.() -> Unit = {},
) {
    val rowGaps = LocalChatRowGaps.current
    val states = sources.map { source -> key(source.account.slot) { source.viewModel.state.collectAsStateWithLifecycle().value } }
    val contexts = sources.map { source -> key(source.account.slot) { rememberRowsContext(source, rowGaps) } }
    val hidden = sources.map { source -> key(source.account.slot) { source.viewModel.hiddenSavedMessagesChatId.collectAsStateWithLifecycle().value } }

    val ready = states.map { (it as? ChatListState.Ready)?.chats }
    val groups = remember(ready, hidden, contexts.map { it.snoozes.due }) {
        mergedChatGroups(
            ready.mapIndexed { index, chats ->
                MergeSource(chats.orEmpty(), hidden[index], contexts[index].snoozes.due)
            },
        )
    }

    // Dotažení dalších chatů u všech účtů naráz: řazení podle data potřebuje od každého aspoň
    // to, co je novější než konec seznamu.
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= info.totalItemsCount - MERGED_LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(listState, sources) {
        snapshotFlow { shouldLoadMore }.collect { if (it) sources.forEach { source -> source.viewModel.loadMore() } }
    }

    when {
        ready.all { it == null } -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }

        groups.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding), content = empty)

        else -> LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 8.dp,
                start = LIST_CONTENT_INSET,
                end = LIST_CONTENT_INSET,
            ),
            verticalArrangement = Arrangement.spacedBy(if (rowGaps) GROUP_ITEM_SPACING else 0.dp),
        ) {
            header()
            groups.forEachIndexed { groupIndex, group ->
                itemsIndexed(group, key = { _, merged -> merged.key }) { index, merged ->
                    val source = sources[merged.source]
                    val slot = source.account.slot
                    AccountScope(source.account) {
                        ChatListItem(
                            rows = contexts[merged.source],
                            chat = merged.chat,
                            position = GroupPosition(index, group.size, groupIndex),
                            selected = selectedChat == slot to merged.chat.id,
                            onOpen = { chat -> onOpen(slot, chat) },
                            onOpenStories = onOpenStories.takeIf { slot == storiesSlot },
                        )
                    }
                }
            }
        }
    }
}

/** [ChatRowsContext] jednoho účtu společného seznamu, s odznakem účtu. */
@Composable
private fun rememberRowsContext(source: ChatSource, rowGaps: Boolean): ChatRowsContext {
    val viewModel = source.viewModel
    val chatColors by viewModel.chatColors.collectAsStateWithLifecycle()
    return ChatRowsContext(
        viewModel = viewModel,
        selection = null,
        savedMessagesChatId = viewModel.savedMessagesChatId.collectAsStateWithLifecycle().value,
        swipeActions = viewModel.swipeActions.collectAsStateWithLifecycle().value,
        snoozes = viewModel.snoozeState.collectAsStateWithLifecycle().value,
        awaitingReply = viewModel.awaitingReply.collectAsStateWithLifecycle().value,
        storyRings = viewModel.storyRings.collectAsStateWithLifecycle().value,
        chatColors = chatColors,
        chatAccents = rememberChatAccents(chatColors.values.toSet()),
        rowGaps = rowGaps,
        account = source.info,
    )
}

/**
 * Filtr účtů nad seznamem chatů: „Všechny účty" a čip s fotkou každého účtu. Jen s víc účty.
 * Čip účtu ukáže jeho chaty (a udělá ho výchozím), „Všechny účty" společný seznam.
 */
internal fun LazyListScope.accountFilterChips(
    accounts: List<AccountEntry>,
    allAccounts: Boolean,
    shownSlot: AccountSlot,
    onSelectAll: () -> Unit,
    onSelectAccount: (AccountSlot) -> Unit,
) {
    item(key = ACCOUNT_FILTER_KEY, contentType = ACCOUNT_FILTER_KEY) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
        ) {
            FilterChip(
                selected = allAccounts,
                onClick = onSelectAll,
                label = { Text(stringResource(Res.string.accounts_filter_all)) },
            )
            accounts.forEach { entry ->
                FilterChip(
                    selected = !allAccounts && entry.account.slot == shownSlot,
                    onClick = { onSelectAccount(entry.account.slot) },
                    label = { Text(entry.title) },
                    leadingIcon = {
                        AccountScope(entry.account) {
                            Avatar(
                                chatId = entry.info?.id ?: 0L,
                                title = entry.title,
                                avatarFileId = entry.info?.photoFileId,
                                size = FILTER_AVATAR_SIZE,
                            )
                        }
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** Klíč řádku s filtrem účtů v seznamu chatů. */
private const val ACCOUNT_FILTER_KEY = "account-filter"

/** Fotka účtu v čipu filtru — velikost ikony čipu. */
private val FILTER_AVATAR_SIZE = 18.dp

/** Kolik položek před koncem společného seznamu se dotahují další chaty. */
private const val MERGED_LOAD_MORE_THRESHOLD = 5
