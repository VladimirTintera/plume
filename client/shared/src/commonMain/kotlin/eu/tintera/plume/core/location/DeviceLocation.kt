package eu.tintera.plume.core.location

import androidx.compose.runtime.Composable

/**
 * Jednorázové zjištění polohy zařízení. Ve výchozím stavu PŘIBLIŽNÉ — jen tolik, kolik je potřeba
 * k hledání míst v okolí (inline bot míst bez polohy vrací místa u nějakého výchozího bodu, ne
 * u uživatele). Přesnou ([rememberDeviceLocation] s `precise`) chce jen tlačítko bota „pošli
 * polohu"; i tam smí uživatel povolit jen přibližnou a pak se vrátí ta.
 *
 * `core/location` je hranice jako `core/audio`: `android.location.*` a `platform.CoreLocation.*`
 * smí být jen tady. O oprávnění se žádá až při [request], tedy ve chvíli, kdy uživatel chce
 * přidat místo — ne dopředu.
 */
interface DeviceLocation {
    /** Poloha, nebo `null`, když ji uživatel nedovolil nebo ji zařízení nezjistilo (i do limitu). */
    fun request(onResult: (GeoPoint?) -> Unit)
}

@Composable
expect fun rememberDeviceLocation(precise: Boolean = false): DeviceLocation

/** Déle se na polohu nečeká — hledání míst pak zůstane bez ní. */
internal const val LOCATION_TIMEOUT_MS = 10_000L

/**
 * Umí platforma posouvat živou polohu, i když je aplikace na pozadí? Android ano (služba
 * v popředí), iOS zatím ne — bez `allowsBackgroundLocationUpdates` by se poloha zastavila hned,
 * jak uživatel aplikaci opustí, a sdílení by klamalo.
 */
expect val liveLocationSharingSupported: Boolean
