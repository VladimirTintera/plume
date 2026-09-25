package eu.tintera.plume.core.location

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLLocationAccuracyKilometer
import platform.Foundation.NSError
import platform.darwin.NSObject
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import platform.darwin.DISPATCH_TIME_NOW

/**
 * iOS: `CLLocationManager` s přesností na kilometr — na místa v okolí stačí. Oprávnění „při
 * používání" se žádá až tady (`NSLocationWhenInUseUsageDescription` v projektu).
 *
 * Manažer i delegát se drží silnou referencí: `CLLocationManager` má delegáta jen slabě a bez
 * toho by ho garbage collector uklidil dřív, než poloha dorazí — stejná past jako u výběru příloh.
 */
@Composable
actual fun rememberDeviceLocation(): DeviceLocation = remember { IosDeviceLocation() }

@OptIn(ExperimentalForeignApi::class)
private class IosDeviceLocation : DeviceLocation {
    private val manager = CLLocationManager().apply { desiredAccuracy = kCLLocationAccuracyKilometer }
    private var pending: ((GeoPoint?) -> Unit)? = null

    private val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
            if (pending == null) return
            when (manager.authorizationStatus) {
                kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways -> manager.requestLocation()
                kCLAuthorizationStatusNotDetermined -> Unit
                else -> deliver(null)
            }
        }

        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            val location = didUpdateLocations.lastOrNull() as? CLLocation
            deliver(
                location?.coordinate?.useContents { GeoPoint(latitude = latitude, longitude = longitude) },
            )
        }

        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
            deliver(null)
        }
    }

    init {
        manager.delegate = delegate
    }

    override fun request(onResult: (GeoPoint?) -> Unit) {
        pending = onResult
        when (manager.authorizationStatus) {
            kCLAuthorizationStatusNotDetermined -> manager.requestWhenInUseAuthorization()
            kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways -> manager.requestLocation()
            else -> return deliver(null)
        }
        dispatch_after(
            dispatch_time(DISPATCH_TIME_NOW, LOCATION_TIMEOUT_MS * 1_000_000),
            dispatch_get_main_queue(),
        ) { deliver(null) }
    }

    private fun deliver(point: GeoPoint?) {
        val callback = pending ?: return
        pending = null
        callback(point)
    }
}
