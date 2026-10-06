package eu.tintera.plume.core.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset

/**
 * Číslo, které se při změně PŘETOČÍ jako počítadlo: přetočí se jen číslice, které se změnily,
 * a když číslo roste, přijíždějí zdola, když klesá, shora (viz `CLAUDE.md`, „Material 3 /
 * vzhled"). Skoková výměna čísla v odznaku se skoro nedala postřehnout.
 *
 * Číslice se párují podle pozice ODZADU, takže při přechodu 9 → 10 jednotky přetočí a desítky
 * jen naskočí, místo aby se celé číslo posunulo o místo. [format] dovoluje oddělovače tisíců
 * ([grouped]); ty se přetočí, jen když se posunou na jiné místo.
 *
 * Číslice se ořezávají na svůj řádek ([clipToBounds]), jinak by při přetáčení vylézaly z odznaku.
 * Čtečce obrazovky se podává celé číslo, ne jednotlivé znaky.
 */
@Composable
fun RollingNumber(
    value: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    format: (Int) -> String = Int::toString,
) {
    // Směr se bere z celého čísla, ne z číslice: 19 → 20 je růst, i když jednotky klesly.
    // Obyčejné pole, ne stav: čte se až v přechodu a zápis do stavu by jen vynutil další kompozici.
    val last = remember { intArrayOf(value, 1) }
    if (value != last[0]) {
        last[1] = if (value > last[0]) 1 else -1
        last[0] = value
    }
    val text = format(value)
    val slide = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val fade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    Row(
        modifier
            .clipToBounds()
            .clearAndSetSemantics { this.text = AnnotatedString(text) },
    ) {
        text.forEachIndexed { index, char ->
            key(text.length - 1 - index) {
                AnimatedContent(
                    targetState = char,
                    transitionSpec = {
                        val direction = last[1]
                        (slideInVertically(slide) { it * direction } + fadeIn(fade))
                            .togetherWith(slideOutVertically(slide) { -it * direction } + fadeOut(fade))
                    },
                    label = "rolling-digit",
                ) { shown ->
                    Text(shown.toString(), style = style, color = color)
                }
            }
        }
    }
}
