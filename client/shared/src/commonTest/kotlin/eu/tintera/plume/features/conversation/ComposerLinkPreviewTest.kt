package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.LinkPreview
import eu.tintera.plume.core.telegram.LinkPreviewOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ComposerLinkPreviewTest {

    private fun preview(url: String = "https://a.cz", canChangeSize: Boolean = true) = LinkPreview(
        url = url,
        siteName = "A",
        title = "Titulek",
        description = null,
        photo = null,
        largePhoto = true,
        showAboveText = false,
        canChangeSize = canChangeSize,
    )

    private val links = listOf("https://a.cz", "https://b.cz")

    @Test
    fun hlavickaNeniUPopiskuAPriloh() {
        val p = preview()
        val none = LinkPreviewOptions()
        assertEquals(p, composerLinkPreview(p, none, editingCaption = false, hasAttachments = false))
        assertNull(composerLinkPreview(p, none, editingCaption = true, hasAttachments = false))
        assertNull(composerLinkPreview(p, none, editingCaption = false, hasAttachments = true))
        assertNull(composerLinkPreview(p, LinkPreviewOptions(disabled = true), editingCaption = false, hasAttachments = false))
    }

    @Test
    fun jinyOdkazZahodiVolbuAleVypnutiNe() {
        val chosen = LinkPreviewOptions(largeMedia = false, aboveText = true)
        assertEquals(LinkPreviewOptions(), chosen.forPreview(preview("https://a.cz"), preview("https://b.cz"), links))
        assertEquals(chosen, chosen.forPreview(preview("https://a.cz"), preview("https://a.cz"), links))

        val off = LinkPreviewOptions(disabled = true, aboveText = true)
        assertEquals(LinkPreviewOptions(disabled = true), off.forPreview(preview("https://a.cz"), preview("https://b.cz"), links))
    }

    @Test
    fun zmizelyNahledVolbuNemeni() {
        val chosen = LinkPreviewOptions(largeMedia = true)
        assertEquals(chosen, chosen.forPreview(preview(), null, links))
    }

    @Test
    fun volbaZKonceptuPatriKPrvnimuNahledu() {
        val fromDraft = LinkPreviewOptions(largeMedia = false, aboveText = true)
        assertEquals(fromDraft, fromDraft.forPreview(previous = null, next = preview(), links = links))
        // Odkaz bez dvou velikostí: velikost by visela neviditelně, poloha zůstává.
        assertEquals(
            LinkPreviewOptions(aboveText = true),
            fromDraft.forPreview(previous = null, next = preview(canChangeSize = false), links = links),
        )
    }

    @Test
    fun vynucenaVelikostDostaneAdresuUkazanehoNahledu() {
        val shown = preview("https://a.cz/clanek")
        assertEquals(
            LinkPreviewOptions(largeMedia = false, url = "https://a.cz/clanek"),
            LinkPreviewOptions(largeMedia = false).forSending(shown),
        )
        // Bez ukázaného náhledu se velikost nevynucuje; poloha adresu nepotřebuje.
        assertNull(LinkPreviewOptions(largeMedia = true).forSending(null))
        assertEquals(LinkPreviewOptions(aboveText = true), LinkPreviewOptions(largeMedia = true, aboveText = true).forSending(null))
    }

    @Test
    fun upravaPrevezmeVolbuZpravy() {
        // Zpráva poslaná bez náhledu zůstane bez náhledu i po úpravě.
        assertEquals(LinkPreviewOptions(disabled = true), LinkPreviewOptions(disabled = true).forEdit("Viz a.cz, opraveno", null))
        // Vynucená velikost s adresou platí i dřív, než dorazí náhled upraveného textu.
        val forced = LinkPreviewOptions(largeMedia = false, aboveText = true, url = "https://a.cz")
        assertEquals(forced, forced.forEdit("Viz https://a.cz", null))
        // Odkaz z textu zmizel: adresa jde pryč, a s ní i velikost, kterou by TDLib stejně ignoroval.
        assertEquals(LinkPreviewOptions(aboveText = true), forced.forEdit("Viz https://b.cz", null))
        assertNull(LinkPreviewOptions().forEdit("Viz a.cz", null))
    }

    @Test
    fun vychoziVolbaNechaRozhodnoutServer() {
        assertNull(LinkPreviewOptions().forSending(preview()))
        assertEquals(LinkPreviewOptions(disabled = true), LinkPreviewOptions(disabled = true, aboveText = true).forSending(preview()))
    }

    @Test
    fun vybranyOdkazPrezijeNahledJinehoWebuALeVypadneSTextem() {
        val chosen = LinkPreviewOptions(largeMedia = true, url = "b.cz")
        // Náhled vybraného odkazu je jiný web než ten předchozí: velikost jde pryč, výběr zůstává.
        assertEquals(
            LinkPreviewOptions(url = "b.cz"),
            chosen.forPreview(preview("https://a.cz"), preview("https://b.cz/"), links = listOf("a.cz", "b.cz")),
        )
        // Odkaz z textu zmizel: výběr taky, náhled se vrátí k prvnímu.
        assertEquals(
            LinkPreviewOptions(largeMedia = true),
            chosen.forPreview(preview("https://a.cz"), null, links = listOf("a.cz")),
        )
        assertEquals(
            LinkPreviewOptions(),
            LinkPreviewOptions(url = "b.cz").forPreview(null, preview(), links = listOf("a.cz")),
        )
    }

    @Test
    fun vybranyOdkazOdejdeIBezNahledu() {
        val chosen = LinkPreviewOptions(url = "b.cz")
        assertFalse(chosen.isDefault)
        assertEquals(chosen, chosen.forSending(null))
        assertEquals(LinkPreviewOptions(url = "https://b.cz/"), chosen.forSending(preview("https://b.cz/")))
    }

    @Test
    fun oznacenyJeVybranyOdkazJinakPrvni() {
        val links = listOf("a.cz", "https://b.cz/x")
        assertEquals("a.cz", LinkPreviewOptions().selectedLink(links))
        assertEquals("https://b.cz/x", LinkPreviewOptions(url = "https://b.cz/x").selectedLink(links))
        assertEquals("a.cz", LinkPreviewOptions(url = "c.cz").selectedLink(links))
        assertNull(LinkPreviewOptions().selectedLink(emptyList()))
    }

    @Test
    fun domenaOdkazu() {
        assertEquals("example.com", linkHost("https://www.example.com/a?b=1"))
        assertEquals("en.wikipedia.org", linkHost("en.wikipedia.org/wiki/Praha"))
        assertEquals("youtu.be", linkHost("http://youtu.be#t"))
        assertEquals("host.cz", linkHost("https://user@host.cz/"))
    }
}
