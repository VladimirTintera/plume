package eu.tintera.plume.features.chatlabels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.app.root.presentation.LocalIsDarkTheme
import eu.tintera.plume.app.root.presentation.theme.chatColorScheme
import eu.tintera.plume.app.root.presentation.theme.seed
import eu.tintera.plume.core.chatlabels.ChatLabel
import eu.tintera.plume.core.chatlabels.ChatLabels
import eu.tintera.plume.core.chatlabels.MAX_LABEL_LENGTH
import eu.tintera.plume.core.settings.ThemeColor
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.core.ui.FormTextField
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.groupedItem
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.features.settings.ThemeColorPicker
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.add_24dp
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.common_delete
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.labels_delete
import eu.tintera.plume.resources.labels_delete_text
import eu.tintera.plume.resources.labels_delete_title
import eu.tintera.plume.resources.labels_edit
import eu.tintera.plume.resources.labels_empty
import eu.tintera.plume.resources.labels_explanation
import eu.tintera.plume.resources.labels_name
import eu.tintera.plume.resources.labels_new
import eu.tintera.plume.resources.labels_save
import eu.tintera.plume.resources.labels_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * **Štítky chatu**: seznam štítků účtu se zaškrtnutím, které má chat [chatId], tužka u každého
 * (přejmenovat, přebarvit, smazat) a „Nový štítek". Úprava se otevře v témže sheetu místo
 * seznamu, ne jako druhé okno nad ním. Zaškrtnutí se ukládá hned, sheet zůstává otevřený —
 * chat jich může mít víc.
 *
 * Otevírá se z nabídky nad chatem v seznamu i z ⋮ konverzace a profilu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatLabelsSheet(chatId: Long, onDismiss: () -> Unit) {
    val store = koinInject<ChatLabels>()
    val labels by store.labels.collectAsStateWithLifecycle(emptyList())
    val assignments by store.assignments.collectAsStateWithLifecycle(emptyMap())
    val mine = assignments[chatId].orEmpty().mapTo(HashSet()) { it.id }
    /** Upravovaný štítek; [NEW_LABEL] = zakládá se nový; `null` = seznam. */
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            val edited = editing
            if (edited == null) {
                LabelList(
                    labels = labels,
                    checked = mine,
                    onToggle = { store.toggle(chatId, it) },
                    onEdit = { editing = it },
                    onNew = { editing = NEW_LABEL },
                )
            } else {
                val existing = labels.firstOrNull { it.id == edited }
                LabelEditor(
                    label = existing,
                    onSave = { name, color ->
                        if (existing == null) {
                            // Nový štítek rovnou na chat, ze kterého se zakládá — kvůli tomu se zakládal.
                            store.create(name, color)?.let { store.toggle(chatId, it) }
                        } else {
                            store.update(existing.id, name, color)
                        }
                        editing = null
                    },
                    onDelete = existing?.let { label -> { store.delete(label.id); editing = null } },
                    onCancel = { editing = null },
                )
            }
        }
    }
}

@Composable
private fun LabelList(
    labels: List<ChatLabel>,
    checked: Set<String>,
    onToggle: (String) -> Unit,
    onEdit: (String) -> Unit,
    onNew: () -> Unit,
) {
    SheetTitle(stringResource(Res.string.labels_title), Modifier.padding(horizontal = 24.dp))
    Text(
        stringResource(if (labels.isEmpty()) Res.string.labels_empty else Res.string.labels_explanation),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    )
    val rows = labels.size + 1
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        labels.forEachIndexed { index, label ->
            ListItem(
                leadingContent = { LabelDot(label.color) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onEdit(label.id) }, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(Res.drawable.edit_24dp), contentDescription = stringResource(Res.string.labels_edit))
                        }
                        Checkbox(checked = label.id in checked, onCheckedChange = null)
                    }
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier
                    .groupedItem(index, rows, MaterialTheme.colorScheme.groupRow)
                    .clickable { onToggle(label.id) },
            ) {
                Text(label.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        ListItem(
            leadingContent = { Icon(painterResource(Res.drawable.add_24dp), contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.groupedItem(labels.size, rows, MaterialTheme.colorScheme.groupRow).clickable(onClick = onNew),
        ) {
            Text(stringResource(Res.string.labels_new))
        }
    }
}

/** Název a barva štítku; u existujícího i smazání (s potvrzením — zmizí ze všech chatů). */
@Composable
private fun LabelEditor(
    label: ChatLabel?,
    onSave: (name: String, color: ThemeColor) -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable(label?.id) { mutableStateOf(label?.name.orEmpty()) }
    var color by rememberSaveable(label?.id) { mutableStateOf(label?.color ?: ThemeColor.BLUE) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isDark = LocalIsDarkTheme.current

    SheetTitle(stringResource(if (label == null) Res.string.labels_new else Res.string.labels_edit), Modifier.padding(horizontal = 24.dp))
    FormTextField(
        value = name,
        onValueChange = { name = it.take(MAX_LABEL_LENGTH) },
        label = { Text(stringResource(Res.string.labels_name)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
    )
    ThemeColorPicker(
        selected = color,
        isDark = isDark,
        onSelect = { color = it },
        schemeOf = { chatColorScheme(isDark, it.seed) },
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onDelete != null) {
            TextButton(
                onClick = { confirmDelete = true },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(Res.string.labels_delete))
            }
        }
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_cancel)) }
            FilledTonalButton(
                onClick = { onSave(name, color) },
                enabled = name.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(stringResource(Res.string.labels_save))
            }
        }
    }
    if (confirmDelete && label != null && onDelete != null) {
        ConfirmDialog(
            title = stringResource(Res.string.labels_delete_title, label.name),
            text = stringResource(Res.string.labels_delete_text),
            confirmLabel = stringResource(Res.string.common_delete),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
            icon = Res.drawable.delete_24dp,
            destructive = true,
        )
    }
}

/** Značka pro `editing` — zakládá se nový štítek (id štítků jsou šestnáctková čísla). */
private const val NEW_LABEL = "new"
