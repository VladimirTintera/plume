package eu.tintera.plume.features.conversation.items

import eu.tintera.plume.features.conversation.ConversationState

import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.LinkPreview
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.telegram.SponsoredMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Skládání bloků konverzace. Zprávy i výsledek jdou jako v obráceném seznamu: **od nejnovější**,
 * index 0 je dole.
 */
class ConversationItemsTest {

    private fun message(
        id: Long,
        timestamp: Int = DAY + id.toInt(),
        senderId: Long = 1L,
        isOutgoing: Boolean = false,
        sendState: SendState? = if (isOutgoing) SendState.Sent else null,
        isService: Boolean = false,
        album: Long = 0L,
        replyToMessageId: Long? = null,
        text: String = "zpráva $id",
        media: MessageMedia? = null,
        linkPreview: LinkPreview? = null,
    ) = ChatMessage(
        id = id,
        isOutgoing = isOutgoing,
        senderName = null,
        senderAvatarFileId = null,
        senderId = senderId,
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

    private fun info(isGroup: Boolean = false, isChannel: Boolean = false) = ChatInfo(
        id = CHAT_ID,
        title = "Chat",
        lastReadOutboxMessageId = 0L,
        lastReadInboxMessageId = 0L,
        unreadCount = 0,
        avatarFileId = null,
        isGroup = isGroup,
        isChannel = isChannel,
        isSavedMessages = false,
        draftText = null,
        draftReplyToMessageId = null,
    )

    private fun items(vararg messages: ChatMessage, state: ConversationState = ConversationState()) =
        conversationItems(state.copy(messages = messages.toList()))

    private fun List<ConversationItem>.frames(): List<MessageFrameData> =
        filterIsInstance<MessageItem>().map { it.frame }

    private fun List<ConversationItem>.kinds(): List<String> = map {
        when (it) {
            is MessageItem -> "m${it.frame.message.id}"
            is ServiceMessageItem -> "s${it.message.id}"
            is DayDividerItem -> "day"
            is TimeDividerItem -> "time"
            UnreadDividerItem -> "unread"
            is SponsoredItem -> "ad"
            LoadingOlderItem -> "loading"
            else -> error("neznámý blok $it")
        }
    }

    @Test
    fun predelDneStojiNadNejstarsiZpravouDne() {
        // Předěl je NAD zprávou, takže v obráceném seznamu ZA ní.
        val result = items(message(3, timestamp = NEXT_DAY), message(2), message(1))
        assertEquals(listOf("m3", "day", "m2", "m1", "day"), result.kinds())
    }

    @Test
    fun poHodinovePauzeStojiNadZpravouCas() {
        val result = items(message(3, timestamp = DAY + HOUR + 60), message(2, timestamp = DAY + 60), message(1))
        assertEquals(listOf("m3", "time", "m2", "m1", "day"), result.kinds())
    }

    @Test
    fun kratsiPauzaCasNepridava() {
        val result = items(message(3, timestamp = DAY + HOUR - 1), message(1))
        assertEquals(listOf("m3", "m1", "day"), result.kinds())
    }

    @Test
    fun naPrelomuDneNeseCasPredelDneNeDvaPredely() {
        val result = items(message(2, timestamp = NEXT_DAY), message(1))
        assertEquals(listOf("m2", "day", "m1", "day"), result.kinds())
    }

    @Test
    fun casovyPredelUkoncujeBlok() {
        // Tentýž odesílatel, ale po pauze: nová skupina, nároží nenavazují.
        val result = items(message(2, timestamp = DAY + HOUR + 1), message(1)).frames()
        assertEquals(false, result[0].continuesAbove)
        assertEquals(false, result[1].continuesBelow)
    }

    @Test
    fun casovyPredelStojiNadPredelemNeprectenych() {
        val result = items(
            message(2, timestamp = DAY + HOUR + 1),
            message(1),
            state = ConversationState(unreadBoundaryId = 1L),
        )
        assertEquals(listOf("m2", "unread", "time", "m1", "day"), result.kinds())
    }

    @Test
    fun klicePredeluJsouJedinecneIKdyzSeDenOpakuje() {
        // Posunuté hodiny odesílatele: novější zpráva s datem z předchozího dne.
        val result = items(message(3), message(2, timestamp = NEXT_DAY), message(1))
        val keys = result.map { it.key }
        assertEquals(keys.size, keys.toSet().size, "klíče: $keys")
    }

    @Test
    fun predelNeprectenychStojiNadPrvniNeprectenou() {
        val result = items(
            message(3),
            message(2),
            message(1),
            state = ConversationState(unreadBoundaryId = 1L),
        )
        assertEquals(listOf("m3", "m2", "unread", "m1", "day"), result.kinds())
    }

    @Test
    fun predelNeprectenychNadNejstarsiNactenouNeni() {
        // Hranice je starší než všechno načtené: nevíme, kde nepřečtené začínají.
        val result = items(message(3), message(2), state = ConversationState(unreadBoundaryId = 1L))
        assertEquals(listOf("m3", "m2", "day"), result.kinds())
    }

    @Test
    fun reklamaDoleANacitaniNahore() {
        val sponsored = SponsoredMessage(messageId = 7L, title = "", text = "", buttonText = "", url = "", isRecommended = false, additionalInfo = null)
        val result = items(
            message(1),
            state = ConversationState(sponsored = listOf(sponsored), loading = true),
        )
        assertEquals(listOf("ad", "m1", "day", "loading"), result.kinds())
    }

    @Test
    fun servisniZpravaPreruziBlok() {
        val result = items(message(3), message(2, isService = true), message(1))
        val newest = assertIs<MessageItem>(result[0])
        assertEquals(false, newest.frame.continuesAbove)
        assertIs<ServiceMessageItem>(result[1])
    }

    @Test
    fun seskupeniSePtaNaObaSousedy() {
        val result = items(message(3), message(2), message(1)).frames()
        // Nejnovější navazuje nahoru, ne dolů; prostřední oběma směry; nejstarší jen dolů.
        assertEquals(listOf(true to false, true to true, false to true), result.map { it.continuesAbove to it.continuesBelow })
    }

    @Test
    fun zaPredelemDneZacinaNovyBlok() {
        val result = items(message(2, timestamp = NEXT_DAY), message(1)).frames()
        assertEquals(false, result[0].continuesAbove)
        assertEquals(false, result[1].continuesBelow)
    }

    @Test
    fun tvarOdesilateleJenVeSkupineUPosledniZpravyBloku() {
        val group = ConversationState(info = info(isGroup = true))
        val result = items(message(3, senderId = 5), message(2, senderId = 5), message(1, senderId = 6), state = group).frames()
        assertEquals(listOf(SenderAvatar.Shown, SenderAvatar.Spacer, SenderAvatar.Shown), result.map { it.avatar })

        val private = items(message(1), state = ConversationState(info = info()))
        assertEquals(SenderAvatar.None, assertIs<MessageItem>(private[0]).frame.avatar)
    }

    @Test
    fun kanalNemaTvarANeotviraReakce() {
        val channel = ConversationState(info = info(isGroup = true, isChannel = true))
        val item = assertIs<MessageItem>(items(message(1, senderId = CHAT_ID), state = channel)[0]).frame
        assertEquals(SenderAvatar.None, item.avatar)
        assertEquals(false, item.reactionsOpenable)
    }

    @Test
    fun prectenoHlasiJenNejnovejsiOdchozi() {
        val result = items(
            message(3, isOutgoing = true),
            message(2, isOutgoing = true),
            state = ConversationState(lastReadOutboxMessageId = 3L),
        ).frames()
        // Skutečný stav mají obě, odznak „přečteno" jen ta nejnovější.
        assertEquals(listOf(Delivery.Read, Delivery.Read), result.map { it.delivery })
        assertEquals(listOf(Delivery.Read, null), result.map { it.badge })
    }

    @Test
    fun citaceSeHledaVNactenychIVDotazenych() {
        val old = message(99)
        val result = items(
            message(3, replyToMessageId = 1),
            message(2, replyToMessageId = 99),
            message(1),
            state = ConversationState(quotes = mapOf(99L to old)),
        ).filterIsInstance<BubbleMessageItem>()
        assertEquals(1L, result[0].quoted?.id)
        assertEquals(99L, result[1].quoted?.id)
        assertNull(result[2].quoted)
    }

    @Test
    fun albumJeJedenBlokSKlicemNejstarsiZpravy() {
        val result = items(message(3, album = 9), message(2, album = 9), message(1))
        val album = assertIs<BubbleMessageItem>(result[0])
        assertEquals(2L, album.key)
        assertEquals(listOf(2L, 3L), album.album?.map { it.id })
        assertTrue(album.containsMessage(3L))
        assertTrue(album.containsMessage(2L))
    }

    @Test
    fun odeslanaZpravaDrziDocasnyKlic() {
        val result = items(message(50, isOutgoing = true), state = ConversationState(itemKeys = mapOf(50L to 7L)))
        assertEquals(7L, result[0].key)
    }

    private val photo = MessageMedia.Photo(fileId = 1, thumbnailFileId = null, fullFileId = 2, width = 1080, height = 1920)

    private val sticker = MessageMedia.Sticker(
        stickerFileId = 1,
        setId = 5L,
        fileId = 1,
        animationFileId = null,
        thumbnailFileId = null,
        width = 512,
        height = 512,
        emoji = "🙂",
    )

    private fun preview(large: Boolean = true) = LinkPreview(
        url = "https://example.com",
        siteName = "Example",
        title = "Titulek",
        description = null,
        photo = photo,
        largePhoto = large,
    )

    private fun single(message: ChatMessage) = items(message).first()

    @Test
    fun obycejnyTextJeBublina() {
        assertIs<BubbleMessageItem>(single(message(1)))
    }

    @Test
    fun parEmojiJeVelkychBezBubliny() {
        val item = assertIs<LargeEmojiMessageItem>(single(message(1, text = "🙂🙂")))
        assertEquals(2, item.emojiCount)
    }

    @Test
    fun emojiVOdpovediSiBublinuDrzi() {
        // Citace se bez pozadí ztratí.
        assertIs<BubbleMessageItem>(single(message(1, text = "🙂", replyToMessageId = 99)))
    }

    @Test
    fun samolepkaJeBezBubliny() {
        assertIs<StickerMessageItem>(single(message(1, text = "", media = sticker)))
        assertIs<BubbleMessageItem>(single(message(1, text = "", media = sticker, replyToMessageId = 99)))
    }

    @Test
    fun fotkaJeNaPlose() {
        assertIs<MediaMessageItem>(single(message(1, text = "popisek", media = photo)))
    }

    @Test
    fun albumFotekJeJedenBlokNaPlose() {
        val result = items(message(3, album = 9, media = photo), message(2, album = 9, media = photo, text = "popisek"))
        val album = assertIs<MediaMessageItem>(result[0])
        assertEquals(listOf(2L, 3L), album.album?.map { it.id })
        // Popisek, reakce i nabídka patří zprávě s popiskem.
        assertEquals(2L, album.frame.message.id)
    }

    @Test
    fun nahledOdkazuJeVzdyckyKarta() {
        // Velký i malý obrázek: karta ze dvou částí, ne proužek v bublině.
        assertIs<LinkPreviewMessageItem>(single(message(1, text = "https://example.com", linkPreview = preview())))
        assertIs<LinkPreviewMessageItem>(single(message(1, text = "https://example.com", linkPreview = preview(large = false))))
    }

    @Test
    fun selhaneOdeslaniJeVidetVzdycky() {
        val failed = items(
            message(3, isOutgoing = true),
            message(2, isOutgoing = true, sendState = SendState.Failed),
        ).frames()
        assertEquals(Delivery.Failed, failed[1].badge)
    }

    private companion object {
        /** Poledne jednoho dne v UTC — daleko od půlnoci v každém rozumném časovém pásmu. */
        const val DAY = 86_400 * 20_000 + 43_200
        const val NEXT_DAY = DAY + 86_400
        const val HOUR = 3_600
        const val CHAT_ID = 1000L
    }
}
