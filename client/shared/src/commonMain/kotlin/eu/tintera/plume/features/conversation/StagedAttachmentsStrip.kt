package eu.tintera.plume.features.conversation

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.description_24dp
import org.jetbrains.compose.resources.painterResource

/**
 * Přílohy čekající na odeslání, nahoře v kartě vstupního pole.
 *
 * Fotky jsou v M3 **multi-browse carouselu**: u víc fotek je vidět jedna velká a okraje dalších,
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

    if (attachments.first() is OutgoingAttachment.Photo) {
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
            AsyncImage(
                // Coil potřebuje schéma, holá cesta by se vyhodnotila jako relativní URI.
                model = "file://${attachment.path}",
                contentDescription = "Fotka ${index + 1} z ${attachments.size}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
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

private val PHOTO_ITEM_WIDTH = 120.dp
private val PHOTO_ITEM_HEIGHT = 120.dp
