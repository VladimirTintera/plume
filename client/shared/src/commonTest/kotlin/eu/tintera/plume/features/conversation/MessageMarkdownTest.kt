package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.MessageEntity
import eu.tintera.plume.core.telegram.MessageEntityType
import kotlin.test.Test
import kotlin.test.assertEquals

class MessageMarkdownTest {

    private fun md(text: String, vararg entities: Triple<Int, Int, MessageEntityType>) =
        markdownOf(text, entities.map { (offset, length, type) -> MessageEntity(offset, length, type) })

    @Test
    fun bezFormatovaniBezeZmeny() {
        assertEquals("Ahoj *světe*", md("Ahoj *světe*"))
    }

    @Test
    fun tucneKurzivaPreskrtnute() {
        assertEquals(
            "**tučně** _kurzíva_ ~~pryč~~",
            md(
                "tučně kurzíva pryč",
                Triple(0, 5, MessageEntityType.Bold),
                Triple(6, 7, MessageEntityType.Italic),
                Triple(14, 4, MessageEntityType.Strikethrough),
            ),
        )
    }

    /** Vnořené značky se zavírají v opačném pořadí, nekříží se. */
    @Test
    fun vnoreneZnacky() {
        assertEquals(
            "**a _b_**",
            md("a b", Triple(0, 3, MessageEntityType.Bold), Triple(2, 1, MessageEntityType.Italic)),
        )
        assertEquals(
            "_**ab**_",
            md("ab", Triple(0, 2, MessageEntityType.Italic), Triple(0, 2, MessageEntityType.Bold)),
        )
    }

    /** `** tučně**` Markdown za tučné nevezme — značka obejde mezery. */
    @Test
    fun mezeryNaKrajichZustanouVne() {
        assertEquals("a **b** c", md("a  b  c".replace("  ", " "), Triple(1, 3, MessageEntityType.Bold)))
    }

    @Test
    fun odkazSPopiskemAleNeHolaAdresa() {
        assertEquals(
            "[web](https://example.com) a example.com",
            md(
                "web a example.com",
                Triple(0, 3, MessageEntityType.Link("https://example.com")),
                Triple(6, 11, MessageEntityType.Link("http://example.com")),
            ),
        )
    }

    @Test
    fun kodVRadkuABlokSJazykem() {
        assertEquals("pusť `ls`", md("pusť ls", Triple(5, 2, MessageEntityType.Code())))
        assertEquals(
            "Takhle:\n```kotlin\nval a = 1\n```\nhotovo",
            md("Takhle:val a = 1hotovo", Triple(7, 9, MessageEntityType.Code(block = true, language = "kotlin"))),
        )
    }

    @Test
    fun citaceNaKazdemRadku() {
        assertEquals(
            "Řekl:\n> první\n> druhý\nkonec",
            md("Řekl:\nprvní\ndruhý\nkonec", Triple(6, 11, MessageEntityType.Quote(expandable = false))),
        )
    }

    @Test
    fun spoiler() {
        assertEquals("vrah je ||komorník||", md("vrah je komorník", Triple(8, 8, MessageEntityType.Spoiler)))
    }

    /** Offsety jsou v UTF-16: emoji (dvě jednotky) před formátováním ho neposune. */
    @Test
    fun emojiPredFormatovanim() {
        assertEquals("😀 **ano**", md("😀 ano", Triple(3, 3, MessageEntityType.Bold)))
    }

    @Test
    fun podtrzeniAZminkyZustanouTextem() {
        assertEquals(
            "důležité @jana",
            md("důležité @jana", Triple(0, 8, MessageEntityType.Underline), Triple(9, 5, MessageEntityType.Mention("jana"))),
        )
    }
}
