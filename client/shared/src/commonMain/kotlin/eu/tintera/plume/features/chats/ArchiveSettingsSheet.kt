package eu.tintera.plume.features.chats

import eu.tintera.plume.core.ui.rememberExpandedSheetState
import androidx.compose.material3.MaterialTheme
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandVertically
import androidx.compose.foundation.layout.fillMaxWidth
import eu.tintera.plume.core.ui.GroupedColumn
import eu.tintera.plume.core.ui.SheetTitle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ArchiveSettings
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_archive_keep_folders
import eu.tintera.plume.resources.chats_archive_keep_folders_summary
import eu.tintera.plume.resources.chats_archive_keep
import eu.tintera.plume.resources.chats_archive_keep_summary
import eu.tintera.plume.resources.chats_archive_settings
import eu.tintera.plume.resources.chats_archive_unknown
import eu.tintera.plume.resources.chats_archive_unknown_summary
import org.jetbrains.compose.resources.stringResource
import eu.tintera.plume.core.ui.SwitchListItem

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
    val sheetState = rememberExpandedSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            SheetTitle(
                stringResource(Res.string.chats_archive_settings),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
            )
            // Přepínače jsou blok zaoblených řádků jako skupiny v Nastavení.
            GroupedColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                /*
                 * Automatická archivace od neznámých je za volbou serveru: zapnout ji smí jen účet,
                 * kterému to server dovolí, a jinde by ťuknutí skončilo chybou. Nenabízí se tedy
                 * vůbec: přepínač, který skončí chybou, je horší než žádný.
                 */
                if (settings.canArchiveUnknownSenders) {
                    SwitchListItem(
                        headline = stringResource(Res.string.chats_archive_unknown),
                        supporting = stringResource(Res.string.chats_archive_unknown_summary),
                        checked = settings.archiveUnknownSenders,
                        onCheckedChange = onArchiveUnknownSenders,
                    )
                }

                SwitchListItem(
                    headline = stringResource(Res.string.chats_archive_keep),
                    supporting = stringResource(Res.string.chats_archive_keep_summary),
                    checked = settings.keepUnmutedArchived,
                    onCheckedChange = onKeepUnmutedArchived,
                )

                /*
                 * Chaty ze složek: jen s nějakou složkou a jen dokud neplatí volba nad ní — ta drží
                 * v archivu všechny neztlumené chaty a TDLib tuhle pak ignoruje. Přepínač bez
                 * následku by sliboval něco, co se nestane.
                 */
                AnimatedVisibility(
                    visible = hasFolders && !settings.keepUnmutedArchived,
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) +
                        fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    SwitchListItem(
                        headline = stringResource(Res.string.chats_archive_keep_folders),
                        supporting = stringResource(Res.string.chats_archive_keep_folders_summary),
                        checked = settings.keepFromFoldersArchived,
                        onCheckedChange = onKeepFromFoldersArchived,
                    )
                }
            }
        }
    }
}
