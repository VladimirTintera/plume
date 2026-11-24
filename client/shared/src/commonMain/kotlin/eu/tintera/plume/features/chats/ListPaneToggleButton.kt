package eu.tintera.plume.features.chats

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.tintera.plume.core.adaptive.ListPaneToggle
import eu.tintera.plume.core.ui.WithTooltip
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.left_panel_close_24dp
import eu.tintera.plume.resources.left_panel_open_24dp
import eu.tintera.plume.resources.list_pane_collapse
import eu.tintera.plume.resources.list_pane_expand
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Sbalí seznam chatů na sloupec s avatary, nebo ho rozbalí ([ListPaneToggle]). V plném seznamu
 * stojí na začátku řádku s pilulkou (na Macu hned za semafory, jako přepínač bočního panelu ve
 * Finderu), ve sloupci nahoře nad účtem (jako tlačítko menu v navigačním railu M3).
 *
 * Ikona přeletí mezi oběma místy jako účet, lupa a Vytvořit ([collapseFlight]); bez toho jen
 * přeskočila, zatímco všechno kolem letělo.
 *
 * Ikona a popis podle podoby, ve které tlačítko stojí ([collapsed]), ne podle kotvy: za tahu
 * táhlem se podoba přepne dřív, než seznam na kotvu dosedne, a tlačítko by slibovalo opak.
 */
@Composable
internal fun ListPaneToggleButton(
    toggle: ListPaneToggle,
    collapsed: Boolean,
    tooltipPosition: TooltipAnchorPosition = TooltipAnchorPosition.Below,
) {
    val label = stringResource(if (collapsed) Res.string.list_pane_expand else Res.string.list_pane_collapse)
    WithTooltip(label = label, position = tooltipPosition) {
        IconButton(onClick = toggle.toggle, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painterResource(if (collapsed) Res.drawable.left_panel_open_24dp else Res.drawable.left_panel_close_24dp),
                contentDescription = label,
                modifier = Modifier.collapseFlight("list-toggle"),
            )
        }
    }
}
