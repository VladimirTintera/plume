package eu.tintera.plume.features.settings

import eu.tintera.plume.core.ai.AiProvider
import eu.tintera.plume.core.ai.GemmaModel
import eu.tintera.plume.core.ai.ModelVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Uložená volba modelu, kterou zařízení nesplní, se opraví na dostupnou. */
class SupportedVariantTest {

    private val everything = AiAvailability(AiProvider.entries, GemmaModel.entries)

    @Test
    fun dostupnaVolbaSeNemeni() {
        assertNull(supportedVariant(ModelVariant(provider = AiProvider.Gemma, gemma = GemmaModel.E4B), everything))
    }

    @Test
    fun bezAicoreSePrepneNaGemmu() {
        val onlyGemma = AiAvailability(listOf(AiProvider.Gemma), GemmaModel.entries)
        assertEquals(
            ModelVariant(provider = AiProvider.Gemma, fast = true),
            supportedVariant(ModelVariant(provider = AiProvider.GeminiNano, fast = true), onlyGemma),
        )
    }

    @Test
    fun maloPametiVratiMensiGemmu() {
        val smallOnly = AiAvailability(AiProvider.entries, listOf(GemmaModel.E2B))
        assertEquals(
            ModelVariant(provider = AiProvider.Gemma, gemma = GemmaModel.E2B),
            supportedVariant(ModelVariant(provider = AiProvider.Gemma, gemma = GemmaModel.E4B), smallOnly),
        )
    }

    @Test
    fun bezPoskytoveleSeNicNemeni() {
        // Sekce se pak nenabízí vůbec; přepisovat uloženou volbu není proč.
        assertNull(supportedVariant(ModelVariant(), AiAvailability(emptyList(), emptyList())))
    }
}
