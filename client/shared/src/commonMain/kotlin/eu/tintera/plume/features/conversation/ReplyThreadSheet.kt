package eu.tintera.plume.features.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.asDate
import eu.tintera.plume.core.ui.asRelativeTime
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.groupedItem
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.features.conversation.items.describe
import eu.tintera.plume.features.conversation.items.summaryLine
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.reply_thread_count
import eu.tintera.plume.resources.reply_thread_more
import eu.tintera.plume.resources.reply_thread_paused
import eu.tintera.plume.resources.reply_thread_replying_to
import eu.tintera.plume.resources.reply_thread_root_missing
import eu.tintera.plume.resources.reply_thread_scanning
import eu.tintera.plume.resources.reply_thread_title
import eu.tintera.plume.resources.sender_you
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * **Vlákno odpovědí** jako seznam (viz [ConversationReplyThread]): kdo, kdy a celý text, od
 * nejstarší zprávy. Zpráva, ze které se vlákno otevřelo, je vybraná a seznam se na ní otevře.
 * Kde vlákno odbočí (zpráva neodpovídá na tu těsně nad sebou), stojí nad textem „Odpovídá: …",
 * jinak by se větve nedaly rozeznat.
 *
 * Ťuknutí skočí na zprávu v konverzaci a sheet zavře ([onPick]), stejně jako seznam výsledků
 * hledání. Dole je stav procházení historie: hledá se, nebo „Prohledáno do …" s „Hledat dál".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ReplyThreadSheet(
    sheet: ReplyThreadSheet,
    /** Kdo píše v soukromém chatu, kde zpráva odesílatele nenese. */
    partnerName: String?,
    onPick: (messageId: Long) -> Unit,
    onScanMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    val messages = sheet.messages
    val listState = rememberLazyListState()
    // Na výchozí zprávu jen jednou, až dorazí řetěz citací nad ní. Odbočky, které přibudou nad
    // ní později, ji neposunou: seznam drží první viditelnou položku podle klíče.
    val originIndex = messages.indexOfFirst { it.id == sheet.originId }
    var scrolledToOrigin by remember { mutableStateOf(false) }
    LaunchedEffect(originIndex) {
        if (!scrolledToOrigin && originIndex > 0) {
            scrolledToOrigin = true
            listState.scrollToItem(originIndex + HEADER_ITEMS)
        }
    }
    val you = stringResource(Res.string.sender_you)
    fun senderOf(message: ChatMessage): String = if (message.isOutgoing) you else message.senderName ?: partnerName.orEmpty()
    val byId = messages.associateBy { it.id }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(Modifier.padding(horizontal = 24.dp)) {
            SheetTitle(stringResource(Res.string.reply_thread_title))
            Text(
                pluralStringResource(Res.plurals.reply_thread_count, messages.size, messages.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp).navigationBarsPadding(),
        ) {
            item(key = "root-missing") {
                if (sheet.rootMissing) {
                    Text(
                        stringResource(Res.string.reply_thread_root_missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            }
            itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                val selected = message.id == sheet.originId
                // Odbočka: odpovídá na jinou zprávu vlákna než na tu těsně nad sebou (z alba
                // je v seznamu jen první fotka, takže se porovnává i album).
                val parent = message.replyToMessageId?.let(byId::get)
                val previous = messages.getOrNull(index - 1)
                val branches = parent != null && previous != null && parent.id != previous.id &&
                    (parent.mediaAlbumId == 0L || parent.mediaAlbumId != previous.mediaAlbumId)
                ThreadRow(
                    message = message,
                    sender = senderOf(message),
                    replyingTo = parent?.takeIf { branches },
                    replyingToSender = parent?.let(::senderOf).orEmpty(),
                    selected = selected,
                    modifier = Modifier
                        .groupedItem(
                            index,
                            messages.size,
                            if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.groupRow,
                            selected = selected,
                        )
                        .clickable { onPick(message.id) },
                )
            }
            item(key = "status") {
                ScanStatus(sheet, onScanMore)
            }
        }
    }
}

@Composable
private fun ThreadRow(
    message: ChatMessage,
    sender: String,
    /** Zpráva, na kterou tahle odpovídá, když to není ta těsně nad ní (odbočka); jinak `null`. */
    replyingTo: ChatMessage?,
    replyingToSender: String,
    selected: Boolean,
    modifier: Modifier,
) {
    val content = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
    val supporting = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    ListItem(
        // Jméno samo nestačí — ve vlákně často píšou pořád titíž dva lidé; začátek zprávy řekne, kam odbočka vede.
        overlineContent = replyingTo?.let { parent ->
            {
                Text(
                    stringResource(Res.string.reply_thread_replying_to, replyingToSender, parent.summaryLine()),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        supportingContent = { Text(message.threadBody(), maxLines = BODY_MAX_LINES, overflow = TextOverflow.Ellipsis) },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = content,
            overlineColor = supporting,
            supportingColor = content,
        ),
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(sender, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                message.timestamp.asRelativeTime(),
                style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                color = supporting,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/** Hledá se — indikátor s větou; zastavené — kam až se došlo a „Hledat dál"; hotové — nic. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScanStatus(sheet: ReplyThreadSheet, onScanMore: () -> Unit) {
    val pausedAt = sheet.pausedAt
    when {
        sheet.scanning -> Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LoadingIndicator(Modifier.size(32.dp))
            Text(
                stringResource(Res.string.reply_thread_scanning),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        pausedAt != null -> Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(Res.string.reply_thread_paused, pausedAt.asDate()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onScanMore, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(Res.string.reply_thread_more))
            }
        }
    }
}

/**
 * Text zprávy pro řádek vlákna: „[Fotka] popisek", vlastní text, nebo náš popis (anketa,
 * servisní oznámení) — jako otisk zprávy při kopírování výběru, jen přeložený v kompozici.
 */
@Composable
private fun ChatMessage.threadBody(): String {
    val label = media
        ?.takeUnless { it is MessageMedia.AnimatedEmoji || it is MessageMedia.Dice || it is MessageMedia.Contact }
        ?.describe()
        ?.takeIf { it.isNotBlank() }
        ?.let { "[$it]" }
    val text = copyableText().ifBlank { description?.resolve().orEmpty() }
    return listOfNotNull(label, text.takeIf { it.isNotBlank() }).joinToString(" ")
}

/** Položky seznamu před zprávami (věta o smazaném začátku). */
private const val HEADER_ITEMS = 1

private const val BODY_MAX_LINES = 8
