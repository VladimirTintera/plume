package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.SheetTitle
import androidx.compose.foundation.ExperimentalFoundationApi
import eu.tintera.plume.core.ui.contextClickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
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
import eu.tintera.plume.features.conversation.items.EffectAnimation
import eu.tintera.plume.resources.message_effect
import eu.tintera.plume.resources.message_effect_play
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
    val sheetState = rememberExpandedSheetState()
    /** Přehrávaný náhled: efekt, kolikáté přehrání a střed políčka v souřadnicích sheetu. */
    var preview by remember { mutableStateOf<EffectPreview?>(null) }
    var runs by remember { mutableIntStateOf(0) }
    var sheetCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Box(Modifier.onPlaced { sheetCoordinates = it }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                SheetTitle(stringResource(Res.string.effect_picker_title), modifier = Modifier.padding(horizontal = 24.dp))
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
                        items(effects, key = { it.id }) { effect ->
                            EffectCell(
                                effect = effect,
                                onClick = { onPick(effect) },
                                onPreview = { cell ->
                                    val sheet = sheetCoordinates ?: return@EffectCell
                                    if (!sheet.isAttached || !cell.isAttached) return@EffectCell
                                    val center = Offset(cell.size.width / 2f, cell.size.height / 2f)
                                    preview = EffectPreview(effect, ++runs, sheet.localPositionOf(cell, center))
                                },
                            )
                        }
                    }
                }
            }
            /*
             * Náhled leží nad celým sheetem, ne v políčku: mřížka ořezává ve směru scrollu,
             * takže by výbuch nad políčkem uřízla. Kotví se ve středu podrženého políčka a letí
             * jako u ODCHOZÍ zprávy — tu uživatel posílá.
             */
            preview?.let { shown ->
                EffectAnimation(
                    effect = shown.effect,
                    run = shown.run,
                    isOutgoing = true,
                    onFinished = { if (preview?.run == shown.run) preview = null },
                    modifier = Modifier.offset { IntOffset(shown.at.x.toInt(), shown.at.y.toInt()) },
                )
            }
        }
    }
}

private data class EffectPreview(val effect: MessageEffect, val run: Int, val at: Offset)

/**
 * Políčko efektu: ťuknutí zprávu pošle, podržení efekt přehraje — bez náhledu by se volilo jen
 * podle emoji, a to o animaci moc neřekne. Podržení je druhá, pomalejší cesta, takže ťuknutí,
 * které je tu hlavní akce, nic nezdržuje.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EffectCell(effect: MessageEffect, onClick: () -> Unit, onPreview: (LayoutCoordinates) -> Unit) {
    val description = stringResource(Res.string.message_effect, effect.emoji)
    val playLabel = stringResource(Res.string.message_effect_play)
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(4.dp)
            .onPlaced { coordinates = it }
            .clip(CircleShape)
            .contextClickable(
                onClick = onClick,
                onLongClickLabel = playLabel,
                onLongClick = if (effect.animationFileId != null) {
                    { coordinates?.let(onPreview) }
                } else {
                    null
                },
            )
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
