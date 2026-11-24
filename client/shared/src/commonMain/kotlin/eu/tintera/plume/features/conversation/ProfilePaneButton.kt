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
import eu.tintera.plume.resources.right_panel_close_24dp
import eu.tintera.plume.resources.right_panel_open_24dp
import eu.tintera.plume.resources.side_pane_close
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Přepínač pravého panelu v hlavičce konverzace a seznamu témat: otevře profil vedle, a když
 * je panel otevřený (profil, kalendář zpráv, co se z nich otevřelo), zavře ho. Zrcadlo přepínače
 * seznamu chatů vlevo. Profil i kalendář vedle konverzace jsou boční panel: přepnutí chatu je
 * nezavře, jen ukážou nový chat (`followProfile`). Ťuknutí na název v hlavičce dělá totéž.
 *
 * - **Tlačítko stojí pořád na jednom místě, v hlavičce vedle, ne v panelu.** Kořen profilu
 *   a kalendář proto zavírací ikonu nemají; panel nad panelem (profil člena otevřený z profilu
 *   skupiny) má šipku zpět, ta vrací v rámci panelu.
 * - **Jen s panely vedle sebe.** Na telefonu je profil celá obrazovka a vede do něj hlavička.
 * - **Otevřít jde jen u chatu s profilem** ([hasProfile]), zavřít vždycky: kalendář stojí
 *   i vedle Uložených zpráv a profil chatu i vedle vlákna komentářů, kam ho přenese přepnutí.
 * - **Zkratka Ctrl+Shift+I / ⌥⌘I dělá totéž** ([onToggleProfile] je `toggleProfile`: otevřený
 *   třetí panel zavře, zavřený otevře profilem). Poslouchá ji hlavička, protože ví, jestli chat
 *   profil má; shell by musel rozlišovat druhy konverzací a archiv.
 */
@Composable
internal fun ProfilePaneButton(
    /** Chat profil má (ne Uložené zprávy, ne vlákno komentářů). */
    hasProfile: Boolean,
    onToggleProfile: () -> Unit,
) {
    val sideBySide = LocalPanesSideBySide.current
    val paneOpen = LocalPaneNeighbours.current.extraBeside
    val usable = sideBySide && (paneOpen || hasProfile)
    val shortcuts = koinInject<KeyboardShortcuts>()
    val currentToggle by rememberUpdatedState(onToggleProfile)
    val enabled by rememberUpdatedState(usable)
    LaunchedEffect(shortcuts) {
        shortcuts.shortcuts.collect { shortcut ->
            if (shortcut == KeyboardShortcut.ToggleProfilePane && enabled) currentToggle()
        }
    }

    if (!usable) return
    val label = stringResource(if (paneOpen) Res.string.side_pane_close else Res.string.profile_pane_open)
    WithTooltip(label = label, position = TooltipAnchorPosition.Below) {
        IconButton(onClick = onToggleProfile, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painterResource(if (paneOpen) Res.drawable.right_panel_close_24dp else Res.drawable.right_panel_open_24dp),
                contentDescription = label,
            )
        }
    }
}
