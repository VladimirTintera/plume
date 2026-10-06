package eu.tintera.plume.core.ui

/**
 * Video jako profilovka (animovaná fotka účtu nebo chatu). Telegram bere čtvercové MP4 bez
 * zvuku, nejvýš [PROFILE_VIDEO_MAX_SECONDS] s, se stranou 160–1280 px a do
 * [PROFILE_VIDEO_MAX_BYTES] — video z galerie tomu skoro nikdy nevyhoví, takže se převádí.
 */
interface ProfileVideo {
    /**
     * První snímek jako JPEG pro ořez ([PhotoCropOverlay]), nejvýš [PROFILE_VIDEO_FRAME_EDGE]
     * px. Náhled z výběru příloh má jen 320 px a výřez by se vybíral z rozmazaného obrázku.
     * Snímek je už otočený jako video, takže výřez v podílech platí pro obojí.
     */
    suspend fun frame(path: String): String

    /**
     * Ořízne video na čtverec podle [region] (`null` = ze středu), zmenší na
     * [PROFILE_VIDEO_EDGE], vezme prvních [PROFILE_VIDEO_MAX_SECONDS] s a zahodí zvuk.
     * Vrací cestu k MP4 vedle zdroje.
     */
    suspend fun crop(path: String, region: CropRegion?): String
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
