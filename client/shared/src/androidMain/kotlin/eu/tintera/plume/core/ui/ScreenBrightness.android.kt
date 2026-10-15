package eu.tintera.plume.core.ui

import android.provider.Settings
import android.view.Window
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.sqrt

/**
 * `WindowManager.LayoutParams.screenBrightness` — přepíše jas jen pro okno aplikace, systémové
 * nastavení se nemění. S odchodem z kompozice se vrátí původní hodnota (obvykle „podle systému").
 *
 * - **Oprávnění `WRITE_SETTINGS` netřeba**, okno si jas přepisuje samo.
 * - **Výchozí jas se čte ze `SCREEN_BRIGHTNESS`** (0–255, lineární). S adaptivním jasem je to
 *   poslední ručně nastavená hodnota, ne ta, kterou zrovna drží senzor — první pohyb tak může
 *   jasem o kus skočit. Na zařízeních s jiným rozsahem (některé Xiaomi) taky; jen se to ořízne.
 * - **Okno bere jas lineárně, oko ho vnímá zhruba jako druhou odmocninu**: bez převodu by se
 *   horní polovina tahu skoro nepoznala a celá změna by se odehrála dole.
 */
@Composable
actual fun rememberScreenBrightness(): ScreenBrightness? {
    val activity = LocalActivity.current ?: return null
    val brightness = remember(activity) {
        val window = activity.window
        val original = window.attributes.screenBrightness
        val start = if (original >= 0f) {
            original
        } else {
            Settings.System.getInt(activity.contentResolver, Settings.System.SCREEN_BRIGHTNESS, DEFAULT_SETTING) / MAX_SETTING
        }
        WindowBrightness(window, original, sqrt(start.coerceIn(0f, 1f)))
    }
    DisposableEffect(brightness) { onDispose { brightness.restore() } }
    return brightness
}

private class WindowBrightness(
    private val window: Window,
    private val original: Float,
    start: Float,
) : ScreenBrightness {
    override var level by mutableFloatStateOf(start)
        private set

    override fun set(level: Float) {
        this.level = level.coerceIn(0f, 1f)
        // Nula by na některých zařízeních displej zhasla úplně (`BRIGHTNESS_OVERRIDE_OFF`).
        apply((this.level * this.level).coerceAtLeast(MIN_WINDOW_BRIGHTNESS))
    }

    fun restore() = apply(original)

    private fun apply(value: Float) {
        window.attributes = window.attributes.apply { screenBrightness = value }
    }
}

private const val DEFAULT_SETTING = 128
private const val MAX_SETTING = 255f
private const val MIN_WINDOW_BRIGHTNESS = 0.004f
