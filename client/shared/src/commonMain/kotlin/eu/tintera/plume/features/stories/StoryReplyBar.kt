package eu.tintera.plume.features.stories

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.customEmojiIdOf
import eu.tintera.plume.core.ui.CustomEmojiIcon
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.confirm
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.features.conversation.ReactionPickerSheet
import eu.tintera.plume.features.media.ON_SCRIM
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.add_reaction_24dp
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.story_delete
import eu.tintera.plume.resources.story_views
import eu.tintera.plume.resources.visibility_24dp
import eu.tintera.plume.resources.conversation_send
import eu.tintera.plume.resources.favorite_24dp
import eu.tintera.plume.resources.menu_more_reactions
import eu.tintera.plume.resources.send_24dp
import eu.tintera.plume.resources.story_react_heart
import eu.tintera.plume.resources.story_reaction_remove
import eu.tintera.plume.resources.story_reply_hint
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** Výška lišty pod příběhem; příběh nad ní nechává místo, aby ho lišta nepřekryla. */
internal val STORY_BAR_HEIGHT = 64.dp

/**
 * Lišta pod cizím příběhem: pole pro odpověď (jen kde jde odpovědět — u kanálu ne), „+" pro
 * výběr reakce a srdce. Srdce je rychlá reakce jako v oficiální aplikaci; je-li reakce už
 * dána, ukazuje ji a ťuknutí ji sundá. S napsaným textem se srdce vymění za Odeslat.
 *
 * Při psaní a ve výběru reakce příběh stojí ([onComposingChange]); pole jede nad klávesnici
 * a zbytek příběhu ztmavne — ťuknutí do ztmavnutí psaní ukončí.
 */
@Composable
internal fun StoryReplyBar(
    story: MessageMedia.Story,
    reactionChoices: List<String>,
    onComposingChange: (Boolean) -> Unit,
    onReact: (String) -> Unit,
    onReply: (String, (Boolean) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember(story.posterChatId, story.storyId) { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var pickerOpen by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val haptics = LocalHapticFeedback.current
    val composingChange by rememberUpdatedState(onComposingChange)
    val composing = focused || text.isNotEmpty() || pickerOpen
    LaunchedEffect(composing) { composingChange(composing) }

    fun send() {
        if (sending || text.isBlank()) return
        sending = true
        haptics.confirm()
        onReply(text) { sent ->
            sending = false
            if (sent) {
                text = ""
                focusManager.clearFocus()
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        if (focused) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = COMPOSE_SHADE_ALPHA))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        focusManager.clearFocus()
                    },
            )
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .height(STORY_BAR_HEIGHT)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (story.canBeReplied) {
                StoryTextField(
                    text = text,
                    onTextChange = { text = it },
                    hint = stringResource(Res.string.story_reply_hint),
                    onSend = ::send,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp)
                        .onFocusChanged { focused = it.isFocused },
                )
            } else {
                Spacer(Modifier.weight(1f))
            }

            if (text.isNotBlank()) {
                FilledIconButton(onClick = ::send, enabled = !sending, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(Res.drawable.send_24dp), stringResource(Res.string.conversation_send))
                }
            } else {
                if (reactionChoices.isNotEmpty()) {
                    IconButton(onClick = { pickerOpen = true }, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            painterResource(Res.drawable.add_reaction_24dp),
                            stringResource(Res.string.menu_more_reactions),
                            tint = ON_SCRIM,
                        )
                    }
                }
                val chosen = story.chosenReaction
                IconButton(
                    onClick = {
                        haptics.confirm()
                        onReact(chosen ?: HEART)
                    },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    if (chosen != null) {
                        ReactionGlyph(chosen, size = 26, description = stringResource(Res.string.story_reaction_remove))
                    } else {
                        Icon(
                            painterResource(Res.drawable.favorite_24dp),
                            stringResource(Res.string.story_react_heart),
                            tint = ON_SCRIM,
                        )
                    }
                }
            }
        }
    }

    if (pickerOpen) {
        ReactionPickerSheet(
            emoji = reactionChoices,
            chosen = setOfNotNull(story.chosenReaction),
            gridHeight = PICKER_GRID_HEIGHT,
            onPick = { reaction ->
                pickerOpen = false
                haptics.confirm()
                onReact(reaction)
            },
            onDismiss = { pickerOpen = false },
        )
    }
}

/** Jednořádkové pole přes příběh: obrys v barvě nad závojem, Odeslat na klávesnici. */
@Composable
internal fun StoryTextField(
    text: String,
    onTextChange: (String) -> Unit,
    hint: String,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = ON_SCRIM),
        cursorBrush = SolidColor(ON_SCRIM),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Send,
        ),
        keyboardActions = KeyboardActions(onSend = { onSend() }),
        modifier = modifier,
        decorationBox = { inner ->
            Box(
                Modifier
                    .height(FIELD_HEIGHT)
                    .border(1.dp, ON_SCRIM.copy(alpha = FIELD_BORDER_ALPHA), CircleShape)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (text.isEmpty()) {
                    Text(
                        hint,
                        color = ON_SCRIM.copy(alpha = HINT_ALPHA),
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                inner()
            }
        },
    )
}

/**
 * Lišta pod vlastním příběhem: kolik lidí ho vidělo (ťuknutí otevře seznam diváků) a smazat.
 */
@Composable
internal fun OwnStoryBar(viewCount: Int?, onViewers: () -> Unit, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(STORY_BAR_HEIGHT)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            onClick = onViewers,
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.textButtonColors(contentColor = ON_SCRIM),
        ) {
            Icon(painterResource(Res.drawable.visibility_24dp), contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                pluralStringResource(Res.plurals.story_views, viewCount ?: 0, viewCount ?: 0),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onDelete, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(Res.drawable.delete_24dp), stringResource(Res.string.story_delete), tint = ON_SCRIM)
        }
    }
}

/** Velké emoji přes příběh po dané reakci — potvrzení, které je vidět i bez pohledu na lištu. */
@Composable
internal fun ReactionBurst(burst: Pair<Int, String>?, modifier: Modifier = Modifier) {
    val (id, reaction) = burst ?: return
    key(id) {
        val scale = remember { Animatable(BURST_START_SCALE) }
        val alpha = remember { Animatable(1f) }
        val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
        val fade = MaterialTheme.motionScheme.slowEffectsSpec<Float>()
        LaunchedEffect(Unit) {
            scale.animateTo(1f, spatial)
            delay(BURST_HOLD_MS)
            alpha.animateTo(0f, fade)
        }
        Box(
            modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.alpha = alpha.value
            },
        ) {
            ReactionGlyph(reaction, size = BURST_SIZE, description = null)
        }
    }
}

/** Krátké hlášení nad lištou (odpověď odeslána, chyba). Po chvíli zmizí samo. */
@Composable
internal fun StoryNotice(notice: UiText?, onShown: () -> Unit, modifier: Modifier = Modifier) {
    val shown by rememberUpdatedState(onShown)
    // Poslední text zůstane i během odjezdu, kdy už je `notice` null.
    var last by remember { mutableStateOf<UiText?>(null) }
    if (notice != null) last = notice
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(NOTICE_MS)
            shown()
        }
    }
    AnimatedVisibility(
        visible = notice != null,
        enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        modifier = modifier,
    ) {
        last?.let { text ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            ) {
                Text(
                    text.resolve(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Obyčejné emoji jako text, vlastní (klíč s id) jako obrázek. */
@Composable
internal fun ReactionGlyph(reaction: String, size: Int, description: String?) {
    val customId = customEmojiIdOf(reaction)
    val semantics = if (description != null) Modifier.semantics { contentDescription = description } else Modifier
    if (customId != null) {
        CustomEmojiIcon(customEmojiId = customId, size = size.dp, modifier = semantics)
    } else {
        // Mimo typografickou škálu schválně, jako v pruhu reakcí v konverzaci.
        Text(reaction, fontSize = (size * GLYPH_TEXT_SCALE).sp, modifier = semantics)
    }
}

/** Výchozí rychlá reakce na příběh, jako v oficiální aplikaci. Telegram srdce vede bez variačního selektoru. */
private const val HEART = "❤"

private val FIELD_HEIGHT = 44.dp
private val PICKER_GRID_HEIGHT = 320.dp
private const val FIELD_BORDER_ALPHA = 0.5f
private const val HINT_ALPHA = 0.7f
private const val COMPOSE_SHADE_ALPHA = 0.5f
private const val BURST_START_SCALE = 0.3f
private const val BURST_SIZE = 120
private const val BURST_HOLD_MS = 450L
private const val NOTICE_MS = 2_000L
private const val GLYPH_TEXT_SCALE = 0.85f
