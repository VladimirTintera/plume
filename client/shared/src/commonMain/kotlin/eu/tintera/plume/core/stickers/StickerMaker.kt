package eu.tintera.plume.core.stickers

/**
 * Z fotky samolepka, jak ji chce Telegram: delší strana přesně 512 px, kratší nejvýš 512,
 * WebP s průhledností. Jen Android; jinde se nezaregistruje a tvorba samolepek se nenabízí.
 */
interface StickerMaker {
    /** Jde odstranit pozadí (ML Kit z Play služeb). Bez Play služeb samolepka zůstane s pozadím. */
    val canRemoveBackground: Boolean

    /**
     * Samolepka z fotky [path] (už upravené a oříznuté editorem). S [removeBackground] zůstane
     * jen hlavní objekt a ořízne se těsně na něj. Výsledek je nový soubor v cache.
     */
    suspend fun make(path: String, removeBackground: Boolean): Result<MadeSticker>

    /**
     * Obálka sady ze samolepky [path]: WebP přesně 100 × 100, samolepka vepsaná doprostřed
     * s průhledným okrajem, nejvýš 32 kB (víc server u obálky nevezme).
     */
    suspend fun makeCover(path: String): Result<MadeSticker> = Result.failure(UnsupportedOperationException())
}

/** Hotová samolepka k nahrání: soubor a jeho rozměry. */
data class MadeSticker(val path: String, val width: Int, val height: Int)

/** Odstranění pozadí nedoběhlo, protože model ještě stahují Play služby — zkusit za chvíli. */
class StickerModelNotReadyException(cause: Throwable) : Exception("Subject segmentation model is not ready", cause)

/** Fotka neobsahuje nic, co by šlo od pozadí oddělit (prázdná maska). */
class NoStickerSubjectException : Exception("No subject found")
