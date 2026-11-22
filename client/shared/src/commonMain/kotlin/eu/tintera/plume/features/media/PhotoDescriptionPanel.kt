package eu.tintera.plume.features.media

import eu.tintera.plume.core.ui.rememberRestoringScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.content_copy_24dp
import eu.tintera.plume.resources.media_describe_disclaimer
import eu.tintera.plume.resources.media_describe_progress
import eu.tintera.plume.resources.media_describe_title
import eu.tintera.plume.resources.menu_copy
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Popis fotky od modelu v telefonu, na místě pruhu reakcí — tamtéž a stejně jako text z fotky
 * ([PhotoTextPanel]), jen se na fotce nic nezvýrazňuje.
 *
 * - **Text přibývá, jak ho model píše**; do prvního slova (model si načítá obraz, na CPU
 *   i desítky vteřin) točí indikátor s větou, co se děje.
 * - **Dopsaný popis přečte čtečka sama** (`liveRegion`): kvůli ní popis hlavně je, a kdo fotku
 *   nevidí, nemá jak poznat, že model doběhl. Během psaní se nečte — přerušovala by se
 *   každým přírůstkem.
 * - **Pod textem věta, že je to odhad modelu** — popis může minout, co na fotce podstatné je.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PhotoDescriptionPanel(
    description: PhotoDescription,
    onCopy: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .widthIn(max = PANEL_MAX_WIDTH)
            .fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.media_describe_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.common_close))
                }
            }
            val error = description.error
            when {
                error != null -> Text(
                    error.resolve(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(end = 12.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
                description.text.isBlank() -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(end = 12.dp),
                ) {
                    LoadingIndicator(Modifier.size(32.dp))
                    Spacer(Modifier.padding(start = 12.dp))
                    Text(
                        stringResource(Res.string.media_describe_progress),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> SelectionContainer {
                    Text(
                        description.text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .heightIn(max = TEXT_MAX_HEIGHT)
                            .verticalScroll(rememberRestoringScrollState())
                            .then(if (description.done) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier),
                    )
                }
            }
            Spacer(Modifier.padding(top = 12.dp))
            Row(
                Modifier.fillMaxWidth().padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.media_describe_disclaimer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (description.done) {
                    FilledTonalButton(onClick = onCopy, shapes = ButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.content_copy_24dp), contentDescription = null)
                        Spacer(Modifier.padding(start = 8.dp))
                        Text(stringResource(Res.string.menu_copy))
                    }
                }
            }
        }
    }
}

/** Stejné míry jako panel textu z fotky — oba leží na témže místě. */
private val TEXT_MAX_HEIGHT = 180.dp
private val PANEL_MAX_WIDTH = 560.dp
