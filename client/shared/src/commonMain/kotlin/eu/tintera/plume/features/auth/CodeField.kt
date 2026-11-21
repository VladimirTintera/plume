package eu.tintera.plume.features.auth

import eu.tintera.plume.core.ui.rememberSyncedTextFieldState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.InputTransformation
import eu.tintera.plume.core.ui.BLOCK_OUTLINE
import eu.tintera.plume.core.ui.blockOutlineColor
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.tabularNumbers

/**
 * Zadání číselného kódu: jedno políčko na číslici.
 *
 * **Uvnitř je JEDNO pole, políčka jsou jen dekorace** (`decorator`), ne [digits] samostatných
 * `TextField`ů. Řada polí znamená přehazovat fokus po každém úhozu, ručně řešit backspace přes
 * hranici políčka a hlavně přijít o vložení ze schránky i o autofill — celý kód by spadl do
 * prvního políčka. S jedním polem to všechno zůstává, jak to platforma umí sama.
 *
 * **`innerTextField()` se MUSÍ složit**, i když není vidět: nese rozvržení textu, bez kterého
 * pole nefunguje. Je proto průhledné (barva textu i kurzoru) a leží pod políčky — číslice
 * kreslí políčka sama.
 *
 * Fokus si pole bere samo. Zadat kód je jediná věc, kterou na téhle obrazovce jde udělat,
 * takže klávesnice nemá na co čekat.
 */
@Composable
internal fun CodeField(
    value: String,
    digits: Int,
    isError: Boolean,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(digits) { focusRequester.requestFocus() }

    val state = rememberSyncedTextFieldState(value, onValueChange)
    BasicTextField(
        state = state,
        // Jen číslice a nejvýš [digits] — i u vloženého textu („Kód: 12345").
        inputTransformation = remember(digits) {
            InputTransformation {
                val typed = asCharSequence().toString()
                val code = codeDigits(typed, digits)
                if (code != typed) replace(0, length, code)
            }
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        textStyle = TextStyle(color = Color.Transparent),
        cursorBrush = SolidColor(Color.Transparent),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Go,
            autoCorrectEnabled = false,
        ),
        onKeyboardAction = { onSubmit() },
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            // Aby klávesnice nabídla kód z došlé SMS. Deklarace navíc nic nestojí a bez ní
            // musí autofill hádat podle okolí.
            .semantics { contentType = ContentType.SmsOtpCode },
        decorator = { innerTextField ->
            // Průhledné pole leží pod políčky; kreslí se z něj jen výběr a případné úchyty,
            // takže se vloženému kódu ze schránky nic nepostaví do cesty.
            innerTextField()
            BoxWithConstraints(
                // Šest samostatných `Text`ů by čtečka obrazovky přečetla jako šest položek;
                // hodnotu hlásí samo pole, tohle je kresba.
                modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
            ) {
                val height = codeCellHeight(maxWidth, digits, CODE_CELL_GAP, CODE_CELL_MAX_HEIGHT)
                Row(horizontalArrangement = Arrangement.spacedBy(CODE_CELL_GAP)) {
                    repeat(digits) { index ->
                        CodeCell(
                            digit = state.text.getOrNull(index),
                            active = focused && index == activeCodeCell(state.text.length, digits),
                            isError = isError,
                            height = height,
                        )
                    }
                }
            }
        },
    )
}

/**
 * Jedno políčko. Šířku dává váha, ne pevný rozměr — pětimístný i šestimístný kód tak vyplní
 * řádek stejně a na úzkém telefonu se políčka zúží místo toho, aby přetekla.
 */
@Composable
private fun RowScope.CodeCell(digit: Char?, active: Boolean, isError: Boolean, height: Dp) {
    Surface(
        shape = CODE_CELL_SHAPE,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        border = BorderStroke(BLOCK_OUTLINE, blockOutlineColor(isError = isError, active = active)),
        modifier = Modifier.weight(1f).height(height),
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(targetState = digit, label = "code-cell-digit") { shown ->
                if (shown != null) {
                    Text(
                        text = shown.toString(),
                        style = MaterialTheme.typography.headlineMedium.tabularNumbers(),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private val CODE_CELL_MAX_HEIGHT = 64.dp
private val CODE_CELL_GAP = 8.dp

/**
 * Zaoblení v procentech kratší strany, ne v dp: pevných 16 dp ukrojí z políčka širokého 45 dp
 * (šest číslic na telefonu širokém 360 dp) skoro celou hranu a zbude z něj ovál. Takhle drží
 * poměr na každé šířce.
 */
private val CODE_CELL_SHAPE = RoundedCornerShape(percent = 25)
