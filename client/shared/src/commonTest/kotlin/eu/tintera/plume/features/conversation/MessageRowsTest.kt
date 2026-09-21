package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.LinkPreview
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.MAX_AUTOPLAY_ANIMATION_BYTES
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.features.conversation.items.albumRowSizes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Skládání alb do položek konverzace. Zprávy jdou jako v konverzaci: od nejnovější. */
class MessageRowsTest {

    private fun message(
        id: Long,
        album: Long = 0L,
        text: String = "",
        timestamp: Int = id.toInt(),
        sendState: SendState? = null,
        isOutgoing: Boolean = sendState != null,
        isService: Boolean = false,
        media: MessageMedia? = null,
        replyToMessageId: Long? = null,
        linkPreview: LinkPreview? = null,
    ) = ChatMessage(
        id = id,
        isOutgoing = isOutgoing,
        senderName = null,
        senderAvatarFileId = null,
        senderId = 1L,
        text = text,
        entities = emptyList(),
        media = media,
        linkPreview = linkPreview,
        reactions = emptyList(),
        sendState = sendState,
        replyToMessageId = replyToMessageId,
        isService = isService,
        timestamp = timestamp,
        isEdited = false,
        mediaAlbumId = album,
    )

    @Test
    fun samostatneZpravyZustavajiSamostatne() {
        val rows = groupAlbums(listOf(message(3), message(2), message(1)))
        assertEquals(listOf(1, 1, 1), rows.map { it.messages.size })
    }

    @Test
    fun albumJeJednaPolozka() {
        val rows = groupAlbums(listOf(message(5), message(4, album = 9), message(3, album = 9), message(2, album = 9), message(1)))
        assertEquals(listOf(1, 3, 1), rows.map { it.messages.size })
        val album = rows[1]
        assertTrue(album.isAlbum)
        // Mřížka jde od nejstarší.
        assertEquals(listOf(2L, 3L, 4L), album.members.map { it.id })
    }

    @Test
    fun dveSousedniAlbaSeNeslevaji() {
        val rows = groupAlbums(listOf(message(4, album = 2), message(3, album = 2), message(2, album = 1), message(1, album = 1)))
        assertEquals(listOf(2, 2), rows.map { it.messages.size })
    }

    @Test
    fun albumPreruseneJinouZpravouSeRozdeli() {
        val rows = groupAlbums(listOf(message(3, album = 7), message(2), message(1, album = 7)))
        assertEquals(3, rows.size)
    }

    @Test
    fun hlavniZpravaAlbaJeTaSPopiskem() {
        val row = groupAlbums(listOf(message(3, album = 1), message(2, album = 1, text = "Výlet"), message(1, album = 1))).single()
        assertEquals(2L, row.primary.id)
        assertEquals("Výlet", row.display.text)
        // Čas bubliny je čas nejnovější fotky.
        assertEquals(3, row.display.timestamp)
    }

    @Test
    fun bezPopiskuJeHlavniNejstarsi() {
        val row = groupAlbums(listOf(message(2, album = 1), message(1, album = 1))).single()
        assertEquals(1L, row.primary.id)
    }

    @Test
    fun albumJeOdeslaneAzCele() {
        val row = groupAlbums(
            listOf(message(2, album = 1, sendState = SendState.Sending), message(1, album = 1, sendState = SendState.Sent)),
        ).single()
        assertEquals(SendState.Sending, row.display.sendState)
    }

    @Test
    fun rozlozeniMrizky() {
        assertEquals(listOf(2), albumRowSizes(2))
        assertEquals(listOf(1, 2), albumRowSizes(3))
        assertEquals(listOf(2, 2, 3), albumRowSizes(7))
        (1..10).forEach { count -> assertEquals(count, albumRowSizes(count).sum()) }
    }

    // --- Kde se hlásí „přečteno" (readBadgeRow) ---

    private fun outgoing(id: Long) = message(id, sendState = SendState.Sent)
    private fun incoming(id: Long) = message(id, isOutgoing = false)

    @Test
    fun prectenoHlasiNejnovejsiMojeZprava() {
        val rows = groupAlbums(listOf(outgoing(3), outgoing(2), incoming(1)))
        assertEquals(3L, readBadgeRows(rows).firstOrNull()?.display?.id)
    }

    @Test
    fun odpovedProtejskuOdznakSchova() {
        // Protějšek napsal PO mojí zprávě, takže ji musel přečíst — odznak nemá co dodat.
        val rows = groupAlbums(listOf(incoming(3), outgoing(2), outgoing(1)))
        assertNull(readBadgeRows(rows).firstOrNull())
    }

    @Test
    fun servisniOznameniOdznakNeschova() {
        // „X se přidal do skupiny" nedokazuje, že si někdo něco přečetl.
        val rows = groupAlbums(listOf(message(3, isService = true), outgoing(2), incoming(1)))
        assertEquals(2L, readBadgeRows(rows).firstOrNull()?.display?.id)
    }

    @Test
    fun bezOdchozichZpravNehlasiNikdo() {
        val rows = groupAlbums(listOf(incoming(2), incoming(1)))
        assertNull(readBadgeRows(rows).firstOrNull())
    }

    @Test
    fun prazdnaKonverzaceNespadne() {
        assertNull(readBadgeRows(emptyList()).firstOrNull())
    }

    @Test
    fun odznakNeseCeleAlbumPresJehoNejstarsiZpravu() {
        // Klíč položky je klíč nejstarší zprávy alba, takže se odznak musí vázat na ni.
        val rows = groupAlbums(
            listOf(
                message(3, album = 5, sendState = SendState.Sent),
                message(2, album = 5, sendState = SendState.Sent),
                incoming(1),
            ),
        )
        assertEquals(2L, readBadgeRows(rows).firstOrNull()?.oldest?.id)
    }

    // --- Médium bez bubliny ---------------------------------------------------------------

    private fun photo() = MessageMedia.Photo(
        fileId = 1,
        thumbnailFileId = null,
        fullFileId = 2,
        width = 1080,
        height = 1920,
    )

    private fun video() = MessageMedia.Video(
        fileId = 1,
        thumbnailFileId = null,
        width = 1920,
        height = 1080,
        durationSeconds = 5,
    )

    private fun document() = MessageMedia.Document(
        fileId = 1,
        fileName = "smlouva.pdf",
        mimeType = "application/pdf",
        sizeBytes = 1024,
        thumbnailFileId = null,
    )

    private fun animation(sizeBytes: Long = 400_000) = MessageMedia.Animation(
        fileId = 1,
        thumbnailFileId = null,
        width = 480,
        height = 480,
        sizeBytes = sizeBytes,
    )

    @Test
    fun fotkaVideoAGifJdouNaPlochu() {
        assertTrue(showsBareMedia(message(id = 1, media = photo()), album = null))
        assertTrue(showsBareMedia(message(id = 1, media = video()), album = null))
        // GIF je taky obrázek sám o sobě — bublina kolem něj nemá co ohraničovat.
        assertTrue(showsBareMedia(message(id = 1, media = animation()), album = null))
    }

    @Test
    fun velkyGifSeSamNestahuje() {
        // Autoplay by bez ptaní snědl data; nad stropem se čeká na ťuknutí.
        assertTrue(animation().autoPlays)
        assertFalse(animation(sizeBytes = MAX_AUTOPLAY_ANIMATION_BYTES + 1).autoPlays)
        // Neznámá velikost (nula od serveru) se taky nepouští sama — nevíme, co by se stáhlo.
        assertFalse(animation(sizeBytes = 0).autoPlays)
    }

    @Test
    fun popisekNaTomNicNemeni() {
        assertTrue(showsBareMedia(message(id = 1, text = "houby", media = photo()), album = null))
    }

    @Test
    fun souborAHlasovkaSiBublinuDrzi() {
        assertFalse(showsBareMedia(message(id = 1, media = document()), album = null))
    }

    @Test
    fun textovaZpravaSiBublinuDrzi() {
        assertFalse(showsBareMedia(message(id = 1, text = "ahoj"), album = null))
    }

    @Test
    fun citaceSiBublinuVynuti() {
        val withQuote = message(id = 1, media = photo(), replyToMessageId = 7L)

        assertFalse(showsBareMedia(withQuote, album = null))
    }

    @Test
    fun albumFotekJdeNaPlochu() {
        val members = listOf(message(id = 1, media = photo()), message(id = 2, media = video()))

        assertTrue(showsBareMedia(members.first(), album = members))
    }

    @Test
    fun albumDokumentuSiBublinuDrzi() {
        val members = listOf(message(id = 1, media = document()), message(id = 2, media = document()))

        assertFalse(showsBareMedia(members.first(), album = members))
    }

    @Test
    fun albumSJednimDokumentemSiBublinuDrzi() {
        val members = listOf(message(id = 1, media = photo()), message(id = 2, media = document()))

        assertFalse(showsBareMedia(members.first(), album = members))
    }

    // --- Karta náhledu odkazu ------------------------------------------------------------

    private fun preview(
        photo: MessageMedia.Photo? = photo(),
        largePhoto: Boolean = true,
    ) = LinkPreview(
        url = "https://example.com",
        siteName = "Example",
        title = "Titulek",
        description = null,
        photo = photo,
        largePhoto = largePhoto,
    )

    @Test
    fun nahledSVelkymObrazkemJeKarta() {
        val message = message(id = 1, text = "odkaz", linkPreview = preview())

        assertTrue(showsLinkPreviewCard(message))
        assertTrue(message.linkPreview!!.showsLargePhoto)
    }

    @Test
    fun malyObrazekJeTakyKartaJenBezVelkehoObrazku() {
        val small = message(id = 1, text = "odkaz", linkPreview = preview(largePhoto = false))

        assertTrue(showsLinkPreviewCard(small))
        assertFalse(small.linkPreview!!.showsLargePhoto)
    }

    @Test
    fun nahledBezObrazkuJeKarta() {
        val textOnly = message(id = 1, text = "odkaz", linkPreview = preview(photo = null))

        assertTrue(showsLinkPreviewCard(textOnly))
        assertFalse(textOnly.linkPreview!!.showsLargePhoto)
    }

    @Test
    fun prazdnyNahledKartaNeni() {
        val empty = LinkPreview(url = "https://example.com", siteName = null, title = null, description = null, photo = null, largePhoto = false)

        assertFalse(showsLinkPreviewCard(message(id = 1, text = "odkaz", linkPreview = empty)))
    }

    @Test
    fun nahledSCitaciSiBublinuDrzi() {
        val reply = message(id = 1, text = "odkaz", linkPreview = preview(), replyToMessageId = 7L)

        assertFalse(showsLinkPreviewCard(reply))
    }

    @Test
    fun zpravaBezNahleduNeniNahled() {
        assertFalse(showsLinkPreviewCard(message(id = 1, text = "jen text")))
    }

    @Test
    fun nahledUZpravySPrilohouZustavaVBubline() {
        val withMedia = message(id = 1, text = "odkaz", media = photo(), linkPreview = preview())

        assertFalse(showsLinkPreviewCard(withMedia))
        // A příloha sama taky ne — náhled je to, co bublinu drží.
        assertFalse(showsBareMedia(withMedia, album = null))
    }

    @Test
    fun vyzadanyOdznakSeUkazeIUStarsiZpravy() {
        val rows = groupAlbums(
            listOf(
                message(id = 5, sendState = SendState.Sent),
                message(id = 3, sendState = SendState.Sent),
                message(id = 2, sendState = SendState.Sent),
            ),
        )

        val badges = readBadgeRows(rows, revealedMessageId = 2L).map { it.display.id }

        // Automatický odznak u nejnovější zprávy zůstává, vyžádaný přibude.
        assertEquals(listOf(5L, 2L), badges)
    }

    @Test
    fun vyzadanyOdznakUNejnovejsiSeNezdvoji() {
        val rows = groupAlbums(listOf(message(id = 5, sendState = SendState.Sent)))

        assertEquals(listOf(5L), readBadgeRows(rows, revealedMessageId = 5L).map { it.display.id })
    }

    @Test
    fun vyzadanyOdznakUCiziZpravyNeplati() {
        val rows = groupAlbums(
            listOf(
                message(id = 5, sendState = SendState.Sent),
                message(id = 2, isOutgoing = false),
            ),
        )

        assertEquals(listOf(5L), readBadgeRows(rows, revealedMessageId = 2L).map { it.display.id })
    }

    @Test
    fun vyzadanyOdznakUAlbaPlatiProCeleAlbum() {
        val rows = groupAlbums(
            listOf(
                message(id = 9, isOutgoing = false),
                message(id = 4, album = 7L, sendState = SendState.Sent),
                message(id = 3, album = 7L, sendState = SendState.Sent),
            ),
        )

        // Ťuknutí na kteroukoli fotku alba hlásí odznak u celé položky.
        assertEquals(listOf(3L), readBadgeRows(rows, revealedMessageId = 4L).map { it.oldest.id })
    }
}
