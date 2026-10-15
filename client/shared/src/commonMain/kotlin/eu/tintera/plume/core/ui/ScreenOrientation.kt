package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable

/** Orientace, kterou si uživatel vynutil ručně (tlačítko otočení u videa). */
enum class ScreenOrientation { Portrait, Landscape }

/**
 * Dá se tu orientace vynutit?
 *
 * Android 16 a novější požadavek na orientaci na velkém displeji (nejmenší šířka od 600 dp:
 * tablet, rozložený Fold) **ignoruje** a aplikace s `targetSdk` 37 se z toho vyvázat nemůže —
 * tlačítko by tam nedělalo nic. iOS zatím ne (viz [LockScreenOrientation]).
 */
@Composable
expect fun screenOrientationLockSupported(): Boolean

/**
 * Vynutí [orientation], dokud je v kompozici; `null` = nic nežádá, platí senzor a systémová volba
 * automatického otáčení.
 *
 * **Požadavky se skládají na zásobník, ne uložit a vrátit** — platí poslední živý, a když žádný
 * není, orientace se pustí. Uložit a vrátit se rozbije, jakmile se dvě obrazovky překryjí mimo
 * pořadí LIFO (viz [DarkSystemBars]).
 */
@Composable
expect fun LockScreenOrientation(orientation: ScreenOrientation?)
