package eu.tintera.plume.features.conversation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

    BarPill(
        title = title,
        subtitle = subtitle,
        onClick = onClick,
        glass = glass,
        modifier = modifier,
        titleColor = MaterialTheme.colorScheme.tertiary,
        leading = {
            BarBadge(
                Res.drawable.videocam_24dp,
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        },
    )
}
