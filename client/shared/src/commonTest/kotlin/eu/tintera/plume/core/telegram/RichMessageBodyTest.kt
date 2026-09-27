package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.PageBlock
import dev.g000sha256.tdl.dto.PageBlockBlockQuote
import dev.g000sha256.tdl.dto.PageBlockCaption
import dev.g000sha256.tdl.dto.PageBlockDivider
import dev.g000sha256.tdl.dto.PageBlockHeader
import dev.g000sha256.tdl.dto.PageBlockList
import dev.g000sha256.tdl.dto.PageBlockListItem
import dev.g000sha256.tdl.dto.PageBlockParagraph
import dev.g000sha256.tdl.dto.PageBlockPhoto
import dev.g000sha256.tdl.dto.RichText
import dev.g000sha256.tdl.dto.RichTextBold
import dev.g000sha256.tdl.dto.RichTextPlain
import dev.g000sha256.tdl.dto.RichTextSpoiler
import dev.g000sha256.tdl.dto.RichTextUrl
import dev.g000sha256.tdl.dto.RichTexts
import kotlin.test.Test
import kotlin.test.assertEquals

class RichMessageBodyTest {

    private val labels = RichMediaLabels(photo = "Fotka", video = "Video", gif = "GIF")

    private fun plain(text: String) = RichTextPlain(text)

    private fun body(vararg blocks: PageBlock) = arrayOf(*blocks).toRichBody(labels)

    /** Text, který entita pokrývá — tak se nejlíp ověří, že offsety sedí. */
    private fun MessageBody.covered(type: MessageEntityType): List<String> =
        entities.filter { it.type == type }.map { text.substring(it.offset, it.offset + it.length) }

    @Test
    fun nadpisJeTucnyAOddelenyVolnymRadkem() {
        val result = body(
            PageBlockParagraph(plain("Úvod")),
            PageBlockHeader(plain("Kapitola")),
            PageBlockParagraph(plain("Text")),
        )
        assertEquals("Úvod\n\nKapitola\nText", result.text)
        assertEquals(listOf("Kapitola"), result.covered(MessageEntityType.Bold))
    }

    @Test
    fun formatovaniUvnitrOdstavceSedi() {
        val result = body(
            PageBlockParagraph(
                RichTexts(
                    arrayOf<RichText>(
                        plain("Viz "),
                        RichTextUrl(RichTextBold(plain("web")), "https://example.com", false),
                        plain(" a "),
                        RichTextSpoiler(plain("tajné")),
                    ),
                ),
            ),
        )
        assertEquals("Viz web a tajné", result.text)
        assertEquals(listOf("web"), result.covered(MessageEntityType.Link("https://example.com")))
        assertEquals(listOf("web"), result.covered(MessageEntityType.Bold))
        assertEquals(listOf("tajné"), result.covered(MessageEntityType.Spoiler))
    }

    @Test
    fun seznamMaOdrazkyCislaIZaskrtavatka() {
        fun item(label: String, text: String, checkbox: Boolean = false, checked: Boolean = false) =
            PageBlockListItem(label, arrayOf<PageBlock>(PageBlockParagraph(plain(text))), checkbox, checked, 0, "")
        val result = body(
            PageBlockList(arrayOf(item("", "mléko"), item("2.", "chleba"))),
            PageBlockList(arrayOf(item("", "hotovo", checkbox = true, checked = true), item("", "zbývá", checkbox = true))),
        )
        assertEquals("• mléko\n2. chleba\n☑ hotovo\n☐ zbývá", result.text)
    }

    @Test
    fun formatovanyBlokZaOdrazkouZustaneNaJejimRadku() {
        val result = body(
            PageBlockList(
                arrayOf(PageBlockListItem("", arrayOf<PageBlock>(PageBlockHeader(plain("Tučně"))), false, false, 0, "")),
            ),
        )
        assertEquals("• Tučně", result.text)
        assertEquals(listOf("Tučně"), result.covered(MessageEntityType.Bold))
    }

    @Test
    fun citaceObaliObsahIPodpis() {
        val result = body(
            PageBlockParagraph(plain("Řekl:")),
            PageBlockBlockQuote(arrayOf<PageBlock>(PageBlockParagraph(plain("Ahoj"))), plain("Jan")),
        )
        assertEquals("Řekl:\nAhoj\n— Jan", result.text)
        assertEquals(listOf("Ahoj\n— Jan"), result.covered(MessageEntityType.Quote(expandable = false)))
    }

    @Test
    fun mediumMaSlovoAPopisekOddelovacCaru() {
        val result = body(
            PageBlockPhoto(null, PageBlockCaption(plain("Most"), plain("")), "", false),
            PageBlockDivider(),
            PageBlockPhoto(null, PageBlockCaption(plain(""), plain("")), "", false),
        )
        assertEquals("[Fotka] Most\n———\n[Fotka]", result.text)
    }

    @Test
    fun prazdnyClanekJePrazdny() {
        assertEquals("", body().text)
        assertEquals("", body(PageBlockParagraph(plain(""))).text)
    }
}
