package eu.tintera.plume.features.stories

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.StoryViewer
import eu.tintera.plume.core.telegram.StoryViewerAction
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.GroupEmptyRow
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.asRelativeTime
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.story_viewer_forwarded
import eu.tintera.plume.resources.story_viewer_reposted
import eu.tintera.plume.resources.story_viewers_expired
import eu.tintera.plume.resources.story_viewers_none
import eu.tintera.plume.resources.story_viewers_title
import eu.tintera.plume.resources.story_views
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Kdo vlastní příběh viděl: jméno, kdy, a reakce vpravo. Seznam se dočítá po stránkách, když
 * dojede na konec. Po vypršení příběhu ho server po čase přestane dávat ([StoryViewersState.expired]).
 */
@Composable
internal fun StoryViewersSheet(
    state: StoryViewersState,
    viewCount: Int?,
    onLoadMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        val count = maxOf(state.total, viewCount ?: 0, state.viewers.size)
        SheetTitle(
            if (count > 0) pluralStringResource(Res.plurals.story_views, count, count) else stringResource(Res.string.story_viewers_title),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp),
        )
        val empty = state.viewers.isEmpty()
        when {
            state.expired -> GroupEmptyRow(stringResource(Res.string.story_viewers_expired), Modifier.padding(16.dp).navigationBarsPadding())
            empty && state.loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
            empty && state.error != null -> GroupEmptyRow(state.error.resolve(), Modifier.padding(16.dp).navigationBarsPadding())
            empty -> GroupEmptyRow(stringResource(Res.string.story_viewers_none), Modifier.padding(16.dp).navigationBarsPadding())
            else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = LIST_MAX_HEIGHT).navigationBarsPadding()) {
                items(state.viewers, key = { "${it.chatId}-${it.action}" }) { viewer -> ViewerRow(viewer) }
                if (state.nextOffset != null) {
                    item(key = "more") {
                        LaunchedEffect(state.viewers.size) { onLoadMore() }
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator(Modifier.size(32.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewerRow(viewer: StoryViewer) {
    val time = viewer.date.asRelativeTime()
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Avatar(
                chatId = viewer.chatId,
                title = viewer.title,
                avatarFileId = viewer.avatarFileId,
                size = 40.dp,
                shape = viewer.kind.avatarShape(),
            )
        },
        supportingContent = {
            Text(
                when (viewer.action) {
                    StoryViewerAction.Viewed -> time
                    StoryViewerAction.Forwarded -> stringResource(Res.string.story_viewer_forwarded, time)
                    StoryViewerAction.Reposted -> stringResource(Res.string.story_viewer_reposted, time)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = viewer.reaction?.let { { ReactionGlyph(it, size = 24, description = null) } },
    ) { Text(viewer.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

private val LIST_MAX_HEIGHT = 520.dp
