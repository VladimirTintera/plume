package eu.tintera.plume.features.conversation.items

import androidx.compose.material3.LocalContentColor
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
        PaddedColumn {
            SenderName()
            Attachment(
                media = media,
                accent = if (message.isOutgoing) onSurface else MaterialTheme.colorScheme.primary,
            )
            EditedLabel(color = onSurface, modifier = Modifier.align(Alignment.End))
        }
    }
}
