package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.features.conversation.MAX_PHOTO_HEIGHT

/**
 * Náhled odkazu pod textem.
 *
 * Tvarem vychází z citace — proužek vlevo v barvě akcentu — protože jde o totéž: vložený cizí
 * obsah, který se musí na první pohled lišit od vlastního textu bubliny. Vlastní pozadí nemá;
 * druhá plocha v bublině by v odchozí `primary` vypadala jako nálepka.
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
) {
    // Proužek se kreslí za obsahem, ne jako `Box` s `fillMaxHeight` v řádku s
    // `height(IntrinsicSize.Min)`. Velký obrázek níž sedí v `BoxWithConstraints` (SubcomposeLayout)
    // a ten intrinsické míry neumí — aplikace spadla při zobrazení každého takového náhledu.
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onLongClick)
            .drawBehind {
                drawRoundRect(
                    color = accent,
                    size = Size(LINK_PREVIEW_STRIPE.toPx(), size.height),
                    cornerRadius = CornerRadius(2.dp.toPx()),
                )
            }
            .padding(start = LINK_PREVIEW_STRIPE + 8.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
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
                        RemoteImage(
                            fileId = photo.fileId,
                            thumbnailFileId = photo.thumbnailFileId,
                            contentDescription = null,
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
                    BoxWithConstraints(Modifier.padding(top = 4.dp)) {
                        val ratio = if (photo.width > 0 && photo.height > 0) {
                            photo.width.toFloat() / photo.height.toFloat()
                        } else {
                            LINK_PREVIEW_FALLBACK_RATIO
                        }
                        RemoteImage(
                            fileId = photo.fileId,
                            thumbnailFileId = photo.thumbnailFileId,
                            contentDescription = null,
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
}

/** Popis náhledu odkazu. Víc řádků by z bubliny udělalo článek. */
internal const val LINK_PREVIEW_DESCRIPTION_LINES = 3

/** Malý obrázek vedle textu náhledu. */
private val LINK_PREVIEW_THUMBNAIL = 56.dp

/** Šířka proužku vlevo od náhledu odkazu — stejná jako u citace. */
private val LINK_PREVIEW_STRIPE = 3.dp

/** Poměr stran velkého obrázku náhledu bez rozměrů — typický obrázek pro sdílení odkazu. */
private const val LINK_PREVIEW_FALLBACK_RATIO = 1.91f
