package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Na Androidu přehled úloh skrývá `FLAG_SECURE`, viz [SecureScreen]. */
@Composable
actual fun PrivacyCover(enabled: Boolean, color: Color) = Unit
