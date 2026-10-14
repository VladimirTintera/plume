package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.MessageActions
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.features.report.ReportFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationSelectionTest {

    private val deletable = MessageActions.Fallback.copy(canDeleteForSelf = true, canDeleteForAll = true)

    private class FakeRepository(private val actions: MessageActions) : UnimplementedConversationRepository() {
        val asked = mutableListOf<Long>()

        override suspend fun messageActions(chatId: Long, messageId: Long): Result<MessageActions> {
            asked += messageId
            return Result.success(actions)
        }
    }

    private val copied = mutableListOf<String>()

    private fun selection(scope: CoroutineScope, state: kotlinx.coroutines.flow.MutableStateFlow<ConversationState>, repository: FakeRepository) =
        ConversationSelection(
            chatId = TEST_CHAT,
            state = state,
            scope = scope,
            repository = repository,
            copyToClipboard = { copied += it },
            reportFlow = ReportFlow(scope) { _, _ -> error("hlášení se v testu nečeká") },
            logger = testLogger,
        )

    @Test
    fun vyberZacneSOznacenouZpravouAPraviSeDoptaServeru() = runTest {
        val state = testState(testMessage(1), testMessage(2))
        val repository = FakeRepository(deletable)
        val selection = selection(this, state, repository)

        selection.startSelection(2)
        advanceUntilIdle()

        assertEquals(listOf(2L), state.value.selectedMessageIds())
        assertEquals(listOf(2L), repository.asked)
        assertTrue(state.value.selection!!.canDelete)
    }

    @Test
    fun odebraniPosledniZpravyVyberUkonci() = runTest {
        val state = testState(testMessage(1))
        val selection = selection(this, state, FakeRepository(deletable))

        selection.startSelection(1)
        selection.toggleSelection(1)

        assertNull(state.value.selection)
    }

    @Test
    fun albumSeVybiraCele() = runTest {
        val state = testState(testMessage(1, album = 9), testMessage(2, album = 9), testMessage(3))
        val selection = selection(this, state, FakeRepository(deletable))

        selection.startSelection(2)

        assertEquals(listOf(1L, 2L), state.value.selectedMessageIds())
    }

    @Test
    fun nadStropVybranychSeNepridaAOhlasiSe() = runTest {
        val messages = (1L..MAX_SELECTED_MESSAGES + 1L).map { testMessage(it) }
        val state = testState(*messages.toTypedArray())
        val selection = selection(this, state, FakeRepository(deletable))
        selection.startSelection(1)
        (2L..MAX_SELECTED_MESSAGES).forEach(selection::toggleSelection)

        selection.toggleSelection(MAX_SELECTED_MESSAGES + 1L)

        assertEquals(MAX_SELECTED_MESSAGES, state.value.selection?.count)
        assertNotNull(state.value.notice)
    }

    @Test
    fun kopirovaniDaTextyOdNejstarsiAVyberZavre() = runTest {
        val state = testState(testMessage(1, "první"), testMessage(2, ""), testMessage(3, "třetí"))
        val selection = selection(this, state, FakeRepository(deletable))
        selection.startSelection(3)
        selection.toggleSelection(2)
        selection.toggleSelection(1)

        selection.copySelection()
        advanceUntilIdle()

        assertEquals(listOf("první\n\ntřetí"), copied)
        assertNull(state.value.selection)
    }

    @Test
    fun smazaniVyberuSeZeptaSPravyCelehoVyberu() = runTest {
        val state = testState(testMessage(1), testMessage(2))
        val selection = selection(this, state, FakeRepository(deletable))
        selection.startSelection(2)
        selection.toggleSelection(1)
        advanceUntilIdle()

        selection.deleteSelection()

        val pending = assertNotNull(state.value.pendingDelete)
        assertEquals(listOf(1L, 2L), pending.messageIds)
        assertTrue(pending.canDeleteForAll)
        assertNull(state.value.selection)
    }
}
