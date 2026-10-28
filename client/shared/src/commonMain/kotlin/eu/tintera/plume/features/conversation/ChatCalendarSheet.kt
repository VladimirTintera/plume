package eu.tintera.plume.features.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import eu.tintera.plume.core.telegram.calendarMonths
import eu.tintera.plume.core.telegram.monthsBetween
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.core.ui.monthTitle
import eu.tintera.plume.core.ui.name
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.shortName
import eu.tintera.plume.core.ui.strongestAccentOn
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.features.profile.MediaCalendarState
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
 * Ťuknutí na den skočí na jeho první zprávu (u druhu na první zprávu toho druhu). Čipy přepínají
 * mezi všemi zprávami a jedním druhem (fotky a videa, soubory, odkazy, hlasovky, hudba); vzdálené
 * datum jde zadat klasickým kalendářem z ikony v hlavičce.
 *
 * Všechny zprávy se počítají z toho, co je v zařízení, a kalendář končí tam, kam sahají — dál
 * nabízí „Načíst starší" (viz [ConversationCalendar]). Druhy počítá server celé
 * (`getChatMessageCalendar`) a měsíce bez nich se vynechávají.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatCalendarSheet(
    state: ChatCalendarState,
    calendar: ConversationCalendar,
    /** Den, na kterém se kalendář otevře (sekundy epochy; viditelný den konverzace); `null` = dnes. */
    initialDay: Int?,
    onJumpToDay: (dayStart: Int) -> Unit,
    onJumpToMessage: (messageId: Long) -> Unit,
) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = remember { Clock.System.now().toLocalDateTime(zone).date }
    val initialMonth = remember(initialDay) {
        initialDay?.let { Instant.fromEpochSeconds(it.toLong()).toLocalDateTime(zone).date.yearMonth }
    }
    var pickingDate by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = calendar::close, sheetState = rememberExpandedSheetState()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetTitle(stringResource(Res.string.chat_calendar_title), modifier = Modifier.weight(1f))
            // Na rok zpátky se roluje dlouho; konkrétní den jde zadat rovnou.
            IconButton(onClick = { pickingDate = true }, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(Res.drawable.edit_calendar_24dp), contentDescription = stringResource(Res.string.conversation_jump_to_date))
            }
        }
        KindChips(selected = state.filter, onSelect = calendar::setFilter)

        val filter = state.filter
        val media = state.media
        // Sheet má pevnou výšku (celou), ne podle obsahu: měsíce přibývají postupně a čip druhu
        // s pár měsíci nebo čekání na první stránku obsah zkrátí — sheet pak poskakoval nahoru
        // a dolů pod prstem, který zrovna mířil na čip nebo den.
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (filter == null) {
                AllMessagesMonths(state.all, calendar, today, zone, initialMonth, onJumpToDay)
            } else if (media != null) {
                KindMonths(media, today, initialMonth, calendar::loadMoreMedia, onJumpToMessage)
            }
        }
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

/** Čip „všechny zprávy" a druhy, které server po dnech spočítá. */
@Composable
private fun KindChips(selected: SharedMediaKind?, onSelect: (SharedMediaKind?) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (listOf<SharedMediaKind?>(null) + CALENDAR_KINDS).forEach { kind ->
            val isSelected = kind == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(kind) },
                label = { Text(stringResource(kind.label())) },
                leadingIcon = if (isSelected) {
                    { Icon(painterResource(Res.drawable.check_24dp), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
    }
}

private val CALENDAR_KINDS = listOf(
    SharedMediaKind.Media,
    SharedMediaKind.Files,
    SharedMediaKind.Links,
    SharedMediaKind.Voice,
    SharedMediaKind.Music,
)

private fun SharedMediaKind?.label(): StringResource = when (this) {
    null -> Res.string.chat_calendar_all
    SharedMediaKind.Media -> Res.string.profile_tab_media
    SharedMediaKind.Files -> Res.string.profile_tab_files
    SharedMediaKind.Links -> Res.string.profile_tab_links
    SharedMediaKind.Voice -> Res.string.profile_tab_voice
    SharedMediaKind.Music -> Res.string.profile_tab_music
    else -> Res.string.chat_calendar_all
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AllMessagesMonths(
    all: AllMessagesCalendar,
    calendar: ConversationCalendar,
    today: LocalDate,
    zone: TimeZone,
    initialMonth: YearMonth?,
    onJumpToDay: (dayStart: Int) -> Unit,
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
    val onPick = { date: LocalDate -> onJumpToDay(date.atStartOfDayIn(zone).epochSeconds.toInt()) }
    val listState = rememberLazyListState()
    // Otevře se na měsíci, který je v konverzaci zrovna vidět — kdo čte starou historii, chce
    // kolem ní, ne od dneška. Osa roste, jak se počítá, takže až měsíc na ose je.
    var scrolled by remember { mutableStateOf(false) }
    LaunchedEffect(months.size) {
        val index = initialMonth?.let { months.indexOf(it) } ?: -1
        if (!scrolled && index >= 0) {
            scrolled = true
            if (index > 0) listState.scrollToItem(index)
        }
    }
    LazyColumn(
        Modifier.fillMaxWidth().navigationBarsPadding(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
    ) {
        itemsIndexed(months, key = { _, month -> month.toString() }) { _, month ->
            Column(MonthModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (month >= coveredMonth) {
                    CoveredMonth(month, all.days, partialDay, today, onPick)
                } else {
                    IslandMonth(month, all.islands, today, onPick)
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
    partialDay: LocalDate?,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
) {
    val days = remember(allDays, month) { allDays.filterKeys { it.yearMonth == month } }
    val partial = partialDay?.takeIf { it.yearMonth == month }
    val empty = days.isEmpty() && partial == null
    // U prázdného měsíce by „0 zpráv" jen opakovalo řádek pod ním.
    MonthHeader(month, total = if (empty) null else monthTotal(days.values.sum(), atLeast = partial != null))
    if (empty) {
        // Prázdný měsíc jen řádkem: v chatu, kde se rok nepsalo, by jinak byly hlavně prázdné mřížky.
        Text(
            stringResource(Res.string.chat_calendar_month_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp),
        )
    } else {
        MonthGrid(month, today, onPick) { date -> coveredDayStatus(date, days, partial) }
    }
}

/** Měsíc starší než souvislý úsek: dny ostrůvků, jsou-li, jinak samé neznámé (a ťukatelné) dny. */
@Composable
private fun IslandMonth(month: YearMonth, islands: List<Island>, today: LocalDate, onPick: (LocalDate) -> Unit) {
    val total = islands.sumOf { island -> island.days.filterKeys { it.yearMonth == month }.values.sum() }
    MonthHeader(month, total = if (total > 0) monthTotal(total, atLeast = true) else null)
    MonthGrid(month, today, onPick) { date -> islandDayStatus(date, islands) }
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

/** Měsíce s aspoň jednou zprávou druhu, od nejnovějšího; starší se dotahují při dojetí. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun KindMonths(
    media: MediaCalendarState,
    today: LocalDate,
    initialMonth: YearMonth?,
    onLoadMore: () -> Unit,
    onJumpToMessage: (messageId: Long) -> Unit,
) {
    val months = remember(media.days) { calendarMonths(media.days) }
    val byDate = remember(media.days) { media.days.associateBy { it.date } }
    if (months.isEmpty()) {
        if (media.loading) CalendarLoading() else CalendarNote(stringResource(Res.string.chat_calendar_kind_empty))
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        // Druh má jen měsíce, ve kterých něco je: nejbližší starší k viditelnému dni.
        val index = initialMonth?.let { target -> months.indexOfFirst { it.month <= target } } ?: -1
        if (index > 0) listState.scrollToItem(index)
    }
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
            if (index >= months.size - 2) LaunchedEffect(media.nextFromMessageId) { onLoadMore() }
            val days = month.cells.filterNotNull().mapNotNull { date -> byDate[date]?.let { date to it.count } }.toMap()
            Column(MonthModifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MonthHeader(month.month, total = days.values.sum().let { pluralStringResource(Res.plurals.chat_calendar_messages, it, it.grouped()) })
                MonthGrid(
                    month = month.month,
                    today = today,
                    onPick = { date -> byDate[date]?.let { onJumpToMessage(it.message.id) } },
                ) { date -> DayStatus.Known(days[date] ?: 0) }
            }
        }
        if (media.loading) item { CalendarLoading() }
    }
}

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

/** Den v měsíci souvislého úseku: před jeho nejstarším dnem ([partialDay]) neznámý. */
internal fun coveredDayStatus(date: LocalDate, counts: Map<LocalDate, Int>, partialDay: LocalDate?): DayStatus = when {
    partialDay != null && date < partialDay -> DayStatus.Unknown
    date == partialDay -> DayStatus.Partial(counts[date] ?: 0)
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
    onPick: (LocalDate) -> Unit,
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
                            onPick = onPick,
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
    onPick: (LocalDate) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val base = BottomSheetDefaults.ContainerColor
    val background = if (level == 0) base else scheme.primary.copy(alpha = HEAT_ALPHA[level]).compositeOver(base)
    // Na syté ploše `primary` je čitelný `onPrimary`, na bledé `onSurface` — vybere se podle
    // skutečného kontrastu, ne podle úrovně (v tmavém motivu je `primary` světlá).
    val content = if (level == 0) scheme.onSurface else strongestAccentOn(background, scheme.onSurface, scheme.onPrimary)
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
    Box(
        Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(background)
            .then(if (isToday) Modifier.border(2.dp, scheme.primary, CircleShape) else Modifier)
            .then(
                if (description != null) {
                    Modifier
                        .clickable { onPick(date) }
                        .clearAndSetSemantics {
                            contentDescription = description
                            role = Role.Button
                        }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
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

/** Den, jehož zprávy v zařízení nejsou — o něm se neví nic, proto ještě tlumenější než prázdný. */
private const val UNKNOWN_DAY_ALPHA = 0.3f
