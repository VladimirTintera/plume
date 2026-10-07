package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.drag_indicator_24dp
import eu.tintera.plume.resources.reorder_done
import eu.tintera.plume.resources.reorder_drag
import eu.tintera.plume.resources.reorder_move_down
import eu.tintera.plume.resources.reorder_move_up
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Hlavička režimu uspořádání (připnutá témata, připnuté chaty): ✕ zahodí změny, „Hotovo" je
 * odešle. Sdílená, aby oba režimy vypadaly i ovládaly se stejně.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderTopBar(title: String, busy: Boolean, onCancel: () -> Unit, onDone: () -> Unit) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onCancel, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.common_cancel))
            }
        },
        actions = {
            TextButton(onClick = onDone, enabled = !busy) { Text(stringResource(Res.string.reorder_done)) }
        },
        colors = TopAppBarDefaults.topAppBarColors(scrolledContainerColor = MaterialTheme.colorScheme.surface),
    )
}

/**
 * Řádek v režimu uspořádání: [leading] (ikona, tvář), název a úchyt. Tah začíná na úchytu hned,
 * bez podržení ([dragHandle]). Čtečka obrazovky tahat neumí, takže řádek nese akce „Posunout
 * nahoru / dolů" ([onMoveUp], [onMoveDown]; `null` = na kraji).
 */
@Composable
fun ReorderRow(
    key: Any,
    title: String,
    shape: Shape,
    dragState: DragReorderState,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit,
) {
    val up = stringResource(Res.string.reorder_move_up)
    val down = stringResource(Res.string.reorder_move_down)
    val dragged = dragState.draggedKey == key
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        // Tažený řádek se zvedne, aby bylo vidět, že jede nad ostatními.
        shadowElevation = if (dragged) 6.dp else 0.dp,
        modifier = modifier.fillMaxWidth().semantics {
            customActions = listOfNotNull(
                onMoveUp?.let { move -> CustomAccessibilityAction(up) { move(); true } },
                onMoveDown?.let { move -> CustomAccessibilityAction(down) { move(); true } },
            )
        },
    ) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            leading()
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.size(48.dp).dragHandle(dragState, key),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(Res.drawable.drag_indicator_24dp),
                    contentDescription = stringResource(Res.string.reorder_drag),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
