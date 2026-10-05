package eu.tintera.plume.features.conversation

import androidx.compose.runtime.Composable
import eu.tintera.plume.core.ui.FolderChatsSheet
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.folder_invite_add
import eu.tintera.plume.resources.folder_invite_already_joined
import eu.tintera.plume.resources.folder_invite_existing
import eu.tintera.plume.resources.folder_invite_existing_none
import eu.tintera.plume.resources.folder_invite_join
import eu.tintera.plume.resources.folder_invite_new
import eu.tintera.plume.resources.folder_invite_new_all_joined
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Náhled sdílené složky z odkazu `t.me/addlist/…`. Nová složka se přidá i bez připojení k chatům,
 * když v některých už uživatel je; u složky, kterou už má, se jen připojuje k novým chatům.
 */
@Composable
fun FolderInviteSheet(
    invite: FolderInviteSheetState,
    onToggle: (Long) -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val preview = invite.preview
    val isNew = preview.folderId == 0
    val missing = preview.missingChats
    FolderChatsSheet(
        iconName = preview.iconName,
        title = preview.title,
        titleEntities = preview.titleEntities,
        description = stringResource(
            when {
                isNew && missing.isEmpty() -> Res.string.folder_invite_new_all_joined
                isNew -> Res.string.folder_invite_new
                missing.isEmpty() -> Res.string.folder_invite_existing_none
                else -> Res.string.folder_invite_existing
            },
        ),
        chats = missing,
        selected = invite.selected,
        onToggle = onToggle,
        footnote = preview.alreadyJoined.takeIf { it > 0 }?.let {
            pluralStringResource(Res.plurals.folder_invite_already_joined, it, it)
        },
        confirmLabel = when {
            isNew -> stringResource(Res.string.folder_invite_add)
            missing.isNotEmpty() -> pluralStringResource(Res.plurals.folder_invite_join, invite.selected.size, invite.selected.size)
            else -> null
        },
        confirmEnabled = invite.canAdd,
        busy = invite.adding,
        onConfirm = onAdd,
        onDismiss = onDismiss,
    )
}
