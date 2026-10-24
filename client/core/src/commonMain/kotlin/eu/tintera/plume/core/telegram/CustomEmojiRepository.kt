package eu.tintera.plume.core.telegram

/**
 * Vlastní emoji (`TextEntityTypeCustomEmoji`): samolepka, která v textu stojí místo obyčejného
 * emoji. Text sám nese jen id a náhradní znak, obrázek se dohledává zvlášť.
 */
interface CustomEmojiRepository {

    /**
     * Emoji podle id, nebo `null`, když ho server nevydal (smazaná sada, výpadek). Volání se
     * slučují do dávek a výsledek se pamatuje — v kanálu bývá v jedné zprávě emoji desítky.
     */
    suspend fun customEmoji(id: Long): CustomEmoji?
}

/**
 * Co se z vlastního emoji dá vykreslit. Formáty jako u samolepky (`MessageMedia.Sticker`):
 * [stillFileId] je `.webp` nebo rastrový náhled, [animationFileId] `.tgs`. `.webm` se přehrát
 * neumí, zbude z něj náhled — a bez náhledu náhradní znak.
 */
data class CustomEmoji(
    val id: Long,
    val stillFileId: Int?,
    val animationFileId: Int?,
    /** Emoji, za které vlastní emoji stojí — popis pro čtečku a náhrada, když obrázek není. */
    val emoji: String,
    /**
     * Jednobarevné emoji, které se má obarvit barvou textu (`needsRepainting`). Jinak by na
     * odchozí bublině bylo tmavé na tmavém.
     */
    val tinted: Boolean,
)
