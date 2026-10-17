package eu.tintera.plume.features.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.audio.VoiceKey
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.features.conversation.formatDuration
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.music_note_24dp
import eu.tintera.plume.resources.pause_24dp
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.profile_music
import eu.tintera.plume.resources.profile_music_remove
import eu.tintera.plume.resources.profile_music_show_all
import eu.tintera.plume.resources.profile_shared_music_pause
import eu.tintera.plume.resources.profile_shared_music_play
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Hudba v profilu (`getUserProfileAudios`): skladby, které si uživatel dal do profilu, pod
 * údaji jako vlastní blok se stejným nadpisem a řádky.
 *
 * - **Ťuknutí skladbu pustí** frontou hudby se seznamem z profilu: po dohrání naváže další
 *   skladba profilu a přehrávač v notifikaci má „další" i „předchozí". U hrající je místo délky
 *   uplynulý čas, jako v záložce Hudba.
 * - **Nejvýš [COLLAPSED_ROWS] skladby**, zbytek za „Zobrazit všechny" — profil je hlavně
 *   o člověku, ne playlist.
 * - **Ve vlastním profilu jde skladbu odebrat** křížkem ([onRemove]); přidává se z nabídky
 *   nad zprávou se skladbou.
 */
@Composable
internal fun ProfileMusic(
    audios: List<MessageMedia.Audio>,
    chatId: Long,
    playback: VoicePlayback?,
    onToggle: (index: Int) -> Unit,
    /** Odebrat z vlastního profilu; `null` = cizí profil. */
    onRemove: ((fileId: Int) -> Unit)?,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shown = if (expanded) audios else audios.take(COLLAPSED_ROWS)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(Res.string.profile_music),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING)) {
            shown.forEachIndexed { index, audio ->
                ProfileAudioRow(
                    audio = audio,
                    playback = playback?.takeIf { it.key == VoiceKey(chatId, profileAudioKey(audio.fileId)) },
                    shape = groupItemShape(index, shown.size),
                    onToggle = { onToggle(index) },
                    onRemove = onRemove?.let { remove -> { remove(audio.fileId) } },
                )
            }
        }
        if (audios.size > shown.size) {
            TextButton(onClick = { expanded = true }, modifier = Modifier.padding(start = 4.dp)) {
                Text(pluralStringResource(Res.plurals.profile_music_show_all, audios.size, audios.size))
            }
        }
    }
}

/** Řádek skladby: obal (nebo nota) s tlačítkem, název, pod ním interpret s délkou. */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun ProfileAudioRow(
    audio: MessageMedia.Audio,
    playback: VoicePlayback?,
    shape: Shape,
    onToggle: () -> Unit,
    onRemove: (() -> Unit)?,
) {
    val playing = playback?.playing == true
    val time = when {
        playback != null && playback.durationMs > 0 ->
            "${formatDuration((playback.positionMs / 1000).toInt())} / ${formatDuration((playback.durationMs / 1000).toInt())}"
        audio.durationSeconds > 0 -> formatDuration(audio.durationSeconds)
        else -> null
    }
    val meta = listOfNotNull(audio.performer?.ifBlank { null }, time).joinToString(" · ")
    val actionLabel = stringResource(if (playing) Res.string.profile_shared_music_pause else Res.string.profile_shared_music_play)
    ListItem(
        headlineContent = { Text(audio.title.ifBlank { audio.fileName }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = meta.takeIf { it.isNotEmpty() }?.let {
            { Text(it, style = LocalTextStyle.current.tabularNumbers(), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        leadingContent = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(COVER_SIZE)
                    .clip(RoundedCornerShape(COVER_CORNER))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                audio.coverFileId?.let { cover ->
                    RemoteImage(fileId = cover, modifier = Modifier.fillMaxSize())
                    // Tlačítko musí být vidět i na světlém obalu.
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = COVER_SCRIM_ALPHA)))
                }
                when {
                    playback?.loading == true && !playing -> CircularWavyProgressIndicator(Modifier.size(24.dp))
                    else -> Icon(
                        painterResource(
                            when {
                                playing -> Res.drawable.pause_24dp
                                playback != null || audio.coverFileId != null -> Res.drawable.play_arrow_24dp
                                else -> Res.drawable.music_note_24dp
                            },
                        ),
                        contentDescription = null,
                        tint = if (audio.coverFileId != null) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        },
        trailingContent = onRemove?.let { remove ->
            {
                IconButton(onClick = remove, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.profile_music_remove))
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        modifier = Modifier.clip(shape).clickable(onClickLabel = actionLabel, onClick = onToggle),
    )
}

/** Kolik skladeb profil ukáže, než se rozbalí. */
private const val COLLAPSED_ROWS = 3

private val COVER_SIZE = 44.dp
private val COVER_CORNER = 8.dp
private const val COVER_SCRIM_ALPHA = 0.35f
