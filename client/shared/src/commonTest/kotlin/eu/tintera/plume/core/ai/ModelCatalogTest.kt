package eu.tintera.plume.core.ai

import eu.tintera.ai.core.ModelRuntime
import eu.tintera.ai.core.ModelVariant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Katalog stahovaných modelů a převod na typy knihovny ([LiteRtModel], [AiModels.variant]). */
class ModelCatalogTest {

    /**
     * Id se ukládají (volba velikosti v nastavení) a jsou ve jméně rozběhnuté úlohy stahování
     * (`ai-model-download-<id>`) — přejmenování by volbu i úlohu ztratilo. Mění-li se tenhle test,
     * je to migrace, ne oprava testu.
     */
    @Test
    fun idSeNemeni() {
        val ids = listOf("E2B", "E4B", "Qwen3_4B", "Qwen2_5_1_5B", "Qwen3_0_6B", "EuroLlm9B", "EuroMoe2_6B", "AyaExpanse8B")
        assertEquals(ids, LiteRtModel.entries.map { it.name })
        assertEquals(ids, LiteRtModel.entries.map { it.downloaded.id })
    }

    /** Jména poskytovatelů se ukládají taky (zapnuté modely, přiřazení úloh). */
    @Test
    fun jmenaPoskytovateluSeNemeni() {
        assertEquals(
            listOf("GeminiNano", "Gemma", "Qwen", "EuroLlm", "Aya", "Apple"),
            AiProvider.entries.map { it.name },
        )
    }

    /**
     * Adresy na bajt: commit drží stažený soubor na verzi z katalogu a rozstažený `.part` navazuje
     * jen na tentýž soubor. Nová verze modelu je vědomá změna tady.
     */
    @Test
    fun presnyTvarAdresy() {
        assertEquals(
            mapOf(
                LiteRtModel.E2B to "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/6e5c4f1e395deb959c494953478fa5cec4b8008f/gemma-4-E2B-it.litertlm?download=true",
                LiteRtModel.E4B to "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/28299f30ee4d43294517a4ac93abd6163412f07f/gemma-4-E4B-it.litertlm?download=true",
                LiteRtModel.Qwen3_4B to "https://huggingface.co/litert-community/Qwen3-4B/resolve/2e62465be458fb02f7a92e339f98270f42dc9b32/qwen3_4b_mixed_int4.litertlm?download=true",
                LiteRtModel.Qwen2_5_1_5B to "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/5e6e7a58d03777898b31330a15d2e0a5a9107de3/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm?download=true",
                LiteRtModel.Qwen3_0_6B to "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/7c650468cc1598861b1a0deb0ab25be6b20cab13/Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm?download=true",
                LiteRtModel.EuroLlm9B to "https://huggingface.co/mradermacher/EuroLLM-9B-Instruct-2512-GGUF/resolve/5138603fccb9dc1f35c25cfd1a4981a78617e9ff/EuroLLM-9B-Instruct-2512.Q4_K_M.gguf?download=true",
                LiteRtModel.EuroMoe2_6B to "https://huggingface.co/mradermacher/EuroMoE-2.6B-A0.6B-Instruct-2512-GGUF/resolve/11be4a9e68eb5aba18e4c3b341cbef2d018b2da0/EuroMoE-2.6B-A0.6B-Instruct-2512.Q4_K_M.gguf?download=true",
                LiteRtModel.AyaExpanse8B to "https://huggingface.co/bartowski/aya-expanse-8b-GGUF/resolve/f9d62ed0c58e6f2ae17975df990b1b8a4013b596/aya-expanse-8b-Q4_K_M.gguf?download=true",
            ),
            LiteRtModel.entries.associateWith { it.downloaded.url },
        )
    }

    /** Knihovna dostane totéž, co stojí v katalogu. */
    @Test
    fun knihovnaDostaneHodnotyZKatalogu() {
        LiteRtModel.entries.forEach { model ->
            val downloaded = model.downloaded
            assertEquals(model.displayName, downloaded.displayName)
            assertEquals(model.fileName, downloaded.fileName)
            assertEquals(model.sizeBytes, downloaded.sizeBytes)
            assertEquals(model.minMemoryGb, downloaded.minMemoryGb)
            assertEquals(model.runtime, downloaded.runtime)
            assertEquals(model.contextTokens, downloaded.contextTokens)
            assertEquals(model.multimodal, downloaded.multimodal)
        }
    }

    /** Varianta souboru vede zpátky na tentýž soubor — Úložiště podle ní maže. */
    @Test
    fun variantaVedeNaSvujSoubor() {
        LiteRtModel.entries.forEach { model ->
            assertEquals(model.downloaded, model.variant.model)
            assertEquals(model.provider, model.variant.provider)
        }
    }

    /** Úloha stahování po zabití procesu zná jen id. */
    @Test
    fun modelPodleId() {
        LiteRtModel.entries.forEach { assertEquals(it, liteRtModelById(it.downloaded.id)) }
        assertNull(liteRtModelById("Zmizely"))
    }

    /** Poskytovatel a varianta jsou navzájem opačné převody, volby jdou jen tomu, komu patří. */
    @Test
    fun variantaPodlePoskytovatele() {
        val models = AiModels(
            preview = true,
            fast = true,
            gemma = LiteRtModel.E4B,
            qwen = LiteRtModel.Qwen3_0_6B,
            euroLlm = LiteRtModel.EuroMoe2_6B,
        )
        AiProvider.entries.forEach { assertEquals(it, models.variant(it).provider) }
        assertEquals(ModelVariant.GeminiNano(preview = true, fast = true), models.variant(AiProvider.GeminiNano))
        assertEquals(ModelVariant.Apple, models.variant(AiProvider.Apple))
        assertEquals(LiteRtModel.E4B.variant, models.variant(AiProvider.Gemma))
        assertEquals(LiteRtModel.Qwen3_0_6B.variant, models.variant(AiProvider.Qwen))
        assertEquals(LiteRtModel.EuroMoe2_6B.variant, models.variant(AiProvider.EuroLlm))
        assertEquals(LiteRtModel.AyaExpanse8B.variant, models.variant(AiProvider.Aya))
    }

    /** Zvuk a obraz jen u Gemmy; Qwen se přepisu ani popisu fotky nenabídne. */
    @Test
    fun multimodalniJenGemma() {
        LiteRtModel.entries.forEach { assertEquals(it.provider == AiProvider.Gemma, it.multimodal) }
    }

    /** Qweny i EuroLLM jdou od největšího — náhrada za nedostupnou velikost bere první, která jde. */
    @Test
    fun velikostiOdNejvetsi() {
        listOf(AiProvider.Qwen, AiProvider.EuroLlm).forEach { provider ->
            val sizes = LiteRtModel.entries.filter { it.provider == provider }.map { it.minMemoryGb }
            assertEquals(sizes.sortedDescending(), sizes, "$provider")
        }
    }

    /** EuroLLM a Aya jsou GGUF pro llama.cpp, Gemma a Qwen soubory LiteRT-LM — runtime se podle toho doinstaluje. */
    @Test
    fun runtimePodlePoskytovatele() {
        LiteRtModel.entries.forEach { model ->
            val gguf = model.provider == AiProvider.EuroLlm || model.provider == AiProvider.Aya
            val expected = if (gguf) ModelRuntime.LlamaCpp else ModelRuntime.LiteRt
            assertEquals(expected, model.runtime, model.name)
            assertEquals(expected == ModelRuntime.LlamaCpp, model.fileName.endsWith(".gguf"), model.name)
        }
    }
}
