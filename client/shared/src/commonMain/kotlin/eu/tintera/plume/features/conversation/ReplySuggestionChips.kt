package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Návrhy odpovědi nad vstupním polem.
 *
 * Čipy, ne bublina v konverzaci: bublina na místě, kde by odpověď byla, vypadá jako odeslaná
 * zpráva a jednou za čas by si někdo myslel, že už odešla. Tenhle vzor znají lidé z Gmailu
 * a Google Zpráv.
 *
 * **Ťuknutí návrh jen VLOŽÍ do pole, neodesílá ho.** Odeslání musí zůstat vědomé — text píše
 * model a uživatel se pod něj podepisuje.
 *
 * Řádek se posouvá do strany: tři návrhy se na úzký telefon vedle sebe nevejdou a lámat je
 * do dvou řádků by pod prstem posouvalo celé pole.
 */
@Composable
fun ReplySuggestionChips(
    suggestions: List<String>,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Poslední neprázdná sada se drží kvůli odchodové animaci: v okamžiku skrytí je seznam už
    // prázdný a `AnimatedVisibility` by dobíhala s prázdným obsahem (tatáž past jako u odznaku
    // doručení a pruhu připnutých).
    val shown = remember { mutableStateOf(emptyList<String>()) }
    if (suggestions.isNotEmpty()) shown.value = suggestions
    val pick by rememberUpdatedState(onPick)

    AnimatedVisibility(
        visible = suggestions.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            shown.value.forEach { suggestion ->
                SuggestionChip(
                    onClick = { pick(suggestion) },
                    label = { Text(suggestion, style = MaterialTheme.typography.labelLarge) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    ),
                )
            }
        }
    }
}
