package eu.tintera.plume.features.chats

import eu.tintera.plume.core.accounts.BadgeAccount
import org.koin.core.parameter.parametersOf
import androidx.compose.material3.SnackbarHostState
import eu.tintera.plume.core.telegram.ChatFolder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.accounts.AccountScope
import eu.tintera.plume.core.ui.ReportDrawnWhen
import eu.tintera.plume.core.accounts.AccountSession
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.accounts.isSharedHistoryChat
import eu.tintera.plume.core.accounts.SharedChats
import org.koin.compose.koinInject
import eu.tintera.plume.core.settings.ChatSwipeActions
import eu.tintera.plume.core.settings.ThemeColor
import eu.tintera.plume.core.chatlabels.ChatLabel
import eu.tintera.plume.core.snooze.ChatSnoozeState
import eu.tintera.plume.core.telegram.AccountInfo
import eu.tintera.plume.core.telegram.ActiveStories
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.adaptive.LIST_CONTENT_INSET
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.LocalChatRowGaps
import eu.tintera.plume.resources.Res
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.annotation.KoinInternalApi

/**
 * Co patří účtu řádku seznamu chatů ([ChatListItem]): jeho ViewModel (akce nad chatem jdou do
 * něj), výběr, odložení, barvy a příběhy. Ve společném seznamu má každý účet vlastní.
 */
@Immutable
internal data class ChatRowsContext(
    val viewModel: ChatsViewModel,
    /** Sheety nad chaty [viewModel]: podržení řádku otevře nabídku, tah odložení. */
    val sheets: ChatSheets,
    val selection: ChatSelection?,
    val savedMessagesChatId: Long?,
    val swipeActions: ChatSwipeActions,
    val snoozes: ChatSnoozeState,
    val awaitingReply: Map<Long, Long>,
    val storyRings: Map<Long, ActiveStories>,
    val chatColors: Map<Long, ThemeColor>,
    val chatAccents: Map<ThemeColor, ColorScheme>,
    val rowGaps: Boolean,
    /** Chat → jeho vlastní štítky (`ChatLabels`). */
    val labels: Map<Long, List<ChatLabel>> = emptyMap(),
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
    /** Otevřené sheety nad chaty tohohle účtu; v kompozici, ne ve [viewModel] (viz [ChatSheets]). */
    val sheets: ChatSheets,
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
internal fun rememberChatSources(accounts: List<AccountEntry>, folder: ChatFolder = ChatFolder.Main): List<ChatSource> =
    accounts.map { entry ->
        key(entry.account.slot) {
            ChatSource(
                account = entry.account,
                viewModel = koinViewModel(
                    key = "chats-all-${folder.key}-${entry.account.slot.id}",
                    scope = entry.account.koin.scopeRegistry.rootScope,
                    parameters = { parametersOf(folder) },
                ),
                info = entry.info,
                sheets = remember { ChatSheets() },
            )
        }
    }

/** Část klíče ViewModelu podle složky: hlavní seznam a archiv mají každý vlastní. */
private val ChatFolder.key: String get() = if (this == ChatFolder.Archive) "archive" else "main"

/**
 * Seznam víc účtů (režim Všechny účty) pro obrazovku seznamu [folder]: zdroje za každý účet,
 * nebo prázdno (jeden účet, režim vypnutý). [busy] je účet, ve kterém běží výběr víc chatů
 * nebo uspořádání připnutých — patří jednomu účtu, takže seznam po tu dobu ukazuje jen jeho.
 */
@Immutable
internal data class MergedChats(
    val sources: List<ChatSource>,
    val busy: ChatSource?,
    /** Účty s fotkami a jmény, jak je ukazuje menu účtu. */
    val entries: List<AccountEntry>,
) {
    /** Kreslí se společný seznam, ne seznam jednoho účtu. */
    val merged: Boolean get() = sources.isNotEmpty() && busy == null
}

/**
 * Účty režimu Všechny účty, jak je ukazuje menu účtu; prázdné, když je účet jeden nebo režim
 * vypnutý. Podle toho se slučuje seznam chatů, archiv, historie hovorů a stahování.
 */
@Composable
internal fun rememberAllAccountsEntries(): List<AccountEntry> =
    rememberAccountSwitcher().let { if (it.allAccounts) it.accounts else emptyList() }

@Composable
internal fun rememberMergedChats(folder: ChatFolder): MergedChats {
    val switcher = rememberAccountSwitcher()
    val entries = switcher.accounts
    val sources = if (switcher.allAccounts) rememberChatSources(entries, folder) else emptyList()
    val busy = sources
        .map { source ->
            key(source.account.slot) {
                val selecting = source.viewModel.selection.collectAsStateWithLifecycle().value != null
                val reordering = source.viewModel.reorder.collectAsStateWithLifecycle().value != null
                source.takeIf { selecting || reordering }
            }
        }
        .firstNotNullOfOrNull { it }
    return MergedChats(sources, busy, entries)
}

/**
 * Nabídka nad chatem a hlášky („Vrátit") pro řádky společného seznamu: každý účet má vlastní
 * ViewModel, a tedy i vlastní nabídku. Archivace hlásí slot účtu, ať se zavře konverzace toho
 * účtu, ne stejného id v jiném.
 */
@Composable
internal fun MergedChatActionsHosts(
    sources: List<ChatSource>,
    snackbarHostState: SnackbarHostState,
    onArchived: ((AccountSlot, ArchiveChange) -> Unit)?,
    onOpenProxySettings: () -> Unit = {},
) {
    sources.forEach { source ->
        key(source.account.slot) {
            AccountScope(source.account) {
                ChatActionsHost(
                    source.viewModel,
                    source.sheets,
                    snackbarHostState,
                    onArchived = onArchived?.let { archived -> { change -> archived(source.account.slot, change) } },
                    onOpenProxySettings = onOpenProxySettings,
                )
            }
        }
    }
}

/**
 * Chat ve společném seznamu: [source] je pořadí účtu v seznamu účtů, ve kterém se řádek otevře
 * a kterému patří jeho akce. Klíč položky nese účet, protože totéž id má chat ve dvou účtech
 * (společná skupina, soukromý chat s týmž člověkem).
 *
 * [others]: spojený řádek ([mergedChatGroups]) — tentýž chat v ostatních účtech, s id chatu
 * v nich (Uložené zprávy mají v každém účtu jiné). Řádek pak nese odznaky všech a klíč bez
 * účtu, ať se nepřeskládá, když se změní účet, kterým se otevírá.
 */
internal data class MergedChat(
    val source: Int,
    val chat: ChatSummary,
    val others: List<MergedInstance> = emptyList(),
    /** Čím se výskyty poznaly jako tentýž chat ([mergeKey]); `null` u řádku jednoho účtu. */
    val mergeKey: String? = null,
) {
    val key: String get() = mergeKey?.let { "$SHARED_ROW_PREFIX$it" } ?: "$source:${chat.id}"

    /** Účty řádku: ten, kterým se otevře, a pak ostatní. */
    val sources: List<Int> get() = listOf(source) + others.map { it.source }

    /** Výskyty řádku (účet a id chatu v něm), v pořadí [sources]. */
    val instances: List<MergedInstance> get() = listOf(MergedInstance(source, chat.id)) + others
}

/**
 * Je [key] klíč řádku společného seznamu ([MergedChat.key])? Hlavičky a karty nad seznamem mají
 * vlastní klíče. Tvar klíče zná jen tenhle soubor; kotva sbalení seznamu (`firstVisibleChatKey`)
 * se ptá tady. Dřív si tvar „zdroj:id" opisovala sama, a když přibyly spojené řádky
 * (`shared:…`), přeskakovala je — sloupec po sbalení dojel až na první nespojený chat.
 */
internal fun isMergedChatKey(key: String): Boolean = key.startsWith(SHARED_ROW_PREFIX) || key.matches(SINGLE_ROW_KEY)

private const val SHARED_ROW_PREFIX = "shared:"
private val SINGLE_ROW_KEY = Regex("""\d+:-?\d+""")

/** Výskyt spojeného chatu: pořadí účtu a id chatu v tom účtu. */
internal data class MergedInstance(val source: Int, val chatId: Long)

/** Vstup [mergedChatGroups] za jeden účet: jeho chaty a co z nich nevidět nebo vynořit. */
internal data class MergeSource(
    val chats: List<ChatSummary>,
    val hiddenSavedMessagesChatId: Long? = null,
    val due: Set<Long> = emptySet(),
    /**
     * Id Uložených zpráv účtu, tedy jeho vlastní user id, i když se v seznamu neukazují.
     * Podle něj se spojí Uložené zprávy účtů a pozná chat s jiným vlastním účtem.
     */
    val savedMessagesChatId: Long? = null,
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
 * - **Chat, který má víc účtů, je jednou** ([mergeKey]; kroky 24 a 25 návrhu). Stojí tam,
 *   kam ho posílá nejvyšší z jeho výskytů (připnutý v jednom účtu je mezi připnutými).
 *   - **Společná skupina nebo kanál** má v obou účtech tutéž historii: otevře se účtem
 *     z [preferred] (kterým se do ní psalo naposledy), jinak tím z nejvyššího výskytu.
 *     Nepřečtené nejvyšší z účtů — jsou to tytéž zprávy, součet by je počítal dvakrát.
 *   - **Soukromý chat a Uložené zprávy** mají v každém účtu jinou historii: řádek ukazuje
 *     a otevírá výskyt s nepřečtenými, jinak ten s nejnovější zprávou (při shodě [preferred]).
 *     Nepřečtené se sčítají — každý účet má jiné zprávy.
 *   - **Uložené zprávy** se spojí i s těmi, které druhý účet v seznamu neukazuje (nepřipnuté):
 *     stačí, že je vidět jeden výskyt.
 * - **Obyčejná skupina ve dvou účtech je dvakrát**: má v každém účtu jiná id zpráv a řádek by
 *   nevěděl, které nepřečtené ukázat. Chat s jiným vlastním účtem taky — v tom druhém je totéž
 *   id jeho Uložených zpráv.
 *
 * [preferred]: id chatu → pořadí účtu, kterým ho otevřít.
 */
internal fun mergedChatGroups(sources: List<MergeSource>, preferred: Map<Long, Int> = emptyMap()): List<List<MergedChat>> {
    class Entry(val source: Int, val chat: ChatSummary, val rank: Int, val key: String?)
    val ownIds = sources.mapNotNull { it.savedMessagesChatId }.toSet()
    // Dokud některý účet nezná své id, nejde chat s vlastním účtem poznat: soukromé se nespojují.
    val ownIdsKnown = sources.all { it.savedMessagesChatId != null }
    val entries = buildList {
        sources.forEachIndexed { index, source ->
            chatGroups(source.chats, source.hiddenSavedMessagesChatId, source.due).flatten().forEach { chat ->
                val rank = when {
                    chat.sponsor != null -> RANK_SPONSORED
                    chat.id in source.due -> RANK_REMINDED
                    chat.isPinned -> RANK_PINNED
                    else -> RANK_REST
                }
                add(Entry(index, chat, rank, mergeKey(chat, source.savedMessagesChatId, ownIds, ownIdsKnown)))
            }
        }
    }
    val instancesByKey = entries.filter { it.key != null }.groupBy { it.key!! }.toMutableMap()
    // Uložené zprávy, které účet neukazuje (nepřipnuté), se přidají k viditelným z jiného účtu.
    instancesByKey[SAVED_MESSAGES_KEY]?.let { visible ->
        val hidden = sources.mapIndexedNotNull { index, source ->
            if (visible.any { it.source == index }) return@mapIndexedNotNull null
            val id = source.savedMessagesChatId ?: return@mapIndexedNotNull null
            source.chats.firstOrNull { it.id == id }?.let { Entry(index, it, RANK_HIDDEN, SAVED_MESSAGES_KEY) }
        }
        instancesByKey[SAVED_MESSAGES_KEY] = (visible + hidden).sortedBy { it.source }
    }
    val shared = instancesByKey.filterValues { it.size > 1 }

    val buckets = List(RANK_REST + 1) { mutableListOf<MergedChat>() }
    entries.forEach { entry ->
        val instances = entry.key?.let(shared::get)
        if (instances == null) {
            buckets[entry.rank] += MergedChat(entry.source, entry.chat)
            return@forEach
        }
        // Řádek stojí jen na místě nejvyššího výskytu, ostatní výskyty vypadnou.
        val best = instances.minBy { it.rank }
        if (entry !== best) return@forEach
        val sharedHistory = isSharedHistoryChat(entry.chat.id)
        val favourite = instances.firstOrNull { it.source == preferred[it.chat.id] }
        val opener = if (sharedHistory) {
            favourite ?: best
        } else {
            val candidates = instances.filter { it.chat.hasUnread }.ifEmpty { instances }
            val newest = candidates.maxOf { it.chat.lastMessageTimestamp ?: Int.MIN_VALUE }
            val latest = candidates.filter { (it.chat.lastMessageTimestamp ?: Int.MIN_VALUE) == newest }
            latest.firstOrNull { it === favourite } ?: latest.first()
        }
        fun count(of: (ChatSummary) -> Int) = instances.map { of(it.chat) }.let { if (sharedHistory) it.max() else it.sum() }
        val chat = opener.chat.copy(
            unreadCount = count { it.unreadCount },
            unreadMentionCount = count { it.unreadMentionCount },
            unreadReactionCount = count { it.unreadReactionCount },
            unreadPollVoteCount = count { it.unreadPollVoteCount },
            isMarkedAsUnread = instances.any { it.chat.isMarkedAsUnread },
            // Připínáček podle místa řádku, ne podle účtu, kterým se otevře.
            isPinned = best.chat.isPinned,
        )
        val others = instances.filter { it !== opener }.map { MergedInstance(it.source, it.chat.id) }
        buckets[entry.rank] += MergedChat(opener.source, chat, others, entry.key)
    }
    buckets[RANK_REST].sortWith(
        compareByDescending<MergedChat> { it.chat.lastMessageTimestamp ?: Int.MIN_VALUE }.thenByDescending { it.chat.order },
    )
    return buckets.filter { it.isNotEmpty() }
}

/** Má chat co číst: nepřečtené zprávy, nebo ruční příznak. */
private val ChatSummary.hasUnread: Boolean get() = unreadCount > 0 || isMarkedAsUnread

/**
 * Podle čeho se výskyt chatu spojí s výskytem v jiném účtu ([mergedChatGroups]); `null` = nespojuje
 * se. Uložené zprávy mají v každém účtu jiné id, proto společný klíč; ostatní spojuje id chatu.
 *
 * - Ne reklama, tajný chat (id jen v jednom účtu) a přímé zprávy kanálu (správce je čte po
 *   vláknech odběratelů, odběratel má vlastní vlákno).
 * - Supergroupa a kanál ([isSharedHistoryChat]) a soukromý chat (kladné id, i bot) ano.
 * - Chat s jiným vlastním účtem ([ownIds]) ne — v něm je to id jeho Uložených zpráv.
 */
internal fun mergeKey(chat: ChatSummary, savedMessagesChatId: Long?, ownIds: Set<Long>, ownIdsKnown: Boolean): String? = when {
    chat.sponsor != null || chat.isSecret || chat.isDirectMessages -> null
    savedMessagesChatId != null && chat.id == savedMessagesChatId -> SAVED_MESSAGES_KEY
    isSharedHistoryChat(chat.id) -> chat.id.toString()
    ownIdsKnown && chat.id > 0 && chat.id !in ownIds -> chat.id.toString()
    else -> null
}

private const val SAVED_MESSAGES_KEY = "saved"

/** Uložené zprávy, které účet v seznamu neukazuje: přidají se jen ke spojenému řádku. */
private const val RANK_HIDDEN = Int.MAX_VALUE

private const val RANK_SPONSORED = 0
private const val RANK_REMINDED = 1
private const val RANK_PINNED = 2
private const val RANK_REST = 3

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
    /** Kolik položek přidá [header]; dojetí za chatem otevřeným zkratkou počítá index za nimi. */
    headerItemCount: Int = 0,
    header: LazyListScope.() -> Unit = {},
) {
    val rowGaps = LocalChatRowGaps.current
    val states = sources.map { source -> key(source.account.slot) { source.viewModel.state.collectAsStateWithLifecycle().value } }
    val contexts = sources.map { source -> key(source.account.slot) { rememberRowsContext(source, rowGaps) } }
    val hidden = sources.map { source -> key(source.account.slot) { source.viewModel.hiddenSavedMessagesChatId.collectAsStateWithLifecycle().value } }

    val ready = states.map { (it as? ChatListState.Ready)?.chats }
    val preferred = rememberPreferredSources(sources)
    // Start je hotový, jakmile aspoň jeden účet opustí načítání (chaty, prázdno nebo chyba), jako
    // u seznamu jednoho účtu (`ChatListContent`). Bez toho se v režimu Všechny účty „Fully drawn"
    // neohlásil vůbec (Play vitals, měření startu).
    ReportDrawnWhen { states.any { it !is ChatListState.Loading } }
    val groups = remember(ready, hidden, contexts.map { it.snoozes.due }, contexts.map { it.savedMessagesChatId }, preferred) {
        mergedChatGroups(
            ready.mapIndexed { index, chats ->
                MergeSource(chats.orEmpty(), hidden[index], contexts[index].snoozes.due, contexts[index].savedMessagesChatId)
            },
            preferred,
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

    // Alt+↓ / Alt+↑ v pořadí společného seznamu; chat se otevře v účtu řádku a seznam za ním
    // dojede, není-li celý vidět (jako v seznamu jednoho účtu).
    var shortcutTarget by remember { mutableStateOf<String?>(null) }
    ChatSwitchShortcuts(
        enabled = true,
        chats = groups,
        key = { sources[it.source].account.slot to it.chat.id },
        selected = selectedChat,
        sponsored = { it.chat.sponsor != null },
        onOpen = { merged ->
            shortcutTarget = merged.key
            onOpen(sources[merged.source].account.slot, merged.chat)
        },
    )
    LaunchedEffect(shortcutTarget, groups) {
        val target = shortcutTarget ?: return@LaunchedEffect
        val position = groups.flatten().indexOfFirst { it.key == target }
        if (position >= 0) {
            val info = listState.layoutInfo
            val fullyVisible = info.visibleItemsInfo.any {
                it.key == target && it.offset >= info.viewportStartOffset + info.beforeContentPadding &&
                    it.offset + it.size <= info.viewportEndOffset - info.afterContentPadding
            }
            if (!fullyVisible) listState.animateScrollToItem(headerItemCount + position)
        }
        shortcutTarget = null
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
                            selected = merged.isOpen(sources, selectedChat),
                            onOpen = { chat -> onOpen(slot, chat) },
                            onOpenStories = onOpenStories.takeIf { slot == storiesSlot },
                            accounts = merged.accounts(sources),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Kterým účtem otevírat spojené společné skupiny ([SharedChats.preferred]), jako pořadí účtu
 * v [sources] pro [mergedChatGroups].
 */
@Composable
internal fun rememberPreferredSources(sources: List<ChatSource>): Map<Long, Int> {
    val preferred by koinInject<SharedChats>().preferred.collectAsStateWithLifecycle()
    return remember(preferred, sources.map { it.account.slot }) {
        preferred.mapNotNull { (chatId, slot) ->
            sources.indexOfFirst { it.account.slot == slot }.takeIf { it >= 0 }?.let { chatId to it }
        }.toMap()
    }
}

/** Je řádek otevřený v detailu? U spojeného řádku v kterémkoli z jeho účtů. */
internal fun MergedChat.isOpen(sources: List<ChatSource>, selected: Pair<AccountSlot, Long>?): Boolean =
    selected != null && instances.any { sources[it.source].account.slot == selected.first && it.chatId == selected.second }

/** Odznaky účtů spojeného řádku; `null` = řádek jednoho účtu, odznak dá jeho [ChatRowsContext]. */
internal fun MergedChat.accounts(sources: List<ChatSource>): List<BadgeAccount>? =
    if (others.isEmpty()) null else this.sources.mapNotNull { index -> sources[index].let { s -> s.info?.let { BadgeAccount(it, s.account) } } }

/** [ChatRowsContext] jednoho účtu společného seznamu, s odznakem účtu. */
@Composable
private fun rememberRowsContext(source: ChatSource, rowGaps: Boolean): ChatRowsContext {
    val viewModel = source.viewModel
    val chatColors by viewModel.chatColors.collectAsStateWithLifecycle()
    return ChatRowsContext(
        viewModel = viewModel,
        sheets = source.sheets,
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
        labels = viewModel.chatLabels.collectAsStateWithLifecycle().value,
    )
}

/** Kolik položek před koncem společného seznamu se dotahují další chaty. */
private const val MERGED_LOAD_MORE_THRESHOLD = 5
