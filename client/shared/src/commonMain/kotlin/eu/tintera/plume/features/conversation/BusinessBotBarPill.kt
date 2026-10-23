package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import eu.tintera.plume.core.telegram.BusinessBotBar
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.business_bot_cannot_reply
import eu.tintera.plume.resources.business_bot_manage
import eu.tintera.plume.resources.business_bot_menu
import eu.tintera.plume.resources.business_bot_pause
import eu.tintera.plume.resources.business_bot_paused
import eu.tintera.plume.resources.business_bot_remove
import eu.tintera.plume.resources.business_bot_remove_confirm
import eu.tintera.plume.resources.business_bot_remove_text
import eu.tintera.plume.resources.business_bot_remove_title
import eu.tintera.plume.resources.business_bot_replying
import eu.tintera.plume.resources.business_bot_resume
import eu.tintera.plume.resources.more_vert_24dp
import eu.tintera.plume.resources.pause_24dp
import eu.tintera.plume.resources.person_remove_24dp
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.settings_24dp
import eu.tintera.plume.resources.smart_toy_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Pruh firemního bota (Telegram Business): bot, který za uživatele odpovídá v tomhle chatu.
 * Tatáž pilulka jako ostatní pruhy ([BarPill]); ťuknutí i ⋮ otevřou nabídku — pozastavit
 * nebo obnovit, správa bota (odkaz od serveru) a odebrání z chatu s potvrzením.
 *
 * **Nezavírá se křížkem.** Je to stav chatu, ne oznámení: zmizí, až bot z chatu zmizí. Tak
 * ho vedou i oficiální klienti.
 */
@Composable
internal fun BusinessBotBarPill(
    bar: BusinessBotBar,
    onSetPaused: (Boolean) -> Unit,
    onManage: () -> Unit,
    onRemove: () -> Unit,
    glass: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    if (confirmRemove) {
        ConfirmDialog(
            title = stringResource(Res.string.business_bot_remove_title, bar.botName),
            text = stringResource(Res.string.business_bot_remove_text),
            confirmLabel = stringResource(Res.string.business_bot_remove_confirm),
            icon = Res.drawable.person_remove_24dp,
            destructive = true,
            onConfirm = {
                confirmRemove = false
                onRemove()
            },
            onDismiss = { confirmRemove = false },
        )
    }
    BarPill(
        title = bar.botName,
        subtitle = stringResource(
            when {
                bar.isPaused -> Res.string.business_bot_paused
                !bar.canReply -> Res.string.business_bot_cannot_reply
                else -> Res.string.business_bot_replying
            },
        ),
        onClick = { menuOpen = true },
        glass = glass,
        modifier = modifier,
        // Pozastavený bot šedě — stav je vidět i bez čtení podnadpisu.
        titleColor = if (bar.isPaused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        leading = { BarBadge(Res.drawable.smart_toy_24dp) },
        trailing = {
            Box {
                IconButton(onClick = { menuOpen = true }, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(Res.drawable.more_vert_24dp), contentDescription = stringResource(Res.string.business_bot_menu))
                }
                ExpressiveMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    groups = listOf(
                        listOf(
                            if (bar.isPaused) {
                                MenuAction(stringResource(Res.string.business_bot_resume), Res.drawable.play_arrow_24dp) {
                                    menuOpen = false
                                    onSetPaused(false)
                                }
                            } else {
                                MenuAction(stringResource(Res.string.business_bot_pause), Res.drawable.pause_24dp) {
                                    menuOpen = false
                                    onSetPaused(true)
                                }
                            },
                            MenuAction(stringResource(Res.string.business_bot_manage), Res.drawable.settings_24dp) {
                                menuOpen = false
                                onManage()
                            },
                        ),
                        listOf(
                            MenuAction(stringResource(Res.string.business_bot_remove), Res.drawable.person_remove_24dp, destructive = true) {
                                menuOpen = false
                                confirmRemove = true
                            },
                        ),
                    ),
                )
            }
        },
    )
}
