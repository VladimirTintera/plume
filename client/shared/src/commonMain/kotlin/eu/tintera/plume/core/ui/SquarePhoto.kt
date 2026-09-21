package eu.tintera.plume.core.ui

/**
 * Ořízne fotku na čtverec ze středu a zmenší ji na [PROFILE_PHOTO_EDGE] — profilová fotka.
 *
 * Kreslí se v kruhu a oficiální klienti ji vždycky ořezávají na čtverec; neořezanou fotku by
 * každý klient ořízl jinak. Vstupem je fotka už připravená výběrem příloh (otočená podle EXIF,
 * JPEG), výstupem nový soubor v cache vedle ní.
 */
interface SquarePhoto {
    suspend fun crop(path: String): String
}

/** Telegram profilovou fotku stejně zmenšuje na 640 px; 1280 nechává rezervu na ostrost. */
const val PROFILE_PHOTO_EDGE = 1280
