package eu.tintera.plume.features.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.LIVE_FOREVER
import eu.tintera.plume.core.telegram.LIVE_LOCATION_PERIODS
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.location_live_period_15m
import eu.tintera.plume.resources.location_live_period_1h
import eu.tintera.plume.resources.location_live_period_8h
import eu.tintera.plume.resources.location_live_period_body
import eu.tintera.plume.resources.location_live_period_forever
import eu.tintera.plume.resources.location_live_period_title
import org.jetbrains.compose.resources.stringResource

/**
 * Na jak dlouho sdílet živou polohu — doby jako u oficiálních klientů ([LIVE_LOCATION_PERIODS]).
 * Volba rovnou začne sdílet: je to vědomý krok z výběru polohy a potvrzení navíc by jen zdržovalo.
 * Věta nad volbami říká, že se poloha posouvá i se zavřenou aplikací a jak to zastavit —
 * bez toho by to z „živé polohy" nikdo nečekal.
 */
@Composable
internal fun LiveLocationPeriodDialog(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.location_live_period_title)) },
        text = {
            Column {
                Text(stringResource(Res.string.location_live_period_body), modifier = Modifier.padding(bottom = 8.dp))
                LIVE_LOCATION_PERIODS.forEach { period ->
                    Text(
                        text = stringResource(
                            when (period) {
                                15 * 60 -> Res.string.location_live_period_15m
                                60 * 60 -> Res.string.location_live_period_1h
                                8 * 60 * 60 -> Res.string.location_live_period_8h
                                LIVE_FOREVER -> Res.string.location_live_period_forever
                                else -> Res.string.location_live_period_forever
                            },
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(period) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) } },
    )
}
