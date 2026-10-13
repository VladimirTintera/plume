package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.conversation_jump_date_go
import eu.tintera.plume.resources.conversation_jump_to_date
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/**
 * Kalendář pro skok na datum. Vrací začátek vybraného dne v MÍSTNÍM pásmu (sekundy epochy) —
 * „5. října" znamená den, jak ho vidí uživatel, ne v UTC. Kalendář sám počítá v milisekundách
 * UTC, takže se den čte přes UTC a teprve pak se přepočte na místní půlnoc. Budoucí dny vybrat
 * nejde.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JumpToDateDialog(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val zone = remember { TimeZone.currentSystemDefault() }
    val today = remember { Clock.System.now().toLocalDateTime(zone).date }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = today.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
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
                    val millis = state.selectedDateMillis ?: return@TextButton
                    val day: LocalDate = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date
                    onPick(day.atStartOfDayIn(zone).epochSeconds.toInt())
                },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(Res.string.conversation_jump_date_go)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    ) {
        DatePicker(
            state = state,
            title = {
                Text(stringResource(Res.string.conversation_jump_to_date), modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp))
            },
        )
    }
}
