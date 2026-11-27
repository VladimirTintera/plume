package eu.tintera.plume.features.conversation

import eu.tintera.ai.core.DeviceLoad
import eu.tintera.plume.core.ai.AiModels
import eu.tintera.ai.core.AiFailureReason
import eu.tintera.plume.core.ai.AiProvider
import eu.tintera.ai.core.Generated
import eu.tintera.ai.core.ModelVariant
import eu.tintera.ai.core.OnDeviceAiException
import eu.tintera.ai.core.OnDeviceAiStatus
import eu.tintera.plume.core.ai.ReplySuggestionsOutput
import eu.tintera.ai.core.StructuredOutputException
import eu.tintera.plume.core.ai.UnimplementedOnDeviceAi
import eu.tintera.plume.core.settings.UnimplementedAppSettings
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.SendState
import kotlin.reflect.KClass
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
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

/** Strukturovaný výstup, kde ho model má, a cesta zpátky k textu, kde nevyjde. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplySuggestionsViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private class FakeSettings(models: AiModels = AiModels()) : UnimplementedAppSettings() {
        val enabled = MutableStateFlow(true)
        override val replySuggestionsEnabled: StateFlow<Boolean> = enabled
        override fun setReplySuggestionsEnabled(enabled: Boolean) {
            this.enabled.value = enabled
        }
        override val onDeviceAiModels: StateFlow<AiModels> = MutableStateFlow(models)
    }

    private object Strained : DeviceLoad {
        override val strained = true
    }

    /**
     * Model s volitelným strukturovaným výstupem. [structured] `null` = model ho nemá;
     * jinak se zavolá a jeho výsledek (nebo výjimka) jde ven.
     */
    private class FakeAi(
        private val structured: (() -> ReplySuggestionsOutput)?,
        private val text: String = "- Z textu",
    ) : UnimplementedOnDeviceAi() {
        val prompts = mutableListOf<String>()
        var textCalls = 0

        override suspend fun status(variant: ModelVariant) = OnDeviceAiStatus.Ready
        override suspend fun supportsStructuredOutput(variant: ModelVariant) = structured != null

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Any> generateStructured(variant: ModelVariant, prompt: String, type: KClass<T>): T {
            prompts += prompt
            return structured!!.invoke() as T
        }

        override fun generate(variant: ModelVariant, prompt: String): Flow<Generated> {
            textCalls++
            prompts += prompt
            return flowOf(Generated(text))
        }
    }

    private val incoming = ChatMessage(
        id = 1,
        isOutgoing = false,
        senderName = "Jana",
        senderAvatarFileId = null,
        senderId = 1L,
        text = "Dáš si kafe?",
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

    private fun ReplySuggestionsViewModel.suggestForIncoming() = suggest(1, "Jana", listOf(incoming), "Já")

    @Test
    fun strukturovanyVystupMaPrednost() = runTest {
        val ai = FakeAi(structured = { ReplySuggestionsOutput(listOf("Jasně", "Nemůžu")) })
        val viewModel = ReplySuggestionsViewModel(ai, FakeSettings())

        viewModel.suggestForIncoming()
        advanceUntilIdle()

        assertEquals(listOf("Jasně", "Nemůžu"), viewModel.suggestions.value)
        assertEquals(0, ai.textCalls, "s typy se text pouštět nemá")
        assertTrue(ai.prompts.single().contains("Dáš si kafe?"))
    }

    /** Návrhy se objevují samy: pod zátěží telefonu je Gemma nepočítá, Nano ano. */
    @Test
    fun podZatiziBezGemmy() = runTest {
        val gemma = FakeAi(structured = null)
        val onlyGemma = ReplySuggestionsViewModel(gemma, FakeSettings(AiModels(enabled = setOf(AiProvider.Gemma))), Strained)
        onlyGemma.suggestForIncoming()
        advanceUntilIdle()
        assertEquals(emptyList(), onlyGemma.suggestions.value)
        assertEquals(0, gemma.textCalls, "Gemma se pod zátěží pouštět nemá")

        val nano = FakeAi(structured = null)
        val withNano = ReplySuggestionsViewModel(nano, FakeSettings(), Strained)
        withNano.suggestForIncoming()
        advanceUntilIdle()
        assertEquals(listOf("Z textu"), withNano.suggestions.value)
    }

    /** Vypnutí z nabídky u čipů: čipy pryč hned, volba vypnutá, snackbar jednou; Vrátit ji zapne. */
    @Test
    fun vypnutiZNabidkyAVraceni() = runTest {
        val settings = FakeSettings()
        val viewModel = ReplySuggestionsViewModel(FakeAi(structured = null), settings)
        viewModel.suggestForIncoming()
        advanceUntilIdle()
        assertEquals(listOf("Z textu"), viewModel.suggestions.value)

        viewModel.turnOff()
        assertEquals(emptyList(), viewModel.suggestions.value)
        assertFalse(settings.enabled.value)
        assertTrue(viewModel.turnedOff.value)

        viewModel.consumeTurnedOff()
        assertFalse(viewModel.turnedOff.value)

        viewModel.turnOn()
        assertTrue(settings.enabled.value)
    }

    /** „Skrýt návrhy": k téže zprávě se znovu nenabízí, dokud obrazovka nezavolá `clear`. */
    @Test
    fun skrytiDrziIdZpravy() = runTest {
        val ai = FakeAi(structured = null)
        val viewModel = ReplySuggestionsViewModel(ai, FakeSettings())
        viewModel.suggestForIncoming()
        advanceUntilIdle()

        viewModel.hide()
        viewModel.suggestForIncoming()
        advanceUntilIdle()

        assertEquals(emptyList(), viewModel.suggestions.value)
        assertEquals(1, ai.textCalls, "k téže zprávě se model znovu pouštět nemá")
    }

    /**
     * Vložený návrh uživatel smaže: vrátí se tytéž čipy bez nového výpočtu. Dřív `clear`
     * zapomněl id a prázdné pole pustilo model znovu — s Aya vteřiny a jiné čipy.
     */
    @Test
    fun smazaniVlozenehoNavrhuVratiTytezCipy() = runTest {
        val ai = FakeAi(structured = null)
        val viewModel = ReplySuggestionsViewModel(ai, FakeSettings())
        viewModel.suggestForIncoming()
        advanceUntilIdle()

        viewModel.consume() // ťuknutí na čip
        viewModel.clear() // v poli je text
        viewModel.suggestForIncoming() // pole je zase prázdné
        advanceUntilIdle()

        assertEquals(listOf("Z textu"), viewModel.suggestions.value)
        assertEquals(1, ai.textCalls, "k téže zprávě se model znovu pouštět nemá")
    }

    /** Schování platí i po smazání pole; k další zprávě se nabízí zase. */
    @Test
    fun schovaniPlatiIPoSmazaniPole() = runTest {
        val ai = FakeAi(structured = null)
        val viewModel = ReplySuggestionsViewModel(ai, FakeSettings())
        viewModel.suggestForIncoming()
        advanceUntilIdle()

        viewModel.hide()
        viewModel.clear()
        viewModel.suggestForIncoming()
        advanceUntilIdle()
        assertEquals(emptyList(), viewModel.suggestions.value)

        viewModel.suggest(2, "Jana", listOf(incoming.copy(id = 2)), "Já")
        advanceUntilIdle()
        assertEquals(listOf("Z textu"), viewModel.suggestions.value)
        assertEquals(2, ai.textCalls)
    }

    /** Pamatuje se jen poslední zpráva: návrat k předchozí počítá znovu. */
    @Test
    fun jinaZpravaSePocitaZnovu() = runTest {
        val ai = FakeAi(structured = null)
        val viewModel = ReplySuggestionsViewModel(ai, FakeSettings())
        viewModel.suggestForIncoming()
        advanceUntilIdle()
        viewModel.suggest(2, "Jana", listOf(incoming.copy(id = 2)), "Já")
        advanceUntilIdle()
        viewModel.suggestForIncoming()
        advanceUntilIdle()

        assertEquals(3, ai.textCalls)
    }

    @Test
    fun bezStrukturovanehoVystupuJdeText() = runTest {
        val ai = FakeAi(structured = null)
        val viewModel = ReplySuggestionsViewModel(ai, FakeSettings())

        viewModel.suggestForIncoming()
        advanceUntilIdle()

        assertEquals(listOf("Z textu"), viewModel.suggestions.value)
    }

    @Test
    fun spatnyTvarSpadneNaText() = runTest {
        val ai = FakeAi(structured = { throw StructuredOutputException(code = 17) })
        val viewModel = ReplySuggestionsViewModel(ai, FakeSettings())

        viewModel.suggestForIncoming()
        advanceUntilIdle()

        assertEquals(listOf("Z textu"), viewModel.suggestions.value)
        assertEquals(1, ai.textCalls)
    }

    @Test
    fun prazdneStrukturovaneNavrhySpadnouNaText() = runTest {
        val ai = FakeAi(structured = { ReplySuggestionsOutput(listOf("   ")) })
        val viewModel = ReplySuggestionsViewModel(ai, FakeSettings())

        viewModel.suggestForIncoming()
        advanceUntilIdle()

        assertEquals(listOf("Z textu"), viewModel.suggestions.value)
    }

    @Test
    fun rozbityModelSeTextemNezkousi() = runTest {
        // Text by selhal stejně; hlášení rozbitého modelu nesmí spolknout pokus navíc.
        val ai = FakeAi(structured = { throw OnDeviceAiException(AiFailureReason.ModelError, ModelVariant.GeminiNano(), 5) })
        val viewModel = ReplySuggestionsViewModel(ai, FakeSettings())

        viewModel.suggestForIncoming()
        advanceUntilIdle()

        assertTrue(viewModel.suggestions.value.isEmpty())
        assertEquals(0, ai.textCalls)
    }
}
