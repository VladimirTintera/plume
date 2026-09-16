package eu.tintera.plume.features.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.play_arrow_24dp
import org.jetbrains.compose.resources.painterResource

/**
 * Přílohy čekající na odeslání, nahoře v kartě vstupního pole.
 *
 * Fotky a videa jsou v M3 **multi-browse carouselu**: u víc fotek je vidět jedna velká a okraje dalších,
 * takže je jasné, že se dá listovat, a karta přitom nezabere půl obrazovky. Soubory náhled nemají,
 * takže jsou to řádky s názvem. Druhy se nemíchají (viz [stageAttachments]), takže je tu vždycky
 * jen jedno z toho.
 *
 * Indexy v [onRemove] jsou indexy v [attachments].
 */
@Composable
fun StagedAttachmentsStrip(
    attachments: List<OutgoingAttachment>,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return

    if (attachments.first() !is OutgoingAttachment.Document) {
        PhotoCarousel(attachments, onRemove, modifier)
    } else {
        Column(modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp)) {
            attachments.forEachIndexed { index, attachment ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(Res.drawable.description_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = attachment.path.substringAfterLast('/'),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    IconButton(onClick = { onRemove(index) }) {
                        Icon(painterResource(Res.drawable.close_24dp), contentDescription = "Odebrat soubor")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoCarousel(
    attachments: List<OutgoingAttachment>,
    onRemove: (Int) -> Unit,
    modifier: Modifier,
) {
    val carouselState = rememberCarouselState { attachments.size }

    HorizontalMultiBrowseCarousel(
        state = carouselState,
        preferredItemWidth = PHOTO_ITEM_WIDTH,
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .height(PHOTO_ITEM_HEIGHT),
    ) { index ->
        val attachment = attachments.getOrNull(index) ?: return@HorizontalMultiBrowseCarousel
        Box(Modifier.fillMaxSize().maskClip(RoundedCornerShape(16.dp))) {
            // Video Coil neotevře; kreslí se jeho náhled, připravený při výběru.
            val image = when (attachment) {
                is OutgoingAttachment.Video -> attachment.thumbnail?.path
                else -> attachment.path
            }
            if (image != null) {
                AsyncImage(
                    // Coil potřebuje schéma, holá cesta by se vyhodnotila jako relativní URI.
                    model = "file://$image",
                    contentDescription = "Příloha ${index + 1} z ${attachments.size}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest))
            }
            if (attachment is OutgoingAttachment.Video) {
                VideoBadge(
                    durationSeconds = attachment.durationSeconds,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                )
            }
            FilledTonalIconButton(
                onClick = { onRemove(index) },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp),
            ) {
                Icon(
                    painterResource(Res.drawable.close_24dp),
                    contentDescription = "Odebrat fotku",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Štítek videa: ikona přehrávání a délka. Závoj `scrim` a bílá, aby byl čitelný přes jakýkoli
 * snímek — stejně jako průběh nahrávání v bublině.
 */
@Composable
private fun VideoBadge(durationSeconds: Int, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            .padding(start = 2.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(Res.drawable.play_arrow_24dp),
            contentDescription = "Video",
            tint = Color.White,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = formatDuration(durationSeconds),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

/** „0:42", „12:05", „1:02:03". */
internal fun formatDuration(seconds: Int): String {
    val total = seconds.coerceAtLeast(0)
    val hours = total / 3600
    val minutes = total / 60 % 60
    val rest = (total % 60).toString().padStart(2, '0')
    return if (hours > 0) "$hours:${minutes.toString().padStart(2, '0')}:$rest" else "$minutes:$rest"
}

private val PHOTO_ITEM_WIDTH = 120.dp
private val PHOTO_ITEM_HEIGHT = 120.dp
