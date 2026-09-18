package eu.tintera.plume.features.settings

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.app.root.presentation.defaultThemeMode
import eu.tintera.plume.app.root.presentation.supportsDynamicColor
import eu.tintera.plume.core.settings.ThemeMode
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.settings_chats
import eu.tintera.plume.resources.settings_separate_unread
import eu.tintera.plume.resources.settings_separate_unread_summary
import eu.tintera.plume.resources.settings_theme
import eu.tintera.plume.resources.settings_theme_dark
import eu.tintera.plume.resources.settings_theme_dynamic
import eu.tintera.plume.resources.settings_theme_light
import eu.tintera.plume.resources.settings_theme_system
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Sekce „Vzhled": motiv a podoba seznamu chatů.
 *
 * Obsah do sloupce sekce, ne celá obrazovka — hlavičku a scrollování drží [SettingsSectionScreen].
 */
@Composable
fun AppearanceSettings(viewModel: AppearanceViewModel) {
    val storedTheme by viewModel.themeMode.collectAsStateWithLifecycle()
    val separateUnread by viewModel.separateUnreadChats.collectAsStateWithLifecycle()

    // Dokud si uživatel nevybral, je vybraná ta možnost, podle které se aplikace opravdu kreslí —
    // prázdný výběr by vypadal jako chyba a nikdo by z něj nepoznal, co teď platí.
    val selectedTheme = storedTheme ?: defaultThemeMode()

    SettingsGroup(Res.string.settings_theme) {
        themeOptions().forEach { mode ->
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
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

/**
 * Nabídka motivů.
 *
 * `DYNAMIC` je Material You — na platformě, kde systémové barvy nejsou, by to byla volba,
 * po které se nic nezmění.
 */
private fun themeOptions(): List<ThemeMode> =
    ThemeMode.entries.filter { it != ThemeMode.DYNAMIC || supportsDynamicColor }

private val ThemeMode.label: StringResource
    get() = when (this) {
        ThemeMode.SYSTEM -> Res.string.settings_theme_system
        ThemeMode.LIGHT -> Res.string.settings_theme_light
        ThemeMode.DARK -> Res.string.settings_theme_dark
        ThemeMode.DYNAMIC -> Res.string.settings_theme_dynamic
    }
