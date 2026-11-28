package eu.tintera.plume.tv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ReactionDetail
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.reactions_title
import eu.tintera.plume.resources.reactions_you
import eu.tintera.plume.resources.tv_reactors_unavailable
import org.jetbrains.compose.resources.stringResource

/**
 * Kdo na zprávu reagoval a čím, od nejnovější reakce — jedna stránka od serveru, jako sheet na
 * telefonu. Řádky jsou zaostřitelné jen kvůli rolování ovladačem; OK na nich nic nedělá.
 *
 * Kanál seznam nedá (server reakce v kanálu anonymizuje) a dialog to řekne větou — tlačítko
 * v nabídce se kvůli tomu neschovává, zpráva neví, v jakém chatu leží.
 */
@Composable
internal fun ReactorsDialog(state: ConversationState, message: ChatMessage, chatTitle: String, onDismiss: () -> Unit) {
    // `null` = načítá se, prázdný seznam = server nic nedal (nebo selhal).
    val details by produceState<List<ReactionDetail>?>(null, message.id) {
        value = state.reactionDetails(message.id).getOrDefault(emptyList())
    }
    Dialog(onDismissRequest = onDismiss, properties = WideDialog) {
        DialogPanel {
            Text(stringResource(Res.string.reactions_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            QuoteCard(message, chatTitle)
            Spacer(Modifier.height(16.dp))
            val shown = details
            when {
                shown == null -> Unit
                shown.isEmpty() -> Text(
                    stringResource(Res.string.tv_reactors_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {
                    val first = remember { FocusRequester() }
                    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        // Výšku dělí panel jako u přeposlání (`DialogPanel` má strop podle obrazovky).
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    ) {
                        itemsIndexed(shown, key = { index, it -> "${it.sender.id}-${it.emoji}-$index" }) { index, detail ->
                            ReactorRow(detail, if (index == 0) Modifier.focusRequester(first) else Modifier)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReactorRow(detail: ReactionDetail, modifier: Modifier) {
    val name = if (detail.isOwn) stringResource(Res.string.reactions_you) else detail.sender.title
    ListItem(
        selected = false,
        onClick = {},
        modifier = modifier,
        leadingContent = { ChatAvatar(fileId = detail.sender.avatarFileId, title = detail.sender.title, size = 40.dp) },
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        // Vlastní emoji jako obrázek (CustomEmoji.kt), jako v čipech.
        trailingContent = { ReactionEmoji(detail.emoji, MaterialTheme.typography.titleLarge) },
    )
}
