package eu.tintera.plume.core.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Android: `LocationManager` bez Play služeb — kvůli jedné poloze se nevyplatí táhnout
 * `play-services-location`. Stačí přibližná poloha (`ACCESS_COARSE_LOCATION`), takže poskytovatel
 * je síťový, od Androidu 12 „fused" ze systému.
 */
@Composable
actual fun rememberDeviceLocation(): DeviceLocation {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<(GeoPoint?) -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val callback = pending[0] ?: return@rememberLauncherForActivityResult
        pending[0] = null
        if (granted) currentLocation(context, callback) else callback(null)
    }
    return remember(launcher) {
        object : DeviceLocation {
            override fun request(onResult: (GeoPoint?) -> Unit) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    currentLocation(context, onResult)
                } else {
                    pending[0] = onResult
                    launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
        }
    }
}

/**
 * Jedno zjištění polohy. Poslední známá poloha stačí, když je čerstvá (do deseti minut) — místa
 * v okolí se tak najdou hned; jinak se čeká na novou, nejvýš [LOCATION_TIMEOUT_MS].
 */
@SuppressLint("MissingPermission") // Volá se jen s uděleným oprávněním, viz výš.
private fun currentLocation(context: Context, onResult: (GeoPoint?) -> Unit) {
    val manager = context.getSystemService(LocationManager::class.java) ?: return onResult(null)
    val provider = listOf(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) LocationManager.FUSED_PROVIDER else null,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
    ).filterNotNull().firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        ?: return onResult(null)

    val recent = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        ?.takeIf { System.currentTimeMillis() - it.time < RECENT_LOCATION_MS }
    if (recent != null) return onResult(recent.toPoint())

    var delivered = false
    val handler = Handler(Looper.getMainLooper())
    val deliver: (Location?) -> Unit = { location ->
        if (!delivered) {
            delivered = true
            onResult(location?.toPoint())
        }
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val cancel = CancellationSignal()
        manager.getCurrentLocation(provider, cancel, ContextCompat.getMainExecutor(context)) { deliver(it) }
        handler.postDelayed({
            cancel.cancel()
            deliver(null)
        }, LOCATION_TIMEOUT_MS)
    } else {
        val listener = android.location.LocationListener { deliver(it) }
        @Suppress("DEPRECATION")
        manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        handler.postDelayed({
            manager.removeUpdates(listener)
            deliver(null)
        }, LOCATION_TIMEOUT_MS)
    }
}

private fun Location.toPoint() = GeoPoint(latitude = latitude, longitude = longitude)

/** Starší poslední známá poloha už může být jinde — hledala by se místa u včerejšího bytu. */
private const val RECENT_LOCATION_MS = 10 * 60 * 1000L
