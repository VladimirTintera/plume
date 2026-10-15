package eu.tintera.plume.core.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration
import java.util.WeakHashMap

@Composable
actual fun screenOrientationLockSupported(): Boolean =
    LocalActivity.current != null && LocalConfiguration.current.smallestScreenWidthDp < LARGE_SCREEN_DP

/**
 * `Activity.requestedOrientation`. Aktivita se otočením znovu nezakládá (`configChanges`
 * v manifestu), takže přehrávač i stav prohlížeče přežijí.
 *
 * Na šířku `SENSOR_LANDSCAPE`: obě strany podle toho, jak uživatel telefon drží, a i s vypnutým
 * automatickým otáčením — o otočení si řekl tlačítkem. Na výšku jen `PORTRAIT`; vzhůru nohama
 * telefony stejně nenabízejí.
 */
@Composable
actual fun LockScreenOrientation(orientation: ScreenOrientation?) {
    val activity = LocalActivity.current ?: return
    DisposableEffect(activity, orientation) {
        if (orientation == null) return@DisposableEffect onDispose {}
        val request = Request(orientation)
        val stack = requests.getOrPut(activity) { mutableListOf() }
        stack += request
        activity.apply(stack)
        onDispose {
            stack -= request
            activity.apply(stack)
            if (stack.isEmpty()) requests.remove(activity)
        }
    }
}

private class Request(val orientation: ScreenOrientation)

private fun Activity.apply(stack: List<Request>) {
    requestedOrientation = when (stack.lastOrNull()?.orientation) {
        ScreenOrientation.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        ScreenOrientation.Portrait -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        // Manifest orientaci nepředepisuje, takže „nic" je výchozí hodnota.
        null -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

/** Požadavky podle aktivity; jen z hlavního vlákna. */
private val requests = WeakHashMap<Activity, MutableList<Request>>()

private const val LARGE_SCREEN_DP = 600
