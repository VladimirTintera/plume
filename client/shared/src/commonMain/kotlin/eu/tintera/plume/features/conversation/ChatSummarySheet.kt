package eu.tintera.plume.features.conversation

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.summary_disclaimer
import eu.tintera.plume.resources.summary_failed
import eu.tintera.plume.resources.summary_model_missing
import eu.tintera.plume.resources.summary_nothing
import eu.tintera.plume.resources.summary_preparing
import eu.tintera.plume.resources.summary_retry
import eu.tintera.plume.resources.summary_title_recent
import eu.tintera.plume.resources.summary_title_unread
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Shrnutí konverzace z modelu v zařízení.
 *
 * Text přibývá, jak ho model píše — na první slovo se čeká i pár vteřin (AICore model při prvním
 * požadavku teprve načítá), takže do té doby stojí indikátor. Text jde vybrat a zkopírovat.
 * Pod ním je vždycky upozornění, že je to výstup modelu: shrnutí může tvrdit něco, co v chatu
 * nezaznělo, a nesmí se tvářit jako obsah konverzace.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatSummarySheet(
    state: ChatSummaryState,
    source: SummarySource?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        ) {
            source?.let {
                val count = it.messages.size
                Text(
                    text = if (it.unread) {
                        pluralStringResource(Res.plurals.summary_title_unread, count, count)
                    } else {
                        pluralStringResource(Res.plurals.summary_title_recent, count, count)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            when (state) {
                ChatSummaryState.Idle -> Unit
                ChatSummaryState.Preparing -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    LoadingIndicator(Modifier.size(40.dp))
                    Text(
                        stringResource(Res.string.summary_preparing),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is ChatSummaryState.Text -> {
                    SelectionContainer {
                        Text(state.text.trim(), style = MaterialTheme.typography.bodyLarge)
                    }
                    if (!state.done) LoadingIndicator(Modifier.size(32.dp))
                }
                ChatSummaryState.ModelMissing -> SummaryNote(Res.string.summary_model_missing)
                ChatSummaryState.NothingToSummarize -> SummaryNote(Res.string.summary_nothing)
                ChatSummaryState.Failed -> {
                    SummaryNote(Res.string.summary_failed)
                    FilledTonalButton(onClick = onRetry) { Text(stringResource(Res.string.summary_retry)) }
                }
            }

            Text(
                stringResource(Res.string.summary_disclaimer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryNote(text: StringResource) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
}
