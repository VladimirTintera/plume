package eu.tintera.plume.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.profile_story_open
import eu.tintera.plume.resources.profile_story_pinned
import eu.tintera.plume.resources.push_pin_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Příběhy, které autor nechal v profilu: dlaždice 9 : 16 (tak jsou příběhy natočené), připnuté
 * první se špendlíkem, video s trojúhelníkem. Ťuknutí otevře prohlížeč nad celou mřížkou od
 * téhle dlaždice; konec mřížky si řekne o další stránku.
 */
@Composable
internal fun ProfileStoriesGrid(
    stories: ProfileStories,
    contentPadding: PaddingValues,
    onOpen: (storyId: Int) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(STORY_TILE_MIN),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(stories.items, key = { _, story -> story.storyId }) { index, story ->
            if (stories.hasMore) LoadMoreAtEnd(index, stories.items.size, onLoadMore)
            StoryTile(story, pinned = story.storyId in stories.pinnedIds, onClick = { onOpen(story.storyId) })
        }
        if (stories.loading) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
        }
    }
}

@Composable
private fun StoryTile(story: MessageMedia.Story, pinned: Boolean, onClick: () -> Unit) {
    val media = story.content
    Box(
        Modifier
            .aspectRatio(STORY_TILE_ASPECT)
            .clip(ShapeDefaults.Small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClickLabel = stringResource(Res.string.profile_story_open), onClick = onClick),
    ) {
        when (media) {
            is MessageMedia.Photo -> RemoteImage(
                fileId = media.fileId,
                placeholder = media.minithumbnail,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            is MessageMedia.Video -> RemoteImage(
                fileId = media.thumbnailFileId ?: media.fileId,
                placeholder = media.minithumbnail,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            else -> Unit
        }
        if (pinned) {
            Badge(Modifier.align(Alignment.TopEnd)) {
                Icon(
                    painterResource(Res.drawable.push_pin_24dp),
                    contentDescription = stringResource(Res.string.profile_story_pinned),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(BADGE_ICON),
                )
            }
        }
        if (media is MessageMedia.Video) {
            Badge(Modifier.align(Alignment.BottomStart)) {
                Icon(
                    painterResource(Res.drawable.play_arrow_24dp),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(BADGE_ICON),
                )
            }
        }
    }
}

/** Kolečko s ikonou v rohu dlaždice — na tónované ploše, ať je čitelné na světlé i tmavé fotce. */
@Composable
private fun Badge(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .padding(6.dp)
            .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private val STORY_TILE_MIN = 110.dp
private const val STORY_TILE_ASPECT = 9f / 16f
private val BADGE_ICON = 14.dp
