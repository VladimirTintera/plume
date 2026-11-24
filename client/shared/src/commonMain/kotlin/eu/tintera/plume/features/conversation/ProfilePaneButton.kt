package eu.tintera.plume.features.conversation

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import eu.tintera.plume.core.adaptive.LocalPaneNeighbours
import eu.tintera.plume.core.ui.KeyboardShortcut
import eu.tintera.plume.core.ui.KeyboardShortcuts
import eu.tintera.plume.core.ui.LocalPanesSideBySide
import eu.tintera.plume.core.ui.WithTooltip
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.profile_pane_open
import eu.tintera.plume.resources.right_panel_open_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Otevře panel s profilem vedle konverzace — protějšek „Zavřít panel" v profilu a zrcadlo
 * přepínače seznamu chatů vlevo. Profil vedle konverzace je boční panel: přepnutí chatu ho
 * nezavře, jen ukáže profil nového chatu (`followProfile`). Ťuknutí na název v hlavičce dělá
 * totéž, jenže to z ničeho nepoznáš.
 *
 * - **Jen s panely vedle sebe a jen se zavřeným panelem.** S otevřeným by vedle sebe stála dvě
 *   tlačítka pro zavření; na telefonu je profil celá obrazovka a vede do něj hlavička.
 * - **Zkratka Ctrl+Shift+I / ⌥⌘I panel přepíná oběma směry** ([onToggleProfile] je
 *   `toggleProfile`: otevřený třetí panel zavře, i kalendář). Poslouchá ji hlavička, protože
 *   ví, jestli chat profil má; shell by musel rozlišovat druhy konverzací a archiv.
 */
@Composable
internal fun ProfilePaneButton(
    /** Chat profil má (ne Uložené zprávy, ne vlákno komentářů). */
    hasProfile: Boolean,
    onToggleProfile: () -> Unit,
) {
    val sideBySide = LocalPanesSideBySide.current
    val shortcuts = koinInject<KeyboardShortcuts>()
    val currentToggle by rememberUpdatedState(onToggleProfile)
    val enabled by rememberUpdatedState(sideBySide && hasProfile)
    LaunchedEffect(shortcuts) {
        shortcuts.shortcuts.collect { shortcut ->
            if (shortcut == KeyboardShortcut.ToggleProfilePane && enabled) currentToggle()
        }
    }

    if (!sideBySide || !hasProfile || LocalPaneNeighbours.current.extraBeside) return
    val label = stringResource(Res.string.profile_pane_open)
    WithTooltip(label = label, position = TooltipAnchorPosition.Below) {
        IconButton(onClick = onToggleProfile, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(Res.drawable.right_panel_open_24dp), contentDescription = label)
        }
    }
}
