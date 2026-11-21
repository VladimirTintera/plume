package eu.tintera.plume.features.profile

import eu.tintera.plume.core.ui.menuScroll
import eu.tintera.plume.core.ui.MenuColors
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatLogEvent
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.isSameDayAs
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.event_log_antispam_report
import eu.tintera.plume.resources.event_log_empty
import eu.tintera.plume.resources.event_log_empty_help
import eu.tintera.plume.resources.event_log_title
import eu.tintera.plume.resources.history_24dp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.CheckableDropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import eu.tintera.plume.core.telegram.ChatLogCategory
import eu.tintera.plume.core.telegram.ChatLogFilter
import eu.tintera.plume.core.telegram.SenderInfo
import eu.tintera.plume.core.ui.MenuPopupProperties
import eu.tintera.plume.core.ui.SearchPill
import eu.tintera.plume.core.ui.horizontalScrollWithWheel
import eu.tintera.plume.resources.chats_search_clear
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.event_log_clear_filters
import eu.tintera.plume.resources.event_log_filter_admins
import eu.tintera.plume.resources.event_log_filter_anyone
import eu.tintera.plume.resources.event_log_filter_authors
import eu.tintera.plume.resources.event_log_filter_choose_author
import eu.tintera.plume.resources.event_log_filter_invites
import eu.tintera.plume.resources.event_log_filter_members
import eu.tintera.plume.resources.event_log_filter_messages
import eu.tintera.plume.resources.event_log_filter_restrictions
import eu.tintera.plume.resources.event_log_filter_settings
import eu.tintera.plume.resources.event_log_filter_topics
import eu.tintera.plume.resources.event_log_filter_video_chats
import eu.tintera.plume.resources.event_log_nothing_found
import eu.tintera.plume.resources.event_log_nothing_found_help
import eu.tintera.plume.resources.event_log_search
import eu.tintera.plume.resources.expand_more_24dp
import eu.tintera.plume.resources.person_24dp
import eu.tintera.plume.resources.search_24dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Poslední akce: po dnech („Dnes", „Včera"), v každém řádku kdo, co a v kolik. Podrobnost
 * (text smazané zprávy, nový popis, odkaz) je pod popisem na dva řádky — celá patří
 * do konverzace, sem jen tolik, aby se poznalo, o co šlo.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EventLogScreen(viewModel: EventLogViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notice = viewModel.notice
    val noticeText = notice?.resolve()
    // Snackbar mimo tělo efektu: vynulování hlášky mění klíč a zrušilo by ho (viz „Vrátit").
    LaunchedEffect(notice) {
        if (noticeText != null) {
            viewModel.notice = null
            scope.launch { snackbarHostState.showSnackbar(noticeText) }
        }
    }
    val events = viewModel.events

    val subpageScroll = profileSubpageScrollBehavior()

    Scaffold(
        modifier = modifier.profileSubpageScroll(subpageScroll),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ProfileSubpageTopBar(
                title = stringResource(Res.string.event_log_title),
                subtitle = rememberChatTitle(viewModel.chatId),
                onBack = onBack,
                scrollBehavior = subpageScroll,
            )
        },
    ) { padding ->
        // „Zrušit filtry" vymaže i hledaný text; ten drží pole samo, proto se založí znovu.
        var searchGeneration by remember { mutableIntStateOf(0) }
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            key(searchGeneration) {
                SearchPill(
                    initialQuery = viewModel.filter.query,
                    onQueryChange = viewModel::setQuery,
                    placeholder = stringResource(Res.string.event_log_search),
                    clearDescription = stringResource(Res.string.chats_search_clear),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            EventLogFilters(
                filter = viewModel.filter,
                admins = viewModel.admins,
                onToggleCategory = viewModel::toggleCategory,
                onToggleAuthor = viewModel::toggleAuthor,
                onAnyAuthor = viewModel::clearAuthors,
            )
            val bottom = PaddingValues(bottom = padding.calculateBottomPadding())
            when {
                events == null -> Box(Modifier.fillMaxSize().padding(bottom), contentAlignment = Alignment.Center) {
                    if (!viewModel.loadFailed) LoadingIndicator()
                }
                // Prázdno kvůli filtru je jiná zpráva než prázdný záznam: radí, co s tím.
                events.isEmpty() && viewModel.filter.isActive -> IntroEmptyState(
                    icon = Res.drawable.search_24dp,
                    title = stringResource(Res.string.event_log_nothing_found),
                    text = stringResource(Res.string.event_log_nothing_found_help),
                    modifier = Modifier.fillMaxSize().padding(bottom),
                    action = {
                        TextButton(onClick = {
                            viewModel.clearFilters()
                            searchGeneration++
                        }, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.event_log_clear_filters)) }
                    },
                )
                events.isEmpty() -> IntroEmptyState(
                    icon = Res.drawable.history_24dp,
                    title = stringResource(Res.string.event_log_empty),
                    text = stringResource(Res.string.event_log_empty_help),
                    modifier = Modifier.fillMaxSize().padding(bottom),
                )
                else -> EventList(events, viewModel, bottom)
            }
        }
    }
}

/**
 * Filtry záznamu: kdo (správce) a druh akce. Řádek jde posouvat do strany, na telefon se
 * všechny čipy nevejdou.
 *
 * - **Druhy jsou `FilterChip`**, ne connected button group jako druh chatu v hledání zpráv:
 *   tam se vybírá nejvýš jedna volba ze tří, tady libovolně mnoho z osmi — a to je přesně
 *   úloha filtračního čipu v M3. Vybraný má fajfku, takže stav nese i tvar, ne jen barva.
 * - **Kdo je čip s nabídkou** (šipka), protože správců bývá víc, než by se vešlo do řádku.
 *   Nabídka se po výběru NEZAVÍRÁ — vybírá se víc lidí naráz; „Kdokoli" výběr zruší a zavře ji.
 *   Bez načtených správců se čip neukazuje (nebylo by co nabídnout).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EventLogFilters(
    filter: ChatLogFilter,
    admins: List<SenderInfo>,
    onToggleCategory: (ChatLogCategory) -> Unit,
    onToggleAuthor: (Long) -> Unit,
    onAnyAuthor: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().horizontalScrollWithWheel().padding(horizontal = 16.dp),
    ) {
        if (admins.isNotEmpty()) AuthorChip(filter, admins, onToggleAuthor, onAnyAuthor)
        CATEGORIES.forEach { (category, label) ->
            val selected = category in filter.categories
            FilterChip(
                selected = selected,
                onClick = { onToggleCategory(category) },
                label = { Text(stringResource(label)) },
                leadingIcon = if (selected) {
                    { Icon(painterResource(Res.drawable.check_24dp), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
                shapes = FilterChipDefaults.shapes(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AuthorChip(
    filter: ChatLogFilter,
    admins: List<SenderInfo>,
    onToggleAuthor: (Long) -> Unit,
    onAnyAuthor: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val chosen = filter.userIds
    val rotation by animateFloatAsState(if (menu) 180f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "author-chevron")
    Box {
        FilterChip(
            selected = chosen.isNotEmpty(),
            onClick = { menu = true },
            label = {
                Text(
                    when (chosen.size) {
                        0 -> stringResource(Res.string.event_log_filter_anyone)
                        // Jeden člověk jménem; vybraný mezitím přestat být správcem nemusí být v seznamu.
                        1 -> admins.firstOrNull { it.id in chosen }?.title
                            ?: pluralStringResource(Res.plurals.event_log_filter_authors, 1, 1)
                        else -> pluralStringResource(Res.plurals.event_log_filter_authors, chosen.size, chosen.size)
                    },
                    maxLines = 1,
                )
            },
            leadingIcon = {
                Icon(
                    painterResource(if (chosen.isEmpty()) Res.drawable.person_24dp else Res.drawable.check_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
            trailingIcon = {
                Icon(
                    painterResource(Res.drawable.expand_more_24dp),
                    contentDescription = stringResource(Res.string.event_log_filter_choose_author),
                    modifier = Modifier.size(FilterChipDefaults.IconSize).graphicsLayer { rotationZ = rotation },
                )
            },
            shapes = FilterChipDefaults.shapes(),
        )
        DropdownMenuPopup(modifier = Modifier.menuScroll(), expanded = menu, onDismissRequest = { menu = false }, properties = MenuPopupProperties) {
            MenuColors {
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 2)) {
                    CheckableDropdownMenuItem(
                        checked = chosen.isEmpty(),
                        onCheckedChange = {
                            menu = false
                            onAnyAuthor()
                        },
                        text = { Text(stringResource(Res.string.event_log_filter_anyone)) },
                        shapes = MenuDefaults.itemShape(0, 1),
                        checkedLeadingIcon = { Icon(painterResource(Res.drawable.check_24dp), contentDescription = null) },
                    )
                }
                Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(1, 2)) {
                    admins.forEachIndexed { index, admin ->
                        CheckableDropdownMenuItem(
                            checked = admin.id in chosen,
                            onCheckedChange = { onToggleAuthor(admin.id) },
                            text = { Text(admin.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            shapes = MenuDefaults.itemShape(index, admins.size),
                            // Fotka říká kdo, fajfka na jejím místě, že je vybraný.
                            leadingIcon = { Avatar(chatId = admin.id, title = admin.title, avatarFileId = admin.avatarFileId, size = 24.dp) },
                            checkedLeadingIcon = { Icon(painterResource(Res.drawable.check_24dp), contentDescription = null) },
                        )
                    }
                }
            }
        }
    }
}

private val CATEGORIES: List<Pair<ChatLogCategory, StringResource>> = listOf(
    ChatLogCategory.Messages to Res.string.event_log_filter_messages,
    ChatLogCategory.Members to Res.string.event_log_filter_members,
    ChatLogCategory.Admins to Res.string.event_log_filter_admins,
    ChatLogCategory.Restrictions to Res.string.event_log_filter_restrictions,
    ChatLogCategory.Settings to Res.string.event_log_filter_settings,
    ChatLogCategory.InviteLinks to Res.string.event_log_filter_invites,
    ChatLogCategory.VideoChats to Res.string.event_log_filter_video_chats,
    ChatLogCategory.Topics to Res.string.event_log_filter_topics,
)

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun EventList(events: List<ChatLogEvent>, viewModel: EventLogViewModel, padding: PaddingValues) {
    // Dny jako bloky: řádky jednoho dne mají společné zaoblení, nad blokem jeho název.
    val days = remember(events) { events.groupConsecutive { a, b -> a.date.isSameDayAs(b.date) } }
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
        modifier = Modifier.fillMaxSize(),
    ) {
        days.forEach { day ->
            item(key = "day-${day.first().id}") {
                Text(
                    day.first().date.asDayLabel(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 4.dp).semantics { heading() },
                )
            }
            itemsIndexed(day, key = { _, event -> event.id }) { index, event ->
                if (event.id == events.last().id) LaunchedEffect(event.id) { viewModel.loadMore() }
                EventRow(
                    event = event,
                    reportingAntiSpam = event.id in viewModel.reportingAntiSpam,
                    onReportAntiSpam = { viewModel.reportAntiSpamMistake(event) },
                    modifier = Modifier.animateItem().clip(groupItemShape(index, day.size)),
                )
            }
        }
        if (viewModel.loadingMore) {
            item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun EventRow(
    event: ChatLogEvent,
    reportingAntiSpam: Boolean,
    onReportAntiSpam: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val description = event.description.resolve()
    // Smazaný účet jméno nemá; popis pak nese řádek sám.
    val actor = event.actor.title.ifBlank { null }
    ListItem(
        supportingContent = {
            Column {
                if (actor != null) Text(description, maxLines = 2, overflow = TextOverflow.Ellipsis)
                event.detail?.let { detail ->
                    Text(
                        detail.resolve(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Zprávu smazal antispam a správce ji může označit za omyl. Tlačítko v řádku, ne
                // v nabídce: jiná akce nad událostí není, nabídka s jedinou položkou by ji jen schovala.
                if (event.antiSpamMessageId != null) {
                    OutlinedButton(
                        onClick = onReportAntiSpam,
                        enabled = !reportingAntiSpam,
                        shapes = ButtonDefaults.shapesFor(ButtonDefaults.ExtraSmallContainerHeight),
                        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.ExtraSmallContainerHeight),
                        modifier = Modifier.padding(top = 8.dp).heightIn(min = ButtonDefaults.ExtraSmallContainerHeight),
                    ) {
                        Text(stringResource(Res.string.event_log_antispam_report))
                    }
                }
            }
        },
        leadingContent = {
            Avatar(chatId = event.actor.id, title = event.actor.title, avatarFileId = event.actor.avatarFileId)
        },
        trailingContent = { Text(event.date.asTimeOfDay(), style = MaterialTheme.typography.labelMedium.tabularNumbers()) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.groupRow),
        modifier = modifier,
    ) { Text(actor ?: description, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** Souvislé úseky seznamu, ve kterých [same] platí pro sousedy — dny v seznamu od nejnovějšího. */
internal fun <T> List<T>.groupConsecutive(same: (T, T) -> Boolean): List<List<T>> {
    val groups = mutableListOf<MutableList<T>>()
    forEach { item ->
        val last = groups.lastOrNull()
        if (last != null && same(last.last(), item)) last += item else groups += mutableListOf(item)
    }
    return groups
}
