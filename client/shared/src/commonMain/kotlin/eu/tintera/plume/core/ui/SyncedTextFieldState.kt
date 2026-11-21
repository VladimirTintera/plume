package eu.tintera.plume.core.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow

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
    // Co pole samo poslalo ven od poslední změny zvenku. Hodnota ze ViewModelu (`StateFlow`)
    // dorazí o dispatch později: při rychlém psaní přijde „a", když pole už má „ab". Přepsat
    // pole ozvěnou by ztratilo úhoz a skočilo kurzorem — zvenku se proto bere jen hodnota,
    // kterou pole neposlalo (vyčištění, doplnění, úprava filtrem ve ViewModelu).
    val sent = remember { ArrayDeque<String>() }
    val currentValue by rememberUpdatedState(value)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    LaunchedEffect(value) {
        if (state.text.toString() == value) {
            sent.clear()
        } else if (value !in sent) {
            sent.clear()
            state.setTextAndPlaceCursorAtEnd(value)
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }.collect { text ->
            if (text != currentValue) {
                sent.addLast(text)
                if (sent.size > SENT_HISTORY) sent.removeFirst()
                currentOnValueChange(text)
            }
        }
    }
    return state
}

/** Kolik vlastních hodnot si pole pamatuje, aby poznalo jejich opožděnou ozvěnu. */
private const val SENT_HISTORY = 32

