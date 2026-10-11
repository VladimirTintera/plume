package eu.tintera.plume.core.location

/**
 * Odkaz, který otevře polohu v mapách platformy.
 *
 * Android: Google Maps přes https — s aplikací Map se otevře v ní (app link), bez ní v prohlížeči.
 * `geo:` by nabídl výběr aplikací, jenže cesta odkazu z konverzace (`openLink`) umí jen adresy
 * s `://`. iOS: Apple Maps přes `maps.apple.com` (univerzální odkaz do Map); `geo:` iOS nezná.
 */
expect fun mapsUrl(latitude: Double, longitude: Double): String

/**
 * Odkaz, který v mapách platformy vyhledá adresu nebo místo zapsané textem („Vinohradská 12,
 * Praha"). Stejné mapy jako [mapsUrl], jen s hledáním místo souřadnic.
 */
expect fun mapsSearchUrl(query: String): String

/**
 * Text do parametru adresy (RFC 3986): písmena, číslice a `-._~` zůstanou, zbytek jako UTF-8
 * po bajtech `%XX`. Mezera je `%20`, ne `+` — obojí mapy čtou, `%20` ale platí všude.
 */
internal fun encodeQueryParameter(value: String): String = buildString {
    value.encodeToByteArray().forEach { byte ->
        val code = byte.toInt() and 0xFF
        val char = code.toChar()
        if (code < 0x80 && (char.isLetterOrDigit() || char in "-._~")) {
            append(char)
        } else {
            append('%').append(HEX[code shr 4]).append(HEX[code and 0x0F])
        }
    }
}

private const val HEX = "0123456789ABCDEF"
