package eu.tintera.plume.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chat_folders_row_help
import eu.tintera.plume.resources.chat_folders_title
import eu.tintera.plume.resources.folder_24dp
import eu.tintera.plume.resources.settings_chat_folders_group
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Skupina „Chaty" v Obecných: zatím jen složky. Samostatná sekce by měla jednu položku. */
@Composable
internal fun ChatsSettings(onOpenFolders: () -> Unit) {
    SettingsGroup(Res.string.settings_chat_folders_group) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(painterResource(Res.drawable.folder_24dp), contentDescription = null) },
            headlineContent = { Text(stringResource(Res.string.chat_folders_title)) },
            supportingContent = { Text(stringResource(Res.string.chat_folders_row_help)) },
            modifier = Modifier.clickable(onClick = onOpenFolders),
        )
    }
}
