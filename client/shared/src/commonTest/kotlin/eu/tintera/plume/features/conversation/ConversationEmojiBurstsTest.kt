package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.EmojiInteraction
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.core.telegram.testMessage
import eu.tintera.plume.features.conversation.items.EmojiBurst
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationEmojiBurstsTest {

    private class FakeRepository(private val click: Result<Int?> = Result.success(EFFECT)) : UnimplementedConversationRepository() {
        val interactions = MutableSharedFlow<EmojiInteraction>()
        val clicked = mutableListOf<Long>()
        val watching = mutableListOf<String>()

        override fun emojiInteractions(chatId: Long): Flow<EmojiInteraction> = interactions

        override suspend fun clickAnimatedEmoji(chatId: Long, messageId: Long): Result<Int?> {
            clicked += messageId
            return click
        }

        override suspend fun sendWatchingAnimation(chatId: Long, emoji: String): Result<Unit> {
            watching += emoji
            return Result.success(Unit)
        }
    }

    private fun emojiMessage(id: Long, sendState: SendState? = null) =
        testMessage(id, text = "").copy(media = MessageMedia.AnimatedEmoji(animationFileId = 1, emoji = "❤️"), sendState = sendState)

    private fun TestScope.bursts(repository: FakeRepository, state: MutableStateFlow<ConversationState> = testState(emojiMessage(1))) =
        ConversationEmojiBursts(
            chatId = TEST_CHAT,
            state = state,
            scope = detachedScope(),
            repository = repository,
            logger = testLogger,
        ).also { runCurrent() }

    @Test
    fun tuknutiPosleEfektAPrehrajeHo() = runTest {
        val repository = FakeRepository()
        val bursts = bursts(repository)

        bursts.tap(1)
        runCurrent()

        assertEquals(listOf(1L), repository.clicked)
        assertEquals(EmojiBurst(messageId = 1, run = 1, animationFileId = EFFECT, fromPartner = false), bursts.bursts.value[1])
    }

    @Test
    fun emojiBezEfektuNicNepublikuje() = runTest {
        val repository = FakeRepository(click = Result.success(null))
        val bursts = bursts(repository)

        bursts.tap(1)
        runCurrent()

        assertEquals(listOf(1L), repository.clicked)
        assertTrue(bursts.bursts.value.isEmpty())
    }

    @Test
    fun odesilanaZpravaSeNeposila() = runTest {
        val repository = FakeRepository()
        val bursts = bursts(repository, testState(emojiMessage(1, SendState.Sending)))

        bursts.tap(1)
        runCurrent()

        assertTrue(repository.clicked.isEmpty())
    }

    @Test
    fun efektOdProtejskuSePoPrehraniNahlasi() = runTest {
        val repository = FakeRepository()
        val bursts = bursts(repository)

        repository.interactions.emit(EmojiInteraction(messageId = 1, animationFileId = EFFECT))
        runCurrent()
        val burst = bursts.bursts.value.getValue(1)
        assertTrue(burst.fromPartner)
        assertTrue(repository.watching.isEmpty(), "Nahlásit až po přehrání, ne po příchodu")

        bursts.shown(burst)
        runCurrent()

        assertEquals(listOf("❤️"), repository.watching)
        assertTrue(bursts.bursts.value.isEmpty())
    }

    @Test
    fun vlastniEfektSeNenahlasuje() = runTest {
        val repository = FakeRepository()
        val bursts = bursts(repository)

        bursts.tap(1)
        runCurrent()
        bursts.shown(bursts.bursts.value.getValue(1))
        runCurrent()

        assertTrue(repository.watching.isEmpty())
    }

    @Test
    fun nevyzvednutyEfektVyprsi() = runTest {
        val repository = FakeRepository()
        val bursts = bursts(repository)

        repository.interactions.emit(EmojiInteraction(messageId = 1, animationFileId = EFFECT))
        runCurrent()
        advanceTimeBy(1_000)
        assertEquals(1, bursts.bursts.value.size)

        advanceTimeBy(1_000)
        assertTrue(bursts.bursts.value.isEmpty(), "Zpráva, na kterou se odscrolluje pozdě, už nevybuchne")
        assertTrue(repository.watching.isEmpty())
    }

    @Test
    fun novejsiTuknutiNahradiStarsi() = runTest {
        val repository = FakeRepository()
        val bursts = bursts(repository)

        bursts.tap(1)
        runCurrent()
        advanceTimeBy(1_000)
        bursts.tap(1)
        runCurrent()
        // Vypršení prvního efektu nesmí smazat druhý.
        advanceTimeBy(700)

        assertEquals(2L, bursts.bursts.value.getValue(1).run)
        advanceUntilIdle()
    }

    private companion object {
        const val EFFECT = 99
    }
}
