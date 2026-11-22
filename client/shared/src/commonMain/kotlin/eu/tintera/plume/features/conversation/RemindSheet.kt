package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ui.rememberRestoringScrollState
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import androidx.compose.material3.ShapeDefaults
import eu.tintera.plume.core.ui.SheetTitle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.snooze.snoozeUntil
import eu.tintera.plume.core.ui.SheetActionRow
import eu.tintera.plume.features.chats.SnoozeOptionRows
import eu.tintera.plume.features.chats.SnoozeTime
import eu.tintera.plume.resources.edit_calendar_24dp
import eu.tintera.plume.resources.auto_awesome_24dp
import eu.tintera.plume.resources.remind_suggesting
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import eu.tintera.plume.resources.remind_from_message
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.remind_pick
import eu.tintera.plume.resources.remind_text
import eu.tintera.plume.resources.remind_title
import eu.tintera.plume.resources.schedule_24dp
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource

/**
 * Kdy zprávu připomenout. Rychlé volby jsou tytéž jako u odložení chatu (za hodinu, dnes večer,
 * zítra ráno — `SnoozeOptionRows`), protože jde o totéž rozhodnutí „vrať se mi k tomu později";
 * přesný čas vybere [onPickTime] dialogy z plánování zpráv.
 *
 * [onRemind] dostane okamžik v sekundách od epochy.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RemindSheet(
    /** Termín ze zprávy (sekundy od epochy); `null` = ve zprávě žádný není. */
    suggested: Long?,
    /** Termín hledá model v zařízení — místo řádku se zatím točí „Hledám termín ve zprávě…". */
    suggesting: Boolean = false,
    /** [suggested] je odhad modelu, ne pravidel: řádek má ikonu AI, ať je vidět, odkud je. */
    fromModel: Boolean = false,
    onRemind: (at: Long) -> Unit,
    onPickTime: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberExpandedSheetState()
    val zone = remember { TimeZone.currentSystemDefault() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.verticalScroll(rememberRestoringScrollState())) {
            SheetTitle(stringResource(Res.string.remind_title), modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
            Text(
                text = stringResource(Res.string.remind_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp),
            )
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                // Termín ze zprávy („sraz v pátek v 15:00") jako první volba, ve vlastním bloku:
                // je to návrh z textu, ne pevná doba, a může se splést.
                if (suggesting) {
                    SheetActionRow(
                        icon = Res.drawable.auto_awesome_24dp,
                        label = stringResource(Res.string.remind_suggesting),
                        shape = ShapeDefaults.LargeIncreased,
                        onClick = {},
                        enabled = false,
                        trailing = { LoadingIndicator(Modifier.size(24.dp)) },
                    )
                    Spacer(Modifier.height(12.dp))
                } else if (suggested != null) {
                    SheetActionRow(
                        icon = if (fromModel) Res.drawable.auto_awesome_24dp else Res.drawable.edit_calendar_24dp,
                        label = stringResource(Res.string.remind_from_message),
                        shape = ShapeDefaults.LargeIncreased,
                        onClick = { onRemind(suggested) },
                        trailing = { SnoozeTime(suggested * 1000) },
                    )
                    Spacer(Modifier.height(12.dp))
                }
                SnoozeOptionRows(
                    onSnooze = { option -> onRemind(snoozeUntil(option, Clock.System.now(), zone).epochSeconds) },
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    reserveIconSpace = false,
                )
                Spacer(Modifier.height(12.dp))
                SheetActionRow(
                    icon = Res.drawable.schedule_24dp,
                    label = stringResource(Res.string.remind_pick),
                    shape = ShapeDefaults.LargeIncreased,
                    onClick = onPickTime,
                )
            }
        }
    }
}
