package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.LinkPreview
import eu.tintera.plume.core.telegram.LinkPreviewOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun hlavickaJenUObycejneZpravy() {
        val p = preview()
        val none = LinkPreviewOptions()
        assertEquals(p, composerLinkPreview(p, none, editing = false, hasAttachments = false))
        assertNull(composerLinkPreview(p, none, editing = true, hasAttachments = false))
        assertNull(composerLinkPreview(p, none, editing = false, hasAttachments = true))
        assertNull(composerLinkPreview(p, LinkPreviewOptions(disabled = true), editing = false, hasAttachments = false))
    }

    @Test
    fun jinyOdkazZahodiVolbuAleVypnutiNe() {
        val chosen = LinkPreviewOptions(largeMedia = false, aboveText = true)
        assertEquals(LinkPreviewOptions(), chosen.forPreview(preview("https://a.cz"), preview("https://b.cz")))
        assertEquals(chosen, chosen.forPreview(preview("https://a.cz"), preview("https://a.cz")))

        val off = LinkPreviewOptions(disabled = true, aboveText = true)
        assertEquals(LinkPreviewOptions(disabled = true), off.forPreview(preview("https://a.cz"), preview("https://b.cz")))
    }

    @Test
    fun zmizelyNahledVolbuNemeni() {
        val chosen = LinkPreviewOptions(largeMedia = true)
        assertEquals(chosen, chosen.forPreview(preview(), null))
    }

    @Test
    fun volbaZKonceptuPatriKPrvnimuNahledu() {
        val fromDraft = LinkPreviewOptions(largeMedia = false, aboveText = true)
        assertEquals(fromDraft, fromDraft.forPreview(previous = null, next = preview()))
        // Odkaz bez dvou velikostí: velikost by visela neviditelně, poloha zůstává.
        assertEquals(
            LinkPreviewOptions(aboveText = true),
            fromDraft.forPreview(previous = null, next = preview(canChangeSize = false)),
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
    fun vychoziVolbaNechaRozhodnoutServer() {
        assertNull(LinkPreviewOptions().forSending(preview()))
        assertEquals(LinkPreviewOptions(disabled = true), LinkPreviewOptions(disabled = true, aboveText = true).forSending(preview()))
        assertTrue(LinkPreviewOptions(url = "https://a.cz").isDefault)
    }
}
