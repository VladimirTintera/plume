package eu.tintera.plume.features.chats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ArchiveSettings
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_archive_keep_folders
import eu.tintera.plume.resources.chats_archive_keep_folders_summary
import eu.tintera.plume.resources.chats_archive_keep
import eu.tintera.plume.resources.chats_archive_keep_summary
import eu.tintera.plume.resources.chats_archive_settings
import eu.tintera.plume.resources.chats_archive_unknown
import eu.tintera.plume.resources.chats_archive_unknown_summary
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Nastavení archivu — sheet z hlavičky archivu, ne sekce v Nastavení.
 *
 * Jsou to volby o tom, co se s archivem děje samo; kdo je hledá, stojí zrovna v archivu.
 * Vlastní sekce Nastavení by je schovala o dvě obrazovky dál a přibyla by pátá položka do
 * seznamu sekcí kvůli dvěma přepínačům.
 */
@Composable
fun ArchiveSettingsSheet(
    settings: ArchiveSettings,
    onArchiveUnknownSenders: (Boolean) -> Unit,
    onKeepUnmutedArchived: (Boolean) -> Unit,
    /** Má účet složky? Bez nich volba o chatech ze složek nemá čeho se týkat. */
    hasFolders: Boolean,
    onKeepFromFoldersArchived: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    // Celý, bez napůl rozbaleného stavu — stejně jako ostatní sheety s přepínači.
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = {
                    Text(
                        stringResource(Res.string.chats_archive_settings),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
            )

            /*
             * Automatická archivace od neznámých je za volbou serveru: zapnout ji smí jen účet,
             * kterému to server dovolí, a jinde by ťuknutí skončilo chybou. Nenabízí se tedy
             * vůbec — stejné pravidlo jako u filtrů seznamu, které nemají co odfiltrovat.
             */
            if (settings.canArchiveUnknownSenders) {
                SwitchRow(
                    title = Res.string.chats_archive_unknown,
                    summary = Res.string.chats_archive_unknown_summary,
                    checked = settings.archiveUnknownSenders,
                    onChange = onArchiveUnknownSenders,
                )
            }

            SwitchRow(
                title = Res.string.chats_archive_keep,
                summary = Res.string.chats_archive_keep_summary,
                checked = settings.keepUnmutedArchived,
                onChange = onKeepUnmutedArchived,
            )

            /*
             * Chaty ze složek: jen s nějakou složkou a jen dokud neplatí volba nad ní — ta drží
             * v archivu všechny neztlumené chaty a TDLib tuhle pak ignoruje. Přepínač bez
             * následku by sliboval něco, co se nestane.
             */
            AnimatedVisibility(visible = hasFolders && !settings.keepUnmutedArchived) {
                SwitchRow(
                    title = Res.string.chats_archive_keep_folders,
                    summary = Res.string.chats_archive_keep_folders_summary,
                    checked = settings.keepFromFoldersArchived,
                    onChange = onKeepFromFoldersArchived,
                )
            }
        }
    }
}

/** Řádek nese klikání celý, přepínač má `onCheckedChange = null` — jako v Nastavení. */
@Composable
private fun SwitchRow(
    title: StringResource,
    summary: StringResource,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(stringResource(title)) },
        supportingContent = { Text(stringResource(summary)) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = { on ->
            haptics.toggle(on)
            onChange(on)
        }),
    )
}
