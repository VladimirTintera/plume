package eu.tintera.plume.core.ai

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Katalog stahovaných modelů a jejich varianty ([LiteRtModel], [ModelVariant.model]). */
class LiteRtModelTest {

    /** Varianta souboru vede zpátky na tentýž soubor — Úložiště podle ní maže. */
    @Test
    fun variantaVedeNaSvujSoubor() {
        LiteRtModel.entries.forEach { assertEquals(it, it.variant.model) }
    }

    @Test
    fun systemovyModelNemaSoubor() {
        assertNull(ModelVariant(provider = AiProvider.GeminiNano).model)
        assertNull(ModelVariant(provider = AiProvider.Apple).model)
        assertEquals(false, ModelVariant(provider = AiProvider.GeminiNano).appManaged)
        assertEquals(true, ModelVariant(provider = AiProvider.Qwen).appManaged)
    }

    /** Zvuk a obraz jen u Gemmy; Qwen se přepisu ani popisu fotky nenabídne. */
    @Test
    fun multimodalniJenGemma() {
        LiteRtModel.entries.forEach { assertEquals(it.provider == AiProvider.Gemma, it.multimodal) }
    }

    /** Qweny jdou od největšího — náhrada za nedostupnou velikost bere první, která jde. */
    @Test
    fun qwenyOdNejvetsiho() {
        val sizes = LiteRtModel.entries.filter { it.provider == AiProvider.Qwen }.map { it.minMemoryGb }
        assertEquals(sizes.sortedDescending(), sizes)
    }

    /** Jména se ukládají (volba velikosti, úloha stahování) — přejmenování by volbu ztratilo. */
    @Test
    fun jmenaPolozekSeNemeni() {
        assertEquals(
            listOf("E2B", "E4B", "Qwen3_4B", "Qwen2_5_1_5B", "Qwen3_0_6B"),
            LiteRtModel.entries.map { it.name },
        )
    }
}
