package eu.tintera.plume.features.conversation.items

import eu.tintera.plume.core.telegram.ArticleMedia
import eu.tintera.plume.core.telegram.MessageEntity
import eu.tintera.plume.core.telegram.MessageEntityType
import eu.tintera.plume.core.telegram.MessageMedia
import kotlin.test.Test
import kotlin.test.assertEquals

class ArticleSegmentsTest {

    private fun photo(id: Int) = MessageMedia.Photo(fileId = id, thumbnailFileId = null, fullFileId = id, width = 4, height = 3)

    /** Kotva přes první výskyt [label] v [text]. */
    private fun anchor(text: String, label: String, index: Int, from: Int = 0, group: Int? = null): ArticleMedia {
        val start = text.indexOf(label, from)
        return ArticleMedia(start, start + label.length, index, photo(index), caption = "", group = group)
    }

    private fun List<ArticleSegment>.shape() = map {
        when (it) {
            is ArticleSegment.Text -> it.text
            is ArticleSegment.Media -> it.media.joinToString("+", "<", ">") { media -> "${media.index}" }
        }
    }

    @Test
    fun mediumRozdeliTextAPopisekZustanePodNim() {
        val text = "Úvod\n[Fotka] Most přes řeku\nKonec"
        val result = articleSegments(text, emptyList(), listOf(anchor(text, "[Fotka] ", 0)))
        assertEquals(listOf("Úvod", "<0>", "Most přes řeku\nKonec"), result.shape())
        assertEquals(text.indexOf("Most"), (result[2] as ArticleSegment.Text).offset)
    }

    @Test
    fun mediumZaMediemNenechaPrazdnyUsek() {
        val text = "[Fotka]\n[Fotka]\nKonec"
        val result = articleSegments(
            text,
            emptyList(),
            listOf(anchor(text, "[Fotka]", 0), anchor(text, "[Fotka]", 1, from = 1)),
        )
        assertEquals(listOf("<0>", "<1>", "Konec"), result.shape())
    }

    @Test
    fun entitySeOrizneAPosuneNaUsek() {
        val text = "Úvod tučně\n[Fotka]\nzase tučně"
        // Tučné přes celý text — i přes médium.
        val bold = MessageEntity(0, text.length, MessageEntityType.Bold)
        val result = articleSegments(text, listOf(bold), listOf(anchor(text, "[Fotka]", 0)))
        val before = result[0] as ArticleSegment.Text
        val after = result[2] as ArticleSegment.Text
        assertEquals(listOf(MessageEntity(0, "Úvod tučně".length, MessageEntityType.Bold)), before.entities)
        assertEquals(listOf(MessageEntity(0, "zase tučně".length, MessageEntityType.Bold)), after.entities)
    }

    @Test
    fun entitaJenPresSlovoMediaZmizi() {
        val text = "A\n[Fotka]\nB"
        val italic = MessageEntity(text.indexOf("[Fotka]"), "[Fotka]".length, MessageEntityType.Italic)
        val result = articleSegments(text, listOf(italic), listOf(anchor(text, "[Fotka]", 0)))
        assertEquals(listOf("A", "<0>", "B"), result.shape())
        result.filterIsInstance<ArticleSegment.Text>().forEach { assertEquals(emptyList(), it.entities) }
    }

    @Test
    fun kotvaMimoTextSeIgnoruje() {
        val text = "Jen text"
        val result = articleSegments(text, emptyList(), listOf(ArticleMedia(5, 50, 0, photo(0), "")))
        assertEquals(listOf("Jen text"), result.shape())
    }

    @Test
    fun mediaJedneKolazeJsouJedenUsek() {
        val text = "Úvod\n[Fotka]\n[Fotka]\n[Fotka]\nKonec"
        val result = articleSegments(
            text,
            emptyList(),
            listOf(
                anchor(text, "[Fotka]", 0, group = 0),
                anchor(text, "[Fotka]", 1, from = 6, group = 0),
                // Třetí je samostatná fotka hned za koláží — do mřížky nepatří.
                anchor(text, "[Fotka]", 2, from = 14),
            ),
        )
        assertEquals(listOf("Úvod", "<0+1>", "<2>", "Konec"), result.shape())
    }

    @Test
    fun dveKolazeZaSebouSeNeslouci() {
        val text = "[Fotka]\n[Fotka]"
        val result = articleSegments(
            text,
            emptyList(),
            listOf(anchor(text, "[Fotka]", 0, group = 0), anchor(text, "[Fotka]", 1, from = 1, group = 1)),
        )
        assertEquals(listOf("<0>", "<1>"), result.shape())
    }

    @Test
    fun popisekVKolaziMrizkuPrerusi() {
        val text = "[Fotka] Most\n[Fotka]"
        val result = articleSegments(
            text,
            emptyList(),
            listOf(anchor(text, "[Fotka] ", 0, group = 0), anchor(text, "[Fotka]", 1, from = 1, group = 0)),
        )
        assertEquals(listOf("<0>", "Most", "<1>"), result.shape())
    }
}
