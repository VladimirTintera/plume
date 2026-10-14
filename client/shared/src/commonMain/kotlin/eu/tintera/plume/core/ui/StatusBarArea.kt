package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable

/**
 * Místo stavového řádku, i když je zrovna schovaný (Android `statusBarsIgnoringVisibility`).
 * Na to, co pod stavový řádek patřit nemá nikdy — ani když ho systém vynuceně drží vidět,
 * ačkoli aplikace ho schovat chtěla (a hlásí jí, že schovaný je).
 */
@Composable
expect fun statusBarArea(): WindowInsets
