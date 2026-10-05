package eu.tintera.plume.features.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.VideoChatInfo
import eu.tintera.plume.core.ui.asUpcomingTime
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.video_chat_active
import eu.tintera.plume.resources.video_chat_live
import eu.tintera.plume.resources.video_chat_live_scheduled
import eu.tintera.plume.resources.video_chat_participants
import eu.tintera.plume.resources.video_chat_scheduled
import eu.tintera.plume.resources.videocam_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Probíhající nebo naplánovaný videochat (živé vysílání v kanálu) nad konverzací, tvarem
 * i sklem jako pruh připnutých zpráv — oba plavou nad seznamem a patří k sobě.
 *
 * - **Připojit se Plume neumí**, pruh jen ohlásí, že se něco děje; ťuknutí řekne, kde se
 *   připojit ([onClick]). Bez pruhu by kdo má jen Plume o videochatu nevěděl vůbec.
 * - **Odznak je kamera v terciárním kruhu** — terciární role nese v aplikaci „živé" věci
 *   (hlas, nahrávání), a od primárního špendlíku připnutých se tak pruhy odliší i barvou.
 */
@Composable
internal fun VideoChatBar(
    info: VideoChatInfo,
    onClick: () -> Unit,
    /** Mléčné sklo nad seznamem: plocha je průhledná, barvu i rozostření nese sklo. */
    glass: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val title = when {
        info.scheduledStart != null -> stringResource(
            if (info.isLiveStream) Res.string.video_chat_live_scheduled else Res.string.video_chat_scheduled,
            info.scheduledStart.toLong().asUpcomingTime(),
        )
        info.isLiveStream -> stringResource(Res.string.video_chat_live)
        else -> stringResource(Res.string.video_chat_active)
    }
    val count = info.participantCount?.takeIf { info.scheduledStart == null }
        ?.let { pluralStringResource(Res.plurals.video_chat_participants, it, it.grouped()) }
    val subtitle = listOfNotNull(info.title.takeIf { it.isNotBlank() }, count).joinToString(" · ")

    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = CircleShape,
        shadowElevation = 3.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier
                .clip(CircleShape)
                .then(glass)
                .clickable(onClick = onClick)
                .heightIn(min = BAR_HEIGHT)
                // Odznak soustředný se zaoblením pilulky, jako u připnutých.
                .padding(start = BADGE_INSET, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = CircleShape, modifier = Modifier.size(BADGE_SIZE)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(Res.drawable.videocam_24dp),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private val BAR_HEIGHT = 56.dp
private val BADGE_SIZE = 40.dp
private val BADGE_INSET = (BAR_HEIGHT - BADGE_SIZE) / 2
