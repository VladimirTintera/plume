package eu.tintera.plume.core.calls

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Hovory tu nejsou (bez mediální vrstvy), obraz se nekreslí. */
@Composable
actual fun CallVideo(source: CallVideoSource, mirror: Boolean, modifier: Modifier, fit: Boolean, onAspectRatio: (Float) -> Unit) = Unit
