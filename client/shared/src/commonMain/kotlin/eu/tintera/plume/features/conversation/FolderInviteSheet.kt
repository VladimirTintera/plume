package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.appendWithCustomEmoji
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.customEmojiInlineContent
import eu.tintera.plume.core.ui.folderIcon
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.folder_invite_add
import eu.tintera.plume.resources.folder_invite_already_joined
import eu.tintera.plume.resources.folder_invite_chats
import eu.tintera.plume.resources.folder_invite_existing
import eu.tintera.plume.resources.folder_invite_existing_none
import eu.tintera.plume.resources.folder_invite_join
import eu.tintera.plume.resources.folder_invite_new
import eu.tintera.plume.resources.folder_invite_new_all_joined
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Náhled sdílené složky z odkazu `t.me/addlist/…`: ikona a název složky, chaty, ke kterým se
 * uživatel připojí (zaškrtnuté všechny, jde odškrtnout), a tlačítko.
 *
 * **Nic se nestane bez potvrzení**, stejně jako u pozvánky do chatu — přidání složky znamená
 * připojit se k chatům, a z odkazu ve zprávě není vidět, ke kterým.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FolderInviteSheet(
    invite: FolderInviteSheetState,
    onToggle: (Long) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Seznam chatů bývá delší než půl obrazovky; napůl rozbalený sheet by tlačítko schoval.
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val preview = invite.preview
    val isNew = preview.folderId == 0
    val missing = preview.missingChats

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(72.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(folderIcon(preview.iconName)), contentDescription = null, modifier = Modifier.size(36.dp))
                }
            }
            val (title, emojiIds) = remember(preview.title, preview.titleEntities) {
                var ids: Set<Long> = emptySet()
                val built = buildAnnotatedString { ids = appendWithCustomEmoji(preview.title, preview.titleEntities) }
                built to ids
            }
            Text(
                text = title,
                inlineContent = customEmojiInlineContent(emojiIds),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(
                    when {
                        isNew && missing.isEmpty() -> Res.string.folder_invite_new_all_joined
                        isNew -> Res.string.folder_invite_new
                        missing.isEmpty() -> Res.string.folder_invite_existing_none
                        else -> Res.string.folder_invite_existing
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (missing.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.folder_invite_chats),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 16.dp),
                )
                Column(verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING)) {
                    missing.forEachIndexed { index, chat ->
                        val checked = chat.id in invite.selected
                        Surface(
                            shape = groupItemShape(index, missing.size),
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        ) {
                            ListItem(
                                headlineContent = { Text(chat.title, maxLines = 1) },
                                leadingContent = {
                                    Avatar(
                                        chatId = chat.id,
                                        title = chat.title,
                                        avatarFileId = chat.avatarFileId,
                                        size = 40.dp,
                                        shape = chat.kind.avatarShape(),
                                    )
                                },
                                // Ťuknutí bere celý řádek, zaškrtávátko jen ukazuje stav.
                                trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                                modifier = Modifier.toggleable(
                                    value = checked,
                                    enabled = !invite.adding,
                                    role = Role.Checkbox,
                                    onValueChange = { onToggle(chat.id) },
                                ),
                            )
                        }
                    }
                }
            }
            if (preview.alreadyJoined > 0) {
                Text(
                    text = pluralStringResource(Res.plurals.folder_invite_already_joined, preview.alreadyJoined, preview.alreadyJoined),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (isNew || missing.isNotEmpty()) {
                Button(
                    onClick = onAdd,
                    enabled = !invite.adding && invite.canAdd,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 56.dp),
                ) {
                    if (invite.adding) {
                        LoadingIndicator(color = LocalContentColor.current)
                    } else if (isNew) {
                        Text(stringResource(Res.string.folder_invite_add))
                    } else {
                        Text(pluralStringResource(Res.plurals.folder_invite_join, invite.selected.size, invite.selected.size))
                    }
                }
            }
        }
    }
}
