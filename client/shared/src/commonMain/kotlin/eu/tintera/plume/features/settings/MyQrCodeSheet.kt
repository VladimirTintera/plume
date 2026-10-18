package eu.tintera.plume.features.settings

import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.MediumButtonIcon
import eu.tintera.plume.core.ui.ButtonEmphasis
import eu.tintera.plume.core.ui.MediumButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.QrCode
import eu.tintera.plume.core.ui.QrScanFailure
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.core.ui.qrScannerSupported
import eu.tintera.plume.core.ui.rememberQrScanner
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.common_try_again
import eu.tintera.plume.resources.content_copy_24dp
import eu.tintera.plume.resources.devices_qr_permission
import eu.tintera.plume.resources.devices_qr_unavailable
import eu.tintera.plume.resources.my_qr_copied
import eu.tintera.plume.resources.my_qr_copy
import eu.tintera.plume.resources.my_qr_description
import eu.tintera.plume.resources.my_qr_expires
import eu.tintera.plume.resources.my_qr_failed
import eu.tintera.plume.resources.my_qr_hint
import eu.tintera.plume.resources.my_qr_permanent
import eu.tintera.plume.resources.my_qr_scan
import eu.tintera.plume.resources.my_qr_share
import eu.tintera.plume.resources.qr_code_scanner_24dp
import eu.tintera.plume.resources.share_24dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * „Můj QR kód": kód s odkazem na vlastní účet, který druhý naskenuje fotoaparátem nebo
 * v Telegramu a otevře se mu chat se mnou ([MyQrCodeViewModel]). Pod kódem sdílení a kopírování
 * odkazu a čtečka kódu od druhého — výměna tak jde oběma směry z jednoho místa.
 *
 * **Kód je vždycky černý na bílé**, i v tmavém motivu ([QrCode]): inverzní kód spousta čteček
 * nepřečte.
 */
@Composable
fun MyQrCodeSheet(onDismiss: () -> Unit, viewModel: MyQrCodeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val sheetState = rememberExpandedSheetState()

    // Odkaz se načítá a obnovuje jen, dokud je sheet otevřený; nový pokus mění klíč.
    LaunchedEffect(state.attempt) { viewModel.keepFresh() }

    val startScan = rememberQrScanner(
        onScanned = { content -> if (viewModel.onScanned(content)) onDismiss() },
        onFailure = { failure ->
            viewModel.showMessage(
                UiText(
                    when (failure) {
                        QrScanFailure.Unavailable -> Res.string.devices_qr_unavailable
                        QrScanFailure.PermissionDenied -> Res.string.devices_qr_permission
                    },
                ),
            )
        },
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            account?.let { me ->
                Avatar(chatId = me.id, title = me.displayName, avatarFileId = me.photoFileId, size = 72.dp)
                Text(
                    text = me.displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
                me.username?.let { username ->
                    Text(
                        "@$username",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(vertical = 8.dp).widthIn(max = QR_MAX_SIZE).fillMaxWidth(QR_WIDTH).aspectRatio(1f),
            ) {
                val link = state.link
                when {
                    state.failed -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(Res.string.my_qr_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        TextButton(onClick = viewModel::retry, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.common_try_again)) }
                    }
                    link != null -> QrCode(
                        content = link.url,
                        contentDescription = stringResource(Res.string.my_qr_description),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    else -> LoadingIndicator()
                }
            }

            Text(
                stringResource(Res.string.my_qr_hint),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            val expiresAt = state.expiresAt
            if (!state.failed && state.link != null) {
                Text(
                    text = if (expiresAt != null) {
                        stringResource(Res.string.my_qr_expires, expiresAt.toInt().asTimeOfDay())
                    } else {
                        stringResource(Res.string.my_qr_permanent)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            val ready = state.link != null && !state.failed
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                OutlinedButton(onClick = viewModel::share, enabled = ready, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) {
                    ButtonIcon(Res.drawable.share_24dp)
                    Text(stringResource(Res.string.my_qr_share))
                }
                // Zkopírováno nese tlačítko samo (fajfka), ne hláška — je to stav ovládacího prvku.
                OutlinedButton(onClick = viewModel::copy, enabled = ready, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) {
                    ButtonIcon(if (state.copied) Res.drawable.check_24dp else Res.drawable.content_copy_24dp)
                    Text(stringResource(if (state.copied) Res.string.my_qr_copied else Res.string.my_qr_copy))
                }
            }
            if (qrScannerSupported) {
                MediumButton(onClick = startScan, modifier = Modifier.fillMaxWidth(), emphasis = ButtonEmphasis.Tonal) {
                    MediumButtonIcon(Res.drawable.qr_code_scanner_24dp)
                    Text(stringResource(Res.string.my_qr_scan))
                }
            }

            state.message?.let { message ->
                Text(
                    message.resolve(),
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

/** Kód přes většinu šířky sheetu, na tabletu ne přes celou. */
private const val QR_WIDTH = 0.8f
private val QR_MAX_SIZE = 320.dp
