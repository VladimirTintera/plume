package eu.tintera.plume.features.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.appendWithCustomEmoji
import eu.tintera.plume.core.ui.asRelativeTime
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.chatTitle
import eu.tintera.plume.core.ui.customEmojiInlineContent
import eu.tintera.plume.core.ui.isSavedMessages
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_drafts
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Přehled rozepsaných zpráv napříč chaty (menu účtu → Koncepty). Ťuknutí otevře chat, kde koncept
 * čeká ve vstupním poli.
 *
 * **Sheet, ne vlastní obrazovka jako archiv.** Archiv je seznam, ve kterém se žije — otevírá se
 * z něj konverzace vedle v dvoupanelu a má vlastní zásobník rout. Koncepty jsou rozcestník: najít
 * rozdělaný chat a jít do něj. Konverzace se otevře v hlavním seznamu, kam patří.
 *
 * **Text konceptu na víc řádků**, ne jeden jako v seznamu chatů: tady je koncept to jediné, co
 * řádek říká, a podle začátku věty se rozepsané zprávy od sebe špatně rozeznávají.
 *
 * Když poslední koncept zmizí (odeslaný z jiného zařízení), sheet se zavře sám — prázdný přehled
 * by byl slepá ulička.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DraftsSheet(
    drafts: List<ChatSummary>,
    onOpen: (ChatSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(SheetValue.Hidden)
    val scope = rememberCoroutineScope()
    LaunchedEffect(drafts.isEmpty()) { if (drafts.isEmpty()) onDismiss() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            stringResource(Res.string.chats_drafts),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().navigationBarsPadding()) {
            items(drafts, key = { it.id }) { chat ->
                DraftRow(
                    chat = chat,
                    onClick = {
                        // Napřed sheet dolů, pak teprve chat — jinak by konverzace naskočila pod
                        // odjíždějícím sheetem a jeho zavření by vypadalo jako zásek.
                        scope.launch { sheetState.hide() }.invokeOnCompletion { onOpen(chat) }
                    },
                )
            }
        }
    }
}

@Composable
private fun DraftRow(chat: ChatSummary, onClick: () -> Unit) {
    val draft = chat.draft ?: return
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(
            chatId = chat.id,
            title = chat.title,
            avatarFileId = chat.avatarFileId,
            size = DRAFT_AVATAR_SIZE,
            isSavedMessages = isSavedMessages(chat.id),
            shape = chat.kind.avatarShape(),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chatTitle(chat.id, chat.title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    draft.timestamp.asRelativeTime(),
                    style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            // Vlastní emoji v konceptu jako obrázek, stejně jako v řádku seznamu chatů.
            val customEmoji = mutableSetOf<Long>()
            val text = buildAnnotatedString { customEmoji += appendWithCustomEmoji(draft.text, draft.entities) }
            Text(
                text,
                inlineContent = customEmojiInlineContent(customEmoji),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = DRAFT_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val DRAFT_AVATAR_SIZE = 48.dp

private const val DRAFT_MAX_LINES = 3
