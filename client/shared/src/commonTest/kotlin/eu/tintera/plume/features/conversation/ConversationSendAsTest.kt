package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.MessageSenderOption
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.core.telegram.messageSenderChoice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Výběr odesílatele ve skupině ([ConversationSendAs]). Část běží na `TestScope`, ne na
 * `viewModelScope`, takže `Dispatchers.setMain` nepotřebuje. Odběr běží v `backgroundScope`,
 * a ten `advanceUntilIdle` NEPOHNE (čeká jen na práci v popředí) — proto `runCurrent`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationSendAsTest {

    private val me = MessageSenderOption(id = 7, title = "Já", avatarFileId = null, kind = ChatKind.Private)
    private val channel = MessageSenderOption(id = -1001, title = "Kanál", avatarFileId = null, kind = ChatKind.Channel)
    private val premium = MessageSenderOption(id = -1002, title = "Prémiový", avatarFileId = null, kind = ChatKind.Channel, locked = true)

    private inner class FakeRepository : UnimplementedConversationRepository() {
        /** Kým se píše; `null` = odesílatele měnit nejde. */
        val sender = MutableStateFlow<Long?>(me.id)
        var options = listOf(me, channel, premium)
        var setFails = false
        val set = mutableListOf<Long>()
        var loads = 0

        override fun messageSender(chatId: Long): Flow<Long?> = sender

        override suspend fun availableMessageSenders(chatId: Long): Result<List<MessageSenderOption>> {
            loads++
            return Result.success(options)
        }

        override suspend fun setMessageSender(chatId: Long, senderId: Long): Result<Unit> {
            set += senderId
            if (setFails) return Result.failure(IllegalStateException("CHAT_SEND_AS_FORBIDDEN"))
            sender.value = senderId
            return Result.success(Unit)
        }
    }

    private val repository = FakeRepository()

    private fun TestScope.sendAs(state: MutableStateFlow<ConversationState>) = ConversationSendAs(
        chatId = TEST_CHAT,
        state = state,
        scope = backgroundScope,
        repository = repository,
        logger = testLogger,
    ).also { it.observe() }

    @Test
    fun vybranyANabidkaSeUkazouUPole() = runTest {
        val state = MutableStateFlow(ConversationState())
        sendAs(state)
        runCurrent()

        val choice = assertNotNull(state.value.messageSender)
        assertEquals(me, choice.current)
        assertEquals(listOf(me, channel, premium), choice.options)
    }

    /** TDLib dá `messageSenderId` jen tam, kde odesílatele měnit jde; jinde se nabídka nečte. */
    @Test
    fun kdeOdesilateleMenitNejdeNicSeNeukazuje() = runTest {
        repository.sender.value = null
        val state = MutableStateFlow(ConversationState())
        sendAs(state)
        runCurrent()

        assertNull(state.value.messageSender)
        assertEquals(0, repository.loads)
    }

    @Test
    fun volbaKanaluOdejdeNaServerASeUkaze() = runTest {
        val state = MutableStateFlow(ConversationState())
        val sendAs = sendAs(state)
        runCurrent()

        sendAs.choose(channel)
        runCurrent()

        assertEquals(listOf(channel.id), repository.set)
        assertEquals(channel, state.value.messageSender?.current)
    }

    @Test
    fun zamcenaVolbaSeNevybereAOznamiProc() = runTest {
        val state = MutableStateFlow(ConversationState())
        val sendAs = sendAs(state)
        runCurrent()

        sendAs.choose(premium)
        runCurrent()

        assertTrue(repository.set.isEmpty())
        assertEquals(me, state.value.messageSender?.current)
        assertNotNull(state.value.notice)
    }

    @Test
    fun selhaniVratiPredchozihoOdesilatele() = runTest {
        repository.setFails = true
        val state = MutableStateFlow(ConversationState())
        val sendAs = sendAs(state)
        runCurrent()

        sendAs.choose(channel)
        runCurrent()

        assertEquals(me, state.value.messageSender?.current)
        assertNotNull(state.value.error)
    }

    /** Změna z jiného zařízení na odesílatele, kterého nabídka nezná: nabídka se přečte znovu. */
    @Test
    fun neznamyOdesilatelNabidkuPrectZnovu() = runTest {
        val state = MutableStateFlow(ConversationState())
        sendAs(state)
        runCurrent()
        val group = MessageSenderOption(id = -1003, title = "Skupina", avatarFileId = null, kind = ChatKind.Group, isChat = true)
        repository.options = listOf(me, channel, premium, group)

        repository.sender.value = group.id
        runCurrent()

        assertEquals(2, repository.loads)
        assertEquals(group, state.value.messageSender?.current)
    }

    @Test
    fun vyberSJedinouVolbouNeniVyber() {
        assertNull(messageSenderChoice(me.id, listOf(me)))
        assertNull(messageSenderChoice(null, listOf(me, channel)))
        // Vybraný, který v nabídce chybí, by avatarem u pole tvrdil něco jiného, než co odejde.
        assertNull(messageSenderChoice(-5, listOf(me, channel)))
        assertEquals(channel, messageSenderChoice(channel.id, listOf(me, channel))?.current)
    }
}
