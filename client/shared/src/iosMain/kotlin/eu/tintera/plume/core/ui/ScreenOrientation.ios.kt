package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable

/**
 * iOS zatím ne. `requestGeometryUpdate` by telefon otočil, jenže kořenový controller je SwiftUI
 * (`UIHostingController`), který podporuje všechny orientace z Info.plist — první pohyb telefonem
 * by orientaci zase vrátil. Zamknout ji jde jen přes `supportedInterfaceOrientationsFor`
 * v delegátu aplikace, který projekt nemá.
 */
@Composable
actual fun screenOrientationLockSupported(): Boolean = false

@Composable
actual fun LockScreenOrientation(orientation: ScreenOrientation?) = Unit
