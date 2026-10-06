package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.tintera.plume.core.telegram.AttachmentMode
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.ui.DarkSystemBars
import eu.tintera.plume.core.ui.MEDIA_PAGE_VERTICAL_GAP
import eu.tintera.plume.core.ui.aspectRatioOf
import eu.tintera.plume.core.ui.attachmentKey
import eu.tintera.plume.core.ui.sharedFlight
import eu.tintera.plume.features.conversation.items.SpoilerOverlay
import eu.tintera.plume.features.conversation.items.rememberSpoilerBlurStyle
import eu.tintera.plume.features.conversation.items.spoilerBlur
import eu.tintera.plume.features.media.DISMISS_FADE
import eu.tintera.plume.features.media.SystemBarShades
import eu.tintera.plume.features.media.VideoPage
import eu.tintera.plume.features.media.ZoomableMediaPage
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.attachment_done
import eu.tintera.plume.resources.attachment_preview_title
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.keyboard_arrow_down_24dp
import kotlin.math.abs
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Připravená příloha přes celou obrazovku, před odesláním: prohlédnout detail, zvolit režim
 * odeslání (spoiler, mizení, jako soubor), případně ji zahodit.
 *
 * **Stránky jsou TYTÉŽ jako v prohlížeči médií a v potvrzení sdílení** ([ZoomableMediaPage],
 * [VideoPage]), jen nakrmené souborem na disku. Dřív tu byl vlastní zoom přes
 * `detectTransformGestures` — bez přiblížení kolem prstů, bez dvojitého ťuknutí, s posunem
 * bez mezí — a video šlo jen jako náhled, přehrát nešlo. Třetí kopie téhož by se rozcházela
 * s prohlížečem při každém doladění.
 *
 * Chová se jako prohlížeč: karta leží mezi lištami, ťuknutí lišty schová a snímek jde od kraje
 * ke kraji (průběh `immersion`), štípnutí a dvojité ťuknutí přibližují, tah dolů náhled zavře.
 * Spoiler je vidět zakrytý jen s lištami — je to volba odesílatele a na celé obrazovce se má dát
 * zkontrolovat, co pod ním je; stejně jako na obrazovce „Komu: …".
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

    // Podklad je černý v obou motivech, takže i ikony systémových lišt musí být světlé.
    if (isOpen) DarkSystemBars()

    // S lištami, nebo přes celou obrazovku; každé otevření začíná s lištami.
    var chromeVisible by remember { mutableStateOf(true) }
    LaunchedEffect(isOpen) { if (isOpen) chromeVisible = true }
    val immersionState = animateFloatAsState(
        targetValue = if (chromeVisible) 0f else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "attachment-preview-immersion",
    )
    // Pružina přestřelí; záporné odsazení nebo zaoblení by layout odmítl.
    val immersion = { immersionState.value.coerceIn(0f, 1f) }

    // Zavírací tah; čte se jen při kreslení. Každé otevření začíná na místě.
    val dismissDrag = remember { Animatable(0f) }
    LaunchedEffect(isOpen) { if (isOpen) dismissDrag.snapTo(0f) }
    val fadeWithDismiss = Modifier.graphicsLayer {
        alpha = 1f - (abs(dismissDrag.value) / size.height * DISMISS_FADE).coerceIn(0f, 1f)
    }

    val density = LocalDensity.current
    val reservedTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() } + TOP_BAR_HEIGHT + MEDIA_PAGE_VERTICAL_GAP
    val reservedBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() } + BOTTOM_BAR_HEIGHT + MEDIA_PAGE_VERTICAL_GAP

    // Spoiler zakrývá jen s lištami, na celé obrazovce je snímek vidět.
    val hidden = attachment.hasSpoiler && chromeVisible
    val spoilerStyle = rememberSpoilerBlurStyle()
    // Náhled nad vstupním polem sem vyletí a při zavření se do něj vrátí.
    val flight = Modifier.sharedFlight(attachmentKey(attachment.path), visible = isOpen)
    val toggleChrome = { chromeVisible = !chromeVisible }

    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(fadeWithDismiss).background(Color.Black))

        when (attachment) {
            is OutgoingAttachment.Video -> VideoPage(
                aspectRatio = aspectRatioOf(attachment.width, attachment.height),
                durationSeconds = attachment.durationSeconds,
                caption = "",
                // Video hraje, dokud je náhled otevřený; zavření ho pozastaví.
                isCurrent = isOpen,
                immersive = true,
                immersion = immersion,
                reservedTop = reservedTop,
                reservedBottom = reservedBottom,
                dismissDrag = dismissDrag,
                onDismiss = onDismiss,
                onTap = toggleChrome,
                onEngage = { chromeVisible = false },
                // Soubor je celý na disku, nic se nestahuje.
                source = { FileState.Ready(attachment.path) },
                streamable = false,
                requestFrom = {},
                hasSpoiler = attachment.hasSpoiler,
                revealed = !hidden,
                onReveal = toggleChrome,
                modifier = Modifier.fillMaxSize(),
                thumbnail = {
                    AsyncImage(
                        // Coil potřebuje schéma, holá cesta by se vyhodnotila jako relativní URI.
                        model = "file://${attachment.thumbnail?.path ?: attachment.path}",
                        contentDescription = stringResource(Res.string.attachment_preview_title),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(flight)
                            .spoilerBlur(hasSpoiler = attachment.hasSpoiler, revealed = !hidden, style = spoilerStyle),
                    )
                },
            )

            else -> ZoomableMediaPage(
                aspectRatio = (attachment as? OutgoingAttachment.Photo)
                    ?.let { aspectRatioOf(it.width, it.height) } ?: 1f,
                caption = "",
                isCurrent = isOpen,
                immersive = true,
                immersion = immersion,
                reservedTop = reservedTop,
                reservedBottom = reservedBottom,
                dismissDrag = dismissDrag,
                onDismiss = onDismiss,
                onTap = toggleChrome,
                // Přiblížení s lištami nedává smysl, fotka by rostla přes ně — jako v prohlížeči.
                onEngage = { chromeVisible = false },
                modifier = Modifier.fillMaxSize(),
                cardModifier = flight,
            ) {
                AsyncImage(
                    model = "file://${attachment.path}",
                    contentDescription = stringResource(Res.string.attachment_preview_title),
                    // Karta má poměr stran snímku, takže `Crop` nic neukrojí.
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .spoilerBlur(hasSpoiler = attachment.hasSpoiler, revealed = !hidden, style = spoilerStyle),
                )
                if (attachment.hasSpoiler) SpoilerOverlay(visible = hidden)
            }
        }

        // Na celé obrazovce ztmavené systémové lišty, jako v prohlížeči.
        SystemBarShades(immersion, fadeWithDismiss)

        val chromeEnter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec())
        val chromeExit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())

        AnimatedVisibility(
            visible = chromeVisible,
            enter = chromeEnter,
            exit = chromeExit,
            modifier = Modifier.align(Alignment.TopStart).then(fadeWithDismiss),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(TOP_BAR_HEIGHT)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
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

                IconButton(onClick = onDelete, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        painter = painterResource(Res.drawable.delete_24dp),
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = chromeEnter,
            exit = chromeExit,
            modifier = Modifier.align(Alignment.BottomCenter).then(fadeWithDismiss),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(BOTTOM_BAR_HEIGHT)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Tonální pilulka s volbou režimu odeslání.
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

private val TOP_BAR_HEIGHT = 56.dp
private val BOTTOM_BAR_HEIGHT = 64.dp
