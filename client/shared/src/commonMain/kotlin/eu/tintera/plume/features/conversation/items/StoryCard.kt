package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.content_story
import eu.tintera.plume.resources.content_unsupported
import eu.tintera.plume.resources.history_toggle_off_24dp
import eu.tintera.plume.resources.motion_photos_on_24dp
import eu.tintera.plume.resources.sensors_24dp
import eu.tintera.plume.resources.story_from
import eu.tintera.plume.resources.story_live
import eu.tintera.plume.resources.story_mention
import eu.tintera.plume.resources.story_unavailable
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Snímek sdíleného příběhu. Obsah je obyčejná fotka nebo video, takže ho kreslí [content] —
 * táž cesta jako příloha zprávy, i s rámečkem podle poměru stran, letem do prohlížeče
 * a stahováním. Navíc je v rohu značka „Příběh", jinak by se příběh od fotky nelišil.
 *
 * Příběh, který už není, nebo živé vysílání (to Plume neumí) je plocha s ikonou a větou ve
 * tvaru příběhu na výšku — prázdné místo by vypadalo jako nenačtený obrázek.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun StoryPreview(
    story: MessageMedia.Story,
    shape: Shape,
    onOpen: () -> Unit,
    onRequestMenu: () -> Unit,
    content: @Composable (MessageMedia) -> Unit,
) {
    val inner = story.content
    if (inner != null) {
        Box {
            content(inner)
            StoryBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        return
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = PLACEHOLDER_WIDTH, height = PLACEHOLDER_HEIGHT)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            // Ťuknutí u živého vysílání vysvětlí, proč se nic neotevře; dlouhý stisk se opakuje.
            .combinedClickable(onClick = onOpen, onLongClick = onRequestMenu),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Icon(
                painterResource(if (story.isLive) Res.drawable.sensors_24dp else Res.drawable.history_toggle_off_24dp),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = stringResource(
                    when {
                        story.isLive -> Res.string.story_live
                        // Příběh je, jen druh obsahu neznáme (novější TDLib).
                        story.isAvailable -> Res.string.content_unsupported
                        else -> Res.string.story_unavailable
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        StoryBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
    }
}

/** Značka „Příběh" v rohu snímku, ve stylu značky „GIF" — tmavý podklad, bílé písmo. */
@Composable
private fun StoryBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(percent = 50),
        color = Color.Black.copy(alpha = BADGE_ALPHA),
        contentColor = Color.White,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 6.dp, end = 8.dp, top = 3.dp, bottom = 3.dp),
        ) {
            Icon(painterResource(Res.drawable.motion_photos_on_24dp), contentDescription = null, modifier = Modifier.size(14.dp))
            Text(text = stringResource(Res.string.content_story), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * Pod snímkem: kdo příběh zveřejnil, proč přišel (zmínka) a jeho popisek. Kreslí se v kartě
 * pod snímkem stejně jako název místa pod mapou.
 */
@Composable
internal fun StoryDetails(story: MessageMedia.Story, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        val title = story.posterName?.let { stringResource(Res.string.story_from, it) }
            ?: stringResource(Res.string.content_story)
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (story.viaMention) {
            Text(
                text = stringResource(Res.string.story_mention),
                style = MaterialTheme.typography.bodyMedium,
                color = color.copy(alpha = DETAILS_SUBTITLE_ALPHA),
            )
        }
        if (story.caption.isNotBlank()) {
            Text(
                text = story.caption,
                style = MaterialTheme.typography.bodyMedium,
                color = color,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private const val BADGE_ALPHA = 0.55f
private const val DETAILS_SUBTITLE_ALPHA = 0.7f

/** Místo nedostupného příběhu: na výšku jako příběh, ale menší — není co ukazovat. */
private val PLACEHOLDER_WIDTH = 160.dp
private val PLACEHOLDER_HEIGHT = 240.dp
