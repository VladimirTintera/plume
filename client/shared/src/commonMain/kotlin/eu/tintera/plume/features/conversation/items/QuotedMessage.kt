package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_reply
import eu.tintera.plume.resources.conversation_voice_message
import eu.tintera.plume.resources.media_photo
import eu.tintera.plume.resources.media_sticker
import eu.tintera.plume.resources.media_video
import eu.tintera.plume.resources.sender_you
import org.jetbrains.compose.resources.stringResource

/**
 * Citovaná zpráva — v bublině nad obsahem odpovědi i nad vstupním polem.
 *
 * Svislý pruh vlevo nese barvu `primary`; je to jediný prvek, který cituje cizí obsah, a musí být
 * na první pohled odlišitelný od vlastního textu bubliny.
 */
@Composable
internal fun QuotedMessage(
    message: ChatMessage,
    partnerName: String?,
    modifier: Modifier = Modifier,
    /**
     * Barva proužku a jména.
     *
     * Musí jít předat: uvnitř odchozí bubliny je pozadí `primary`, takže natvrdo zadaná
     * `primary` by citaci udělala neviditelnou — stejná barva na stejné barvě.
     */
    accent: Color = MaterialTheme.colorScheme.primary,
    /**
     * Ťuknutí skočí na citovanou zprávu. `null` nad vstupním polem, kde se na ni skákat nemá
     * proč — uživatel ji právě vybral.
     */
    onClick: (() -> Unit)? = null,
    /**
     * Dlouhý stisk se musí zopakovat — potomek s vlastním klikáním ho k bublině nepustí, takže
     * by nad citací nešla otevřít nabídka. Stejná past jako u fotky.
     */
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .then(
                if (onClick != null) {
                    // Ořez kvůli vlnce; citace nemá vlastní pozadí, takže bez něj by se kreslila
                    // do ostrého obdélníku.
                    Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    Modifier
                },
            )
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )

        Column {
            Text(
                text = when {
                    message.isOutgoing -> stringResource(Res.string.sender_you)
                    // Ve skupině nese jméno sama zpráva; v soukromém chatu je odesílatel
                    // vždycky protějšek, takže se vezme název chatu.
                    else -> message.senderName ?: partnerName ?: stringResource(Res.string.conversation_reply)
                },
                style = MaterialTheme.typography.labelLarge,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // U zprávy bez textu (samotná fotka) by citace byla prázdná.
                text = message.text.ifBlank { message.media.describe() },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun MessageMedia?.describe(): String = when (this) {
    is MessageMedia.Photo -> stringResource(Res.string.media_photo)
    is MessageMedia.Attachment -> label
    // V citaci není kam kreslit obrázek, takže samolepka se popíše svým emoji.
    is MessageMedia.Sticker -> emoji.ifBlank { stringResource(Res.string.media_sticker) }
    is MessageMedia.AnimatedEmoji -> emoji
    is MessageMedia.VoiceNote -> stringResource(Res.string.conversation_voice_message)
    is MessageMedia.Video -> stringResource(Res.string.media_video)
    is MessageMedia.Document -> fileName
    null -> ""
}
