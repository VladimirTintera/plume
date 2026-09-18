package eu.tintera.plume.app.root.presentation

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import eu.tintera.plume.app.root.presentation.theme.AppMotion
import eu.tintera.plume.core.settings.ThemeMode
import eu.tintera.plume.core.ui.LocalSharedTransitionScope

/** Podpora Material You dynamic color (Android 12+). Řídí default motivu i nabídku volby v Nastavení. */
expect val supportsDynamicColor: Boolean

/** Sladí vzhled systémových lišt (barvu ikon status/nav baru) s vybraným motivem. Reálně jen Android; jinde no-op. */
@Composable
expect fun SystemBarsAppearance(isDark: Boolean)

@Composable
expect fun colorScheme(isDark: Boolean, dynamic: Boolean): ColorScheme

/** Výchozí motiv, když uživatel nic nenastavil: Android → Dynamic (zachová Material You), jinde System. */
fun defaultThemeMode(): ThemeMode = if (supportsDynamicColor) ThemeMode.DYNAMIC else ThemeMode.SYSTEM

/** Efektivní „je tmavé téma" pro daný [ThemeMode] (SYSTEM/DYNAMIC → sledují systémový dark mód). */
@Composable
fun ThemeMode.resolveIsDark(): Boolean = when (this) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM, ThemeMode.DYNAMIC -> isSystemInDarkTheme()
}

/**
 * Kořenové téma aplikace.
 *
 * [SharedTransitionLayout] je tu schválně už teď, i když spike zatím sdílené prvky nepoužívá:
 * přechod seznam chatů → konverzace (avatar a jméno letí do top baru) je klíčová expressive animace
 * a scope musí být nad celým navigačním stromem, ne uvnitř jedné obrazovky.
 */
@Composable
fun AppTheme(
    themeMode: ThemeMode = defaultThemeMode(),
    content: @Composable () -> Unit,
) {
    val isDark = themeMode.resolveIsDark()
    val dynamic = themeMode == ThemeMode.DYNAMIC
    val colorScheme = colorScheme(isDark, dynamic)

    SystemBarsAppearance(isDark)

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = AppMotion,
    ) {
        SharedTransitionLayout {
            CompositionLocalProvider(
                LocalSharedTransitionScope provides this,
            ) {
                content()
            }
        }
    }
}
