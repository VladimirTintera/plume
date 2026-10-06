package eu.tintera.plume.features.conversation.items

import eu.tintera.plume.core.telegram.ArticleMedia
import eu.tintera.plume.core.telegram.MessageEntity

/** Kus článku v bublině: text, nebo médium na jeho místě. */
internal sealed interface ArticleSegment {

    /**
     * Úsek textu s entitami posunutými na jeho začátek. [offset] je, kde v celém textu zprávy
     * úsek začíná — kvůli zvýraznění hledaného slova, které se počítá nad celým textem.
     */
    data class Text(val text: String, val entities: List<MessageEntity>, val offset: Int) : ArticleSegment

    /** Médium, nebo víc médií jedné koláže (mřížka). Nikdy prázdné. */
    data class Media(val media: List<ArticleMedia>) : ArticleSegment
}

/**
 * Text článku rozdělený v místech médií.
 *
 * Text zprávy zůstává JEDEN řetězec (hledání, kopírování, výběr textu a citace z nabídky
 * pracují nad ním) a média v něm stojí jako „[Fotka] popisek". Bublina místo slova nakreslí
 * médium: úsek [ArticleMedia.start]–[ArticleMedia.end] se vypustí, popisek za ním zůstane
 * prvním řádkem dalšího úseku.
 *
 * - **Konce řádků na hranách úseků se zahazují.** Médium stojí na vlastním řádku; zalomení
 *   před ním a za ním by v bublině udělalo prázdný řádek nad médiem i pod ním.
 * - **Entity se ořežou na úsek a posunou** na jeho začátek. Entita přes médium (citace, ve
 *   které je fotka) tím vyjde jako dvě — nad médiem a pod ním.
 * - Prázdný úsek (médium hned za médiem) se vynechá.
 * - **Média jedné koláže jdou do jednoho úseku** (mřížka), jen když mezi nimi není nic než
 *   konec řádku. Médium koláže s vlastním popiskem mřížku přeruší — popisek by v ní neměl kde být.
 */
internal fun articleSegments(
    text: String,
    entities: List<MessageEntity>,
    media: List<ArticleMedia>,
): List<ArticleSegment> {
    val anchors = media
        .filter { it.start in 0..it.end && it.end <= text.length }
        .sortedBy { it.start }
    val segments = mutableListOf<ArticleSegment>()
    var cursor = 0

    fun addText(from: Int, to: Int) {
        var start = from
        var end = to
        while (start < end && text[start] == '\n') start++
        while (end > start && text[end - 1] == '\n') end--
        if (start >= end || text.substring(start, end).isBlank()) return
        segments += ArticleSegment.Text(
            text = text.substring(start, end),
            entities = entities.mapNotNull { entity ->
                val entityStart = maxOf(entity.offset, start)
                val entityEnd = minOf(entity.offset + entity.length, end)
                if (entityEnd <= entityStart) null else entity.copy(offset = entityStart - start, length = entityEnd - entityStart)
            },
            offset = start,
        )
    }

    anchors.forEach { anchor ->
        // Překrývající se kotvy nevzniknou, ale pojistka nic nestojí: druhou by text přeskočil.
        if (anchor.start < cursor) return@forEach
        val previous = segments.lastOrNull() as? ArticleSegment.Media
        val joins = previous != null && anchor.group != null &&
            previous.media.last().group == anchor.group &&
            text.substring(cursor, anchor.start).isBlank()
        if (joins) {
            segments[segments.lastIndex] = ArticleSegment.Media(previous!!.media + anchor)
        } else {
            addText(cursor, anchor.start)
            segments += ArticleSegment.Media(listOf(anchor))
        }
        cursor = anchor.end
    }
    addText(cursor, text.length)
    return segments
}
