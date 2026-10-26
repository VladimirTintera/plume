package eu.tintera.plume.features.conversation

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ai.AiModels
import eu.tintera.plume.core.ai.AiProvider
import eu.tintera.plume.core.ai.Generated
import eu.tintera.plume.core.ai.ModelVariant
import eu.tintera.plume.core.ai.NoopOnDeviceAi
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.ai.OnDeviceAiStatus
import eu.tintera.plume.core.settings.UnimplementedAppSettings
import eu.tintera.plume.core.telegram.OutgoingAttachment
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Návrh popisku fotky nad polem ([ConversationCaption]). */
@OptIn(ExperimentalCoroutinesApi::class)
class ConversationCaptionTest {

    // Sběrač stavu běží v `backgroundScope`, a tu `runCurrent()` nepouští — vrátí se,
    // jakmile zbývá jen práce na pozadí. Proto `runCurrent()`.

    private class Settings(models: AiModels, enabled: Boolean = true) : UnimplementedAppSettings() {
        override val onDeviceAiEnabled: StateFlow<Boolean> = MutableStateFlow(enabled)
        override val onDeviceAiModels: StateFlow<AiModels> = MutableStateFlow(models)
    }

    /** Obraz bere jen Gemma, jako ve skutečnosti. */
    private class FakeAi(val answer: String = "„Západ slunce nad Vltavou“") : OnDeviceAi by NoopOnDeviceAi {
        val prompts = mutableListOf<String>()
        override suspend fun status(variant: ModelVariant) = OnDeviceAiStatus.Ready
        override suspend fun canDescribeImage(variant: ModelVariant) = variant.provider == AiProvider.Gemma
        override fun describeImage(variant: ModelVariant, imagePath: String, languageCode: String, prompt: String): Flow<Generated> {
            prompts += prompt
            return flowOf(Generated(answer))
        }
    }

    private val photo = OutgoingAttachment.Photo(path = "/cache/fotka.jpg", width = 10, height = 10)
    private val both = AiModels(enabled = setOf(AiProvider.GeminiNano, AiProvider.Gemma))

    @Test
    fun navrhJdeDoPrazdnehoPole() = runTest {
        val state = MutableStateFlow(ConversationState())
        val ai = FakeAi()
        val caption = ConversationCaption(state, backgroundScope, Settings(both), ai, Logger)
        runCurrent()
        assertEquals(CaptionSuggestion.Hidden, state.value.captionSuggestion)

        state.update { it.copy(attachments = listOf(photo)) }
        runCurrent()
        assertEquals(CaptionSuggestion.Available, state.value.captionSuggestion)

        caption.suggest()
        runCurrent()
        assertEquals(InputFill("Západ slunce nad Vltavou", onlyIfEmpty = true), state.value.inputFill)
        assertEquals(CaptionSuggestion.Available, state.value.captionSuggestion)
        assertTrue("caption" in ai.prompts.single())
    }

    /** Jen s Nano (obraz nebere), vypnutou AI, nebo když první příloha fotka není: nic. */
    @Test
    fun bezModeluNaObrazSeNenabizi() = runTest {
        for ((settings, attachments) in listOf(
            Settings(AiModels(enabled = setOf(AiProvider.GeminiNano))) to listOf(photo),
            Settings(both, enabled = false) to listOf(photo),
            Settings(both) to listOf(OutgoingAttachment.Document(path = "/cache/a.pdf")),
        )) {
            val state = MutableStateFlow(ConversationState(attachments = attachments))
            ConversationCaption(state, backgroundScope, settings, FakeAi(), Logger)
            runCurrent()
            assertEquals(CaptionSuggestion.Hidden, state.value.captionSuggestion)
        }
    }

    /** Fotka odebraná, než model dopsal: návrh do pole nejde. */
    @Test
    fun odebranaFotkaNavrhNedostane() = runTest {
        val state = MutableStateFlow(ConversationState(attachments = listOf(photo)))
        val caption = ConversationCaption(state, backgroundScope, Settings(both), FakeAi(), Logger)
        runCurrent()
        caption.suggest()
        state.update { it.copy(attachments = emptyList()) }
        runCurrent()
        assertNull(state.value.inputFill)
        assertEquals(CaptionSuggestion.Hidden, state.value.captionSuggestion)
    }
}
