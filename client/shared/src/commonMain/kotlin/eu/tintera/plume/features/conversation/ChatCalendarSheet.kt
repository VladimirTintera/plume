package eu.tintera.plume.features.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.keyboard_arrow_up_24dp
import eu.tintera.plume.resources.common_close
import androidx.compose.foundation.clickable
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.MutableState
import eu.tintera.plume.core.ui.standaloneName
import eu.tintera.plume.resources.apps_24dp
import eu.tintera.plume.resources.chat_calendar_year_view
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.TextButton
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.style.TextOverflow
import eu.tintera.plume.core.telegram.calendarMonthsOf
import eu.tintera.plume.core.ui.SearchPill
import eu.tintera.plume.resources.chat_calendar_member
import eu.tintera.plume.resources.chat_calendar_search
import eu.tintera.plume.resources.chat_calendar_search_empty
import eu.tintera.plume.resources.chat_calendar_search_hint
import eu.tintera.plume.resources.chat_calendar_stats_average
import eu.tintera.plume.resources.chat_calendar_stats_busiest
import eu.tintera.plume.resources.chat_calendar_stats_streak
import eu.tintera.plume.resources.chat_calendar_today
import eu.tintera.plume.resources.conversation_search_clear
import eu.tintera.plume.resources.person_search_24dp
import eu.tintera.plume.resources.search_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import eu.tintera.plume.core.telegram.CalendarDayMedia
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.contextClickable
import eu.tintera.plume.resources.arrow_forward_24dp
import eu.tintera.plume.resources.chat_calendar_day_actions
import eu.tintera.plume.resources.chat_calendar_day_clear
import eu.tintera.plume.resources.chat_calendar_day_go
import eu.tintera.plume.resources.chat_calendar_day_summarize
import eu.tintera.plume.resources.chat_calendar_legend_all
import eu.tintera.plume.resources.chat_calendar_legend_hold
import eu.tintera.plume.resources.delete_sweep_24dp
import eu.tintera.plume.resources.summarize_24dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.adaptive.ContentWidth
import eu.tintera.plume.core.telegram.CalendarMonth
import eu.tintera.plume.core.telegram.SharedMediaKind
import eu.tintera.plume.core.telegram.calendarMonth
import eu.tintera.plume.core.telegram.monthsBetween
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.core.ui.monthTitle
import eu.tintera.plume.core.ui.name
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.shortName
import eu.tintera.plume.core.ui.strongestAccentOn
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chat_calendar_all
import eu.tintera.plume.resources.chat_calendar_at_least
import eu.tintera.plume.resources.chat_calendar_count_thousands
import eu.tintera.plume.resources.chat_calendar_day_unknown
import eu.tintera.plume.resources.chat_calendar_failed
import eu.tintera.plume.resources.chat_calendar_kind_empty
import eu.tintera.plume.resources.chat_calendar_load_older
import eu.tintera.plume.resources.chat_calendar_messages
import eu.tintera.plume.resources.chat_calendar_month_empty
import eu.tintera.plume.resources.chat_calendar_older_missing
import eu.tintera.plume.resources.chat_calendar_start
import eu.tintera.plume.resources.chat_calendar_title
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.conversation_jump_to_date
import eu.tintera.plume.resources.date_day_month
import eu.tintera.plume.resources.decimal_number
import eu.tintera.plume.resources.edit_calendar_24dp
import eu.tintera.plume.resources.profile_calendar_day
import eu.tintera.plume.resources.profile_tab_files
import eu.tintera.plume.resources.profile_tab_links
import eu.tintera.plume.resources.profile_tab_media
import eu.tintera.plume.resources.profile_tab_music
import eu.tintera.plume.resources.profile_tab_voice
import kotlin.math.ceil
import kotlin.time.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.yearMonth
import kotlin.time.Instant
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Kalendář konverzace z ⋮: měsíce s počtem zpráv u každého dne, sytější barva = víc zpráv.
 * Ťuknutí na den skočí na jeho první zprávu (u druhu a hledání na první zprávu toho dne, která
 * sedí). Čipy přepínají mezi všemi zprávami, hledáním (výraz, člen skupiny) a druhy obsahu
 * (fotky a videa, soubory, odkazy, hlasovky, hudba); vzdálené datum jde zadat klasickým
 * kalendářem z ikony v hlavičce, „Dnes" vrátí na začátek.
 *
 * Všechny zprávy se počítají z toho, co je v zařízení, a kalendář končí tam, kam sahají — dál
 * nabízí „Načíst starší" (viz [ConversationCalendar]). Druhy a hledání počítá server celé
 * a měsíce bez nich se vynechávají.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatCalendarSheet(
    state: ChatCalendarState,
    calendar: ConversationCalendar,
    /** Den, na kterém se kalendář otevře (sekundy epochy; viditelný den konverzace); `null` = dnes. */
    initialDay: Int?,
    onJumpToDay: (dayStart: Int) -> Unit,
    onJumpToMessage: (messageId: Long) -> Unit,
    /** „Shrnout den" z podržení dne; `null`, když shrnutí v zařízení není. */
    onSummarizeDay: ((LocalDate) -> Unit)?,
    /** „Vymazat historii za den…"; `null`, kde po dnech mazat nejde. */
    onClearDay: ((LocalDate) -> Unit)?,
    /** Hledat podle člena jde jen ve skupině — v soukromém chatu jsou odesílatelé dva, v kanálu jeden. */
    canFilterBySender: Boolean,
) {
    ModalBottomSheet(onDismissRequest = calendar::close, sheetState = rememberExpandedSheetState()) {
        ChatCalendarContent(state, calendar, initialDay, onJumpToDay, onJumpToMessage, onSummarizeDay, onClearDay, canFilterBySender, onClose = null)
    }
}

/**
 * Kalendář jako panel vedle konverzace (široká plocha, `ChatCalendarRoute`): tentýž obsah jako
 * sheet, jen s ✕ v hlavičce. Ťuknutí na den chat vedle posune a panel zůstane otevřený — kvůli
 * tomu panel je: listovat dny a dívat se, co se v nich psalo, bez zavírání a otevírání.
 * Podklad v barvě sheetu, ať sytost dnů vychází stejně jako v něm.
 */
@Composable
internal fun ChatCalendarPane(
    state: ChatCalendarState,
    calendar: ConversationCalendar,
    initialDay: Int?,
    onJumpToDay: (dayStart: Int) -> Unit,
    onJumpToMessage: (messageId: Long) -> Unit,
    onSummarizeDay: ((LocalDate) -> Unit)?,
    onClearDay: ((LocalDate) -> Unit)?,
    canFilterBySender: Boolean,
    onClose: () -> Unit,
) {
    Surface(color = BottomSheetDefaults.ContainerColor, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                // Spodek si berou seznamy měsíců (`navigationBarsPadding`); panel stojí vpravo.
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End))
                .padding(top = 8.dp),
        ) {
            ChatCalendarContent(state, calendar, initialDay, onJumpToDay, onJumpToMessage, onSummarizeDay, onClearDay, canFilterBySender, onClose)
        }
    }
}

/** Obsah kalendáře — hlavička, čipy, hledání, měsíce nebo roční přehled — pro sheet i panel. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ColumnScope.ChatCalendarContent(
    state: ChatCalendarState,
    calendar: ConversationCalendar,
    initialDay: Int?,
    onJumpToDay: (dayStart: Int) -> Unit,
    onJumpToMessage: (messageId: Long) -> Unit,
    onSummarizeDay: ((LocalDate) -> Unit)?,
    onClearDay: ((LocalDate) -> Unit)?,
    canFilterBySender: Boolean,
    /** ✕ v hlavičce panelu; sheet se zavírá tahem a mimo něj, `null`. */
    onClose: (() -> Unit)?,
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = remember { Clock.System.now().toLocalDateTime(zone).date }
    val initialMonth = remember(initialDay) {
        initialDay?.let { Instant.fromEpochSeconds(it.toLong()).toLocalDateTime(zone).date.yearMonth }
    }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val filter = state.filter
    // Stav seznamu patří k filtru: nový výraz nebo druh začíná nahoře, ne tam, kde byl ten minulý.
    val listState = remember(filter) { LazyListState() }
    val scope = rememberCoroutineScope()
    val awayFromTop by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    var yearView by rememberSaveable { mutableStateOf(false) }
    // Měsíc, na který se má seznam po výběru v ročním přehledu posunout; seznam si ho odebere.
    val scrollTarget = remember { mutableStateOf<YearMonth?>(null) }

    run {
        Row(
            Modifier.fillMaxWidth().padding(start = if (onClose != null) 4.dp else 24.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onClose != null) {
                IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.common_close))
                }
            }
            SheetTitle(stringResource(Res.string.chat_calendar_title), modifier = Modifier.weight(1f))
            // Roky na jedné obrazovce: kdy se psalo víc a kdy míň, a skok do vzdáleného měsíce.
            IconToggleButton(checked = yearView, onCheckedChange = { yearView = it }, shapes = IconButtonDefaults.toggleableShapes()) {
                Icon(painterResource(Res.drawable.apps_24dp), contentDescription = stringResource(Res.string.chat_calendar_year_view))
            }
            // Na rok zpátky se roluje dlouho; konkrétní den jde zadat rovnou.
            IconButton(onClick = { pickingDate = true }, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(Res.drawable.edit_calendar_24dp), contentDescription = stringResource(Res.string.conversation_jump_to_date))
            }
        }
        FilterChips(
            selected = filter,
            canFilterBySender = canFilterBySender,
            onSelect = calendar::setFilter,
            onPickSender = calendar::openMemberPicker,
            onClearSender = { calendar.setSearchSender(null) },
        )
        if (filter is CalendarFilter.Search) {
            SearchPill(
                initialQuery = filter.query,
                onQueryChange = calendar::setSearchQuery,
                placeholder = stringResource(Res.string.chat_calendar_search_hint),
                clearDescription = stringResource(Res.string.conversation_search_clear),
                // Kdo ťukl na Hledat, chce psát; kdo vybral člena, hledá všechny jeho zprávy.
                autoFocus = filter.sender == null && filter.query.isEmpty(),
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )
        }

        val server = state.server
        val hasActions = onSummarizeDay != null || onClearDay != null
        CalendarLegend(allMessages = filter == CalendarFilter.All, hasActions = hasActions)
        val dayActions = remember(zone, onJumpToDay, onSummarizeDay, onClearDay) {
            DayActions(
                onJump = { date -> onJumpToDay(date.atStartOfDayIn(zone).epochSeconds.toInt()) },
                onSummarize = onSummarizeDay,
                onClear = onClearDay,
            )
        }
        // Sheet má pevnou výšku (celou), ne podle obsahu: měsíce přibývají postupně a čip druhu
        // s pár měsíci nebo čekání na první stránku obsah zkrátí — sheet pak poskakoval nahoru
        // a dolů pod prstem, který zrovna mířil na čip nebo den.
        val pickMonth = { month: YearMonth ->
            yearView = false
            scrollTarget.value = month
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                yearView && filter == CalendarFilter.All -> {
                    val axis = remember(state.all, today) { allAxis(state.all, today) }
                    if (axis == null) CalendarLoading() else YearOverview(axis.newest, axis.oldest, today, axis.status, onPickMonth = pickMonth)
                }
                yearView && server != null -> {
                    val axis = remember(server) { serverAxis(server) }
                    if (axis == null) {
                        if (server.loading) CalendarLoading() else CalendarNote(stringResource(Res.string.chat_calendar_kind_empty))
                    } else {
                        YearOverview(axis.newest, axis.oldest, today, axis.status, onPickMonth = pickMonth, onReachEnd = calendar::loadMoreServer)
                    }
                }
                filter == CalendarFilter.All -> AllMessagesMonths(state.all, calendar, today, initialMonth, dayActions, listState, scrollTarget)
                server != null -> ServerMonths(
                    server = server,
                    thumbnails = filter == CalendarFilter.Kind(SharedMediaKind.Media),
                    today = today,
                    initialMonth = initialMonth,
                    onLoadMore = calendar::loadMoreServer,
                    // U druhu a hledání vede ťuknutí na první zprávu dne, která sedí, ne na začátek dne.
                    actions = dayActions,
                    onJumpToMessage = onJumpToMessage,
                    listState = listState,
                    scrollTarget = scrollTarget,
                )
                else -> CalendarNote(stringResource(Res.string.chat_calendar_search_empty))
            }
            // Zpátky na nejnovější měsíc — po odrolování o roky by to jinak bylo dlouhé švihání.
            TodayButton(
                visible = awayFromTop && !yearView,
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    state.memberPicker?.let { picker ->
        MemberPickerSheet(
            picker = picker,
            onQueryChange = calendar::onMemberQuery,
            onPick = calendar::setSearchSender,
            onDismiss = calendar::closeMemberPicker,
        )
    }

    if (pickingDate) {
        JumpToDateDialog(
            initialDay = initialDay,
            onPick = { seconds ->
                pickingDate = false
                onJumpToDay(seconds)
            },
            onDismiss = { pickingDate = false },
        )
    }
}

/**
 * „Dnes" dole nad seznamem, když je odrolovaný. Ne v hlavičce: v úzkém panelu se tam nevešel,
 * nadpis se zalomil a hlavička poskočila pokaždé, když se tlačítko ukázalo.
 */
@Composable
private fun TodayButton(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        modifier = modifier.navigationBarsPadding().padding(bottom = 16.dp),
    ) {
        FilledTonalButton(onClick = onClick, shapes = ButtonDefaults.shapes()) {
            Icon(painterResource(Res.drawable.keyboard_arrow_up_24dp), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(Res.string.chat_calendar_today))
        }
    }
}

/**
 * Čipy: všechny zprávy, hledání, člen skupiny (s hledáním se kombinuje — výraz od toho člena)
 * a druhy, které server po dnech spočítá.
 */
@Composable
private fun FilterChips(
    selected: CalendarFilter,
    canFilterBySender: Boolean,
    onSelect: (CalendarFilter) -> Unit,
    onPickSender: () -> Unit,
    onClearSender: () -> Unit,
) {
    val search = selected as? CalendarFilter.Search
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CalendarChip(stringResource(Res.string.chat_calendar_all), selected == CalendarFilter.All) { onSelect(CalendarFilter.All) }
        CalendarChip(stringResource(Res.string.chat_calendar_search), search != null, icon = Res.drawable.search_24dp) {
            onSelect(CalendarFilter.Search())
        }
        if (canFilterBySender) {
            val sender = search?.sender
            // Vybraný člen je v čipu jménem; ťuknutí ho zruší, jinak otevře výběr.
            CalendarChip(
                sender?.title ?: stringResource(Res.string.chat_calendar_member),
                sender != null,
                icon = Res.drawable.person_search_24dp,
            ) { if (sender != null) onClearSender() else onPickSender() }
        }
        CALENDAR_KINDS.forEach { kind ->
            CalendarChip(stringResource(kind.label()), selected == CalendarFilter.Kind(kind)) { onSelect(CalendarFilter.Kind(kind)) }
        }
    }
}

@Composable
private fun CalendarChip(label: String, selected: Boolean, icon: DrawableResource? = null, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = CHIP_LABEL_MAX)) },
        leadingIcon = when {
            selected -> {
                { Icon(painterResource(Res.drawable.check_24dp), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
            }
            icon != null -> {
                { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
            }
            else -> null
        },
    )
}

/** Jméno člena v čipu se zkrátí — dlouhé by čipy vytlačilo z obrazovky. */
private val CHIP_LABEL_MAX = 160.dp

private val CALENDAR_KINDS = listOf(
    SharedMediaKind.Media,
    SharedMediaKind.Files,
    SharedMediaKind.Links,
    SharedMediaKind.Voice,
    SharedMediaKind.Music,
)

private fun SharedMediaKind.label(): StringResource = when (this) {
    SharedMediaKind.Media -> Res.string.profile_tab_media
    SharedMediaKind.Files -> Res.string.profile_tab_files
    SharedMediaKind.Links -> Res.string.profile_tab_links
    SharedMediaKind.Voice -> Res.string.profile_tab_voice
    SharedMediaKind.Music -> Res.string.profile_tab_music
    else -> Res.string.profile_tab_media
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AllMessagesMonths(
    all: AllMessagesCalendar,
    calendar: ConversationCalendar,
    today: LocalDate,
    initialMonth: YearMonth?,
    actions: DayActions,
    listState: LazyListState,
    scrollTarget: MutableState<YearMonth?>,
) {
    val coveredFrom = all.coveredFrom
    if (coveredFrom == null) {
        when {
            all.counting -> CalendarLoading()
            all.failed -> CalendarNote(stringResource(Res.string.chat_calendar_failed))
            else -> CalendarNote(stringResource(Res.string.chat_calendar_month_empty))
        }
        return
    }
    // Osa jde až k měsíci první zprávy chatu, ne jen po souvislý úsek: kdo skočil na staré datum,
    // chce se v kalendáři dostat i tam — s tím, co z té doby v zařízení je, a s ťukatelnými dny.
    val coveredMonth = coveredFrom.yearMonth
    val axisEnd = all.firstMonth?.takeIf { !all.reachedStart && it < coveredMonth } ?: coveredMonth
    val months = remember(today.yearMonth, axisEnd) { monthsBetween(today.yearMonth, axisEnd) }
    // Den, od kterého dál do minulosti souvislý úsek nesahá; po začátek chatu je známé všechno.
    val partialDay = coveredFrom.takeUnless { all.reachedStart }
    // Otevře se na měsíci, který je v konverzaci zrovna vidět — kdo čte starou historii, chce
    // kolem ní, ne od dneška. Osa roste, jak se počítá, takže až měsíc na ose je.
    var scrolled by remember(listState) { mutableStateOf(false) }
    LaunchedEffect(months.size) {
        val index = initialMonth?.let { months.indexOf(it) } ?: -1
        if (!scrolled && index >= 0) {
            scrolled = true
            if (index > 0) listState.scrollToItem(index)
        }
    }
    ScrollToTarget(scrollTarget, listState) { target -> months.indexOf(target) }
    LazyColumn(
        Modifier.fillMaxWidth().navigationBarsPadding(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
    ) {
        itemsIndexed(months, key = { _, month -> month.toString() }) { _, month ->
            Column(MonthModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (month >= coveredMonth) {
                    CoveredMonth(month, all.days, all.islands, partialDay, today, actions)
                } else {
                    IslandMonth(month, all.islands, today, actions)
                }
            }
            // „Načíst starší" patří k místu, kde souvislý úsek končí, ne na konec osy: pod ním
            // můžou být starší měsíce s ostrůvky, ale souvislý úsek se prodlužuje odtud.
            if (month == coveredMonth) OlderFooter(all, onLoadOlder = calendar::loadOlder)
        }
        if (axisEnd < coveredMonth) {
            item(key = "start") {
                Column(MonthModifier, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(Res.string.chat_calendar_start),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Měsíc, kam sahá souvislý úsek od nejnovější zprávy. */
@Composable
private fun CoveredMonth(
    month: YearMonth,
    allDays: Map<LocalDate, Int>,
    islands: List<Island>,
    partialDay: LocalDate?,
    today: LocalDate,
    actions: DayActions,
) {
    val days = remember(allDays, month) { allDays.filterKeys { it.yearMonth == month } }
    val partial = partialDay?.takeIf { it.yearMonth == month }
    // Ostrůvky před koncem úseku v tomtéž měsíci se do součtu počítají taky (viz coveredDayStatus).
    val islandTotal = remember(islands, month) { islands.sumOf { island -> island.days.filterKeys { it.yearMonth == month }.values.sum() } }
    val empty = days.isEmpty() && partial == null
    // U prázdného měsíce by „0 zpráv" jen opakovalo řádek pod ním.
    MonthHeader(month, total = if (empty) null else monthTotal(days.values.sum() + islandTotal, atLeast = partial != null))
    // Souhrn jen u měsíce spočteného celého: z poloviny dat by „nejrušnější den" lhal.
    if (!empty && partial == null) MonthStatsLine(month, days, today)
    if (empty) {
        // Prázdný měsíc jen řádkem: v chatu, kde se rok nepsalo, by jinak byly hlavně prázdné mřížky.
        Text(
            stringResource(Res.string.chat_calendar_month_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    } else {
        MonthGrid(month, today, actions) { date -> coveredDayStatus(date, days, partial, islands) }
    }
}

/** Měsíc starší než souvislý úsek: dny ostrůvků, jsou-li, jinak samé neznámé (a ťukatelné) dny. */
@Composable
private fun IslandMonth(month: YearMonth, islands: List<Island>, today: LocalDate, actions: DayActions) {
    val total = islands.sumOf { island -> island.days.filterKeys { it.yearMonth == month }.values.sum() }
    MonthHeader(month, total = if (total > 0) monthTotal(total, atLeast = true) else null)
    MonthGrid(month, today, actions) { date -> islandDayStatus(date, islands) }
}

/**
 * Pod měsícem, kde končí souvislý úsek: dál do minulosti ho zařízení nemá — kalendář to řekne
 * a nabídne starší stáhnout. Po začátek chatu už jen „Začátek chatu".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OlderFooter(all: AllMessagesCalendar, onLoadOlder: () -> Unit) {
    Column(
        MonthModifier.padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            all.counting || all.loadingOlder -> LoadingIndicator()
            all.reachedStart -> Text(
                stringResource(Res.string.chat_calendar_start),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> {
                Text(
                    stringResource(if (all.failed) Res.string.chat_calendar_failed else Res.string.chat_calendar_older_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                FilledTonalButton(onClick = onLoadOlder, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.chat_calendar_load_older))
                }
            }
        }
    }
}

/**
 * Měsíce s aspoň jednou zprávou druhu nebo výsledkem hledání, od nejnovějšího; starší se
 * dotahují při dojetí. Nejstarší načtený měsíc je spočtený jen zčásti, dokud nepřijde další
 * stránka — součet má „+" a souhrn chybí.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ServerMonths(
    server: ServerDays,
    /** Den jako kruh s první fotkou (čip Média), jako kalendář v profilu. */
    thumbnails: Boolean,
    today: LocalDate,
    initialMonth: YearMonth?,
    onLoadMore: () -> Unit,
    actions: DayActions,
    onJumpToMessage: (messageId: Long) -> Unit,
    listState: LazyListState,
    scrollTarget: MutableState<YearMonth?>,
) {
    val months = remember(server.days) { calendarMonthsOf(server.days.keys) }
    val counts = remember(server.days) { server.days.mapValues { it.value.count } }
    val media = remember(server.days, thumbnails) {
        if (thumbnails) server.days.mapNotNull { (date, day) -> day.media?.let { date to it } }.toMap() else null
    }
    val serverActions = remember(actions, server.days) {
        DayActions(
            onJump = { date -> server.days[date]?.let { onJumpToMessage(it.firstMessageId) } },
            onSummarize = actions.onSummarize,
            onClear = actions.onClear,
        )
    }
    if (months.isEmpty()) {
        if (server.loading) CalendarLoading() else CalendarNote(stringResource(Res.string.chat_calendar_kind_empty))
        return
    }
    var scrolled by remember(listState) { mutableStateOf(false) }
    LaunchedEffect(months.size) {
        // Jen měsíce, ve kterých něco je: nejbližší starší k viditelnému dni. Dokud k němu
        // stránky nedošly, čeká se — kalendář se otevře tam, kde konverzace stojí.
        val index = initialMonth?.let { target -> months.indexOfFirst { it.month <= target } } ?: -1
        if (!scrolled && (index >= 0 || server.reachedEnd)) {
            scrolled = true
            if (index > 0) listState.scrollToItem(index)
        }
    }
    // Druh má jen měsíce, ve kterých něco je: z přehledu na nejbližší starší.
    ScrollToTarget(scrollTarget, listState) { target -> months.indexOfFirst { it.month <= target } }
    LazyColumn(
        Modifier.fillMaxWidth().navigationBarsPadding(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
    ) {
        itemsIndexed(months, key = { _, month -> month.month.toString() }) { index, month: CalendarMonth ->
            // Dotahuje se, už když se ukáže předposlední měsíc. Klíčem je místo, odkud se
            // pokračuje, ne počet měsíců: stránka rušného kanálu přinese jen pár dnů téhož
            // měsíce a s počtem měsíců jako klíčem se další už nevyžádala.
            if (index >= months.size - 2) LaunchedEffect(server.next) { onLoadMore() }
            val days = counts.filterKeys { it.yearMonth == month.month }
            val complete = server.reachedEnd || index < months.size - 1
            Column(MonthModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MonthHeader(month.month, total = monthTotal(days.values.sum(), atLeast = !complete))
                if (complete) MonthStatsLine(month.month, days, today)
                MonthGrid(
                    month = month.month,
                    today = today,
                    actions = serverActions,
                    media = media,
                ) { date -> DayStatus.Known(days[date] ?: 0) }
            }
        }
        if (server.loading) item { CalendarLoading() }
    }
}

/** Posune seznam na měsíc vybraný v ročním přehledu ([index] ho najde) a cíl odebere. */
@Composable
private fun ScrollToTarget(target: MutableState<YearMonth?>, listState: LazyListState, index: (YearMonth) -> Int) {
    val month = target.value
    LaunchedEffect(month) {
        if (month == null) return@LaunchedEffect
        val at = index(month)
        if (at >= 0) listState.scrollToItem(at)
        target.value = null
    }
}

/** Osa kalendáře (od nejnovějšího měsíce po nejstarší) a co se ví o kterém dni. */
private class CalendarAxis(val newest: YearMonth, val oldest: YearMonth, val status: (LocalDate) -> DayStatus)

/**
 * Všechny zprávy: od běžného měsíce po první zprávu chatu (nebo konec souvislého úseku), den
 * jako v měsících — souvislý úsek, ostrůvky, jinak neznámý. Totéž čtou měsíce i přehled.
 */
private fun allAxis(all: AllMessagesCalendar, today: LocalDate): CalendarAxis? {
    val coveredFrom = all.coveredFrom ?: return null
    val coveredMonth = coveredFrom.yearMonth
    val axisEnd = all.firstMonth?.takeIf { !all.reachedStart && it < coveredMonth } ?: coveredMonth
    val partialDay = coveredFrom.takeUnless { all.reachedStart }
    return CalendarAxis(today.yearMonth, axisEnd) { date ->
        if (date.yearMonth >= coveredMonth) coveredDayStatus(date, all.days, partialDay, all.islands) else islandDayStatus(date, all.islands)
    }
}

/** Druh nebo hledání: od nejnovějšího načteného dne; za nejstarší stránkou neznámo, dokud nepřijde další. */
private fun serverAxis(server: ServerDays): CalendarAxis? {
    if (server.days.isEmpty()) return null
    val newest = server.days.keys.max()
    val oldest = server.days.keys.min()
    return CalendarAxis(newest.yearMonth, oldest.yearMonth) { date ->
        if (!server.reachedEnd && date < oldest) DayStatus.Unknown else DayStatus.Known(server.days[date]?.count ?: 0)
    }
}

/**
 * Roční přehled: roky od nejnovějšího, v každém dvanáct malých měsíců s tečkou za den, sytou
 * podle počtu proti nejrušnějšímu dni ROKU — tady jde o srovnání měsíců mezi sebou. Ťuknutí na
 * měsíc přepne zpět na měsíce a posune na něj. Měsíce mimo osu (budoucí, před začátkem chatu)
 * jsou jen tlumené.
 */
@Composable
private fun YearOverview(
    newest: YearMonth,
    oldest: YearMonth,
    today: LocalDate,
    status: (LocalDate) -> DayStatus,
    onPickMonth: (YearMonth) -> Unit,
    /** U dnů ze serveru: dotáhnout starší, když se dojede k nejstaršímu roku. */
    onReachEnd: (() -> Unit)? = null,
) {
    val years = (newest.year downTo oldest.year).toList()
    LazyColumn(
        Modifier.fillMaxWidth().navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
    ) {
        itemsIndexed(years, key = { _, year -> year }) { index, year ->
            if (onReachEnd != null && index == years.size - 1) LaunchedEffect(oldest) { onReachEnd() }
            val max = remember(year, status) {
                (1..12).maxOf { m -> calendarMonth(YearMonth(year, m)).cells.filterNotNull().maxOfOrNull { status(it).count() } ?: 0 }
            }
            Column(MonthModifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(year.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
                (1..12).chunked(MONTHS_PER_ROW).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { m ->
                            val month = YearMonth(year, m)
                            MiniMonth(
                                month = month,
                                enabled = month in oldest..newest && month <= today.yearMonth,
                                max = max,
                                today = today,
                                status = status,
                                onClick = { onPickMonth(month) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun DayStatus.count(): Int = (this as? DayStatus.Known)?.count ?: (this as? DayStatus.Partial)?.count ?: 0

/** Malý měsíc v ročním přehledu: název a tečka za každý den, bez čísel. */
@Composable
private fun MiniMonth(
    month: YearMonth,
    enabled: Boolean,
    max: Int,
    today: LocalDate,
    status: (LocalDate) -> DayStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val base = BottomSheetDefaults.ContainerColor
    val cells = remember(month) { calendarMonth(month).cells }
    val title = stringResource(month.month.standaloneName())
    Column(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .then(if (enabled) Modifier.clickable(role = Role.Button, onClickLabel = monthTitle(month), onClick = onClick) else Modifier)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) scheme.onSurface else scheme.onSurfaceVariant.copy(alpha = UNKNOWN_DAY_ALPHA),
            modifier = Modifier.padding(start = 2.dp),
        )
        cells.chunked(DAYS_IN_WEEK).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(1.dp)) {
                        if (date != null) {
                            val day = status(date)
                            val level = heatLevel(day.count(), max)
                            val color = when {
                                !enabled || date > today || day == DayStatus.Unknown -> scheme.onSurfaceVariant.copy(alpha = MINI_UNKNOWN_ALPHA)
                                level == 0 -> scheme.surfaceContainerHighest
                                else -> scheme.primary.copy(alpha = HEAT_ALPHA[level]).compositeOver(base)
                            }
                            Box(Modifier.fillMaxSize().clip(CircleShape).background(color))
                        }
                    }
                }
            }
        }
    }
}

/** Tři měsíce vedle sebe — na telefonu se tečky dne ještě rozeznají. */
private const val MONTHS_PER_ROW = 3

/** Den, o kterém se v přehledu neví nic (nebo mimo osu): jen náznak tečky. */
private const val MINI_UNKNOWN_ALPHA = 0.08f

@Composable
private fun MonthHeader(month: YearMonth, total: String?) {
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(monthTitle(month), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (total != null) {
            Text(
                total,
                style = MaterialTheme.typography.labelLarge.tabularNumbers(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Součet měsíce; „120+ zpráv" u měsíce, do kterého spočtené zprávy sahají jen zčásti. */
@Composable
private fun monthTotal(total: Int, atLeast: Boolean): String {
    val number = total.grouped()
    val shown = if (atLeast) stringResource(Res.string.chat_calendar_at_least, number) else number
    return pluralStringResource(Res.plurals.chat_calendar_messages, total, shown)
}

/** Co se o dni ví: spočtený celý, jen zčásti („+"), nebo nic, protože jeho zprávy v zařízení nejsou. */
@Immutable
internal sealed interface DayStatus {
    data class Known(val count: Int) : DayStatus
    data class Partial(val count: Int) : DayStatus
    data object Unknown : DayStatus
}

/**
 * Den v měsíci souvislého úseku. Před jeho nejstarším dnem ([partialDay]) rozhodují ostrůvky —
 * i ve stejném měsíci: skok o tři týdny zpátky přinese ostrůvek v měsíci, kde úsek končí, a bez
 * nich by tam zůstaly samé neznámé dny, přestože jsou spočtené.
 */
internal fun coveredDayStatus(date: LocalDate, counts: Map<LocalDate, Int>, partialDay: LocalDate?, islands: List<Island>): DayStatus = when {
    partialDay != null && date < partialDay -> islandDayStatus(date, islands)
    date == partialDay -> DayStatus.Partial((counts[date] ?: 0) + islands.sumOf { it.days[date] ?: 0 })
    else -> DayStatus.Known(counts[date] ?: 0)
}

/** Den starší než souvislý úsek: známý jen uvnitř ostrůvku, jeho krajní dny zčásti. */
internal fun islandDayStatus(date: LocalDate, islands: List<Island>): DayStatus {
    val island = islands.firstOrNull { date in it.oldestDay..it.newestDay } ?: return DayStatus.Unknown
    val count = island.days[date] ?: 0
    return if (date == island.oldestDay || date == island.newestDay) DayStatus.Partial(count) else DayStatus.Known(count)
}

/**
 * Mřížka měsíce od pondělí. Neznámé dny ([DayStatus.Unknown]) se kreslí tlumeně a bez počtu, ne
 * jako „nic" — prázdný den by tvrdil, že se ten den nepsalo; ťuknout na ně jde, skok na datum se
 * ptá serveru.
 */
@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    actions: DayActions,
    /** První fotka dne u čipu Média; `null` = bez náhledů. */
    media: Map<LocalDate, CalendarDayMedia>? = null,
    status: (LocalDate) -> DayStatus,
) {
    val cells = remember(month) { calendarMonth(month).cells }
    val statuses = cells.filterNotNull().associateWith(status)
    val max = statuses.values.maxOfOrNull { (it as? DayStatus.Known)?.count ?: (it as? DayStatus.Partial)?.count ?: 0 } ?: 0
    Row(Modifier.fillMaxWidth()) {
        DayOfWeek.entries.forEach { day ->
            Text(
                stringResource(day.shortName()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    cells.chunked(DAYS_IN_WEEK).forEach { week ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            week.forEach { date ->
                Box(Modifier.weight(1f).aspectRatio(1f)) {
                    if (date != null) {
                        val day = statuses.getValue(date)
                        val count = (day as? DayStatus.Known)?.count ?: (day as? DayStatus.Partial)?.count ?: 0
                        DayCell(
                            date = date,
                            count = count,
                            level = heatLevel(count, max),
                            unknown = day == DayStatus.Unknown && date <= today,
                            partial = day is DayStatus.Partial,
                            isToday = date == today,
                            actions = actions,
                            media = media?.get(date),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Den: číslo a pod ním počet, kruh za nimi tím sytější, čím víc zpráv proti nejrušnějšímu dni
 * měsíce. Měřítko je po měsících — v chatu, kde se jednou psalo tisíc zpráv denně a jindy
 * deset, by jinak klidné měsíce vyšly celé bledé. [unknown] = o dni se neví nic (jeho zprávy
 * v zařízení nejsou), ťuknutí na něj skočí na datum přes server.
 */
@Composable
private fun DayCell(
    date: LocalDate,
    count: Int,
    level: Int,
    unknown: Boolean,
    partial: Boolean,
    isToday: Boolean,
    actions: DayActions,
    /** První fotka dne (čip Média): den je pak kruh s jejím náhledem. */
    media: CalendarDayMedia? = null,
) {
    val scheme = MaterialTheme.colorScheme
    val base = BottomSheetDefaults.ContainerColor
    val photo = media?.message?.media as? MessageMedia.Photo
    val video = media?.message?.media as? MessageMedia.Video
    // Skryté médium (spoiler) náhled neukáže, jako kalendář v profilu — odkrývá se až v chatu.
    val hidden = photo?.hasSpoiler == true || video?.hasSpoiler == true
    val thumbnail = (photo?.thumbnailFileId ?: video?.thumbnailFileId).takeUnless { hidden }
    val background = if (level == 0) base else scheme.primary.copy(alpha = HEAT_ALPHA[level]).compositeOver(base)
    // Na syté ploše `primary` je čitelný `onPrimary`, na bledé `onSurface` — vybere se podle
    // skutečného kontrastu, ne podle úrovně (v tmavém motivu je `primary` světlá). Přes fotku
    // bílá na ztmavení, jako v profilu.
    val content = when {
        thumbnail != null -> Color.White
        level == 0 -> scheme.onSurface
        else -> strongestAccentOn(background, scheme.onSurface, scheme.onPrimary)
    }
    val dayName = stringResource(Res.string.date_day_month, date.day, stringResource(date.month.name()))
    val description = when {
        count > 0 -> stringResource(
            Res.string.profile_calendar_day,
            dayName,
            pluralStringResource(
                Res.plurals.chat_calendar_messages,
                count,
                count.grouped().let { if (partial) stringResource(Res.string.chat_calendar_at_least, it) else it },
            ),
        )
        unknown -> stringResource(Res.string.chat_calendar_day_unknown, dayName)
        else -> null
    }
    // Podržení jen u dne se zprávami: u neznámého dne není co shrnout ani mazat.
    val menuActions = dayMenu(date, actions).takeIf { count > 0 }
    var menuOpen by remember { mutableStateOf(false) }
    val actionsLabel = stringResource(Res.string.chat_calendar_day_actions)
    Box {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(background)
                .then(if (isToday) Modifier.border(2.dp, scheme.primary, CircleShape) else Modifier)
                .then(
                    if (description != null) {
                        Modifier
                            .contextClickable(
                                role = Role.Button,
                                onLongClick = menuActions?.let { { menuOpen = true } },
                                onClick = { actions.onJump(date) },
                            )
                            .clearAndSetSemantics {
                                contentDescription = description
                                role = Role.Button
                                onClick { actions.onJump(date); true }
                                if (menuActions != null) onLongClick(actionsLabel) { menuOpen = true; true }
                            }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnail != null) {
                RemoteImage(
                    fileId = thumbnail,
                    placeholder = (photo?.minithumbnail ?: video?.minithumbnail),
                    modifier = Modifier.fillMaxSize(),
                )
                // Bílé číslo na fotce potřebuje podklad — na světlé fotce by zaniklo.
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = DAY_SCRIM_ALPHA)))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    date.day.toString(),
                    style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
                    color = if (count > 0) content else scheme.onSurfaceVariant.copy(alpha = if (unknown) UNKNOWN_DAY_ALPHA else EMPTY_DAY_ALPHA),
                )
                if (count > 0) {
                    Text(
                        cellCount(count).let { if (partial) stringResource(Res.string.chat_calendar_at_least, it) else it },
                        style = MaterialTheme.typography.labelSmall.tabularNumbers(),
                        color = content,
                        maxLines = 1,
                    )
                }
            }
        }
        if (menuActions != null) {
            ExpressiveMenu(expanded = menuOpen, onDismiss = { menuOpen = false }, groups = menuActions)
        }
    }
}

/**
 * Nabídka z podržení dne: přejít, shrnout den modelem v zařízení a vymazat historii za den.
 * Mazání ve vlastní skupině v chybové barvě, jako všude ([MenuAction.destructive]).
 */
@Composable
private fun dayMenu(date: LocalDate, actions: DayActions): List<List<MenuAction>>? {
    val summarize = actions.onSummarize
    val clear = actions.onClear
    if (summarize == null && clear == null) return null
    return listOf(
        listOfNotNull(
            MenuAction(stringResource(Res.string.chat_calendar_day_go), Res.drawable.arrow_forward_24dp) { actions.onJump(date) },
            summarize?.let { MenuAction(stringResource(Res.string.chat_calendar_day_summarize), Res.drawable.summarize_24dp) { it(date) } },
        ),
        listOfNotNull(
            clear?.let {
                MenuAction(stringResource(Res.string.chat_calendar_day_clear), Res.drawable.delete_sweep_24dp, destructive = true) { it(date) }
            },
        ),
    )
}

/**
 * Co jde se dnem udělat — ťuknutí ([onJump]) a nabídka z podržení. Jedna skupina místo tří
 * lambd v každé funkci mřížky (viz „Parametry funkce" v CLAUDE.md).
 */
@Immutable
internal class DayActions(
    val onJump: (LocalDate) -> Unit,
    val onSummarize: ((LocalDate) -> Unit)?,
    val onClear: ((LocalDate) -> Unit)?,
)

/**
 * Pod čipy: co znamená šedý den a „+" (jen u všech zpráv — druhy počítá server celé) a že den
 * jde podržet. Bez toho se na obojí dalo přijít jen náhodou.
 */
@Composable
private fun CalendarLegend(allMessages: Boolean, hasActions: Boolean) {
    val parts = listOfNotNull(
        stringResource(Res.string.chat_calendar_legend_all).takeIf { allMessages },
        stringResource(Res.string.chat_calendar_legend_hold).takeIf { hasActions },
    )
    if (parts.isEmpty()) return
    Text(
        parts.joinToString(" "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp),
    )
}

/** Počet v políčku: do tisíce celý, výš v tisících („1,2k"), aby se vešel do kruhu. */
@Composable
private fun cellCount(count: Int): String {
    if (count < 1000) return count.toString()
    val whole = count / 1000
    val tenths = count % 1000 / 100
    val number = if (whole < 10 && tenths != 0) {
        stringResource(Res.string.decimal_number, whole.toString(), tenths.toString())
    } else {
        whole.toString()
    }
    return stringResource(Res.string.chat_calendar_count_thousands, number)
}

/** Sytost dne: 0 = žádná zpráva, 1–4 podle podílu na nejrušnějším dni měsíce. */
internal fun heatLevel(count: Int, max: Int): Int =
    if (count <= 0 || max <= 0) 0 else ceil(HEAT_LEVELS * count.toDouble() / max).toInt().coerceIn(1, HEAT_LEVELS)

/**
 * Pod nadpisem měsíce: nejrušnější den, průměr za den a nejdelší série dnů se zprávami.
 * Z počtů, které kalendář už má — nic se nedotahuje.
 */
@Composable
private fun MonthStatsLine(month: YearMonth, counts: Map<LocalDate, Int>, today: LocalDate) {
    val stats = remember(month, counts, today) { monthStats(month, counts, today) } ?: return
    val busiest = stringResource(
        Res.string.chat_calendar_stats_busiest,
        stringResource(Res.string.date_day_month, stats.busiest.day, stringResource(stats.busiest.month.name())),
        stats.busiestCount.grouped(),
    )
    val average = stringResource(
        Res.string.chat_calendar_stats_average,
        if (stats.perDayTenths % 10 == 0) {
            (stats.perDayTenths / 10).toString()
        } else {
            stringResource(Res.string.decimal_number, (stats.perDayTenths / 10).toString(), (stats.perDayTenths % 10).toString())
        },
    )
    // Série jednoho dne nic neříká.
    val streak = stats.longestStreak.takeIf { it >= 2 }?.let { pluralStringResource(Res.plurals.chat_calendar_stats_streak, it, it) }
    Text(
        listOfNotNull(busiest, average, streak).joinToString(" · "),
        style = MaterialTheme.typography.labelMedium.tabularNumbers(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
    )
}

/** Souhrn měsíce ([monthStats]); průměr v desetinách, ať se zaokrouhlí jednou a celočíselně. */
internal data class MonthStats(val busiest: LocalDate, val busiestCount: Int, val perDayTenths: Int, val longestStreak: Int)

/**
 * Nejrušnější den (při shodě ten dřívější), průměr za den a nejdelší série dnů se zprávami.
 * Běžný měsíc se počítá jen po dnešek — průměr za dny, které ještě nebyly, by ho tlačil dolů.
 * `null`, když v měsíci nic není.
 */
internal fun monthStats(month: YearMonth, counts: Map<LocalDate, Int>, today: LocalDate): MonthStats? {
    val days = calendarMonth(month).cells.filterNotNull().filter { it <= today }
    if (days.isEmpty()) return null
    val active = days.filter { (counts[it] ?: 0) > 0 }
    if (active.isEmpty()) return null
    val busiest = active.maxWith(compareBy<LocalDate> { counts.getValue(it) }.thenByDescending { it })
    val total = active.sumOf { counts.getValue(it) }
    var longest = 0
    var run = 0
    for (day in days) {
        run = if ((counts[day] ?: 0) > 0) run + 1 else 0
        longest = maxOf(longest, run)
    }
    return MonthStats(
        busiest = busiest,
        busiestCount = counts.getValue(busiest),
        perDayTenths = (total * 10 + days.size / 2) / days.size,
        longestStreak = longest,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CalendarLoading() {
    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
}

@Composable
private fun CalendarNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
    )
}

private const val DAYS_IN_WEEK = 7

/**
 * Měsíc nejvýš na šířku formuláře, uprostřed: na rozloženém Foldu by sheet (640 dp) natáhl dny
 * do kruhů přes 90 dp a měsíc by se nevešel na obrazovku.
 */
private val MonthModifier = Modifier
    .fillMaxWidth()
    .wrapContentWidth(Alignment.CenterHorizontally)
    .widthIn(max = ContentWidth.Form)
    .padding(horizontal = 16.dp)
private const val HEAT_LEVELS = 4

/** Průhlednost `primary` za dnem podle úrovně; nejvyšší je plná barva. */
private val HEAT_ALPHA = floatArrayOf(0f, 0.18f, 0.36f, 0.62f, 1f)

/** Den bez zpráv je jen orientační; plnou barvu mají dny, na které jde ťuknout. */
private const val EMPTY_DAY_ALPHA = 0.6f

/**
 * Ztmavení náhledu fotky pod číslem dne a počtem. Silnější než v profilu (0,3): tam je přes
 * fotku jen číslo dne, tady dva řádky a pestré obálky je bez něj přebíjely.
 */
private const val DAY_SCRIM_ALPHA = 0.4f

/** Den, jehož zprávy v zařízení nejsou — o něm se neví nic, proto ještě tlumenější než prázdný. */
private const val UNKNOWN_DAY_ALPHA = 0.3f
