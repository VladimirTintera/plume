package eu.tintera.plume.features.forward

import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.FakeChatRepository
import eu.tintera.plume.core.telegram.ForwardCapabilities
import eu.tintera.plume.core.telegram.ForwardOptions
import eu.tintera.plume.core.telegram.FormatSpan
import eu.tintera.plume.core.telegram.LinkPreviewOptions
import eu.tintera.plume.core.telegram.NameMention
import eu.tintera.plume.core.telegram.ReplyQuote
import eu.tintera.plume.core.telegram.SendOptions
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.core.telegram.testMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.story_forward_failed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** Příběh poslaný týmž výběrem jako přeposlání zpráv. */
@OptIn(ExperimentalCoroutinesApi::class)
class ForwardStoryTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Zapisuje, co kam odešlo; do [failing] příběh neodejde. */
    private class Conversations(val failing: Set<Long> = emptySet()) : UnimplementedConversationRepository() {
        val log = mutableListOf<String>()
        val silent = mutableListOf<Boolean>()
        var capabilitiesAsked = false

        override suspend fun forwardCapabilities(chatId: Long, messageIds: List<Long>): ForwardCapabilities {
            capabilitiesAsked = true
            return ForwardCapabilities(canHideSender = true)
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
            log += "$chatId:$text"
            silent += options.silent
            return Result.success(testMessage(1, text, outgoing = true))
        }

        override suspend fun sendStory(toChatId: Long, posterChatId: Long, storyId: Int, options: SendOptions): Result<Unit> {
            if (toChatId in failing) return Result.failure(IllegalStateException("CHAT_SEND_PLAIN_FORBIDDEN"))
            log += "$toChatId:příběh $posterChatId/$storyId"
            silent += options.silent
            return Result.success(Unit)
        }

        override suspend fun forward(fromChatId: Long, messageIds: List<Long>, toChatId: Long, options: ForwardOptions): Result<Unit> {
            log += "$toChatId:zprávy"
            return Result.success(Unit)
        }
    }

    private class Chats : FakeChatRepository() {
        override suspend fun summaries(chatIds: List<Long>): List<ChatSummary> = chatIds.map { chat(it, "Chat $it") }
    }

    private fun viewModel(conversations: Conversations) = ForwardViewModel(
        fromChatId = 7,
        messageIds = emptyList(),
        storyId = 42,
        repository = conversations,
        chatRepository = Chats(),
        reportUsed = {},
    )

    @Test
    fun pribehOdejdeDoKazdehoCilePoKomentari() = runTest(dispatcher) {
        val conversations = Conversations()
        val vm = viewModel(conversations)
        listOf(2L, 3L).forEach(vm::toggle)
        vm.setComment("koukni")
        vm.setSilent(true)

        var opened: Long? = null
        vm.send { opened = it }
        advanceUntilIdle()

        assertEquals(listOf("2:koukni", "2:příběh 7/42", "3:koukni", "3:příběh 7/42"), conversations.log)
        assertEquals(List(4) { true }, conversations.silent)
        assertEquals(2L, opened)
    }

    @Test
    fun uPribehuSeKopieNezjistuje() = runTest(dispatcher) {
        val conversations = Conversations()
        val vm = viewModel(conversations)
        advanceUntilIdle()

        assertFalse(conversations.capabilitiesAsked)
        assertFalse(vm.state.first().capabilities.canHideSender)
    }

    @Test
    fun neodeslanyPribehMaVlastniHlasku() = runTest(dispatcher) {
        val vm = viewModel(Conversations(failing = setOf(2)))
        vm.toggle(2)

        vm.send {}
        advanceUntilIdle()

        assertEquals(UiText(Res.string.story_forward_failed), vm.state.first { !it.sending && it.error != null }.error)
    }
}

private fun chat(id: Long, title: String) = ChatSummary(
    id = id,
    title = title,
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
