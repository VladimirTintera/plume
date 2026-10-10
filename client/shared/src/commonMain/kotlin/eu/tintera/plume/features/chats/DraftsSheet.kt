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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import eu.tintera.plume.resources.chats_draft_delete
import eu.tintera.plume.resources.chats_draft_deleted
import eu.tintera.plume.resources.chats_drafts
import eu.tintera.plume.resources.chats_undo
import eu.tintera.plume.resources.delete_24dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
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
 * by byl slepá ulička. Po smazání posledního konceptu ale až s doběhnutým snackbarem, jinak by
 * „Vrátit" odjelo se sheetem.
 *
 * **Smazání nemá potvrzovací dialog, má „Vrátit"** — jako archivace v seznamu chatů. Snackbar je
 * uvnitř sheetu: snackbar seznamu by ležel pod ním.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DraftsSheet(
    drafts: List<ChatSummary>,
    deleted: DraftDeleted?,
    onOpen: (ChatSummary) -> Unit,
    onDelete: (ChatSummary) -> Unit,
    onDeletedShown: () -> Unit,
    onUndo: (DraftDeleted) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(SheetValue.Hidden)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentDrafts by rememberUpdatedState(drafts)
    /**
     * Smazání a snackbary s „Vrátit", které ještě běží; dokud nějaké je, prázdný přehled se
     * nezavírá. Počítá se od ŤUKNUTÍ, ne od výsledku: update, že koncept zmizel, může přijít
     * dřív než výsledek smazání, a přehled by se zavřel bez „Vrátit".
     */
    var busy by remember { mutableIntStateOf(0) }
    LaunchedEffect(drafts.isEmpty(), busy) { if (drafts.isEmpty() && busy == 0) onDismiss() }

    // Snackbar běží mimo efekt: ten se vynulováním události restartuje a zrušil by ho (viz
    // „Vrátit" v ChatActionsHost).
    LaunchedEffect(deleted) {
        val event = deleted ?: return@LaunchedEffect
        onDeletedShown()
        if (event.restore == null) {
            busy = (busy - 1).coerceAtLeast(0)
            return@LaunchedEffect
        }
        scope.launch {
            try {
                // Další smazání odsune předchozí „Vrátit", jinak by snackbary čekaly ve frontě.
                snackbarHostState.currentSnackbarData?.dismiss()
                val result = snackbarHostState.showSnackbar(
                    message = getString(Res.string.chats_draft_deleted),
                    actionLabel = getString(Res.string.chats_undo),
                    duration = SnackbarDuration.Short,
                )
                if (result == SnackbarResult.ActionPerformed) {
                    onUndo(event)
                    // Vrácený koncept přijde updatem; bez čekání by se přehled bez posledního
                    // konceptu zavřel dřív, než se vrátí.
                    withTimeoutOrNull(UNDO_WAIT_MS) { snapshotFlow { currentDrafts.isNotEmpty() }.first { it } }
                }
            } finally {
                // Bez záporu: po otočení displeje začíná počítadlo znovu od nuly.
                busy = (busy - 1).coerceAtLeast(0)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            stringResource(Res.string.chats_drafts),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
        )
        // Snackbar POD seznamem, ne přes něj: sheet je vysoký jen na obsah, a po smazání by
        // „Koncept smazán" zakryl zbylý koncept (ověřeno na emulátoru se dvěma koncepty).
        Column(Modifier.navigationBarsPadding()) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                items(drafts, key = { it.id }) { chat ->
                    DraftRow(
                        chat = chat,
                        onClick = {
                            // Napřed sheet dolů, pak teprve chat — jinak by konverzace naskočila pod
                            // odjíždějícím sheetem a jeho zavření by vypadalo jako zásek.
                            scope.launch { sheetState.hide() }.invokeOnCompletion { onOpen(chat) }
                        },
                        onDelete = {
                            busy++
                            onDelete(chat)
                        },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
            SnackbarHost(snackbarHostState)
        }
    }
}

@Composable
private fun DraftRow(chat: ChatSummary, onClick: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val draft = chat.draft ?: return
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
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
        IconButton(onClick = onDelete, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(Res.drawable.delete_24dp), contentDescription = stringResource(Res.string.chats_draft_delete))
        }
    }
}

private val DRAFT_AVATAR_SIZE = 48.dp

private const val DRAFT_MAX_LINES = 3

/** Jak dlouho po „Vrátit" čekat na vrácený koncept, než se prázdný přehled smí zavřít. */
private const val UNDO_WAIT_MS = 2_000L
