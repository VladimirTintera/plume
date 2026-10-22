package eu.tintera.plume.features.media

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.adaptive.ContentWidth
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.WithTooltip
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.flag_24dp
import eu.tintera.plume.resources.info_24dp
import eu.tintera.plume.resources.more_vert_24dp
import eu.tintera.plume.resources.sponsored_bar_ad
import eu.tintera.plume.resources.sponsored_close
import eu.tintera.plume.resources.sponsored_info
import eu.tintera.plume.resources.sponsored_menu
import eu.tintera.plume.resources.sponsored_report
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Reklama nad videem na celé obrazovce — „malý rámeček nad přehrávačem" ze
 * [specifikace](https://core.telegram.org/api/sponsored-messages).
 *
 * - **Text celý, neořezaný**: zhlédnutí se hlásí, jakmile se rámeček ukáže, a specifikace ho chce
 *   až s celým textem na obrazovce. Reklamy ve videu jsou krátké, jen text.
 * - **Ťuknutí otevře inzerenta** ([onOpen] nahlásí kliknutí). ✕ přijde, až uplyne minimum
 *   (`minDisplayDuration`), dřív zavřít nejde.
 * - **„Informace o sponzorovi" v ⋮**, jen když nějaké jsou (kdo platí, proč se reklama ukazuje) —
 *   nad videem by dlouhý text zakryl obraz. Nahlásit jen tam, kde to TDLib dovolí.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun VideoAdBox(
    shown: ViewerVideoAd,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onReport: () -> Unit,
    /** Nabídka nebo informace o sponzorovi jsou otevřené — reklama se do zavření nesmí skrýt. */
    onHeld: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ad = shown.ad
    var infoOpen by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val currentHeld by rememberUpdatedState(onHeld)
    LaunchedEffect(infoOpen, menuOpen) { currentHeld(infoOpen || menuOpen) }
    DisposableEffect(Unit) { onDispose { currentHeld(false) } }
    val info = listOf(ad.sponsorInfo, ad.additionalInfo).filter { it.isNotBlank() }.joinToString("\n\n")

    Surface(
        onClick = onOpen,
        shape = ShapeDefaults.Large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.widthIn(max = ContentWidth.Bubble),
    ) {
        Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)) {
            ad.photo?.let { photo ->
                RemoteImage(
                    fileId = photo.thumbnailFileId ?: photo.fileId,
                    modifier = Modifier.padding(top = 4.dp, end = 12.dp).size(PHOTO_SIZE).clip(CircleShape),
                )
            }
            Column(Modifier.weight(1f).padding(top = 4.dp, bottom = 4.dp)) {
                Text(
                    stringResource(Res.string.sponsored_bar_ad),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (ad.title.isNotBlank()) Text(ad.title, style = MaterialTheme.typography.titleSmall)
                if (ad.text.isNotBlank()) {
                    Text(ad.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (info.isNotEmpty() || ad.canBeReported) {
                VideoAdMenu(
                    open = menuOpen,
                    onOpenChange = { menuOpen = it },
                    onInfo = { infoOpen = true }.takeIf { info.isNotEmpty() },
                    onReport = onReport.takeIf { ad.canBeReported },
                )
            }
            /*
             * ✕ má místo od začátku, jen je do minima neviditelné. Dokud chybělo, stál na jeho místě
             * ⋮ a s příchodem ✕ uskočil — ťuknutí na ⋮ pak trefilo reklamu a otevřelo inzerenta
             * (ověřeno na emulátoru). Neviditelné tlačítko ťuknutí spolkne a nic neudělá, takže se
             * nepočítá ani jako kliknutí na reklamu.
             */
            val closeAlpha by animateFloatAsState(
                targetValue = if (shown.canClose) 1f else 0f,
                animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                label = "video-ad-close",
            )
            val label = stringResource(Res.string.sponsored_close)
            IconButton(
                onClick = { if (shown.canClose) onClose() },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier
                    .graphicsLayer { alpha = closeAlpha }
                    .then(if (shown.canClose) Modifier else Modifier.clearAndSetSemantics {}),
            ) {
                Icon(painterResource(Res.drawable.close_24dp), contentDescription = label)
            }
        }
    }

    if (infoOpen) {
        AlertDialog(
            onDismissRequest = { infoOpen = false },
            title = { Text(stringResource(Res.string.sponsored_info)) },
            text = { Text(info) },
            confirmButton = {
                TextButton(onClick = { infoOpen = false }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.common_close))
                }
            },
        )
    }
}

@Composable
private fun VideoAdMenu(open: Boolean, onOpenChange: (Boolean) -> Unit, onInfo: (() -> Unit)?, onReport: (() -> Unit)?) {
    val label = stringResource(Res.string.sponsored_menu)
    Box(contentAlignment = Alignment.Center) {
        WithTooltip(label) {
            IconButton(onClick = { onOpenChange(true) }, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(Res.drawable.more_vert_24dp), contentDescription = label)
            }
        }
        ExpressiveMenu(
            expanded = open,
            onDismiss = { onOpenChange(false) },
            groups = listOf(
                listOfNotNull(
                    onInfo?.let { show ->
                        MenuAction(label = stringResource(Res.string.sponsored_info), icon = Res.drawable.info_24dp) {
                            onOpenChange(false)
                            show()
                        }
                    },
                    onReport?.let { report ->
                        MenuAction(label = stringResource(Res.string.sponsored_report), icon = Res.drawable.flag_24dp) {
                            onOpenChange(false)
                            report()
                        }
                    },
                ),
            ),
        )
    }
}

private val PHOTO_SIZE = 40.dp
