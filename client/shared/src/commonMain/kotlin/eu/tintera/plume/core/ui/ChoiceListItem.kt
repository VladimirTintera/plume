package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.check_24dp
import org.jetbrains.compose.resources.painterResource

/**
 * **Řádek výběru jedné hodnoty v bloku zaoblených řádků** (doba automatického mazání, zvuk
 * upozornění, sada samolepek skupiny, diskuse kanálu). Vybraný se z bloku vyčlení plochou
 * `secondaryContainer`, zaoblením skupiny a fajfkou na konci — stejně jako vybraná země.
 *
 * Dřív to byly holé `ListItem`y s kolečkem vlevo, jediné místo v aplikaci, kde výběr vypadal
 * jako M2 seznam. Fajfka nese výběr i bez barvy (barvoslepost, paleta se zvýšeným kontrastem);
 * čtečka obrazovky ho čte z `selectable` s rolí přepínače. [actions] jsou tlačítka řádku
 * (přehrát, smazat tón) a stojí před fajfkou.
 *
 * Pozici v bloku ([index], [count]) zná volající — řádky bývají ve sloupci s `if`.
 */
@Composable
fun ChoiceListItem(
    label: String,
    selected: Boolean,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
    edge: Dp = 16.dp,
) {
    val colors = MaterialTheme.colorScheme
    // Popředí vybraného se předává i ListItemu — sám by barvil `onSurface`, a to je na světlém
    // `secondaryContainer` palety se zvýšeným kontrastem nečitelné (viz výběr země).
    val content = if (selected) colors.onSecondaryContainer else colors.onSurface
    val secondary = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant
    ListItem(
        headlineContent = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = leading,
        trailingContent = if (selected || actions != null) {
            {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    actions?.invoke()
                    if (selected) Icon(painterResource(Res.drawable.check_24dp), contentDescription = null)
                }
            }
        } else {
            null
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent,
            headlineColor = content,
            supportingColor = secondary,
            leadingIconColor = content,
            trailingIconColor = content,
        ),
        modifier = modifier
            .groupedItem(
                index = index,
                count = count,
                color = if (selected) colors.secondaryContainer else colors.groupRow,
                selected = selected,
                edge = edge,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    )
}
