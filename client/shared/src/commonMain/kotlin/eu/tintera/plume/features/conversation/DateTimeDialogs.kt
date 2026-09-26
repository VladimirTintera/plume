package eu.tintera.plume.features.conversation

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.search_filter_apply
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * Dny od dneška po den, kdy vyprší [maxAheadSeconds] od [now] — obojí v MÍSTNÍM pásmu.
 * [utcTimeMillis] je půlnoc dne v UTC, jak ji výběr data posílá.
 */
internal fun futureDaySelectable(utcTimeMillis: Long, now: Long, maxAheadSeconds: Long, zone: TimeZone): Boolean {
    val day = Instant.fromEpochMilliseconds(utcTimeMillis).toLocalDateTime(TimeZone.UTC).date
    val first = Instant.fromEpochSeconds(now).toLocalDateTime(zone).date
    val last = Instant.fromEpochSeconds(now + maxAheadSeconds).toLocalDateTime(zone).date
    return day in first..last
}

/**
 * Výběr dne v budoucnosti (konec ankety, naplánovaná zpráva). Vrací **půlnoc vybraného dne
 * v UTC** — čas k ní se skládá v místním pásmu (`closeDateOf`), jinak by se den posunul.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FutureDayDialog(now: Long, maxAheadSeconds: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val zone = TimeZone.currentSystemDefault()
    val state = rememberDatePickerState(
        // Dnešek je předvybraný: nejčastěji se plánuje „na později dnes" a stačí potvrdit.
        // Výběr pracuje s půlnocí dne v UTC, ne v místním pásmu (viz `closeDateOf`).
        initialSelectedDateMillis = remember(now) {
            Instant.fromEpochSeconds(now).toLocalDateTime(zone).date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        },
        selectableDates = remember(now, maxAheadSeconds) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    futureDaySelectable(utcTimeMillis, now, maxAheadSeconds, zone)
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = { state.selectedDateMillis?.let(onPick) },
            ) { Text(stringResource(Res.string.search_filter_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

/** Hodina a minuta v místním čase, jak ho uživatel zná; výchozí z [initial]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeOfDayDialog(title: String, initial: Long, onPick: (hour: Int, minute: Int) -> Unit, onDismiss: () -> Unit) {
    val start = remember(initial) { Instant.fromEpochSeconds(initial).toLocalDateTime(TimeZone.currentSystemDefault()) }
    val state = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute)
    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            TextButton(onClick = { onPick(state.hour, state.minute) }) { Text(stringResource(Res.string.search_filter_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    ) {
        TimePicker(state = state)
    }
}
