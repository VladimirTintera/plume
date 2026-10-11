package eu.tintera.plume.features.profile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.app.root.presentation.LocalIsDarkTheme
import eu.tintera.plume.app.root.presentation.theme.chatColorScheme
import eu.tintera.plume.app.root.presentation.theme.seed
import eu.tintera.plume.core.settings.ThemeColor
import eu.tintera.plume.features.settings.ThemeColorPicker
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chat_color_explanation
import eu.tintera.plume.resources.chat_color_title
import org.jetbrains.compose.resources.stringResource

/**
 * Výběr barvy konverzace: „Podle aplikace" a tytéž vzorky jako barva aplikace v Nastavení.
 *
 * **Výběr sheet nezavírá.** Profil i sheet samotný leží v barvě chatu (`ChatColorTheme`), takže
 * se po ťuknutí přebarví hned za prstem — to je náhled, a zavřením by zmizel dřív, než ho
 * uživatel uvidí. Věta nahoře říká, že barvu nikdo jiný nevidí: Telegram má témata chatu, která
 * se ukazují oběma stranám, a bez ní by šlo čekat totéž.
 */
@Composable
internal fun ChatColorSheet(
    current: ThemeColor?,
    onSelect: (ThemeColor?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    val isDark = LocalIsDarkTheme.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = stringResource(Res.string.chat_color_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        Text(
            text = stringResource(Res.string.chat_color_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        )
        ThemeColorPicker(
            selected = current,
            isDark = isDark,
            onSelect = onSelect,
            onSelectDefault = { onSelect(null) },
            schemeOf = { chatColorScheme(isDark, it.seed) },
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}
