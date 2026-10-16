package eu.tintera.plume.features.conversation.items

import eu.tintera.plume.core.ui.orText
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.resolve
import org.jetbrains.compose.resources.painterResource
import eu.tintera.plume.resources.format_quote_24dp
import androidx.compose.ui.Alignment
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import eu.tintera.plume.core.ui.contextClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import eu.tintera.plume.core.telegram.Minithumbnail
import eu.tintera.plume.core.ui.BlurredThumbnail
import eu.tintera.plume.core.ui.RemoteImage
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.ProductKind
import eu.tintera.plume.core.telegram.title
import eu.tintera.plume.core.ui.appendWithCustomEmoji
import eu.tintera.plume.core.ui.customEmojiInlineContent
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.content_gift
import eu.tintera.plume.resources.content_story
import eu.tintera.plume.resources.content_giveaway
import eu.tintera.plume.resources.content_giveaway_winners
import eu.tintera.plume.resources.content_game
import eu.tintera.plume.resources.content_invoice
import eu.tintera.plume.resources.conversation_reply
import eu.tintera.plume.resources.conversation_voice_message
import eu.tintera.plume.resources.media_photo
import eu.tintera.plume.resources.media_sticker
import eu.tintera.plume.resources.content_gif
import eu.tintera.plume.resources.content_video_note
import eu.tintera.plume.resources.content_contact
import eu.tintera.plume.resources.content_paid
import eu.tintera.plume.resources.content_live_location
import eu.tintera.plume.resources.content_location
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
    /**
     * Citovaný úryvek, když odpověď necituje celou zprávu ([ChatMessage.replyQuote]); místo
     * začátku zprávy se ukáže on.
     */
    quoteText: String? = null,
) {
    val clickable = if (onClick != null) {
        Modifier.contextClickable(onClick = onClick, onLongClick = onLongClick)
    } else {
        Modifier
    }
    if (!contained) {
        QuoteContent(
            message,
            partnerName,
            accent,
            quoteText,
            modifier.then(clickable).padding(horizontal = MESSAGE_PADDING_H, vertical = MESSAGE_PADDING_V),
        )
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
        QuoteContent(message, partnerName, accent, quoteText, Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

/** Náhled přílohy vlevo (když nějaký je) a vedle něj kdo a co. */
@Composable
private fun QuoteContent(
    message: ChatMessage,
    partnerName: String?,
    accent: Color,
    quoteText: String?,
    modifier: Modifier,
) {
    val thumbnail = message.media.quoteThumbnail()
    if (thumbnail == null) {
        Column(modifier) { QuoteLines(message, partnerName, accent, quoteText) }
        return
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        QuoteThumbnailImage(thumbnail, Modifier.padding(end = 8.dp).size(QUOTE_THUMBNAIL_SIZE))
        Column { QuoteLines(message, partnerName, accent, quoteText) }
    }
}

@Composable
private fun QuoteThumbnailImage(thumbnail: QuoteThumbnail, modifier: Modifier) {
    val shape: Shape = if (thumbnail.round) CircleShape else RoundedCornerShape(QUOTE_THUMBNAIL_CORNER)
    val clipped = modifier.clip(shape)
    when {
        thumbnail.hidden -> thumbnail.placeholder?.let { BlurredThumbnail(it, clipped) }
        thumbnail.fileId != null -> RemoteImage(
            fileId = thumbnail.fileId,
            modifier = if (thumbnail.transparent) modifier else clipped,
            contentScale = if (thumbnail.transparent) ContentScale.Fit else ContentScale.Crop,
            background = if (thumbnail.transparent) Color.Transparent else MaterialTheme.colorScheme.surfaceContainerHighest,
            placeholder = thumbnail.placeholder,
        )
        else -> thumbnail.placeholder?.let { BlurredThumbnail(it, clipped) }
    }
}

/**
 * Obrázek do citace: co se nakreslí vlevo od jména, aby odpověď na fotku nebyla jen „Fotka".
 *
 * [fileId] je nejmenší varianta — do čtverečku 36 dp se ostrá verze stahovat nemá.
 * [hidden] = spoiler nebo citlivý obsah: kreslí se JEN rozmazaný [placeholder], nic se
 * nestahuje, jinak by citace prozradila, co bublina nad ní schovává.
 */
internal data class QuoteThumbnail(
    val fileId: Int?,
    val placeholder: Minithumbnail?,
    val hidden: Boolean = false,
    /** Video kolečko je i v citaci kolečko. */
    val round: Boolean = false,
    /** Samolepka: průhledná, bez ořezu a podložky. */
    val transparent: Boolean = false,
)

/**
 * Náhled citované přílohy, nebo `null`, když není co ukázat.
 *
 * **Mizející a tajné médium náhled nemá** — ani rozmazaný: citace žije v konverzaci déle než
 * médium samo a náhled by z něj po vypršení zůstal. Skrytému (spoiler, citlivý) zbyde rozmazání,
 * a bez rozmazaného náhledu nic — prázdný čtvereček by nic neřekl.
 */
internal fun MessageMedia?.quoteThumbnail(): QuoteThumbnail? = when (this) {
    is MessageMedia.Photo -> when {
        isSecret || selfDestructType != null -> null
        hasSpoiler || isSensitive -> minithumbnail?.let { QuoteThumbnail(null, it, hidden = true) }
        else -> QuoteThumbnail(thumbnailFileId ?: fileId, minithumbnail)
    }
    is MessageMedia.Video -> when {
        isSecret || selfDestructType != null -> null
        hasSpoiler || isSensitive -> minithumbnail?.let { QuoteThumbnail(null, it, hidden = true) }
        thumbnailFileId == null && minithumbnail == null -> null
        else -> QuoteThumbnail(thumbnailFileId, minithumbnail)
    }
    is MessageMedia.Animation -> when {
        hasSpoiler || isSensitive -> minithumbnail?.let { QuoteThumbnail(null, it, hidden = true) }
        thumbnailFileId == null && minithumbnail == null -> null
        else -> QuoteThumbnail(thumbnailFileId, minithumbnail)
    }
    is MessageMedia.VideoNote ->
        if (thumbnailFileId == null && minithumbnail == null) null
        else QuoteThumbnail(thumbnailFileId, minithumbnail, round = true)
    // Citlivý soubor a samolepka rozmazaný náhled nemají — nezbývá než žádný.
    is MessageMedia.Document -> thumbnailFileId?.takeUnless { isSensitive }?.let { QuoteThumbnail(it, null) }
    is MessageMedia.Audio ->
        if (coverFileId == null && coverMinithumbnail == null) null
        else QuoteThumbnail(coverFileId, coverMinithumbnail)
    is MessageMedia.Sticker -> (thumbnailFileId ?: fileId)?.takeUnless { isSensitive }
        ?.let { QuoteThumbnail(it, null, transparent = true) }
    is MessageMedia.Attachment -> thumbnailFileId?.let { QuoteThumbnail(it, null) }
    is MessageMedia.Product -> photo?.let { QuoteThumbnail(it.thumbnailFileId ?: it.fileId, it.minithumbnail) }
    // Nezaplacené médium: jen rozmazaný náhled, soubor server nevydá.
    is MessageMedia.PaidMedia -> minithumbnail?.let { QuoteThumbnail(null, it, hidden = true) }
    is MessageMedia.AnimatedEmoji, is MessageMedia.Dice, is MessageMedia.VoiceNote,
    is MessageMedia.Location, is MessageMedia.Call, is MessageMedia.Contact, null -> null
    is MessageMedia.Giveaway -> sticker?.let { it.thumbnailFileId ?: it.fileId }
        ?.let { QuoteThumbnail(it, null, transparent = true) }
    is MessageMedia.Gift -> sticker?.let { it.thumbnailFileId ?: it.fileId }
        ?.let { QuoteThumbnail(it, null, transparent = true) }
    is MessageMedia.Story -> content.quoteThumbnail()
}

/**
 * Kdo a co: ikona citace a jméno v barvě [accent], pod nimi nejvýš dva řádky textu (nebo popis
 * přílohy). Ikona stojí VŽDY před jménem — na kartě zprávy i nad vstupním polem, na obou
 * stranách konverzace; stejně jako tužka u úpravy zprávy říká, k čemu se to vztahuje.
 */
@Composable
private fun QuoteLines(message: ChatMessage, partnerName: String?, accent: Color, quoteText: String?) {
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
    // Úryvek má přednost: citovala se jen ta část. U zprávy bez textu (samotná fotka) by
    // citace byla prázdná, proto popis přílohy. Vlastní emoji se kreslí jen u celé zprávy —
    // úryvek entity nenese a offsety zprávy by do něj ukazovaly jinam.
    val customEmoji = mutableSetOf<Long>()
    val text = when {
        quoteText != null -> AnnotatedString(quoteText)
        message.text.isNotBlank() -> buildAnnotatedString {
            customEmoji += appendWithCustomEmoji(message.text, message.entities)
        }
        // Zpráva bez vlastního textu: náš popis (servisní oznámení, anketa), jinak popis přílohy.
        else -> AnnotatedString(message.description?.resolve() ?: message.media.describe())
    }
    Text(
        text = text,
        inlineContent = customEmojiInlineContent(customEmoji),
        style = MaterialTheme.typography.bodySmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Zpráva v jednom řádku (pruh připnutých, naplánované zprávy): vlastní text, jinak náš popis
 * (anketa, servisní oznámení), jinak popis přílohy.
 */
@Composable
internal fun ChatMessage.summaryLine(): String =
    text.ifBlank { description?.resolve() ?: media.describe() }.replace('\n', ' ')

@Composable
internal fun MessageMedia?.describe(): String = describeText().resolve()

/**
 * Popis přílohy jako [UiText] — i mimo kompozici, kde se překládá `resolveNow` (kopírování výběru
 * s časem a jménem).
 */
internal fun MessageMedia?.describeText(): UiText = when (this) {
    is MessageMedia.Photo -> UiText(Res.string.media_photo)
    is MessageMedia.Attachment -> label
    // Vedle náhledu samolepky stojí její emoji (bez náhledu je to jediné, co o ní víme).
    is MessageMedia.Sticker -> emoji.orText(Res.string.media_sticker)
    is MessageMedia.AnimatedEmoji -> UiText.Raw(emoji)
    is MessageMedia.Dice -> UiText.Raw(label)
    is MessageMedia.VoiceNote -> UiText(Res.string.conversation_voice_message)
    is MessageMedia.Video -> UiText(Res.string.media_video)
    is MessageMedia.Animation -> UiText(Res.string.content_gif)
    is MessageMedia.VideoNote -> UiText(Res.string.content_video_note)
    is MessageMedia.Audio -> UiText.Raw(listOfNotNull(performer, title).joinToString(" – "))
    is MessageMedia.Document -> UiText.Raw(fileName)
    is MessageMedia.Location -> title?.let(UiText::Raw)
        ?: UiText(if (isLive) Res.string.content_live_location else Res.string.content_location)
    is MessageMedia.Call -> UiText(outcome.title(isVideo, isGroup, isActive))
    is MessageMedia.Contact -> UiText(Res.string.content_contact, name.ifBlank { phone })
    is MessageMedia.PaidMedia -> UiText(Res.string.content_paid)
    is MessageMedia.Giveaway -> UiText(
        if (results == null) Res.string.content_giveaway else Res.string.content_giveaway_winners,
    )
    is MessageMedia.Gift -> UiText(Res.string.content_gift)
    is MessageMedia.Story -> UiText(Res.string.content_story)
    is MessageMedia.Product -> UiText(
        when (kind) {
            ProductKind.Invoice -> Res.string.content_invoice
            ProductKind.Game -> Res.string.content_game
        },
        title,
    )
    null -> UiText.Raw("")
}

/** Náhled přílohy v citaci — zhruba výška jména a řádku textu. */
private val QUOTE_THUMBNAIL_SIZE = 36.dp

/** Menší než zaoblení kontejneru citace, ve kterém náhled leží. */
private val QUOTE_THUMBNAIL_CORNER = 6.dp

/** Ikona citace před jménem — o kus menší než písmo jména, aby jméno zůstalo hlavní. */
private val QUOTE_ICON_SIZE = 16.dp

/** Zaoblení kontejneru citace. Menší než bublina — je v ní vnořený. */
internal val QUOTE_CORNER = 8.dp

/** Jak moc je kontejner citace tónovaný akcentem. Dost na odlišení, ne na soupeření s textem. */
internal const val QUOTE_CONTAINER_ALPHA = 0.14f
