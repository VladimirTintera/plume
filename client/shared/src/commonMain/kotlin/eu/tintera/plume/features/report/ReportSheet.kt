package eu.tintera.plume.features.report

import eu.tintera.plume.core.ui.rememberRestoringScrollState
import eu.tintera.plume.core.ui.FormTextField
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ReportOption
import eu.tintera.plume.core.telegram.ReportStep
import org.jetbrains.compose.resources.getString
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.core.ui.SheetActionRow
import eu.tintera.plume.core.ui.sheetActionShape
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.report_details_title
import eu.tintera.plume.resources.report_messages_body
import eu.tintera.plume.resources.report_messages_title
import eu.tintera.plume.resources.report_send
import eu.tintera.plume.resources.report_text_hint
import eu.tintera.plume.resources.report_text_optional_hint
import org.jetbrains.compose.resources.stringResource

/** Nejvýš tolik znaků bere `reportChat` jako podrobnosti. */
private const val REPORT_TEXT_MAX = 1024

/**
 * Průvodce nahlášením — jeden sheet, ve kterém se střídají kroky od serveru.
 *
 * **Otázky i důvody píše server**, v jazyce účtu. Vlastní seznam důvodů by se rozešel s tím, co
 * server přijme, a volba „spam" má u každého druhu chatu jiné id. Aplikace jen kreslí.
 *
 * Sheet jde celý, bez napůl rozbaleného stavu: důvodů bývá osm a u polovičního sheetu by
 * poslední skončily pod okrajem (tatáž past jako u správy členů).
 */
@Composable
fun ReportSheet(
    wizard: ReportWizard,
    onChoose: (ReportOption) -> Unit,
    onSubmitText: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberExpandedSheetState()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // Výměna kroku je prolnutí, ne posun: jde o jednu otázku za druhou na témž místě.
        val enter = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val exit = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(
            targetState = wizard.step,
            transitionSpec = { fadeIn(enter) togetherWith fadeOut(exit) },
            contentKey = { it::class },
            label = "report-step",
        ) { step ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberRestoringScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            ) {
                when (step) {
                    is ReportStep.ChooseOption -> OptionsStep(step, enabled = !wizard.busy, onChoose = onChoose)
                    is ReportStep.EnterText -> TextStep(step, enabled = !wizard.busy, onSubmit = onSubmitText)
                    ReportStep.NeedsMessages -> MessagesStep(onDismiss)
                    // Hotový průvodce se zavírá dřív, než by tenhle krok šel vykreslit.
                    ReportStep.Done, is ReportStep.Ended -> Unit
                }
            }
        }
    }
}

@Composable
private fun StepTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 16.dp),
    )
}

@Composable
private fun OptionsStep(step: ReportStep.ChooseOption, enabled: Boolean, onChoose: (ReportOption) -> Unit) {
    StepTitle(step.title)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        step.options.forEachIndexed { index, option ->
            SheetActionRow(
                icon = null,
                label = option.text,
                shape = sheetActionShape(index, step.options.size),
                enabled = enabled,
                reserveIconSpace = false,
                onClick = { onChoose(option) },
            )
        }
    }
}

@Composable
private fun ColumnScope.TextStep(step: ReportStep.EnterText, enabled: Boolean, onSubmit: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    StepTitle(stringResource(Res.string.report_details_title))
    // Tentýž tónovaný blok s rámečkem při psaní jako ostatní pole v aplikaci; dřív jeho
    // napodobenina, která fokus neukázala.
    FormTextField(
        value = text,
        onValueChange = { text = it.take(REPORT_TEXT_MAX) },
        placeholder = {
            Text(stringResource(if (step.optional) Res.string.report_text_optional_hint else Res.string.report_text_hint))
        },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        enabled = enabled,
        minLines = 3,
        modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
    )
    Button(
        onClick = { onSubmit(text) },
        enabled = enabled && (step.optional || text.isNotBlank()),
        modifier = Modifier.align(Alignment.End).padding(top = 16.dp),
        shapes = ButtonDefaults.shapes(),
    ) {
        Text(stringResource(Res.string.report_send))
    }
}

/**
 * Server chce vědět, které zprávy — a ty z nabídky nad chatem dodat nejde. Místo slepé uličky
 * se řekne, kudy vede cesta: nabídka nad zprávou má vlastní „Nahlásit".
 */
@Composable
private fun ColumnScope.MessagesStep(onDismiss: () -> Unit) {
    StepTitle(stringResource(Res.string.report_messages_title))
    Text(
        text = stringResource(Res.string.report_messages_body),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
    Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(top = 16.dp), shapes = ButtonDefaults.shapes()) {
        Text(stringResource(Res.string.common_close))
    }
}

/**
 * Průvodce a jeho výsledek na obrazovce: sheet, dokud se server ptá, a snackbar, když je hotovo
 * (nebo proč ne). Seznam chatů, profil i konverzace to kreslí stejně, takže jedno místo.
 */
@Composable
fun ReportHost(flow: ReportFlow, snackbarHostState: SnackbarHostState) {
    val wizard by flow.wizard.collectAsStateWithLifecycle()
    val outcome by flow.outcome.collectAsStateWithLifecycle()
    // Snackbar běží mimo efekt: vynulování výsledku mění klíč efektu a zrušilo by ho
    // (stejná past jako „Vrátit" u odepnuté zprávy).
    val snackbarScope = rememberCoroutineScope()

    LaunchedEffect(outcome) {
        val text = outcome ?: return@LaunchedEffect
        flow.consumeOutcome()
        val message = text.resolveNow()
        snackbarScope.launch { snackbarHostState.showSnackbar(message) }
    }

    wizard?.let {
        ReportSheet(
            wizard = it,
            onChoose = flow::choose,
            onSubmitText = flow::submitText,
            onDismiss = flow::cancel,
        )
    }
}
