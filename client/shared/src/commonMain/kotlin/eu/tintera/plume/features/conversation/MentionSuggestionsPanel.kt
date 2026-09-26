package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.MentionCandidate
import eu.tintera.plume.core.telegram.usernameMention
import eu.tintera.plume.core.ui.Avatar

/**
 * Napovídání zmínky nad vstupním polem: členové skupiny k rozepsanému `@…` u kurzoru.
 *
 * **Tentýž panel jako napovídání příkazů** ([BotCommandsPanel]) a výsledky inline bota — nad
 * polem, se stropem výšky; mění se s každým písmenem a klávesnice musí zůstat otevřená.
 * Řádek nese tvář, jméno a `@uživatelské jméno`, když ho člen má: podle něj se pozná, jestli se
 * vloží `@jmeno`, nebo zmínka jménem.
 */
@Composable
internal fun MentionSuggestionsPanel(
    candidates: List<MentionCandidate>,
    visible: Boolean,
    onPick: (MentionCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Při odjezdu se kreslí poslední seznam, ne prázdno (stejně jako pruh připnutých).
    val last = remember { arrayOf(candidates) }
    if (candidates.isNotEmpty()) last[0] = candidates

    AnimatedVisibility(
        visible = visible && candidates.isNotEmpty(),
        enter = expandVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(),
        exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(PANEL_CORNER),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            LazyColumn(Modifier.heightIn(max = MAX_HEIGHT).padding(vertical = 4.dp)) {
                items(last[0], key = { it.userId }) { candidate ->
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        leadingContent = {
                            Avatar(
                                chatId = candidate.userId,
                                title = candidate.name,
                                avatarFileId = candidate.avatarFileId,
                                size = AVATAR_SIZE,
                            )
                        },
                        headlineContent = {
                            Text(candidate.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = candidate.username?.let {
                            { Text(usernameMention(it), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        },
                        modifier = Modifier.clickable { onPick(candidate) },
                    )
                }
            }
        }
    }
}

/** Nejvýš tolik zabere seznam; zbytek se roluje. Stejně jako napovídání příkazů. */
private val MAX_HEIGHT = 280.dp

private val AVATAR_SIZE = 40.dp

private val PANEL_CORNER = 20.dp
