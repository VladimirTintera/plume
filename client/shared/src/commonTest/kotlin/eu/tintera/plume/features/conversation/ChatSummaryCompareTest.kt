package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.ai.AiModels
import eu.tintera.plume.core.ai.AiProvider
import eu.tintera.plume.core.ai.Generated
import eu.tintera.plume.core.ai.ModelInfo
import eu.tintera.plume.core.ai.ModelVariant
import eu.tintera.plume.core.ai.OnDeviceAiStatus
import eu.tintera.plume.core.ai.UnimplementedOnDeviceAi
import eu.tintera.plume.core.settings.UnimplementedAppSettings
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.SendState
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Porovnání modelů v sheetu shrnutí ([ChatSummaryViewModel.compare]). */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSummaryCompareTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class FakeSettings(models: AiModels) : UnimplementedAppSettings() {
        override val onDeviceAiEnabled: StateFlow<Boolean> = MutableStateFlow(true)
        override val onDeviceAiModels: StateFlow<AiModels> = MutableStateFlow(models)
    }

    /** Každý model odpoví svým jménem; Gemma je nestažená, když [gemmaReady] není. */
    private class FakeAi(val gemmaReady: Boolean = true) : UnimplementedOnDeviceAi() {
        val generatedBy = mutableListOf<AiProvider>()
        val prompts = mutableListOf<String>()
        override suspend fun status(variant: ModelVariant) = when {
            variant.provider == AiProvider.Gemma && !gemmaReady -> OnDeviceAiStatus.Downloadable
            else -> OnDeviceAiStatus.Ready
        }
        override suspend fun info(variant: ModelVariant) = ModelInfo(variant.provider.name, 8_192)
        override suspend fun countTokens(variant: ModelVariant, prompt: String): Int? = null
        override fun generate(variant: ModelVariant, prompt: String): Flow<Generated> {
            generatedBy += variant.provider
            prompts += prompt
            return flowOf(Generated("- ${variant.provider.name}"))
        }
    }

    private val message = ChatMessage(
        id = 1,
        isOutgoing = false,
        senderName = "Jana",
        senderAvatarFileId = null,
        senderId = 1L,
        text = "Sraz v pátek v šest u Kotvy.",
        entities = emptyList(),
        media = null,
        linkPreview = null,
        reactions = emptyList(),
        sendState = SendState.Sent,
        replyToMessageId = null,
        isService = false,
        timestamp = 1,
        isEdited = false,
    )

    private val both = AiModels(enabled = setOf(AiProvider.GeminiNano, AiProvider.Gemma))

    private fun ChatSummaryViewModel.summarizeOne() =
        summarize("Jana", "Já") { SummarySource(listOf(message), unread = true) }

    /** Hlavní výsledek z Nano (automaticky), porovnání dopočítá Gemma — po něm, ne souběžně. */
    @Test
    fun porovnaniDopocitaOstatniModely() = runTest {
        val ai = FakeAi()
        val viewModel = ChatSummaryViewModel(ai, FakeSettings(both))
        backgroundScope.launchCollect(viewModel)

        viewModel.summarizeOne()
        advanceUntilIdle()
        val main = viewModel.state.value as ChatSummaryState.Text
        assertEquals("- GeminiNano", main.text)
        // Podpis uživatele („Já") jde do promptu, ať o něm model píše ve druhé osobě.
        assertTrue("\"Já\" in the transcript is the user who reads this summary" in ai.prompts.first())
        assertTrue(main.done && main.elapsedMs != null, "hotový výsledek nese čas")
        assertTrue(viewModel.canCompare.value)

        viewModel.compare()
        advanceUntilIdle()
        val compared = viewModel.comparison.value.single()
        assertEquals(AiProvider.Gemma, compared.provider)
        assertEquals("- Gemma", (compared.state as ChatSummaryState.Text).text)
        assertEquals(listOf(AiProvider.GeminiNano, AiProvider.Gemma), ai.generatedBy)
        // Hlavní výsledek zůstává, jak byl.
        assertEquals("- GeminiNano", (viewModel.state.value as ChatSummaryState.Text).text)
    }

    /** Nestažená Gemma: porovnání řekne, že model chybí, a nic nepočítá. */
    @Test
    fun nepripravenyModelJenRekneZeChybi() = runTest {
        val ai = FakeAi(gemmaReady = false)
        val viewModel = ChatSummaryViewModel(ai, FakeSettings(both))

        viewModel.summarizeOne()
        advanceUntilIdle()
        viewModel.compare()
        advanceUntilIdle()

        assertEquals(ChatSummaryState.ModelMissing, viewModel.comparison.value.single().state)
        assertEquals(listOf(AiProvider.GeminiNano), ai.generatedBy)
    }

    /** Vysvětlení se porovnává stejně; nové otevření sheetu staré porovnání zahodí. */
    @Test
    fun vysvetleniANoveOtevreniZahodiPorovnani() = runTest {
        val viewModel = ChatSummaryViewModel(FakeAi(), FakeSettings(both))

        viewModel.explain(message)
        advanceUntilIdle()
        viewModel.compare()
        advanceUntilIdle()
        assertEquals(AiProvider.Gemma, viewModel.comparison.value.single().provider)

        viewModel.summarizeOne()
        assertEquals(emptyList(), viewModel.comparison.value)
    }

    /**
     * Přepis hlasovky shrne model pro TEXT (Nano), ne ten na zvuk: přepis už je hotový. Sheet
     * pozná hlasovku podle zdroje; porovnání i „Zkusit znovu" jdou se stejným přepisem.
     */
    @Test
    fun prepisHlasovkyShrneTextovyModel() = runTest {
        val ai = FakeAi()
        val viewModel = ChatSummaryViewModel(ai, FakeSettings(both))

        viewModel.summarizeVoice(message, transcript = "tak v pátek v šest u Kotvy", speaker = "Jana")
        advanceUntilIdle()
        assertEquals("- GeminiNano", (viewModel.state.value as ChatSummaryState.Text).text)
        assertTrue(viewModel.source.value?.voice == true)
        assertTrue("tak v pátek v šest u Kotvy" in ai.prompts.single() && "what Jana said" in ai.prompts.single())

        viewModel.compare()
        advanceUntilIdle()
        assertEquals(AiProvider.Gemma, viewModel.comparison.value.single().provider)

        viewModel.retry { error("shrnutí konverzace se pouštět nemá") }
        advanceUntilIdle()
        assertTrue("tak v pátek v šest u Kotvy" in ai.prompts.last())
        assertEquals(emptyList(), viewModel.comparison.value)
    }

    /** S jedním zapnutým modelem není s čím porovnávat. */
    @Test
    fun sJednimModelemSeNeporovnava() = runTest {
        val viewModel = ChatSummaryViewModel(FakeAi(), FakeSettings(AiModels(enabled = setOf(AiProvider.GeminiNano))))
        backgroundScope.launchCollect(viewModel)

        viewModel.summarizeOne()
        advanceUntilIdle()
        viewModel.compare()
        advanceUntilIdle()

        assertEquals(false, viewModel.canCompare.value)
        assertEquals(emptyList(), viewModel.comparison.value)
    }

    /** `canCompare` je `WhileSubscribed`: bez odběru by zůstal na výchozí hodnotě. */
    private fun CoroutineScope.launchCollect(viewModel: ChatSummaryViewModel) =
        launch { viewModel.canCompare.collect {} }
}
