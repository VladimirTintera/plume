package eu.tintera.plume.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.locale.APP_LANGUAGES
import eu.tintera.plume.core.locale.nativeLanguageName
import eu.tintera.plume.core.locale.AppLanguage
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.language_24dp
import eu.tintera.plume.resources.settings_app_language
import eu.tintera.plume.resources.settings_language_group
import eu.tintera.plume.resources.settings_language_in_system
import eu.tintera.plume.resources.settings_language_system
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Skupina „Jazyk" v Obecných: jazyk aplikace nezávisle na systému.
 *
 * Kde ho systém pro jednu aplikaci nezná (Android do 12), se skupina neukáže vůbec. Na iOS jen
 * otevře systémové Nastavení — změnit ho z aplikace tam nejde (viz [AppLanguage]).
 */
@Composable
internal fun LanguageSettings(language: AppLanguage = koinInject()) {
    if (!language.isSupported) return
    // Systém drží volbu sám a po změně překreslí obrazovku novou konfigurací; tady jen zrcadlo.
    var current by remember { mutableStateOf(language.current()) }
    var picking by rememberSaveable { mutableStateOf(false) }

    SettingsGroup(Res.string.settings_language_group) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(painterResource(Res.drawable.language_24dp), contentDescription = null) },
            headlineContent = { Text(stringResource(Res.string.settings_app_language)) },
            supportingContent = {
                Text(
                    when {
                        !language.canChooseInApp -> stringResource(Res.string.settings_language_in_system)
                        else -> languageLabel(current)
                    },
                )
            },
            modifier = Modifier.clickable {
                if (language.canChooseInApp) picking = true else language.openSystemSettings()
            },
        )
    }

    if (picking) {
        LanguageDialog(
            selected = current,
            onSelect = { chosen ->
                picking = false
                current = chosen
                language.set(chosen)
            },
            onDismiss = { picking = false },
        )
    }
}

/**
 * Výběr jazyka. Názvy jazyků jsou v jejich vlastním jazyce („Čeština" i v anglické aplikaci) —
 * kdo se omylem přepne do jazyka, kterému nerozumí, musí najít cestu zpátky.
 */
@Composable
private fun LanguageDialog(
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.settings_app_language)) },
        text = {
            Column {
                (listOf<String?>(null) + APP_LANGUAGES).forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == selected, role = Role.RadioButton, onClick = { onSelect(option) })
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(languageLabel(option), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}

/** Jazyk jeho vlastním názvem ([nativeLanguageName]); `null` = podle systému, v jazyce aplikace. */
@Composable
private fun languageLabel(language: String?): String =
    language?.let(::nativeLanguageName) ?: stringResource(Res.string.settings_language_system)
