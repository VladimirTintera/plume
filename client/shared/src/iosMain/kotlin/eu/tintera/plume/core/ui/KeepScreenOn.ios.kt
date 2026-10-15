package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import platform.UIKit.UIApplication

/** `idleTimerDisabled` platí pro celou aplikaci; `AVPlayerLayer` ho na rozdíl od `AVPlayerViewController` nenastaví. */
@Composable
actual fun KeepScreenOn() {
    DisposableEffect(Unit) {
        requests++
        UIApplication.sharedApplication.idleTimerDisabled = true
        onDispose {
            requests--
            UIApplication.sharedApplication.idleTimerDisabled = requests > 0
        }
    }
}

private var requests = 0
