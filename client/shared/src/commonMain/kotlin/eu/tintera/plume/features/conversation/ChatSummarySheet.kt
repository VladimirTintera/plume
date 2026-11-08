package eu.tintera.plume.features.conversation

import eu.tintera.plume.resources.voice_summary_title_video
import eu.tintera.plume.resources.voice_summary_title
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ai.AiProvider
import eu.tintera.plume.resources.decimal_number
import eu.tintera.plume.resources.summary_details_elapsed
import eu.tintera.plume.resources.summary_compare
import eu.tintera.plume.features.settings.title
import androidx.compose.material3.OutlinedButton
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.resources.explain_failed
import eu.tintera.plume.resources.explain_preparing
import eu.tintera.plume.resources.explain_title
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.resources.summary_details_model
import eu.tintera.plume.resources.summary_details_tokens
import eu.tintera.plume.resources.summary_details_tokens_no_limit
import eu.tintera.plume.resources.summary_details_trimmed
import eu.tintera.plume.resources.summary_disclaimer
import eu.tintera.plume.resources.summary_failed
import eu.tintera.plume.resources.summary_model_missing
import eu.tintera.plume.resources.summary_nothing
import eu.tintera.plume.resources.summary_preparing
import eu.tintera.plume.resources.summary_reading_part
import eu.tintera.plume.resources.summary_details_parts
import eu.tintera.plume.resources.summary_retry
import eu.tintera.plume.resources.summary_truncated
import eu.tintera.plume.resources.date_day_month
import eu.tintera.plume.core.ui.name
import eu.tintera.plume.resources.summary_title_day
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
    /** Totéž z ostatních zapnutých modelů ([ChatSummaryViewModel.compare]); prázdné = neporovnává se. */
    comparison: List<ComparedSummary> = emptyList(),
    /** Nabídnout porovnání — jen s víc zapnutými modely ([ChatSummaryViewModel.canCompare]). */
    canCompare: Boolean = false,
    onCompare: () -> Unit = {},
    /** Kdo počítal hlavní výsledek; nadpis nad ním jen při porovnání. */
    mainProvider: AiProvider? = null,
) {
    val sheetState = rememberExpandedSheetState()
    // Vysvětlení jedné zprávy (`ChatSummaryViewModel.explain`) jde stejným sheetem, jen s vlastními větami.
    val explain = source?.explain == true

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                // Bez toho poslední řádek (model a tokeny) zůstane pod navigačním pruhem
                // a nejde ho dorolovat nad něj — přečte se až po rozbalení sheetu.
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        ) {
            source?.let {
                val count = it.messages.size
                SheetTitle(
                    text = if (it.explain) {
                        stringResource(Res.string.explain_title)
                    } else if (it.voice) {
                        val videoNote = it.messages.firstOrNull()?.media is MessageMedia.VideoNote
                        stringResource(if (videoNote) Res.string.voice_summary_title_video else Res.string.voice_summary_title)
                    } else if (it.day != null) {
                        stringResource(
                            Res.string.summary_title_day,
                            stringResource(Res.string.date_day_month, it.day.day, stringResource(it.day.month.name())),
                        )
                    } else if (it.unread) {
                        pluralStringResource(Res.plurals.summary_title_unread, count, count)
                    } else {
                        pluralStringResource(Res.plurals.summary_title_recent, count, count)
                    },
                )
            }

            // Při porovnání nese i hlavní výsledek jméno modelu, jinak by nebylo poznat, čí je.
            if (comparison.isNotEmpty()) mainProvider?.let { ProviderHeading(it) }
            SummaryResult(state, explain, onRetry)

            /*
             * Porovnání až po HOTOVÉM výsledku: modely se počítají po sobě, ne souběžně (Gemma
             * drží gigabajty a čas by se při souběhu nedal porovnat), a tlačítko během psaní by
             * slibovalo něco, co hned nezačne.
             */
            val finished = (state as? ChatSummaryState.Text)?.done == true
            if (canCompare && comparison.isEmpty() && finished) {
                OutlinedButton(onClick = onCompare, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(Res.string.summary_compare))
                }
            }
            comparison.forEach { compared ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProviderHeading(compared.provider)
                    // Bez „Zkusit znovu": porovnání se pouští znovu otevřením sheetu.
                    SummaryResult(compared.state, explain, onRetry = null)
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ProviderHeading(provider: AiProvider) {
    Text(
        stringResource(provider.title),
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
    )
}

/** Stav jednoho výpočtu: čekání, části, text s patičkou, nebo proč nic. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SummaryResult(state: ChatSummaryState, explain: Boolean, onRetry: (() -> Unit)?) {
    when (state) {
        ChatSummaryState.Idle -> Unit
        ChatSummaryState.Preparing -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            LoadingIndicator(Modifier.size(40.dp))
            Text(
                stringResource(if (explain) Res.string.explain_preparing else Res.string.summary_preparing),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is ChatSummaryState.ReadingParts -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.heightIn(min = 48.dp),
        ) {
            LoadingIndicator(Modifier.size(40.dp))
            Text(
                stringResource(Res.string.summary_reading_part, state.part, state.parts),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is ChatSummaryState.Text -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SelectionContainer {
                Text(state.text.trim(), style = MaterialTheme.typography.bodyLarge)
            }
            if (!state.done) LoadingIndicator(Modifier.size(32.dp))
            if (state.truncated) {
                Text(
                    stringResource(Res.string.summary_truncated),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            SummaryDetailsLine(state.details, state.elapsedMs)
        }
        ChatSummaryState.ModelMissing -> SummaryNote(Res.string.summary_model_missing)
        ChatSummaryState.NothingToSummarize -> SummaryNote(Res.string.summary_nothing)
        is ChatSummaryState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryNote(if (explain) Res.string.explain_failed else Res.string.summary_failed)
            // Proč — a kde to spravit, když je chyba v systému a opakování nepomůže.
            Text(
                state.reason.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            onRetry?.let {
                FilledTonalButton(onClick = it, shapes = ButtonDefaults.shapes()) { Text(stringResource(Res.string.summary_retry)) }
            }
        }
    }
}

@Composable
private fun SummaryNote(text: StringResource) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
}

/** Drobným písmem, který model psal a jak plný byl vstup — prototyp má ukázat, jak model funguje. */
@Composable
private fun SummaryDetailsLine(details: SummaryDetails, elapsedMs: Long?) {
    val parts = buildList {
        details.modelName?.let { add(stringResource(Res.string.summary_details_model, it)) }
        // Čas je to, co se při porovnání modelů vedle češtiny měří nejvíc.
        elapsedMs?.let { ms ->
            val seconds = stringResource(Res.string.decimal_number, (ms / 1000).toString(), ((ms % 1000) / 100).toString())
            add(stringResource(Res.string.summary_details_elapsed, seconds))
        }
        details.promptTokens?.let { tokens ->
            add(
                details.tokenLimit
                    ?.let { stringResource(Res.string.summary_details_tokens, tokens.grouped(), it.grouped()) }
                    ?: stringResource(Res.string.summary_details_tokens_no_limit, tokens.grouped()),
            )
        }
        if (details.parts > 1) {
            add(pluralStringResource(Res.plurals.summary_details_parts, details.parts, details.parts))
        }
        if (details.keptLines < details.totalLines) {
            add(stringResource(Res.string.summary_details_trimmed, details.keptLines, details.totalLines))
        }
    }
    if (parts.isEmpty()) return
    Text(
        parts.joinToString(" · "),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
