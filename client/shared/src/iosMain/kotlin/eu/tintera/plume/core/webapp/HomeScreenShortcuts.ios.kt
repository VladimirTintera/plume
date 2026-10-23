package eu.tintera.plume.core.webapp

import androidx.compose.runtime.Composable

/** iOS zástupce aplikace na plochu přidat neumí (jen Safari přes sdílení). */
@Composable
actual fun rememberHomeScreenShortcuts(): HomeScreenShortcuts = NoHomeScreenShortcuts
