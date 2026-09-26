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
import eu.tintera.plume.core.telegram.BotCommandItem

/**
 * Příkazy botů nad vstupním polem: napovídání po napsání „/" a seznam z tlačítka u pole.
 *
 * **Nad polem, ne ve sheetu**, jako vlastní klávesnice bota (`ReplyKeyboardPanel`): napovídání
 * se mění s každým písmenem a sheet by se o klávesnici přetahoval. Ťuknutí příkaz rovnou pošle
 * — tak to dělají oficiální klienti; příkaz s argumentem se dopíše do pole ručně.
 *
 * Strop výšky ([MAX_HEIGHT]) a rolování: bot může mít desítky příkazů a s otevřenou klávesnicí
 * by z konverzace nezbylo nic.
 */
@Composable
internal fun BotCommandsPanel(
    commands: List<BotCommandItem>,
    visible: Boolean,
    onPick: (BotCommandItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Při odjezdu se kreslí poslední seznam, ne prázdno (stejně jako pruh připnutých).
    val last = remember { arrayOf(commands) }
    if (commands.isNotEmpty()) last[0] = commands

    AnimatedVisibility(
        visible = visible && commands.isNotEmpty(),
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
                // Ve skupině může mít stejný příkaz víc botů; klíč je proto celý posílaný text.
                items(last[0], key = { it.text }) { command ->
                    ListItem(
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        headlineContent = {
                            Text(
                                command.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        supportingContent = command.description.takeIf { it.isNotBlank() }?.let {
                            { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        },
                        modifier = Modifier.clickable { onPick(command) },
                    )
                }
            }
        }
    }
}

/** Nejvýš tolik zabere seznam; zbytek se roluje. Zhruba čtyři příkazy s popisem. */
private val MAX_HEIGHT = 280.dp

private val PANEL_CORNER = 20.dp
