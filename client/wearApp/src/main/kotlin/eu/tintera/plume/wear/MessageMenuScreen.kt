package eu.tintera.plume.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ConversationRepository
import eu.tintera.plume.core.telegram.MessageActions
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.customEmojiIdOf
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.connection_connecting
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.reply_24dp
import eu.tintera.plume.resources.wear_confirm_not_deleted
import eu.tintera.plume.resources.wear_confirm_not_sent
import eu.tintera.plume.resources.wear_message_delete
import eu.tintera.plume.resources.wear_message_delete_for_all
import eu.tintera.plume.resources.wear_message_delete_for_me
import eu.tintera.plume.resources.wear_message_delete_title
import eu.tintera.plume.resources.wear_message_reply
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private val logger = Logger.withTag("WearMessageMenu")

/** Kolik reakcí se nabídne — na hodinkách se v dlouhém seznamu nikdo nehrabe. */
private const val MAX_REACTIONS = 12

private val REACTION_SIZE = 40.dp

private const val REACTIONS_PER_ROW = 4

/**
 * Nabídka nad zprávou (ťuknutí na bublinu): nahoře začátek zprávy, pod ním mřížka reakcí, pak
 * odpověď s citací a smazání vlastní zprávy. Dřív ťuknutí otevíralo rovnou výběr reakce a na
 * konkrétní zprávu (ve skupině konkrétnímu člověku) odpovědět nešlo.
 *
 * - **Reakce:** emoji, která chat u zprávy dovoluje (`availableReactions`, nejvýš
 *   [MAX_REACTIONS]); daná je plná a ťuknutí ji odebere. Vlastní emoji hodinky nevykreslí.
 * - **Odpověď** otevře systémové zadávání s citovanou zprávou v nadpisu a pošle se jako odpověď
 *   na ni (`replyToMessageId`); konverzace ji uvidí z `newMessages`.
 * - **Smazání** jen u vlastní zprávy, kterou server smazat dovolí ([MessageActions]), po potvrzení.
 *   Pro všechny, když to jde (ve skupině vždycky), jinak jen u sebe — dialog říká, které platí.
 *
 * Po akci se nabídka zavře. Nezdar ohlásí potvrzovací dialog a nabídka zůstane.
 */
@Composable
fun MessageMenuScreen(chatId: Long, messageId: Long, onDone: () -> Unit) {
    val conversations = koinInject<ConversationRepository>()
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf<MenuData?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<StringResource?>(null) }
    var problemShown by remember { mutableStateOf(false) }
    val fail: (StringResource) -> Unit = {
        problem = it
        problemShown = true
    }

    LaunchedEffect(chatId, messageId) {
        val message = async { conversations.message(chatId, messageId).getOrNull() }
        val actions = async {
            conversations.messageActions(chatId, messageId)
                .onFailure { logger.w(it) { "Akce nad zprávou $messageId se nenačetly." } }
                .getOrDefault(MessageActions.Fallback)
        }
        val reactions = async {
            conversations.availableReactions(chatId, messageId)
                .onFailure { logger.w(it) { "Reakce u zprávy $messageId se nenačetly." } }
                .getOrNull()?.emoji.orEmpty()
                .filter { customEmojiIdOf(it) == null }
                .take(MAX_REACTIONS)
        }
        loaded = MenuData(message.await(), actions.await(), reactions.await())
    }

    val openReply = rememberReplyInput { text ->
        scope.launch {
            conversations.send(chatId, text, replyToMessageId = messageId)
                .onSuccess { onDone() }
                .onFailure {
                    logger.w(it) { "Odpověď na zprávu $messageId z hodinek neodešla." }
                    fail(Res.string.wear_confirm_not_sent)
                }
        }
    }

    val data = loaded
    if (data == null) {
        LoadingScreen(stringResource(Res.string.connection_connecting))
        return
    }
    val message = data.message
    val preview = message?.let { previewOf(it) }.orEmpty()
    val chosen = message?.reactions?.filter { it.isChosen }?.map { it.emoji }?.toSet().orEmpty()
    val canDelete = message?.isOutgoing == true && (data.actions.canDeleteForAll || data.actions.canDeleteForSelf)

    val state = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    ScreenScaffold(scrollState = state) { contentPadding ->
        TransformingLazyColumn(state = state, contentPadding = contentPadding) {
            if (preview.isNotBlank()) {
                item {
                    ListHeader(
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    ) { Text(preview, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                }
            }
            val onReact: (String) -> Unit = { emoji ->
                scope.launch {
                    conversations.setReaction(chatId, messageId, emoji, add = emoji !in chosen)
                        .onFailure { logger.w(it) { "Reakce $emoji na zprávu $messageId neprošla." } }
                    onDone()
                }
            }
            // Po řádcích, ne jako jedna položka: u okraje displeje se pak zmenšuje každý řádek
            // zvlášť a transformace neseřezává rohová tlačítka celé mřížky (ověřeno na emulátoru).
            data.reactions.chunked(REACTIONS_PER_ROW).forEach { row ->
                item { ReactionRow(row, chosen, onReact, Modifier.transformedItem(this, spec)) }
            }
            if (data.actions.canReply) {
                item {
                    Button(
                        onClick = { openReply(preview) },
                        icon = { Icon(painterResource(Res.drawable.reply_24dp), contentDescription = null) },
                        label = { Text(stringResource(Res.string.wear_message_reply)) },
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    )
                }
            }
            if (canDelete) {
                item {
                    Button(
                        onClick = { confirmDelete = true },
                        icon = { Icon(painterResource(Res.drawable.delete_24dp), contentDescription = null) },
                        label = { Text(stringResource(Res.string.wear_message_delete)) },
                        colors = ButtonDefaults.filledTonalButtonColors(contentColor = MaterialTheme.colorScheme.error, iconColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    )
                }
            }
        }
    }

    AlertDialog(
        visible = confirmDelete,
        onDismissRequest = { confirmDelete = false },
        icon = { Icon(painterResource(Res.drawable.delete_24dp), contentDescription = null) },
        title = { Text(stringResource(Res.string.wear_message_delete_title)) },
        text = {
            Text(stringResource(if (data.actions.canDeleteForAll) Res.string.wear_message_delete_for_all else Res.string.wear_message_delete_for_me))
        },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = {
                    confirmDelete = false
                    scope.launch {
                        conversations.deleteMessages(chatId, listOf(messageId), revoke = data.actions.canDeleteForAll)
                            .onSuccess { onDone() }
                            .onFailure {
                                logger.w(it) { "Zprávu $messageId se z hodinek nepodařilo smazat." }
                                fail(Res.string.wear_confirm_not_deleted)
                            }
                    }
                },
            )
        },
    )
    FailureConfirmation(
        visible = problemShown,
        text = problem?.let { stringResource(it) }.orEmpty(),
        onDismiss = { problemShown = false },
    )
}

private class MenuData(val message: ChatMessage?, val actions: MessageActions, val reactions: List<String>)

/**
 * Řádek mřížky reakcí: kulatá tlačítka, daná plná. Dřív byla každá reakce celým řádkem přes
 * šířku a dvanáct řádků se muselo prorolovat.
 */
@Composable
private fun ReactionRow(reactions: List<String>, chosen: Set<String>, onReact: (String) -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        reactions.forEach { emoji ->
            val content: @Composable BoxScope.() -> Unit = { Text(emoji, style = MaterialTheme.typography.titleMedium) }
            if (emoji in chosen) {
                FilledIconButton(onClick = { onReact(emoji) }, shapes = IconButtonDefaults.animatedShapes(), modifier = Modifier.size(REACTION_SIZE), content = content)
            } else {
                FilledTonalIconButton(onClick = { onReact(emoji) }, shapes = IconButtonDefaults.animatedShapes(), modifier = Modifier.size(REACTION_SIZE), content = content)
            }
        }
    }
}

/** Začátek zprávy pro nadpis nabídky a citaci v zadávání: text, emoji z přílohy, nebo popis. */
@Composable
private fun previewOf(message: ChatMessage): String {
    val emoji = (message.media as? MessageMedia.AnimatedEmoji)?.emoji ?: (message.media as? MessageMedia.Dice)?.emoji
    return message.text.ifEmpty { emoji ?: message.description?.resolve().orEmpty() }
}
