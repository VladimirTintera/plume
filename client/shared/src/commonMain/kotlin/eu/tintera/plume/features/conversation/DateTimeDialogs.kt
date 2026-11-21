package eu.tintera.plume.features.conversation

import androidx.compose.material3.DatePicker
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimeScroll
import androidx.compose.material3.VibrantTimePickerDialog
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(Res.string.search_filter_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_cancel)) } },
    ) {
        DatePicker(state = state)
    }
}

/** Hodina a minuta v místním čase, jak ho uživatel zná; výchozí z [initial]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeOfDayDialog(title: String, initial: Long, onPick: (hour: Int, minute: Int) -> Unit, onDismiss: () -> Unit) {
    val start = remember(initial) { Instant.fromEpochSeconds(initial).toLocalDateTime(TimeZone.currentSystemDefault()) }
    TimeOfDayDialog(title = title, hour = start.hour, minute = start.minute, onPick = onPick, onDismiss = onDismiss)
}

/**
 * Hodina a minuta bez data — výchozí [hour]:[minute].
 *
 * Expresivní výběr s kolečky (`TimeScroll` ve `VibrantTimePickerDialog`, material3 1.13), ne
 * ciferník: hodina a minuta se točí každá zvlášť a jde to i kolečkem myši. Dialog vlastní nadpis
 * nemá, nese ho první řádek obsahu stylem nadpisu klasického `TimePickerDialog` (`labelMedium`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimeOfDayDialog(title: String, hour: Int, minute: Int, onPick: (hour: Int, minute: Int) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute)
    VibrantTimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(state.hour, state.minute) }, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.search_filter_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_cancel)) } },
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 20.dp),
        )
        TimeScroll(state = state, shapes = TimePickerDefaults.shapes())
    }
}
