package eu.tintera.plume.features.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.FolderChatsSheet
import eu.tintera.plume.core.ui.GROUP_CORNER
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.core.ui.folderIcon
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.folder_invite_join
import eu.tintera.plume.resources.folder_new_chats_hide
import eu.tintera.plume.resources.folder_new_chats_sheet
import eu.tintera.plume.resources.folder_new_chats_show
import eu.tintera.plume.resources.folder_new_chats_text
import eu.tintera.plume.resources.folder_new_chats_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Karta nad seznamem přidané sdílené složky: autor do ní přidal chaty. Stejný tvar a barvy jako
 * karta aktualizace — je to oznámení, které nic neblokuje. „Skrýt" nové chaty vezme jako viděné
 * (server je pak znovu nevydá), „Zobrazit" otevře výběr, ke kterým se připojit.
 */
@Composable
fun NewFolderChatsCard(
    state: NewFolderChats,
    onShow: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val accent = accentOn(colors.primaryContainer, colors.primary, colors.onPrimaryContainer)
    val onAccent = if (accent == colors.primary) colors.onPrimary else colors.primaryContainer

    Surface(
        shape = RoundedCornerShape(GROUP_CORNER),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(accent, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(folderIcon(state.iconName)), contentDescription = null, tint = onAccent)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(Res.string.folder_new_chats_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        pluralStringResource(Res.plurals.folder_new_chats_text, state.chats.size, state.chats.size, state.title),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onPrimaryContainer.copy(alpha = 0.8f),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                TextButton(
                    onClick = onHide,
                    enabled = !state.busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = accent),
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(Res.string.folder_new_chats_hide)) }
                Button(
                    onClick = onShow,
                    enabled = !state.busy,
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = onAccent),
                    shapes = ButtonDefaults.shapes(),
                ) { Text(stringResource(Res.string.folder_new_chats_show)) }
            }
        }
    }
}

/** Výběr nových chatů složky, ke kterým se připojit — tentýž sheet jako náhled sdílené složky. */
@Composable
fun NewFolderChatsSheet(
    state: NewFolderChats,
    onToggle: (Long) -> Unit,
    onJoin: () -> Unit,
    onDismiss: () -> Unit,
) {
    FolderChatsSheet(
        iconName = state.iconName,
        title = state.title,
        titleEntities = state.titleEntities,
        description = stringResource(Res.string.folder_new_chats_sheet),
        chats = state.chats,
        selected = state.selected,
        onToggle = onToggle,
        footnote = null,
        confirmLabel = pluralStringResource(Res.plurals.folder_invite_join, state.selected.size, state.selected.size),
        confirmEnabled = state.selected.isNotEmpty(),
        busy = state.busy,
        onConfirm = onJoin,
        onDismiss = onDismiss,
    )
}
