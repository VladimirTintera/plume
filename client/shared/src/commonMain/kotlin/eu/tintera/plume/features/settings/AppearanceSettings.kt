package eu.tintera.plume.features.settings

import eu.tintera.plume.resources.widgets_24dp
import eu.tintera.plume.resources.settings_chats_widget
import eu.tintera.plume.resources.settings_chats_widget_summary
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ToggleButton
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.app.root.presentation.resolveIsDark
import eu.tintera.plume.app.root.presentation.supportsDynamicColor
import eu.tintera.plume.core.settings.ThemeColor
import eu.tintera.plume.core.settings.ThemeMode
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.brightness_medium_24dp
import eu.tintera.plume.resources.dark_mode_24dp
import eu.tintera.plume.resources.light_mode_24dp
import eu.tintera.plume.resources.palette_24dp
import eu.tintera.plume.resources.settings_chat_row_gaps
import eu.tintera.plume.resources.settings_chat_row_gaps_summary
import eu.tintera.plume.resources.settings_chats
import eu.tintera.plume.resources.settings_messages
import eu.tintera.plume.resources.settings_theme
import eu.tintera.plume.resources.settings_theme_color
import eu.tintera.plume.resources.settings_theme_color_dynamic_note
import eu.tintera.plume.resources.settings_theme_dark
import eu.tintera.plume.resources.settings_theme_dynamic
import eu.tintera.plume.resources.settings_theme_dynamic_summary
import eu.tintera.plume.resources.settings_theme_light
import eu.tintera.plume.resources.settings_theme_system
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import eu.tintera.plume.core.ui.SwitchListItem
import eu.tintera.plume.core.ui.highlightAnchor
import eu.tintera.plume.core.ui.TitledGroup

/**
 * Sekce „Vzhled": motiv, barva aplikace, písmo a samolepky ve zprávách a řádky náhledu v seznamu
 * chatů. Řazení seznamu chatů je v sekci Chaty.
 *
 * Obsah do sloupce sekce, ne celá obrazovka — hlavičku a scrollování drží [SettingsSectionScreen].
 */
@Composable
fun AppearanceSettings(viewModel: AppearanceViewModel) {
    val storedTheme by viewModel.themeMode.collectAsStateWithLifecycle()
    val storedDynamic by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val storedColor by viewModel.themeColor.collectAsStateWithLifecycle()
    val messageTextSize by viewModel.messageTextSize.collectAsStateWithLifecycle()
    val stickerSize by viewModel.stickerSize.collectAsStateWithLifecycle()
    val chatPreviewLines by viewModel.chatPreviewLines.collectAsStateWithLifecycle()
    val chatRowGaps by viewModel.chatRowGaps.collectAsStateWithLifecycle()

    // Dokud si uživatel nevybral, je vybraná ta možnost, podle které se aplikace opravdu kreslí —
    // prázdný výběr by vypadal jako chyba a nikdo by z něj nepoznal, co teď platí.
    val selectedTheme = storedTheme ?: ThemeMode.SYSTEM
    val dynamicColor = storedDynamic ?: supportsDynamicColor

    TitledGroup(Res.string.settings_theme) {
        ThemeModeSelector(selected = selectedTheme, onSelect = viewModel::setThemeMode)

        // Barvy z tapety se kombinují s kterýmkoli motivem, proto přepínač, ne čtvrtá volba.
        // Kde systémové barvy nejsou (iOS, Android do 11), by to byla volba, po které se nic nezmění.
        if (supportsDynamicColor) {
            SwitchListItem(
                headline = stringResource(Res.string.settings_theme_dynamic),
                checked = dynamicColor,
                supporting = stringResource(Res.string.settings_theme_dynamic_summary),
                icon = Res.drawable.palette_24dp,
                onCheckedChange = viewModel::setDynamicColor,
            )
        }
    }

    // Barva platí, jen když neplatí tapeta — s tapetou žádný vzorek vybraný není, jinak by
    // tvrdil, že se aplikace kreslí z něj. Řada je vidět i tak: ťuknutí tapetu vypne.
    val dynamicActive = dynamicColor && supportsDynamicColor
    TitledGroup(Res.string.settings_theme_color) {
        // Vzorky i poznámka pod nimi jsou jeden řádek bloku: poznámka mluví o vzorcích.
        Column {
            ThemeColorPicker(
                selected = if (dynamicActive) null else storedColor ?: ThemeColor.BLUE,
                isDark = selectedTheme.resolveIsDark(),
                onSelect = { color -> viewModel.chooseThemeColor(color, dynamicActive) },
            )
            AnimatedVisibility(visible = dynamicActive) {
                Text(
                    stringResource(Res.string.settings_theme_color_dynamic_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                )
            }
        }
    }

    TitledGroup(Res.string.settings_messages) {
        MessageTextSizeSetting(stored = messageTextSize, onChange = viewModel::setMessageTextSize)
        StickerSizeSetting(stored = stickerSize, onChange = viewModel::setStickerSize)
    }

    TitledGroup(Res.string.settings_chats) {
        ChatPreviewLinesSetting(selected = chatPreviewLines, onSelect = viewModel::setChatPreviewLines)
        val gapsTitle = stringResource(Res.string.settings_chat_row_gaps)
        SwitchListItem(
            headline = gapsTitle,
            checked = chatRowGaps,
            supporting = stringResource(Res.string.settings_chat_row_gaps_summary),
            modifier = Modifier.highlightAnchor(gapsTitle),
            onCheckedChange = viewModel::setChatRowGaps,
        )
    }
}

/** Widget s chaty na ploše: umístí ho launcher po potvrzení v jeho dialogu. Jen kde to launcher umí. */
@Composable
internal fun ChatsWidgetRow(viewModel: AppearanceViewModel) {
    if (!viewModel.canPinWidget) return
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(Res.drawable.widgets_24dp), contentDescription = null) },
        headlineContent = { Text(stringResource(Res.string.settings_chats_widget)) },
        supportingContent = { Text(stringResource(Res.string.settings_chats_widget_summary)) },
        modifier = Modifier.clickable(onClick = viewModel::pinChatsWidget),
    )
}

/**
 * Motiv jako spojená přepínací tlačítka (M3 Expressive connected button group), ne tři řádky
 * s kolečky: volba jedné ze tří krátkých možností je přesně to, na co skupina je, a kolečka pod
 * sebou byla vzor M2. Vybrané tlačítko si přianimuje ikonu motivu, stejně jako záložky profilu —
 * ikony u všech tří by se na telefon s popisky nevešly.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ThemeModeSelector(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    val modes = ThemeMode.entries
    Row(
        Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        modes.forEachIndexed { index, mode ->
            val checked = mode == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { if (!checked) onSelect(mode) },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    modes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
            ) {
                AnimatedVisibility(
                    visible = checked,
                    enter = expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec()) +
                        fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = shrinkHorizontally(MaterialTheme.motionScheme.fastSpatialSpec()) +
                        fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                ) {
                    Icon(
                        painterResource(mode.icon),
                        contentDescription = null,
                        modifier = Modifier.padding(end = ButtonDefaults.IconSpacing).size(ButtonDefaults.IconSize),
                    )
                }
                Text(stringResource(mode.label), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
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
