package eu.tintera.plume.features.settings

import eu.tintera.plume.core.ui.highlightAnchor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.Birthdate
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.birthdate_done
import eu.tintera.plume.resources.birthdate_none
import eu.tintera.plume.resources.birthdate_privacy_hint
import eu.tintera.plume.resources.birthdate_remove
import eu.tintera.plume.resources.birthdate_row
import eu.tintera.plume.resources.birthdate_show_year
import eu.tintera.plume.resources.cake_24dp
import eu.tintera.plume.resources.common_cancel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock
import kotlin.time.Instant
import eu.tintera.plume.core.ui.SwitchListItem

/**
 * Datum narození v úpravě profilu: řádek s datem (ťuknutí otevře kalendář) a pod ním „Ukazovat
 * rok". Telegram rok nevyžaduje — bez něj kontakty uvidí jen den a měsíc a věk se nikde
 * neukáže. Ukládá se tlačítkem Uložit.
 *
 * **Volba roku je ve formuláři, ne v dialogu s kalendářem.** V dialogu se kalendář s dalším
 * řádkem nevešel: obsah `DatePickerDialog` leží v `Box` s omezenou výškou a stlačený kalendář
 * kreslil první týden přes názvy dnů (ověřeno na emulátoru).
 *
 * @param value datum z kalendáře, i s rokem.
 * @param shown co se uloží a ukáže (bez roku, když je volba vypnutá).
 */
@Composable
internal fun BirthdateRows(
    value: Birthdate?,
    shown: Birthdate?,
    showYear: Boolean,
    onChange: (Birthdate?) -> Unit,
    onShowYearChange: (Boolean) -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    // Kalendář otevřela volba roku (datum ho nemělo): po výběru se rok zapne.
    var pickingForYear by rememberSaveable { mutableStateOf(false) }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(Res.drawable.cake_24dp), contentDescription = null) },
        headlineContent = { Text(stringResource(Res.string.birthdate_row)) },
        supportingContent = { Text(shown?.label() ?: stringResource(Res.string.birthdate_none)) },
        modifier = Modifier
            .highlightAnchor(stringResource(Res.string.birthdate_row))
            .clickable {
                pickingForYear = false
                picking = true
            },
    )
    if (value != null) {
        // Datum bez roku (uložené dřív): rok musí přijít z kalendáře, zapnutí ho otevře.
        val toggle: (Boolean) -> Unit = { on ->
            if (on && value.year == null) {
                pickingForYear = true
                picking = true
            } else {
                onShowYearChange(on)
            }
        }
        SwitchListItem(
            checked = showYear && value.year != null,
            onCheckedChange = toggle,
            headlineContent = { Text(stringResource(Res.string.birthdate_show_year)) },
            supportingContent = { Text(stringResource(Res.string.birthdate_privacy_hint)) },
            // Prázdné místo velikosti ikony: text stojí pod „Datum narození", ke kterému patří.
            leadingContent = { Spacer(Modifier.size(24.dp)) },
        )
    }
    if (picking) {
        BirthdatePickerDialog(
            initial = value,
            onConfirm = { picked ->
                picking = false
                onChange(picked)
                if (picked != null && pickingForYear) onShowYearChange(true)
            },
            onDismiss = { picking = false },
        )
    }
}

/**
 * Kalendář M3 pro datum narození. Vybírá se celé datum (kalendář rok mít musí); zda se rok
 * ukáže, řídí volba ve formuláři. Datum bez roku se otevře v letošku.
 *
 * Kalendář počítá v milisekundách UTC; převádí se přes půlnoc UTC, aby den neuskočil podle
 * časového pásma.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BirthdatePickerDialog(
    initial: Birthdate?,
    onConfirm: (Birthdate?) -> Unit,
    onDismiss: () -> Unit,
    /** Popisek potvrzení; „Hotovo" ve vlastním profilu, „Navrhnout" u návrhu kontaktu. */
    confirmLabel: StringResource = Res.string.birthdate_done,
) {
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.let { it.inYear(it.year ?: today.year).utcMillis() },
        yearRange = MIN_YEAR..today.year,
        selectableDates = remember(today) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= today.utcMillis()
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
                    onConfirm(Birthdate.of(Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.UTC).date, withYear = true))
                },
                enabled = state.selectedDateMillis != null,
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(confirmLabel)) }
        },
        dismissButton = {
            Row {
                if (initial != null) {
                    TextButton(onClick = { onConfirm(null) }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(Res.string.birthdate_remove), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_cancel)) }
            }
        },
    ) {
        DatePicker(
            state = state,
            title = {
                Text(stringResource(Res.string.birthdate_row), modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp))
            },
            showModeToggle = true,
        )
    }
}

private fun LocalDate.utcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

/** Nejstarší rok v kalendáři — dál do minulosti by se jen déle listovalo. */
private const val MIN_YEAR = 1900
