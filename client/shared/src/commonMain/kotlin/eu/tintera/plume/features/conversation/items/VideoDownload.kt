package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.download_24dp
import eu.tintera.plume.resources.media_video_download
import eu.tintera.plume.resources.media_video_download_cancel
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Stažení videa z náhledu v konverzaci ([MessageAction.DownloadVideo]). Poskytuje ho seznam
 * zpráv; `null` = náhled bez stahování (anketa, reklama, článek).
 */
internal class VideoDownload(
    val onDownload: (messageId: Long) -> Unit,
    val onCancel: (messageId: Long) -> Unit,
)

internal val LocalVideoDownload = compositionLocalOf<VideoDownload?> { null }

/**
 * Tlačítko v rohu náhledu videa: šipka stáhne, během stahování vlnitý kruh s průběhem a křížkem
 * stahování zruší. Stažené video tlačítko nemá — to říká fajfka ve štítku s délkou.
 *
 * Tmavé kolečko a bílá jako průběh nahrávání (`UploadIndicator`), čitelné přes jakýkoli snímek.
 * Tvar nese stav (kruh průběhu), proto bez morfování tlačítek; klikání je `clickable`, dlouhý
 * stisk nad ním nabídku otevírat nemusí. Plocha pro prst 48 dp, vidět je 36 dp.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VideoDownloadButton(
    file: FileState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val downloading = file as? FileState.Downloading
    val label = stringResource(if (downloading != null) Res.string.media_video_download_cancel else Res.string.media_video_download)
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = label, onClick = if (downloading != null) onCancel else onDownload),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (downloading != null) Res.drawable.close_24dp else Res.drawable.download_24dp),
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
            if (downloading != null) {
                CircularWavyProgressIndicator(
                    progress = { downloading.progress },
                    color = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
        }
    }
}
