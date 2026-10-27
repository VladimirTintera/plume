package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.files.MediaSharer
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.content_copy_24dp
import eu.tintera.plume.resources.invite_links_share_failed
import eu.tintera.plume.resources.link_qr_description
import eu.tintera.plume.resources.link_qr_hint
import eu.tintera.plume.resources.my_qr_copied
import eu.tintera.plume.resources.my_qr_copy
import eu.tintera.plume.resources.my_qr_share
import eu.tintera.plume.resources.share_24dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * **QR kód odkazu** na veřejný chat, uživatele nebo pozvánku, se sdílením a kopírováním odkazu.
 * Tentýž jazyk jako „Můj QR kód" (`MyQrCodeSheet`): kdo stojí vedle, naskenuje kód, komu se
 * posílá, dostane odkaz.
 *
 * Odkaz je hotový (veřejné jméno, pozvánka), takže sheet nic nenačítá ani neobnovuje — na rozdíl
 * od vlastního kódu, jehož dočasný token vyprší. Sdílení a kopírování dělá sheet sám
 * ([MediaSharer], [TextClipboard]); volající dodá jen odkaz a čím ho pojmenovat.
 *
 * **Kód je vždycky černý na bílé**, i v tmavém motivu ([QrCode]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkQrSheet(
    link: String,
    title: String,
    onDismiss: () -> Unit,
    /** Fotka nad názvem (chat, skupina); `null` = jen název (pozvánka). */
    avatar: (@Composable () -> Unit)? = null,
) {
    val sharer = koinInject<MediaSharer>()
    val clipboard = koinInject<TextClipboard>()
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    var shareFailed by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            avatar?.invoke()
            // Velký název je tu obsah (komu kód patří), ne hlavička — jako u „Můj QR kód".
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
            // Odkaz pod názvem, jen když název není on sám (pozvánka bez názvu a bez chatu).
            val shownLink = link.withoutScheme()
            if (shownLink != title) {
                Text(
                    shownLink,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(vertical = 8.dp).widthIn(max = QR_MAX_SIZE).fillMaxWidth(QR_WIDTH).aspectRatio(1f),
            ) {
                QrCode(
                    content = link,
                    contentDescription = stringResource(Res.string.link_qr_description, title),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(
                stringResource(Res.string.link_qr_hint),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            val share = stringResource(Res.string.my_qr_share)
            // Zkopírováno nese tlačítko samo (fajfka), ne hláška — je to stav ovládacího prvku.
            val copy = stringResource(if (copied) Res.string.my_qr_copied else Res.string.my_qr_copy)
            val onShare = { shareFailed = !sharer.shareText(link) }
            val onCopy: () -> Unit = {
                scope.launch {
                    clipboard.copy(link)
                    copied = true
                    delay(COPIED_SHOWN_MS)
                    copied = false
                }
            }
            ActionButtonGroup(
                listOf(
                    GroupAction(share, onShare, weight = 1f) { modifier, source ->
                        OutlinedButton(onClick = onShare, modifier = modifier, interactionSource = source, shapes = ButtonDefaults.shapes()) {
                            ButtonIcon(Res.drawable.share_24dp)
                            GroupActionText(share)
                        }
                    },
                    GroupAction(copy, onCopy, weight = 1f) { modifier, source ->
                        OutlinedButton(onClick = onCopy, modifier = modifier, interactionSource = source, shapes = ButtonDefaults.shapes()) {
                            ButtonIcon(if (copied) Res.drawable.check_24dp else Res.drawable.content_copy_24dp)
                            GroupActionText(copy)
                        }
                    },
                ),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            )
            if (shareFailed) {
                Text(
                    stringResource(Res.string.invite_links_share_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ButtonIcon(icon: DrawableResource) {
    Icon(
        painterResource(icon),
        contentDescription = null,
        modifier = Modifier.padding(end = 8.dp).size(ButtonDefaults.IconSize),
    )
}

/** Odkaz pod názvem bez `https://` — je kratší a čte se jako adresa, ne jako kód. */
internal fun String.withoutScheme(): String = substringAfter("://")

/** Kód přes většinu šířky sheetu, na tabletu ne přes celou. */
private const val QR_WIDTH = 0.8f
private val QR_MAX_SIZE = 320.dp

private const val COPIED_SHOWN_MS = 2_000L
