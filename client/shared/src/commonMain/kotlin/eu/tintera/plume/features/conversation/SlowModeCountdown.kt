package eu.tintera.plume.features.conversation

import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_slow_mode_wait
import kotlin.time.Clock
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/*
 * Odpočet pomalého režimu místo odesílacího tlačítka.
 *
 * Ve skupině s pomalým režimem dřív zpráva odešla a vrátila se chybou „Další zprávu půjde
 * poslat za 27 s" — uživatel ji napsal zbytečně, nebo ji musel psát znovu. Odpočet řekne
 * předem, KDY to půjde; psát se dá dál, jen odeslat ne. Ťuknutí řekne totéž větou (snackbar),
 * protože samotné číslo neříká proč.
 */

/**
 * Kolik celých sekund zbývá do [until] (epoch ms), každou sekundu znovu; 0 = nečeká se.
 * Tiká se na hranu sekundy, ne po pevné sekundě od spuštění — jinak by číslo naskakovalo
 * o kus později, než skutečná sekunda uplyne.
 */
@Composable
internal fun rememberSlowModeSecondsLeft(until: Long?): Int {
    var left by remember(until) { mutableIntStateOf(secondsLeft(until, Clock.System.now().toEpochMilliseconds())) }
    LaunchedEffect(until) {
        until ?: return@LaunchedEffect
        while (true) {
            val now = Clock.System.now().toEpochMilliseconds()
            left = secondsLeft(until, now)
            if (left <= 0) break
            delay((until - now) % MILLIS_PER_SECOND + 1)
        }
    }
    return left
}

/** Celé sekundy do [until], zaokrouhlené nahoru: „0:00" by tvrdilo, že už jde psát. */
internal fun secondsLeft(until: Long?, now: Long): Int =
    if (until == null || until <= now) 0 else ((until - now + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()

/** „0:27", „4:05" — jako čas hlasovky, aby se nezdálo, že jde o jinou jednotku. */
internal fun slowModeLabel(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/**
 * Tlačítko s odpočtem. Tonální, ne plné jako odeslání: nic neudělá, jen vysvětlí. Stejná
 * velikost a tvar jako tlačítko, které zastupuje, takže pole při výměně neposkočí.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SlowModeCountdownButton(seconds: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.conversation_slow_mode_wait, seconds)
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(
            shape = IconButtonDefaults.mediumRoundShape,
            pressedShape = IconButtonDefaults.mediumPressedShape,
        ),
        modifier = modifier
            .size(IconButtonDefaults.mediumContainerSize())
            .semantics { contentDescription = description },
    ) {
        Text(slowModeLabel(seconds), style = MaterialTheme.typography.labelLarge.tabularNumbers())
    }
}

private const val MILLIS_PER_SECOND = 1000L
