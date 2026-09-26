package eu.tintera.plume.features.conversation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.tintera.plume.core.telegram.MessageEffect
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.effect_picker_empty
import eu.tintera.plume.resources.effect_picker_hint
import eu.tintera.plume.resources.effect_picker_title
import eu.tintera.plume.resources.message_effect
import org.jetbrains.compose.resources.stringResource

/**
 * Výběr efektu, se kterým zpráva odejde (podržení odesílacího tlačítka → „Odeslat s efektem…").
 * Ťuknutí na efekt zprávu rovnou pošle — výběr JE odeslání, druhý krok s potvrzením by byl
 * ťuknutí navíc u něčeho, co jde vidět hned po odeslání.
 *
 * Efekt kreslí jeho emoji, ne animaci: mřížka desítek animací by stála víc, než přinese,
 * a podoba efektu se ukáže nad odeslanou zprávou.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EffectPickerSheet(
    effects: List<MessageEffect>?,
    onPick: (MessageEffect) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                text = stringResource(Res.string.effect_picker_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                text = stringResource(Res.string.effect_picker_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            when {
                effects == null -> Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
                effects.isEmpty() -> Text(
                    text = stringResource(Res.string.effect_picker_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(EFFECT_CELL),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.heightIn(max = MAX_GRID_HEIGHT),
                ) {
                    items(effects, key = { it.id }) { effect -> EffectCell(effect) { onPick(effect) } }
                }
            }
        }
    }
}

@Composable
private fun EffectCell(effect: MessageEffect, onClick: () -> Unit) {
    val description = stringResource(Res.string.message_effect, effect.emoji)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(4.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // Výška řádku rovná písmu, jinak emoji v buňce sedí níž (jako u čipu reakce).
        Text(effect.emoji, style = TextStyle(fontSize = 30.sp, lineHeight = 30.sp))
    }
}

private val EFFECT_CELL = 56.dp

/** Strop výšky mřížky; efektů je pár desítek a zbytek se roluje. */
private val MAX_GRID_HEIGHT = 360.dp
