package eu.tintera.plume.features.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.LIVE_FOREVER
import eu.tintera.plume.core.telegram.LIVE_LOCATION_PERIODS
import eu.tintera.plume.core.telegram.PROXIMITY_ALERT_RADII
import eu.tintera.plume.core.ui.ChoiceListItem
import eu.tintera.plume.core.ui.GROUP_SPACING
import eu.tintera.plume.core.ui.GroupedColumn
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.distanceUiText
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.location_live_period_15m
import eu.tintera.plume.resources.location_live_period_1h
import eu.tintera.plume.resources.location_live_period_8h
import eu.tintera.plume.resources.location_live_period_body
import eu.tintera.plume.resources.location_live_period_forever
import eu.tintera.plume.resources.location_live_period_title
import eu.tintera.plume.resources.location_off_24dp
import eu.tintera.plume.resources.location_proximity_body
import eu.tintera.plume.resources.location_proximity_off
import eu.tintera.plume.resources.location_proximity_title
import eu.tintera.plume.resources.share_location_24dp
import eu.tintera.plume.resources.timer_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Na jak dlouho sdílet živou polohu — doby jako u oficiálních klientů ([LIVE_LOCATION_PERIODS]).
 * Volba rovnou začne sdílet: je to vědomý krok z výběru polohy a potvrzení navíc by jen zdržovalo.
 * Věta nad volbami říká, že se poloha posouvá i se zavřenou aplikací a jak to zastavit —
 * bez toho by to z „živé polohy" nikdo nečekal.
 *
 * Sheet s blokem akcí, ne dialog: dřív to byl `AlertDialog` s řádky textu v primární barvě,
 * tedy odkazy bez tvaru — vzor M2.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveLocationPeriodSheet(onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            SheetHeader(stringResource(Res.string.location_live_period_title), stringResource(Res.string.location_live_period_body))
            GroupedColumn(Modifier.padding(horizontal = 16.dp)) {
                LIVE_LOCATION_PERIODS.forEach { period ->
                    ActionRow(
                        label = stringResource(
                            when (period) {
                                15 * 60 -> Res.string.location_live_period_15m
                                60 * 60 -> Res.string.location_live_period_1h
                                8 * 60 * 60 -> Res.string.location_live_period_8h
                                LIVE_FOREVER -> Res.string.location_live_period_forever
                                else -> Res.string.location_live_period_forever
                            },
                        ),
                        // „Dokud nezastavím" se od dob liší: nekončí samo.
                        icon = if (period == LIVE_FOREVER) Res.drawable.share_location_24dp else Res.drawable.timer_24dp,
                        onClick = { onPick(period) },
                    )
                }
            }
        }
    }
}

/**
 * Do jaké vzdálenosti upozornit, až se někdo z chatu přiblíží ([PROXIMITY_ALERT_RADII]).
 * Nastavená vzdálenost je vyznačená a „Vypnout upozornění" je jen u zapnutého — u vypnutého
 * by nic nedělalo; je to akce, ne volba, proto má vlastní blok. Vlastní poloměr z jiného
 * klienta (třeba 300 m) se do voleb zařadí, ať je vidět, co platí.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProximityAlertSheet(currentRadius: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val radii = (PROXIMITY_ALERT_RADII + listOfNotNull(currentRadius.takeIf { it > 0 })).distinct().sorted()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            SheetHeader(stringResource(Res.string.location_proximity_title), stringResource(Res.string.location_proximity_body))
            Column(Modifier.selectableGroup()) {
                radii.forEachIndexed { index, radius ->
                    ChoiceListItem(
                        label = distanceUiText(radius).resolve(),
                        selected = radius == currentRadius,
                        index = index,
                        count = radii.size,
                        onClick = { onPick(radius) },
                    )
                }
            }
            if (currentRadius > 0) {
                Spacer(Modifier.height(GROUP_SPACING))
                GroupedColumn(Modifier.padding(horizontal = 16.dp)) {
                    ActionRow(
                        label = stringResource(Res.string.location_proximity_off),
                        icon = Res.drawable.location_off_24dp,
                        onClick = { onPick(0) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetHeader(title: String, body: String) {
    SheetTitle(title, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp))
    Text(
        body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 4.dp, bottom = 16.dp),
    )
}

@Composable
private fun ActionRow(label: String, icon: DrawableResource, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    ) { Text(label) }
}
