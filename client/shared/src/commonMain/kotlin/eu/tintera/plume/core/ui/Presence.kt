package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.presence_last_month
import eu.tintera.plume.resources.presence_last_week
import eu.tintera.plume.resources.presence_online
import eu.tintera.plume.resources.presence_recently
import org.jetbrains.compose.resources.stringResource

@Composable
fun Presence.describe(): String = when (this) {
    Presence.Online -> stringResource(Res.string.presence_online)
    Presence.Recently -> stringResource(Res.string.presence_recently)
    Presence.LastWeek -> stringResource(Res.string.presence_last_week)
    Presence.LastMonth -> stringResource(Res.string.presence_last_month)
    // Celá věta, ne čas vsazený do šablony — viz [asLastSeen].
    is Presence.Offline -> wasOnline.asLastSeen()
}
