package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DateRangePickerDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ClearHistoryRights
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.MediumButton
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.asDate
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.clear_history_all
import eu.tintera.plume.resources.clear_history_all_text
import eu.tintera.plume.resources.clear_history_confirm
import eu.tintera.plume.resources.clear_history_for_all
import eu.tintera.plume.resources.clear_history_for_all_group
import eu.tintera.plume.resources.clear_history_period
import eu.tintera.plume.resources.clear_history_period_pick
import eu.tintera.plume.resources.clear_history_period_range
import eu.tintera.plume.resources.clear_history_pick_title
import eu.tintera.plume.resources.clear_history_title
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.menu_select
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Vymazání historie z nabídky ⋮: celou, nebo jen vybrané dny (kde to TDLib umí,
 * [ClearHistoryRights.byDate]), a s volbou „i pro protějšek", kde to jde. Chat zůstane
 * v seznamu — od „Smazat chat" se liší právě tím.
 *
 * [onConfirm] dostane `from`/`to` v sekundách epochy (oba konce včetně, MÍSTNÍ dny), nebo `null`
 * pro celou historii.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClearHistorySheet(
    rights: ClearHistoryRights,
    /** Jméno protějšku v soukromém chatu; `null` ve skupině. */
    peerName: String?,
    /** Co se stane s celou historií; téma a vlákno nezůstanou „prázdné v seznamu" jako chat. */
    allText: StringResource = Res.string.clear_history_all_text,
    onConfirm: (from: Int?, to: Int?, forAll: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var byDate by rememberSaveable { mutableStateOf(false) }
    var from by rememberSaveable { mutableStateOf<Int?>(null) }
    var to by rememberSaveable { mutableStateOf<Int?>(null) }
    var forAll by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf(false) }
    // Smazat i ostatním jde po dnech jen v soukromém chatu (TDLib `revoke`).
    val canForAll = if (byDate) rights.forAllByDate else rights.forAll

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetTitle(stringResource(Res.string.clear_history_title), modifier = Modifier.padding(horizontal = 8.dp))

            // Bez mazání po dnech není z čeho vybírat: jediná předvybraná volba by jen mátla.
            if (!rights.byDate) {
                Text(
                    stringResource(allText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            } else Column(verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING)) {
                val count = 2
                ClearOption(
                    selected = !byDate,
                    title = stringResource(Res.string.clear_history_all),
                    text = stringResource(allText),
                    index = 0,
                    count = count,
                    onSelect = { byDate = false },
                )
                run {
                    val start = from
                    val end = to
                    ClearOption(
                        selected = byDate,
                        title = stringResource(Res.string.clear_history_period),
                        text = if (start != null && end != null) {
                            // Jeden den jednou: „18. října", ne „18. října – 18. října".
                            if (start.asDate() == end.asDate()) {
                                start.asDate()
                            } else {
                                stringResource(Res.string.clear_history_period_range, start.asDate(), end.asDate())
                            }
                        } else {
                            stringResource(Res.string.clear_history_period_pick)
                        },
                        index = 1,
                        count = count,
                        // Výběr řádku rovnou otevře kalendář — bez dnů by volba nic neznamenala.
                        onSelect = {
                            byDate = true
                            picking = true
                        },
                    )
                }
            }

            if (canForAll) {
                ListItem(
                    headlineContent = {
                        Text(
                            if (peerName != null) {
                                stringResource(Res.string.clear_history_for_all, peerName)
                            } else {
                                stringResource(Res.string.clear_history_for_all_group)
                            },
                        )
                    },
                    leadingContent = { Checkbox(checked = forAll, onCheckedChange = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier
                        .clip(groupItemShape(0, 1))
                        .toggleable(value = forAll, role = Role.Checkbox, onValueChange = { forAll = it }),
                )
            }

            MediumButton(
                onClick = {
                    onDismiss()
                    if (byDate) onConfirm(from, to, forAll && canForAll) else onConfirm(null, null, forAll && canForAll)
                },
                enabled = !byDate || (from != null && to != null),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.clear_history_confirm))
            }
        }
    }

    if (picking) {
        ClearHistoryRangeDialog(
            onPick = { start, end ->
                from = start
                to = end
                picking = false
            },
            onDismiss = {
                picking = false
                // Kalendář zavřený bez výběru: bez dnů zůstává volba na celé historii.
                if (from == null) byDate = false
            },
        )
    }
}

@Composable
private fun ClearOption(
    selected: Boolean,
    title: String,
    text: String,
    index: Int,
    count: Int,
    onSelect: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(text) },
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .clip(groupItemShape(index, count, selected = selected))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    )
}

/**
 * Rozsah dnů k vymazání. Jako u skoku na datum ([JumpToDateDialog]) počítá kalendář v UTC,
 * takže se dny čtou přes UTC a teprve pak se vezme jejich MÍSTNÍ půlnoc; konec je poslední
 * vteřina posledního dne. Budoucí dny vybrat nejde. Jediný den = začátek i konec stejný.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClearHistoryRangeDialog(onPick: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = remember { Clock.System.now().toLocalDateTime(zone).date }
    val state = rememberDateRangePickerState(
        selectableDates = remember(today) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    utcTimeMillis <= today.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

                override fun isSelectableYear(year: Int): Boolean = year <= today.year
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val start = state.selectedStartDateMillis ?: return@TextButton
                    val end = state.selectedEndDateMillis ?: start
                    val first = utcDay(start)
                    val last = utcDay(end)
                    onPick(
                        first.atStartOfDayIn(zone).epochSeconds.toInt(),
                        (last.plus(DatePeriod(days = 1)).atStartOfDayIn(zone).epochSeconds - 1).toInt(),
                    )
                },
                enabled = state.selectedStartDateMillis != null,
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(Res.string.menu_select)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_cancel)) }
        },
    ) {
        DateRangePicker(
            state = state,
            /*
             * Výchozí hlavička rozsahu („Datum zahájení – Datum ukončení") je `headlineLarge`
             * stavěná na celou obrazovku; v dialogu se zalomila po písmenech. Menší písmo
             * a bez přepínače na zadání textem se vejde.
             */
            headline = {
                ProvideTextStyle(MaterialTheme.typography.titleLarge) {
                    DateRangePickerDefaults.DateRangePickerHeadline(
                        selectedStartDateMillis = state.selectedStartDateMillis,
                        selectedEndDateMillis = state.selectedEndDateMillis,
                        displayMode = state.displayMode,
                        dateFormatter = remember { DatePickerDefaults.dateFormatter() },
                        modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                    )
                }
            },
            showModeToggle = false,
            title = {
                Text(
                    stringResource(Res.string.clear_history_pick_title),
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                )
            },
            // Rozsahový kalendář roluje měsíce sám; bez omezení výšky by vytlačil tlačítka z dialogu.
            modifier = Modifier.weight(1f),
        )
    }
}

private fun utcDay(millis: Long): LocalDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
