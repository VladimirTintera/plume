package eu.tintera.plume.core.ui

/**
 * Jestli si fotka, která jde přiblížit (prohlížeč médií), bere od Coilu plné rozlišení místo
 * velikosti karty ([RemoteImage], `fullResolution`).
 *
 * **Desktop ano**: dekodér na Skie zmenší přesně na velikost karty (na desktopu se šířkou kolem
 * 360 px) a přiblížení pak ten malý obrázek jen roztáhne — leták byl při 2,5× rozmazaný na
 * nečitelnost. **Android ne**: dekóduje přes `inSampleSize` po mocninách dvou a obrázek nechá
 * až dvakrát větší než kartu, ta je navíc široká jako displej; přiblížení je tam ostré i tak.
 * **iOS ne**, dokud se to tam neověří (týž dekodér jako desktop, takže ho to nejspíš potkává).
 */
expect val zoomLoadsFullResolution: Boolean
