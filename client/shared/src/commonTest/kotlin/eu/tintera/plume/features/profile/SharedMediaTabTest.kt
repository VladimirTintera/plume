package eu.tintera.plume.features.profile

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.SharedMediaKind
import eu.tintera.plume.core.telegram.SharedMediaPage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Přechody záložky sdíleného obsahu — viz [SharedMediaTab]. */
class SharedMediaTabTest {

    private fun message(id: Long) = ChatMessage(
        id = id,
        isOutgoing = false,
        senderName = null,
        senderAvatarFileId = null,
        senderId = 7L,
        text = "soubor",
        entities = emptyList(),
        media = null,
        linkPreview = null,
        reactions = emptyList(),
        sendState = null,
        replyToMessageId = null,
        isService = false,
        timestamp = 1,
        isEdited = false,
    )

    private fun page(vararg ids: Long, end: Boolean = false) =
        SharedMediaPage(messages = ids.map(::message), reachedEnd = end)

    @Test
    fun prvniStrankaNahradiVysledkyPredchozihoDotazu() {
        val tab = SharedMediaTab().withFirstPage("", page(9, 8, 7))

        val found = tab.withFirstPage("faktura", page(4))

        assertEquals(listOf(4L), found.messages.map { it.id })
        assertEquals("faktura", found.query)
        assertEquals(4L, found.nextFromMessageId)
    }

    @Test
    fun dalsiStrankaSePripojiJenKeSvemuDotazu() {
        val tab = SharedMediaTab().withFirstPage("faktura", page(9, 8))

        // Pomalá odpověď na předchozí dotaz, která dorazila až po novém hledání.
        val stale = tab.withNextPage("fak", fromMessageId = 8, page = page(3))
        val current = tab.withNextPage("faktura", fromMessageId = 8, page = page(5, 4))

        assertEquals(listOf(9L, 8L), stale.messages.map { it.id })
        assertEquals(listOf(9L, 8L, 5L, 4L), current.messages.map { it.id })
    }

    @Test
    fun strankaOdjinudSeNepripoji() {
        val tab = SharedMediaTab().withFirstPage("", page(9, 8))

        val otherPlace = tab.withNextPage("", fromMessageId = 42, page = page(5))

        assertEquals(tab, otherPlace)
    }

    @Test
    fun tataZpravaNaDvouStrankachZustaneJednou() {
        val tab = SharedMediaTab().withFirstPage("", page(9, 8))

        val next = tab.withNextPage("", fromMessageId = 8, page = page(8, 7))

        assertEquals(listOf(9L, 8L, 7L), next.messages.map { it.id })
    }

    @Test
    fun pokracujeSeOdPosledniVraceneNeOdPosledniUkazane() {
        // Stránka samých mizejících fotek: po odfiltrování prázdná, ale server něco vrátil.
        val filtered = SharedMediaPage(messages = emptyList(), reachedEnd = false, nextFromMessageId = 30)
        val tab = SharedMediaTab().withFirstPage("", page(40)).withNextPage("", fromMessageId = 40, page = filtered)

        assertEquals(30L, tab.nextFromMessageId)
        assertTrue(tab.canLoadMore)
    }

    @Test
    fun naKonciSeUzNedotahuje() {
        val tab = SharedMediaTab().withFirstPage("", page(9)).withNextPage("", 9, page(end = true))

        assertFalse(tab.canLoadMore)
        assertEquals(listOf(9L), tab.messages.map { it.id })
    }

    @Test
    fun nactenaZalozkaBezStrankyNedotahuje() {
        assertFalse(SharedMediaTab(loading = true).canLoadMore)
    }

    @Test
    fun selhaniCiziStrankyStavNemeni() {
        val tab = SharedMediaTab().withFirstPage("faktura", page(9))

        assertEquals(tab, tab.withFailedPage("fak"))
        assertFalse(tab.withFailedPage("faktura").canLoadMore)
    }

    @Test
    fun hledaniJenUObsahuSeJmenemAPriVetsimPoctu() {
        assertFalse(offersSharedSearch(SharedMediaKind.Media, count = 500, query = ""))
        assertFalse(offersSharedSearch(SharedMediaKind.Files, count = SHARED_SEARCH_MIN_COUNT, query = ""))
        assertTrue(offersSharedSearch(SharedMediaKind.Files, count = SHARED_SEARCH_MIN_COUNT + 1, query = ""))
        assertTrue(offersSharedSearch(SharedMediaKind.Links, count = 40, query = ""))
        assertTrue(offersSharedSearch(SharedMediaKind.Music, count = 40, query = ""))
    }

    @Test
    fun rozepsaneHledaniSeNeschova() {
        assertTrue(offersSharedSearch(SharedMediaKind.Files, count = 2, query = "fak"))
    }
}
