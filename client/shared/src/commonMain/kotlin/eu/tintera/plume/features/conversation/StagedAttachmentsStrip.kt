package eu.tintera.plume.features.conversation

import eu.tintera.plume.resources.music_note_24dp
import androidx.compose.material3.ShapeDefaults
import eu.tintera.plume.core.telegram.sendableTogether
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import eu.tintera.plume.core.telegram.AttachmentMode
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.ui.attachmentKey
import eu.tintera.plume.core.ui.localImage
import eu.tintera.plume.core.ui.sharedFlight
import eu.tintera.plume.features.conversation.items.SpoilerOverlay
import eu.tintera.plume.features.conversation.items.rememberSpoilerBlurStyle
import eu.tintera.plume.features.conversation.items.spoilerBlur
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.attachment_mode_title
import eu.tintera.plume.resources.attachment_remove_file
import eu.tintera.plume.resources.attachment_remove_photo
import eu.tintera.plume.resources.attachments_albums
import eu.tintera.plume.resources.attachments_group
import eu.tintera.plume.resources.attachments_separate_messages
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.content_file
import eu.tintera.plume.resources.conversation_attachment_position
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.media_video
import eu.tintera.plume.resources.media_view_once
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.schedule_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import okio.Path.Companion.toPath

/**
 * Přílohy čekající na odeslání, nahoře v kartě vstupního pole.
 *
 * U jedné fotky/videa se zobrazí miniatura s možností otevření do fullscreenu a vedle ní
 * Material 3 Expressive karta pro volbu režimu odeslání (standard, spoiler, view-once, časovač, soubor).
 * U víc fotek jsou v M3 **multi-browse carouselu**, kde každá položka má tlačítko pro nastavení svého režimu.
 * Soubory jsou řádky s názvem.
 *
 * Indexy v [onRemove] a [onSetMode] jsou indexy v [attachments].
 */
@Composable
fun StagedAttachmentsStrip(
    attachments: List<OutgoingAttachment>,
    onRemove: (Int) -> Unit,
    onOpen: (Int) -> Unit = {},
    onSetMode: (Int, AttachmentMode) -> Unit = { _, _ -> },
    /** Chat mizení dovoluje; v albu (carousel) se stejně nenabízí. */
    selfDestructAllowed: Boolean = false,
    previewAttachmentIndex: Int? = null,
    /** Odejdou každá zvlášť, ne v albu; [onToggleSeparately] `null` volbu nenabízí. */
    separately: Boolean = false,
    onToggleSeparately: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return

    Column(modifier) {
        StagedAttachmentsItems(attachments, onRemove, onOpen, onSetMode, selfDestructAllowed, previewAttachmentIndex)
        if (attachments.size > 1 && onToggleSeparately != null) {
            AlbumGrouping(attachments, separately, onToggleSeparately)
        }
    }
}

/**
 * **Seskupit do alba**, nebo poslat každou přílohu zvlášť. Věta vedle říká, co z výběru
 * doopravdy odejde, když to není jedno album: víc než deset položek se rozdělí do několika
 * ([attachmentBatches]) a bez seskupení je každá vlastní zprávou.
 */
@Composable
private fun AlbumGrouping(attachments: List<OutgoingAttachment>, separately: Boolean, onToggle: () -> Unit) {
    val batches = attachmentBatches(attachments, separately).size
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp),
    ) {
        FilterChip(
            selected = !separately,
            onClick = onToggle,
            label = { Text(stringResource(Res.string.attachments_group)) },
            leadingIcon = if (!separately) {
                { Icon(painterResource(Res.drawable.check_24dp), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
            } else {
                null
            },
        )
        if (separately || batches > 1) {
            Text(
                pluralStringResource(
                    if (separately) Res.plurals.attachments_separate_messages else Res.plurals.attachments_albums,
                    batches,
                    batches,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StagedAttachmentsItems(
    attachments: List<OutgoingAttachment>,
    onRemove: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    onSetMode: (Int, AttachmentMode) -> Unit,
    selfDestructAllowed: Boolean,
    previewAttachmentIndex: Int?,
    modifier: Modifier = Modifier,
) {
    if (attachments.first().let { it is OutgoingAttachment.Photo || it is OutgoingAttachment.Video }) {
        if (attachments.size == 1) {
            SinglePhotoPreview(
                attachment = attachments.first(),
                onRemove = { onRemove(0) },
                onOpen = { onOpen(0) },
                onSetMode = { onSetMode(0, it) },
                selfDestructAllowed = selfDestructAllowed,
                isPreviewOpen = previewAttachmentIndex == 0,
                modifier = modifier,
            )
        } else {
            PhotoCarousel(
                attachments = attachments,
                onRemove = onRemove,
                onOpen = onOpen,
                onSetMode = onSetMode,
                previewingIndex = previewAttachmentIndex,
                modifier = modifier,
            )
        }
    } else {
        Column(modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp)) {
            // Jak doopravdy odejdou: skladba mezi soubory jde jako soubor, a řádek to má říct.
            sendableTogether(attachments).forEachIndexed { index, attachment ->
                val audio = attachment as? OutgoingAttachment.Audio
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(if (audio != null) Res.drawable.music_note_24dp else Res.drawable.description_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        // Skladba se představí jako v přehrávači; bez značek aspoň jménem souboru.
                        text = audio?.title?.let { title -> listOfNotNull(title, audio.performer).joinToString(" – ") }
                            ?: attachment.path.toPath().name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                        modifier = Modifier.weight(1f).padding(start = 12.dp),
                    )
                    IconButton(onClick = { onRemove(index) }, shapes = IconButtonDefaults.shapes()) {
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
    onSetMode: (AttachmentMode) -> Unit,
    selfDestructAllowed: Boolean,
    isPreviewOpen: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val isVideo = attachment is OutgoingAttachment.Video
    val mode = attachment.mode
    val hasSpoiler = attachment.hasSpoiler
    val image = when (attachment) {
        is OutgoingAttachment.Video -> attachment.thumbnail?.path
        else -> attachment.path
    }
    val spoilerStyle = rememberSpoilerBlurStyle()
    var showSheet by remember { mutableStateOf(false) }

    if (showSheet) {
        AttachmentModeSheet(
            currentMode = mode,
            isVideo = isVideo,
            selfDestructAllowed = selfDestructAllowed,
            onSelectMode = onSetMode,
            onDismiss = { showSheet = false },
        )
    }

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
                .clip(ShapeDefaults.Large)
                .clickable(onClick = onOpen),
        ) {
            if (image != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(ShapeDefaults.Large)
                        .sharedFlight(attachmentKey(attachment.path), visible = !isPreviewOpen),
                ) {
                    AsyncImage(
                        model = localImage(image),
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

            // Štítky na miniatuře
            when (mode) {
                is AttachmentMode.Timer -> {
                    TimerBadge(
                        label = "${mode.seconds}s",
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    )
                }
                is AttachmentMode.ViewOnce -> {
                    TimerBadge(
                        label = stringResource(Res.string.media_view_once),
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    )
                }
                is AttachmentMode.AsFile -> {
                    FileBadge(
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    )
                }
                is AttachmentMode.Standard, is AttachmentMode.Spoiler -> {
                    if (attachment is OutgoingAttachment.Video) {
                        VideoBadge(
                            durationSeconds = attachment.durationSeconds,
                            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                        )
                    }
                }
            }

            FilledTonalIconButton(
                onClick = onRemove,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp),
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    painterResource(Res.drawable.close_24dp),
                    contentDescription = stringResource(Res.string.attachment_remove_photo),
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Ovládací prvek napravo: kombinace FilledTonal (výchozí) a Filled (aktivní režim)
        val isCustomMode = mode !is AttachmentMode.Standard
        if (isCustomMode) {
            FilledIconButton(
                onClick = { showSheet = true },
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(48.dp),
                shape = ShapeDefaults.Large,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    painter = painterResource(getAttachmentModeIcon(mode, isVideo)),
                    contentDescription = stringResource(Res.string.attachment_mode_title),
                    modifier = Modifier.size(24.dp),
                )
            }
        } else {
            FilledTonalIconButton(
                onClick = { showSheet = true },
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(48.dp),
                shape = ShapeDefaults.Large,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                Icon(
                    painter = painterResource(getAttachmentModeIcon(mode, isVideo)),
                    contentDescription = stringResource(Res.string.attachment_mode_title),
                    modifier = Modifier.size(24.dp),
                )
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
    onSetMode: (Int, AttachmentMode) -> Unit,
    previewingIndex: Int? = null,
    modifier: Modifier,
) {
    val carouselState = rememberCarouselState { attachments.size }
    val spoilerStyle = rememberSpoilerBlurStyle()
    var sheetAttachmentIndex by remember { mutableStateOf<Int?>(null) }

    val targetAttachment = sheetAttachmentIndex?.let { attachments.getOrNull(it) }
    if (targetAttachment != null && sheetAttachmentIndex != null) {
        val targetIndex = sheetAttachmentIndex!!
        AttachmentModeSheet(
            currentMode = targetAttachment.mode,
            isVideo = targetAttachment is OutgoingAttachment.Video,
            // Víc příloh je album a v albu mizení není (`selfDestructAllowed`).
            selfDestructAllowed = false,
            onSelectMode = { newMode ->
                onSetMode(targetIndex, newMode)
            },
            onDismiss = { sheetAttachmentIndex = null },
        )
    }

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
        val mode = attachment.mode
        val hasSpoiler = attachment.hasSpoiler
        val isVideo = attachment is OutgoingAttachment.Video

        Box(
            Modifier
                .fillMaxSize()
                .maskClip(ShapeDefaults.Large)
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
                        .clip(ShapeDefaults.Large)
                        .sharedFlight(attachmentKey(attachment.path), visible = previewingIndex != index),
                ) {
                    AsyncImage(
                        // Coil potřebuje schéma, holá cesta by se vyhodnotila jako relativní URI.
                        model = localImage(image),
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

            // Štítky na miniatuře v carouselu
            when (mode) {
                is AttachmentMode.Timer -> {
                    TimerBadge(
                        label = "${mode.seconds}s",
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    )
                }
                is AttachmentMode.ViewOnce -> {
                    TimerBadge(
                        label = stringResource(Res.string.media_view_once),
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    )
                }
                is AttachmentMode.AsFile -> {
                    FileBadge(
                        modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                    )
                }
                is AttachmentMode.Standard, is AttachmentMode.Spoiler -> {
                    if (attachment is OutgoingAttachment.Video) {
                        VideoBadge(
                            durationSeconds = attachment.durationSeconds,
                            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                        )
                    }
                }
            }

            FilledTonalIconButton(
                onClick = { onRemove(index) },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp),
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    painterResource(Res.drawable.close_24dp),
                    contentDescription = stringResource(Res.string.attachment_remove_photo),
                    modifier = Modifier.size(18.dp),
                )
            }

            val isCustomMode = mode !is AttachmentMode.Standard
            FilledTonalIconButton(
                onClick = { sheetAttachmentIndex = index },
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).size(32.dp),
                colors = if (isCustomMode) {
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                        contentColor = Color.White,
                    )
                },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(getAttachmentModeIcon(mode, isVideo)),
                    contentDescription = stringResource(Res.string.attachment_mode_title),
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
private fun TimerBadge(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(ShapeDefaults.Small)
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
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

/**
 * Štítek média odesílaného jako nekomprimovaný soubor.
 */
@Composable
private fun FileBadge(modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(ShapeDefaults.Small)
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            painterResource(Res.drawable.description_24dp),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = stringResource(Res.string.content_file),
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
            .clip(ShapeDefaults.Small)
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
