package eu.tintera.plume.features.calls

import eu.tintera.plume.core.ui.rememberRestoringScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.CallProblem
import eu.tintera.plume.core.telegram.CallRatingRequest
import eu.tintera.plume.core.telegram.CallRepository
import eu.tintera.plume.core.ui.FormTextField
import eu.tintera.plume.core.ui.MediumButton
import eu.tintera.plume.core.ui.SheetTitle
import eu.tintera.plume.core.ui.rememberExpandedSheetState
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.call_problem_distorted_speech
import eu.tintera.plume.resources.call_problem_distorted_video
import eu.tintera.plume.resources.call_problem_dropped
import eu.tintera.plume.resources.call_problem_echo
import eu.tintera.plume.resources.call_problem_interruptions
import eu.tintera.plume.resources.call_problem_noise
import eu.tintera.plume.resources.call_problem_pixelated_video
import eu.tintera.plume.resources.call_problem_silent_local
import eu.tintera.plume.resources.call_problem_silent_remote
import eu.tintera.plume.resources.call_rating_comment
import eu.tintera.plume.resources.call_rating_problems
import eu.tintera.plume.resources.call_rating_send
import eu.tintera.plume.resources.call_rating_star
import eu.tintera.plume.resources.call_rating_text
import eu.tintera.plume.resources.call_rating_title
import eu.tintera.plume.resources.star_24dp
import eu.tintera.plume.resources.star_filled_24dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Hodnocení hovoru, o které server po skončeném hovoru požádal (`needRating`). Ukáže se až po
 * zmizení obrazovky hovoru — přes ni by se hodnotilo něco, co ještě dobíhá. Zavření sheetu je
 * „teď ne": nic se neposílá a znovu se nezeptáme.
 */
@Composable
fun CallRatingHost() {
    val calls = koinInject<CallRepository>()
    val request by calls.ratingRequest.collectAsStateWithLifecycle()
    val active by calls.active.collectAsStateWithLifecycle()
    val pending = request?.takeIf { active == null } ?: return
    CallRatingSheet(pending, calls)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun CallRatingSheet(request: CallRatingRequest, calls: CallRepository) {
    val scope = rememberCoroutineScope()
    var stars by rememberSaveable(request.callId) { mutableIntStateOf(0) }
    var problems by remember(request.callId) { mutableStateOf(emptySet<CallProblem>()) }
    var comment by rememberSaveable(request.callId) { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = calls::dismissRating, sheetState = rememberExpandedSheetState()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberRestoringScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        ) {
            SheetTitle(stringResource(Res.string.call_rating_title))
            Text(
                stringResource(Res.string.call_rating_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                for (value in 1..5) {
                    val filled = value <= stars
                    IconToggleButton(
                        checked = filled,
                        onCheckedChange = { stars = value },
                        shapes = IconButtonDefaults.toggleableShapes(),
                        modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
                    ) {
                        Icon(
                            painterResource(if (filled) Res.drawable.star_filled_24dp else Res.drawable.star_24dp),
                            contentDescription = stringResource(Res.string.call_rating_star, value),
                            tint = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                        )
                    }
                }
            }
            // Co nefungovalo, má smysl jen u horšího hodnocení; u pěti hvězd ho TDLib ani nebere.
            if (stars in 1..4) {
                Text(
                    stringResource(Res.string.call_rating_problems),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.semantics { heading() },
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CallProblem.entries.filter { !it.videoOnly || request.isVideo }.forEach { problem ->
                        FilterChip(
                            selected = problem in problems,
                            onClick = { problems = if (problem in problems) problems - problem else problems + problem },
                            label = { Text(stringResource(problem.label())) },
                            shapes = FilterChipDefaults.shapes(),
                        )
                    }
                }
                FormTextField(
                    value = comment,
                    onValueChange = { comment = it.take(COMMENT_LIMIT) },
                    label = { Text(stringResource(Res.string.call_rating_comment)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                )
            }
            MediumButton(
                onClick = {
                    val sent = stars
                    // Sheet odejde hned; odeslání doběhne na pozadí, chybu jen zaloguje repository.
                    scope.launch { calls.rate(request, sent, comment, problems) }
                },
                enabled = stars > 0,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                Text(stringResource(Res.string.call_rating_send))
            }
        }
    }
}

private fun CallProblem.label(): StringResource = when (this) {
    CallProblem.Echo -> Res.string.call_problem_echo
    CallProblem.Noise -> Res.string.call_problem_noise
    CallProblem.Interruptions -> Res.string.call_problem_interruptions
    CallProblem.DistortedSpeech -> Res.string.call_problem_distorted_speech
    CallProblem.SilentLocal -> Res.string.call_problem_silent_local
    CallProblem.SilentRemote -> Res.string.call_problem_silent_remote
    CallProblem.Dropped -> Res.string.call_problem_dropped
    CallProblem.DistortedVideo -> Res.string.call_problem_distorted_video
    CallProblem.PixelatedVideo -> Res.string.call_problem_pixelated_video
}

/** Delší komentář server k hodnocení nebere; víc znaků by jen tiše zmizelo. */
private const val COMMENT_LIMIT = 1024
