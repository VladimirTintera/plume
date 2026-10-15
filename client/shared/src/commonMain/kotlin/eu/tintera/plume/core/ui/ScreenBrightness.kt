package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable

/**
 * Jas displeje nastavený z aplikace (gesto v přehrávači videa).
 *
 * - **Platí, jen dokud je v kompozici** ([rememberScreenBrightness]); s odchodem se vrátí, co
 *   bylo. Jako YouTube: jas z přehrávače nepřežije zavření videa.
 * - **[level] je jas, jak ho vidí oko** (0 tma, 1 naplno), ne výkon podsvícení. Na Androidu
 *   se převádí, aby tah prstem měnil jas rovnoměrně po celé délce; iOS takovou stupnici má sám.
 */
interface ScreenBrightness {
    val level: Float

    fun set(level: Float)
}

/** `null`, kde jas měnit nejde (desktop). */
@Composable
expect fun rememberScreenBrightness(): ScreenBrightness?
