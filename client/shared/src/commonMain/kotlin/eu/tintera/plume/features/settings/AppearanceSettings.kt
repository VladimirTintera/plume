package eu.tintera.plume.features.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.app.root.presentation.supportsDynamicColor
import eu.tintera.plume.core.settings.ThemeMode
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.brightness_medium_24dp
import eu.tintera.plume.resources.dark_mode_24dp
import eu.tintera.plume.resources.light_mode_24dp
import eu.tintera.plume.resources.palette_24dp
import eu.tintera.plume.resources.settings_chats
import eu.tintera.plume.resources.settings_separate_unread
import eu.tintera.plume.resources.settings_separate_unread_summary
import eu.tintera.plume.resources.settings_theme
import eu.tintera.plume.resources.settings_theme_dark
import eu.tintera.plume.resources.settings_theme_dynamic
import eu.tintera.plume.resources.settings_theme_dynamic_summary
import eu.tintera.plume.resources.settings_theme_light
import eu.tintera.plume.resources.settings_theme_system
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Sekce „Vzhled": motiv a podoba seznamu chatů.
 *
 * Obsah do sloupce sekce, ne celá obrazovka — hlavičku a scrollování drží [SettingsSectionScreen].
 */
@Composable
fun AppearanceSettings(viewModel: AppearanceViewModel) {
    val storedTheme by viewModel.themeMode.collectAsStateWithLifecycle()
    val storedDynamic by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val separateUnread by viewModel.separateUnreadChats.collectAsStateWithLifecycle()

    // Dokud si uživatel nevybral, je vybraná ta možnost, podle které se aplikace opravdu kreslí —
    // prázdný výběr by vypadal jako chyba a nikdo by z něj nepoznal, co teď platí.
    val selectedTheme = storedTheme ?: ThemeMode.SYSTEM
    val dynamicColor = storedDynamic ?: supportsDynamicColor

    SettingsGroup(Res.string.settings_theme) {
        ThemeMode.entries.forEach { mode ->
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(painterResource(mode.icon), contentDescription = null) },
                headlineContent = { Text(stringResource(mode.label)) },
                // `onClick = null` u kolečka: klikání nese celý řádek, aby se nemuselo trefovat
                // do něj — a aby čtečka obrazovky četla jednu položku, ne dvě.
                trailingContent = { RadioButton(selected = mode == selectedTheme, onClick = null) },
                modifier = Modifier.selectable(
                    selected = mode == selectedTheme,
                    role = Role.RadioButton,
                    onClick = { viewModel.setThemeMode(mode) },
                ),
            )
        }

        // Barvy z tapety se kombinují s kterýmkoli motivem, proto přepínač, ne čtvrté kolečko.
        // Kde systémové barvy nejsou (iOS, Android do 11), by to byla volba, po které se nic nezmění.
        if (supportsDynamicColor) {
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                leadingContent = { Icon(painterResource(Res.drawable.palette_24dp), contentDescription = null) },
                headlineContent = { Text(stringResource(Res.string.settings_theme_dynamic)) },
                supportingContent = { Text(stringResource(Res.string.settings_theme_dynamic_summary)) },
                trailingContent = { Switch(checked = dynamicColor, onCheckedChange = null) },
                modifier = Modifier.toggleable(
                    value = dynamicColor,
                    role = Role.Switch,
                    onValueChange = viewModel::setDynamicColor,
                ),
            )
        }
    }

    SettingsGroup(Res.string.settings_chats) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(stringResource(Res.string.settings_separate_unread)) },
            supportingContent = { Text(stringResource(Res.string.settings_separate_unread_summary)) },
            trailingContent = { Switch(checked = separateUnread, onCheckedChange = null) },
            modifier = Modifier.toggleable(
                value = separateUnread,
                role = Role.Switch,
                onValueChange = viewModel::setSeparateUnreadChats,
            ),
        )
    }
}

private val ThemeMode.label: StringResource
    get() = when (this) {
        ThemeMode.SYSTEM -> Res.string.settings_theme_system
        ThemeMode.LIGHT -> Res.string.settings_theme_light
        ThemeMode.DARK -> Res.string.settings_theme_dark
    }

private val ThemeMode.icon: DrawableResource
    get() = when (this) {
        ThemeMode.SYSTEM -> Res.drawable.brightness_medium_24dp
        ThemeMode.LIGHT -> Res.drawable.light_mode_24dp
        ThemeMode.DARK -> Res.drawable.dark_mode_24dp
    }
