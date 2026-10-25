package eu.tintera.plume.wear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

class IconAction(val icon: DrawableResource, val label: StringResource, val onClick: () -> Unit)

/**
 * Řada kulatých tónových ikon místo tlačítek s popisky pod sebou: nad seznamem chatů (Hledat,
 * Archiv a složky, Nastavení) a na konci konverzace nad Odpovědět (hlasovka, samolepka, poloha).
 * Tři tlačítka přes celou šířku zabrala celý displej a obsah (první chat, nejnovější zpráva)
 * byl vidět až po odrolování. Popisek nese čtečka obrazovky (`contentDescription`); ikony jsou
 * na hodinkách srozumitelné i bez textu.
 */
@Composable
fun IconActionRow(actions: List<IconAction>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        actions.forEach { action ->
            FilledTonalIconButton(onClick = action.onClick, shapes = IconButtonDefaults.animatedShapes()) {
                Icon(painterResource(action.icon), contentDescription = stringResource(action.label))
            }
        }
    }
}
