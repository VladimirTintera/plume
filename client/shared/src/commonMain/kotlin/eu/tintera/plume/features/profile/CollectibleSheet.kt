package eu.tintera.plume.features.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.CollectibleInfo
import eu.tintera.plume.core.telegram.CollectibleItem
import eu.tintera.plume.core.telegram.asUiText
import eu.tintera.plume.core.telegram.cryptoAmountParts
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.asDate
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.collectible_failed
import eu.tintera.plume.resources.collectible_open
import eu.tintera.plume.resources.collectible_purchased
import eu.tintera.plume.resources.collectible_title
import eu.tintera.plume.resources.decimal_number
import eu.tintera.plume.resources.product_price
import eu.tintera.plume.resources.profile_username_value
import org.jetbrains.compose.resources.stringResource

/**
 * Sběratelské jméno nebo číslo +888: kdy a za kolik se koupilo na Fragmentu, a odkaz tam.
 * Jako v oficiálním klientovi po ťuknutí na jméno; víc sběratelských jmen v jednom řádku má
 * každé svůj blok.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CollectibleInfoSheet(sheet: CollectibleSheet, onOpenUrl: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetTitle(stringResource(Res.string.collectible_title))
            sheet.items.forEach { item ->
                CollectibleBlock(
                    item = item,
                    info = sheet.infos[item],
                    failed = item in sheet.failed,
                    onOpenUrl = onOpenUrl,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CollectibleBlock(item: CollectibleItem, info: CollectibleInfo?, failed: Boolean, onOpenUrl: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            when (item) {
                is CollectibleItem.Username -> stringResource(Res.string.profile_username_value, item.username)
                is CollectibleItem.Phone -> item.phone
            },
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
        when {
            info != null -> {
                val crypto = cryptoAmountParts(info.cryptocurrencyAmount, info.cryptocurrency)
                val cryptoNumber = crypto.fraction?.let { stringResource(Res.string.decimal_number, crypto.whole.grouped(), it) }
                    ?: crypto.whole.grouped()
                Text(
                    stringResource(
                        Res.string.collectible_purchased,
                        info.purchaseDate.asDate(),
                        stringResource(Res.string.product_price, cryptoNumber, info.cryptocurrency),
                        info.price.asUiText().resolve(),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = { onOpenUrl(info.url) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.collectible_open))
                }
            }
            failed -> Text(
                stringResource(Res.string.collectible_failed),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            else -> LoadingIndicator()
        }
    }
}
