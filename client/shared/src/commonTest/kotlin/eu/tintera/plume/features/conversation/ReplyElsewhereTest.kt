package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.settings.UnimplementedAppSettings
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.ComposedText
import eu.tintera.plume.core.telegram.ExternalReplyTarget
import eu.tintera.plume.core.telegram.FormatSpan
import eu.tintera.plume.core.telegram.LinkPreviewOptions
import eu.tintera.plume.core.telegram.NameMention
import eu.tintera.plume.core.telegram.ReplyQuote
import eu.tintera.plume.core.telegram.SendOptions
import eu.tintera.plume.core.telegram.UnimplementedChatRepository
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.core.telegram.testMessage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** „Odpovědět v jiném chatu": odpověď tady na zprávu z jiného chatu. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplyElsewhereTest {

    /** Zapisuje označení odpovědi jinam a odeslání v pořadí, jak přišla. */
    private class Repository : UnimplementedConversationRepository() {
        val log = mutableListOf<String>()

        override suspend fun message(chatId: Long, messageId: Long): Result<ChatMessage> =
            Result.success(testMessage(messageId, "zpráva z $chatId"))

        override fun setExternalReply(chatId: Long, reply: ExternalReplyTarget?) {
            log += "označení ${reply?.let { "${it.fromChatId}/${it.messageId}" }}"
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
            log += "odeslání $text na $replyToMessageId"
            return Result.success(testMessage(1000, text, outgoing = true))
        }

        override suspend fun sendTyping(chatId: Long, typing: Boolean) = Result.success(Unit)

        override suspend fun linkPreview(text: String, url: String?) = Result.failure<Nothing>(IllegalStateException("bez náhledu"))
    }

    private class Chats : UnimplementedChatRepository() {
        override val diceEmojis: StateFlow<List<String>> = MutableStateFlow(emptyList())
        override val availableEffectIds: StateFlow<List<Long>> = MutableStateFlow(emptyList())
        override suspend fun summaries(chatIds: List<Long>): List<ChatSummary> = chatIds.map { id ->
            ChatSummary(
                id = id,
                title = "Jana",
                unreadCount = 0,
                lastMessagePreview = null,
                isPinned = false,
                order = id,
                avatarFileId = null,
                isMuted = false,
                isMarkedAsUnread = false,
                kind = ChatKind.Private,
                lastMessageTimestamp = null,
                presence = null,
                activity = null,
                draft = null,
            )
        }
    }

    private class Settings : UnimplementedAppSettings() {
        override val stripTrackingParameters: StateFlow<Boolean> = MutableStateFlow(false)
        override val discreetReading: StateFlow<Boolean> = MutableStateFlow(false)
    }

    private val repository = Repository()

    private fun TestScope.composer(state: MutableStateFlow<ConversationState>): ConversationComposer {
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
        return ConversationComposer(
            chatId = TEST_CHAT,
            state = state,
            scope = this,
            appScope = this,
            repository = repository,
            chatRepository = Chats(),
            settings = Settings(),
            reportUsed = {},
            suggestions = suggestions,
            readAfterOwnMessage = {},
            sendAttachments = { _, _, _ -> },
            logger = testLogger,
        )
    }

    @Test
    fun odpovedJinamPrevezmeZpravuZJinehoChatu() = runTest {
        val state = testState(testMessage(1))
        val composer = composer(state)

        composer.replyElsewhere(fromChatId = 7, messageId = 55)
        runCurrent()

        assertEquals(55L, state.value.replyingTo?.id)
        assertEquals(ReplyElsewhere(55, "Jana"), state.value.replyElsewhere)
        assertEquals(listOf("označení 7/55"), repository.log)

        // Pruh se jménem chatu si hlídá, kdy odpověď zmizí; bez toho by test na konci čekal.
        composer.clearReply()
        runCurrent()
        assertNull(state.value.replyElsewhere)
    }

    @Test
    fun oznaceniVydrziDoOdeslani() = runTest {
        val state = testState(testMessage(1))
        val composer = composer(state)
        composer.replyElsewhere(fromChatId = 7, messageId = 55)
        runCurrent()

        composer.send(ComposedText("díky"))
        runCurrent()

        // Pole se vyprázdní hned, odeslání jde až potom — označení ho musí přežít.
        assertEquals(listOf("označení 7/55", "odeslání díky na 55"), repository.log)
        assertNull(state.value.replyingTo)
        assertNull(state.value.replyElsewhere)
    }

    @Test
    fun odpovedNaZpravuTadyOznaceniZrusi() = runTest {
        val state = testState(testMessage(1))
        val composer = composer(state)
        composer.replyElsewhere(fromChatId = 7, messageId = 55)
        runCurrent()

        composer.replyTo(1)
        runCurrent()

        assertEquals(listOf("označení 7/55", "označení null"), repository.log)
        assertEquals(1L, state.value.replyingTo?.id)
        assertNull(state.value.replyElsewhere)
    }
}
