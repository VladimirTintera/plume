package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.dto.LocalFile
import dev.g000sha256.tdl.dto.PageBlock
import dev.g000sha256.tdl.dto.PageBlockBlockQuote
import dev.g000sha256.tdl.dto.PageBlockCaption
import dev.g000sha256.tdl.dto.PageBlockDivider
import dev.g000sha256.tdl.dto.PageBlockHeader
import dev.g000sha256.tdl.dto.PageBlockList
import dev.g000sha256.tdl.dto.PageBlockListItem
import dev.g000sha256.tdl.dto.PageBlockParagraph
import dev.g000sha256.tdl.dto.PageBlockPhoto
import dev.g000sha256.tdl.dto.Photo
import dev.g000sha256.tdl.dto.PhotoSize
import dev.g000sha256.tdl.dto.RemoteFile
import dev.g000sha256.tdl.dto.RichText
import dev.g000sha256.tdl.dto.RichTextBold
import dev.g000sha256.tdl.dto.RichTextPlain
import dev.g000sha256.tdl.dto.RichTextSpoiler
import dev.g000sha256.tdl.dto.RichTextUrl
import dev.g000sha256.tdl.dto.RichTexts
import kotlin.test.Test
import kotlin.test.assertEquals

class RichMessageBodyTest {

    private val labels = RichMediaLabels(
        photo = "Fotka",
        video = "Video",
        gif = "GIF",
        audio = "Audio",
        voice = "Hlasovka",
        file = "Soubor",
    )

    /** Fotka, jak ji TDLib posílá: jedna velikost se souborem [fileId]. */
    private fun photo(fileId: Int) = Photo(
        hasStickers = false,
        minithumbnail = null,
        sizes = arrayOf(
            PhotoSize(
                type = "x",
                photo = File(
                    id = fileId,
                    size = 1,
                    expectedSize = 1,
                    local = LocalFile("", true, false, false, false, 0, 0, 0),
                    remote = RemoteFile("r$fileId", "u$fileId", false, true, 1),
                ),
                width = 800,
                height = 600,
                progressiveSizes = IntArray(0),
            ),
        ),
    )

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

    @Test
    fun fotkaMaKotvuPresSlovoAPopisekZustavaTextem() {
        val result = body(
            PageBlockParagraph(plain("Úvod")),
            PageBlockPhoto(photo(7), PageBlockCaption(plain("Most"), plain("")), "", false),
            PageBlockPhoto(photo(8), PageBlockCaption(plain(""), plain("")), "", false),
            PageBlockParagraph(plain("Konec")),
        )
        assertEquals("Úvod\n[Fotka] Most\n[Fotka]\nKonec", result.text)
        val media = result.article!!.media
        assertEquals(listOf("[Fotka] ", "[Fotka]"), media.map { result.text.substring(it.start, it.end) })
        assertEquals(listOf(0, 1), media.map { it.index })
        assertEquals(listOf("Most", ""), media.map { it.caption })
        assertEquals(listOf(7, 8), media.map { (it.media as MessageMedia.Photo).fileId })
        assertEquals(false, result.article!!.isPartial)
    }

    @Test
    fun fotkaBezSouboruKotvuNema() {
        val result = body(PageBlockPhoto(null, PageBlockCaption(plain("Most"), plain("")), "", false))
        assertEquals("[Fotka] Most", result.text)
        assertEquals(null, result.article)
    }

    @Test
    fun useknutyClanekToHlasi() {
        val result = arrayOf<PageBlock>(PageBlockParagraph(plain("Začátek"))).toRichBody(labels, isFull = false)
        assertEquals(true, result.article?.isPartial)
        assertEquals(emptyList(), result.article?.media)
    }

    @Test
    fun citliveMediumJeSkryte() {
        val result = arrayOf<PageBlock>(PageBlockPhoto(photo(7), null, "", false)).toRichBody(labels, hideSensitive = true)
        val photo = result.article!!.media.single().media as MessageMedia.Photo
        assertEquals(true, photo.hasSpoiler)
        assertEquals(true, photo.isSensitive)
    }
}
