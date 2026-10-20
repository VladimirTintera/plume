package eu.tintera.plume.features.stories

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.tintera.plume.core.telegram.ActiveStories
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.StoryRingState
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.storyRing
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.stories_stack
import org.jetbrains.compose.resources.stringResource

/**
 * Hromádka autorů s příběhy v pilulce hledání, vedle fotky účtu. Je to jediný vstup k příběhům
 * kontaktů, se kterými chat nemáme — v seznamu chatů nejsou, takže by u nich kroužek nebyl.
 * Ťuknutí pustí příběhy po sobě od prvního autora s nezhlédnutým. Bez příběhů hromádka zmizí
 * a pilulka zůstane, jak byla — nahoře nic netrčí.
 *
 * [rim] je barva pilulky: lem kolem každého avataru odděluje překrývající se kolečka.
 */
@Composable
internal fun StoryStack(
    posters: List<ActiveStories>,
    rim: Color,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shown = remember(posters) { posters.take(STACK_SIZE) }
    AnimatedVisibility(
        visible = shown.isNotEmpty(),
        enter = expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) +
            fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
        exit = shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) +
            fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        modifier = modifier,
    ) {
        val start = (posters.firstOrNull { it.hasUnread } ?: posters.firstOrNull())?.chatId
        val step = STACK_AVATAR - STACK_OVERLAP
        Box(
            Modifier
                .clip(CircleShape)
                .clickable(
                    enabled = start != null,
                    onClickLabel = stringResource(Res.string.stories_stack),
                    role = Role.Button,
                ) { start?.let(onOpen) }
                .padding(STACK_PADDING)
                .size(width = STACK_AVATAR + step * (shown.size - 1).coerceAtLeast(0), height = STACK_AVATAR),
        ) {
            shown.forEachIndexed { index, poster ->
                val shape = poster.kind.avatarShape()
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = step * index)
                        // První autor navrchu: hromádka se čte zleva jako seznam.
                        .zIndex((shown.size - index).toFloat())
                        .storyRing(
                            ring = StoryRingState(total = 1, unread = if (poster.hasUnread) 1 else 0, closeFriends = poster.hasUnreadForCloseFriends),
                            shape = shape,
                            unreadColor = if (poster.hasUnreadForCloseFriends) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            readColor = MaterialTheme.colorScheme.outlineVariant,
                            gap = 1.dp,
                            width = 1.5.dp,
                            readWidth = 1.dp,
                        )
                        .background(rim, shape),
                ) {
                    Avatar(
                        chatId = poster.avatarColorId,
                        title = poster.title,
                        avatarFileId = poster.avatarFileId,
                        size = STACK_AVATAR,
                        shape = shape,
                        backdrop = rim,
                    )
                }
            }
        }
    }
}

private const val STACK_SIZE = 3
private val STACK_AVATAR = 24.dp
private val STACK_OVERLAP = 9.dp
private val STACK_PADDING = 6.dp
