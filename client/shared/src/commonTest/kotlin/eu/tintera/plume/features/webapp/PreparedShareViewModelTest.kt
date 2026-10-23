package eu.tintera.plume.features.webapp

import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.InlineResult
import eu.tintera.plume.core.telegram.InlineRowKind
import eu.tintera.plume.core.telegram.PreparedMessage
import eu.tintera.plume.core.telegram.ReplyQuote
import eu.tintera.plume.core.telegram.SwitchInlineTarget
import eu.tintera.plume.core.telegram.UnimplementedChatRepository
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.webapp_share_failed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import kotlin.test.assertTrue

/** Sdílení zprávy připravené botem (`shareMessage`): kam se nabídne a kdy se to povedlo. */
class PreparedShareViewModelTest {

    private val main = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(main)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val targets = SwitchInlineTarget.Chosen(users = true, bots = false, groups = true, channels = false)
    private val message = PreparedMessage(
        queryId = 77,
        result = InlineResult.Row(id = "res", kind = InlineRowKind.Article, title = "Výsledek", description = "", thumbnailFileId = null),
        targets = targets,
    )

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

    private val jana = chat(1, "Jana Nováková")
    private val petr = chat(2, "Petr")

    private inner class Chats : UnimplementedChatRepository() {
        var askedFor: SwitchInlineTarget.Chosen? = null
        override val savedMessagesChatId: StateFlow<Long?> = MutableStateFlow(9L)
        override suspend fun inlineQueryTargets(target: SwitchInlineTarget.Chosen): List<ChatSummary> {
            askedFor = target
            return listOf(jana, petr)
        }
    }

    private class Conversations(private val failing: Set<Long> = emptySet()) : UnimplementedConversationRepository() {
        val sent = mutableListOf<Triple<Long, Long, String>>()
        override suspend fun sendInlineResult(
            chatId: Long,
            queryId: Long,
            resultId: String,
            replyToMessageId: Long?,
            quote: ReplyQuote?,
        ): Result<ChatMessage> {
            sent += Triple(chatId, queryId, resultId)
            if (chatId in failing) return Result.failure(IllegalStateException("odmítnuto"))
            return Result.success(
                ChatMessage(
                    id = 1, isOutgoing = true, senderName = null, senderAvatarFileId = null, senderId = 9, text = "",
                    entities = emptyList(), media = null, linkPreview = null, reactions = emptyList(), sendState = null,
                    replyToMessageId = null, isService = false, timestamp = 1, isEdited = false,
                ),
            )
        }
    }

    @Test
    fun nabidneChatyPodleDruhuOdBota() = runTest {
        val chats = Chats()
        val viewModel = PreparedShareViewModel(message, Conversations(), chats)
        advanceUntilIdle()

        assertEquals(targets, chats.askedFor)
        assertEquals(listOf(jana, petr), viewModel.state.value.shown)
    }

    @Test
    fun hledaBezDiakritiky() = runTest {
        val viewModel = PreparedShareViewModel(message, Conversations(), Chats())
        advanceUntilIdle()

        viewModel.onQueryChange("novakova")
        assertEquals(listOf(jana), viewModel.state.value.shown)
    }

    @Test
    fun posleDoVsechVybranychJakoVysledekInlineDotazu() = runTest {
        val conversations = Conversations()
        val viewModel = PreparedShareViewModel(message, conversations, Chats())
        advanceUntilIdle()
        var done = false

        viewModel.toggle(1)
        viewModel.toggle(2)
        viewModel.send { done = true }
        advanceUntilIdle()

        assertEquals(listOf(Triple(1L, 77L, "res"), Triple(2L, 77L, "res")), conversations.sent)
        assertTrue(done)
    }

    /** Stačí jeden cíl, kam zpráva odešla — tak to hlásí i oficiální klienti. */
    @Test
    fun castecneSelhaniJeUspech() = runTest {
        val viewModel = PreparedShareViewModel(message, Conversations(failing = setOf(1)), Chats())
        advanceUntilIdle()
        var done = false

        viewModel.toggle(1)
        viewModel.toggle(2)
        viewModel.send { done = true }
        advanceUntilIdle()

        assertTrue(done)
    }

    /** Nikam neodešla: výběr zůstane s hláškou, mini aplikace se zatím nic nedozví. */
    @Test
    fun uplneSelhaniNechaVyberOtevreny() = runTest {
        val viewModel = PreparedShareViewModel(message, Conversations(failing = setOf(1)), Chats())
        advanceUntilIdle()
        var done = false

        viewModel.toggle(1)
        viewModel.send { done = true }
        advanceUntilIdle()

        assertFalse(done)
        assertEquals(UiText(Res.string.webapp_share_failed), viewModel.state.value.error)
        assertFalse(viewModel.state.value.sending)
    }

    @Test
    fun bezVyberuSeNicNeposle() = runTest {
        val conversations = Conversations()
        val viewModel = PreparedShareViewModel(message, conversations, Chats())
        advanceUntilIdle()

        viewModel.send { }
        advanceUntilIdle()

        assertEquals(emptyList(), conversations.sent)
    }
}
