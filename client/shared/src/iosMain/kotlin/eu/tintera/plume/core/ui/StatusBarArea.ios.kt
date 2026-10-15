package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable

/**
 * Viditelné místo: schovaná lišta (video na celé obrazovce, `HideSystemBars`) se tu počítá jako
 * žádná. Výřez a ostrůvek drží `safeAreaInsets` dál, takže pod ně se stejně nic nedostane.
 */
@Composable
actual fun statusBarArea(): WindowInsets = WindowInsets.statusBars
