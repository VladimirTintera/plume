package eu.tintera.plume.features.forward

import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.FakeChatRepository
import eu.tintera.plume.core.telegram.ForwardCapabilities
import eu.tintera.plume.core.telegram.ForwardOptions
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.forward_failed
import eu.tintera.plume.resources.forward_failed_partial
import eu.tintera.plume.resources.forward_failed_partial_more
import eu.tintera.plume.resources.saved_messages
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
import kotlin.test.assertNull

/**
 * Přeposlání do víc chatů, když část selže: opakované „Odeslat" nesmí poslat zprávu podruhé tam,
 * kam už dorazila.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ForwardRetryTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Přeposílá do všech chatů kromě [failing]; [sent] zapisuje, kam co odešlo. */
    private class Conversations(val failing: MutableSet<Long>) : UnimplementedConversationRepository() {
        val sent = mutableListOf<Long>()
        override suspend fun forwardCapabilities(chatId: Long, messageIds: List<Long>) = ForwardCapabilities()
        override suspend fun forward(fromChatId: Long, messageIds: List<Long>, toChatId: Long, options: ForwardOptions): Result<Unit> {
            if (toChatId in failing) return Result.failure(IllegalStateException("CHAT_WRITE_FORBIDDEN"))
            sent += toChatId
            return Result.success(Unit)
        }
    }

    private class Chats : FakeChatRepository() {
        override suspend fun summaries(chatIds: List<Long>): List<ChatSummary> = chatIds.map { chat(it, "Chat $it") }
    }

    private fun viewModel(conversations: Conversations, reported: MutableList<Long> = mutableListOf()) = ForwardViewModel(
        fromChatId = 1,
        messageIds = listOf(10),
        repository = conversations,
        chatRepository = Chats(),
        reportUsed = { reported += it },
    )

    @Test
    fun poCastecnemSelhaniZustanouVybraneJenNedorucene() = runTest(dispatcher) {
        val conversations = Conversations(failing = mutableSetOf(3, 4))
        val reported = mutableListOf<Long>()
        val vm = viewModel(conversations, reported)
        listOf(2L, 3L, 4L, 5L).forEach(vm::toggle)
        var opened: Long? = null

        vm.send { opened = it }
        advanceUntilIdle()

        val state = vm.state.first { !it.sending && it.error != null }
        assertNull(opened, "Po selhání se chat neotevírá — uživatel má vidět, co neodešlo")
        assertEquals(setOf(3L, 4L), state.selected)
        assertEquals(UiText(Res.plurals.forward_failed_partial_more, 1, UiText.Raw("Chat 3"), 1), state.error)
        assertEquals(listOf(2L, 5L), reported, "Jako použité se hlásí jen chaty, kam zpráva dorazila")

        // Opakování jde jen do nedoručených; teď už projde.
        conversations.failing.clear()
        vm.send { opened = it }
        advanceUntilIdle()
        assertEquals(listOf(2L, 5L, 3L, 4L), conversations.sent)
        assertEquals(3L, opened)
    }

    @Test
    fun jedenNedorucenyJmenujeJenJeho() = runTest(dispatcher) {
        val vm = viewModel(Conversations(failing = mutableSetOf(3)))
        listOf(2L, 3L).forEach(vm::toggle)

        vm.send {}
        advanceUntilIdle()

        val state = vm.state.first { !it.sending && it.error != null }
        assertEquals(setOf(3L), state.selected)
        assertEquals(UiText(Res.string.forward_failed_partial, UiText.Raw("Chat 3")), state.error)
    }

    @Test
    fun ulozeneZpravySeJmenujiPrelozene() = runTest(dispatcher) {
        // FakeChatRepository má Uložené zprávy pod id 999.
        val vm = viewModel(Conversations(failing = mutableSetOf(999)))
        listOf(2L, 999L).forEach(vm::toggle)

        vm.send {}
        advanceUntilIdle()

        assertEquals(
            UiText(Res.string.forward_failed_partial, UiText(Res.string.saved_messages)),
            vm.state.first { it.error != null }.error,
        )
    }

    @Test
    fun kdyzNeodesloNicZustaneVyberCely() = runTest(dispatcher) {
        val vm = viewModel(Conversations(failing = mutableSetOf(2, 3)))
        listOf(2L, 3L).forEach(vm::toggle)

        vm.send {}
        advanceUntilIdle()

        val state = vm.state.first { !it.sending && it.error != null }
        assertEquals(setOf(2L, 3L), state.selected)
        assertEquals(UiText(Res.string.forward_failed), state.error)
    }

    private companion object {
        fun chat(id: Long, title: String) = ChatSummary(
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
    }
}
