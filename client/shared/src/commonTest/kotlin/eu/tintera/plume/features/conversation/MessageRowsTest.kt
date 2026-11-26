package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.settings.MEDIUM_BYTES
import eu.tintera.plume.core.telegram.Article
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ExternalReply
import eu.tintera.plume.core.telegram.LinkPreview
import eu.tintera.plume.core.telegram.LinkPreviewTarget
import eu.tintera.plume.core.telegram.MessageEntity
import eu.tintera.plume.core.telegram.MessageEntityType
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SendFailure
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.error_no_write_access
import eu.tintera.plume.resources.error_slowmode_wait_seconds
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
        entities: List<MessageEntity> = emptyList(),
        failure: SendFailure? = null,
    ) = ChatMessage(
        id = id,
        isOutgoing = isOutgoing,
        senderName = null,
        senderAvatarFileId = null,
        senderId = 1L,
        text = text,
        entities = entities,
        media = media,
        linkPreview = linkPreview,
        reactions = emptyList(),
        sendState = sendState,
        replyToMessageId = replyToMessageId,
        isService = isService,
        timestamp = timestamp,
        isEdited = false,
        mediaAlbumId = album,
        sendFailure = failure,
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
    fun mapaPolohyJdeNaPlochuJakoFotka() {
        val location = MessageMedia.Location(50.08, 14.42, title = "Staroměstské náměstí", address = "Praha")
        assertTrue(showsBareMedia(message(id = 1, media = location), album = null))
        // Odpověď mapu na ploše nechá — citace je horní díl karty mapy.
        assertTrue(showsBareMedia(message(id = 1, media = location, replyToMessageId = 5L), album = null))
    }

    @Test
    fun polohaSeNeupravujeJakoPopisek() {
        // Poloha popisek nemá; „Upravit" by slibovalo úpravu, kterou server odmítne.
        assertNull(message(id = 1, media = MessageMedia.Location(50.0, 14.0)).composerEdit())
    }

    @Test
    fun zpravaSVlastnimEmojiSeUpravujeJenKdeSeSmiPoslat() {
        val withEmoji = message(
            id = 1,
            text = "Ahoj 😀",
            entities = listOf(MessageEntity(offset = 5, length = 2, type = MessageEntityType.CustomEmoji(7))),
        )
        assertNull(withEmoji.composerEdit())
        assertEquals(ComposerEdit.Text, withEmoji.composerEdit(customEmoji = true))
        // Úprava, která už běží, se na vlastní emoji neptá — o tom se rozhodlo na začátku.
        assertEquals(ComposerEdit.Text, withEmoji.editKind())
    }

    @Test
    fun velkyGifSeSamNestahuje() {
        // Autoplay by bez ptaní snědl data; nad stropem se čeká na ťuknutí.
        assertTrue(animation().autoPlaysWithin(MEDIUM_BYTES))
        assertFalse(animation(sizeBytes = MEDIUM_BYTES + 1).autoPlaysWithin(MEDIUM_BYTES))
        // Neznámá velikost (nula od serveru) se taky nepouští sama — nevíme, co by se stáhlo.
        assertFalse(animation(sizeBytes = 0).autoPlaysWithin(MEDIUM_BYTES))
        // Vypnuté stahování (strop nula) nepustí nic.
        assertFalse(animation().autoPlaysWithin(0))
    }

    @Test
    fun popisekNaTomNicNemeni() {
        assertTrue(showsBareMedia(message(id = 1, text = "houby", media = photo()), album = null))
    }

    @Test
    fun souborAHlasovkaSiBublinuDrzi() {
        assertFalse(showsBareMedia(message(id = 1, media = document()), album = null))
    }

    private fun imageFile(
        fileName: String = "fotka.jpg",
        sizeBytes: Long = 900_000,
        thumbnail: Boolean = true,
        isSensitive: Boolean = false,
    ) = MessageMedia.Document(
        fileId = 1,
        fileName = fileName,
        mimeType = "",
        sizeBytes = sizeBytes,
        thumbnailFileId = if (thumbnail) 2 else null,
        thumbnailWidth = if (thumbnail) 320 else 0,
        thumbnailHeight = if (thumbnail) 240 else 0,
        isSensitive = isSensitive,
    )

    @Test
    fun obrazekJakoSouborJdeNaPlochuVelky() {
        assertTrue(imageFile().showsLargePreview)
        assertTrue(imageFile(fileName = "klip.mp4").showsLargePreview)
        assertTrue(showsBareMedia(message(id = 1, media = imageFile()), album = null))
        // I s textem zprávy — ten je popiskem pod názvem souboru.
        assertTrue(showsBareMedia(message(id = 1, text = "ahoj", media = imageFile()), album = null))
    }

    @Test
    fun velkyNahledJenSNahledemAObrazkem() {
        // Bez náhledu (HEIC ho od serveru nedostane) není co kreslit.
        assertFalse(imageFile(fileName = "fotka.heic", thumbnail = false).showsLargePreview)
        // PDF s náhledem je pořád dokument — ikona a název o něm řeknou víc.
        assertFalse(imageFile(fileName = "smlouva.pdf").showsLargePreview)
        // Citlivý soubor rozostří a odkryje řádek.
        assertFalse(imageFile(isSensitive = true).showsLargePreview)
        // Album souborů je seznam, ne mřížka.
        val members = listOf(message(id = 1, media = imageFile()), message(id = 2, media = imageFile()))
        assertFalse(showsBareMedia(members.first(), album = members))
    }

    @Test
    fun originalSeStahujeJenMalyObrazek() {
        assertTrue(imageFile().loadsOriginalPreview(MEDIUM_BYTES))
        assertFalse(imageFile(sizeBytes = MEDIUM_BYTES + 1).loadsOriginalPreview(MEDIUM_BYTES))
        // Neznámá velikost se nestahuje, stejně jako u GIFů.
        assertFalse(imageFile(sizeBytes = 0).loadsOriginalPreview(MEDIUM_BYTES))
        // Video zůstává u náhledu.
        assertFalse(imageFile(fileName = "klip.mp4").loadsOriginalPreview(MEDIUM_BYTES))
        // Bez automatického stahování souborů zůstane u náhledu i malý obrázek.
        assertFalse(imageFile().loadsOriginalPreview(0))
    }

    @Test
    fun textovaZpravaSiBublinuDrzi() {
        assertFalse(showsBareMedia(message(id = 1, text = "ahoj"), album = null))
    }

    @Test
    fun citaceBublinuNevynuti() {
        // Citace je horní díl karty snímku, jako značka přeposlání.
        assertTrue(showsBareMedia(message(id = 1, media = photo(), replyToMessageId = 7L), album = null))
        val external = message(id = 1, media = photo()).copy(
            externalReply = ExternalReply(quote = message(id = 9, text = "jinde"), target = null),
        )
        assertTrue(showsBareMedia(external, album = null))
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
        showAboveText = false,
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
        val empty = LinkPreview(
            url = "https://example.com",
            siteName = null,
            title = null,
            description = null,
            photo = null,
            largePhoto = false,
            showAboveText = false,
        )

        assertFalse(showsLinkPreviewCard(message(id = 1, text = "odkaz", linkPreview = empty)))
    }

    @Test
    fun profilovkaVNahleduNeniNikdyVelka() {
        // Odkaz na kanál: profilovka jde do kruhu vedle popisu, ne přes celou šířku karty.
        val channel = preview().copy(target = LinkPreviewTarget.Channel)
        assertTrue(preview().showsLargePhoto)
        assertFalse(channel.showsLargePhoto)
    }

    @Test
    fun nahledJenSeSamolepkamiJeKarta() {
        val sticker = MessageMedia.Sticker(
            stickerFileId = 1, setId = 2, fileId = 1, animationFileId = null, thumbnailFileId = null,
            width = 512, height = 512, emoji = "🙂",
        )
        val stickers = LinkPreview(
            url = "https://t.me/addstickers/x", siteName = null, title = null, description = null, photo = null,
            largePhoto = false, showAboveText = false, target = LinkPreviewTarget.Stickers, stickers = listOf(sticker),
        )
        assertTrue(showsLinkPreviewCard(message(id = 1, text = "odkaz", linkPreview = stickers)))
    }

    @Test
    fun nahledSCitaciSiBublinuDrzi() {
        val reply = message(id = 1, text = "odkaz", linkPreview = preview(), replyToMessageId = 7L)

        assertFalse(showsLinkPreviewCard(reply))
    }

    @Test
    fun nahledVeVlaknuKomentaruJeKarta() {
        val comment = message(id = 1, text = "odkaz", linkPreview = preview(), replyToMessageId = 7L)

        assertTrue(showsLinkPreviewCard(comment, threadRootId = 7L))
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

    private fun quote(offset: Int, length: Int, expandable: Boolean) =
        MessageEntity(offset = offset, length = length, type = MessageEntityType.Quote(expandable = expandable))

    @Test
    fun sbalitelnaCitacePresCelouZpravuSeSbali() {
        val text = "Převzatá věta."
        assertTrue(collapsesQuote(message(1, text = text, entities = listOf(quote(0, text.length, expandable = true)))))
    }

    @Test
    fun obycejnaCitaceSeNesbali() {
        val text = "Převzatá věta."
        assertFalse(collapsesQuote(message(1, text = text, entities = listOf(quote(0, text.length, expandable = false)))))
    }

    @Test
    fun sbalitelnaCitaceUvnitrDelsiZpravySeNesbali() {
        // `maxLines` platí pro celý text, takže by se ořízla i věta pod citací.
        val text = "Citace.\nA moje věta pod ní."
        assertFalse(collapsesQuote(message(1, text = text, entities = listOf(quote(0, 7, expandable = true)))))
    }

    @Test
    fun zpravaBezCitaceSeNesbali() {
        assertFalse(collapsesQuote(message(1, text = "Jen text.")))
    }

    private val slowMode = SendFailure(canRetry = true, reason = UiText(Res.string.error_slowmode_wait_seconds, 30))
    private val forbidden = SendFailure(canRetry = false, reason = UiText(Res.string.error_no_write_access))

    private fun failed(id: Long, album: Long = 0L, failure: SendFailure = slowMode) =
        message(id, album = album, sendState = SendState.Failed, failure = failure)

    @Test
    fun znovuSePosilaJenNeodeslanaZprava() {
        val messages = listOf(failed(3), message(2, sendState = SendState.Sent), message(1, sendState = SendState.Sent))
        assertEquals(listOf(3L), failedGroup(messages, 3).map { it.id })
        // Mezitím odešla, nebo zmizela: není co posílat.
        assertTrue(failedGroup(messages, 2).isEmpty())
        assertTrue(failedGroup(messages, 99).isEmpty())
    }

    @Test
    fun albumSePosilaCeleAVzestupne() {
        // Odeslaná fotka alba zůstává, neodeslané jdou spolu — po kouskách by se album rozpadlo.
        val messages = listOf(failed(6, album = 9), message(5, album = 9, sendState = SendState.Sent), failed(4, album = 9), failed(3))
        assertEquals(listOf(4L, 6L), failedGroup(messages, 6).map { it.id })
        assertEquals(listOf(4L, 6L), failedGroup(messages, 4).map { it.id })
    }

    @Test
    fun albumJdePoslatZnovuJenCele() {
        assertNull(albumFailure(listOf(message(2, album = 9, sendState = SendState.Sent))))
        assertEquals(slowMode, albumFailure(listOf(failed(2, album = 9), failed(1, album = 9))))
        // Důvod je ten, kvůli kterému to nejde.
        assertEquals(
            SendFailure(canRetry = false, reason = forbidden.reason),
            albumFailure(listOf(failed(2, album = 9), failed(1, album = 9, failure = forbidden))),
        )
    }

    @Test
    fun neodeslaneAlbumNeseDuvodVBubline() {
        val row = groupAlbums(listOf(failed(2, album = 9, failure = forbidden), message(1, album = 9, sendState = SendState.Sent))).single()
        assertEquals(SendState.Failed, row.display.sendState)
        assertEquals(false, row.display.sendFailure?.canRetry)
    }

    @Test
    fun dlouhyTextSeSbaluje() {
        assertTrue(collapsesLongText(message(1, text = "Dlouhý text"), searchQuery = null))
    }

    @Test
    fun zpravaSNalezemHledaniSeNesbaluje() {
        // Nález by mohl být ve schované části a skok na něj by ho neukázal.
        assertFalse(collapsesLongText(message(1, text = "Někde dole je slovo hledané"), searchQuery = "hledané"))
        assertTrue(collapsesLongText(message(1, text = "Někde dole je slovo"), searchQuery = "jiné"))
    }

    @Test
    fun clanekSeNesbaluje() {
        // Článek má vlastní „Číst celý článek".
        assertFalse(collapsesLongText(message(1, text = "Článek").copy(article = Article()), searchQuery = null))
    }
}
