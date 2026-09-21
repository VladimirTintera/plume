package eu.tintera.plume.features.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.tintera.plume.core.telegram.AttachmentMode
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.ui.attachmentKey
import eu.tintera.plume.core.ui.sharedFlight
import eu.tintera.plume.features.conversation.items.SpoilerOverlay
import eu.tintera.plume.features.conversation.items.rememberSpoilerBlurStyle
import eu.tintera.plume.features.conversation.items.spoilerBlur
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.attachment_done
import eu.tintera.plume.resources.attachment_preview_title
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.keyboard_arrow_down_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Zvětšený fullscreen náhled připravené přílohy před odesláním.
 *
 * Umožňuje prohlédnout detail snímku (včetně zoomu a posunu), zkontrolovat nebo vybrat
 * režim odeslání v Material 3 Expressive dialogu a případně snímek zahodit.
 */
@Composable
internal fun StagedAttachmentPreview(
    attachment: OutgoingAttachment,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSetMode: (AttachmentMode) -> Unit,
    selfDestructAllowed: Boolean,
    isOpen: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val mode = attachment.mode
    val isVideo = attachment is OutgoingAttachment.Video
    val hasSpoiler = attachment.hasSpoiler

    val imagePath = when (attachment) {
        is OutgoingAttachment.Video -> attachment.thumbnail?.path ?: attachment.path
        else -> attachment.path
    }

    val aspectRatio = when (attachment) {
        is OutgoingAttachment.Photo -> {
            if (attachment.width > 0 && attachment.height > 0) {
                attachment.width.toFloat() / attachment.height.toFloat()
            } else null
        }
        is OutgoingAttachment.Video -> {
            if (attachment.width > 0 && attachment.height > 0) {
                attachment.width.toFloat() / attachment.height.toFloat()
            } else null
        }
        is OutgoingAttachment.Document -> null
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val spoilerStyle = rememberSpoilerBlurStyle()
    var showModeSheet by remember { mutableStateOf(false) }

    if (showModeSheet) {
        AttachmentModeSheet(
            currentMode = mode,
            isVideo = isVideo,
            selfDestructAllowed = selfDestructAllowed,
            onSelectMode = onSetMode,
            onDismiss = { showModeSheet = false },
        )
    }

    LaunchedEffect(isOpen) {
        if (!isOpen) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Zvětšovací obrázek / náhled
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 4f)
                        if (scale == 1f) {
                            offset = Offset.Zero
                        } else {
                            offset += pan
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .then(
                        if (aspectRatio != null) {
                            Modifier
                                .fillMaxSize()
                                .wrapContentSize(Alignment.Center)
                                .aspectRatio(aspectRatio, matchHeightConstraintsFirst = aspectRatio < 1f)
                        } else {
                            Modifier.fillMaxSize()
                        }
                    )
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
                    .sharedFlight(attachmentKey(attachment.path), visible = isOpen),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = "file://$imagePath",
                    contentDescription = stringResource(Res.string.attachment_preview_title),
                    contentScale = if (aspectRatio != null) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .spoilerBlur(hasSpoiler = hasSpoiler, revealed = false, style = spoilerStyle),
                )

                if (hasSpoiler && scale <= 1f) {
                    SpoilerOverlay(visible = true)
                }
            }
        }

        // Horní lišta
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(Res.drawable.arrow_back_24dp),
                    contentDescription = null,
                    tint = Color.White,
                )
            }

            Text(
                text = stringResource(Res.string.attachment_preview_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(start = 8.dp),
            )

            Spacer(Modifier.weight(1f))

            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(Res.drawable.delete_24dp),
                    contentDescription = null,
                    tint = Color.White,
                )
            }
        }

        // Dolní lišta s ovládacími prvky
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.75f),
        ) {
            Row(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Tonal pill button pro výběr režimu odeslání
                val isCustomMode = mode !is AttachmentMode.Standard
                Surface(
                    onClick = { showModeSheet = true },
                    shape = RoundedCornerShape(20.dp),
                    color = if (isCustomMode) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                            else MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            painter = painterResource(getAttachmentModeIcon(mode, isVideo)),
                            contentDescription = null,
                            tint = if (isCustomMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = getAttachmentModeLabel(mode, isVideo),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isCustomMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                        )
                        Icon(
                            painter = painterResource(Res.drawable.keyboard_arrow_down_24dp),
                            contentDescription = null,
                            tint = if (isCustomMode) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(Res.string.attachment_done),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
