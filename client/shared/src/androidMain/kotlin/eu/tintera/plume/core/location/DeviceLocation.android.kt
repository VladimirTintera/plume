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
 * `play-services-location`. Přibližná poloha (`ACCESS_COARSE_LOCATION`) jde přes síťového
 * poskytovatele, od Androidu 12 „fused" ze systému.
 *
 * **Přesná se žádá spolu s přibližnou** (od Androidu 12 tak systém nabídne volbu „přesná /
 * přibližná"); stačí kterékoli z nich. S přesnou dodá „fused" (nebo GPS) polohu na metry,
 * s přibližnou totéž co jinde.
 */
@Composable
actual fun rememberDeviceLocation(precise: Boolean): DeviceLocation {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<(GeoPoint?) -> Unit>(1) }
    val permissions = if (precise) {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    } else {
        arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val callback = pending[0] ?: return@rememberLauncherForActivityResult
        pending[0] = null
        if (result.values.any { it }) currentLocation(context, precise, callback) else callback(null)
    }
    return remember(launcher, precise) {
        object : DeviceLocation {
            override fun request(onResult: (GeoPoint?) -> Unit) {
                val granted = permissions.map { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
                // U přesné se ptá, dokud není udělená ona — přibližnou mohl uživatel povolit
                // dřív kvůli hledání míst, a to by tady stačit nemělo.
                if (granted.first() || (!precise && granted.any { it })) {
                    currentLocation(context, precise, onResult)
                } else {
                    pending[0] = onResult
                    launcher.launch(permissions)
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
private fun currentLocation(context: Context, precise: Boolean, onResult: (GeoPoint?) -> Unit) {
    val manager = context.getSystemService(LocationManager::class.java) ?: return onResult(null)
    val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val provider = listOf(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) LocationManager.FUSED_PROVIDER else null,
        // GPS jen s přesnou polohou — s přibližnou ho systém nepustí a síť je rychlejší.
        if (precise && fine) LocationManager.GPS_PROVIDER else null,
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
    ).filterNotNull().firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        ?: return onResult(null)

    // Pro bota se posílá poloha TEĎ: poslední známá musí být skoro čerstvá.
    val maxAge = if (precise) RECENT_PRECISE_LOCATION_MS else RECENT_LOCATION_MS
    val lastKnown = runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
    val age = lastKnown?.let { System.currentTimeMillis() - it.time }
    if (lastKnown != null && age!! < maxAge) return onResult(lastKnown.toPoint())
    // Když čerstvá nepřijde (GPS v budově, emulátor), je lepší pár minut stará poloha než žádná.
    val fallback = lastKnown?.takeIf { age!! < RECENT_LOCATION_MS }

    var delivered = false
    val handler = Handler(Looper.getMainLooper())
    val deliver: (Location?) -> Unit = { location ->
        if (!delivered) {
            delivered = true
            onResult((location ?: fallback)?.toPoint())
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

/** Poloha posílaná botovi má být, kde uživatel je teď, ne kde byl před čtvrthodinou. */
private const val RECENT_PRECISE_LOCATION_MS = 60 * 1000L

actual val liveLocationSharingSupported: Boolean = true
