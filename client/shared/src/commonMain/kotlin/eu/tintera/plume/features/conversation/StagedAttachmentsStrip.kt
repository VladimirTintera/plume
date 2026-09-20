package eu.tintera.plume.features.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.SelfDestructType
import eu.tintera.plume.core.ui.attachmentKey
import eu.tintera.plume.core.ui.sharedFlight
import eu.tintera.plume.features.conversation.items.SpoilerOverlay
import eu.tintera.plume.features.conversation.items.rememberSpoilerBlurStyle
import eu.tintera.plume.features.conversation.items.spoilerBlur
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.attachment_remove_file
import eu.tintera.plume.resources.attachment_remove_photo
import eu.tintera.plume.resources.attachment_spoiler
import eu.tintera.plume.resources.attachment_timer_off
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.conversation_attachment_position
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.media_self_destruct_seconds
import eu.tintera.plume.resources.media_video
import eu.tintera.plume.resources.media_view_once
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.schedule_24dp
import eu.tintera.plume.resources.visibility_off_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Přílohy čekající na odeslání, nahoře v kartě vstupního pole.
 *
 * U jedné fotky/videa se zobrazí miniatura s možností otevření do fullscreenu a vedle ní
 * ovládací prvky (přepínač spoileru a volič samozničujícího časovače).
 * U víc fotek jsou v M3 **multi-browse carouselu**. Soubory jsou řádky s názvem.
 *
 * Indexy v [onRemove] jsou indexy v [attachments].
 */
@Composable
fun StagedAttachmentsStrip(
    attachments: List<OutgoingAttachment>,
    onRemove: (Int) -> Unit,
    onOpen: (Int) -> Unit = {},
    onToggleSpoiler: (Int) -> Unit = {},
    onSetSelfDestruct: (Int, SelfDestructType?) -> Unit = { _, _ -> },
    previewAttachmentIndex: Int? = null,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return

    if (attachments.first() !is OutgoingAttachment.Document) {
        if (attachments.size == 1) {
            SinglePhotoPreview(
                attachment = attachments.first(),
                onRemove = { onRemove(0) },
                onOpen = { onOpen(0) },
                onToggleSpoiler = { onToggleSpoiler(0) },
                onSetSelfDestruct = { onSetSelfDestruct(0, it) },
                isPreviewOpen = previewAttachmentIndex == 0,
                modifier = modifier,
            )
        } else {
            PhotoCarousel(
                attachments = attachments,
                onRemove = onRemove,
                onOpen = onOpen,
                onToggleSpoiler = onToggleSpoiler,
                previewingIndex = previewAttachmentIndex,
                modifier = modifier,
            )
        }
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
                        Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.attachment_remove_file))
                    }
                }
            }
        }
    }
}

/**
 * Samostatná fotka/video nad vstupním polem s ovládacími prvky napravo od miniatury.
 */
@Composable
private fun SinglePhotoPreview(
    attachment: OutgoingAttachment,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
    onToggleSpoiler: () -> Unit,
    onSetSelfDestruct: (SelfDestructType?) -> Unit,
    isPreviewOpen: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val hasSpoiler = when (attachment) {
        is OutgoingAttachment.Photo -> attachment.hasSpoiler
        is OutgoingAttachment.Video -> attachment.hasSpoiler
        is OutgoingAttachment.Document -> false
    }
    val selfDestructType = when (attachment) {
        is OutgoingAttachment.Photo -> attachment.selfDestructType
        is OutgoingAttachment.Video -> attachment.selfDestructType
        is OutgoingAttachment.Document -> null
    }
    val image = when (attachment) {
        is OutgoingAttachment.Video -> attachment.thumbnail?.path
        else -> attachment.path
    }
    val spoilerStyle = rememberSpoilerBlurStyle()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Miniatura
        Box(
            modifier = Modifier
                .size(PHOTO_ITEM_WIDTH, PHOTO_ITEM_HEIGHT)
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onOpen),
        ) {
            if (image != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .sharedFlight(attachmentKey(attachment.path), visible = !isPreviewOpen),
                ) {
                    AsyncImage(
                        model = "file://$image",
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .spoilerBlur(hasSpoiler = hasSpoiler, revealed = false, style = spoilerStyle),
                    )

                    if (hasSpoiler) {
                        SpoilerOverlay(visible = true)
                    }
                }
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest))
            }

            if (selfDestructType != null) {
                TimerBadge(
                    selfDestructType = selfDestructType,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                )
            } else if (attachment is OutgoingAttachment.Video) {
                VideoBadge(
                    durationSeconds = attachment.durationSeconds,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                )
            }

            FilledTonalIconButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp),
            ) {
                Icon(
                    painterResource(Res.drawable.close_24dp),
                    contentDescription = stringResource(Res.string.attachment_remove_photo),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Ovládací prvky napravo
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Spoiler chip
            FilterChip(
                selected = hasSpoiler,
                onClick = onToggleSpoiler,
                leadingIcon = {
                    Icon(
                        painter = painterResource(Res.drawable.visibility_off_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                label = { Text(stringResource(Res.string.attachment_spoiler)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )

            // Self-destruct timer chip
            var menuExpanded by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = selfDestructType != null,
                    onClick = { menuExpanded = true },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.schedule_24dp),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                    label = {
                        Text(
                            when (selfDestructType) {
                                is SelfDestructType.Timer -> stringResource(
                                    Res.string.media_self_destruct_seconds,
                                    selfDestructType.seconds,
                                )
                                is SelfDestructType.Immediately -> stringResource(Res.string.media_view_once)
                                null -> stringResource(Res.string.attachment_timer_off)
                            },
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.attachment_timer_off)) },
                        onClick = {
                            onSetSelfDestruct(null)
                            menuExpanded = false
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.media_view_once)) },
                        onClick = {
                            onSetSelfDestruct(SelfDestructType.Immediately)
                            menuExpanded = false
                        },
                    )
                    listOf(5, 10, 15, 30).forEach { seconds ->
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.media_self_destruct_seconds, seconds)) },
                            onClick = {
                                onSetSelfDestruct(SelfDestructType.Timer(seconds))
                                menuExpanded = false
                            },
                        )
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
    onOpen: (Int) -> Unit,
    onToggleSpoiler: (Int) -> Unit,
    previewingIndex: Int? = null,
    modifier: Modifier,
) {
    val carouselState = rememberCarouselState { attachments.size }
    val spoilerStyle = rememberSpoilerBlurStyle()

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
        val hasSpoiler = when (attachment) {
            is OutgoingAttachment.Photo -> attachment.hasSpoiler
            is OutgoingAttachment.Video -> attachment.hasSpoiler
            is OutgoingAttachment.Document -> false
        }
        Box(
            Modifier
                .fillMaxSize()
                .maskClip(RoundedCornerShape(16.dp))
                .clickable { onOpen(index) },
        ) {
            // Video Coil neotevře; kreslí se jeho náhled, připravený při výběru.
            val image = when (attachment) {
                is OutgoingAttachment.Video -> attachment.thumbnail?.path
                else -> attachment.path
            }
            if (image != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .sharedFlight(attachmentKey(attachment.path), visible = previewingIndex != index),
                ) {
                    AsyncImage(
                        // Coil potřebuje schéma, holá cesta by se vyhodnotila jako relativní URI.
                        model = "file://$image",
                        contentDescription = stringResource(Res.string.conversation_attachment_position, index + 1, attachments.size),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .spoilerBlur(hasSpoiler = hasSpoiler, revealed = false, style = spoilerStyle),
                    )

                    if (hasSpoiler) {
                        SpoilerOverlay(visible = true)
                    }
                }
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest))
            }

            val selfDestructType = when (attachment) {
                is OutgoingAttachment.Photo -> attachment.selfDestructType
                is OutgoingAttachment.Video -> attachment.selfDestructType
                is OutgoingAttachment.Document -> null
            }

            if (selfDestructType != null) {
                TimerBadge(
                    selfDestructType = selfDestructType,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                )
            } else if (attachment is OutgoingAttachment.Video) {
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
                    contentDescription = stringResource(Res.string.attachment_remove_photo),
                    modifier = Modifier.size(18.dp),
                )
            }

            IconButton(
                onClick = { onToggleSpoiler(index) },
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(32.dp),
            ) {
                Icon(
                    painterResource(Res.drawable.visibility_off_24dp),
                    contentDescription = stringResource(Res.string.attachment_spoiler),
                    tint = if (hasSpoiler) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Štítek samozničujícího média: ikona časovače a typ/doba.
 */
@Composable
private fun TimerBadge(selfDestructType: SelfDestructType, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            painterResource(Res.drawable.schedule_24dp),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = when (selfDestructType) {
                is SelfDestructType.Immediately -> stringResource(Res.string.media_view_once)
                is SelfDestructType.Timer -> "${selfDestructType.seconds}s"
            },
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
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
            contentDescription = stringResource(Res.string.media_video),
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
