package eu.tintera.plume.features.chats

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.MessageSearchFilter
import eu.tintera.plume.core.telegram.SearchPeriod
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.date_short
import eu.tintera.plume.resources.expand_more_24dp
import eu.tintera.plume.resources.search_filter_any_time
import eu.tintera.plume.resources.search_filter_apply
import eu.tintera.plume.resources.search_filter_channels
import eu.tintera.plume.resources.search_filter_custom
import eu.tintera.plume.resources.search_filter_date
import eu.tintera.plume.resources.search_filter_groups
import eu.tintera.plume.resources.search_filter_month
import eu.tintera.plume.resources.search_filter_private
import eu.tintera.plume.resources.search_filter_range
import eu.tintera.plume.resources.search_filter_week
import eu.tintera.plume.resources.search_filter_year
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Čipy nad výsledky hledání: druh chatu (jeden z nich, druhé ťuknutí ho zruší) a období.
 *
 * - **Druh chatu jako tři `FilterChip`y, ne nabídka** — jsou vidět hned a přepnou se jedním
 *   ťuknutím; období má víc možností a vlastní rozsah, proto je za jedním čipem s nabídkou.
 * - **Čipy, ne connected button group.** Ta je na volbu z pevné sady, kde je vždycky něco
 *   vybrané (přepínač zobrazení) — filtr ale smí být prázdný, a čtyři spojená tlačítka by
 *   zabrala celou šířku, takže by se čip období na řádek nevešel. Filtry hledání mají čipy
 *   i Google aplikace v Expressive podobě.
 * - **Vybraný čip nese fajfku** (výchozí vzhled M3), takže se výběr pozná i bez barvy.
 * - **Řádek jde posouvat do strany** — v češtině a na úzkém telefonu se všechny čtyři nevejdou.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MessageSearchFilters(filter: MessageSearchFilter, onChange: (MessageSearchFilter) -> Unit) {
    var periodMenu by remember { mutableStateOf(false) }
    var pickRange by rememberSaveable { mutableStateOf(false) }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        KIND_CHIPS.forEach { (kind, label) ->
            val selected = filter.chatKind == kind
            FilterChip(
                selected = selected,
                onClick = { onChange(filter.copy(chatKind = if (selected) null else kind)) },
                label = { Text(stringResource(label)) },
                leadingIcon = if (selected) {
                    { Icon(painterResource(Res.drawable.check_24dp), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else {
                    null
                },
            )
        }
        Box {
            FilterChip(
                selected = filter.period != null,
                onClick = { periodMenu = true },
                label = { Text(filter.period?.label() ?: stringResource(Res.string.search_filter_date)) },
                trailingIcon = {
                    Icon(painterResource(Res.drawable.expand_more_24dp), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize))
                },
            )
            /*
             * Expressive menu, ne holé `DropdownMenu`: vybrané období je v něm zaškrtnuté, takže
             * se po otevření pozná, co platí. Předvolby a vlastní rozsah jsou dvě skupiny — rozsah
             * otevírá další krok (výběr dat) a pod sebou ukazuje, jaký je.
             */
            DropdownMenuPopup(expanded = periodMenu, onDismissRequest = { periodMenu = false }) {
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 2)) {
                    PERIODS.forEachIndexed { index, (period, label) ->
                        DropdownMenuItem(
                            checked = filter.period == period,
                            onCheckedChange = {
                                periodMenu = false
                                onChange(filter.copy(period = period))
                            },
                            text = { Text(stringResource(label)) },
                            shapes = MenuDefaults.itemShape(index, PERIODS.size),
                            checkedLeadingIcon = { Icon(painterResource(Res.drawable.check_24dp), contentDescription = null) },
                        )
                    }
                }
                Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(1, 2)) {
                    val custom = filter.period as? SearchPeriod.Custom
                    DropdownMenuItem(
                        checked = custom != null,
                        // Ťuknutí na už vybraný rozsah ho nezruší, ale otevře výběr znovu — zrušit
                        // jde „Kdykoli" nahoře, jako u předvoleb.
                        onCheckedChange = {
                            periodMenu = false
                            pickRange = true
                        },
                        text = { Text(stringResource(Res.string.search_filter_custom)) },
                        supportingText = custom?.let { { Text(it.label()) } },
                        shapes = MenuDefaults.itemShape(0, 1),
                        checkedLeadingIcon = { Icon(painterResource(Res.drawable.check_24dp), contentDescription = null) },
                    )
                }
            }
        }
    }

    if (pickRange) {
        // Budoucí dny nabízet nemá smysl — žádná zpráva v nich není.
        val state = rememberDateRangePickerState(selectableDates = PastDates)
        DatePickerDialog(
            onDismissRequest = { pickRange = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        pickRange = false
                        val start = state.selectedStartDateMillis ?: return@TextButton
                        val end = state.selectedEndDateMillis ?: start
                        onChange(filter.copy(period = SearchPeriod.Custom(start.localDayStart(), end.localDayStart())))
                    },
                ) { Text(stringResource(Res.string.search_filter_apply)) }
            },
            dismissButton = { TextButton(onClick = { pickRange = false }) { Text(stringResource(Res.string.common_cancel)) } },
        ) {
            DateRangePicker(state = state, modifier = Modifier.weight(1f))
        }
    }
}

private object PastDates : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= Clock.System.now().toEpochMilliseconds()
}

/**
 * Výběr dat vrací půlnoc vybraného dne v UTC. Den se z ní vezme v UTC a začátek hledání je
 * jeho půlnoc v MÍSTNÍM pásmu — jinak by se v Česku posunul o hodinu či dvě do předchozího dne.
 */
private fun Long.localDayStart(): Long {
    val day = Instant.fromEpochMilliseconds(this).toLocalDateTime(TimeZone.UTC).date
    return day.atStartOfDayIn(TimeZone.currentSystemDefault()).epochSeconds
}

@Composable
private fun SearchPeriod.label(): String = when (this) {
    SearchPeriod.Week -> stringResource(Res.string.search_filter_week)
    SearchPeriod.Month -> stringResource(Res.string.search_filter_month)
    SearchPeriod.Year -> stringResource(Res.string.search_filter_year)
    is SearchPeriod.Custom -> stringResource(Res.string.search_filter_range, fromEpochSeconds.shortDate(), toEpochSeconds.shortDate())
}

@Composable
private fun Long.shortDate(): String {
    val date: LocalDate = Instant.fromEpochSeconds(this).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return stringResource(Res.string.date_short, date.day, date.month.ordinal + 1)
}

private val KIND_CHIPS: List<Pair<ChatKind, StringResource>> = listOf(
    ChatKind.Private to Res.string.search_filter_private,
    ChatKind.Group to Res.string.search_filter_groups,
    ChatKind.Channel to Res.string.search_filter_channels,
)

/** `null` = kdykoli, tedy zrušit období. */
private val PERIODS: List<Pair<SearchPeriod?, StringResource>> = listOf(
    null to Res.string.search_filter_any_time,
    SearchPeriod.Week to Res.string.search_filter_week,
    SearchPeriod.Month to Res.string.search_filter_month,
    SearchPeriod.Year to Res.string.search_filter_year,
)
