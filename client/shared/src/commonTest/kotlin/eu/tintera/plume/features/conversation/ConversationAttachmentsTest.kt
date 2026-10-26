package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.settings.ExpressionMode
import eu.tintera.plume.core.settings.UnimplementedAppSettings
import eu.tintera.plume.core.share.SharedContent
import eu.tintera.plume.core.share.ShareRequests
import eu.tintera.plume.core.telegram.ActiveLiveLocation
import eu.tintera.plume.core.telegram.AttachmentMode
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ComposedText
import eu.tintera.plume.core.telegram.ContentAccess
import eu.tintera.plume.core.telegram.FormatSpan
import eu.tintera.plume.core.telegram.LinkPreviewOptions
import eu.tintera.plume.core.telegram.LiveFix
import eu.tintera.plume.core.telegram.LiveLocationRepository
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.MessageSchedule
import eu.tintera.plume.core.telegram.NameMention
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.Place
import eu.tintera.plume.core.telegram.RecentFile
import eu.tintera.plume.core.telegram.ReplyQuote
import eu.tintera.plume.core.telegram.SendOptions
import eu.tintera.plume.core.telegram.UnimplementedChatRepository
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.core.telegram.testMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationAttachmentsTest {

    private data class SentAttachments(val caption: String, val count: Int, val replyTo: Long?, val options: SendOptions)

    private class FakeRepository : UnimplementedConversationRepository() {
        var attachmentsResult: Result<List<ChatMessage>> = Result.success(listOf(testMessage(900, outgoing = true)))
        val sentAttachments = mutableListOf<SentAttachments>()
        val locations = mutableListOf<Pair<Place, Long?>>()
        val contacts = mutableListOf<Long>()
        val drafts = mutableListOf<String>()
        val cards = mutableMapOf<Long, MessageMedia.Contact>()

        override suspend fun sendAttachments(
            chatId: Long,
            attachments: List<OutgoingAttachment>,
            caption: String,
            replyToMessageId: Long?,
            quote: ReplyQuote?,
            options: SendOptions,
            mentions: List<NameMention>,
            formats: List<FormatSpan>,
        ): Result<List<ChatMessage>> {
            sentAttachments += SentAttachments(caption, attachments.size, replyToMessageId, options)
            return attachmentsResult
        }

        override suspend fun sendLocation(chatId: Long, place: Place, replyToMessageId: Long?, quote: ReplyQuote?): Result<ChatMessage> {
            locations += place to replyToMessageId
            return Result.success(testMessage(901, outgoing = true))
        }

        override suspend fun contactCard(userId: Long): Result<MessageMedia.Contact> =
            cards[userId]?.let { Result.success(it) } ?: Result.failure(IllegalStateException("USER_ID_INVALID"))

        override suspend fun sendContact(chatId: Long, userId: Long, replyToMessageId: Long?, quote: ReplyQuote?): Result<ChatMessage> {
            contacts += userId
            return Result.success(testMessage(902, outgoing = true))
        }

        override suspend fun saveDraft(
            chatId: Long,
            text: String,
            replyToMessageId: Long?,
            linkPreview: LinkPreviewOptions?,
            quote: ReplyQuote?,
            formats: List<FormatSpan>,
        ): Result<Unit> {
            drafts += text
            return Result.success(Unit)
        }

        var recent: List<RecentFile> = emptyList()
        val recentQueries = mutableListOf<String>()
        val sentFiles = mutableListOf<Pair<List<Int>, Long?>>()

        override suspend fun recentFiles(query: String): Result<List<RecentFile>> {
            recentQueries += query
            return Result.success(recent.filter { query.isEmpty() || query in it.name })
        }

        override suspend fun sendFiles(
            chatId: Long,
            fileIds: List<Int>,
            replyToMessageId: Long?,
            quote: ReplyQuote?,
            options: SendOptions,
        ): Result<List<ChatMessage>> {
            sentFiles += fileIds to replyToMessageId
            return Result.success(listOf(testMessage(903, outgoing = true)))
        }

        override suspend fun linkPreview(text: String, url: String?) = Result.failure<Nothing>(IllegalStateException("bez náhledu"))
        override suspend fun sendTyping(chatId: Long, typing: Boolean) = Result.success(Unit)
    }

    private class FakeChats : UnimplementedChatRepository() {
        override val diceEmojis: StateFlow<List<String>> = MutableStateFlow(emptyList())
        override val availableEffectIds: StateFlow<List<Long>> = MutableStateFlow(emptyList())
    }

    private class FakeSettings : UnimplementedAppSettings() {
        override val stripTrackingParameters: StateFlow<Boolean> = MutableStateFlow(false)
        override val discreetReading: StateFlow<Boolean> = MutableStateFlow(false)
        override val expressionMode: StateFlow<ExpressionMode> = MutableStateFlow(ExpressionMode.Emoji)
    }

    private class NoLiveLocations : LiveLocationRepository {
        override val active: StateFlow<List<ActiveLiveLocation>> = MutableStateFlow(emptyList())
        override val viewed: Flow<Unit> = emptyFlow()
        override suspend fun update(fix: LiveFix) = Unit
        override suspend fun setProximityAlert(chatId: Long, messageId: Long, radiusMeters: Int) = Result.success(Unit)
        override suspend fun stop(chatId: Long, messageId: Long) = Result.success(Unit)
        override suspend fun stopAll() = Unit
    }

    private val repository = FakeRepository()
    private val shares = ShareRequests()
    private val usedChats = mutableListOf<Long>()

    /** Přílohy i psaní, ke kterému patří (popisek je rozepsaný text, odpověď sdílejí). */
    private class Parts(val attachments: ConversationAttachments, val composer: ConversationComposer)

    private fun TestScope.parts(state: MutableStateFlow<ConversationState>): Parts {
        val suggestions = ConversationSuggestions(
            chatId = TEST_CHAT,
            state = state,
            scope = this,
            repository = repository,
            media = object : MediaSender {
                override fun sendMedia(
                    failureMessage: org.jetbrains.compose.resources.StringResource,
                    scheduled: Boolean,
                    send: suspend (replyTo: Long?, quote: ReplyQuote?) -> Result<ChatMessage>,
                ) = error("napovídání v testu nic neposílá")
            },
            composedDraft = { ComposedText("") },
            onDraftChanged = {},
            openBotStart = {},
            requestWebApp = {},
            logger = testLogger,
        )
        lateinit var attachments: ConversationAttachments
        val composer = ConversationComposer(
            chatId = TEST_CHAT,
            state = state,
            scope = this,
            appScope = this,
            repository = repository,
            chatRepository = FakeChats(),
            settings = FakeSettings(),
            reportUsed = { usedChats += it },
            suggestions = suggestions,
            readAfterOwnMessage = {},
            sendAttachments = { caption, files, options -> attachments.sendAttachments(caption, files, options) },
            logger = testLogger,
        )
        attachments = ConversationAttachments(
            chatId = TEST_CHAT,
            state = state,
            scope = this,
            appScope = this,
            repository = repository,
            chatRepository = FakeChats(),
            settings = FakeSettings(),
            shareRequests = shares,
            reportUsed = { usedChats += it },
            liveLocations = NoLiveLocations(),
            photoMarkup = null,
            composer = composer,
            suggestions = suggestions,
            logger = testLogger,
        )
        return Parts(attachments, composer)
    }

    private fun photo(name: String, mode: AttachmentMode = AttachmentMode.Standard) =
        OutgoingAttachment.Photo(path = "/tmp/$name.jpg", width = 100, height = 100, mode = mode)

    private fun stateIn(info: eu.tintera.plume.core.telegram.ChatInfo, vararg messages: ChatMessage) =
        MutableStateFlow(ConversationState(messages = messages.sortedByDescending { it.id }, info = info))

    private fun recent(id: Int, name: String = "soubor$id.pdf") = RecentFile(fileId = id, name = name, size = 1_000L, sentAt = 1_700_000_000)

    @Test
    fun posledniSouboryOdejdouVPoradiTuknutiSOdpovedi() = runTest {
        repository.recent = listOf(recent(1), recent(2), recent(3))
        val reply = testMessage(50)
        val state = stateIn(testChatInfo(), reply)
        state.value = state.value.copy(replyingTo = reply)
        val parts = parts(state)

        parts.attachments.openRecentFiles()
        advanceUntilIdle()
        assertEquals(3, state.value.recentFiles?.files?.size)
        parts.attachments.toggleRecentFile(3)
        parts.attachments.toggleRecentFile(1)
        parts.attachments.sendRecentFiles()
        advanceUntilIdle()

        assertEquals(listOf<Pair<List<Int>, Long?>>(listOf(3, 1) to 50L), repository.sentFiles, "album v pořadí ťuknutí, jako odpověď")
        assertNull(state.value.recentFiles)
        assertNull(state.value.replyingTo, "odpověď se odesláním spotřebuje")
        assertEquals(listOf(TEST_CHAT), usedChats)
    }

    @Test
    fun posledniSouboryNejvysDeset() = runTest {
        repository.recent = (1..12).map { recent(it) }
        val state = stateIn(testChatInfo())
        val parts = parts(state)
        parts.attachments.openRecentFiles()
        advanceUntilIdle()

        (1..12).forEach { parts.attachments.toggleRecentFile(it) }
        assertEquals((1..MAX_RECENT_FILES).toList(), state.value.recentFiles?.selected)
        parts.attachments.toggleRecentFile(2)
        assertEquals(MAX_RECENT_FILES - 1, state.value.recentFiles?.selected?.size, "odznačit jde i v plném albu")
    }

    @Test
    fun hledaniPoslednichSouboruPockaNaDopsani() = runTest {
        repository.recent = listOf(recent(1, "faktura.pdf"), recent(2, "smlouva.docx"))
        val state = stateIn(testChatInfo())
        val parts = parts(state)
        parts.attachments.openRecentFiles()
        advanceUntilIdle()
        parts.attachments.toggleRecentFile(1)

        parts.attachments.searchRecentFiles("sm")
        parts.attachments.searchRecentFiles("smlouva")
        advanceUntilIdle()

        assertEquals(listOf("", "smlouva"), repository.recentQueries)
        assertEquals(listOf(2), state.value.recentFiles?.files?.map { it.fileId })
        assertEquals(listOf(1), state.value.recentFiles?.selected, "výběr hledání přežije")
    }

    @Test
    fun fotkyTamKdeNejsouDovoleneSeNepridajiAOhlasiSe() = runTest {
        val state = stateIn(testChatInfo().copy(content = ContentAccess.All.copy(photos = false)))
        val parts = parts(state)

        parts.attachments.addAttachments(listOf(photo("a")))

        assertTrue(state.value.attachments.isEmpty())
        assertNotNull(state.value.notice)
    }

    @Test
    fun nadStropPrilohSeNepridaAOhlasiSe() = runTest {
        val state = stateIn(testChatInfo())
        val parts = parts(state)

        parts.attachments.addAttachments((1..MAX_ATTACHMENTS + 2).map { photo("p$it") })

        assertEquals(MAX_ATTACHMENTS, state.value.attachments.size)
        assertNotNull(state.value.notice)
    }

    @Test
    fun mizeniSeZAlbaVytratiViditelne() = runTest {
        val state = stateIn(testChatInfo())
        val parts = parts(state)
        parts.attachments.addAttachments(listOf(photo("a", AttachmentMode.ViewOnce)))
        assertIs<AttachmentMode.ViewOnce>(state.value.attachments.single().mode)

        parts.attachments.addAttachments(listOf(photo("b")))

        assertTrue(state.value.attachments.none { it.mode is AttachmentMode.ViewOnce }, "v albu mizení není dovolené")
        assertNotNull(state.value.notice)
    }

    @Test
    fun mizeniVeSkupineNejdeNastavit() = runTest {
        val state = stateIn(testChatInfo(isGroup = true))
        val parts = parts(state)
        parts.attachments.addAttachments(listOf(photo("a")))

        parts.attachments.setAttachmentMode(0, AttachmentMode.ViewOnce)

        assertEquals(AttachmentMode.Standard, state.value.attachments.single().mode)
    }

    @Test
    fun spoilerSePrepinaTamIZpet() = runTest {
        val state = stateIn(testChatInfo())
        val parts = parts(state)
        parts.attachments.addAttachments(listOf(photo("a")))

        parts.attachments.toggleAttachmentSpoiler(0)
        assertEquals(AttachmentMode.Spoiler, state.value.attachments.single().mode)
        parts.attachments.toggleAttachmentSpoiler(0)
        assertEquals(AttachmentMode.Standard, state.value.attachments.single().mode)
    }

    @Test
    fun odebranaPrilohaZmiziJenTa() = runTest {
        val state = stateIn(testChatInfo())
        val parts = parts(state)
        parts.attachments.addAttachments(listOf(photo("a"), photo("b"), photo("c")))

        parts.attachments.removeAttachment(1)

        assertEquals(listOf("/tmp/a.jpg", "/tmp/c.jpg"), state.value.attachments.map { it.path })
    }

    @Test
    fun odeslaniPrilohSPopiskemVyprazdniPoleIOdpoved() = runTest {
        val state = stateIn(testChatInfo(), testMessage(1))
        val parts = parts(state)
        parts.attachments.addAttachments(listOf(photo("a"), photo("b")))
        parts.composer.onDraftChanged(ComposedText("popisek"))
        parts.composer.replyTo(1)

        parts.composer.send(ComposedText("popisek"))
        assertTrue(state.value.attachments.isEmpty())
        assertNull(state.value.replyingTo)
        assertEquals("", parts.composer.draft.text)
        advanceUntilIdle()

        assertEquals(listOf(SentAttachments("popisek", 2, replyTo = 1, SendOptions())), repository.sentAttachments)
        assertTrue(state.value.messages.any { it.id == 900L }, "odeslané přílohy jsou hned v seznamu")
        assertEquals(listOf(TEST_CHAT), usedChats)
        assertEquals("", repository.drafts.last(), "koncept se smaže")
    }

    @Test
    fun neodeslanePrilohySeVratiNadPole() = runTest {
        repository.attachmentsResult = Result.failure(IllegalStateException("PHOTO_INVALID_DIMENSIONS"))
        val state = stateIn(testChatInfo())
        val parts = parts(state)
        parts.attachments.addAttachments(listOf(photo("a")))

        parts.composer.send(ComposedText(""))
        advanceUntilIdle()

        assertNotNull(state.value.error)
        assertEquals(listOf("/tmp/a.jpg"), state.value.attachments.map { it.path })
    }

    @Test
    fun naplanovanePrilohySeJenOhlasi() = runTest {
        val state = stateIn(testChatInfo())
        val parts = parts(state)
        parts.attachments.addAttachments(listOf(photo("a")))

        parts.composer.send(ComposedText(""), SendOptions(schedule = MessageSchedule.WhenOnline))
        advanceUntilIdle()

        assertNotNull(state.value.notice)
        assertTrue(state.value.messages.isEmpty())
    }

    @Test
    fun polohaOdejdeSOdpovedi() = runTest {
        val state = stateIn(testChatInfo(), testMessage(1))
        val parts = parts(state)
        parts.composer.replyTo(1)
        val place = Place(title = "Doma", address = "Praha", latitude = 50.0, longitude = 14.4)

        parts.attachments.sendLocation(place)
        advanceUntilIdle()

        assertEquals(listOf<Pair<Place, Long?>>(place to 1L), repository.locations)
        assertNull(state.value.replyingTo)
    }

    @Test
    fun kdeSePsatNesmiPolohaNeodejde() = runTest {
        val state = stateIn(testChatInfo(canJoin = true))
        val parts = parts(state)

        parts.attachments.sendLocation(Place(title = "Doma", address = "Praha", latitude = 50.0, longitude = 14.4))
        advanceUntilIdle()

        assertTrue(repository.locations.isEmpty())
    }

    @Test
    fun zivaPolohaBezZjistenePolohySeOhlasi() = runTest {
        val state = stateIn(testChatInfo())
        val parts = parts(state)

        parts.attachments.shareLiveLocation(point = null, livePeriod = 900)

        assertNotNull(state.value.notice)
    }

    @Test
    fun kontaktBezCislaSeNeposleAOhlasiSe() = runTest {
        repository.cards[5] = MessageMedia.Contact(name = "Eva", phone = "", userId = 5)
        val state = stateIn(testChatInfo())
        val parts = parts(state)

        parts.attachments.pickContact(5)
        advanceUntilIdle()

        assertNull(state.value.pendingContact)
        assertNotNull(state.value.notice)
    }

    @Test
    fun kontaktSCislemSePotvrdiAOdejde() = runTest {
        repository.cards[5] = MessageMedia.Contact(name = "Eva", phone = "+420 123", userId = 5)
        val state = stateIn(testChatInfo())
        val parts = parts(state)

        parts.attachments.pickContact(5)
        advanceUntilIdle()
        assertEquals(5L, state.value.pendingContact?.userId)

        parts.attachments.confirmContact()
        advanceUntilIdle()
        assertEquals(listOf(5L), repository.contacts)
        assertNull(state.value.pendingContact)
    }

    @Test
    fun sdilenyTextSePripojiZaRozepsanyASdileniSeVyridi() = runTest {
        val state = stateIn(testChatInfo())
        val parts = parts(state)
        parts.composer.onDraftChanged(ComposedText("koncept"))
        val id = shares.start("https://example.com", hasFiles = false)
        val shared = SharedContent(id = id, text = "https://example.com", attachments = emptyList())

        parts.attachments.receiveShared(shared)

        assertEquals("koncept\nhttps://example.com", state.value.inputFill?.text)
        assertEquals("koncept\nhttps://example.com", parts.composer.draft.text)
        assertNull(shares.incoming.value, "sdílení je vyřízené")
    }
}
