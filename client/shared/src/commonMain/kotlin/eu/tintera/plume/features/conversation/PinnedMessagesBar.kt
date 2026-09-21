package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.features.conversation.items.describe
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.conversation_all_pinned
import eu.tintera.plume.resources.conversation_pinned_count
import eu.tintera.plume.resources.conversation_pinned_message
import eu.tintera.plume.resources.conversation_pinned_message_numbered
import eu.tintera.plume.resources.format_list_bulleted_24dp
import eu.tintera.plume.resources.menu_unpin
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Pruh připnutých zpráv pod hlavičkou: VŽDY JEDEN ŘÁDEK, ať je připnutých kolik chce.
 *
 * Kanály připnuté příspěvky většinou neodepínají, jen přidávají další, takže seznam přes tři řádky
 * visel nad konverzací jako trvalý blok. Ukazuje se proto jen jedna zpráva — na začátku
 * nejnovější — a ťuknutí na ni skočí a posune pruh na starší (dokola). Tak to dělá i oficiální
 * klient. Kolik jich je a kde v řadě stojíme, říká svislý ukazatel vlevo; celý seznam je za
 * ikonou vpravo v sheetu.
 *
 * Karta plave nad zprávami s okrajem, ne přes celou šířku: je to doplněk konverzace, ne kus
 * hlavičky, a zaoblená plocha s drobným stínem se od zpráv pod ní odlepí sama.
 */
@Composable
internal fun PinnedMessagesBar(
    messages: List<ChatMessage>,
    /** Index ukázané zprávy v [messages] (0 = nejnovější). */
    index: Int,
    canUnpin: Boolean,
    onOpen: (ChatMessage) -> Unit,
    onUnpin: (ChatMessage) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val current = messages.getOrNull(index) ?: return
    val multiple = messages.size > 1

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(PINNED_BAR_CORNER),
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier
                .clickable { onOpen(current) }
                .heightIn(min = PINNED_BAR_HEIGHT)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PinnedPositionIndicator(count = messages.size, index = index)

            // Starší zpráva přijede shora — historií se jde nahoru. Skoková výměna textu by se
            // dala přehlédnout.
            AnimatedContent(
                targetState = current,
                contentKey = { it.id },
                transitionSpec = {
                    val older = messages.indexOfFirst { it.id == targetState.id } >
                        messages.indexOfFirst { it.id == initialState.id }
                    val sign = if (older) 1 else -1
                    (slideInVertically { -sign * it / 2 } + fadeIn())
                        .togetherWith(slideOutVertically { sign * it / 2 } + fadeOut())
                },
                modifier = Modifier.weight(1f).padding(start = 10.dp),
                label = "pinned-message",
            ) { message ->
                val number = messages.size - messages.indexOfFirst { it.id == message.id }
                PinnedContent(
                    message = message,
                    title = if (multiple) {
                        stringResource(Res.string.conversation_pinned_message_numbered, number)
                    } else {
                        stringResource(Res.string.conversation_pinned_message)
                    },
                )
            }

            when {
                // U víc zpráv je odepínání v seznamu — křížek vedle jedné z nich by sváděl
                // k domněnce, že zavře celý pruh.
                multiple -> IconButton(onClick = onShowAll) {
                    Icon(
                        painterResource(Res.drawable.format_list_bulleted_24dp),
                        contentDescription = stringResource(Res.string.conversation_all_pinned),
                    )
                }
                canUnpin -> IconButton(onClick = { onUnpin(current) }) {
                    Icon(
                        painterResource(Res.drawable.close_24dp),
                        contentDescription = stringResource(Res.string.menu_unpin),
                        modifier = Modifier.size(20.dp),
                    )
                }
                else -> Box(Modifier.width(8.dp))
            }
        }
    }
}

/** Titulek, náhled přílohy a text jedné připnuté zprávy. */
@Composable
private fun PinnedContent(message: ChatMessage, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        message.media.thumbnailFileId()?.let { fileId ->
            RemoteImage(
                fileId = fileId,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(PINNED_THUMBNAIL)
                    .clip(RoundedCornerShape(6.dp)),
            )
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                // Připnutá fotka bez popisku by byla prázdný řádek.
                text = message.text.ifBlank { message.media.describe() }.replace('\n', ' '),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Svislý ukazatel pozice: jeden dílek na zprávu, nejnovější dole, zvýrazněná ta ukázaná.
 *
 * Víc než [INDICATOR_MAX_SEGMENTS] dílků by byly nerozeznatelné čárky, takže se u delší řady
 * ukazuje jen okno kolem aktuální zprávy a posouvá se s ní (jako v oficiálním klientu).
 */
@Composable
private fun PinnedPositionIndicator(count: Int, index: Int) {
    val visible = count.coerceAtMost(INDICATOR_MAX_SEGMENTS)
    // Okno nad řadou od nejnovější: aktuální zpráva se drží uvnitř, nejraději ne na kraji.
    val windowStart = (index - (visible - 2)).coerceIn(0, (count - visible).coerceAtLeast(0))
    val segment = (INDICATOR_HEIGHT - INDICATOR_GAP * (visible - 1)) / visible

    Column(
        Modifier.height(INDICATOR_HEIGHT).width(3.dp),
        verticalArrangement = Arrangement.spacedBy(INDICATOR_GAP),
    ) {
        // Shora dolů od nejstarší v okně k nejnovější.
        for (slot in visible - 1 downTo 0) {
            val active = windowStart + slot == index
            val color by animateColorAsState(
                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                MaterialTheme.motionScheme.defaultEffectsSpec(),
            )
            Box(
                Modifier
                    .width(3.dp)
                    .height(segment)
                    .clip(RoundedCornerShape(50))
                    .background(color),
            )
        }
    }
}

/**
 * Všechny připnuté zprávy v sheetu, od nejnovější. Ťuknutí na řádek skočí na zprávu, křížek ji
 * odepne (když to právo dovolí).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PinnedMessagesSheet(
    messages: List<ChatMessage>,
    canUnpin: Boolean,
    onOpen: (ChatMessage) -> Unit,
    onUnpin: (ChatMessage) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val scope = rememberCoroutineScope()

    // Sheet se napřed schová, teprve pak se skáče — zmizení bez animace vypadá jako pád.
    fun hideThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = pluralStringResource(Res.plurals.conversation_pinned_count, messages.size, messages.size),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 4.dp, bottom = 8.dp),
        )
        LazyColumn(Modifier.padding(bottom = 8.dp)) {
            items(messages, key = { it.id }) { message ->
                PinnedSheetRow(
                    message = message,
                    canUnpin = canUnpin,
                    onOpen = { hideThen { onOpen(message) } },
                    // Sheet zůstává otevřený: odepíná se často víc zpráv po sobě.
                    onUnpin = { onUnpin(message) },
                )
            }
        }
    }
}

@Composable
private fun PinnedSheetRow(
    message: ChatMessage,
    canUnpin: Boolean,
    onOpen: () -> Unit,
    onUnpin: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = PINNED_BAR_HEIGHT)
            .clickable(onClick = onOpen)
            .padding(start = 24.dp, end = if (canUnpin) 12.dp else 24.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Špendlík u řádku není: připnuté jsou tu všechny, a nadpis sheetu to říká.
        message.media.thumbnailFileId()?.let { fileId ->
            RemoteImage(
                fileId = fileId,
                modifier = Modifier.padding(end = 12.dp).size(PINNED_THUMBNAIL).clip(RoundedCornerShape(6.dp)),
            )
        }
        Text(
            text = message.text.ifBlank { message.media.describe() }.replace('\n', ' '),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (canUnpin) {
            IconButton(onClick = onUnpin) {
                Icon(
                    painterResource(Res.drawable.close_24dp),
                    contentDescription = stringResource(Res.string.menu_unpin),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Obrázek do náhledu, jen u druhů, které obrázek nesou. Do 36dp čtverečku stačí nejmenší
 * varianta; ostrou verzi kvůli němu stahovat nemá smysl.
 */
private fun MessageMedia?.thumbnailFileId(): Int? = when (this) {
    is MessageMedia.Photo -> thumbnailFileId ?: fileId
    is MessageMedia.Video -> thumbnailFileId
    is MessageMedia.Document -> thumbnailFileId
    else -> null
}

/**
 * Připnutá, kterou uživatel právě míjí: nejnovější z těch, které leží NAD viditelnou částí.
 *
 * Pruh tím slouží i jako orientace v historii — ukazuje, pod kterou připnutou zprávou uživatel
 * zrovna je, místo aby napořád visel na nejnovější. Tak to dělá i oficiální klient.
 *
 * **Porovnává se STRIKTNĚ.** Připnutá, na kterou se právě skočilo, stojí nahoře viewportu;
 * kdyby se počítala jako „nad", ukazoval by ji pruh dál a ťuknutí by vedlo pořád na ni —
 * procházení připnutých by se zaseklo.
 *
 * [pinned] je od nejnovější po nejstarší. Nad nejstarší připnutou (tedy na začátku historie)
 * se ukazuje ona sama: výš už žádná není. Dokud se nic nevykreslilo ([topVisibleMessageId] je
 * `null`), platí nejnovější — s tou se pruh otevírá.
 */
internal fun pinnedForScroll(pinned: List<ChatMessage>, topVisibleMessageId: Long?): Long? {
    if (pinned.isEmpty()) return null
    val top = topVisibleMessageId ?: return pinned.first().id
    return (pinned.firstOrNull { it.id < top } ?: pinned.last()).id
}

/** Pořadí ukázané zprávy v pruhu, když zná jen id (seznam se mezitím mohl změnit). */
internal fun pinnedIndex(messages: List<ChatMessage>, shownId: Long?): Int =
    messages.indexOfFirst { it.id == shownId }.coerceAtLeast(0)

private val PINNED_BAR_HEIGHT = 52.dp
private val PINNED_BAR_CORNER = 16.dp
private val PINNED_THUMBNAIL = 36.dp
private val INDICATOR_HEIGHT = 36.dp
private val INDICATOR_GAP = 2.dp
private const val INDICATOR_MAX_SEGMENTS = 4
