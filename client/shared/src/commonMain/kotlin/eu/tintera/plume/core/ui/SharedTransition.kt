package eu.tintera.plume.core.ui

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.compositionLocalOf

/**
 * Scope pro sdílené prvky mezi obrazovkami. Poskytuje ho kořenové téma, protože
 * [androidx.compose.animation.SharedTransitionLayout] musí obalovat celý navigační strom — jinak
 * sdílený prvek nepřežije výměnu obrazovky.
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * Sdílený prvek, jehož viditelnost řídí volající.
 *
 * Běžné `sharedElement` chce `AnimatedVisibilityScope`, který vzniká jen uvnitř `AnimatedVisibility`
 * nebo `AnimatedContent`. Položka `LazyColumn` ani obsah vstupního pole v takovém scope nejsou,
 * takže se páruje přes variantu s ručně řízenou viditelností: právě jedna strana je `visible`,
 * druhá `false`, a přepnutí spustí přechod mezi jejich pozicemi.
 *
 * Když scope chybí (teoreticky mimo `AppTheme`), modifikátor se neaplikuje a vykreslení
 * funguje dál bez animace.
 */
@Composable
fun Modifier.sharedFlight(key: String, visible: Boolean): Modifier {
    val scope = LocalSharedTransitionScope.current ?: return this

    return with(scope) {
        this@sharedFlight.sharedElementWithCallerManagedVisibility(
            sharedContentState = rememberSharedContentState(key = key),
            visible = visible,
            boundsTransform = { _, _ -> flightSpec },
        )
    }
}

/**
 * Průlet textu z pole do bubliny. Jemný bounce jako zbytek aplikace, ale kratší — je to dlouhá
 * dráha přes celou obrazovku a delší doba by působila líně.
 */
private val flightSpec = spring<Rect>(
    dampingRatio = 0.8f,
    stiffness = Spring.StiffnessMediumLow,
)
