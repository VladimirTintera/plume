package eu.tintera.plume.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.customEmojiIdOf
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.connection_connecting
import eu.tintera.plume.resources.wear_reactions_none
import eu.tintera.plume.resources.wear_reactions_title
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val logger = Logger.withTag("WearReactions")

/** Kolik reakcí se nabídne — na hodinkách se v dlouhém seznamu nikdo nehrabe. */
private const val MAX_CHOICES = 12

/**
 * Výběr reakce na zprávu: emoji, která chat u zprávy dovoluje (`availableReactions`), už daná
 * zvýrazněná. Ťuknutí reakci přidá, nebo — je-li daná — odebere, a vrátí se do konverzace; ta
 * změnu uvidí živě (`interactionUpdates`). Vlastní emoji hodinky nevykreslí, vynechávají se.
 */
@Composable
fun ReactionPickerScreen(chatId: Long, messageId: Long, onDone: () -> Unit) {
    val conversations = koinInject<ConversationRepository>()
    val scope = rememberCoroutineScope()
    var choices by remember { mutableStateOf<List<String>?>(null) }
    var chosen by remember { mutableStateOf(emptySet<String>()) }

    LaunchedEffect(chatId, messageId) {
        chosen = conversations.message(chatId, messageId).getOrNull()
            ?.reactions?.filter { it.isChosen }?.map { it.emoji }?.toSet().orEmpty()
        choices = conversations.availableReactions(chatId, messageId)
            .onFailure { logger.w(it) { "Reakce u zprávy $messageId se nenačetly." } }
            .getOrNull()?.emoji.orEmpty()
            .filter { customEmojiIdOf(it) == null }
            .take(MAX_CHOICES)
    }

    val current = choices
    when {
        current == null -> LoadingScreen(stringResource(Res.string.connection_connecting))
        current.isEmpty() -> MessageScreen(stringResource(Res.string.wear_reactions_none))
        else -> {
            val state = rememberTransformingLazyColumnState()
            ScreenScaffold(scrollState = state) { contentPadding ->
                TransformingLazyColumn(state = state, contentPadding = contentPadding) {
                    item { ListHeader { Text(stringResource(Res.string.wear_reactions_title)) } }
                    items(current) { emoji ->
                        val isChosen = emoji in chosen
                        Button(
                            onClick = {
                                scope.launch {
                                    conversations.setReaction(chatId, messageId, emoji, add = !isChosen)
                                        .onFailure { logger.w(it) { "Reakce $emoji na zprávu $messageId neprošla." } }
                                    onDone()
                                }
                            },
                            label = {
                                Text(
                                    emoji,
                                    style = MaterialTheme.typography.titleLarge,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            },
                            colors = if (isChosen) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
