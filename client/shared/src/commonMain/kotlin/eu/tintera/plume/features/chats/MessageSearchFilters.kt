package eu.tintera.plume.features.chats

import eu.tintera.plume.resources.search_filter_choose_period
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import eu.tintera.plume.resources.search_filter_apply
import eu.tintera.plume.resources.search_filter_channels
import eu.tintera.plume.resources.search_filter_custom
import eu.tintera.plume.resources.search_filter_groups
import eu.tintera.plume.resources.search_filter_month
import eu.tintera.plume.resources.search_filter_private
import eu.tintera.plume.resources.search_filter_range
import eu.tintera.plume.resources.search_filter_week
import eu.tintera.plume.resources.search_filter_year_short
import eu.tintera.plume.resources.search_filter_month_short
import eu.tintera.plume.resources.search_filter_week_short
import eu.tintera.plume.resources.search_filter_year
import eu.tintera.plume.resources.search_source_chats
import eu.tintera.plume.resources.search_source_public
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
 * Filtry nad výsledky hledání: druh chatu a období.
 *
 * - **Druh chatu je connected button group** (Soukromé / Skupiny / Kanály, `ToggleButton`
 *   se spojenými tvary). Vybrané tlačítko je plné, s fajfkou a celé zakulacené, stejně jako
 *   záložky v profilu. Druhé ťuknutí výběr zruší — na rozdíl od záložek smí být filtr prázdný.
 * - **Období je split button**: levá část období zapne a vypne a ukazuje které, pravá (šipka)
 *   otevře nabídku s výběrem. Samo „vypnout" je ťuknutí na levou část, proto v nabídce není
 *   „Kdykoli". Zapnuté období je plné jako vybraný druh chatu — obojí je „filtr platí".
 * - **Řádek jde posouvat do strany** — na úzkém telefonu se skupina i split button nevejdou.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MessageSearchFilters(filter: MessageSearchFilter, onChange: (MessageSearchFilter) -> Unit) {
    var periodMenu by remember { mutableStateOf(false) }
    var pickRange by rememberSaveable { mutableStateOf(false) }
    // Které období levá část zapne; pamatuje si poslední vybrané, i když je zrovna vypnuté.
    var lastPeriod by remember { mutableStateOf<SearchPeriod>(SearchPeriod.Week) }
    filter.period?.let { if (it != lastPeriod) lastPeriod = it }

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            KIND_CHIPS.forEachIndexed { index, (kind, label) ->
                val selected = filter.chatKind == kind
                ToggleButton(
                    checked = selected,
                    onCheckedChange = { onChange(filter.copy(chatKind = if (selected) null else kind)) },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        KIND_CHIPS.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                    // Užší odsazení: s výchozím se skupina i split button na telefon nevešly.
                    contentPadding = PaddingValues(horizontal = BUTTON_PADDING),
                ) {
                    AnimatedVisibility(visible = selected) {
                        Icon(
                            painterResource(Res.drawable.check_24dp),
                            contentDescription = null,
                            modifier = Modifier.padding(end = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
                        )
                    }
                    Text(stringResource(label))
                }
            }
        }

        val active = filter.period != null
        val rotation by animateFloatAsState(if (periodMenu) 180f else 0f, label = "period-chevron")
        Box {
            SplitButtonLayout(
                leadingButton = {
                    SplitButtonDefaults.LeadingButton(
                        onClick = { onChange(filter.copy(period = if (active) null else lastPeriod)) },
                        colors = if (active) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                        contentPadding = PaddingValues(start = BUTTON_PADDING, end = BUTTON_PADDING / 2),
                        // Čtečka obrazovky má slyšet, jestli období platí — plnou barvu nevidí.
                        modifier = Modifier.semantics { selected = active },
                    ) {
                        AnimatedVisibility(visible = active) {
                            Icon(
                                painterResource(Res.drawable.check_24dp),
                                contentDescription = null,
                                modifier = Modifier.padding(end = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
                            )
                        }
                        // Krátce („Týden"), plné znění je v nabídce: s „Poslední týden" se řádek
                        // v češtině nevešel a šipka nabídky skončila za okrajem.
                        Text(lastPeriod.shortLabel())
                    }
                },
                trailingButton = {
                    SplitButtonDefaults.TrailingButton(
                        checked = periodMenu,
                        onCheckedChange = { periodMenu = it },
                        colors = if (active) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                    ) {
                        Icon(
                            painterResource(Res.drawable.expand_more_24dp),
                            contentDescription = stringResource(Res.string.search_filter_choose_period),
                            modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize).graphicsLayer { rotationZ = rotation },
                        )
                    }
                },
            )
            /*
             * Expressive menu: vybrané období je zaškrtnuté. Předvolby a vlastní rozsah jsou dvě
             * skupiny — rozsah otevírá další krok (výběr dat) a pod sebou ukazuje, jaký je.
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
                        // Ťuknutí na už vybraný rozsah ho nezruší, ale otevře výběr znovu.
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
private fun SearchPeriod.shortLabel(): String = when (this) {
    SearchPeriod.Week -> stringResource(Res.string.search_filter_week_short)
    SearchPeriod.Month -> stringResource(Res.string.search_filter_month_short)
    SearchPeriod.Year -> stringResource(Res.string.search_filter_year_short)
    is SearchPeriod.Custom -> label()
}

@Composable
private fun Long.shortDate(): String {
    val date: LocalDate = Instant.fromEpochSeconds(this).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return stringResource(Res.string.date_short, date.day, date.month.ordinal + 1)
}

/**
 * U hashtagu odkud: vlastní chaty, nebo veřejné příspěvky kanálů (`searchPublicMessagesByTag`).
 *
 * Tentýž prvek jako druh chatu — connected button group —, jen vybrané je vždycky právě jedno:
 * je to volba zdroje, ne filtr, který jde vypnout. Přepínač místo dvou sekcí pod sebou: obě jsou
 * stránkované a jedna by se k druhé nedostala, dokud by se nedotáhla celá.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HashtagSourceToggle(filter: MessageSearchFilter, onChange: (MessageSearchFilter) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        SOURCES.forEachIndexed { index, (public, label) ->
            val selected = filter.publicPosts == public
            ToggleButton(
                checked = selected,
                // Druh chatu a období se na veřejné příspěvky nevztahují a UI je schová — zůstat
                // v filtru nesmí, jinak by prázdný výsledek radil „zkus změnit filtr", který není vidět.
                onCheckedChange = {
                    if (!selected) onChange(if (public) MessageSearchFilter(publicPosts = true) else filter.copy(publicPosts = false))
                },
                shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes() else ButtonGroupDefaults.connectedTrailingButtonShapes(),
                modifier = Modifier.weight(1f).semantics { this.selected = selected },
            ) {
                AnimatedVisibility(visible = selected) {
                    Icon(
                        painterResource(Res.drawable.check_24dp),
                        contentDescription = null,
                        modifier = Modifier.padding(end = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
                    )
                }
                Text(stringResource(label), maxLines = 1)
            }
        }
    }
}

private val SOURCES: List<Pair<Boolean, StringResource>> = listOf(
    false to Res.string.search_source_chats,
    true to Res.string.search_source_public,
)

private val KIND_CHIPS: List<Pair<ChatKind, StringResource>> = listOf(
    ChatKind.Private to Res.string.search_filter_private,
    ChatKind.Group to Res.string.search_filter_groups,
    ChatKind.Channel to Res.string.search_filter_channels,
)

private val BUTTON_PADDING = 12.dp

/** Předvolby v nabídce; vypnout období jde levou částí split buttonu. */
private val PERIODS: List<Pair<SearchPeriod, StringResource>> = listOf(
    SearchPeriod.Week to Res.string.search_filter_week,
    SearchPeriod.Month to Res.string.search_filter_month,
    SearchPeriod.Year to Res.string.search_filter_year,
)
