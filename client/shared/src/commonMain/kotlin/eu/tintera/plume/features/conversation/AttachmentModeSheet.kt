package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.SheetTitle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import eu.tintera.plume.core.telegram.AttachmentMode
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.attachment_mode_as_file
import eu.tintera.plume.resources.attachment_mode_as_file_desc
import eu.tintera.plume.resources.attachment_mode_section_format
import eu.tintera.plume.resources.attachment_mode_section_protection
import eu.tintera.plume.resources.attachment_mode_spoiler_desc
import eu.tintera.plume.resources.attachment_mode_standard_desc
import eu.tintera.plume.resources.attachment_mode_standard_photo
import eu.tintera.plume.resources.attachment_mode_standard_video
import eu.tintera.plume.resources.attachment_mode_timer_desc
import eu.tintera.plume.resources.attachment_mode_title
import eu.tintera.plume.resources.attachment_mode_view_once_desc
import eu.tintera.plume.resources.attachment_spoiler
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.image_24dp
import eu.tintera.plume.resources.media_self_destruct_seconds
import eu.tintera.plume.resources.media_view_once
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.schedule_24dp
import eu.tintera.plume.resources.visibility_off_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import eu.tintera.plume.core.ui.accentOn

/**
 * Vrátí ikonu odpovídající režimu přílohy.
 */
fun getAttachmentModeIcon(mode: AttachmentMode, isVideo: Boolean): DrawableResource = when (mode) {
    is AttachmentMode.Standard -> if (isVideo) Res.drawable.play_arrow_24dp else Res.drawable.image_24dp
    is AttachmentMode.Spoiler -> Res.drawable.visibility_off_24dp
    is AttachmentMode.ViewOnce -> Res.drawable.schedule_24dp
    is AttachmentMode.Timer -> Res.drawable.schedule_24dp
    is AttachmentMode.AsFile -> Res.drawable.description_24dp
}

/**
 * Vrátí textový popis aktuálního režimu přílohy.
 */
@Composable
fun getAttachmentModeLabel(mode: AttachmentMode, isVideo: Boolean): String = when (mode) {
    is AttachmentMode.Standard -> stringResource(if (isVideo) Res.string.attachment_mode_standard_video else Res.string.attachment_mode_standard_photo)
    is AttachmentMode.Spoiler -> stringResource(Res.string.attachment_spoiler)
    is AttachmentMode.ViewOnce -> stringResource(Res.string.media_view_once)
    is AttachmentMode.Timer -> stringResource(Res.string.media_self_destruct_seconds, mode.seconds)
    is AttachmentMode.AsFile -> stringResource(Res.string.attachment_mode_as_file)
}

/**
 * Spodní list pro výběr režimu přílohy (způsob odeslání) v Material 3 Expressive stylu.
 *
 * Sjednocuje volby:
 * - Běžné zobrazení (Standard)
 * - Spoiler
 * - Jednorázové zhlédnutí (View once)
 * - Časovač autodestrukce (5s, 10s, 15s, 30s)
 * - Odeslání jako soubor bez komprese (AsFile)
 *
 * [selfDestructAllowed] = `false` schová jednorázové zhlédnutí i časovač — mizet smí jen jediná
 * příloha v soukromém chatu (pravidlo `selfDestructAllowed` v `StagedAttachments.kt`). Nabídnout
 * je jinde by znamenalo chybu až po odeslání.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentModeSheet(
    currentMode: AttachmentMode,
    isVideo: Boolean,
    selfDestructAllowed: Boolean,
    onSelectMode: (AttachmentMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberExpandedSheetState()
    var selectedTimerSeconds by remember(currentMode) {
        mutableIntStateOf((currentMode as? AttachmentMode.Timer)?.seconds ?: 10)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            SheetTitle(stringResource(Res.string.attachment_mode_title), modifier = Modifier.padding(bottom = 16.dp))

            // Sekce: Zobrazení a ochrana
            Text(
                text = stringResource(Res.string.attachment_mode_section_protection),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                // 1. Standard
                ModeOptionRow(
                    title = stringResource(if (isVideo) Res.string.attachment_mode_standard_video else Res.string.attachment_mode_standard_photo),
                    description = stringResource(Res.string.attachment_mode_standard_desc),
                    icon = if (isVideo) Res.drawable.play_arrow_24dp else Res.drawable.image_24dp,
                    selected = currentMode is AttachmentMode.Standard,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 4.dp),
                    onClick = {
                        onSelectMode(AttachmentMode.Standard)
                        onDismiss()
                    },
                )

                // 2. Spoiler
                ModeOptionRow(
                    title = stringResource(Res.string.attachment_spoiler),
                    description = stringResource(Res.string.attachment_mode_spoiler_desc),
                    icon = Res.drawable.visibility_off_24dp,
                    selected = currentMode is AttachmentMode.Spoiler,
                    // Bez mizejících médií je spoiler poslední položkou skupiny.
                    shape = if (selfDestructAllowed) {
                        RoundedCornerShape(4.dp)
                    } else {
                        RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
                    },
                    onClick = {
                        onSelectMode(AttachmentMode.Spoiler)
                        onDismiss()
                    },
                )

                // 3. View Once
                if (selfDestructAllowed) ModeOptionRow(
                    title = stringResource(Res.string.media_view_once),
                    description = stringResource(Res.string.attachment_mode_view_once_desc),
                    icon = Res.drawable.schedule_24dp,
                    selected = currentMode is AttachmentMode.ViewOnce,
                    shape = RoundedCornerShape(4.dp),
                    onClick = {
                        onSelectMode(AttachmentMode.ViewOnce)
                        onDismiss()
                    },
                )

                // 4. Timer
                val timerMode = currentMode as? AttachmentMode.Timer
                val isTimerSelected = timerMode != null
                if (selfDestructAllowed) Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                        .background(
                            if (isTimerSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                ) {
                    ModeOptionRowContent(
                        title = stringResource(
                            Res.string.media_self_destruct_seconds,
                            timerMode?.seconds ?: selectedTimerSeconds,
                        ),
                        description = stringResource(Res.string.attachment_mode_timer_desc),
                        icon = Res.drawable.schedule_24dp,
                        selected = isTimerSelected,
                        onClick = {
                            onSelectMode(AttachmentMode.Timer(selectedTimerSeconds))
                        },
                    )

                    // Pokud je časovač aktivní, zobrazit výběr intervalu pomocí slideru
                    AnimatedVisibility(
                        visible = isTimerSelected,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(Res.string.media_self_destruct_seconds, 1),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                )
                                Text(
                                    text = stringResource(Res.string.media_self_destruct_seconds, selectedTimerSeconds),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = selectedAccent(),
                                )
                                Text(
                                    text = stringResource(Res.string.media_self_destruct_seconds, 30),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                )
                            }
                            Slider(
                                value = selectedTimerSeconds.toFloat().coerceIn(1f, 30f),
                                onValueChange = { newValue ->
                                    val sec = newValue.roundToInt().coerceIn(1, 30)
                                    selectedTimerSeconds = sec
                                    onSelectMode(AttachmentMode.Timer(sec))
                                },
                                valueRange = 1f..30f,
                                steps = 28,
                                colors = SliderDefaults.colors(
                                    thumbColor = selectedAccent(),
                                    activeTrackColor = selectedAccent(),
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Sekce: Kvalita a formát
            Text(
                text = stringResource(Res.string.attachment_mode_section_format),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )

            // 5. As File
            ModeOptionRow(
                title = stringResource(Res.string.attachment_mode_as_file),
                description = stringResource(Res.string.attachment_mode_as_file_desc),
                icon = Res.drawable.description_24dp,
                selected = currentMode is AttachmentMode.AsFile,
                shape = RoundedCornerShape(20.dp),
                onClick = {
                    onSelectMode(AttachmentMode.AsFile)
                    onDismiss()
                },
            )

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
internal fun ModeOptionRow(
    title: String,
    description: String,
    icon: DrawableResource,
    selected: Boolean,
    shape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        ModeOptionRowContent(
            title = title,
            description = description,
            icon = icon,
            selected = selected,
            onClick = onClick,
        )
    }
}

@Composable
private fun ModeOptionRowContent(
    title: String,
    description: String,
    icon: DrawableResource,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                // Celá adresa u výběru odkazu umí být dlouhá; dva řádky stačí k rozlišení.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (selected) {
            Spacer(Modifier.width(12.dp))
            Icon(
                painter = painterResource(Res.drawable.check_24dp),
                contentDescription = null,
                tint = selectedAccent(),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/**
 * Akcent na vybrané volbě, která leží v `primaryContainer`. V paletě se zvýšeným kontrastem
 * na ní `primary` splyne, pak ustoupí barvě popředí plochy (viz `accentOn`).
 */
@Composable
private fun selectedAccent(): Color = accentOn(
    background = MaterialTheme.colorScheme.primaryContainer,
    accent = MaterialTheme.colorScheme.primary,
    fallback = MaterialTheme.colorScheme.onPrimaryContainer,
)
