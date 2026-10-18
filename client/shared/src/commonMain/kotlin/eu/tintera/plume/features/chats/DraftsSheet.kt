package eu.tintera.plume.features.chats

import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.resources.tag_24dp
import eu.tintera.plume.core.ui.accentOn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import eu.tintera.plume.core.ui.readString
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
    drafts: List<DraftItem>,
    deleted: DraftDeleted?,
    onOpen: (DraftItem) -> Unit,
    onDelete: (DraftItem) -> Unit,
    onDeletedShown: () -> Unit,
    onUndo: (DraftDeleted) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberExpandedSheetState()
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
                    message = readString(Res.string.chats_draft_deleted),
                    actionLabel = readString(Res.string.chats_undo),
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
        // Řádky včetně těch, které právě odjíždějí: smazaný koncept z `drafts` zmizí hned, ale
        // v seznamu má dojet animací, jinak by řádek i výška sheetu skočily v jednom snímku.
        val leaving = remember { LeavingDrafts(drafts) }
        var leftCount by remember { mutableIntStateOf(0) }
        val rows = remember(drafts, leftCount) { leaving.rows(drafts) }
        val present = remember(drafts) { drafts.mapTo(HashSet()) { it.key } }

        // Snackbar POD seznamem, ne přes něj: sheet je vysoký jen na obsah, a po smazání by
        // „Koncept smazán" zakryl zbylý koncept (ověřeno na emulátoru se dvěma koncepty).
        // `animateContentSize`: výška sheetu dojede i tam, kde se mění skokem (snackbar).
        Column(
            Modifier
                .navigationBarsPadding()
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        ) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                items(rows, key = { it.key }) { item ->
                    val isPresent = item.key in present
                    // Koncepty, se kterými se přehled otevřel, jsou vidět hned; vrácený („Vrátit")
                    // se rozbalí.
                    val visibility = remember { MutableTransitionState(leaving.initiallyShown(item.key)) }
                    visibility.targetState = isPresent
                    if (!isPresent && visibility.isIdle && !visibility.currentState) {
                        LaunchedEffect(Unit) {
                            leaving.gone(item.key)
                            leftCount++
                        }
                    }
                    AnimatedVisibility(
                        visibleState = visibility,
                        enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) +
                            fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                    ) {
                        DraftRow(
                            item = item,
                            enabled = isPresent,
                            onClick = {
                                // Napřed sheet dolů, pak teprve chat — jinak by konverzace naskočila pod
                                // odjíždějícím sheetem a jeho zavření by vypadalo jako zásek.
                                scope.launch { sheetState.hide() }.invokeOnCompletion { onOpen(item) }
                            },
                            onDelete = {
                                busy++
                                onDelete(item)
                            },
                        )
                    }
                }
            }
            SnackbarHost(snackbarHostState)
        }
    }
}

/**
 * Koncepty, které z přehledu zmizely, ale ještě dojíždějí animací — na svém původním místě.
 *
 * Obyčejná třída, ne stav: řádky se z ní počítají v `remember(drafts, …)` přímo při kompozici.
 * Odjíždějící řádek tak je vidět už v prvním snímku bez konceptu; s efektem by jeden snímek chyběl
 * a řádek by bliknul.
 */
private class LeavingDrafts(initial: List<DraftItem>) {
    private val initialIds = initial.mapTo(HashSet()) { it.key }
    private var last: List<DraftItem> = initial
    private val leaving = mutableMapOf<String, Pair<Int, DraftItem>>()

    fun initiallyShown(key: String): Boolean = key in initialIds

    fun rows(drafts: List<DraftItem>): List<DraftItem> {
        val ids = drafts.mapTo(HashSet()) { it.key }
        last.forEachIndexed { index, item -> if (item.key !in ids) leaving.getOrPut(item.key) { index to item } }
        ids.forEach { leaving.remove(it) }
        last = drafts
        val rows = drafts.toMutableList()
        leaving.values.sortedBy { it.first }.forEach { (index, chat) -> rows.add(index.coerceAtMost(rows.size), chat) }
        return rows
    }

    fun gone(key: String) {
        leaving.remove(key)
    }
}

@Composable
private fun DraftRow(item: DraftItem, enabled: Boolean, onClick: () -> Unit, onDelete: () -> Unit) {
    val chat = item.chat
    val draft = item.draft
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(
            chatId = chat.avatarColorId,
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
            // Koncept v tématu: pod názvem skupiny téma, akcentem jako v seznamu témat. Akcent
            // se ověřuje proti ploše sheetu (`accentOn`), v paletě se zvýšeným kontrastem ustoupí.
            item.topic?.let { topic ->
                val surface = BottomSheetDefaults.ContainerColor
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val color = accentOn(surface, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurface)
                    Icon(painterResource(Res.drawable.tag_24dp), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                    Text(
                        topic.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
        IconButton(onClick = onDelete, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(Res.drawable.delete_24dp), contentDescription = stringResource(Res.string.chats_draft_delete))
        }
    }
}

private val DRAFT_AVATAR_SIZE = 48.dp

private const val DRAFT_MAX_LINES = 3

/** Jak dlouho po „Vrátit" čekat na vrácený koncept, než se prázdný přehled smí zavřít. */
private const val UNDO_WAIT_MS = 2_000L
