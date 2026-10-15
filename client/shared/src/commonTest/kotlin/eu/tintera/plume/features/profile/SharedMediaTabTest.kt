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
        // GIF ani kolečko nemají podle čeho hledat — jako fotky v Médiích.
        assertFalse(offersSharedSearch(SharedMediaKind.Gifs, count = 500, query = ""))
        assertFalse(offersSharedSearch(SharedMediaKind.VideoNotes, count = 500, query = ""))
        assertFalse(offersSharedSearch(SharedMediaKind.Voice, count = 500, query = ""))
    }

    @Test
    fun rozepsaneHledaniSeNeschova() {
        assertTrue(offersSharedSearch(SharedMediaKind.Files, count = 2, query = "fak"))
    }

    @Test
    fun oknoPoSkokuNahradiSeznamAKNejnovejsiNesaha() {
        val tab = SharedMediaTab().withFirstPage("", page(100, 99, 98))

        val window = tab.withWindow(target = 50, page(55, 52, 50, 48, 45))

        assertEquals(listOf(55L, 52L, 50L, 48L, 45L), window.messages.map { it.id })
        assertFalse(window.reachedNewest)
        assertTrue(window.canLoadNewer)
        assertEquals(50L, window.scrollTo)
        // Ke starším se pokračuje jako vždy, od posledního vráceného.
        assertEquals(45L, window.nextFromMessageId)
    }

    @Test
    fun novejsiSePridajiNadOkno() {
        val window = SharedMediaTab().withWindow(target = 50, page(55, 50, 45))

        // Stránka čtená od nejnovější načtené obsahuje i ji samotnou.
        val grown = window.copy(loadingNewer = true).withNewerPage(fromMessageId = 55, page(70, 60, 55))

        assertEquals(listOf(70L, 60L, 55L, 50L, 45L), grown.messages.map { it.id })
        assertFalse(grown.loadingNewer)
        assertFalse(grown.reachedNewest)
    }

    @Test
    fun stranaBezNovejsichZnamenaNejnovejsi() {
        val window = SharedMediaTab().withWindow(target = 50, page(55, 50))

        val top = window.withNewerPage(fromMessageId = 55, page(55))

        assertTrue(top.reachedNewest)
        assertFalse(top.canLoadNewer)
    }

    @Test
    fun novejsiZJinehoOknaSeZahodi() {
        val window = SharedMediaTab().withWindow(target = 50, page(55, 50))

        // Mezitím se skočilo jinam; stránka patřila oknu začínajícímu zprávou 80.
        val same = window.withNewerPage(fromMessageId = 80, page(90, 80))

        assertEquals(window, same)
    }
}
