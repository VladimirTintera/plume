package eu.tintera.plume.features.chats

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.em
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatPreviewThumbnail
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.ui.BlurredThumbnail
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.isSavedMessages
import eu.tintera.plume.features.conversation.items.Delivery
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.sender_you
import org.jetbrains.compose.resources.stringResource

/**
 * Kdo poslední zprávu napsal, jak to náhled v seznamu uvádí před textem („Jana: …", „Vy: …"),
 * nebo `null`. Stejně jako nalezené zprávy v hledání a poslední zpráva tématu.
 *
 * - **Ve skupině jméno**, protože píše víc lidí; v soukromém chatu a kanálu odesílatele říká
 *   název chatu.
 * - **U vlastní zprávy „Vy" všude kromě kanálu** — kanál píše jménem kanálu, ne uživatele.
 *   A kromě Uložených zpráv: tam je vlastní každá zpráva a „Vy:" by stálo u všech.
 * - **Oznámení se jménem ve větě předponu nedostane** („Jana se přidala") — byla by tam dvakrát.
 */
@Composable
internal fun ChatSummary.previewSender(): String? = when {
    !lastMessageShowsSender -> null
    lastMessageOutgoing -> stringResource(Res.string.sender_you).takeIf { kind != ChatKind.Channel && !isSavedMessages(id) }
    else -> lastMessageSender
}

/**
 * Stav doručení vlastní poslední zprávy, jak ho kreslí pod bublinou konverzace, nebo `null`.
 *
 * Uložené zprávy ho nemají (nikdo jiný je nečte) a kanál jen hodiny a vykřičník: „přečteno"
 * tam nic neříká, protože čtenářů je víc a každý jindy.
 */
@Composable
internal fun ChatSummary.previewDelivery(): Delivery? {
    if (!lastMessageOutgoing || isSavedMessages(id)) return null
    return when (lastMessageSendState) {
        SendState.Sending -> Delivery.Sending
        SendState.Failed -> Delivery.Failed
        SendState.Sent -> when {
            kind == ChatKind.Channel -> null
            lastMessageRead -> Delivery.Read
            else -> Delivery.Sent
        }
        null -> null
    }
}

/** Klíč inline obsahu s obrázkem v náhledu; v jednom řádku je nejvýš jeden. */
internal const val PREVIEW_THUMBNAIL_KEY = "chat-preview-thumbnail"

/**
 * Obrázek v náhledu jako inline obsah textu, ne prvek vedle něj: stojí ZA jménem odesílatele
 * („Jana: ▣ Na chalupě") a s textem se zkracuje, jak se řádek zúží. Prvek v `Row` by jméno
 * odtrhl od textu, nebo by se jméno muselo kreslit zvlášť a zkracovat samo.
 */
internal fun previewThumbnailInlineContent(thumbnail: ChatPreviewThumbnail?): Map<String, InlineTextContent> =
    if (thumbnail == null) {
        emptyMap()
    } else {
        mapOf(
            PREVIEW_THUMBNAIL_KEY to InlineTextContent(
                Placeholder(
                    width = PREVIEW_THUMBNAIL_SIZE.em,
                    height = PREVIEW_THUMBNAIL_SIZE.em,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
                ),
            ) { PreviewThumbnailImage(thumbnail) },
        )
    }

/**
 * Skrytý obrázek (spoiler, citlivý obsah) jen rozmazaný a nic se nestahuje — seznam je vidět
 * bez otevření chatu. Stejné pravidlo jako citace v konverzaci (`QuoteThumbnailImage`).
 */
@Composable
private fun PreviewThumbnailImage(thumbnail: ChatPreviewThumbnail) {
    val clipped = Modifier.fillMaxSize().clip(if (thumbnail.round) CircleShape else MaterialTheme.shapes.extraSmall)
    val fileId = thumbnail.fileId
    when {
        thumbnail.hidden || fileId == null -> thumbnail.placeholder?.let { BlurredThumbnail(it, clipped) }
        else -> RemoteImage(
            fileId = fileId,
            modifier = clipped,
            contentScale = ContentScale.Crop,
            background = MaterialTheme.colorScheme.surfaceContainerHighest,
            placeholder = thumbnail.placeholder,
        )
    }
}

/** Obrázek je o kus větší než písmo, jako vlastní emoji, aby na něm bylo něco vidět. */
private const val PREVIEW_THUMBNAIL_SIZE = 1.3f
