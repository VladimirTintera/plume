package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable

/** Desktop video nepřehrává (otevírá ho systémová aplikace) a jas monitoru aplikaci nepatří. */
@Composable
actual fun rememberScreenBrightness(): ScreenBrightness? = null
