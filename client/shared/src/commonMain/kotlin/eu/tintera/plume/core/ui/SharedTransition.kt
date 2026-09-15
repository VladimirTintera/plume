package eu.tintera.plume.core.ui

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.navigation3.ui.LocalNavAnimatedContentScope
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

/**
 * Je právě vidět dvoupanelové rozvržení?
 *
 * Sdílený prvek má smysl jen tam, kde se obrazovky **vyměňují**. V dvoupanelovém rozvržení zůstává
 * seznam vedle konverzace, takže obě strany páru existují naráz — avatar by odletěl z položky,
 * která je pořád vidět, a navíc by se o jeden klíč přetahovaly dva prvky. Shell tenhle příznak
 * poskytuje, aby o něm obrazovky nemusely nic vědět.
 */
val LocalTwoPane = compositionLocalOf { false }

/**
 * Sdílený prvek napříč obrazovkami `NavDisplay` (položka seznamu → detail).
 *
 * Na rozdíl od [sharedFlight] se viditelnost neřídí ručně: obě strany jsou v `AnimatedContent`
 * NavDisplaye, takže si scope řekne sám, která odchází a která přichází.
 */
@Composable
fun Modifier.sharedNavElement(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    if (LocalTwoPane.current) return this

    return with(shared) {
        this@sharedNavElement.sharedElement(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
            boundsTransform = { _, _ -> flightSpec },
        )
    }
}

/**
 * Totéž pro text.
 *
 * Text se sdílí přes `sharedBounds` + `ScaleToBounds`, ne přes `sharedElement`: název chatu má
 * v seznamu jinou velikost písma než v hlavičce konverzace a prosté sdílení prvku by nechalo text
 * přeskládat skokem na konci letu. `ScaleToBounds` místo toho celý text plynule zvětší.
 */
@Composable
fun Modifier.sharedNavText(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    if (LocalTwoPane.current) return this

    return with(shared) {
        this@sharedNavText.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
            boundsTransform = { _, _ -> flightSpec },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.CenterStart,
            ),
        )
    }
}

/*
 * Klíče páru seznam ↔ konverzace. Obsahují id chatu, jinak by se při rychlém přepnutí mezi chaty
 * spároval avatar jednoho chatu s avatarem druhého.
 */
fun chatAvatarKey(chatId: Long): String = "chat-avatar-$chatId"

fun chatTitleKey(chatId: Long): String = "chat-title-$chatId"
