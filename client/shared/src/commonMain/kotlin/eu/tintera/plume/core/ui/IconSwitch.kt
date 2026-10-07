package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.close_24dp
import org.jetbrains.compose.resources.painterResource

/**
 * Přepínač s ikonou v jezdci: fajfka zapnutý, křížek vypnutý. Stav tak nese i tvar, ne jen
 * barva — pozná ho i barvoslepý a v paletě se zvýšeným kontrastem, kde barvy stop splývají.
 *
 * Řádek nastavení nese klikání celý (`toggleable(role = Switch)`), proto výchozí
 * `onCheckedChange = null`. Ikona je dekorace: stav čte čtečka obrazovky z řádku.
 */
@Composable
fun IconSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        // Barvu ikony podává přepínač sám (`checkedIconColor` / `uncheckedIconColor`).
        thumbContent = {
            Icon(
                painterResource(if (checked) Res.drawable.check_24dp else Res.drawable.close_24dp),
                contentDescription = null,
                modifier = Modifier.size(SwitchDefaults.IconSize),
            )
        },
    )
}
