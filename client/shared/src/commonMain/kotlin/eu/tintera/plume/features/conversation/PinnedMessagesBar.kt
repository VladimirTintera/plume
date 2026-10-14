package eu.tintera.plume.features.conversation

import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.conversation_unpin_all_body
import eu.tintera.plume.resources.conversation_unpin_all_title
import eu.tintera.plume.resources.conversation_unpin_all
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.features.conversation.items.summaryLine
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.conversation_all_pinned
import eu.tintera.plume.resources.conversation_pinned_count
import eu.tintera.plume.resources.conversation_pinned_message
import eu.tintera.plume.resources.conversation_pinned_message_numbered
import eu.tintera.plume.resources.format_list_bulleted_24dp
import eu.tintera.plume.resources.menu_unpin
import eu.tintera.plume.resources.push_pin_filled_24dp
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
 * klient. Kolik jich je a kde v řadě stojíme, říká titulek („Připnutá zpráva 3 z 5"); celý
 * seznam je za ikonou vpravo v sheetu.
 *
 * **Plovoucí pilulka ze skla, ne karta s proužkem.** Svislý proužek vlevo (dílek na zprávu) byl
 * jazyk M2 a v aplikaci už jednou ustoupil ploše (citace, náhled odkazu). Pruh je tvarem stejný
 * jako plovoucí lišty konverzace — pilulka, která se od zpráv pod ní odlepí stínem a sklem. Vlevo
 * je špendlík v sedmistranné sušence ([PinnedBadge]); ta se při přechodu na jinou připnutou
 * pootočí, takže změnu je vidět i koutkem oka, bez čtení titulku.
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
    /** Mléčné sklo nad seznamem: plocha je průhledná, barvu i rozostření nese sklo. */
    glass: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val current = messages.getOrNull(index) ?: return
    val multiple = messages.size > 1

    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = CircleShape,
        shadowElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier
                .clip(CircleShape)
                .then(glass)
                .clickable { onOpen(current) }
                .heightIn(min = PINNED_BAR_HEIGHT)
                // Odznak stojí od kraje tak, aby jeho kruh byl soustředný se zaoblením pilulky.
                .padding(start = PINNED_BADGE_INSET, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PinnedBadge(index = index)

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
                modifier = Modifier.weight(1f).padding(start = 12.dp),
                label = "pinned-message",
            ) { message ->
                val number = messages.size - messages.indexOfFirst { it.id == message.id }
                PinnedContent(
                    message = message,
                    title = if (multiple) {
                        stringResource(Res.string.conversation_pinned_message_numbered, number, messages.size)
                    } else {
                        stringResource(Res.string.conversation_pinned_message)
                    },
                )
            }

            when {
                // U víc zpráv je odepínání v seznamu — křížek vedle jedné z nich by sváděl
                // k domněnce, že zavře celý pruh.
                multiple -> IconButton(onClick = onShowAll, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        painterResource(Res.drawable.format_list_bulleted_24dp),
                        contentDescription = stringResource(Res.string.conversation_all_pinned),
                    )
                }
                canUnpin -> IconButton(onClick = { onUnpin(current) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        painterResource(Res.drawable.close_24dp),
                        contentDescription = stringResource(Res.string.menu_unpin),
                        modifier = Modifier.size(20.dp),
                    )
                }
                else -> Box(Modifier.width(12.dp))
            }
        }
    }
}

/**
 * Špendlík v sedmistranné sušence. Sušenka se s každou další připnutou pootočí o jednu vlnku
 * (starší po směru hodinových ručiček, novější proti), pružinou s přeskokem; špendlík stojí.
 *
 * Úhel je přímo funkce indexu, ne počítadlo kroků — při skoku o víc zpráv (odepnutí, scroll)
 * se sušenka protočí o víc vlnek, a stav navíc se nedrží.
 */
@Composable
private fun PinnedBadge(index: Int) {
    val rotation by animateFloatAsState(
        targetValue = index * PINNED_BADGE_STEP,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow),
        label = "pinned-badge",
    )
    val container = MaterialTheme.colorScheme.primaryContainer
    // Sedmistranná sušenka jinde v aplikaci nic neznamená (viz `ShapeMorphs`) a na rozdíl od
    // kruhu je na ní otočení vidět.
    val shape = MaterialShapes.Cookie7Sided.toShape()
    Box(Modifier.size(PINNED_BADGE_SIZE), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = rotation }
                .background(container, shape),
        )
        Icon(
            painterResource(Res.drawable.push_pin_filled_24dp),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp),
        )
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
                    .clip(RoundedCornerShape(10.dp)),
            )
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMediumEmphasized.tabularNumbers(),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                // Připnutá fotka bez popisku by byla prázdný řádek.
                text = message.summaryLine(),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
    onUnpinAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val scope = rememberCoroutineScope()
    var confirmUnpinAll by rememberSaveable { mutableStateOf(false) }

    // Sheet se napřed schová, teprve pak se skáče — zmizení bez animace vypadá jako pád.
    fun hideThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 4.dp, bottom = 12.dp),
        ) {
            Text(
                text = pluralStringResource(Res.plurals.conversation_pinned_count, messages.size, messages.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            // U jediné zprávy stačí křížek v řádku; „vše" dává smysl až od dvou.
            if (canUnpin && messages.size > 1) {
                TextButton(onClick = { confirmUnpinAll = true }) {
                    Text(stringResource(Res.string.conversation_unpin_all), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        // Blok zaoblených řádků jako seznam chatů, kontakty nebo odkazy v profilu — dřív to byly
        // holé řádky na ploše sheetu, jiný vzhled pro tentýž „seznam věcí".
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
        ) {
            itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                PinnedSheetRow(
                    message = message,
                    canUnpin = canUnpin,
                    onOpen = { hideThen { onOpen(message) } },
                    // Sheet zůstává otevřený: odepíná se často víc zpráv po sobě.
                    onUnpin = { onUnpin(message) },
                    // Odepnutá zpráva odejde animací a sousedé si přeberou krajní zaoblení.
                    modifier = Modifier.animateItem().clip(groupItemShape(index, messages.size)),
                )
            }
        }
    }

    // „Vrátit" by muselo připnout každou zprávu zvlášť, proto se ptá předem — na rozdíl od jedné.
    if (confirmUnpinAll) {
        AlertDialog(
            onDismissRequest = { confirmUnpinAll = false },
            title = { Text(pluralStringResource(Res.plurals.conversation_unpin_all_title, messages.size, messages.size)) },
            text = { Text(stringResource(Res.string.conversation_unpin_all_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnpinAll = false
                    hideThen(onUnpinAll)
                }) {
                    Text(stringResource(Res.string.conversation_unpin_all), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnpinAll = false }) { Text(stringResource(Res.string.common_cancel)) }
            },
        )
    }
}

@Composable
private fun PinnedSheetRow(
    message: ChatMessage,
    canUnpin: Boolean,
    onOpen: () -> Unit,
    onUnpin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        headlineContent = {
            Text(
                text = message.summaryLine(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        // Kdy zpráva padla — u kanálu s desítkami připnutých se podle toho orientuje nejlíp.
        supportingContent = { Text(message.timestamp.asDayLabel(), maxLines = 1) },
        // Špendlík u řádku není: připnuté jsou tu všechny, a nadpis sheetu to říká. Bez náhledu
        // slot zůstane prázdný (`null`), jinak by si ListItem nechal odsazení pro nic.
        leadingContent = message.media.thumbnailFileId()?.let { fileId ->
            {
                RemoteImage(
                    fileId = fileId,
                    modifier = Modifier.size(PINNED_SHEET_THUMBNAIL).clip(RoundedCornerShape(12.dp)),
                )
            }
        },
        trailingContent = if (canUnpin) {
            {
                IconButton(onClick = onUnpin, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        painterResource(Res.drawable.close_24dp),
                        contentDescription = stringResource(Res.string.menu_unpin),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        } else {
            null
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = modifier.clickable(onClick = onOpen),
    )
}

/**
 * Obrázek do náhledu, jen u druhů, které obrázek nesou. Do 36dp čtverečku stačí nejmenší
 * varianta; ostrou verzi kvůli němu stahovat nemá smysl.
 */
private fun MessageMedia?.thumbnailFileId(): Int? = when (this) {
    // Spoiler, citlivé a mizející médium v pruhu náhled nemá: pruh visí nad konverzací pořád
    // a ukázal by ostře, co bublina schovává.
    is MessageMedia.Photo -> (thumbnailFileId ?: fileId).takeUnless { hasSpoiler || isSecret || selfDestructType != null }
    is MessageMedia.Video -> thumbnailFileId.takeUnless { hasSpoiler || isSecret || selfDestructType != null }
    is MessageMedia.Document -> thumbnailFileId.takeUnless { isSensitive }
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

private val PINNED_BAR_HEIGHT = 56.dp
private val PINNED_THUMBNAIL = 36.dp
private val PINNED_SHEET_THUMBNAIL = 48.dp
private val PINNED_BADGE_SIZE = 40.dp
/** (výška pilulky − odznak) / 2: kruh odznaku je soustředný s jejím zaoblením. */
private val PINNED_BADGE_INSET = (PINNED_BAR_HEIGHT - PINNED_BADGE_SIZE) / 2

/** O kolik se sušenka pootočí na jednu připnutou: jedna vlnka, takže přechod dosedne na týž obrys. */
private const val PINNED_BADGE_STEP = 360f / 7
