package eu.tintera.plume.features.settings

import eu.tintera.plume.core.ai.AiModels
import eu.tintera.plume.core.ai.AiProvider
import eu.tintera.plume.core.ai.AiTask
import eu.tintera.plume.core.ai.LiteRtModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Uložené modely, které zařízení nesplní, se opraví na dostupné. */
class SupportedModelsTest {

    private val everything = AiAvailability(AiProvider.entries, LiteRtModel.entries)

    @Test
    fun dostupnaVolbaSeNemeni() {
        val both = AiModels(enabled = setOf(AiProvider.GeminiNano, AiProvider.Gemma), gemma = LiteRtModel.E4B)
        assertNull(supportedModels(both, everything))
    }

    @Test
    fun bezAicoreSePrepneNaGemmu() {
        val onlyGemma = AiAvailability(listOf(AiProvider.Gemma), LiteRtModel.entries)
        assertEquals(
            AiModels(enabled = setOf(AiProvider.Gemma), fast = true),
            supportedModels(AiModels(enabled = setOf(AiProvider.GeminiNano), fast = true), onlyGemma),
        )
    }

    /** Z obou zapnutých zůstane ten, který tu je — nepřidá se žádný další. */
    @Test
    fun nedostupnyModelSeVypustiZeZapnutych() {
        val onlyGemma = AiAvailability(listOf(AiProvider.Gemma), LiteRtModel.entries)
        assertEquals(
            AiModels(enabled = setOf(AiProvider.Gemma)),
            supportedModels(AiModels(enabled = setOf(AiProvider.GeminiNano, AiProvider.Gemma)), onlyGemma),
        )
    }

    /** Výchozí Gemini Nano na iOS: jediný model je tam Apple Intelligence. */
    @Test
    fun naIosZbudeApple() {
        val apple = AiAvailability(listOf(AiProvider.Apple), emptyList())
        assertEquals(AiModels(enabled = setOf(AiProvider.Apple)), supportedModels(AiModels(), apple))
    }

    @Test
    fun maloPametiVratiMensiGemmu() {
        val smallOnly = AiAvailability(AiProvider.entries, listOf(LiteRtModel.E2B))
        assertEquals(
            AiModels(enabled = setOf(AiProvider.Gemma), gemma = LiteRtModel.E2B),
            supportedModels(AiModels(enabled = setOf(AiProvider.Gemma), gemma = LiteRtModel.E4B), smallOnly),
        )
    }

    /** Přiřazení k chybějícímu modelu se nemaže — nepočítá se, dokud model není zapnutý. */
    @Test
    fun prirazeniZustava() {
        val onlyNano = AiAvailability(listOf(AiProvider.GeminiNano), emptyList())
        val models = AiModels(
            enabled = setOf(AiProvider.GeminiNano, AiProvider.Gemma),
            assigned = mapOf(AiTask.Summary to AiProvider.Gemma),
        )
        val fixed = supportedModels(models, onlyNano)
        assertEquals(mapOf(AiTask.Summary to AiProvider.Gemma), fixed?.assigned)
        assertNull(fixed?.assignment(AiTask.Summary))
    }

    @Test
    fun bezPoskytoveleSeNicNemeni() {
        // Sekce se pak nenabízí vůbec; přepisovat uloženou volbu není proč.
        assertNull(supportedModels(AiModels(), AiAvailability(emptyList(), emptyList())))
    }
}
