package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import platform.UIKit.UIScreen

/**
 * `UIScreen.brightness` — na rozdíl od Androidu **systémový jas**, ne jen pro aplikaci. iOS ho
 * po odchodu z aplikace sám nevrátí, takže se původní hodnota vrací s odchodem z kompozice
 * i s odchodem na pozadí (a po návratu se jas z gesta nastaví znovu). Stupnice je už vnímaná,
 * převod jako na Androidu netřeba.
 */
@Composable
actual fun rememberScreenBrightness(): ScreenBrightness? {
    val brightness = remember { ScreenBrightnessIos(UIScreen.mainScreen.brightness.toFloat()) }
    DisposableEffect(brightness) { onDispose { brightness.restore() } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { brightness.restore() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { brightness.reapply() }
    return brightness
}

private class ScreenBrightnessIos(private val original: Float) : ScreenBrightness {
    override var level by mutableFloatStateOf(original)
        private set
    private var changed = false

    override fun set(level: Float) {
        this.level = level.coerceIn(0f, 1f)
        changed = true
        UIScreen.mainScreen.brightness = this.level.toDouble()
    }

    fun restore() {
        if (changed) UIScreen.mainScreen.brightness = original.toDouble()
    }

    fun reapply() {
        if (changed) UIScreen.mainScreen.brightness = level.toDouble()
    }
}
