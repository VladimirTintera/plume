package eu.tintera.plume.core.webapp

import androidx.compose.runtime.Composable

/** Desktop plochu s připnutými zástupci aplikací nemá. */
@Composable
actual fun rememberHomeScreenShortcuts(): HomeScreenShortcuts = NoHomeScreenShortcuts
