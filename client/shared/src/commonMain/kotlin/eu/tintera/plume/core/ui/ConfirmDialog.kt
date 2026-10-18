package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.unit.dp
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
 * - **[content]** pod větou nese volbu, bez které se akce nedá potvrdit: „smazat i pro ostatní",
 *   délku vyhození, heslo k předání vlastnictví, chaty k opuštění se složkou. Obsah se posouvá,
 *   když je dlouhý; volba pod textem patří k otázce, proto není dialog jiného druhu.
 *   Zaškrtávací volba je [DialogCheckRow].
 * - **[iconContent]** místo [icon], když to, čeho se akce týká, nese obrázek (fotka kontaktu
 *   při jeho posílání). Má přednost před [icon].
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
    /** Zrušit jde, i když potvrzení zrovna běží? `false` drží dialog otevřený do konce akce. */
    dismissEnabled: Boolean = true,
    iconContent: (@Composable () -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val accent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = iconContent ?: icon?.let { { Icon(painterResource(it), contentDescription = null, tint = accent) } },
        title = { Text(title) },
        text = if (content == null) {
            text?.let { { Text(it) } }
        } else {
            {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                ) {
                    text?.let { Text(it) }
                    content()
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = confirmEnabled,
                colors = ButtonDefaults.textButtonColors(contentColor = accent),
                shapes = ButtonDefaults.shapes(),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = dismissEnabled, shapes = ButtonDefaults.shapes()) { Text(dismissLabel) }
        },
    )
}

/**
 * Zaškrtávací volba v [ConfirmDialog] („připnout i pro ostatní", „povolit botovi psát").
 * Ťuká se na celý řádek, ne jen na čtvereček; [trailing] nese údaj k volbě (velikost kategorie
 * při mazání mezipaměti).
 */
@Composable
fun DialogCheckRow(
    label: String,
    checked: Boolean,
    trailing: (@Composable () -> Unit)? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}
