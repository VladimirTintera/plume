package eu.tintera.plume.core.ui

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.common_more_options
import eu.tintera.plume.resources.more_vert_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Jedno tlačítko [ActionButtonGroup]. [button] dostane modifikátor a zdroj interakcí, které MUSÍ
 * předat svému tlačítku — podle nich skupina pozná stisk. Popisek patří do [GroupActionText]. Vzhled (`TextButton`, `Button`,
 * barvy karty) si nese volající. [label], [onClick] a [enabled] jsou pro položku nabídky, kam
 * tlačítko spadne, když se řada nevejde (velké písmo, úzký displej).
 */
class GroupAction(
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    /** Podíl na šířce řady, jako `Modifier.weight`; `NaN` = přirozená šířka. */
    val weight: Float = Float.NaN,
    /**
     * Kolik se tlačítko smí smrsknout, když se stiskne soused — jeho vodorovné odsazení
     * (výchozí `Button`; `TextButton` předává `ButtonDefaults.TextButtonContentPadding`, má ho
     * poloviční). Popisek se pak na okamžik stisku vejde jen přes okraj ([GroupActionText]).
     */
    val compressionLimit: PaddingValues = ButtonDefaults.ContentPadding,
    val button: @Composable (modifier: Modifier, interactionSource: MutableInteractionSource) -> Unit,
)

/**
 * **Tlačítka vedle sebe jako M3 Expressive `ButtonGroup`**: stisknuté se roztáhne a sousedé
 * uhnou, takže dvojice „Teď ne / Aktualizovat" reaguje jako jeden prvek, ne jako dvě nezávislá
 * tlačítka v `Row`. Co se nevejde, spadne do nabídky za třemi tečkami.
 *
 * Vlastní obal, ne holé `ButtonGroup`: výchozí ukazatel přetečení
 * (`ButtonGroupDefaults.OverflowIndicator`) má holý `TooltipBox`, který na desktopu shazuje
 * vykreslování (viz `WithTooltip`), a `clickableItem` neumí barvy karty.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ActionButtonGroup(
    actions: List<GroupAction>,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(8.dp, Alignment.End),
) {
    val moreLabel = stringResource(Res.string.common_more_options)
    ButtonGroup(
        overflowIndicator = { menu ->
            WithTooltip(moreLabel) {
                IconButton(
                    onClick = { if (menu.isShowing) menu.dismiss() else menu.show() },
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(painterResource(Res.drawable.more_vert_24dp), contentDescription = moreLabel)
                }
            }
        },
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEach { action ->
            customItem(
                buttonGroupContent = {
                    val source = remember { MutableInteractionSource() }
                    val weighted = if (action.weight.isNaN()) Modifier else Modifier.weight(action.weight)
                    action.button(weighted.animateWidth(source, action.compressionLimit), source)
                },
                menuContent = { menu ->
                    DropdownMenuItem(
                        text = { Text(action.label) },
                        onClick = {
                            menu.dismiss()
                            action.onClick()
                        },
                        enabled = action.enabled,
                    )
                },
            )
        }
    }
}

/**
 * Popisek tlačítka v [ActionButtonGroup]. Když se stiskne soused, tlačítko se smrskne o svoje
 * odsazení a popisek by se zalomil na dva řádky — řada by poskočila (ověřeno v úpravě fotky,
 * „Celá fotka" při stisku „Otočit"). Proto jeden řádek, který smí na okamžik stisku přesáhnout
 * do okraje tlačítka; tak to dělá i `ButtonGroup.clickableItem` v material3.
 */
@Composable
fun GroupActionText(text: String, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    Text(text, modifier = modifier, color = color, maxLines = 1, softWrap = false, overflow = TextOverflow.Visible)
}
