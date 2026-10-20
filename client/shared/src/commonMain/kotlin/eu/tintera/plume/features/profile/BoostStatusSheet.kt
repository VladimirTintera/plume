package eu.tintera.plume.features.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.BoostStatus
import eu.tintera.plume.core.ui.ActionButtonGroup
import eu.tintera.plume.core.ui.GroupAction
import eu.tintera.plume.core.ui.GroupActionText
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.boosts_copy_link
import eu.tintera.plume.resources.boosts_failed
import eu.tintera.plume.resources.boosts_help
import eu.tintera.plume.resources.boosts_level
import eu.tintera.plume.resources.boosts_max_level
import eu.tintera.plume.resources.boosts_missing
import eu.tintera.plume.resources.boosts_premium_members
import eu.tintera.plume.resources.boosts_share_link
import eu.tintera.plume.resources.boosts_title
import eu.tintera.plume.resources.boosts_total
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Stav boostů skupiny nebo kanálu pro správce: úroveň, kolik boostů chybí do další, kolik členů
 * má Premium (jen ti boostovat můžou) a odkaz, kterým o boost členy požádat. Boostovat samo
 * Plume neumí — chce to Premium (`docs/roadmap.md`, „Vědomě mimo").
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BoostStatusSheet(
    sheet: BoostSheet,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            SheetTitle(stringResource(Res.string.boosts_title))
            val status = sheet.status
            when {
                status != null -> BoostStatusContent(status, onCopy, onShare)
                sheet.failed -> Text(
                    stringResource(Res.string.boosts_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoostStatusContent(status: BoostStatus, onCopy: () -> Unit, onShare: () -> Unit) {
    Text(
        stringResource(Res.string.boosts_level, status.level),
        style = MaterialTheme.typography.headlineSmallEmphasized,
    )
    Text(
        listOf(
            pluralStringResource(Res.plurals.boosts_total, status.boostCount, status.boostCount.grouped()),
            if (status.nextLevelBoosts == 0) {
                stringResource(Res.string.boosts_max_level)
            } else {
                pluralStringResource(Res.plurals.boosts_missing, status.missingBoosts, status.missingBoosts.grouped(), status.level + 1)
            },
        ).joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    // Průběh, ne čekání: kolik z cesty k další úrovni je za námi.
    LinearWavyProgressIndicator(progress = { status.progress }, modifier = Modifier.fillMaxWidth())
    if (status.premiumMembers > 0) {
        Text(
            pluralStringResource(
                Res.plurals.boosts_premium_members,
                status.premiumMembers,
                status.premiumMembers.grouped(),
                status.premiumPercentage.toInt(),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Text(
        stringResource(Res.string.boosts_help),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (status.boostUrl.isNotBlank()) {
        val copy = stringResource(Res.string.boosts_copy_link)
        val share = stringResource(Res.string.boosts_share_link)
        ActionButtonGroup(
            listOf(
                GroupAction(copy, onCopy, weight = 1f) { modifier, source ->
                    FilledTonalButton(onClick = onCopy, modifier = modifier, interactionSource = source, shapes = ButtonDefaults.shapes()) {
                        GroupActionText(copy)
                    }
                },
                GroupAction(share, onShare, weight = 1f) { modifier, source ->
                    Button(onClick = onShare, modifier = modifier, interactionSource = source, shapes = ButtonDefaults.shapes()) {
                        GroupActionText(share)
                    }
                },
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}
