package eu.tintera.plume.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.shortcuts.MAX_SHORTCUT_TEXT
import eu.tintera.plume.core.shortcuts.TextShortcut
import eu.tintera.plume.core.shortcuts.newShortcutId
import eu.tintera.plume.core.shortcuts.shortcutName
import eu.tintera.plume.core.shortcuts.shortcutText
import eu.tintera.plume.core.shortcuts.SHORTCUT_PREFIX
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.core.ui.FormTextField
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.add_24dp
import eu.tintera.plume.resources.bolt_filled_24dp
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.common_delete
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.text_shortcuts_add
import eu.tintera.plume.resources.text_shortcuts_delete_text
import eu.tintera.plume.resources.text_shortcuts_delete_title
import eu.tintera.plume.resources.text_shortcuts_edit
import eu.tintera.plume.resources.text_shortcuts_help
import eu.tintera.plume.resources.text_shortcuts_name
import eu.tintera.plume.resources.text_shortcuts_name_help
import eu.tintera.plume.resources.text_shortcuts_name_taken
import eu.tintera.plume.resources.text_shortcuts_save
import eu.tintera.plume.resources.text_shortcuts_text
import eu.tintera.plume.resources.text_shortcuts_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * **Textové zkratky** (Nastavení → Chaty): seznam „/název → text", přidání, úprava a smazání.
 * Ve vstupním poli se zkratka nabídne po napsání `/` a začátku názvu (`ShortcutSuggestionsPanel`).
 * Obrazovka sahá rovnou do [AppSettings] — nic se nenačítá ani nepočítá, ViewModel by jen
 * přeposílal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TextShortcutsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val settings = koinInject<AppSettings>()
    val shortcuts by settings.textShortcuts.collectAsStateWithLifecycle()
    /** Upravovaná zkratka; [NEW_SHORTCUT] = nová; `null` = editor zavřený. */
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    val scrollBehavior = settingsScrollBehavior()
    Scaffold(
        modifier = modifier.settingsScroll(scrollBehavior),
        topBar = { SettingsTopBar(title = stringResource(Res.string.text_shortcuts_title), onBack = onBack, scrollBehavior = scrollBehavior) },
    ) { padding ->
        val count = shortcuts.size + 1
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "help") {
                Text(
                    stringResource(Res.string.text_shortcuts_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
            }
            item(key = "add") {
                ListItem(
                    leadingContent = { Icon(painterResource(Res.drawable.add_24dp), contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.groupRow, headlineColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.animateItem().clip(groupItemShape(0, count)).clickable { editing = NEW_SHORTCUT },
                ) { Text(stringResource(Res.string.text_shortcuts_add)) }
            }
            itemsIndexed(shortcuts, key = { _, it -> it.id }) { index, shortcut ->
                ListItem(
                    leadingContent = { Icon(painterResource(Res.drawable.bolt_filled_24dp), contentDescription = null) },
                    supportingContent = { Text(shortcut.text.replace('\n', ' '), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.groupRow),
                    modifier = Modifier.animateItem().clip(groupItemShape(index + 1, count)).clickable { editing = shortcut.id },
                ) { Text(shortcutText(shortcut.name), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }

    editing?.let { id ->
        val existing = shortcuts.firstOrNull { it.id == id }
        ShortcutEditorSheet(
            initial = existing,
            taken = { name -> shortcuts.any { it.name == name && it.id != existing?.id } },
            onSave = { name, text ->
                settings.setTextShortcuts(
                    if (existing == null) {
                        shortcuts + TextShortcut(newShortcutId(), name, text, order = (shortcuts.maxOfOrNull { it.order } ?: -1) + 1)
                    } else {
                        shortcuts.map { if (it.id == existing.id) it.copy(name = name, text = text) else it }
                    },
                )
                editing = null
            },
            onDelete = existing?.let { shortcut -> { settings.setTextShortcuts(shortcuts.filterNot { it.id == shortcut.id }); editing = null } },
            onDismiss = { editing = null },
        )
    }
}

/** Název (bez lomítka, jen písmena, číslice a podtržítko) a text; u existující i smazání. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShortcutEditorSheet(
    initial: TextShortcut?,
    taken: (String) -> Boolean,
    onSave: (name: String, text: String) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var text by rememberSaveable(initial?.id) { mutableStateOf(initial?.text.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val nameTaken = name.isNotEmpty() && taken(name)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            SheetTitle(
                stringResource(if (initial == null) Res.string.text_shortcuts_add else Res.string.text_shortcuts_edit),
                Modifier.padding(horizontal = 24.dp),
            )
            FormTextField(
                value = name,
                onValueChange = { name = shortcutName(it) },
                label = { Text(stringResource(Res.string.text_shortcuts_name)) },
                prefix = { Text(SHORTCUT_PREFIX) },
                singleLine = true,
                isError = nameTaken,
                supportingText = {
                    Text(stringResource(if (nameTaken) Res.string.text_shortcuts_name_taken else Res.string.text_shortcuts_name_help))
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            )
            FormTextField(
                value = text,
                onValueChange = { text = it.take(MAX_SHORTCUT_TEXT) },
                label = { Text(stringResource(Res.string.text_shortcuts_text)) },
                minLines = 3,
                maxLines = 8,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text(stringResource(Res.string.common_delete)) }
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_cancel)) }
                    FilledTonalButton(
                        onClick = { onSave(name, text.trimEnd()) },
                        enabled = name.isNotEmpty() && !nameTaken && text.isNotBlank(),
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.padding(start = 8.dp),
                    ) { Text(stringResource(Res.string.text_shortcuts_save)) }
                }
            }
        }
    }
    if (confirmDelete && initial != null && onDelete != null) {
        ConfirmDialog(
            title = stringResource(Res.string.text_shortcuts_delete_title, initial.name),
            text = stringResource(Res.string.text_shortcuts_delete_text),
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

private const val NEW_SHORTCUT = "new"
