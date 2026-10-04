package eu.tintera.plume.features.conversation.items

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import eu.tintera.plume.core.telegram.MessageMedia

/*
 * Zprávy, které jsou celé jedno gesto — pár emoji nebo samolepka — se kreslí bez bubliny.
 *
 * Bublina kolem nich nemá co ohraničovat: samolepka má vlastní tvar s průhledností a velká emoji
 * jsou obrázek samy o sobě. Bez pozadí se kreslí na plochu obrazovky, takže barva popředí patří
 * K NÍ (výchozí `LocalContentColor`), ne k barvě bubliny, která tam není.
 */

/**
 * Zpráva z pár emoji (nejvýš [MAX_LARGE_EMOJI]), velkým písmem a bez bubliny.
 *
 * Míň emoji = větší: jedno je gesto, tři už jsou věta, takže se velikost s počtem krotí.
 */
@Immutable
internal data class LargeEmojiMessageItem(
    override val frame: MessageFrameData,
    val emojiCount: Int,
) : MessageItem {

    @Composable
    override fun MessageBodyScope.Body() {
        val onSurface = LocalContentColor.current
        PaddedColumn {
            SenderName()
            MessageText(
                linkColor = if (message.isOutgoing) onSurface else MaterialTheme.colorScheme.primary,
                style = when (emojiCount) {
                    1 -> MaterialTheme.typography.displayLarge
                    2 -> MaterialTheme.typography.displayMedium
                    else -> MaterialTheme.typography.displaySmall
                },
            )
            EditedLabel(color = onSurface, modifier = Modifier.align(Alignment.End))
        }
    }
}

/** Samolepka nebo animované emoji, bez bubliny. Ťuknutí na samolepku otevře její sadu. */
@Immutable
internal data class StickerMessageItem(
    override val frame: MessageFrameData,
    val media: MessageMedia,
) : MessageItem {

    @Composable
    override fun MessageBodyScope.Body() {
        val onSurface = LocalContentColor.current
        val accent = if (message.isOutgoing) onSurface else MaterialTheme.colorScheme.primary
        PaddedColumn {
            SenderName()
            if (media is MessageMedia.VideoNote) {
                VideoNoteWithTranscript(media, accent)
            } else {
                Attachment(media = media, accent = accent)
            }
            EditedLabel(color = onSurface, modifier = Modifier.align(Alignment.End))
        }
    }
}

/**
 * Kolečko s přepisem: tlačítko stojí vedle kolečka na volné straně a přepis pod ním v kartě
 * v barvě bubliny — holý text by se na ploše konverzace ztratil, stejně jako popisek u fotky.
 */
@Composable
private fun MessageBodyScope.VideoNoteWithTranscript(note: MessageMedia.VideoNote, accent: Color) {
    val transcript = rememberTranscript(note.transcript, note.durationSeconds)
    val button: @Composable () -> Unit = {
        if (transcript.hasButton) {
            TranscribeButton(
                state = transcript,
                container = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
    BelowWithSameWidth(
        top = {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (message.isOutgoing) button()
                Attachment(media = note, accent = accent)
                if (!message.isOutgoing) button()
            }
        },
        below = {
            AnimatedVisibility(
                visible = transcript.shown,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Surface(color = bubbleColor, contentColor = onBubbleColor, shape = RoundedCornerShape(BUBBLE_CORNER)) {
                    TranscriptBody(
                        state = transcript,
                        color = onBubbleColor,
                        modifier = Modifier.padding(horizontal = MESSAGE_PADDING_H, vertical = MESSAGE_PADDING_V),
                    )
                }
            }
        },
    )
}
