package eu.tintera.plume.core.ai

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Jak dlouho držet Gemmu v paměti ([gemmaIdleUnloadMs]). */
class GemmaIdleTest {

    private fun ai(nano: OnDeviceAiStatus) = object : OnDeviceAi by NoopOnDeviceAi {
        override suspend fun status(variant: ModelVariant) =
            if (variant.provider == AiProvider.GeminiNano) nano else OnDeviceAiStatus.Ready
    }

    private val both = AiModels(enabled = setOf(AiProvider.GeminiNano, AiProvider.Gemma))

    /** Text počítá Nano, Gemma jen zvuk a obraz: pustit ji dřív. */
    @Test
    fun sNanoNaTextKratce() = runTest {
        assertEquals(GEMMA_IDLE_MEDIA_MS, ai(OnDeviceAiStatus.Ready).gemmaIdleUnloadMs(both))
    }

    @Test
    fun jedinaGemmaPocitaTextDlouho() = runTest {
        assertEquals(GEMMA_IDLE_TEXT_MS, ai(OnDeviceAiStatus.Unsupported).gemmaIdleUnloadMs(AiModels(enabled = setOf(AiProvider.Gemma))))
    }

    /** Nano se stahuje, text zatím převzala Gemma — chová se jako textová. */
    @Test
    fun nanoSeStahujeDlouho() = runTest {
        assertEquals(GEMMA_IDLE_TEXT_MS, ai(OnDeviceAiStatus.Downloading).gemmaIdleUnloadMs(both))
    }

    /** Stačí jedna textová úloha ručně na Gemmě. */
    @Test
    fun rucnePrirazenyTextDlouho() = runTest {
        val summaryOnGemma = both.copy(assigned = mapOf(AiTask.Summary to AiProvider.Gemma))
        assertEquals(GEMMA_IDLE_TEXT_MS, ai(OnDeviceAiStatus.Ready).gemmaIdleUnloadMs(summaryOnGemma))
    }
}
