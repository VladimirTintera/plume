package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.settings.UnimplementedAppSettings
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ComposedText
import eu.tintera.plume.core.telegram.FormatSpan
import eu.tintera.plume.core.telegram.LinkPreviewOptions
import eu.tintera.plume.core.telegram.MessageSchedule
import eu.tintera.plume.core.telegram.NameMention
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.ReplyQuote
import eu.tintera.plume.core.telegram.SendOptions
import eu.tintera.plume.core.telegram.UnimplementedChatRepository
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationComposerTest {

    /** Zpráva, jak ji psaní poslalo serveru. */
    private data class Sent(val text: String, val replyTo: Long?, val quote: ReplyQuote?, val options: SendOptions)

    private data class SavedDraft(val text: String, val replyTo: Long?)

    private class FakeRepository : UnimplementedConversationRepository() {
        var sendResult: (String) -> Result<ChatMessage> = { text -> Result.success(testMessage(1000, text, outgoing = true)) }
        var editResult: Result<Unit> = Result.success(Unit)
        val sent = mutableListOf<Sent>()
        val typing = mutableListOf<Boolean>()
        val drafts = mutableListOf<SavedDraft>()
        val edits = mutableListOf<Pair<Long, String>>()
        val dice = mutableListOf<String>()

        override suspend fun sendDice(chatId: Long, emoji: String, replyToMessageId: Long?, quote: ReplyQuote?): Result<ChatMessage> {
            dice += emoji
            return Result.success(testMessage(1001, emoji, outgoing = true))
        }

        override suspend fun send(
            chatId: Long,
            text: String,
            replyToMessageId: Long?,
            linkPreview: LinkPreviewOptions?,
            quote: ReplyQuote?,
            options: SendOptions,
            mentions: List<NameMention>,
            formats: List<FormatSpan>,
        ): Result<ChatMessage> {
            sent += Sent(text, replyToMessageId, quote, options)
            return sendResult(text)
        }

        override suspend fun sendTyping(chatId: Long, typing: Boolean): Result<Unit> {
            this.typing += typing
            return Result.success(Unit)
        }

        override suspend fun saveDraft(
            chatId: Long,
            text: String,
            replyToMessageId: Long?,
            linkPreview: LinkPreviewOptions?,
            quote: ReplyQuote?,
            formats: List<FormatSpan>,
        ): Result<Unit> {
            drafts += SavedDraft(text, replyToMessageId)
            return Result.success(Unit)
        }

        override suspend fun editText(
            chatId: Long,
            messageId: Long,
            text: String,
            linkPreview: LinkPreviewOptions?,
            formats: List<FormatSpan>,
        ): Result<Unit> {
            edits += messageId to text
            return editResult
        }

        val mediaEdits = mutableListOf<Triple<Long, OutgoingAttachment, String>>()

        override suspend fun editMedia(
            chatId: Long,
            messageId: Long,
            attachment: OutgoingAttachment,
            caption: String,
            formats: List<FormatSpan>,
        ): Result<Unit> {
            mediaEdits += Triple(messageId, attachment, caption)
            return Result.success(Unit)
        }

        /** Náhled odkazu v textech testů není — server by žádný nenašel. */
        override suspend fun linkPreview(text: String, url: String?) = Result.failure<Nothing>(IllegalStateException("bez náhledu"))
    }

    private class FakeChats : UnimplementedChatRepository() {
        override val diceEmojis: StateFlow<List<String>> = MutableStateFlow(listOf("🎲"))
        override val availableEffectIds: StateFlow<List<Long>> = MutableStateFlow(emptyList())
    }

    private class FakeSettings(discreet: Boolean = false) : UnimplementedAppSettings() {
        override val stripTrackingParameters: StateFlow<Boolean> = MutableStateFlow(false)
        override val discreetReading: StateFlow<Boolean> = MutableStateFlow(discreet)
    }

    private val repository = FakeRepository()
    private var readAfterOwn = 0
    private val usedChats = mutableListOf<Long>()
    private val attachmentSends = mutableListOf<Pair<String, List<OutgoingAttachment>>>()

    private fun TestScope.composer(
        state: MutableStateFlow<ConversationState>,
        settings: FakeSettings = FakeSettings(),
    ): ConversationComposer {
        val scope: CoroutineScope = this
        val suggestions = ConversationSuggestions(
            chatId = TEST_CHAT,
            state = state,
            scope = scope,
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
        return ConversationComposer(
            chatId = TEST_CHAT,
            state = state,
            scope = scope,
            appScope = scope,
            repository = repository,
            chatRepository = FakeChats(),
            settings = settings,
            reportUsed = { usedChats += it },
            suggestions = suggestions,
            readAfterOwnMessage = { readAfterOwn++ },
            sendAttachments = { caption, files, _ -> attachmentSends += caption.text to files },
            logger = testLogger,
        )
    }

    @Test
    fun odeslanyTextLetiDoBublinyAZmiziPoDobehnutiLetu() = runTest {
        val state = testState(testMessage(1))
        val composer = composer(state)

        composer.send(ComposedText("  ahoj  "))
        val flight = assertNotNull(state.value.flight)
        assertEquals("ahoj", flight.text)
        runCurrent()

        assertEquals(listOf(Sent("ahoj", null, null, SendOptions())), repository.sent)
        assertEquals(1, readAfterOwn)
        assertEquals(listOf(TEST_CHAT), usedChats)
        assertTrue(state.value.messages.any { it.id == 1000L }, "rozepsaná zpráva je hned v seznamu")
        advanceTimeBy(FLIGHT_DURATION_MS + 1)
        assertNull(state.value.flight)
    }

    @Test
    fun prazdnyTextNeodejde() = runTest {
        val state = testState()
        val composer = composer(state)

        composer.send(ComposedText("   "))
        advanceUntilIdle()

        assertTrue(repository.sent.isEmpty())
        assertNull(state.value.flight)
    }

    @Test
    fun odpovedSeSpotrebujeHnedPriOdeslani() = runTest {
        val state = testState(testMessage(1), testMessage(2))
        val composer = composer(state)
        composer.replyTo(2)

        composer.send(ComposedText("jo"))
        assertNull(state.value.replyingTo, "druhá zpráva už nesmí odejít se stejnou citací")
        advanceUntilIdle()

        assertEquals(2L, repository.sent.single().replyTo)
    }

    @Test
    fun sPrilohamiOdejdeTextJakoPopisek() = runTest {
        val photo = OutgoingAttachment.Photo(path = "/tmp/a.jpg", width = 100, height = 100)
        val state = MutableStateFlow(ConversationState(attachments = listOf(photo)))
        val composer = composer(state)

        composer.send(ComposedText("popisek"))
        advanceUntilIdle()

        assertEquals(listOf("popisek" to listOf<OutgoingAttachment>(photo)), attachmentSends)
        assertTrue(repository.sent.isEmpty())
    }

    @Test
    fun naplanovanaZpravaNeletiJenSeOhlasi() = runTest {
        val state = testState()
        val composer = composer(state)

        composer.send(ComposedText("ráno"), SendOptions(schedule = MessageSchedule.WhenOnline))
        assertNull(state.value.flight)
        advanceUntilIdle()

        assertEquals(1, repository.sent.size)
        assertNotNull(state.value.notice)
        assertTrue(state.value.messages.isEmpty(), "naplánovaná do konverzace nepatří")
    }

    @Test
    fun neodeslanaZpravaOhlasiChybuAZrusiLet() = runTest {
        repository.sendResult = { Result.failure(IllegalStateException("CHAT_WRITE_FORBIDDEN")) }
        val state = testState()
        val composer = composer(state)

        composer.send(ComposedText("ahoj"))
        advanceUntilIdle()

        assertNotNull(state.value.error)
        assertNull(state.value.flight)
    }

    @Test
    fun samotnaKostkaOdejdeJakoHodNeJakoText() = runTest {
        val state = testState()
        val composer = composer(state)

        composer.send(ComposedText("🎲"))
        advanceUntilIdle()

        assertEquals(listOf("🎲"), repository.dice)
        assertTrue(repository.sent.isEmpty())
        assertNull(state.value.flight, "hod neletí z pole, jako samolepka")
    }

    @Test
    fun psaniSeOhlasiJednouAVymazaniHoUkonci() = runTest {
        val state = testState()
        val composer = composer(state)

        composer.onDraftChanged(ComposedText("a"))
        composer.onDraftChanged(ComposedText("ah"))
        composer.onDraftChanged(ComposedText(""))
        runCurrent()

        assertEquals(listOf(true, false), repository.typing)
    }

    @Test
    fun priDiskretnimCteniSePsaniNehlasi() = runTest {
        val state = testState()
        val composer = composer(state, FakeSettings(discreet = true))

        composer.onDraftChanged(ComposedText("ahoj"))
        runCurrent()

        assertTrue(repository.typing.isEmpty())
    }

    @Test
    fun konceptSeUloziAzPoPauzeAJenPosledni() = runTest {
        val state = testState()
        val composer = composer(state)

        composer.onDraftChanged(ComposedText("a"))
        advanceTimeBy(500)
        composer.onDraftChanged(ComposedText("ah"))
        advanceUntilIdle()

        assertEquals(listOf(SavedDraft("ah", null)), repository.drafts)
    }

    @Test
    fun priOdchoduSeCekajiciKonceptUloziHned() = runTest {
        val state = testState()
        val composer = composer(state)
        composer.onDraftChanged(ComposedText("rozepsáno"))
        runCurrent()

        composer.saveOnLeave()
        runCurrent()

        assertEquals(listOf(SavedDraft("rozepsáno", null)), repository.drafts)
    }

    @Test
    fun upravaOdlozeRozepsanyTextAZruseniHoVrati() = runTest {
        val state = testState(testMessage(5, "stará"))
        val composer = composer(state)
        composer.onDraftChanged(ComposedText("rozepsaná"))

        composer.startEdit(5)
        assertEquals(5L, state.value.editing?.id)
        assertEquals("stará", state.value.inputFill?.text)

        composer.cancelEdit()
        assertNull(state.value.editing)
        assertEquals("rozepsaná", state.value.inputFill?.text)
        assertEquals("rozepsaná", composer.draft.text)
    }

    @Test
    fun upravaBezZmenyNicNeposle() = runTest {
        val state = testState(testMessage(5, "stará"))
        val composer = composer(state)
        composer.startEdit(5)

        composer.send(ComposedText("stará"))
        advanceUntilIdle()

        assertTrue(repository.edits.isEmpty())
        assertNull(state.value.editing)
    }

    @Test
    fun zmenenaUpravaOdejde() = runTest {
        val state = testState(testMessage(5, "stará"))
        val composer = composer(state)
        composer.startEdit(5)

        composer.send(ComposedText("nová"))
        advanceUntilIdle()

        assertEquals(listOf(5L to "nová"), repository.edits)
        assertTrue(repository.sent.isEmpty(), "úprava neodchází jako nová zpráva")
    }

    @Test
    fun nepovedenaUpravaSeVratiDoPoleIsTextem() = runTest {
        repository.editResult = Result.failure(IllegalStateException("MESSAGE_EDIT_TIME_EXPIRED"))
        val state = testState(testMessage(5, "stará"))
        val composer = composer(state)
        composer.startEdit(5)

        composer.send(ComposedText("nová"))
        advanceUntilIdle()

        assertNotNull(state.value.error)
        assertEquals(5L, state.value.editing?.id)
        assertEquals("nová", state.value.inputFill?.text)
    }

    @Test
    fun kdeSePsatNesmiNeniKamOdpovidat() = runTest {
        val state = MutableStateFlow(ConversationState(messages = listOf(testMessage(1)), info = testChatInfo(canJoin = true)))
        val composer = composer(state)

        composer.replyTo(1)

        assertNull(state.value.replyingTo)
    }

    @Test
    fun citaceNeseMistoUryvkuVTextu() = runTest {
        val state = testState(testMessage(1, "dobré ráno všem"))
        val composer = composer(state)

        composer.quoteReply(1, "ráno")

        assertEquals(1L, state.value.replyingTo?.id)
        assertEquals(ReplyQuote("ráno", 6), state.value.replyQuote)
    }

    @Test
    fun uryvekMimoTextJeObycejnaOdpoved() = runTest {
        val state = testState(testMessage(1, "dobré ráno"))
        val composer = composer(state)

        composer.quoteReply(1, "večer")

        assertEquals(1L, state.value.replyingTo?.id)
        assertNull(state.value.replyQuote)
    }

    // --- Výměna přílohy při úpravě ---

    private fun photoMessage(id: Long) = testMessage(id, "popisek").copy(
        media = MessageMedia.Photo(fileId = 1, thumbnailFileId = null, fullFileId = 1, width = 10, height = 10),
    )

    private val novaFotka = OutgoingAttachment.Photo(path = "/a/nova.jpg", width = 10, height = 10)

    @Test
    fun vymenaSeNabidneJenKdyzJiServerDovoli() = runTest {
        val state = testState(photoMessage(5))
        val composer = composer(state)

        composer.startEdit(5)
        assertNull(state.value.editReplace)

        composer.cancelEdit()
        composer.startEdit(5, canEditMedia = true)
        assertEquals(ReplaceMedia.Visual, state.value.editReplace)
    }

    @Test
    fun ulozeniSNovouPrilohouJiVymeniIsPopiskem() = runTest {
        val state = testState(photoMessage(5))
        val composer = composer(state)
        composer.startEdit(5, canEditMedia = true)

        composer.replaceEditMedia(listOf(novaFotka))
        // Popisek beze změny: bez nové přílohy by se nic neposlalo.
        composer.send(ComposedText("popisek"))
        advanceUntilIdle()

        assertEquals(listOf(Triple(5L, novaFotka as OutgoingAttachment, "popisek")), repository.mediaEdits)
        assertNull(state.value.editing)
        assertNull(state.value.editReplacement)
    }

    /** Fotku za soubor server nevymění — výběr se neuloží a ohlásí se. */
    @Test
    fun souborMistoFotkySeNeprijme() = runTest {
        val state = testState(photoMessage(5))
        val composer = composer(state)
        composer.startEdit(5, canEditMedia = true)

        composer.replaceEditMedia(listOf(OutgoingAttachment.Document("/a/doc.pdf")))

        assertNull(state.value.editReplacement)
        assertNotNull(state.value.notice)
    }

    @Test
    fun ponechaniPuvodniPrilohyVratiUpravuPopisku() = runTest {
        val state = testState(photoMessage(5))
        val composer = composer(state)
        composer.startEdit(5, canEditMedia = true)
        composer.replaceEditMedia(listOf(novaFotka))

        composer.keepEditMedia()
        composer.send(ComposedText("popisek"))
        advanceUntilIdle()

        assertTrue(repository.mediaEdits.isEmpty())
    }
}
