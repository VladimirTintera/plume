package eu.tintera.plume.features.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.AccountUsernames
import eu.tintera.plume.core.ui.IconSwitch
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.keyboard_arrow_down_24dp
import eu.tintera.plume.resources.keyboard_arrow_up_24dp
import eu.tintera.plume.resources.profile_username_value
import eu.tintera.plume.resources.usernames_collectible
import eu.tintera.plume.resources.usernames_editable
import eu.tintera.plume.resources.usernames_hint
import eu.tintera.plume.resources.usernames_move_down
import eu.tintera.plume.resources.usernames_move_up
import eu.tintera.plume.resources.usernames_section
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Všechna uživatelská jména účtu pod polem uživatelského jména — jen když jich je víc než to
 * upravitelné (sběratelská z Fragmentu). Zapnutá napřed v pořadí (první je hlavní, šipky ho
 * mění), vypnutá za nimi. Každá změna jde na server hned.
 *
 * Řazení šipkami, ne tažením: jmen bývá pár, a tah uvnitř posuvného formuláře by se pral
 * s posouváním.
 */
@Composable
internal fun UsernamesSection(
    usernames: AccountUsernames,
    onSetActive: (String, Boolean) -> Unit,
    onMove: (String, up: Boolean) -> Unit,
) {
    Column {
        Text(
            stringResource(Res.string.usernames_section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp),
        )
        usernames.active.forEachIndexed { index, name ->
            UsernameRow(
                name = name,
                editable = name == usernames.editable,
                active = true,
                canMoveUp = index > 0,
                canMoveDown = index < usernames.active.lastIndex,
                onSetActive = onSetActive,
                onMove = onMove,
            )
        }
        usernames.disabled.forEach { name ->
            UsernameRow(
                name = name,
                editable = name == usernames.editable,
                active = false,
                canMoveUp = false,
                canMoveDown = false,
                onSetActive = onSetActive,
                onMove = onMove,
            )
        }
        Text(
            stringResource(Res.string.usernames_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun UsernameRow(
    name: String,
    editable: Boolean,
    active: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onSetActive: (String, Boolean) -> Unit,
    onMove: (String, up: Boolean) -> Unit,
) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = {
            Text(
                stringResource(Res.string.profile_username_value, name),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (active) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        supportingContent = {
            Text(stringResource(if (editable) Res.string.usernames_editable else Res.string.usernames_collectible))
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (active) {
                    IconButton(onClick = { onMove(name, true) }, enabled = canMoveUp, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.keyboard_arrow_up_24dp), contentDescription = stringResource(Res.string.usernames_move_up))
                    }
                    IconButton(onClick = { onMove(name, false) }, enabled = canMoveDown, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.keyboard_arrow_down_24dp), contentDescription = stringResource(Res.string.usernames_move_down))
                    }
                }
                // Upravitelné jméno vypnout nejde (TDLib) — přepínač ho jen ukazuje.
                IconSwitch(
                    checked = active,
                    onCheckedChange = { onSetActive(name, it) },
                    enabled = !(editable && active),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        },
    )
}
