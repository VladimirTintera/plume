package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable

/** Stavový řádek tu aplikace neschovává, jeho místo je tedy to viditelné. */
@Composable
actual fun statusBarArea(): WindowInsets = WindowInsets.statusBars
