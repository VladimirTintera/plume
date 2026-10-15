package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable

/** Okno na ploše se neotáčí. */
@Composable
actual fun screenOrientationLockSupported(): Boolean = false

@Composable
actual fun LockScreenOrientation(orientation: ScreenOrientation?) = Unit
