package eu.tintera.plume.features.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.WelcomeMessage
import eu.tintera.plume.core.ui.FormTextField
import eu.tintera.plume.core.ui.GroupedColumn
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.WithTooltip
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.welcome_messages_add
import eu.tintera.plume.resources.welcome_messages_delete
import eu.tintera.plume.resources.welcome_messages_empty
import eu.tintera.plume.resources.welcome_messages_failed
import eu.tintera.plume.resources.welcome_messages_help
import eu.tintera.plume.resources.welcome_messages_new
import eu.tintera.plume.resources.welcome_messages_title
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Uvítací zprávy skupiny nebo kanálu pro nové členy (`addChatWelcomeMessage` a spol.): seznam
 * v pořadí, v jakém odejdou, smazání u každé a pole pro novou textovou. Média přidávat Plume
 * neumí — zpráva s médiem od jiného klienta se tu ukáže popisem obsahu a jde smazat.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun WelcomeMessagesSheet(
    sheet: WelcomeSheet,
    onAdd: (String) -> Unit,
    onDelete: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    // Po přidání se pole vyprázdní — až když server přidání potvrdil, jinak by text zmizel i po chybě.
    LaunchedEffect(sheet.added) { if (sheet.added > 0) draft = "" }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            SheetTitle(stringResource(Res.string.welcome_messages_title))
            Text(
                stringResource(Res.string.welcome_messages_help),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val messages = sheet.messages
            when {
                sheet.failed -> Text(
                    stringResource(Res.string.welcome_messages_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                messages == null -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                messages.isEmpty() -> Text(
                    stringResource(Res.string.welcome_messages_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
                else -> GroupedColumn { messages.forEach { WelcomeRow(it, onDelete) } }
            }
            if (!sheet.failed) {
                FormTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(stringResource(Res.string.welcome_messages_new)) },
                    enabled = !sheet.busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                Button(
                    onClick = { onAdd(draft) },
                    enabled = draft.isNotBlank() && !sheet.busy,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(stringResource(Res.string.welcome_messages_add))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WelcomeRow(message: WelcomeMessage, onDelete: (Int) -> Unit) {
    val text = message.text.ifBlank { message.description?.resolve().orEmpty() }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(text, maxLines = 4) },
        trailingContent = {
            val label = stringResource(Res.string.welcome_messages_delete)
            WithTooltip(label) {
                IconButton(onClick = { onDelete(message.id) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(Res.drawable.delete_24dp), contentDescription = label)
                }
            }
        },
    )
}
