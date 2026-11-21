package eu.tintera.plume.core.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Stav pole ([TextFieldState]) svázaný s hodnotou zvenku: psaní jde do [onValueChange], změna
 * [value] zvenku (ViewModel pole vyčistí) se do stavu promítne. Pro pole, která od Compose 1.13
 * existují jen se stavem (`SecureTextField`, `BasicTextField(state)`), u komponent, jejichž
 * volající drží hodnotu jako `String` (formuláře, hledání, kód).
 *
 * **Vědomě `remember`, ne `rememberTextFieldState`**: ten je `rememberSaveable` a heslo by se
 * serializovalo do uloženého stavu (a s ním případně na disk). Hodnotu drží volající.
 */
@Composable
internal fun rememberSyncedTextFieldState(value: String, onValueChange: (String) -> Unit): TextFieldState {
    val state = remember { TextFieldState(value) }
    SyncState(
        state = state,
        value = value,
        read = { state.text.toString() },
        write = { state.setTextAndPlaceCursorAtEnd(it) },
        onValueChange = onValueChange,
    )
    return state
}

/**
 * Totéž pro text **i s výběrem** ([TextFieldValue]): vstupní pole zpráv, kde koncept
 * (`ComposerDraft`) potřebuje polohu kurzoru — vkládá na ni zmínky a emoji, podle výběru zapíná
 * formátování. Změna zvenku přenese do pole text i výběr; pohyb kurzoru v poli jde ven taky.
 */
@Composable
internal fun rememberSyncedTextFieldState(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit): TextFieldState {
    val state = remember { TextFieldState(value.text, value.selection) }
    SyncState(
        state = state,
        value = value.copy(composition = null),
        read = { TextFieldValue(state.text.toString(), state.selection) },
        write = { next ->
            state.edit {
                if (asCharSequence().toString() != next.text) replace(0, length, next.text)
                selection = TextRange(next.selection.start.coerceIn(0, length), next.selection.end.coerceIn(0, length))
            }
        },
        onValueChange = onValueChange,
    )
    return state
}

/**
 * Obousměrné svázání [state] s [value]. Co pole samo poslalo ven od poslední změny zvenku, se
 * pamatuje: hodnota ze ViewModelu (`StateFlow`) dorazí o dispatch později a při rychlém psaní
 * přijde „a", když pole už má „ab". Přepsat pole ozvěnou by ztratilo úhoz a skočilo kurzorem —
 * zvenku se proto bere jen hodnota, kterou pole neposlalo (vyčištění, doplnění, úprava filtrem
 * ve ViewModelu, vložená zmínka).
 */
@Composable
private fun <T> SyncState(
    state: TextFieldState,
    value: T,
    read: () -> T,
    write: (T) -> Unit,
    onValueChange: (T) -> Unit,
) {
    val sent = remember(state) { ArrayDeque<T>() }
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentRead by rememberUpdatedState(read)
    LaunchedEffect(value) {
        if (currentRead() == value) {
            sent.clear()
        } else if (value !in sent) {
            sent.clear()
            write(value)
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { currentRead() }.collect { current ->
            if (current != currentValue) {
                sent.addLast(current)
                if (sent.size > SENT_HISTORY) sent.removeFirst()
                currentOnValueChange(current)
            }
        }
    }
}

/** Kolik vlastních hodnot si pole pamatuje, aby poznalo jejich opožděnou ozvěnu. */
private const val SENT_HISTORY = 32
