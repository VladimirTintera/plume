package eu.tintera.plume.core.location

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Polohu zařízení desktop z JVM nezjistí; hledání míst pak běží bez ní. */
@Composable
actual fun rememberDeviceLocation(precise: Boolean): DeviceLocation = remember {
    object : DeviceLocation {
        override fun request(onResult: (GeoPoint?) -> Unit) = onResult(null)
    }
}

actual val liveLocationSharingSupported: Boolean = false

/** OpenStreetMap: na desktopu není mapová aplikace, kterou by šlo předpokládat. */
actual fun mapsUrl(latitude: Double, longitude: Double): String =
    "https://www.openstreetmap.org/?mlat=$latitude&mlon=$longitude#map=16/$latitude/$longitude"

actual fun mapsSearchUrl(query: String): String =
    "https://www.openstreetmap.org/search?query=${encodeQueryParameter(query)}"
