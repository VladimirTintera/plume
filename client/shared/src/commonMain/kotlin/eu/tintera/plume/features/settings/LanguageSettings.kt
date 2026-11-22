package eu.tintera.plume.features.settings

import eu.tintera.plume.core.ui.rememberRestoringScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.GROUP_SPACING
import eu.tintera.plume.core.ui.ChoiceListItem
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.locale.APP_LANGUAGES
import eu.tintera.plume.core.locale.nativeLanguageName
import eu.tintera.plume.core.locale.AppLanguage
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.language_24dp
import eu.tintera.plume.resources.settings_app_language
import eu.tintera.plume.resources.settings_language_group
import eu.tintera.plume.resources.settings_language_in_system
import eu.tintera.plume.resources.settings_language_system
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Řádek „Jazyk" v seznamu Nastavení: jazyk aplikace nezávisle na systému. Samostatný řádek, ne
 * sekce — hledá ho i ten, kdo vzhled neřeší, a sekce s jedinou volbou by byla navíc.
 *
 * Na Androidu do 12 a na desktopu, kde systém jazyk pro jednu aplikaci nezná, ho drží aplikace
 * sama. Na iOS řádek jen otevře systémové Nastavení — změnit ho z aplikace tam nejde
 * (viz [AppLanguage]).
 */
@Composable
internal fun LanguageRow(language: AppLanguage = koinInject()) {
    if (!language.isSupported) return
    // Systém drží volbu sám a po změně překreslí obrazovku novou konfigurací; tady jen zrcadlo.
    var current by remember { mutableStateOf(language.current()) }
    var picking by rememberSaveable { mutableStateOf(false) }

    SettingsListRow(
        icon = Res.drawable.language_24dp,
        title = stringResource(Res.string.settings_language_group),
        summary = if (language.canChooseInApp) languageLabel(current) else stringResource(Res.string.settings_language_in_system),
        first = true,
        last = true,
        onClick = { if (language.canChooseInApp) picking = true else language.openSystemSettings() },
    )

    if (picking) {
        LanguageSheet(
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
 *
 * Sheet se dvěma bloky („Podle systému" a jazyky), ne dialog s kolečky: jazyků s komunitními
 * překlady přibývá a dialog by se jimi brzy přeplnil.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageSheet(
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberExpandedSheetState()) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberRestoringScrollState()).padding(bottom = 16.dp)) {
            SheetTitle(
                stringResource(Res.string.settings_app_language),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp),
            )
            Column(Modifier.selectableGroup()) {
                ChoiceListItem(
                    label = languageLabel(null),
                    selected = selected == null,
                    index = 0,
                    count = 1,
                    onClick = { onSelect(null) },
                )
                Spacer(Modifier.height(GROUP_SPACING))
                APP_LANGUAGES.forEachIndexed { index, option ->
                    ChoiceListItem(
                        label = languageLabel(option),
                        selected = option == selected,
                        index = index,
                        count = APP_LANGUAGES.size,
                        onClick = { onSelect(option) },
                    )
                }
            }
        }
    }
}

/** Jazyk jeho vlastním názvem ([nativeLanguageName]); `null` = podle systému, v jazyce aplikace. */
@Composable
private fun languageLabel(language: String?): String =
    language?.let(::nativeLanguageName) ?: stringResource(Res.string.settings_language_system)
