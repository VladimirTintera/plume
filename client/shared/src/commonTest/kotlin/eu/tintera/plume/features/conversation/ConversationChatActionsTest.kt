package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.BotIntro
import eu.tintera.plume.core.telegram.ChatBar
import eu.tintera.plume.core.telegram.JoinResult
import eu.tintera.plume.core.telegram.PendingJoinRequests
import eu.tintera.plume.core.telegram.ReportStep
import eu.tintera.plume.core.telegram.SpamActions
import eu.tintera.plume.core.telegram.SpamBar
import eu.tintera.plume.core.telegram.UnimplementedChatRepository
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.core.telegram.UnimplementedInviteLinkRepository
import eu.tintera.plume.core.telegram.VideoChatInfo
import eu.tintera.plume.core.telegram.WriteAccess
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.features.chats.SpamCleanup
import eu.tintera.plume.features.report.ReportFlow
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_blocked
import eu.tintera.plume.resources.chats_unarchived
import eu.tintera.plume.resources.conversation_join_failed
import eu.tintera.plume.resources.conversation_unblock_failed
import eu.tintera.plume.resources.invite_request_sent
import eu.tintera.plume.resources.join_guard_declined
import eu.tintera.plume.resources.profile_mute_failed
import eu.tintera.plume.resources.spam_bar_contact_added
import eu.tintera.plume.resources.spam_bar_failed
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationChatActionsTest {

    private class FakeRepository : UnimplementedConversationRepository() {
        var joinResult: Result<JoinResult> = Result.success(JoinResult.Joined(TEST_CHAT))
        var join: CompletableDeferred<Unit>? = null
        var joins = 0

        override suspend fun joinChat(chatId: Long): Result<JoinResult> {
            joins++
            join?.await()
            return joinResult
        }
    }

    /** Zapisuje volání v pořadí, v jakém přišla — u spamu na pořadí záleží. */
    private class FakeChats : UnimplementedChatRepository() {
        val calls = mutableListOf<String>()
        val bar = MutableStateFlow<ChatBar?>(null)
        val videoChat = MutableStateFlow<VideoChatInfo?>(null)
        var blockResult: Result<Unit> = Result.success(Unit)
        /** Hodnoty `blocked` v pořadí volání. */
        val blockedValues = mutableListOf<Boolean>()
        var block: CompletableDeferred<Unit>? = null
        var failure: Throwable? = null

        private fun call(name: String): Result<Unit> {
            calls += name
            return failure?.let { Result.failure(it) } ?: Result.success(Unit)
        }

        override fun chatBar(chatId: Long): Flow<ChatBar?> = bar
        override fun videoChat(chatId: Long): Flow<VideoChatInfo?> = videoChat
        override suspend fun setMuted(chatId: Long, muted: Boolean) = call("mute $muted")
        override suspend fun addToContacts(chatId: Long) = call("contact")
        override suspend fun unarchiveFromSpamBar(chatId: Long) = call("unarchive")
        override suspend fun sharePhoneNumber(chatId: Long) = call("phone")
        override suspend fun dismissChatBar(chatId: Long) = call("dismiss")
        override suspend fun deleteOrLeave(chatId: Long) = call("delete")
        override suspend fun closeSecretChat(chatId: Long) = call("close")

        override suspend fun setBlocked(chatId: Long, blocked: Boolean): Result<Unit> {
            calls += "block"
            blockedValues += blocked
            block?.await()
            return blockResult
        }
    }

    private class FakeInviteLinks : UnimplementedInviteLinkRepository() {
        val requests = MutableStateFlow(PendingJoinRequests(count = 0))
        override fun pendingJoinRequests(chatId: Long): Flow<PendingJoinRequests> = requests
    }

    private val repository = FakeRepository()
    private val chats = FakeChats()
    private val inviteLinks = FakeInviteLinks()

    /** Nahlášení: co server odpoví průvodci; jen `Done` pustí blokování a smazání dál. */
    private var reportAnswer: ReportStep = ReportStep.Done
    private val reported = mutableListOf<Long>()

    /** Strážní boti, na které se čeká, a co se má stát po jejich schválení. */
    private val guards = mutableListOf<Pair<JoinResult.GuardBot, (Long) -> Unit>>()

    /** Kolikrát se po odblokování spustil bot. */
    private var botRestarts = 0

    private fun TestScope.actions(state: MutableStateFlow<ConversationState>, threadRootId: Long? = null) =
        ConversationChatActions(
            chatId = TEST_CHAT,
            threadRootId = threadRootId,
            state = state,
            scope = detachedScope(),
            repository = repository,
            chatRepository = chats,
            inviteLinks = inviteLinks,
            reportFlow = ReportFlow(this) { chatId, _ ->
                reported += chatId
                Result.success(reportAnswer)
            },
            logger = testLogger,
            awaitGuardBot = { guard, onApproved -> guards += guard to onApproved },
            restartBot = { botRestarts++ },
        )

    private val spamBar = SpamBar(canReportSpam = true, canBlock = true, canAddContact = true)

    @Test
    fun pruhSeUPrevedeneSkupinyNeukazuje() = runTest {
        val state = testState()
        val actions = actions(state)
        backgroundScope.launch { actions.chatBar.collect {} }
        chats.bar.value = spamBar
        runCurrent()
        assertEquals(spamBar, actions.chatBar.value)

        state.value = state.value.copy(info = testChatInfo(isGroup = true).copy(upgradedToChatId = 99))
        runCurrent()
        assertNull(actions.chatBar.value, "zvát a psát jde jen do nové supergroupy")
    }

    @Test
    fun videochatAZadostiSeVeVlaknuKomentaruNeukazuji() = runTest {
        chats.videoChat.value = VideoChatInfo(isLiveStream = false, participantCount = 3, scheduledStart = null, title = "")
        inviteLinks.requests.value = PendingJoinRequests(count = 2)

        val chat = actions(testState())
        backgroundScope.launch { chat.videoChat.collect {} }
        backgroundScope.launch { chat.joinRequests.collect {} }
        val thread = actions(testState(), threadRootId = 7)
        backgroundScope.launch { thread.videoChat.collect {} }
        backgroundScope.launch { thread.joinRequests.collect {} }
        runCurrent()

        assertEquals(3, chat.videoChat.value?.participantCount)
        assertEquals(2, chat.joinRequests.value?.count)
        assertNull(thread.videoChat.value)
        assertNull(thread.joinRequests.value)

        inviteLinks.requests.value = PendingJoinRequests(count = 0)
        runCurrent()
        assertNull(chat.joinRequests.value, "nula žádostí = žádný pruh")
    }

    @Test
    fun pridaniSeVratiPoleADruheTuknutiNeposilaZnovu() = runTest {
        val state = MutableStateFlow(ConversationState(info = testChatInfo(isChannel = true, canJoin = true)))
        val waiting = CompletableDeferred<Unit>()
        repository.join = waiting
        val actions = actions(state)

        actions.join()
        actions.join()
        runCurrent()
        assertTrue(state.value.joining)
        assertEquals(1, repository.joins)

        waiting.complete(Unit)
        advanceUntilIdle()
        assertFalse(state.value.joining)
        assertEquals(false, state.value.info?.canJoin)
    }

    private fun blockedState(writeAccess: WriteAccess = WriteAccess.Allowed, bot: Boolean = false) = MutableStateFlow(
        ConversationState(
            info = testChatInfo().copy(writeAccess = writeAccess),
            spam = SpamActions(canBlock = true, isBlocked = true),
            botIntro = if (bot) BotIntro(description = "") else null,
        ),
    )

    @Test
    fun zablokovanemuMistoPoleOdblokovat() {
        val blocked = blockedState().value
        assertTrue(blocked.showsUnblock)
        assertFalse(blocked.canWrite, "ani odpověď swipem, pole není")

        // Dokud stav blokování nedorazí, je pole — jako všude, kde se psaní neví.
        val unknown = blocked.copy(spam = null)
        assertFalse(unknown.showsUnblock)
        assertTrue(unknown.canWrite)

        // Zablokování odjinud (`blockChanges` přepíše `spam`) lištu ukáže a odblokování schová.
        assertFalse(blocked.copy(spam = SpamActions(canBlock = true)).showsUnblock)
    }

    @Test
    fun tajnyChatKamPsatNejdeSiNechaSvouVetu() {
        assertFalse(blockedState(WriteAccess.SecretClosed).value.showsUnblock)
        assertFalse(blockedState(WriteAccess.SecretPending).value.showsUnblock)
    }

    /** Protějšek, který zprávy nepřijímá (`canSendMessageToUser`): věta místo pole, ne swipe. */
    @Test
    fun protejsekKteryZpravyNeprijimaNemaPole() {
        for (access in listOf(WriteAccess.PremiumOnly, WriteAccess.DeletedAccount, WriteAccess.PaidMessages)) {
            val state = ConversationState(info = testChatInfo().copy(writeAccess = access))
            assertFalse(state.canWrite, "$access")
            assertFalse(state.showsUnblock, "$access")
        }
    }

    @Test
    fun odblokovaniVratiPoleADruheTuknutiNeposilaZnovu() = runTest {
        val state = blockedState()
        val waiting = CompletableDeferred<Unit>()
        chats.block = waiting
        val actions = actions(state)

        actions.unblock()
        actions.unblock()
        runCurrent()
        assertTrue(state.value.unblocking)
        assertEquals(listOf(false), chats.blockedValues)

        waiting.complete(Unit)
        advanceUntilIdle()
        assertFalse(state.value.unblocking)
        assertFalse(state.value.showsUnblock)
        assertTrue(state.value.canWrite)
        assertEquals(0, botRestarts, "člověku se nic neposílá")
    }

    @Test
    fun odblokovanyBotSeRovnouSpusti() = runTest {
        val state = blockedState(bot = true)
        actions(state).unblock()
        advanceUntilIdle()
        assertEquals(listOf(false), chats.blockedValues)
        assertEquals(1, botRestarts)
        assertFalse(state.value.peerBlocked)
    }

    @Test
    fun nepovedeneOdblokovaniNechaListuANespustiBota() = runTest {
        val state = blockedState(bot = true)
        chats.blockResult = Result.failure(IllegalStateException("USER_ID_INVALID"))
        actions(state).unblock()
        advanceUntilIdle()
        assertEquals(UiText(Res.string.conversation_unblock_failed), state.value.error)
        assertTrue(state.value.showsUnblock)
        assertFalse(state.value.unblocking)
        assertEquals(0, botRestarts)
    }

    @Test
    fun nepovedenePridaniSeOhlasiAPoleNevrati() = runTest {
        val state = MutableStateFlow(ConversationState(info = testChatInfo(isChannel = true, canJoin = true)))
        repository.joinResult = Result.failure(IllegalStateException("CHANNEL_PRIVATE"))
        actions(state).join()
        advanceUntilIdle()
        assertEquals(UiText(Res.string.conversation_join_failed), state.value.error)
        assertEquals(true, state.value.info?.canJoin)
        assertFalse(state.value.joining)
    }

    @Test
    fun zadostOVstupNechaTlacitkoAOhlasiSe() = runTest {
        val state = MutableStateFlow(ConversationState(info = testChatInfo(isChannel = false, canJoin = true)))
        repository.joinResult = Result.success(JoinResult.Requested)
        actions(state).join()
        advanceUntilIdle()
        assertEquals(UiText(Res.string.invite_request_sent), state.value.notice)
        assertEquals(true, state.value.info?.canJoin, "dokud správce nerozhodne, členem není")
    }

    @Test
    fun strazniBotPrevezmeVstupAPoSchvaleniVratiPole() = runTest {
        val state = MutableStateFlow(ConversationState(info = testChatInfo(isChannel = false, canJoin = true)))
        val guard = JoinResult.GuardBot(botUserId = 500, queryId = 77)
        repository.joinResult = Result.success(guard)
        actions(state).join()
        advanceUntilIdle()
        assertEquals(guard, guards.single().first)
        assertEquals(true, state.value.info?.canJoin)
        assertFalse(state.value.joining)

        guards.single().second(TEST_CHAT)
        assertEquals(false, state.value.info?.canJoin)
    }

    @Test
    fun okamziteOdmitnutiBotemMaVlastniVetu() = runTest {
        val state = MutableStateFlow(ConversationState(info = testChatInfo(isChannel = false, canJoin = true)))
        repository.joinResult = Result.success(JoinResult.Declined)
        actions(state).join()
        advanceUntilIdle()
        assertEquals(UiText(Res.string.join_guard_declined), state.value.error)
        assertEquals(true, state.value.info?.canJoin)
    }

    @Test
    fun ztlumeniPrepneStavANeznamyStavNechaBytNaPokoji() = runTest {
        val unknown = testState()
        actions(unknown).toggleMute()
        advanceUntilIdle()
        assertTrue(chats.calls.isEmpty(), "dokud stav ztlumení nedorazil, není co přepnout")

        val muted = testState().apply { value = value.copy(muted = true) }
        actions(muted).toggleMute()
        advanceUntilIdle()
        assertEquals(listOf("mute false"), chats.calls)

        chats.failure = IllegalStateException("x")
        actions(muted).toggleMute()
        advanceUntilIdle()
        assertEquals(UiText(Res.string.profile_mute_failed), muted.value.error)
    }

    @Test
    fun akceZPruhuHlasiVysledek() = runTest {
        val state = testState()
        val actions = actions(state)
        actions.addSpamBarContact()
        advanceUntilIdle()
        assertEquals(UiText(Res.string.spam_bar_contact_added), state.value.notice)

        actions.unarchiveFromSpamBar()
        advanceUntilIdle()
        assertEquals(UiText(Res.string.chats_unarchived), state.value.notice)
        assertTrue(actions.unarchived.value, "obrazovka otevřená z archivu se přestěhuje")

        chats.failure = IllegalStateException("x")
        actions.dismissChatBar()
        advanceUntilIdle()
        assertEquals(UiText(Res.string.spam_bar_failed), state.value.error)
        assertEquals(listOf("contact", "unarchive", "dismiss"), chats.calls)
    }

    @Test
    fun nepovedeneVraceniZArchivuObrazovkuNestehuje() = runTest {
        chats.failure = IllegalStateException("x")
        val actions = actions(testState())
        actions.unarchiveFromSpamBar()
        advanceUntilIdle()
        assertFalse(actions.unarchived.value)
    }

    @Test
    fun spamSeNahlasiZablokujeASmazeVTomtoPoradi() = runTest {
        val state = testState()
        val actions = actions(state)
        actions.resolveSpamBar(SpamBarCleanup(report = true, block = true, delete = true))
        advanceUntilIdle()
        assertEquals(listOf(TEST_CHAT), reported)
        assertEquals(listOf("block", "delete"), chats.calls)
        assertTrue(actions.chatGone.value)
    }

    @Test
    fun poChybeBlokovaniSeChatNesmaze() = runTest {
        val state = testState()
        chats.blockResult = Result.failure(IllegalStateException("x"))
        val actions = actions(state)
        actions.resolveSpamBar(SpamBarCleanup(report = false, block = true, delete = true))
        advanceUntilIdle()
        assertEquals(listOf("block"), chats.calls)
        assertFalse(actions.chatGone.value)
        assertEquals(UiText(Res.string.spam_bar_failed), state.value.error)
    }

    @Test
    fun nedokoncenyPruvodceNahlasenimNicNezablokujeANesmaze() = runTest {
        reportAnswer = ReportStep.NeedsMessages
        val actions = actions(testState())
        actions.resolveSpamBar(SpamBarCleanup(report = true, block = true, delete = true))
        advanceUntilIdle()
        assertEquals(listOf(TEST_CHAT), reported)
        assertTrue(chats.calls.isEmpty())
        assertFalse(actions.chatGone.value)
    }

    @Test
    fun zablokovanyChatBezSmazaniZustaneSHlaskou() = runTest {
        val state = testState()
        val actions = actions(state)
        actions.resolveSpamBar(SpamBarCleanup(report = false, block = true, delete = false))
        advanceUntilIdle()
        assertEquals(listOf("block"), chats.calls)
        assertFalse(actions.chatGone.value)
        assertEquals(UiText(Res.string.chats_blocked), state.value.notice)
    }

    @Test
    fun smazaniZNabidkyZavreKonverzaci() = runTest {
        val actions = actions(testState())
        actions.deleteOrLeave(SpamCleanup())
        advanceUntilIdle()
        assertEquals(listOf("delete"), chats.calls)
        assertTrue(actions.chatGone.value)
    }

    @Test
    fun nepovedeneSmazaniKonverzaciNechaOtevrenou() = runTest {
        val state = testState()
        chats.failure = IllegalStateException("x")
        val actions = actions(state)
        actions.deleteOrLeave(SpamCleanup())
        advanceUntilIdle()
        assertFalse(actions.chatGone.value)
        assertTrue(state.value.error != null)
    }
}
