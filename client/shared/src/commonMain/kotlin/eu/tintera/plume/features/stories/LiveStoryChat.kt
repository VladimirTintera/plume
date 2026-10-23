package eu.tintera.plume.features.stories

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.LiveMessage
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.confirm
import eu.tintera.plume.core.ui.contextClickable
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.features.media.ON_SCRIM
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.common_delete
import eu.tintera.plume.resources.conversation_send
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.flag_24dp
import eu.tintera.plume.resources.send_24dp
import eu.tintera.plume.resources.star_filled_24dp
import eu.tintera.plume.resources.story_live_message_delete_spam
import eu.tintera.plume.resources.story_live_message_hint
import eu.tintera.plume.resources.story_live_paid_only
import eu.tintera.plume.resources.story_live_stars
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Chat živého vysílání přes spodek obrazu: nejnovější zpráva dole, starší odjíždějí nahoru
 * a v horní třetině mizí. Pod ním závoj, ať je text čitelný na jakémkoli obrazu.
 *
 * Placená zpráva a zpráva vysílajícího mají vlastní plochu. Barvy úrovní od serveru
 * (`updateGroupCallMessageLevels`) Plume nebere — přijdou jen jednou po startu a nesedí
 * k paletě; odlišení nese plocha a počet hvězd.
 *
 * Podržení zprávy, kterou jde smazat, nabídne smazání; správci i s nahlášením spamu.
 */
@Composable
internal fun LiveChatMessages(
    messages: List<LiveMessage>,
    canModerate: Boolean,
    onDelete: (LiveMessage, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (messages.isEmpty()) return
    val list = rememberLazyListState()
    val newest = messages.last().id
    var seen by remember { mutableIntStateOf(newest) }
    // Kdo zrovna nečte starší, tomu chat dojede na novou zprávu; kdo odroloval, toho nechá.
    // Seznam drží pozici podle klíče, takže index první viditelné poskočí o přibyvší zprávy.
    LaunchedEffect(newest) {
        val previous = messages.indexOfFirst { it.id == seen }
        val added = if (previous < 0) messages.size else messages.lastIndex - previous
        if (list.firstVisibleItemIndex <= added + 1) list.animateScrollToItem(0)
        seen = newest
    }
    Box(
        modifier.background(
            Brush.verticalGradient(0f to Color.Transparent, 1f to MaterialTheme.colorScheme.scrim.copy(alpha = SCRIM_ALPHA)),
        ),
    ) {
        LazyColumn(
            state = list,
            reverseLayout = true,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxSize()
                .fadingTop(),
        ) {
            items(messages.asReversed(), key = { it.id }) { message ->
                LiveChatRow(message, canModerate, onDelete, Modifier.animateItem())
            }
        }
    }
}

@Composable
private fun LiveChatRow(
    message: LiveMessage,
    canModerate: Boolean,
    onDelete: (LiveMessage, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember(message.id) { mutableStateOf(false) }
    val highlighted = message.starCount > 0 || message.isFromOwner
    val colors = MaterialTheme.colorScheme
    val (container, content) = when {
        message.isFromOwner -> colors.primaryContainer to colors.onPrimaryContainer
        message.starCount > 0 -> colors.tertiaryContainer to colors.onTertiaryContainer
        else -> Color.Transparent to ON_SCRIM
    }
    Box(modifier) {
        Surface(
            color = container,
            contentColor = content,
            shape = ShapeDefaults.Large,
            modifier = Modifier.contextClickable(
                enabled = message.canBeDeleted,
                onLongClick = { menu = true },
                onClick = {},
            ),
        ) {
            Row(
                Modifier.padding(horizontal = if (highlighted) 8.dp else 0.dp, vertical = if (highlighted) 6.dp else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Avatar(chatId = message.senderId, title = message.senderName, avatarFileId = message.avatarFileId, size = AVATAR_SIZE)
                Column(Modifier.weight(1f, fill = false)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            message.senderName,
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            color = content.copy(alpha = if (highlighted) 1f else NAME_ALPHA),
                            maxLines = 1,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (message.starCount > 0) StarCount(message.starCount)
                    }
                    if (message.text.isNotEmpty()) {
                        Text(message.text, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        if (menu) {
            ExpressiveMenu(
                expanded = true,
                onDismiss = { menu = false },
                groups = listOf(
                    buildList {
                        add(
                            MenuAction(stringResource(Res.string.common_delete), Res.drawable.delete_24dp, destructive = true) {
                                menu = false
                                onDelete(message, false)
                            },
                        )
                        if (canModerate) {
                            add(
                                MenuAction(stringResource(Res.string.story_live_message_delete_spam), Res.drawable.flag_24dp, destructive = true) {
                                    menu = false
                                    onDelete(message, true)
                                },
                            )
                        }
                    },
                ),
            )
        }
    }
}

/** Hvězda a počet; čtečce celá věta („50 hvězd Telegramu"). */
@Composable
private fun StarCount(count: Long, modifier: Modifier = Modifier) {
    val description = pluralStringResource(Res.plurals.story_live_stars, count.toInt(), count.toInt())
    Row(
        modifier.clearAndSetSemantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(painterResource(Res.drawable.star_filled_24dp), contentDescription = null, modifier = Modifier.size(16.dp))
        Text(count.toString(), style = MaterialTheme.typography.labelLarge.tabularNumbers())
    }
}

/**
 * Placená reakce od diváka: pilulka s hvězdami a jménem vyjede zespodu a rozplyne se. Klíč
 * roste s každou reakcí, takže i dvě stejné po sobě proletí obě.
 */
@Composable
internal fun LiveReactionBurst(reaction: LiveReaction?, modifier: Modifier = Modifier) {
    val current = reaction ?: return
    key(current.id) {
        val rise = remember { Animatable(0f) }
        val alpha = remember { Animatable(1f) }
        val spatial = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
        val fade = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
        LaunchedEffect(Unit) {
            rise.animateTo(1f, spatial)
            delay(BURST_HOLD_MS)
            alpha.animateTo(0f, fade)
        }
        Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = ShapeDefaults.ExtraLarge,
            modifier = modifier.graphicsLayer {
                translationY = (1f - rise.value) * BURST_RISE.toPx()
                this.alpha = alpha.value * rise.value.coerceIn(0f, 1f)
            },
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StarCount(current.starCount)
                current.senderName?.let { name ->
                    Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Lišta pod živým příběhem: pole pro komentář, a když psaní stojí hvězdy, místo něj věta —
 * Plume hvězdami neplatí. S textem se objeví Odeslat; pole jede nad klávesnici, obraz
 * zůstává vidět (chat se čte právě při psaní, proto bez ztmavnutí jako u odpovědi).
 */
@Composable
internal fun LiveMessageBar(
    live: LiveStoryState,
    onSend: (String, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(live.key) { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current

    fun send() {
        if (sending || text.isBlank()) return
        sending = true
        haptics.confirm()
        onSend(text) { sent ->
            sending = false
            if (sent) text = ""
        }
    }

    Row(
        modifier
            .fillMaxWidth()
            .imePadding()
            .height(STORY_BAR_HEIGHT)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            live.canSend -> {
                StoryTextField(
                    text = text,
                    onTextChange = { text = it },
                    hint = stringResource(Res.string.story_live_message_hint),
                    onSend = ::send,
                    modifier = Modifier.weight(1f),
                )
                if (text.isNotBlank()) {
                    FilledIconButton(onClick = ::send, enabled = !sending, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.send_24dp), stringResource(Res.string.conversation_send))
                    }
                }
            }

            live.paidOnly -> Text(
                stringResource(Res.string.story_live_paid_only),
                color = ON_SCRIM.copy(alpha = HINT_TEXT_ALPHA),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )

            else -> Spacer(Modifier.weight(1f))
        }
    }
    // Vysílání skončilo uprostřed psaní — klávesnice by visela nad větou o konci.
    LaunchedEffect(live.canSend) { if (!live.canSend) focusManager.clearFocus() }
}

/** Horní okraj seznamu se rozplývá, aby starší zprávy neusekl ostrý okraj. */
private fun Modifier.fadingTop(): Modifier = graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, FADE_END to Color.Black),
            blendMode = BlendMode.DstIn,
        )
    }

private val AVATAR_SIZE = 28.dp
private val BURST_RISE = 48.dp
private const val BURST_HOLD_MS = 1_200L
private const val SCRIM_ALPHA = 0.55f
private const val NAME_ALPHA = 0.8f
private const val HINT_TEXT_ALPHA = 0.8f
private const val FADE_END = 0.3f
