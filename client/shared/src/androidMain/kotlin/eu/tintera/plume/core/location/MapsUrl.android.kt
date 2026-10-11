package eu.tintera.plume.core.location

actual fun mapsUrl(latitude: Double, longitude: Double): String =
    "https://maps.google.com/?q=$latitude,$longitude"

actual fun mapsSearchUrl(query: String): String =
    "https://maps.google.com/?q=${encodeQueryParameter(query)}"
