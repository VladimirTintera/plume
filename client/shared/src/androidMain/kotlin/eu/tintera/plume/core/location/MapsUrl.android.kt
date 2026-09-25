package eu.tintera.plume.core.location

actual fun mapsUrl(latitude: Double, longitude: Double): String =
    "https://maps.google.com/?q=$latitude,$longitude"
