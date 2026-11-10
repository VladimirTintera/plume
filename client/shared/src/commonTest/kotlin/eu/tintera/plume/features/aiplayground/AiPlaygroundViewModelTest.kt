package eu.tintera.plume.features.aiplayground

import eu.tintera.plume.core.ai.AiModels
import eu.tintera.plume.core.ai.AiProvider
import eu.tintera.plume.core.ai.LiteRtModel
import eu.tintera.plume.core.ai.provider
import eu.tintera.ai.core.Generated
import eu.tintera.ai.core.ModelInfo
import eu.tintera.ai.core.ModelVariant
import eu.tintera.ai.core.OnDeviceAiStatus
import eu.tintera.plume.core.ai.UnimplementedOnDeviceAi
import eu.tintera.plume.core.settings.UnimplementedAppSettings
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Hřiště s modelem ([AiPlaygroundViewModel]): kola, historie, ořez a zastavení. */
@OptIn(ExperimentalCoroutinesApi::class)
class AiPlaygroundViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class FakeSettings(models: AiModels) : UnimplementedAppSettings() {
        override val onDeviceAiEnabled: StateFlow<Boolean> = MutableStateFlow(true)
        override val onDeviceAiModels: StateFlow<AiModels> = MutableStateFlow(models)
    }

    /**
     * Model, který odpoví [reply] na každou otázku; tokeny = znaky promptu, takže [limit] je
     * limit vstupu ve znacích. Gemma je nestažená, když [gemmaReady] není; stahované modely
     * mimo Gemmu jsou stažené jen ty z [downloaded].
     */
    private class FakeAi(
        val limit: Int? = null,
        val gemmaReady: Boolean = true,
        val downloaded: Set<LiteRtModel> = emptySet(),
        val reply: (String) -> Flow<Generated> = { flow { emit(Generated("Odpověď")) } },
    ) : UnimplementedOnDeviceAi() {
        val prompts = mutableListOf<String>()
        val generatedBy = mutableListOf<AiProvider>()
        val generatedVariants = mutableListOf<ModelVariant>()
        override suspend fun status(variant: ModelVariant) = when {
            variant.provider == AiProvider.Gemma -> if (gemmaReady) OnDeviceAiStatus.Ready else OnDeviceAiStatus.Downloadable
            variant.appManaged -> if (downloaded.any { it.variant == variant }) OnDeviceAiStatus.Ready else OnDeviceAiStatus.Downloadable
            else -> OnDeviceAiStatus.Ready
        }
        override suspend fun info(variant: ModelVariant) = ModelInfo(variant.provider.name, limit)
        override suspend fun countTokens(variant: ModelVariant, prompt: String): Int = prompt.length
        override fun generate(variant: ModelVariant, prompt: String): Flow<Generated> {
            prompts += prompt
            generatedBy += variant.provider
            generatedVariants += variant
            return reply(prompt)
        }
    }

    private val nano = AiModels(enabled = setOf(AiProvider.GeminiNano))
    private val both = AiModels(enabled = setOf(AiProvider.GeminiNano, AiProvider.Gemma))

    @Test
    fun odpovedJeHotovaSMerenim() = runTest {
        val ai = FakeAi()
        val viewModel = AiPlaygroundViewModel(ai, FakeSettings(nano))

        viewModel.send("  Ahoj  ")
        advanceUntilIdle()

        val turn = viewModel.state.value.turns.single()
        assertEquals("Ahoj", turn.question)
        assertEquals("Odpověď", turn.answer.text)
        assertEquals(AnswerPhase.Done, turn.answer.phase)
        assertEquals(AiProvider.GeminiNano, turn.answer.provider)
        assertEquals("Odpověď".length, turn.answer.tokens)
        assertTrue(turn.answer.totalMs != null && turn.answer.firstWordMs != null)
        assertTrue(ai.prompts.single().endsWith("User: Ahoj\nAssistant:"))
        assertFalse(viewModel.state.value.generating)
    }

    @Test
    fun dalsiKoloNeseHistorii() = runTest {
        val ai = FakeAi()
        val viewModel = AiPlaygroundViewModel(ai, FakeSettings(nano))

        viewModel.send("První")
        advanceUntilIdle()
        viewModel.send("Druhá")
        advanceUntilIdle()

        val prompt = ai.prompts.last()
        assertTrue("User: První\nAssistant: Odpověď" in prompt, prompt)
        assertTrue(prompt.endsWith("User: Druhá\nAssistant:"))
        assertNull(viewModel.state.value.rememberedFromId)
    }

    /** Co se nevejde, odpadne od nejstarších kol celé, a předěl stojí nad prvním, které zbylo. */
    @Test
    fun orezOdNejstarsichKol() = runTest {
        val long = "x".repeat(200)
        val ai = FakeAi(limit = 900, reply = { flow { emit(Generated(long)) } })
        val viewModel = AiPlaygroundViewModel(ai, FakeSettings(nano))

        repeat(4) { index ->
            viewModel.send("Otázka $index")
            advanceUntilIdle()
        }

        val prompt = ai.prompts.last()
        assertTrue(prompt.length <= 900, "prompt má ${prompt.length} znaků")
        assertFalse("Otázka 0" in prompt)
        assertTrue("Otázka 2" in prompt)
        val turns = viewModel.state.value.turns
        val firstKept = turns.first { "User: ${it.question}\n" in prompt }
        assertEquals(firstKept.id, viewModel.state.value.rememberedFromId)
    }

    /** Model dopsal repliku za uživatele: odpověď končí před ní a generování se zastaví. */
    @Test
    fun replikaZaUzivateleSeUsekne() = runTest {
        var after = false
        val ai = FakeAi(
            reply = {
                flow {
                    emit(Generated("Mám se dobře."))
                    emit(Generated("Mám se dobře.\nUser: A co ty?"))
                    after = true
                    emit(Generated("Mám se dobře.\nUser: A co ty?\nAssistant: Já taky."))
                }
            },
        )
        val viewModel = AiPlaygroundViewModel(ai, FakeSettings(nano))

        viewModel.send("Jak se máš?")
        advanceUntilIdle()

        val answer = viewModel.state.value.turns.single().answer
        assertEquals("Mám se dobře.", answer.text)
        assertEquals(AnswerPhase.Done, answer.phase)
        assertFalse(after, "generování mělo skončit u repliky uživatele")
    }

    @Test
    fun zastaveniNechaRozepsanyText() = runTest {
        val ai = FakeAi(
            reply = { prompt ->
                flow {
                    emit(Generated("Rozepsaná"))
                    if ("Druhá" !in prompt) awaitCancellation()
                }
            },
        )
        val viewModel = AiPlaygroundViewModel(ai, FakeSettings(nano))

        viewModel.send("První")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.generating)
        viewModel.stop()
        advanceUntilIdle()

        val stopped = viewModel.state.value.turns.single().answer
        assertEquals(AnswerPhase.Stopped, stopped.phase)
        assertEquals("Rozepsaná", stopped.text)
        assertFalse(viewModel.state.value.generating)

        // Zastavená odpověď se počítá do historie dalšího kola.
        viewModel.send("Druhá")
        advanceUntilIdle()
        assertTrue("User: První\nAssistant: Rozepsaná" in ai.prompts.last())
    }

    @Test
    fun nestazenyModelRekneZeChybi() = runTest {
        val ai = FakeAi(gemmaReady = false)
        val viewModel = AiPlaygroundViewModel(ai, FakeSettings(both))

        viewModel.choose(both.variant(AiProvider.Gemma))
        viewModel.send("Ahoj")
        advanceUntilIdle()

        assertEquals(AnswerPhase.ModelMissing, viewModel.state.value.turns.single().answer.phase)
        assertTrue(ai.prompts.isEmpty())
    }

    /** Ručně vybraný model platí pro další odpovědi i pro „Odpovědět znovu". */
    @Test
    fun znovuOdpoviVybranyModel() = runTest {
        val ai = FakeAi()
        val viewModel = AiPlaygroundViewModel(ai, FakeSettings(both))

        viewModel.send("Ahoj")
        advanceUntilIdle()
        viewModel.choose(both.variant(AiProvider.Gemma))
        viewModel.regenerate()
        advanceUntilIdle()

        assertEquals(listOf(AiProvider.GeminiNano, AiProvider.Gemma), ai.generatedBy)
        val turn = viewModel.state.value.turns.single()
        assertEquals(AiProvider.Gemma, turn.answer.provider)
        // Znovu se ptá na totéž, bez staré odpovědi v historii.
        assertFalse("Assistant: Odpověď" in ai.prompts.last())
    }

    /**
     * Dvě stažené velikosti Qwenu jsou v nabídce obě, i když Nastavení drží jen jednu; nestažená
     * velikost tam není, ledaže je to ta zvolená. Vybraná velikost pak opravdu odpovídá.
     */
    @Test
    fun nabidkaUkazeVsechnyStazeneVelikosti() = runTest {
        val models = AiModels(enabled = setOf(AiProvider.GeminiNano, AiProvider.Qwen), qwen = LiteRtModel.Qwen2_5_1_5B)
        val ai = FakeAi(downloaded = setOf(LiteRtModel.Qwen3_4B, LiteRtModel.Qwen3_0_6B))
        val viewModel = AiPlaygroundViewModel(ai, FakeSettings(models))
        backgroundScope.launch { viewModel.models.collect {} }
        backgroundScope.launch { viewModel.current.collect {} }
        advanceUntilIdle()

        val offered = viewModel.models.value
        assertEquals(
            listOf(models.variant(AiProvider.GeminiNano), LiteRtModel.Qwen3_4B.variant, LiteRtModel.Qwen2_5_1_5B.variant, LiteRtModel.Qwen3_0_6B.variant),
            offered.map { it.variant },
        )
        assertEquals(listOf(true, true, false, true), offered.map { it.ready })
        assertEquals(listOf(null, "Qwen3 4B", "Qwen2.5 1.5B", "Qwen3 0.6B"), offered.map { it.name })

        viewModel.choose(LiteRtModel.Qwen3_0_6B.variant)
        advanceUntilIdle()
        assertEquals(CurrentModel(LiteRtModel.Qwen3_0_6B.variant, automatic = false), viewModel.current.value)
        viewModel.send("Ahoj")
        advanceUntilIdle()
        viewModel.choose(LiteRtModel.Qwen3_4B.variant)
        viewModel.regenerate()
        advanceUntilIdle()

        assertEquals(listOf(LiteRtModel.Qwen3_0_6B.variant, LiteRtModel.Qwen3_4B.variant), ai.generatedVariants)
    }

    /** Velikost vypnutého modelu neplatí: odpovídá automatika. */
    @Test
    fun velikostVypnutehoModeluNeplati() = runTest {
        val ai = FakeAi(downloaded = setOf(LiteRtModel.Qwen3_0_6B))
        val viewModel = AiPlaygroundViewModel(ai, FakeSettings(nano))
        backgroundScope.launch { viewModel.models.collect {} }
        advanceUntilIdle()
        assertEquals(listOf(AiProvider.GeminiNano), viewModel.models.value.map { it.provider })

        viewModel.choose(LiteRtModel.Qwen3_0_6B.variant)
        viewModel.send("Ahoj")
        advanceUntilIdle()

        assertEquals(listOf(AiProvider.GeminiNano), ai.generatedBy)
    }

    @Test
    fun rychlostSeNemeriUKratkehoPsani() {
        val quick = PlaygroundAnswer(provider = AiProvider.Gemma, firstWordMs = 1_000, totalMs = 1_100, tokens = 5)
        assertNull(quick.tokensPerSecond)
        val measured = quick.copy(totalMs = 3_000, tokens = 40)
        assertEquals(20.0, measured.tokensPerSecond)
    }
}
