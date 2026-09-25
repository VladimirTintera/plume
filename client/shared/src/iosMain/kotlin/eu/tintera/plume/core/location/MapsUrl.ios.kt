package eu.tintera.plume.core.location

actual fun mapsUrl(latitude: Double, longitude: Double): String =
    "https://maps.apple.com/?ll=$latitude,$longitude&q=$latitude,$longitude"
