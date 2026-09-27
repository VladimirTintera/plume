package eu.tintera.plume.features.conversation.items

import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.LinkPreview
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.features.conversation.MAX_PHOTO_HEIGHT

/**
 * Náhled odkazu UVNITŘ bubliny — jen u zprávy s citací nebo přílohou; jinak má náhled vlastní
 * kartu ([LinkPreviewMessageItem]).
 *
 * Tónovaný kontejner v barvě akcentu, stejně jako citace: vložený cizí obsah se od vlastního
 * textu liší plochou, ne proužkem (dřív proužek vlevo, jazyk M2). Akcent se podává zvenku —
 * v odchozí `primary` bublině je to barva popředí, `primary` by tam zmizela.
 *
 * Ťuknutí otevře odkaz stejnou cestou jako odkaz v textu (přes ViewModel). Dlouhý stisk se musí
 * zopakovat, jinak by ho náhled k bublině nepustil — stejná past jako u fotky a citace.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun LinkPreviewCard(
    preview: LinkPreview,
    accent: Color,
    onOpen: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    messageId: Long = 0L,
) {
    val shape = RoundedCornerShape(QUOTE_CORNER)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accent.copy(alpha = QUOTE_CONTAINER_ALPHA))
            .combinedClickable(onClick = onOpen, onLongClick = onLongClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                preview.siteName?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                preview.title?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                preview.description?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = LINK_PREVIEW_DESCRIPTION_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Malý obrázek vedle textu, jen když odesílatel nechtěl velký.
            if (!preview.largePhoto) {
                preview.photo?.let { photo ->
                    LinkPreviewImage(
                        photo = photo,
                        messageId = messageId,
                        label = false,
                        onLongClick = onLongClick,
                        modifier = Modifier
                            .size(LINK_PREVIEW_THUMBNAIL)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }
            }
        }

        if (preview.largePhoto) {
            preview.photo?.let { photo ->
                // Výška ručně, ne `aspectRatio` — ten by strop z `heightIn` neuplatnil (viz fotka).
                BoxWithConstraints {
                    val ratio = if (photo.width > 0 && photo.height > 0) {
                        photo.width.toFloat() / photo.height.toFloat()
                    } else {
                        LINK_PREVIEW_FALLBACK_RATIO
                    }
                    LinkPreviewImage(
                        photo = photo,
                        messageId = messageId,
                        onLongClick = onLongClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((maxWidth / ratio).coerceAtMost(MAX_PHOTO_HEIGHT))
                            .clip(RoundedCornerShape(8.dp)),
                    )
                }
            }
        }
    }
}

/** Popis náhledu odkazu. Víc řádků by z bubliny udělalo článek. */
internal const val LINK_PREVIEW_DESCRIPTION_LINES = 3

/** Malý obrázek vedle textu náhledu. */
internal val LINK_PREVIEW_THUMBNAIL = 56.dp

/** Poměr stran velkého obrázku náhledu bez rozměrů — typický obrázek pro sdílení odkazu. */
private const val LINK_PREVIEW_FALLBACK_RATIO = 1.91f

/**
 * Obrázek náhledu odkazu. U citlivé zprávy rozostřený a odkrytý až ťuknutím, jako fotka se
 * spoilerem ([SensitiveBox]) — jinak by náhled ukázal ostře, co přímo poslaná fotka skrývá.
 * [modifier] nese rozměr a ořez, klikání na odkaz nechává na volajícím.
 */
@Composable
internal fun LinkPreviewImage(
    photo: MessageMedia.Photo,
    messageId: Long,
    modifier: Modifier = Modifier,
    label: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    imageModifier: Modifier = Modifier,
) {
    SensitiveBox(
        messageId = messageId,
        hidden = photo.isSensitive,
        label = label,
        onLongClick = onLongClick,
        modifier = modifier,
    ) { blur ->
        RemoteImage(
            fileId = photo.fileId,
            thumbnailFileId = photo.thumbnailFileId,
            placeholder = photo.minithumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().then(blur).then(imageModifier),
        )
    }
}
