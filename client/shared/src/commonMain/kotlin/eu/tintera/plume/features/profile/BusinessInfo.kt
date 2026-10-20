package eu.tintera.plume.features.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.MINUTES_PER_DAY
import eu.tintera.plume.core.telegram.OpeningInterval
import eu.tintera.plume.core.telegram.OpeningState
import eu.tintera.plume.core.telegram.byDay
import eu.tintera.plume.core.telegram.isWholeDay
import eu.tintera.plume.core.telegram.stateAt
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.groupedItem
import eu.tintera.plume.core.ui.longName
import eu.tintera.plume.core.ui.onName
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.business_always_open
import eu.tintera.plume.resources.business_closed
import eu.tintera.plume.resources.business_closed_day
import eu.tintera.plume.resources.business_hours
import eu.tintera.plume.resources.business_hours_local
import eu.tintera.plume.resources.business_interval
import eu.tintera.plume.resources.business_open_until
import eu.tintera.plume.resources.business_opens_at
import eu.tintera.plume.resources.business_opens_day
import eu.tintera.plume.resources.business_opens_tomorrow
import kotlin.time.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource

/** Minuta týdne podle hodin v telefonu: 0 = pondělí 0:00. */
private fun localMinuteOfWeek(): Int {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return (now.dayOfWeek.isoDayNumber - 1) * MINUTES_PER_DAY + now.hour * 60 + now.minute
}

/** Den v týdnu podle indexu od pondělí (0–6). */
private fun dayOf(index: Int): DayOfWeek = DayOfWeek.entries[index]

/** Minuta dne jako hodiny („9:00", „17:30"); půlnoc na konci úseku je „24:00". */
private fun Int.clock(end: Boolean = false): String {
    val minutes = if (end && this == 0) MINUTES_PER_DAY else this
    return "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}"
}

/**
 * Stav firmy jednou větou: „Otevřeno · do 17:00", „Zavřeno · otevírá zítra v 9:00". Počítá se
 * při složení, ne živě — profil se otevírá na chvíli a minutové překreslování by bylo navíc.
 */
@Composable
internal fun openingStateText(hours: List<OpeningInterval>): String {
    val state = remember(hours) { hours.stateAt(localMinuteOfWeek()) }
    return when (state) {
        OpeningState.AlwaysOpen -> stringResource(Res.string.business_always_open)
        is OpeningState.Open -> stringResource(Res.string.business_open_until, state.closesAt.clock(end = true))
        is OpeningState.Closed -> {
            val opensAt = state.opensAt
            when {
                opensAt == null -> stringResource(Res.string.business_closed)
                state.inDays == 0 -> stringResource(Res.string.business_opens_at, opensAt.clock())
                state.inDays == 1 -> stringResource(Res.string.business_opens_tomorrow, opensAt.clock())
                else -> stringResource(Res.string.business_opens_day, stringResource(dayOf(state.dayIndex).onName()), opensAt.clock())
            }
        }
    }
}

/** Otevírací doba po dnech, dnešek zvýrazněný. Čas je místní, a věta pod tabulkou to říká. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BusinessHoursSheet(hours: List<OpeningInterval>, onDismiss: () -> Unit) {
    val days = remember(hours) { hours.byDay() }
    val today = remember { localMinuteOfWeek() / MINUTES_PER_DAY }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        SheetTitle(
            stringResource(Res.string.business_hours),
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 12.dp),
        )
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            days.forEachIndexed { index, ranges ->
                val isToday = index == today
                Row(
                    Modifier
                        .groupedItem(index, days.size, MaterialTheme.colorScheme.surfaceContainerLowest, edge = 0.dp, pressable = false)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val weight = if (isToday) FontWeight.SemiBold else null
                    Text(
                        stringResource(dayOf(index).longName()),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = weight,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = when {
                            ranges.isEmpty() -> stringResource(Res.string.business_closed_day)
                            ranges.isWholeDay() -> stringResource(Res.string.business_always_open)
                            else -> ranges.map { stringResource(Res.string.business_interval, it.first.clock(), (it.last + 1).clock(end = true)) }
                                .joinToString("\n")
                        },
                        style = MaterialTheme.typography.bodyLarge.tabularNumbers(),
                        fontWeight = weight,
                        color = if (ranges.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                    )
                }
            }
            Text(
                stringResource(Res.string.business_hours_local),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 12.dp),
            )
        }
    }
}
