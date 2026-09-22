package eu.tintera.plume.features.conversation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.content_copy_24dp
import eu.tintera.plume.resources.conversation_selection_close
import eu.tintera.plume.resources.conversation_selection_count
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.forward_24dp
import eu.tintera.plume.resources.menu_copy
import eu.tintera.plume.resources.menu_delete
import eu.tintera.plume.resources.menu_forward
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Hlavička v režimu výběru: ✕ a počet vybraných.
 *
 * Akce jsou dole v plovoucím toolbaru, ne tady — stejně jako u hledání v konverzaci, kde je
 * nahoře pilulka a dole šipky mezi výsledky. Spodek je v dosahu palce a hlavička má říct jen
 * to, v jakém režimu uživatel je a kolik toho vybral.
 */
@Composable
internal fun SelectionTopBar(selection: MessageSelection, onClose: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = pluralStringResource(Res.plurals.conversation_selection_count, selection.count, selection.count),
                style = MaterialTheme.typography.titleLarge.tabularNumbers(),
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    painterResource(Res.drawable.close_24dp),
                    contentDescription = stringResource(Res.string.conversation_selection_close),
                )
            }
        },
    )
}

/**
 * Akce nad výběrem místo vstupního pole — kopírovat, přeposlat, smazat.
 *
 * **Tlačítka nemizí, jen zšednou.** Práva se u každé vybrané zprávy načítají zvlášť, takže by se
 * toolbar při každém ťuknutí přeskládal pod prstem; a zmizelá akce neřekne, že ji zrovna tenhle
 * výběr nedovoluje. Dokud práva nedorazí, jsou zašedlé všechny.
 */
@Composable
internal fun SelectionToolbar(
    selection: MessageSelection,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onDelete: () -> Unit,
) {
    ConversationFloatingBar(visible = selection.count > 0) {
        ToolbarAction(
            icon = Res.drawable.content_copy_24dp,
            label = Res.string.menu_copy,
            enabled = selection.canCopy,
            onClick = onCopy,
        )
        ToolbarAction(
            icon = Res.drawable.forward_24dp,
            label = Res.string.menu_forward,
            enabled = selection.canForward,
            onClick = onForward,
        )
        ToolbarAction(
            icon = Res.drawable.delete_24dp,
            label = Res.string.menu_delete,
            enabled = selection.canDelete,
            onClick = onDelete,
        )
    }
}

/** Zašednutí se prolíná, aby toolbar po načtení práv neproblikl. */
@Composable
private fun ToolbarAction(
    icon: org.jetbrains.compose.resources.DrawableResource,
    label: org.jetbrains.compose.resources.StringResource,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else DISABLED_ALPHA,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "selection-action-alpha",
    )
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            painterResource(icon),
            contentDescription = stringResource(label),
            modifier = Modifier.alpha(alpha),
        )
    }
}

private const val DISABLED_ALPHA = 0.38f
