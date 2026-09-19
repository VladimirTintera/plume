package eu.tintera.plume.features.conversation.items

import org.jetbrains.compose.resources.painterResource
import eu.tintera.plume.resources.format_quote_24dp
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.lerp
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    /**
     * `false` = citace je horní částí karty zprávy (viz `QuotedBubble`): bez vlastní plochy
     * a zaoblení, s odsazením bubliny a v přirozené šířce — plochu i šířku řeší karta.
     */
    contained: Boolean = true,
) {
    val clickable = if (onClick != null) {
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier
    }
    if (!contained) {
        Column(modifier.then(clickable).padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)) {
            QuoteLines(message, partnerName, accent)
        }
        return
    }

    /*
     * Citace je TÓNOVANÝ KONTEJNER: zaoblená plocha v odstínu akcentu. Dřív to byl jen proužek
     * s textem, který v bublině splýval s odpovědí pod ním; plocha citaci vyčlení sama, takže
     * proužek navíc nic nepřidával. Plocha se bere
     * z akcentu s nízkou průhledností, ne z pevné role — v odchozí `primary` bublině je akcentem
     * barva popředí, takže kontejner zůstane tónem téže bubliny.
     */
    val quoteShape = RoundedCornerShape(QUOTE_CORNER)
    Row(
        modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp)
            .clip(quoteShape)
            .background(accent.copy(alpha = QUOTE_CONTAINER_ALPHA))
            .then(clickable)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            QuoteLines(message, partnerName, accent)
        }
    }
}

/**
 * Kdo a co: ikona citace a jméno v barvě [accent], pod nimi nejvýš dva řádky textu (nebo popis
 * přílohy). Ikona stojí VŽDY před jménem — na kartě zprávy i nad vstupním polem, na obou
 * stranách konverzace; stejně jako tužka u úpravy zprávy říká, k čemu se to vztahuje.
 */
@Composable
private fun QuoteLines(message: ChatMessage, partnerName: String?, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(Res.drawable.format_quote_24dp),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.padding(end = 4.dp).size(QUOTE_ICON_SIZE),
        )
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
    }
    Text(
        // U zprávy bez textu (samotná fotka) by citace byla prázdná.
        text = message.text.ifBlank { message.media.describe() },
        style = MaterialTheme.typography.bodySmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Plocha vloženého obsahu v kartě zprávy — citace nahoře, popis webu u náhledu odkazu dole.
 * `tertiaryContainer` na obou stranách: je to cizí obsah, takže se od textu zprávy musí lišit,
 * a terciární role nesplyne s žádnou z barev bublin.
 *
 * Zvýrazněná příchozí zpráva (skok, hledání) ale sama přejde do `tertiaryContainer` — plocha pak
 * ztmavne o kus k `tertiary`, jinak by obě části karty splynuly.
 */
@Composable
internal fun MessageBodyScope.insetContainerColor(): Color {
    val colors = MaterialTheme.colorScheme
    return if (frame.emphasized && !message.isOutgoing) {
        lerp(colors.tertiaryContainer, colors.tertiary, EMPHASIZED_INSET_SHIFT)
    } else {
        colors.tertiaryContainer
    }
}

private const val EMPHASIZED_INSET_SHIFT = 0.22f

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

/** Ikona citace před jménem — o kus menší než písmo jména, aby jméno zůstalo hlavní. */
private val QUOTE_ICON_SIZE = 16.dp

/** Zaoblení kontejneru citace. Menší než bublina — je v ní vnořený. */
internal val QUOTE_CORNER = 8.dp

/** Jak moc je kontejner citace tónovaný akcentem. Dost na odlišení, ne na soupeření s textem. */
internal const val QUOTE_CONTAINER_ALPHA = 0.14f
