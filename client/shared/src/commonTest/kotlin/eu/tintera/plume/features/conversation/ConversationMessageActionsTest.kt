package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ForwardOptions
import eu.tintera.plume.core.telegram.MessageActions
import eu.tintera.plume.core.telegram.MessageSchedule
import eu.tintera.plume.core.telegram.ModerationChoice
import eu.tintera.plume.core.telegram.SenderModeration
import eu.tintera.plume.core.telegram.SenderModerationRights
import eu.tintera.plume.core.telegram.UnimplementedChatRepository
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.core.telegram.testMessage
import eu.tintera.plume.features.report.ReportFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationMessageActionsTest {

    private data class Pinned(val messageId: Long, val notify: Boolean, val onlyForSelf: Boolean)

    private data class Forwarded(val ids: List<Long>, val toChatId: Long, val options: ForwardOptions)

    private class FakeRepository : UnimplementedConversationRepository() {
        var deleteResult: Result<Unit> = Result.success(Unit)
        val pinned = mutableListOf<Pinned>()
        val deleted = mutableListOf<Pair<List<Long>, Boolean>>()
        val forwarded = mutableListOf<Forwarded>()
        val blocked = mutableListOf<List<Any>>()

        override suspend fun blockFromReplies(messageId: Long, deleteMessage: Boolean, deleteAll: Boolean, reportSpam: Boolean): Result<Unit> {
            blocked += listOf(messageId, deleteMessage, deleteAll, reportSpam)
            return Result.success(Unit)
        }
        val protectionAnswers = mutableListOf<Pair<Long, Boolean>>()
        var protectionResult: Result<Unit> = Result.success(Unit)

        override suspend fun answerProtectionDisableRequest(chatId: Long, messageId: Long, approve: Boolean): Result<Unit> {
            protectionAnswers += messageId to approve
            return protectionResult
        }

        override suspend fun pin(chatId: Long, messageId: Long, notify: Boolean, onlyForSelf: Boolean): Result<Unit> {
            pinned += Pinned(messageId, notify, onlyForSelf)
            return Result.success(Unit)
        }

        override suspend fun deleteMessages(chatId: Long, messageIds: List<Long>, revoke: Boolean): Result<Unit> {
            deleted += messageIds to revoke
            return deleteResult
        }

        override suspend fun forward(fromChatId: Long, messageIds: List<Long>, toChatId: Long, options: ForwardOptions): Result<Unit> {
            forwarded += Forwarded(messageIds, toChatId, options)
            return Result.success(Unit)
        }

        override suspend fun addSharedContact(chatId: Long, messageId: Long): Result<Long> = Result.success(77L)

        var moderation: SenderModeration? = null
        val moderated = mutableListOf<Pair<List<Long>, ModerationChoice>>()

        override suspend fun senderModeration(chatId: Long, messageIds: List<Long>): Result<SenderModeration?> =
            Result.success(moderation)

        override suspend fun deleteAsModerator(
            chatId: Long,
            messageIds: List<Long>,
            userId: Long,
            choice: ModerationChoice,
        ): Result<Unit> {
            moderated += messageIds to choice
            return Result.success(Unit)
        }
    }

    private class FakeChats(savedMessages: Long? = SAVED) : UnimplementedChatRepository() {
        val removed = mutableListOf<Long>()
        override val savedMessagesChatId: StateFlow<Long?> = MutableStateFlow(savedMessages)

        override suspend fun removeContact(userId: Long): Result<Unit> {
            removed += userId
            return Result.success(Unit)
        }
    }

    private val repository = FakeRepository()
    private var menusClosed = 0
    private val unpinned = mutableListOf<Long>()

    private fun TestScope.actions(state: MutableStateFlow<ConversationState>, chats: FakeChats = FakeChats()) =
        ConversationMessageActions(
            chatId = TEST_CHAT,
            state = state,
            scope = this,
            repository = repository,
            chatRepository = chats,
            reminderTimeModel = null,
            reportFlow = ReportFlow(this) { _, _ -> error("hlášení se v testu nečeká") },
            closeMenus = { menusClosed++ },
            unpin = { unpinned += it.id },
            logger = testLogger,
        )

    private fun stateWith(info: eu.tintera.plume.core.telegram.ChatInfo?, vararg messages: ChatMessage) =
        MutableStateFlow(ConversationState(messages = messages.sortedByDescending { it.id }, info = info))

    /** Otevřená nabídka nad zprávou s danými právy. */
    private fun menu(messageId: Long, actions: MessageActions) = MessageMenu(
        messageId = messageId,
        timestamp = 0,
        isOutgoing = true,
        emoji = emptyList(),
        chosenEmoji = emptySet(),
        actions = actions,
        items = emptyList(),
        favoriteSticker = null,
    )

    @Test
    fun blokovaniZOdpovediSePotvrdiAOdejdeSVolbami() = runTest {
        val state = stateWith(testChatInfo(), testMessage(9))
        val actions = actions(state)

        actions.requestBlockFromReplies(9)
        assertEquals(9L, state.value.pendingRepliesBlock)
        actions.confirmBlockFromReplies(deleteMessage = true, deleteAll = false, reportSpam = true)
        advanceUntilIdle()

        assertEquals(listOf(listOf<Any>(9L, true, false, true)), repository.blocked)
        assertNull(state.value.pendingRepliesBlock)
        assertNotNull(state.value.notice)
    }

    @Test
    fun zrusenaZadostOBlokovaniNicNeposle() = runTest {
        val state = stateWith(testChatInfo(), testMessage(9))
        val actions = actions(state)

        actions.requestBlockFromReplies(9)
        actions.dismissBlockFromReplies()
        actions.confirmBlockFromReplies(deleteMessage = true, deleteAll = true, reportSpam = true)
        advanceUntilIdle()

        assertEquals(emptyList(), repository.blocked)
    }

    @Test
    fun odpovedNaZadostOVypnutiOchranyOdejdeAPotvrdiSe() = runTest {
        val state = stateWith(testChatInfo(), testMessage(5))
        val actions = actions(state)

        actions.answerProtectionRequest(5, approve = true)
        advanceUntilIdle()

        assertEquals(listOf(5L to true), repository.protectionAnswers)
        assertNotNull(state.value.notice)
        assertNull(state.value.error)
    }

    @Test
    fun neuspesnaOdpovedNaZadostSeOhlasi() = runTest {
        repository.protectionResult = Result.failure(IllegalStateException("MESSAGE_ID_INVALID"))
        val state = stateWith(testChatInfo(), testMessage(5))
        val actions = actions(state)

        actions.answerProtectionRequest(5, approve = false)
        advanceUntilIdle()

        assertEquals(listOf(5L to false), repository.protectionAnswers)
        assertNotNull(state.value.error)
    }

    @Test
    fun vUlozenychZpravachSePripneHnedBezUpozorneni() = runTest {
        val state = stateWith(testChatInfo(isSavedMessages = true), testMessage(1))
        val actions = actions(state)

        actions.requestPin(1)
        advanceUntilIdle()

        assertEquals(listOf(Pinned(1, notify = false, onlyForSelf = false)), repository.pinned)
        assertNull(state.value.pendingPin)
        assertEquals(1, menusClosed)
    }

    @Test
    fun veSkupineSeZeptaAPotvrzeniUpozorni() = runTest {
        val state = stateWith(testChatInfo(isGroup = true), testMessage(1))
        val actions = actions(state)

        actions.requestPin(1)
        assertEquals(PinScope.Group, state.value.pendingPin?.scope)
        actions.confirmPin(option = true)
        advanceUntilIdle()

        assertEquals(listOf(Pinned(1, notify = true, onlyForSelf = false)), repository.pinned)
        assertNull(state.value.pendingPin)
    }

    @Test
    fun vSoukromemChatuBezVolbySePripneJenSobe() = runTest {
        val state = stateWith(testChatInfo(), testMessage(1))
        val actions = actions(state)

        actions.requestPin(1)
        assertEquals(PinScope.Private, state.value.pendingPin?.scope)
        actions.confirmPin(option = false)
        advanceUntilIdle()

        assertEquals(listOf(Pinned(1, notify = false, onlyForSelf = true)), repository.pinned)
    }

    @Test
    fun zrusenePripnutiNicNeposle() = runTest {
        val state = stateWith(testChatInfo(isGroup = true), testMessage(1))
        val actions = actions(state)

        actions.requestPin(1)
        actions.cancelPin()
        actions.confirmPin(option = true)
        advanceUntilIdle()

        assertTrue(repository.pinned.isEmpty())
    }

    @Test
    fun odepnutiZNabidkyJdeCestouPruhuPripnutych() = runTest {
        val state = stateWith(null, testMessage(1))
        val actions = actions(state)

        actions.unpinFromMenu(1)

        assertEquals(listOf(1L), unpinned)
        assertEquals(1, menusClosed)
    }

    @Test
    fun smazaniAlbaSeZeptaNaCeleAlbumSPravyZNabidky() = runTest {
        val state = stateWith(null, testMessage(1, album = 9), testMessage(2, album = 9), testMessage(3))
        state.value = state.value.copy(messageMenu = menu(2, MessageActions.Fallback.copy(canDeleteForSelf = true)))
        val actions = actions(state)

        actions.requestDelete(2)

        val pending = assertNotNull(state.value.pendingDelete)
        assertEquals(setOf(1L, 2L), pending.messageIds.toSet())
        assertTrue(pending.canDeleteForSelf)
        assertEquals(false, pending.canDeleteForAll)
        assertNull(state.value.messageMenu)
    }

    @Test
    fun potvrzeneSmazaniOdejdeSVolbouProVsechny() = runTest {
        val state = stateWith(null, testMessage(1))
        state.value = state.value.copy(messageMenu = menu(1, MessageActions.Fallback.copy(canDeleteForAll = true)))
        val actions = actions(state)
        actions.requestDelete(1)

        actions.confirmDelete(revoke = true)
        advanceUntilIdle()

        assertEquals(listOf(listOf(1L) to true), repository.deleted)
        assertNull(state.value.pendingDelete)
    }

    @Test
    fun nepovedeneSmazaniSeOhlasi() = runTest {
        repository.deleteResult = Result.failure(IllegalStateException("MESSAGE_DELETE_FORBIDDEN"))
        val state = stateWith(null, testMessage(1))
        state.value = state.value.copy(messageMenu = menu(1, MessageActions.Fallback.copy(canDeleteForSelf = true)))
        val actions = actions(state)
        actions.requestDelete(1)

        actions.confirmDelete(revoke = false)
        advanceUntilIdle()

        assertNotNull(state.value.error)
    }

    @Test
    fun preposlaniAlbaVezmeCeleAlbum() = runTest {
        val state = stateWith(null, testMessage(1, album = 9), testMessage(2, album = 9))
        val actions = actions(state)

        actions.requestForward(1)

        assertEquals(setOf(1L, 2L), state.value.forwardRequest?.toSet())
    }

    @Test
    fun pripominkaOdejdeDoUlozenychNaplanovana() = runTest {
        val state = stateWith(null, testMessage(1))
        val actions = actions(state)

        actions.remind(1, at = 2_000_000_000)
        advanceUntilIdle()

        assertEquals(
            listOf(Forwarded(listOf(1L), SAVED, ForwardOptions(schedule = MessageSchedule.At(2_000_000_000)))),
            repository.forwarded,
        )
        assertNotNull(state.value.notice)
        assertNull(state.value.remindRequest)
    }

    @Test
    fun bezUlozenychZpravSePripominkaNeposle() = runTest {
        val state = stateWith(null, testMessage(1))
        val actions = actions(state, FakeChats(savedMessages = null))

        actions.remind(1, at = 2_000_000_000)
        advanceUntilIdle()

        assertTrue(repository.forwarded.isEmpty())
    }

    @Test
    fun pridanyKontaktSeOhlasiAJdeVratit() = runTest {
        val chats = FakeChats()
        val state = stateWith(null, testMessage(1))
        val actions = actions(state, chats)

        actions.addSharedContact(1)
        advanceUntilIdle()
        assertEquals(77L, state.value.addedContact)

        actions.undoAddContact(77)
        advanceUntilIdle()
        assertEquals(listOf(77L), chats.removed)
    }

    @Test
    fun zrusenaPrilohaSeSmazeProVsechny() = runTest {
        val state = stateWith(null)
        val actions = actions(state)

        actions.cancelUpload(5)
        advanceUntilIdle()

        assertEquals(listOf(listOf(5L) to true), repository.deleted)
    }

    private companion object {
        const val SAVED = 500L
    }

    private val spammer = SenderModeration(
        userId = 42,
        name = "Spammer",
        rights = SenderModerationRights(canDeleteAll = true, canDeleteReactions = true, canReportSpam = true, canBan = true),
    )

    @Test
    fun spravciSeDoPotvrzeniSmazaniDoctouVolbyNavic() = runTest {
        repository.moderation = spammer
        val state = stateWith(null, testMessage(1))
        state.value = state.value.copy(messageMenu = menu(1, MessageActions.Fallback.copy(canDeleteForAll = true)))
        val actions = actions(state)

        actions.requestDelete(1)
        advanceUntilIdle()

        assertEquals(spammer, state.value.pendingDelete?.moderation)
    }

    /** Smazat jde jen u sebe = vlastní zpráva nebo soukromý chat; na server se kvůli tomu neptá. */
    @Test
    fun bezMazaniProVsechnySeVolbyNavicNehledaji() = runTest {
        repository.moderation = spammer
        val state = stateWith(null, testMessage(1))
        state.value = state.value.copy(messageMenu = menu(1, MessageActions.Fallback.copy(canDeleteForSelf = true)))
        val actions = actions(state)

        actions.requestDelete(1)
        advanceUntilIdle()

        assertNull(state.value.pendingDelete?.moderation)
    }

    @Test
    fun zaskrtnuteVolbyOdejdouSpolecneSeSmazanim() = runTest {
        repository.moderation = spammer
        val state = stateWith(null, testMessage(1))
        state.value = state.value.copy(messageMenu = menu(1, MessageActions.Fallback.copy(canDeleteForAll = true)))
        val actions = actions(state)
        actions.requestDelete(1)
        advanceUntilIdle()

        val choice = ModerationChoice(reportSpam = true, ban = true)
        actions.confirmDelete(revoke = true, choice = choice)
        advanceUntilIdle()

        assertEquals(listOf(listOf(1L) to choice), repository.moderated)
        assertTrue(repository.deleted.isEmpty())
        assertNotNull(state.value.notice)
    }

    @Test
    fun bezZaskrtnuteVolbyJeToObycejneSmazani() = runTest {
        repository.moderation = spammer
        val state = stateWith(null, testMessage(1))
        state.value = state.value.copy(messageMenu = menu(1, MessageActions.Fallback.copy(canDeleteForAll = true)))
        val actions = actions(state)
        actions.requestDelete(1)
        advanceUntilIdle()

        actions.confirmDelete(revoke = true)
        advanceUntilIdle()

        assertEquals(listOf(listOf(1L) to true), repository.deleted)
        assertTrue(repository.moderated.isEmpty())
    }

    /** Jen smazání reakcí je taky volba správce: jde přes `deleteAsModerator`, ne holé mazání. */
    @Test
    fun samotneSmazaniReakciJdeJakoModerovani() = runTest {
        repository.moderation = spammer
        val state = stateWith(null, testMessage(1))
        state.value = state.value.copy(messageMenu = menu(1, MessageActions.Fallback.copy(canDeleteForAll = true)))
        val actions = actions(state)
        actions.requestDelete(1)
        advanceUntilIdle()

        val choice = ModerationChoice(deleteReactions = true)
        actions.confirmDelete(revoke = true, choice = choice)
        advanceUntilIdle()

        assertEquals(listOf(listOf(1L) to choice), repository.moderated)
        assertTrue(repository.deleted.isEmpty())
        // Nikdo nebyl vyhozený, takže ani hláška o vyhození.
        assertNull(state.value.notice)
    }
}
