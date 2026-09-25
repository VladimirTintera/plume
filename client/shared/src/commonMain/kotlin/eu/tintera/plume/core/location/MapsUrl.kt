package eu.tintera.plume.core.location

/**
 * Odkaz, který otevře polohu v mapách platformy.
 *
 * Android: Google Maps přes https — s aplikací Map se otevře v ní (app link), bez ní v prohlížeči.
 * `geo:` by nabídl výběr aplikací, jenže cesta odkazu z konverzace (`openLink`) umí jen adresy
 * s `://`. iOS: Apple Maps přes `maps.apple.com` (univerzální odkaz do Map); `geo:` iOS nezná.
 */
expect fun mapsUrl(latitude: Double, longitude: Double): String
