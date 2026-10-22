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
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import eu.tintera.plume.core.telegram.BoostSource
import eu.tintera.plume.core.telegram.ChatBoostEntry
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.GroupedColumn
import eu.tintera.plume.core.ui.asDate
import eu.tintera.plume.resources.boosts_list_title
import eu.tintera.plume.resources.boosts_load_more
import eu.tintera.plume.resources.boosts_no_winner
import eu.tintera.plume.resources.boosts_expires
import eu.tintera.plume.resources.boosts_source_gift_code
import eu.tintera.plume.resources.boosts_source_giveaway
import eu.tintera.plume.resources.boosts_count
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
    onLoadMore: () -> Unit,
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
                status != null -> {
                    BoostStatusContent(status, onCopy, onShare)
                    BoostList(sheet, onLoadMore)
                }
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

/**
 * Kdo chat boostuje (`getChatBoosts`): tvář a jméno, kolik boostů, odkud (dárkový kód, soutěž;
 * Premium se nepíše — je to běžný případ) a do kdy platí. Bez boostů se oddíl nekreslí, stav
 * nad ním už říká „0 boostů"; selhání seznamu taky ne — stav je hlavní a ten se načetl.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BoostList(sheet: BoostSheet, onLoadMore: () -> Unit) {
    val entries = sheet.entries
    when {
        entries == null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
        entries.isEmpty() -> Unit
        else -> {
            Text(
                stringResource(Res.string.boosts_list_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )
            GroupedColumn {
                entries.forEach { BoostRow(it) }
            }
            if (sheet.nextOffset != null) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (sheet.loadingMore) {
                        LoadingIndicator()
                    } else {
                        TextButton(onClick = onLoadMore, shapes = ButtonDefaults.shapes()) {
                            Text(stringResource(Res.string.boosts_load_more))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoostRow(entry: ChatBoostEntry) {
    val booster = entry.booster
    val name = booster?.title ?: stringResource(Res.string.boosts_no_winner)
    val details = listOfNotNull(
        stringResource(Res.string.boosts_expires, entry.expiresAt.asDate()),
        when (entry.source) {
            BoostSource.Premium -> null
            BoostSource.GiftCode -> stringResource(Res.string.boosts_source_gift_code)
            // U soutěže bez výherce to říká už jméno.
            BoostSource.Giveaway -> stringResource(Res.string.boosts_source_giveaway).takeIf { booster != null }
        },
    ).joinToString(" · ")
    ListItem(
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(details) },
        leadingContent = {
            Avatar(chatId = booster?.id ?: 0, title = name, avatarFileId = booster?.avatarFileId, size = 40.dp)
        },
        // Víc boostů od téhož (Premium má víc slotů) jako počet vpravo.
        trailingContent = entry.count.takeIf { it > 1 }?.let { count ->
            { Text(stringResource(Res.string.boosts_count, count), style = MaterialTheme.typography.labelLarge.tabularNumbers()) }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

