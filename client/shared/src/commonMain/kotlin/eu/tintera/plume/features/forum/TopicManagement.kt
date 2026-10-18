package eu.tintera.plume.features.forum

import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.MediumButton
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ForumTopic
import eu.tintera.plume.core.telegram.TOPIC_COLORS
import eu.tintera.plume.core.telegram.TOPIC_NAME_MAX
import eu.tintera.plume.core.telegram.TopicIcon
import eu.tintera.plume.core.telegram.TopicRights
import eu.tintera.plume.core.telegram.topicActions
import eu.tintera.plume.core.ui.ForumTopicIcon
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.edit_profile_save
import eu.tintera.plume.resources.forum_topic_close
import eu.tintera.plume.resources.forum_topic_color
import eu.tintera.plume.resources.forum_topic_create
import eu.tintera.plume.resources.forum_topic_delete
import eu.tintera.plume.resources.forum_topic_delete_body
import eu.tintera.plume.resources.forum_topic_delete_title
import eu.tintera.plume.resources.forum_topic_hide_general
import eu.tintera.plume.resources.forum_topic_mute
import eu.tintera.plume.resources.forum_topic_notifications
import eu.tintera.plume.resources.forum_topic_name
import eu.tintera.plume.resources.forum_topic_new
import eu.tintera.plume.resources.forum_topic_pin
import eu.tintera.plume.resources.forum_topic_rename
import eu.tintera.plume.resources.forum_topic_rename_title
import eu.tintera.plume.resources.forum_topic_reopen
import eu.tintera.plume.resources.forum_topic_unmute
import eu.tintera.plume.resources.forum_topic_unpin
import eu.tintera.plume.resources.lock_24dp
import eu.tintera.plume.resources.lock_open_24dp
import eu.tintera.plume.resources.notifications_24dp
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.tune_24dp
import eu.tintera.plume.resources.drag_indicator_24dp
import eu.tintera.plume.resources.forum_topic_reorder
import eu.tintera.plume.resources.push_pin_24dp
import eu.tintera.plume.resources.visibility_off_24dp
import eu.tintera.plume.resources.topic_color_red
import eu.tintera.plume.resources.topic_color_pink
import eu.tintera.plume.resources.topic_color_green
import eu.tintera.plume.resources.topic_color_violet
import eu.tintera.plume.resources.topic_color_yellow
import eu.tintera.plume.resources.topic_color_blue
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Položka nabídky nad tématem. */
private class TopicMenuItem(
    val label: String,
    val icon: DrawableResource,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Nabídka nad tématem (podržení řádku), tvarem jako nabídka nad chatem v seznamu chatů.
 * Co se nabídne, rozhoduje [topicActions]; ztlumit smí každý — týká se jen jeho.
 * Smazání je poslední a v chybové barvě, a potvrzuje se dialogem.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TopicActionsSheet(
    topic: ForumTopic,
    rights: TopicRights,
    onDismiss: () -> Unit,
    onPin: (Boolean) -> Unit,
    onMute: (Boolean) -> Unit,
    onNotifications: () -> Unit,
    /** Uspořádat připnutá — jen u připnutého tématu a když je co přeskládat; jinak `null`. */
    onReorder: (() -> Unit)?,
    onRename: () -> Unit,
    onClose: (Boolean) -> Unit,
    onHideGeneral: () -> Unit,
    onDelete: () -> Unit,
) {
    val actions = topicActions(topic, rights)
    val items = buildList {
        if (actions.pin) {
            add(
                TopicMenuItem(
                    stringResource(if (topic.isPinned) Res.string.forum_topic_unpin else Res.string.forum_topic_pin),
                    Res.drawable.push_pin_24dp,
                ) { onPin(!topic.isPinned) },
            )
        }
        add(
            TopicMenuItem(
                stringResource(if (topic.isMuted) Res.string.forum_topic_unmute else Res.string.forum_topic_mute),
                if (topic.isMuted) Res.drawable.notifications_24dp else Res.drawable.notifications_off_24dp,
            ) { onMute(!topic.isMuted) },
        )
        // Rychlé ztlumení výš je napořád; doby, náhled, zmínky a návrat ke skupině jsou tady.
        add(TopicMenuItem(stringResource(Res.string.forum_topic_notifications), Res.drawable.tune_24dp, onClick = onNotifications))
        if (topic.isPinned && onReorder != null) {
            add(TopicMenuItem(stringResource(Res.string.forum_topic_reorder), Res.drawable.drag_indicator_24dp, onClick = onReorder))
        }
        if (actions.rename) add(TopicMenuItem(stringResource(Res.string.forum_topic_rename), Res.drawable.edit_24dp, onClick = onRename))
        if (actions.close) {
            add(
                TopicMenuItem(
                    stringResource(if (topic.isClosed) Res.string.forum_topic_reopen else Res.string.forum_topic_close),
                    if (topic.isClosed) Res.drawable.lock_open_24dp else Res.drawable.lock_24dp,
                ) { onClose(!topic.isClosed) },
            )
        }
        if (actions.hideGeneral) {
            add(TopicMenuItem(stringResource(Res.string.forum_topic_hide_general), Res.drawable.visibility_off_24dp, onClick = onHideGeneral))
        }
        if (actions.delete) {
            add(TopicMenuItem(stringResource(Res.string.forum_topic_delete), Res.drawable.delete_24dp, destructive = true, onClick = onDelete))
        }
    }
    ModalBottomSheet(sheetState = rememberExpandedSheetState(), onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
            ) {
                ForumTopicIcon(icon = topic.icon, name = topic.name, isGeneral = topic.isGeneral, size = 32.dp)
                SheetTitle(topic.name, maxLines = 1)
            }
            items.forEachIndexed { index, item ->
                val color = if (item.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                ListItem(
                    headlineContent = { Text(item.label) },
                    leadingContent = { Icon(painterResource(item.icon), contentDescription = null) },
                    colors = ListItemDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        headlineColor = color,
                        leadingIconColor = color,
                    ),
                    modifier = Modifier
                        .clip(groupItemShape(index, items.size))
                        .clickable {
                            onDismiss()
                            item.onClick()
                        },
                )
            }
        }
    }
}

/**
 * Formulář tématu: název, u nového i barva ikony. Barvy jsou jen ty, které TDLib přijme
 * ([TOPIC_COLORS]). Přejmenování barvu nemění — `editForumTopic` ji měnit neumí.
 * Náhled ikony nahoře ukazuje, jak bude téma v seznamu vypadat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TopicEditorSheet(
    editor: TopicEditor,
    busy: Boolean,
    onChange: (TopicEditor) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberExpandedSheetState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (editor.isNew) {
                    ForumTopicIcon(
                        icon = TopicIcon(color = editor.color, customEmojiId = null),
                        name = editor.name,
                        isGeneral = false,
                        size = 44.dp,
                    )
                }
                Text(
                    stringResource(if (editor.isNew) Res.string.forum_topic_new else Res.string.forum_topic_rename_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }
            OutlinedTextField(
                value = editor.name,
                onValueChange = { onChange(editor.copy(name = it.take(TOPIC_NAME_MAX))) },
                label = { Text(stringResource(Res.string.forum_topic_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            if (editor.isNew) {
                Text(
                    stringResource(Res.string.forum_topic_color),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    TOPIC_COLORS.forEachIndexed { index, rgb ->
                        ColorChoice(
                            rgb = rgb,
                            name = stringResource(COLOR_NAMES[index]),
                            selected = rgb == editor.color,
                        ) { onChange(editor.copy(color = rgb)) }
                    }
                }
            }
            MediumButton(onClick = onSave, enabled = editor.canSave && !busy, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (editor.isNew) Res.string.forum_topic_create else Res.string.edit_profile_save))
            }
        }
    }
}

/**
 * Kolečko barvy. Vybrané má fajfku a obrys v barvě popředí, takže výběr nese i tvar,
 * nejen barva — žlutá a zelená se jinak na první pohled pletou.
 */
@Composable
private fun ColorChoice(rgb: Int, name: String, selected: Boolean, onClick: () -> Unit) {
    val color = Color(0xFF000000 or rgb.toLong())
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = name
            },
    ) {
        Surface(shape = CircleShape, color = color, modifier = Modifier.size(34.dp)) {
            if (selected) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(painterResource(Res.drawable.check_24dp), contentDescription = null, tint = Color.Black.copy(alpha = 0.7f))
                }
            }
        }
    }
}

/** Smazání tématu: nevratné a pro všechny, i se zprávami — to musí věta říct. */
@Composable
internal fun DeleteTopicDialog(topic: ForumTopic, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    ConfirmDialog(
        title = stringResource(Res.string.forum_topic_delete_title, topic.name),
        text = stringResource(Res.string.forum_topic_delete_body),
        confirmLabel = stringResource(Res.string.forum_topic_delete),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        icon = Res.drawable.delete_24dp,
        destructive = true,
    )
}

/** Jména barev pro čtečku obrazovky, v pořadí [TOPIC_COLORS]. */
private val COLOR_NAMES = listOf(
    Res.string.topic_color_blue,
    Res.string.topic_color_yellow,
    Res.string.topic_color_violet,
    Res.string.topic_color_green,
    Res.string.topic_color_pink,
    Res.string.topic_color_red,
)
