package eu.tintera.plume.core.telegram

/**
 * Reklamy, které se ukazují během videa z kanálu na celé obrazovce (`getVideoMessageAdvertisements`).
 * [Specifikace reklam](https://core.telegram.org/api/sponsored-messages) je chce zobrazit: postupně,
 * podle času PŘEHRÁVÁNÍ (pozastavené video časovače zastaví) — první po [startDelaySeconds], další
 * [betweenDelaySeconds] po skrytí předchozí.
 */
data class VideoAds(
    val ads: List<VideoAd>,
    val startDelaySeconds: Int,
    val betweenDelaySeconds: Int,
)

/** Jedna reklama nad videem. [uniqueId] jde do hlášení zhlédnutí, kliknutí i nahlášení. */
data class VideoAd(
    val uniqueId: Long,
    val title: String,
    val text: String,
    /** Dřív ji uživatel zavřít nesmí. */
    val minDisplaySeconds: Int,
    /** Pak se sama skryje. */
    val maxDisplaySeconds: Int,
    val canBeReported: Boolean,
    /** Kam vede ťuknutí (inzerent). */
    val url: String,
    /** Fotka inzerenta; `null` = nemá se ukazovat. */
    val photo: MessageMedia.Photo?,
    /** Kdo reklamu platí; prázdné = není. */
    val sponsorInfo: String,
    /** Doplňující informace od Telegramu; prázdné = není. */
    val additionalInfo: String,
)
