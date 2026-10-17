package eu.tintera.plume.features.conversation.items

import eu.tintera.plume.features.conversation.ConversationState
import eu.tintera.plume.features.conversation.Typing
import eu.tintera.plume.features.conversation.NoticeMenu
import eu.tintera.plume.core.telegram.SelfDestructType
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.content_expired_photo
import eu.tintera.plume.core.telegram.ChatActivity
import eu.tintera.plume.core.telegram.SenderInfo

import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ExternalReply
import eu.tintera.plume.core.telegram.ForwardOrigin
import eu.tintera.plume.core.telegram.LinkPreview
import eu.tintera.plume.core.telegram.PostStats
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
        forwardOrigin: ForwardOrigin? = null,
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
        forwardOrigin = forwardOrigin,
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
            is TypingItem -> "typing"
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
    fun predelNeprectenychPreskociVlastniOdpoved() {
        // Hranice je poslední přečtená PŘÍCHOZÍ; vlastní odpověď po ní (m2) má vyšší id, ale
        // nepřečtená není. Předěl patří nad m3 — dřív se neukázal vůbec.
        val result = items(
            message(3),
            message(2, isOutgoing = true),
            message(1),
            state = ConversationState(unreadBoundaryId = 1L),
        )
        assertEquals(listOf("m3", "unread", "m2", "m1", "day"), result.kinds())
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
    fun piseStojiPodNejnovejsiZpravouANadReklamou() {
        val sponsored = SponsoredMessage(messageId = 7L, title = "", text = "", buttonText = "", url = "", isRecommended = false, additionalInfo = null)
        val result = items(
            message(2),
            message(1),
            state = ConversationState(sponsored = listOf(sponsored), typing = Typing(ChatActivity.Typing, sender = null)),
        )
        assertEquals(listOf("ad", "typing", "m2", "m1", "day"), result.kinds())
    }

    @Test
    fun piseVOkneVHistoriiNeni() {
        // Nejnovější zpráva v okně není — indikátor by stál uprostřed minulosti.
        val result = items(
            message(1),
            state = ConversationState(reachedEnd = false, typing = Typing(ChatActivity.Typing, sender = null)),
        )
        assertEquals(listOf("m1", "day"), result.kinds())
    }

    @Test
    fun piseVSoukromemChatuNeseTvarChatu() {
        val result = items(
            message(1),
            state = ConversationState(info = info(), typing = Typing(ChatActivity.Typing, sender = null)),
        )
        val typing = assertIs<TypingItem>(result[0])
        assertEquals(TypingFace(CHAT_ID, "Chat", null), typing.face)
        assertEquals(false, typing.inGroup)
    }

    @Test
    fun piseVeSkupineNeseTvarOdesilatele() {
        val sender = SenderInfo(id = 42L, title = "Jana", avatarFileId = 5)
        val result = items(
            message(1),
            state = ConversationState(info = info(isGroup = true), typing = Typing(ChatActivity.RecordingVoice, sender)),
        )
        val typing = assertIs<TypingItem>(result[0])
        assertEquals(TypingFace(42L, "Jana", 5), typing.face)
        assertEquals(true, typing.inGroup)
        assertEquals(ChatActivity.RecordingVoice, typing.activity)
    }

    @Test
    fun piseVeSkupineBezZnamehoOdesilateleNemaTvarChatu() {
        // Tvář skupiny u trojtečky by tvrdila, že píše skupina.
        val result = items(
            message(1),
            state = ConversationState(info = info(isGroup = true), typing = Typing(ChatActivity.Typing, sender = null)),
        )
        assertNull(assertIs<TypingItem>(result[0]).face)
    }

    @Test
    fun kanalPiseNeukazuje() {
        val result = items(
            message(1),
            state = ConversationState(info = info(isGroup = true, isChannel = true), typing = Typing(ChatActivity.Typing, sender = null)),
        )
        assertEquals(listOf("m1", "day"), result.kinds())
    }

    @Test
    fun teckyStridajiVyskokAOdpocinek() {
        // Každá tečka skáče jen v části periody a o kus později než ta před ní.
        assertEquals(0f, dotLift(0f, 0))
        assertEquals(1f, dotLift(0.2f, 0), 0.001f)
        assertEquals(0f, dotLift(0.2f, 2))
        assertEquals(1f, dotLift(0.5f, 2), 0.001f)
        // Konec periody je odpočinek všech tří, jinak by trojtečka vlnila bez ustání.
        assertEquals(0f, dotLift(0.8f, 0))
        assertEquals(0f, dotLift(0.8f, 1))
        assertEquals(0f, dotLift(0.8f, 2))
    }

    @Test
    fun okoKoukaAMrkneJenNaKonciPeriody() {
        assertEquals(0f, eyeClosure(0f))
        assertEquals(0f, eyeClosure(0.85f))
        assertEquals(1f, eyeClosure(0.95f), 0.001f)
        assertEquals(0f, eyeClosure(1f), 0.001f)
    }

    @Test
    fun spotrebovaneMiziciMediumJeHnedOznameni() {
        // Spotřebovanou mizející fotku TDLib vymění za `MessageExpiredPhoto` až za chvíli;
        // do té doby nesmí v seznamu stát bublina, která pak skočí na oznámení.
        val secret = photo.copy(selfDestructType = SelfDestructType.Immediately)
        val result = conversationItems(
            ConversationState(messages = listOf(message(3, media = secret, text = "popisek"), message(2, media = photo), message(1))),
            expired = setOf(3L, 2L),
        )
        assertEquals(listOf("s3", "m2", "m1", "day"), result.kinds())
        val notice = assertIs<ServiceMessageItem>(result.first()).message
        assertNull(notice.media)
        assertEquals("", notice.text)
        assertEquals(UiText(Res.string.content_expired_photo), notice.description)
    }

    @Test
    fun nabidkaOznameniPatriJenJemu() {
        val state = ConversationState(
            messages = listOf(message(2, isService = true), message(1, isService = true)),
            noticeMenu = NoticeMenu(messageId = 2L, canDeleteForSelf = true, canDeleteForAll = false),
        )
        val (newer, older) = conversationItems(state).filterIsInstance<ServiceMessageItem>()
        assertEquals(2L, newer.menu?.messageId)
        assertNull(older.menu)
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
    fun odpovedZJinehoChatuNeseCitaciSama() {
        // Citovaná zpráva je v jiném chatu, v historii téhle konverzace ji builder nenajde.
        val external = ExternalReply(
            quote = message(500, text = "z jiného chatu"),
            target = ForwardOrigin.Chat(chatId = 7L, messageId = 500L, name = "Jiný chat"),
        )
        val result = items(
            message(2).copy(externalReply = external),
            message(1),
        ).filterIsInstance<BubbleMessageItem>()
        assertEquals("z jiného chatu", result[0].quoted?.text)
        assertNull(result[1].quoted)
    }

    @Test
    fun veVlaknuSeKorenNecituje() {
        // Komentář odpovídá kořeni (příspěvku) — citace nad každým by jen opakovala příspěvek.
        // Odpověď na jiný komentář citaci nese dál.
        val result = items(
            message(3, replyToMessageId = 2),
            message(2, replyToMessageId = 1),
            message(1),
            state = ConversationState(threadRootId = 1L),
        ).filterIsInstance<BubbleMessageItem>()
        assertEquals(2L, result[0].quoted?.id)
        assertNull(result[1].quoted)
    }

    @Test
    fun komentareJenVKanalu() {
        val stats = PostStats(views = 1200, forwards = 0, comments = 0)
        val post = message(1).copy(postStats = stats)
        assertEquals(stats, items(post, state = ConversationState(info = info(isChannel = true))).frames()[0].postStats)
        // V diskusní skupině má `replyInfo` každá zpráva; komentáře tam nejsou, zhlédnutí ano.
        assertEquals(
            stats.copy(comments = null),
            items(post, state = ConversationState(info = info(isGroup = true))).frames()[0].postStats,
        )
        // Bez zhlédnutí a bez komentářů není co ukázat.
        val reply = message(2).copy(postStats = PostStats(views = 0, forwards = 0, comments = 3))
        assertNull(items(reply, state = ConversationState(info = info(isGroup = true))).frames()[0].postStats)
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
        showAboveText = false,
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
    fun videoKoleckoJeBezBubliny() {
        val note = MessageMedia.VideoNote(
            fileId = 1, thumbnailFileId = null, durationSeconds = 5, sizeBytes = 1000, isViewed = false,
        )
        assertIs<StickerMessageItem>(single(message(1, text = "", media = note)))
        // Citace se bez pozadí ztratí, stejně jako u samolepky.
        assertIs<BubbleMessageItem>(single(message(1, text = "", media = note, replyToMessageId = 99)))
    }

    @Test
    fun preposlanaSamolepkaAEmojiJsouVBubline() {
        // Značka přeposlání je údaj navíc — obsah s údajem navíc je karta, jako fotka s popiskem.
        val origin = ForwardOrigin.User(userId = 5, name = "Jana")
        assertIs<BubbleMessageItem>(single(message(1, text = "", media = sticker, forwardOrigin = origin)))
        assertIs<BubbleMessageItem>(single(message(1, text = "🙂", forwardOrigin = origin)))
    }

    @Test
    fun preposlanaFotkaZustavaNaPlose() {
        // Snímek nese značku v hlavičce vlastní karty, bublinu nepotřebuje.
        val origin = ForwardOrigin.User(userId = 5, name = "Jana")
        assertIs<MediaMessageItem>(single(message(1, text = "", media = photo, forwardOrigin = origin)))
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
    fun zadnyBlokZpravyNezabereCelyRadek() {
        /*
         * Protější strana musí zůstat volná u KAŽDÉHO druhu zprávy, ne jen u textu. Holý snímek
         * ani album nemá bublinu, takže barvu, podle které by se odesílatel poznal, nemá vůbec —
         * fotka přes celý řádek vypadala na obou stranách konverzace stejně.
         */
        val blocks = listOf(
            single(message(1)),
            single(message(2, text = "🙂🙂")),
            single(message(3, text = "", media = sticker)),
            single(message(4, text = "popisek", media = photo)),
            single(message(5, text = "https://example.com", linkPreview = preview())),
            single(message(6, text = "https://example.com", linkPreview = preview(large = false))),
        )

        blocks.forEach { block ->
            val item = assertIs<MessageItem>(block)
            assertTrue(
                item.maxWidthFraction < 1f,
                "${item::class.simpleName} zabere celý řádek (${item.maxWidthFraction})",
            )
        }
    }

    @Test
    fun selhaneOdeslaniJeVidetVzdycky() {
        val failed = items(
            message(3, isOutgoing = true),
            message(2, isOutgoing = true, sendState = SendState.Failed),
        ).frames()
        assertEquals(Delivery.Failed, failed[1].badge)
    }

    @Test
    fun fotkaSeSpoileremSiNesePriznakVModelu() {
        val photoWithSpoiler = photo.copy(hasSpoiler = true)
        val msg = message(1, media = photoWithSpoiler)
        val item = single(msg)
        assertIs<MediaMessageItem>(item)
        val media = item.frame.message.media
        assertIs<MessageMedia.Photo>(media)
        assertTrue(media.hasSpoiler)
    }

    @Test
    fun videoSeSpoileremSiNesePriznakVModelu() {
        val video = MessageMedia.Video(fileId = 1, thumbnailFileId = 2, width = 100, height = 100, durationSeconds = 10, hasSpoiler = true)
        val msg = message(1, media = video)
        val item = single(msg)
        assertIs<MediaMessageItem>(item)
        val media = item.frame.message.media
        assertIs<MessageMedia.Video>(media)
        assertTrue(media.hasSpoiler)
    }

    private companion object {
        /** Poledne jednoho dne v UTC — daleko od půlnoci v každém rozumném časovém pásmu. */
        const val DAY = 86_400 * 20_000 + 43_200
        const val NEXT_DAY = DAY + 86_400
        const val HOUR = 3_600
        const val CHAT_ID = 1000L
    }
}
