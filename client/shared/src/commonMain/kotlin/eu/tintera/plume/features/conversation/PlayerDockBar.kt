package eu.tintera.plume.features.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.audio.VoiceKey
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.location_dock_show_message
import eu.tintera.plume.resources.pause_24dp
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.player_dock_next
import eu.tintera.plume.resources.player_dock_stop
import eu.tintera.plume.resources.profile_shared_music_pause
import eu.tintera.plume.resources.profile_shared_music_play
import eu.tintera.plume.resources.skip_next_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Co hraje a patří do docku téhle konverzace ([docksIn]) — vše, co řádek potřebuje kromě
 * průběhu, který se čte zvlášť, dvacetkrát za vteřinu.
 */
internal data class DockedPlayer(
    val key: VoiceKey,
    val music: Boolean,
    /** Název skladby, u hlasovky kdo ji poslal. */
    val title: String,
    /** Interpret, u hlasovky „Hlasová zpráva"; `null` = nic. */
    val label: String?,
    val coverFileId: Int? = null,
    /** Další skladba v chatu ([eu.tintera.plume.core.audio.MusicQueue]); u hlasovky nikdy. */
    val hasNext: Boolean = false,
    /** Video kolečko se zvukem: v tlačítku hraje jeho obraz, [coverFileId] je jeho náhled. */
    val video: Boolean = false,
)

/**
 * Patří hrající zvuk do docku konverzace [chatId]?
 *
 * Hlasovka a skladba z tohohle chatu ano, náhled vlastní nahrávky ne (ten má vlastní pruh nad
 * polem). Skladba z JINÉHO chatu taky: hraje dál i po odchodu z něj a v aplikaci by ji jinak
 * nešlo zastavit. Hlasovka z jiného chatu nastat nemá — odchod z chatu ji zastaví.
 */
internal fun VoicePlayback.docksIn(chatId: Long): Boolean = when {
    key.isVoicePreview -> false
    key.chatId == chatId -> true
    else -> music
}

/**
 * Mini přehrávač: řádek docku pro hrající hudbu, hlasovku a kolečko se zvukem, jejichž zpráva
 * odjela z obrazovky.
 * Dokud hraje, zůstává po ruce pauza, „další" a zastavení — dřív se odscrollovaná hlasovka
 * zastavit nedala vůbec a hudba jen z notifikace.
 *
 * - **Vysouvá se jako dock polohy** ([dockReveal]): o tolik, kolik z hrající zprávy zajelo pod
 *   okraj. Skladba z jiného chatu svou zprávu v seznamu nemá, a je proto v docku celá.
 * - **Tlačítko je kolečko průběhu**: obal skladby (nebo tónovaná plocha), přes něj pauza
 *   a po obvodu, kolik zbývá. Průběh se čte až při kreslení.
 * - **Barva říká, čeho se týká**: hlas je v aplikaci terciární (jako odesílací tlačítko při
 *   nahrávání), hudba primární.
 * - **Ťuknutí na text vede ke zprávě**, u skladby z jiného chatu do toho chatu.
 * - **Kolečko hraje přímo v tlačítku** ([video]): obraz kreslí dock, až je kolečko víc v docku
 *   než v seznamu — do té doby je v tlačítku jeho náhled.
 */
@Composable
internal fun PlayerDockBar(
    player: DockedPlayer,
    /** Stav přehrávače; čte se až tam, kde se kreslí průběh a čas. */
    playback: () -> VoicePlayback?,
    reveal: () -> Float,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onShow: () -> Unit,
    glass: Modifier = Modifier,
    /** Obraz hrajícího kolečka; kreslí se přes náhled ([DockedPlayer.coverFileId]). */
    video: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = if (player.music) scheme.primary else scheme.tertiary
    val container = if (player.music) scheme.primaryContainer else scheme.tertiaryContainer
    val onContainer = if (player.music) scheme.onPrimaryContainer else scheme.onTertiaryContainer
    val showLabel = stringResource(Res.string.location_dock_show_message)

    Box(modifier.dockReveal(reveal)) {
        Surface(
            color = Color.Transparent,
            contentColor = scheme.onSurface,
            shape = DOCK_SHAPE,
            shadowElevation = DOCK_ELEVATION,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Row(
                Modifier
                    .clip(DOCK_SHAPE)
                    .then(glass)
                    .heightIn(min = DOCK_ROW_HEIGHT)
                    .padding(start = DOCK_BADGE_INSET, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayButton(player, playback, accent, container, onContainer, onToggle, video)
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = DOCK_ROW_HEIGHT)
                        .clickable(onClickLabel = showLabel, onClick = onShow)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        player.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    PlaybackLine(player.label, playback)
                }
                if (player.hasNext) {
                    IconButton(onClick = onNext) {
                        Icon(painterResource(Res.drawable.skip_next_24dp), contentDescription = stringResource(Res.string.player_dock_next))
                    }
                }
                IconButton(onClick = onStop) {
                    Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.player_dock_stop))
                }
            }
        }
    }
}

/** Pauza / přehrát s průběhem po obvodu, přes obal skladby, když ho zpráva má. */
@Composable
private fun PlayButton(
    player: DockedPlayer,
    playback: () -> VoicePlayback?,
    accent: Color,
    container: Color,
    onContainer: Color,
    onToggle: () -> Unit,
    video: (@Composable () -> Unit)?,
) {
    // Ikona se mění jen při spuštění a pauze, ne s každým posunem pozice.
    val playing by remember(playback) { derivedStateOf { playback()?.playing == true } }
    val loading by remember(playback) { derivedStateOf { playback()?.loading == true } }
    val label = stringResource(if (playing) Res.string.profile_shared_music_pause else Res.string.profile_shared_music_play)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(DOCK_BADGE_SIZE)
            .clip(CircleShape)
            .background(container)
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics { contentDescription = label },
    ) {
        val covered = player.coverFileId != null || video != null
        player.coverFileId?.let { cover -> RemoteImage(fileId = cover, modifier = Modifier.fillMaxSize()) }
        video?.invoke()
        // Ikona i průběh musí být vidět i na světlém obalu.
        if (covered) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = COVER_SCRIM_ALPHA)))
        val tint = if (covered) Color.White else onContainer
        if (loading) {
            CircularProgressIndicator(color = tint, strokeWidth = RING_WIDTH, modifier = Modifier.fillMaxSize().padding(RING_WIDTH / 2))
        } else {
            CircularProgressIndicator(
                progress = { playback()?.progress ?: 0f },
                color = if (covered) Color.White else accent,
                trackColor = Color.Transparent,
                strokeWidth = RING_WIDTH,
                gapSize = 0.dp,
                modifier = Modifier.fillMaxSize().padding(RING_WIDTH / 2),
            )
        }
        Icon(
            painterResource(if (playing) Res.drawable.pause_24dp else Res.drawable.play_arrow_24dp),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** „Interpret · 0:12 / 3:40": čas se přepisuje jednou za vteřinu, ne s každým tikem pozice. */
@Composable
private fun PlaybackLine(label: String?, playback: () -> VoicePlayback?) {
    val time by remember(playback) {
        derivedStateOf {
            playback()?.takeIf { it.durationMs > 0 }?.let { p ->
                "${formatDuration((p.positionMs / 1000).toInt())} / ${formatDuration((p.durationMs / 1000).toInt())}"
            }
        }
    }
    val line = listOfNotNull(label, time).joinToString(" · ")
    if (line.isEmpty()) return
    Text(
        line,
        style = MaterialTheme.typography.bodyMedium.tabularNumbers(),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private val RING_WIDTH = 3.dp
private const val COVER_SCRIM_ALPHA = 0.35f
