package eu.tintera.plume.core.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialExpressiveTheme
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
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Položka [ExpressiveMenu]. [destructive] = nevratná akce, kreslí se v chybové barvě.
 *
 * [trailing] je na konci řádku (odznak s počtem) — je to STAV toho, kam položka vede, ne další
 * akce; klikání nese celý řádek.
 *
 * [supporting] je drobný text pod popiskem — třeba rychlejší cesta k téže akci („podržte šipku
 * zpět"), kterou se uživatel dozví pokaždé, když položku použije.
 */
class MenuAction(
    val label: String,
    val icon: DrawableResource? = null,
    val destructive: Boolean = false,
    val trailing: (@Composable () -> Unit)? = null,
    val supporting: String? = null,
    /** Vlastní obsah místo [icon] (fotka účtu v menu účtu). */
    val leading: (@Composable () -> Unit)? = null,
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
 *
 * Okno nabídky si nebere klávesnici ([MenuPopupProperties]): nabídka nad vstupním polem ji
 * nechá otevřenou a konverzace pod ní neposkočí. Obsah roluje ([menuScroll]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    groups: List<List<MenuAction>>,
    popupPositionProvider: DropdownMenuPopupPositionProvider =
        MenuDefaults.rememberDropdownMenuPopupPositionProvider(MenuAnchorPosition.Below),
    properties: PopupProperties = MenuPopupProperties,
) {
    val shown = groups.filter { it.isNotEmpty() }
    DropdownMenuPopup(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.menuScroll(),
        popupPositionProvider = popupPositionProvider,
        properties = properties,
    ) {
        MenuColors {
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
                            leadingIcon = action.leading ?: action.icon?.let { icon -> { Icon(painterResource(icon), contentDescription = null) } },
                            trailingContent = action.trailing,
                            supportingText = action.supporting?.let { text -> { Text(text) } },
                            colors = colors,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Obsah nabídky roluje, když se nevejde do okna. `DropdownMenuPopup` z material3 Expressive to
 * na rozdíl od starého `DropdownMenu` nedělá sám (1.12: `DropdownMenuPopupContent` je holý
 * `Column`): okno nabídky je nejvýš tak vysoké jako obrazovka a co přeteče, je pod okrajem bez
 * cesty k němu. Telefon naležato má kolem 360 dp, takže z menu účtu zmizelo Nastavení — poslední
 * položka, a jinudy se k němu nejde.
 *
 * Patří do `modifier` každého `DropdownMenuPopup`; hlídá to `verifyMenuScroll`.
 */
@Composable
fun Modifier.menuScroll(): Modifier = verticalScroll(rememberRestoringScrollState())

/**
 * Barvy nabídky: plocha skupin i položek je `surfaceContainerHighest`, ne výchozí
 * `surfaceContainerLow` z M3 Expressive (`StandardMenuTokens`).
 *
 * `surfaceContainerLow` je pod stránkou (`surfaceContainer`, `asListPage`) i pod řádky na ní
 * (`surfaceBright`), takže nabídka v tmavém motivu vypadala jako díra: nad seznamem chatů #121318
 * na řádcích #2A2C34 (API 37). Vrstvení projektu dává nabídce nad plochou `surfaceContainerHighest`
 * (CLAUDE.md, „Vrstvení"). Přemapovává se role, ne barvy po komponentách: skupinu i každou
 * položku kreslí M3 z téhož tokenu a položek jsou desítky.
 *
 * Kdo kreslí `DropdownMenuPopup` sám, obalí jeho obsah taky.
 */
@Composable
fun MenuColors(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val menu = remember(scheme) { scheme.copy(surfaceContainerLow = scheme.surfaceContainerHighest) }
    MaterialExpressiveTheme(colorScheme = menu, content = content)
}
