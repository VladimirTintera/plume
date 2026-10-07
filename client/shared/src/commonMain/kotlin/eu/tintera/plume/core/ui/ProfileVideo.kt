package eu.tintera.plume.core.ui

/**
 * Video jako profilovka (animovaná fotka účtu nebo chatu). Telegram bere čtvercové MP4 bez
 * zvuku, nejvýš [PROFILE_VIDEO_MAX_SECONDS] s, se stranou 160–1280 px a do
 * [PROFILE_VIDEO_MAX_BYTES] — video z galerie tomu skoro nikdy nevyhoví, takže se převádí.
 */
interface ProfileVideo {
    /** Délka videa v milisekundách; z ní se počítá, kam jde úsek posunout ([VideoTrim]). */
    suspend fun durationMs(path: String): Long

    /**
     * Snímek v čase [atMs] jako JPEG, nejvýš [edge] px. Pro ořez ([PhotoCropOverlay]) celých
     * [PROFILE_VIDEO_FRAME_EDGE] — náhled z výběru příloh má jen 320 px a výřez by se vybíral
     * z rozmazaného obrázku —, pro pás snímků pod ním [PROFILE_VIDEO_STRIP_EDGE].
     * Snímek je už otočený jako video, takže výřez v podílech platí pro obojí.
     *
     * Soubor je pro čas a velikost vlastní a podruhé se jen vrátí: snímek se vybírá tahem
     * a pás se skládá při každém otevření ořezu.
     */
    suspend fun frame(path: String, atMs: Long = 0, edge: Int = PROFILE_VIDEO_FRAME_EDGE): String

    /**
     * Ořízne video na čtverec podle [region] (`null` = ze středu), zmenší na
     * [PROFILE_VIDEO_EDGE], vezme [PROFILE_VIDEO_MAX_SECONDS] s od [startMs] a zahodí zvuk.
     * Vrací cestu k MP4 vedle zdroje.
     */
    suspend fun crop(path: String, region: CropRegion?, startMs: Long = 0): String
}

/** Strana animované profilovky; tolik používají oficiální klienti. */
const val PROFILE_VIDEO_EDGE = 800

/** Nejmenší strana, kterou Telegram u animované profilovky přijme. */
const val PROFILE_VIDEO_MIN_EDGE = 160

const val PROFILE_VIDEO_MAX_SECONDS = 10

const val PROFILE_VIDEO_MAX_BYTES = 2L * 1024 * 1024

/**
 * Datový tok obrazu: 10 s × 1,2 Mbit/s je 1,5 MB, tedy pod stropem i s rezervou na to, že kodér
 * požadavek přesně nedodrží.
 */
const val PROFILE_VIDEO_BITRATE = 1_200_000

/** Snímek pro ořez — stejně jako u fotky v ořezu stačí 1600 px. */
const val PROFILE_VIDEO_FRAME_EDGE = 1600

/** Snímek do pásu pod ořezem: dlaždice jsou vysoké 56 dp. */
const val PROFILE_VIDEO_STRIP_EDGE = 200

/** Jméno souboru se snímkem — jedno pro obě platformy, ať se pozná v cache. */
internal fun profileFrameName(atMs: Long, edge: Int) = "profile-frame-$edge-$atMs.jpg"
