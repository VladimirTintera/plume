package eu.tintera.plume.features.digest

import eu.tintera.ai.core.Generated
import eu.tintera.ai.core.ModelInfo
import eu.tintera.ai.core.ModelVariant
import eu.tintera.ai.core.OnDeviceAiStatus
import eu.tintera.plume.core.ai.AiModels
import eu.tintera.plume.core.ai.AiProvider
import eu.tintera.plume.core.ai.UnimplementedOnDeviceAi
import eu.tintera.plume.core.ai.channelDigestPrompt
import eu.tintera.plume.core.ai.digestLine
import eu.tintera.plume.core.settings.UnimplementedAppSettings
import eu.tintera.plume.core.telegram.ChatFolder
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.UnimplementedChatRepository
import eu.tintera.plume.core.telegram.UnimplementedConversationRepository
import eu.tintera.plume.core.telegram.testMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
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

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelDigestViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private val now = 1_800_000_000

    private fun chat(id: Long, kind: ChatKind = ChatKind.Channel, muted: Boolean = true, unread: Int = 3, ageSeconds: Int = 3600) = ChatSummary(
        id = id,
        title = "Kanál $id",
        unreadCount = unread,
        lastMessagePreview = null,
        isPinned = false,
        order = id,
        avatarFileId = null,
        isMuted = muted,
        isMarkedAsUnread = false,
        kind = kind,
        lastMessageTimestamp = now - ageSeconds,
        presence = null,
        activity = null,
        draft = null,
        lastMessageId = id * 100,
    )

    private class FakeChats(list: List<ChatSummary>) : UnimplementedChatRepository() {
        private val state = MutableStateFlow<ChatListState>(ChatListState.Ready(list, canLoadMore = false))
        override fun chats(folder: ChatFolder): StateFlow<ChatListState> = state
    }

    private inner class FakeConversations : UnimplementedConversationRepository() {
        val asked = mutableListOf<Long>()
        override suspend fun history(chatId: Long, fromMessageId: Long, limit: Int, offset: Int): Result<List<ChatMessage>> {
            asked += chatId
            // Dva čerstvé příspěvky a jeden starší než den, od nejnovějšího.
            return Result.success(
                listOf(
                    testMessage(chatId * 100, text = "Nové $chatId").copy(timestamp = now - 60),
                    testMessage(chatId * 100 - 1, text = "Dřívější $chatId").copy(timestamp = now - 120),
                    testMessage(chatId * 100 - 2, text = "Včerejší staré").copy(timestamp = now - 2 * 86_400),
                ).take(limit),
            )
        }
    }

    private class FakeSettings(enabled: Boolean = true) : UnimplementedAppSettings() {
        override val onDeviceAiEnabled: StateFlow<Boolean> = MutableStateFlow(enabled)
        override val onDeviceAiModels: StateFlow<AiModels> = MutableStateFlow(AiModels(enabled = setOf(AiProvider.GeminiNano)))
    }

    private class FakeAi(val status: OnDeviceAiStatus = OnDeviceAiStatus.Ready) : UnimplementedOnDeviceAi() {
        val prompts = mutableListOf<String>()
        override suspend fun status(variant: ModelVariant) = status
        override suspend fun info(variant: ModelVariant) = ModelInfo("test", null)
        override fun generate(variant: ModelVariant, prompt: String): Flow<Generated> {
            prompts += prompt
            return flow {
                emit(Generated("Shrnutí"))
                emit(Generated("Shrnutí kanálu."))
            }
        }
    }

    @Test
    fun vybereZtlumeneKanalySNovymZaDen() {
        val picked = digestChannels(
            listOf(
                chat(1),
                chat(2, muted = false),
                chat(3, kind = ChatKind.Group),
                chat(4, unread = 0),
                chat(5, ageSeconds = 2 * 86_400),
                chat(6, ageSeconds = 60),
            ),
            since = now - 86_400,
        )
        // Nejnovější napřed.
        assertEquals(listOf(6L, 1L), picked.map { it.id })
    }

    @Test
    fun shrneKanalyPoJednomAZapamatujeSi() = runTest(dispatcher) {
        val ai = FakeAi()
        val conversations = FakeConversations()
        val cache = ChannelDigestCache()
        val model = ChannelDigestViewModel(FakeChats(listOf(chat(1), chat(2, ageSeconds = 60))), conversations, ai, FakeSettings(), cache, now = { now })
        advanceUntilIdle()

        val channels = model.state.value.channels!!
        assertEquals(DigestAi.Ready, model.state.value.ai)
        assertEquals(listOf(DigestSummary.Done("Shrnutí kanálu."), DigestSummary.Done("Shrnutí kanálu.")), channels.map { it.summary })
        // Do promptu jen příspěvky za posledních 24 hodin, od nejstaršího.
        assertTrue(ai.prompts.first().indexOf("Dřívější 2") < ai.prompts.first().indexOf("Nové 2"))
        assertTrue(ai.prompts.none { "Včerejší staré" in it })

        // Znovu otevřený přehled bere shrnutí z paměti, model nepouští.
        val again = ChannelDigestViewModel(FakeChats(listOf(chat(1), chat(2, ageSeconds = 60))), conversations, ai, FakeSettings(), cache, now = { now })
        advanceUntilIdle()
        assertEquals(2, ai.prompts.size)
        assertTrue(again.state.value.channels!!.all { it.summary is DigestSummary.Done })
    }

    @Test
    fun bezModeluJenSeznam() = runTest(dispatcher) {
        val off = ChannelDigestViewModel(FakeChats(listOf(chat(1))), FakeConversations(), FakeAi(), FakeSettings(enabled = false), ChannelDigestCache(), now = { now })
        val missing = ChannelDigestViewModel(FakeChats(listOf(chat(1))), FakeConversations(), FakeAi(OnDeviceAiStatus.Downloadable), FakeSettings(), ChannelDigestCache(), now = { now })
        advanceUntilIdle()

        assertEquals(DigestAi.Off, off.state.value.ai)
        assertEquals(DigestAi.Missing, missing.state.value.ai)
        assertEquals(listOf<DigestSummary>(DigestSummary.None), missing.state.value.channels!!.map { it.summary })
    }

    @Test
    fun promptBereNejnovejsiKdyzSeNevejde() {
        val prompt = channelDigestPrompt("Kanál", listOf("a".repeat(500), "b".repeat(500), "konec"), "cs", maxChars = 520)
        assertTrue("konec" in prompt && "b".repeat(500) in prompt)
        assertTrue("a".repeat(500) !in prompt)
        assertTrue("Czech" in prompt)
    }

    @Test
    fun odpovedNaJedenOdstavec() {
        assertEquals("První věta. Druhá věta.", digestLine("- První věta.\n- Druhá věta."))
    }
}
