package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Hlavní akce v hlavičce obrazovky — „Uložit", „Přidat", „Vytvořit", „Hotovo". Plné tlačítko,
 * ne `TextButton`: je to hlavní akce obrazovky a holý text vedle ikon v liště se od nich
 * neodlišil (M3 Expressive dává potvrzení plochu). Tvar se při stisku mění jako u ostatních
 * tlačítek. Sdílí ho Nastavení, podstránky profilu, nový kontakt, nová skupina i řazení.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SaveActionButton(
    onClick: () -> Unit,
    enabled: Boolean,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.padding(horizontal = 8.dp),
        content = content,
    )
}
