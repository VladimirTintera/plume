package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.DropdownMenuPopupPositionProvider
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Položka [ExpressiveMenu]. [destructive] = nevratná akce, kreslí se v chybové barvě.
 *
 * [trailing] je na konci řádku (odznak s počtem) — je to STAV toho, kam položka vede, ne další
 * akce; klikání nese celý řádek.
 */
class MenuAction(
    val label: String,
    val icon: DrawableResource? = null,
    val destructive: Boolean = false,
    val trailing: (@Composable () -> Unit)? = null,
    val onClick: () -> Unit,
)

/**
 * Nabídka v podobě M3 Expressive: položky ve skupinách (`DropdownMenuGroup`) se společným
 * zaoblením krajních položek, mezi skupinami mezera.
 *
 * **Skupiny nesou význam**: co k sobě patří, je v jedné, a nevratná akce (smazat, opustit) má
 * skupinu vlastní — oddělená a v chybové barvě se nesplete s častými akcemi nad ní. Stejný
 * jazyk jako bloky v seznamu chatů: krajní položka nese velké zaoblení.
 *
 * Prázdné skupiny se zahazují, takže volající může skupinu poskládat z podmíněných položek
 * a nemusí hlídat, jestli v ní něco zbylo. Nabídka zavře sebe sama před akcí ([onDismiss]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    groups: List<List<MenuAction>>,
    popupPositionProvider: DropdownMenuPopupPositionProvider =
        MenuDefaults.rememberDropdownMenuPopupPositionProvider(MenuAnchorPosition.Below),
) {
    val shown = groups.filter { it.isNotEmpty() }
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss, popupPositionProvider = popupPositionProvider) {
        shown.forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) Spacer(Modifier.height(MenuDefaults.GroupSpacing))
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(groupIndex, shown.size)) {
                group.forEachIndexed { index, action ->
                    val colors = if (action.destructive) {
                        MenuDefaults.itemColors(
                            textColor = MaterialTheme.colorScheme.error,
                            leadingIconColor = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        MenuDefaults.itemColors()
                    }
                    DropdownMenuItem(
                        onClick = {
                            onDismiss()
                            action.onClick()
                        },
                        text = { Text(action.label) },
                        shape = MenuDefaults.itemShape(index, group.size).shape,
                        leadingIcon = action.icon?.let { icon -> { Icon(painterResource(icon), contentDescription = null) } },
                        trailingIcon = action.trailing,
                        colors = colors,
                    )
                }
            }
        }
    }
}
