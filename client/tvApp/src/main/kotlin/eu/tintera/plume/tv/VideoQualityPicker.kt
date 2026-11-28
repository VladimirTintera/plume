package eu.tintera.plume.tv

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import eu.tintera.plume.core.connectivity.NetworkKind
import eu.tintera.plume.core.connectivity.NetworkMonitor
import eu.tintera.plume.core.settings.AutoDownloadSettings
import eu.tintera.plume.core.settings.autoDownloadLevel
import eu.tintera.plume.core.settings.limits
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.VideoQuality
import eu.tintera.plume.core.video.VideoQualityChoice
import eu.tintera.plume.core.video.mediaCodecVideoDecoder
import eu.tintera.plume.core.video.pickVideoQuality
import eu.tintera.plume.core.video.videoQualities
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.media_quality_auto
import eu.tintera.plume.resources.media_quality_auto_hint
import eu.tintera.plume.resources.media_quality_lines
import eu.tintera.plume.resources.media_quality_original
import eu.tintera.plume.resources.media_quality_with_size
import eu.tintera.plume.resources.tv_video_quality_title
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningReduce
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Kvality jednoho videa a ta, která hraje: [options] od nejlepší (první je původní soubor),
 * [autoPick] co by vybrala automatika, [playing] co hraje podle volby [choice].
 */
@Immutable
data class PlayedQuality(
    val options: List<VideoQuality>,
    val autoPick: VideoQuality,
    val playing: VideoQuality,
    val choice: VideoQualityChoice,
)

/**
 * Kterou kvalitu videa [video] přehrát — tatáž pravidla jako v prohlížeči na telefonu
 * (`pickVideoQuality` v `core/video`, docs/poznatky/prohlizec-medii.md, „Kvalita videa"):
 *
 * - **Neměřená síť (Wi-Fi, ethernet — televize skoro vždycky)**: přepočítaná kvalita ve STEJNÉM
 *   rozlišení jako původní, jinak původní soubor. Přepočítanou server dělá pro streamování
 *   a rozbíhá se hned; původní soubor bývá, jak ho odesílatel nahrál, s indexem na konci.
 * - **Měřená síť**: strop podle výchozích úrovní automatického stahování (mobilní data 720p,
 *   spořič dat 480p). Televize nastavení stahování nemá, platí výchozí.
 * - **Už stažený původní soubor vyhrává vždycky.**
 *
 * Dekodér se zjišťuje z `MediaCodecList` (sdílený s telefonem, `mediaCodecVideoDecoder`):
 * z každé velikosti, kterou server posílá v AV1 i h264, se nabídne ta s hardwarovým dekodérem.
 * Na slabé televizi (Chromecast) je to rozdíl mezi plynulým obrazem a trhaným.
 *
 * `null`, dokud se neví, na jaké síti televize je a jestli je původní soubor stažený.
 * Automatika se přepočítá i za běhu (Wi-Fi → mobilní data přepne na menší kvalitu), výpadek
 * sítě ale ne.
 */
@Composable
fun rememberPlayedQuality(video: MessageMedia.Video, choice: VideoQualityChoice): PlayedQuality? {
    val network = koinInject<NetworkMonitor>()
    val files = koinInject<FileRepository>()
    val options = remember(video) {
        videoQualities(video.original, video.alternatives) { mediaCodecVideoDecoder(it.codec.orEmpty(), it.width, it.height) }
    }
    // Výpadek sítě (`None`) automatiku nepřepíná: přepnutí by zrušilo rozstahovaný soubor a bez
    // sítě se stejně nic nestáhne. Platí poslední známá síť; jen první hlášení projde, jaké je.
    val kindFlow = remember(network) {
        network.status.map { it.kind }.runningReduce { last, next -> if (next == NetworkKind.None) last else next }
    }
    val kind by kindFlow.collectAsState(initial = null)
    val saveData by network.saveData.collectAsState(initial = false)
    val originalDownloaded by produceState<Boolean?>(null, video.fileId) {
        value = files.observe(video.fileId).first() is FileState.Ready
    }
    val currentKind = kind ?: return null
    val downloaded = originalDownloaded ?: return null
    val maxLines = autoDownloadLevel(AutoDownloadSettings(), currentKind, saveData).limits.maxVideoLines
    val auto = pickVideoQuality(options, VideoQualityChoice.Auto, maxLines, downloaded)
    val playing = if (choice == VideoQualityChoice.Auto) auto else pickVideoQuality(options, choice, maxLines, downloaded)
    return PlayedQuality(options, auto, playing, choice)
}

/** „720p" — kratší strana videa ([VideoQuality.lines]). */
@Composable
fun qualityLabel(quality: VideoQuality): String = stringResource(Res.string.media_quality_lines, quality.lines)

/**
 * Výběr kvality přes přehrávač: Automaticky (s tím, co by automatika vybrala) a kvality od
 * nejlepší s velikostí souboru. Fokus začíná na zvolené položce. Dialog, ne nabídka u tlačítka:
 * přehrávač tlačítka jako cíle fokusu nemá, klávesy bere celý.
 */
@Composable
fun VideoQualityDialog(quality: PlayedQuality, onChoose: (VideoQualityChoice) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = WideDialog) {
        DialogPanel {
            Text(stringResource(Res.string.tv_video_quality_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            val chosen = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { chosen.requestFocus() } }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val autoChosen = quality.choice == VideoQualityChoice.Auto
                QualityRow(
                    headline = stringResource(Res.string.media_quality_auto),
                    supporting = stringResource(Res.string.media_quality_auto_hint, qualityLabel(quality.autoPick)),
                    checked = autoChosen,
                    onClick = { onChoose(VideoQualityChoice.Auto) },
                    modifier = if (autoChosen) Modifier.focusRequester(chosen) else Modifier,
                )
                for (option in quality.options) {
                    val checked = !autoChosen && option == quality.playing
                    val size = option.sizeBytes.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }
                    val supporting = when {
                        option.isOriginal && size != null ->
                            stringResource(Res.string.media_quality_with_size, stringResource(Res.string.media_quality_original), size)
                        option.isOriginal -> stringResource(Res.string.media_quality_original)
                        else -> size
                    }
                    QualityRow(
                        headline = qualityLabel(option),
                        supporting = supporting,
                        checked = checked,
                        onClick = { onChoose(if (option.isOriginal) VideoQualityChoice.Original else VideoQualityChoice.Lines(option.lines)) },
                        modifier = if (checked) Modifier.focusRequester(chosen) else Modifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun QualityRow(headline: String, supporting: String?, checked: Boolean, onClick: () -> Unit, modifier: Modifier) {
    ListItem(
        selected = checked,
        onClick = onClick,
        modifier = modifier,
        headlineContent = { Text(headline) },
        supportingContent = supporting?.let { { Text(it) } },
        trailingContent = if (checked) {
            { Icon(painterResource(Res.drawable.check_24dp), contentDescription = null, modifier = Modifier.size(24.dp)) }
        } else {
            null
        },
    )
}
