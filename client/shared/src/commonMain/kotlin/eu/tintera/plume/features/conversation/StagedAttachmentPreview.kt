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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.SelfDestructType
import eu.tintera.plume.core.ui.attachmentKey
import eu.tintera.plume.core.ui.sharedFlight
import eu.tintera.plume.features.conversation.items.SpoilerOverlay
import eu.tintera.plume.features.conversation.items.rememberSpoilerBlurStyle
import eu.tintera.plume.features.conversation.items.spoilerBlur
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.attachment_done
import eu.tintera.plume.resources.attachment_preview_title
import eu.tintera.plume.resources.attachment_spoiler
import eu.tintera.plume.resources.attachment_timer_off
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.media_self_destruct_seconds
import eu.tintera.plume.resources.media_view_once
import eu.tintera.plume.resources.schedule_24dp
import eu.tintera.plume.resources.visibility_off_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Zvětšený fullscreen náhled připravené přílohy před odesláním.
 *
 * Umožňuje prohlédnout detail snímku (včetně zoomu a posunu), zkontrolovat nebo přepnout
 * spoiler, nastavit samozničující časovač (view-once / TTL) a případně snímek zahodit.
 */
@Composable
internal fun StagedAttachmentPreview(
    attachment: OutgoingAttachment,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onToggleSpoiler: () -> Unit,
    onSetSelfDestruct: (SelfDestructType?) -> Unit,
    isOpen: Boolean = true,
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
                // Přepínač Spoileru
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

                // Volič časovače
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

                Spacer(Modifier.weight(1f))

                TextButton(onClick = onDismiss) {
                    Text(
                        text = stringResource(Res.string.attachment_done),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}
