package eu.tintera.plume.features.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.RecentFile
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.MediumButton
import eu.tintera.plume.core.ui.SearchPill
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.asDate
import eu.tintera.plume.core.ui.fileSizeText
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_search_clear
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.recent_files_empty
import eu.tintera.plume.resources.recent_files_none_found
import eu.tintera.plume.resources.recent_files_pick
import eu.tintera.plume.resources.recent_files_search
import eu.tintera.plume.resources.recent_files_send
import eu.tintera.plume.resources.recent_files_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Nedávno odeslané soubory (sponka → Poslední soubory): hledání, výběr zaškrtnutím (nejvýš
 * [MAX_RECENT_FILES], pořadí ťuknutí je pořadí v albu) a Odeslat. Soubory server už má, takže
 * se neposílají přes pás příloh nad polem, ale rovnou odtud.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RecentFilesPicker(
    sheet: RecentFilesSheet,
    onQueryChange: (String) -> Unit,
    onToggle: (Int) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        SheetTitle(stringResource(Res.string.recent_files_title), modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        SearchPill(
            initialQuery = sheet.query,
            onQueryChange = onQueryChange,
            placeholder = stringResource(Res.string.recent_files_search),
            clearDescription = stringResource(Res.string.conversation_search_clear),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Box(Modifier.fillMaxWidth().height(LIST_HEIGHT)) {
            val files = sheet.files
            when {
                files == null -> LoadingIndicator(Modifier.align(Alignment.Center))
                files.isEmpty() -> Text(
                    text = sheet.error?.resolve() ?: stringResource(
                        if (sheet.query.isBlank()) Res.string.recent_files_empty else Res.string.recent_files_none_found,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                )
                else -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                ) {
                    itemsIndexed(files, key = { _, file -> file.fileId }) { index, file ->
                        RecentFileRow(
                            file = file,
                            selected = file.fileId in sheet.selected,
                            // Plné album: další soubory ustoupí, vybrané jde dál odznačit.
                            enabled = file.fileId in sheet.selected || sheet.canSelectMore,
                            index = index,
                            count = files.size,
                            onToggle = { onToggle(file.fileId) },
                        )
                    }
                }
            }
        }
        MediumButton(
            onClick = onSend,
            enabled = sheet.selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        ) {
            val count = sheet.selected.size
            Text(
                if (count == 0) {
                    stringResource(Res.string.recent_files_pick)
                } else {
                    pluralStringResource(Res.plurals.recent_files_send, count, count)
                },
            )
        }
    }
}

@Composable
private fun RecentFileRow(file: RecentFile, selected: Boolean, enabled: Boolean, index: Int, count: Int, onToggle: () -> Unit) {
    ListItem(
        // Název uprostřed zkrácený: přípona na konci je to, podle čeho se soubor pozná.
        headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.MiddleEllipsis) },
        supportingContent = { Text(listOf(fileSizeText(file.size), file.sentAt.asDate()).joinToString(" · ")) },
        leadingContent = {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(Res.drawable.description_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        },
        // Stav nese řádek (`toggleable`), zaškrtávátko je jen jeho obraz.
        trailingContent = { Checkbox(checked = selected, onCheckedChange = null, enabled = enabled) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier
            .clip(groupItemShape(index, count))
            .toggleable(value = selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
    )
}

private val LIST_HEIGHT = 420.dp

/** Řádek, který už vybrat nejde (plné album) — ustoupí, ale zůstane čitelný. */
private const val DISABLED_ALPHA = 0.5f
