package eu.tintera.plume.features.profile

import eu.tintera.plume.core.ui.rememberExpandedSheetState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.durationText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.auto_delete_explanation
import eu.tintera.plume.resources.auto_delete_explanation_secret
import eu.tintera.plume.resources.auto_delete_off
import eu.tintera.plume.resources.auto_delete_title
import org.jetbrains.compose.resources.stringResource

/**
 * Výběr doby automatického mazání: vypnuto, den, týden, měsíc — tytéž doby jako oficiální
 * klienti. Když chat nastavil jinou dobu (jiný klient, bot), je ve výběru taky, aby bylo vidět,
 * co platí.
 *
 * Věta nahoře říká následek: týká se všech v chatu a jen NOVÝCH zpráv. Bez ní by šlo čekat, že
 * se smaže i to, co už v chatu je.
 */
@Composable
internal fun AutoDeleteSheet(
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    /** Tajný chat: časovač po přečtení v sekundách až týdnech, ne mazání po dnech. */
    secret: Boolean = false,
    /** Věta s následkem; výchozí mluví o tomhle chatu, v Nastavení o nových chatech. */
    explanation: String = stringResource(
        if (secret) Res.string.auto_delete_explanation_secret else Res.string.auto_delete_explanation,
    ),
) {
    val sheetState = rememberExpandedSheetState()
    val options = autoDeleteOptions(current, secret)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = stringResource(Res.string.auto_delete_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        Text(
            text = explanation,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Column(Modifier.selectableGroup().padding(bottom = 16.dp)) {
            options.forEach { seconds ->
                val selected = seconds == current
                ListItem(
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    leadingContent = { RadioButton(selected = selected, onClick = null) },
                    headlineContent = {
                        Text(if (seconds == 0) stringResource(Res.string.auto_delete_off) else durationText(seconds))
                    },
                    // Řádek nese klikání celý, kolečko má `onClick = null` — viz „Obrazovka nastavení".
                    modifier = Modifier.selectable(selected = selected, role = Role.RadioButton) { onSelect(seconds) },
                )
            }
        }
    }
}

/** Nabízené doby; jiná, právě platná doba se zařadí mezi ně, aby bylo vidět, co je nastavené. */
internal fun autoDeleteOptions(current: Int, secret: Boolean = false): List<Int> =
    ((if (secret) SECRET_OPTIONS else AUTO_DELETE_OPTIONS) + current.takeIf { it > 0 }).filterNotNull().distinct().sorted()

private const val DAY = 24 * 60 * 60

/** Vypnuto, den, týden a měsíc (31 dní, jak ho posílá Telegram) — jako oficiální klienti. */
private val AUTO_DELETE_OPTIONS = listOf(0, DAY, 7 * DAY, 31 * DAY)

/**
 * Tajný chat: časovač běží od PŘEČTENÍ a TDLib bere libovolné sekundy. Výběr jako oficiální
 * klienti, jen zúžený — patnáct položek po sekundě by se do listu nevešlo.
 */
private val SECRET_OPTIONS = listOf(0, 10, 30, 60, 60 * 60, DAY, 7 * DAY)
