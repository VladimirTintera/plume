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
import kotlin.test.assertTrue

/** Komentář, „bez zvuku" a časté cíle u přeposlání. */
@OptIn(ExperimentalCoroutinesApi::class)
class ForwardCommentTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Zapisuje, co kam odešlo a v jakém pořadí; [failingComment] / [failingForward] selžou. */
    private class Conversations(
        val failingComment: MutableSet<Long> = mutableSetOf(),
        val failingForward: MutableSet<Long> = mutableSetOf(),
    ) : UnimplementedConversationRepository() {
        val log = mutableListOf<String>()
        val silent = mutableListOf<Boolean>()

        override suspend fun forwardCapabilities(chatId: Long, messageIds: List<Long>) = ForwardCapabilities()

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
            if (chatId in failingComment) return Result.failure(IllegalStateException("CHAT_WRITE_FORBIDDEN"))
            log += "$chatId:$text"
            silent += options.silent
            return Result.success(testMessage(1, text, outgoing = true))
        }

        override suspend fun forward(fromChatId: Long, messageIds: List<Long>, toChatId: Long, options: ForwardOptions): Result<Unit> {
            if (toChatId in failingForward) return Result.failure(IllegalStateException("CHAT_WRITE_FORBIDDEN"))
            log += "$toChatId:zprávy"
            silent += options.silent
            return Result.success(Unit)
        }
    }

    private class Chats : FakeChatRepository() {
        override suspend fun summaries(chatIds: List<Long>): List<ChatSummary> = chatIds.map { chat(it, "Chat $it") }
    }

    private fun viewModel(conversations: Conversations, chats: Chats = Chats()) = ForwardViewModel(
        fromChatId = 1,
        messageIds = listOf(10),
        repository = conversations,
        chatRepository = chats,
        reportUsed = {},
    )

    @Test
    fun komentarJdeDoKazdehoCilePredZpravami() = runTest(dispatcher) {
        val conversations = Conversations()
        val vm = viewModel(conversations)
        listOf(2L, 3L).forEach(vm::toggle)
        vm.setComment("  koukni na tohle  ")

        vm.send {}
        advanceUntilIdle()

        assertEquals(listOf("2:koukni na tohle", "2:zprávy", "3:koukni na tohle", "3:zprávy"), conversations.log)
    }

    @Test
    fun bezKomentareJenZpravy() = runTest(dispatcher) {
        val conversations = Conversations()
        val vm = viewModel(conversations)
        vm.toggle(2)
        vm.setComment("   ")

        vm.send {}
        advanceUntilIdle()

        assertEquals(listOf("2:zprávy"), conversations.log)
    }

    @Test
    fun bezZvukuPlatiProKomentarIZpravy() = runTest(dispatcher) {
        val conversations = Conversations()
        val vm = viewModel(conversations)
        vm.toggle(2)
        vm.setComment("ahoj")
        vm.setSilent(true)

        vm.send {}
        advanceUntilIdle()

        assertEquals(listOf(true, true), conversations.silent)
    }

    @Test
    fun neodeslanyKomentarZastaviZpravyDoTohoCile() = runTest(dispatcher) {
        val conversations = Conversations(failingComment = mutableSetOf(3))
        val vm = viewModel(conversations)
        listOf(2L, 3L).forEach(vm::toggle)
        vm.setComment("ahoj")

        vm.send {}
        advanceUntilIdle()

        assertEquals(listOf("2:ahoj", "2:zprávy"), conversations.log)
        assertEquals(setOf(3L), vm.state.first { !it.sending && it.error != null }.selected)
    }

    @Test
    fun opakovaniPoSelhaniZpravNeposleKomentarPodruhe() = runTest(dispatcher) {
        val conversations = Conversations(failingForward = mutableSetOf(2))
        val vm = viewModel(conversations)
        vm.toggle(2)
        vm.setComment("ahoj")

        vm.send {}
        advanceUntilIdle()
        conversations.failingForward.clear()
        vm.send {}
        advanceUntilIdle()

        assertEquals(listOf("2:ahoj", "2:zprávy"), conversations.log)
    }

    @Test
    fun casteCileJenBezDotazu() = runTest(dispatcher) {
        val chats = Chats().apply { topForwardChats = listOf(chat(5, "Jana")) }
        val vm = viewModel(Conversations(), chats)
        val frequent = vm.state.first { it.frequent.isNotEmpty() }.frequent
        assertEquals(listOf(5L), frequent.map { it.id })

        vm.onQueryChange("ja")
        assertTrue(vm.state.first { it.query == "ja" }.frequent.isEmpty())
    }

    @Test
    fun blokyVyberuSCastymiCili() {
        val chats = listOf(chat(5, "Jana"), chat(6, "Petr"))
        assertEquals(listOf("chats"), forwardSections(emptyList(), chats, "Časté", "Chaty").map { it.key })
        val sections = forwardSections(listOf(chat(5, "Jana")), chats, "Časté", "Chaty")
        assertEquals(listOf("frequent" to "Časté", "chats" to "Chaty"), sections.map { it.key to it.title })
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
