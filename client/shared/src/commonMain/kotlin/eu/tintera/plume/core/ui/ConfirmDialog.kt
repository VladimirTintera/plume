package eu.tintera.plume.core.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.common_cancel
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * **Potvrzení akce**: ikona, nadpis s otázkou, věta s následkem a dvě tlačítka.
 *
 * Dřív si ho psal každý soubor sám (40 destruktivních potvrzení, ikonu měla dvě) a nevratná akce
 * se poznala jen podle červeného textu tlačítka. M3 dialog s ikonou ji vystředí nad nadpis
 * a nadpis vystředí taky — co se stane, je tak vidět dřív, než se dočte věta.
 *
 * - **[destructive]**: ikona i potvrzení v chybové barvě. Tlačítka zůstávají textová, jak je
 *   M3 (i Expressive) u dialogů má; barvu nese [ButtonDefaults.textButtonColors], ne `Text`,
 *   takže sedí i vlnka po ťuknutí.
 * - **Ikona říká, CO se stane** (koš, odchod, blokování), ne „pozor" — varovný trojúhelník
 *   u každého smazání by přestal cokoli znamenat.
 * - Složitější dialogy (volba „smazat i pro ostatní", pole s heslem) zůstávají `AlertDialog`;
 *   tenhle je pro otázku s jednou odpovědí.
 */
@Composable
fun ConfirmDialog(
    title: String,
    text: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    icon: DrawableResource? = null,
    destructive: Boolean = false,
    confirmEnabled: Boolean = true,
    dismissLabel: String = stringResource(Res.string.common_cancel),
) {
    val accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { Icon(painterResource(it), contentDescription = null, tint = accent) } },
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmEnabled,
                colors = ButtonDefaults.textButtonColors(contentColor = accent),
                shapes = ButtonDefaults.shapes(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(dismissLabel) }
        },
    )
}
