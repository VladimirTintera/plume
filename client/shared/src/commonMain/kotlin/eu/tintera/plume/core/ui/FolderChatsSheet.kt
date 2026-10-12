package eu.tintera.plume.core.ui

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
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.MessageEntity
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.folder_invite_chats
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Sheet se složkou a chaty, ke kterým se uživatel připojí: ikona a název složky, věta, zaškrtávací
 * řádky chatů (všechny zaškrtnuté, jdou odškrtnout) a tlačítko. Kreslí ho náhled sdílené složky
 * z odkazu i nové chaty, které autor do přidané složky doplnil — jeden vzhled pro tutéž otázku.
 *
 * **Nic se nestane bez potvrzení**, stejně jako u pozvánky do chatu — připojit se k chatům
 * z odkazu nebo ze složky, kterou spravuje někdo jiný, je rozhodnutí uživatele.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FolderChatsSheet(
    iconName: String?,
    title: String,
    titleEntities: List<MessageEntity>,
    description: String,
    chats: List<ChatSummary>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
    /** Věta pod chaty („v dalších už jsi"); `null` = žádná. */
    footnote: String?,
    /** Popisek tlačítka; `null` = tlačítko není (není co přidat). */
    confirmLabel: String?,
    confirmEnabled: Boolean,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Seznam chatů bývá delší než půl obrazovky; napůl rozbalený sheet by tlačítko schoval.
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )

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
                    Icon(painterResource(folderIcon(iconName)), contentDescription = null, modifier = Modifier.size(36.dp))
                }
            }
            val (titleText, emojiIds) = remember(title, titleEntities) {
                var ids: Set<Long> = emptySet()
                val built = buildAnnotatedString { ids = appendWithCustomEmoji(title, titleEntities) }
                built to ids
            }
            Text(
                text = titleText,
                inlineContent = customEmojiInlineContent(emojiIds),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (chats.isNotEmpty()) {
                Text(
                    text = stringResource(Res.string.folder_invite_chats),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, start = 16.dp),
                )
                CheckableChatRows(chats = chats, selected = selected, onToggle = onToggle, enabled = !busy)
            }
            footnote?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (confirmLabel != null) {
                Button(
                    onClick = onConfirm,
                    enabled = !busy && confirmEnabled,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).heightIn(min = 56.dp),
                ) {
                    if (busy) {
                        LoadingIndicator(color = LocalContentColor.current)
                    } else {
                        Text(confirmLabel)
                    }
                }
            }
        }
    }
}

/**
 * Zaškrtávací řádky chatů v bloku zaoblených řádků. Ťuknutí bere celý řádek, zaškrtávátko jen
 * ukazuje stav. Sdílí ho náhled sdílené složky, nové chaty ve složce, úprava odkazu na složku
 * a dotaz, které chaty opustit při smazání složky — tatáž otázka „které z těchhle chatů".
 */
@Composable
fun CheckableChatRows(
    chats: List<ChatSummary>,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING), modifier = modifier) {
        chats.forEachIndexed { index, chat ->
            val checked = chat.id in selected
            Surface(
                shape = groupItemShape(index, chats.size),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) {
                ListItem(
                    headlineContent = { Text(chat.title, maxLines = 1) },
                    leadingContent = {
                        Avatar(
                            chatId = chat.avatarColorId,
                            title = chat.title,
                            avatarFileId = chat.avatarFileId,
                            size = 40.dp,
                            shape = chat.kind.avatarShape(),
                        )
                    },
                    trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                    modifier = Modifier.toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = { onToggle(chat.id) },
                    ),
                )
            }
        }
    }
}
