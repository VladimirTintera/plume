package eu.tintera.plume.core.ui

import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import java.util.WeakHashMap

/**
 * `View.keepScreenOn` na view kompozice: okno nezhasíná, dokud je view připojené a viditelné.
 *
 * Video se kreslí do `TextureView`, a tak displej nedržel nikdo — `PlayerView` z Media3 by to
 * dělal sám, jenže ten kreslí do `SurfaceView` (viz `VideoSurface`). Na samotném `TextureView` to
 * být nemůže: tentýž povrch mají i GIFy v bublinách, které hrají pořád dokola.
 */
@Composable
actual fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        val count = (requests[view] ?: 0) + 1
        requests[view] = count
        view.keepScreenOn = true
        onDispose {
            val left = (requests[view] ?: 1) - 1
            if (left > 0) requests[view] = left else requests.remove(view)
            view.keepScreenOn = left > 0
        }
    }
}

/** Kolik požadavků drží které view; jen z hlavního vlákna. */
private val requests = WeakHashMap<View, Int>()
