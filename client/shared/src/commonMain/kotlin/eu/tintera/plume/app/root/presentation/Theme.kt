package eu.tintera.plume.app.root.presentation

import eu.tintera.plume.core.ui.RootOverlayHost
import eu.tintera.plume.core.ui.DarkSystemBarsRequests
import eu.tintera.plume.core.ui.LocalBackgroundBlurAllowed
import eu.tintera.plume.core.ui.LocalDarkSystemBars
import eu.tintera.plume.core.ui.rememberBackgroundBlurAllowed
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.materialkolor.ktx.animateColorScheme
import eu.tintera.plume.app.root.presentation.theme.AppMotion
import eu.tintera.plume.app.root.presentation.theme.appTypography
import eu.tintera.plume.app.root.presentation.theme.seed
import eu.tintera.plume.core.settings.ThemeColor
import eu.tintera.plume.core.settings.ThemeMode
import eu.tintera.plume.core.ui.LocalSharedTransitionScope

/** Podpora Material You dynamic color (Android 12+). Řídí default motivu i nabídku volby v Nastavení. */
expect val supportsDynamicColor: Boolean

/**
 * Sladí vzhled systémových lišt (barvu ikon status/nav baru) s tím, co pod nimi leží: [isDark] je
 * tmavý motiv NEBO trvající `DarkSystemBars`. Jediné místo, které ikony lišt okna activity mění.
 * Reálně jen Android; jinde no-op.
 */
@Composable
expect fun SystemBarsAppearance(isDark: Boolean)

/** Schéma aplikace: barvy z tapety ([dynamic]), jinak paleta dopočítaná ze [seed]. */
@Composable
expect fun colorScheme(isDark: Boolean, dynamic: Boolean, seed: Color): ColorScheme

/** Efektivní „je tmavé téma" pro daný [ThemeMode] (SYSTEM → sleduje systémový dark mód). */
@Composable
fun ThemeMode.resolveIsDark(): Boolean = when (this) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

/**
 * Je zvolený motiv tmavý? Kdo staví vlastní paletu uvnitř aplikace (barva konverzace), potřebuje
 * vědět, pro který režim — ze světlosti plochy se to během prolínání motivu poznat spolehlivě nedá.
 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }

/**
 * CÍLOVÉ schéma aplikace — stabilní instance, ne právě prolínané. Barva konverzace se k němu
 * vrací, když ji uživatel zruší; prolínané schéma je v každém snímku nová instance a cíl
 * animace by se s ním měnil pořád.
 */
val LocalAppColorScheme = staticCompositionLocalOf<ColorScheme?> { null }

/**
 * Kořenové téma aplikace.
 *
 * [SharedTransitionLayout] je tu schválně už teď, i když spike zatím sdílené prvky nepoužívá:
 * přechod seznam chatů → konverzace (avatar a jméno letí do top baru) je klíčová expressive animace
 * a scope musí být nad celým navigačním stromem, ne uvnitř jedné obrazovky.
 *
 * **Schéma se prolíná, nemění se skokem** ([animateColorScheme]): přepnutí motivu v Nastavení
 * přebarví celou obrazovku naráz a skoková výměna vypadala jako probliknutí. Každá role schématu
 * má vlastní [androidx.compose.animation.animateColor], takže se plní i plochy, které nejsou
 * přes sebe (pozadí stránky, bubliny, lišty) — překryvný crossfade celé aplikace by na průsvitných
 * místech prosvítal.
 */
@Composable
fun AppTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = supportsDynamicColor,
    themeColor: ThemeColor = ThemeColor.BLUE,
    content: @Composable () -> Unit,
) {
    val isDark = themeMode.resolveIsDark()
    val target = colorScheme(isDark, dynamicColor && supportsDynamicColor, themeColor.seed)
    val colorScheme = animateColorScheme(target, animationSpec = { AppMotion.defaultEffectsSpec<Color>() })

    val darkSystemBars = remember { DarkSystemBarsRequests() }
    SystemBarsAppearance(isDark || darkSystemBars.active)

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = AppMotion,
        typography = appTypography(),
    ) {
        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this,
                LocalBackgroundBlurAllowed provides rememberBackgroundBlurAllowed(),
                LocalDarkSystemBars provides darkSystemBars,
                LocalIsDarkTheme provides isDark,
                LocalAppColorScheme provides target,
            ) {
                // Bubliny z kořene kompozice, mimo měření obrazovek — viz WithTooltip.
                RootOverlayHost(content)
            }
        }
    }
}
