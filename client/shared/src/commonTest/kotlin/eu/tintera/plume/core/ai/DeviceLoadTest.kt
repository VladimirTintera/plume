package eu.tintera.plume.core.ai

import eu.tintera.ai.core.DeviceLoad
import eu.tintera.ai.core.ModelVariant
import eu.tintera.ai.core.NoDeviceLoad
import eu.tintera.ai.core.NoopOnDeviceAi
import eu.tintera.ai.core.OnDeviceAi
import eu.tintera.ai.core.OnDeviceAiStatus
import eu.tintera.ai.core.avoidUnderLoad
import eu.tintera.plume.core.settings.UnimplementedAppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Úlohy, které běží samy, pod zátěží telefonu bez Gemmy ([proactiveVariant]). */
class DeviceLoadTest {

    private val nano = ModelVariant.GeminiNano()
    private val gemma = LiteRtModel.E2B.variant

    @Test
    fun podZatiziJenGemmaOdpadne() {
        assertNull(avoidUnderLoad(gemma, strained = true))
        assertEquals(nano, avoidUnderLoad(nano, strained = true))
        assertEquals(gemma, avoidUnderLoad(gemma, strained = false))
        assertNull(avoidUnderLoad(LiteRtModel.Qwen3_4B.variant, strained = true))
        assertEquals(ModelVariant.Apple, avoidUnderLoad(ModelVariant.Apple, strained = true))
    }

    private class Settings(models: AiModels) : UnimplementedAppSettings() {
        override val onDeviceAiModels: StateFlow<AiModels> = MutableStateFlow(models)
    }

    private object Strained : DeviceLoad {
        override val strained = true
    }

    private val ready = object : OnDeviceAi by NoopOnDeviceAi {
        override suspend fun status(variant: ModelVariant) = OnDeviceAiStatus.Ready
    }

    /**
     * Ručně přiřazená Gemma pod zátěží taky počká — úsporný režim je taky volba uživatele.
     * Popis fotek na pozadí umí jen Gemma, takže pod zátěží nejede vůbec.
     */
    @Test
    fun rucniPrirazeniTakyPocka() = runTest {
        val both = AiModels(enabled = setOf(AiProvider.GeminiNano, AiProvider.Gemma))
        assertNull(ready.proactiveVariant(AiTask.Replies, Settings(both.copy(assigned = mapOf(AiTask.Replies to AiProvider.Gemma))), Strained))
        assertEquals(AiProvider.GeminiNano, ready.proactiveVariant(AiTask.Replies, Settings(both), Strained)?.provider)
        assertNull(ready.proactiveVariant(AiTask.PhotoDescription, Settings(both), Strained))
        assertEquals(AiProvider.Gemma, ready.proactiveVariant(AiTask.PhotoDescription, Settings(both), NoDeviceLoad)?.provider)
    }
}
