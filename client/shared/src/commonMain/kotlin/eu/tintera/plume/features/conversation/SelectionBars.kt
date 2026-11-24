package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.adaptive.paneHeaderContainerColor
import androidx.compose.material3.TopAppBarDefaults
import eu.tintera.plume.resources.selection_copy_markdown
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.selection_copy_with_names
import eu.tintera.plume.resources.selection_copy_text
import eu.tintera.plume.resources.forum_24dp
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.ExpressiveMenu
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import eu.tintera.plume.resources.menu_report
import eu.tintera.plume.resources.flag_24dp
import eu.tintera.plume.resources.menu_copy
import eu.tintera.plume.resources.menu_delete
import eu.tintera.plume.resources.menu_forward
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import eu.tintera.plume.core.adaptive.WindowControlsEndSpace
import eu.tintera.plume.core.adaptive.WindowControlsSpace

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
        colors = TopAppBarDefaults.topAppBarColors(containerColor = paneHeaderContainerColor()),
        title = {
            Text(
                text = pluralStringResource(Res.plurals.conversation_selection_count, selection.count, selection.count),
                style = MaterialTheme.typography.titleLargeEmphasized.tabularNumbers(),
            )
        },
        navigationIcon = {
            WindowControlsSpace {
                IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        painterResource(Res.drawable.close_24dp),
                        contentDescription = stringResource(Res.string.conversation_selection_close),
                    )
                }
            }
        },
        actions = { WindowControlsEndSpace() },
    )
}

/**
 * Akce nad výběrem místo vstupního pole — kopírovat, přeposlat, nahlásit, smazat.
 *
 * **Tlačítka nemizí, jen zšednou.** Práva se u každé vybrané zprávy načítají zvlášť, takže by se
 * toolbar při každém ťuknutí přeskládal pod prstem; a zmizelá akce neřekne, že ji zrovna tenhle
 * výběr nedovoluje. Dokud práva nedorazí, jsou zašedlé všechny.
 */
@Composable
internal fun SelectionToolbar(
    selection: MessageSelection,
    onCopy: () -> Unit,
    onCopyWithNames: () -> Unit,
    onCopyMarkdown: () -> Unit,
    onForward: () -> Unit,
    onReport: () -> Unit,
    onDelete: () -> Unit,
) {
    ConversationFloatingBar(visible = selection.count > 0) {
        // Kopírovat nabídne tři podoby: holé texty (jako dřív), přepis s časem a jménem
        // („[10. 10. 2026 14:22] Jana: …") pro jazykový model a Markdown s formátováním pro poznámky.
        var copyMenu by remember { mutableStateOf(false) }
        Box {
            ToolbarAction(
                icon = Res.drawable.content_copy_24dp,
                label = Res.string.menu_copy,
                enabled = selection.canCopy,
                onClick = { copyMenu = true },
            )
            ExpressiveMenu(
                expanded = copyMenu,
                onDismiss = { copyMenu = false },
                groups = listOf(
                    listOf(
                        MenuAction(stringResource(Res.string.selection_copy_text), Res.drawable.content_copy_24dp) {
                            copyMenu = false
                            onCopy()
                        },
                        MenuAction(stringResource(Res.string.selection_copy_with_names), Res.drawable.forum_24dp) {
                            copyMenu = false
                            onCopyWithNames()
                        },
                        MenuAction(stringResource(Res.string.selection_copy_markdown), Res.drawable.description_24dp) {
                            copyMenu = false
                            onCopyMarkdown()
                        },
                    ),
                ),
            )
        }
        ToolbarAction(
            icon = Res.drawable.forward_24dp,
            label = Res.string.menu_forward,
            enabled = selection.canForward,
            onClick = onForward,
        )
        // Před smazáním: spam se nejdřív nahlásí, pak maže — jako v úklidu spamu u chatu.
        ToolbarAction(
            icon = Res.drawable.flag_24dp,
            label = Res.string.menu_report,
            enabled = selection.canReport,
            onClick = onReport,
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
    IconButton(onClick = onClick, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
        Icon(
            painterResource(icon),
            contentDescription = stringResource(label),
            modifier = Modifier.alpha(alpha),
        )
    }
}

private const val DISABLED_ALPHA = 0.38f
