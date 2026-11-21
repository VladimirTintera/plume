package eu.tintera.plume.features.profile

import eu.tintera.plume.resources.profile_story_albums_all
import eu.tintera.plume.resources.profile_story_album_empty
import eu.tintera.plume.core.ui.GroupEmptyRow
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
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
    onSelectAlbum: (albumId: Int?) -> Unit,
    modifier: Modifier = Modifier,
    /** Věta nad prázdnou mřížkou; výchozí je prázdné album (v profilu jinak záložka není). */
    emptyText: String? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(STORY_TILE_MIN),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        // Alba jako čipy přes celou šířku nad mřížkou; „Vše" první. Bez alb nic.
        if (stories.albums.isNotEmpty()) {
            item(key = "albums", span = { GridItemSpan(maxLineSpan) }) {
                AlbumChips(stories, onSelectAlbum)
            }
        }
        if (stories.items.isEmpty() && !stories.loading && !stories.hasMore) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                GroupEmptyRow(emptyText ?: stringResource(Res.string.profile_story_album_empty))
            }
        }
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

@Composable
private fun AlbumChips(stories: ProfileStories, onSelectAlbum: (Int?) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    ) {
        item(key = "all") {
            FilterChip(
                selected = stories.albumId == null,
                onClick = { onSelectAlbum(null) },
                label = { Text(stringResource(Res.string.profile_story_albums_all)) },
                shapes = FilterChipDefaults.shapes(),
            )
        }
        items(stories.albums, key = { it.id }) { album ->
            FilterChip(
                selected = stories.albumId == album.id,
                onClick = { onSelectAlbum(album.id) },
                label = { Text(album.name, maxLines = 1) },
                shapes = FilterChipDefaults.shapes(),
            )
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
