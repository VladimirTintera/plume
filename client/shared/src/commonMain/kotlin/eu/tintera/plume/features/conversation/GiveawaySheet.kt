package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.SheetTitle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.GiveawayStatus
import eu.tintera.plume.core.telegram.localizedCountryName
import eu.tintera.plume.core.ui.asDate
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.content_copy_24dp
import eu.tintera.plume.resources.giveaway_code_copied
import eu.tintera.plume.resources.giveaway_copy_code
import eu.tintera.plume.resources.giveaway_refunded
import eu.tintera.plume.resources.giveaway_status_administrator
import eu.tintera.plume.resources.giveaway_status_already_member
import eu.tintera.plume.resources.giveaway_status_country
import eu.tintera.plume.resources.giveaway_status_eligible
import eu.tintera.plume.resources.giveaway_status_failed
import eu.tintera.plume.resources.giveaway_status_not_won
import eu.tintera.plume.resources.giveaway_status_participating
import eu.tintera.plume.resources.giveaway_status_title
import eu.tintera.plume.resources.giveaway_status_won_premium
import eu.tintera.plume.resources.giveaway_status_won_stars
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Jak je na tom tenhle účet se soutěží: účastní se, může se zapojit, vyhrál… Výhra Premium
 * nese dárkový kód — jde zkopírovat; uplatnit ho Plume neumí, věta to říká.
 *
 * Potvrzení kopírování je fajfka v tlačítku, ne snackbar: ten by zůstal pod modálním sheetem.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GiveawayStatusSheet(
    sheet: GiveawaySheet,
    onCopyCode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberExpandedSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetTitle(stringResource(Res.string.giveaway_status_title))
            val status = sheet.status
            when {
                status != null -> StatusContent(status, sheet.codeCopied, onCopyCode)
                sheet.failed -> Text(
                    text = stringResource(Res.string.giveaway_status_failed),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
        }
    }
}

@Composable
private fun StatusContent(status: GiveawayStatus, codeCopied: Boolean, onCopyCode: (String) -> Unit) {
    val text = when (status) {
        GiveawayStatus.Participating -> stringResource(Res.string.giveaway_status_participating)
        GiveawayStatus.Eligible -> stringResource(Res.string.giveaway_status_eligible)
        is GiveawayStatus.AlreadyMember ->
            stringResource(Res.string.giveaway_status_already_member, status.sinceEpochSeconds.toInt().asDate())
        GiveawayStatus.Administrator -> stringResource(Res.string.giveaway_status_administrator)
        is GiveawayStatus.DisallowedCountry -> stringResource(
            Res.string.giveaway_status_country,
            localizedCountryName(status.countryCode) ?: status.countryCode,
        )
        GiveawayStatus.NotWon -> stringResource(Res.string.giveaway_status_not_won)
        GiveawayStatus.Refunded -> stringResource(Res.string.giveaway_refunded)
        is GiveawayStatus.Won -> if (status.giftCode.isNotBlank()) {
            stringResource(Res.string.giveaway_status_won_premium)
        } else {
            pluralStringResource(
                Res.plurals.giveaway_status_won_stars,
                status.stars.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                status.stars.grouped(),
            )
        }
    }
    Text(text = text, style = MaterialTheme.typography.bodyLarge)

    val code = (status as? GiveawayStatus.Won)?.giftCode?.takeIf { it.isNotBlank() } ?: return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Vybrat jde i ručně — kdo kód přepisuje do jiného zařízení, nemusí kopírovat celý.
        SelectionContainer {
            Text(
                text = code,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
        }
    }
    FilledTonalButton(onClick = { onCopyCode(code) }, modifier = Modifier.fillMaxWidth(), shapes = ButtonDefaults.shapes()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                painterResource(if (codeCopied) Res.drawable.check_24dp else Res.drawable.content_copy_24dp),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(stringResource(if (codeCopied) Res.string.giveaway_code_copied else Res.string.giveaway_copy_code))
        }
    }
}
