package eu.tintera.plume.core.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.remember
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.DropdownMenuPopupPositionProvider
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorPosition
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.MenuGroupShapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
                DropdownMenuGroup(shapes = menuGroupShape(groupIndex, shown.size)) {
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
    .padding(vertical = MenuShadowRoom)

/**
 * Místo pro stín skupin nahoře a dole UVNITŘ rolovaného obsahu. `verticalScroll` ořezává obsah ve
 * směru rolování (do strany ne), takže stín horní skupiny nahoře a spodní skupiny dole byl
 * useknutý rovnou hranou — po stranách stín byl, nahoře a dole ne. Nejvíc to bylo vidět na
 * desktopu. Skupina má stín úrovně 2 (`MenuDefaults.ShadowElevation`, 3 dp) a ten pod ní sahá
 * o pár bodů; nabídka je tím o tolik dál od kotvy, což nevadí.
 */
private val MenuShadowRoom = 6.dp

/**
 * Tvar skupiny nabídky: [MenuDefaults.groupShape] BEZ přechodu do „neaktivního" tvaru.
 *
 * M3 Expressive skupině, nad kterou myš byla a odjela, mění tvar na `inactiveShape` (všechny rohy
 * malé, `SegmentedMenuTokens.InactiveContainerShape`); skupina, nad kterou myš nikdy nebyla,
 * zůstává v tvaru podle pořadí (krajní rohy velké). Na desktopu se tak spodní skupina při každém
 * přejetí myší zaoblila a po odjetí zhranatěla, zatímco horní (myš do ní z kotvy často
 * nevjede) zůstávala kulatá — vypadalo to jako chyba. Na dotykovém displeji hover není a tvar se
 * nemění nikdy; tady platí totéž i pro myš.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun menuGroupShape(index: Int, count: Int): MenuGroupShapes =
    MenuDefaults.groupShape(index, count).let { it.copy(inactiveShape = it.shape) }

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
