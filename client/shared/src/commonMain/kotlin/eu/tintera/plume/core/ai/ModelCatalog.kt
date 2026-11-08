package eu.tintera.plume.core.ai

import eu.tintera.ai.core.DownloadedModel
import eu.tintera.ai.core.ModelRuntime
import eu.tintera.ai.core.ModelVariant
import eu.tintera.ai.core.huggingFaceUrl

/*
 * Katalog modelů je věc aplikace, ne knihovny: ai-engine zná runtime a stahuje, co dostane
 * ([DownloadedModel]), a žádný konkrétní model nejmenuje. Volby, úlohy a texty v Plume mluví
 * o poskytovatelích ([AiProvider]) a na knihovní [ModelVariant] se převádí na jednom místě
 * ([AiModels.variant], [provider]).
 */

/**
 * Kdo model počítá.
 *
 * - [GeminiNano]: systémový model v AICore. Stahuje a aktualizuje ho telefon, aplikace nic
 *   nebalí, ale je jen na některých zařízeních a ML Kit nepustí k větším variantám.
 * - [Gemma]: otevřený model, který si stáhne aplikace (gigabajty) a počítá ho přes LiteRT-LM.
 *   Běží na každém dost silném telefonu a jde vybrat i větší E4B.
 * - [Qwen]: druhý otevřený model přes tentýž runtime, jen na text. Je tu kvůli češtině — jestli
 *   ji umí líp než Gemma, má ukázat porovnání modelů na skutečné konverzaci.
 * - [EuroLlm]: evropský otevřený model (EuroLLM a EuroMoE od utter-project), trénovaný na všech
 *   jazycích EU včetně češtiny. Jen text, přes llama.cpp ([ModelRuntime.LlamaCpp]) — ve formátu
 *   LiteRT-LM není.
 * - [Aya]: vícejazyčný model Aya Expanse od Cohere Labs, 23 jazyků včetně češtiny. Jen text, přes
 *   llama.cpp. **Licence CC BY-NC 4.0** (jen nekomerčně, s uvedením autora) — aplikace ji má ukázat
 *   před stažením, protože licenci přijímá uživatel, který si model stahuje (Nastavení,
 *   `settings_aya_license`).
 *
 * **Jména se ukládají** (`on_device_ai_models`, přiřazení úloh), takže se nepřejmenovávají.
 */
enum class AiProvider {
    GeminiNano,
    Gemma,
    Qwen,
    EuroLlm,
    Aya,

    /**
     * Apple Intelligence (Foundation Models) na iOS 26+: systémový model jako Gemini Nano —
     * stahuje a aktualizuje ho systém, aplikace ho jen používá (`AppleOnDeviceAi`, Swift most).
     */
    Apple,
}

/**
 * Modely, které si aplikace umí stáhnout a počítá je sama — přes LiteRT-LM, nebo llama.cpp
 * ([runtime]). Jméno zůstalo z doby, kdy byl jen LiteRT-LM.
 *
 * **Commit je součást adresy**, takže se stahuje pořád tentýž soubor, i když autor na Hugging
 * Face nahraje novější — nová verze = nový řádek tady, vědomě. Pod [minMemoryGb] se model
 * nenabízí, protože by systém aplikaci při načítání zabil. **Jména položek se ukládají**
 * (nastavení aplikace, jméno úlohy stahování `ai-model-download-<jméno>`), takže se
 * nepřejmenovávají: [DownloadedModel.id] je přesně jméno položky.
 *
 * Pořadí položek téhož poskytovatele je pořadí, ve kterém se hledá náhrada za velikost, na
 * kterou zařízení nestačí (`supportedModels` v Nastavení). Qweny jdou od největšího, takže
 * telefon dostane největší, který unese; u Gemmy je první výchozí menší E2B.
 *
 * Co se u modelů naměřilo: tokeny na českém chatu s odkazy a čísly (`summaryPrompt`) Gemma 4 E2B
 * 2,2 znaku (2,3 bajtu) na token, Qwen3 2,0 znaku (2,2 bajtu); dřívější předpoklad tří znaků na
 * token (`transcriptCharsFor`) poslal u Gemmy 16 330 znaků = 7 547 tokenů do keše 8 192. Gemma 4
 * unese víc než 8 192 tokenů (Gallery uvádí kontext 32 000), ale keš stojí paměť úměrně délce
 * a 8 192 je totéž, co dává Gemini Nano, takže strop knihovny ji drží tam. Zvuk umí Gemma 4 (E2B
 * i E4B), Qwen ne; zvukový backend je jen CPU (pro Gemmu 3n „must be CPU" i v Gallery).
 *
 * @property provider komu model patří: volba velikosti v Nastavení je jen mezi modely téhož.
 * @property multimodal bere i zvuk a obraz (přepis hlasovek, popis fotek), ne jen text.
 * @property contextTokens kolik tokenů (vstup i výstup dohromady) soubor unese; víc engine
 *   nedostane, ani když by aplikace chtěla delší kontext.
 * @property runtime čím se soubor počítá.
 */
enum class LiteRtModel(
    val displayName: String,
    val provider: AiProvider,
    val repository: String,
    val fileName: String,
    val commit: String,
    val sizeBytes: Long,
    val minMemoryGb: Int,
    val multimodal: Boolean,
    val contextTokens: Int = 8_192,
    val runtime: ModelRuntime = ModelRuntime.LiteRt,
) {
    // Gemma: soubor, commit, velikost i paměť z katalogu Google AI Edge Gallery
    // (`model_allowlists/1_0_19.json`).
    E2B(
        displayName = "Gemma 4 E2B",
        provider = AiProvider.Gemma,
        repository = "litert-community/gemma-4-E2B-it-litert-lm",
        fileName = "gemma-4-E2B-it.litertlm",
        commit = "6e5c4f1e395deb959c494953478fa5cec4b8008f",
        sizeBytes = 2_588_147_712,
        minMemoryGb = 8,
        multimodal = true,
    ),
    E4B(
        displayName = "Gemma 4 E4B",
        provider = AiProvider.Gemma,
        repository = "litert-community/gemma-4-E4B-it-litert-lm",
        fileName = "gemma-4-E4B-it.litertlm",
        commit = "28299f30ee4d43294517a4ac93abd6163412f07f",
        sizeBytes = 3_659_530_240,
        minMemoryGb = 12,
        multimodal = true,
    ),

    /*
     * Qwen3 4B v Gallery není; soubor je z litert-community (Apache 2.0, bez souhlasu s licencí).
     * Dynamický INT4 s GPU kompozity (export z 31. 10. 2026) potřebuje LiteRT-LM 0.19 — se
     * starší verzí GPU engine na souboru s vnějšími vahami spadne (LiteRT-LM#3986). Paměť podle
     * karty modelu: na 8GB Pixelu 8a při prvním výpočtu došla, proto 12 GB jako u E4B.
     */
    Qwen3_4B(
        displayName = "Qwen3 4B",
        provider = AiProvider.Qwen,
        repository = "litert-community/Qwen3-4B",
        fileName = "qwen3_4b_mixed_int4.litertlm",
        commit = "2e62465be458fb02f7a92e339f98270f42dc9b32",
        sizeBytes = 2_272_935_936,
        minMemoryGb = 12,
        multimodal = false,
        contextTokens = 4_096,
    ),

    /** Qwen2.5 1,5B: soubor, commit, velikost i paměť z katalogu Gallery (`1_0_23.json`). */
    Qwen2_5_1_5B(
        displayName = "Qwen2.5 1.5B",
        provider = AiProvider.Qwen,
        repository = "litert-community/Qwen2.5-1.5B-Instruct",
        fileName = "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
        commit = "5e6e7a58d03777898b31330a15d2e0a5a9107de3",
        sizeBytes = 1_597_922_856,
        minMemoryGb = 6,
        multimodal = false,
        contextTokens = 4_096,
    ),

    /*
     * Qwen3 0,6B z litert-community, týž export jako Qwen3 4B (INT4, GPU kompozity, LiteRT-LM
     * 0.19). Paměť: proces s ním podle karty modelu na Pixelu 8a sahá na ~1,9 GB, proto 4 GB.
     */
    Qwen3_0_6B(
        displayName = "Qwen3 0.6B",
        provider = AiProvider.Qwen,
        repository = "litert-community/Qwen3-0.6B",
        fileName = "Qwen3-0.6B_dynamic_wi4b32_afp32.litertlm",
        commit = "7c650468cc1598861b1a0deb0ab25be6b20cab13",
        sizeBytes = 343_490_560,
        minMemoryGb = 4,
        multimodal = false,
        contextTokens = 4_096,
    ),

    /*
     * EuroLLM 9B Instruct (verze 2512) od utter-project, Apache 2.0, GGUF Q4_K_M od mradermacher
     * (originál je za souhlasem, kvantizace ne). Kontext 4 096 kvůli paměti: KV keš 9B modelu roste
     * s délkou a soubor sám má 5,6 GB. Paměť 16 GB — 12GB telefon by po načtení měl na zbytek
     * systému a aplikace jen pár GB.
     */
    EuroLlm9B(
        displayName = "EuroLLM 9B",
        provider = AiProvider.EuroLlm,
        repository = "mradermacher/EuroLLM-9B-Instruct-2512-GGUF",
        fileName = "EuroLLM-9B-Instruct-2512.Q4_K_M.gguf",
        commit = "5138603fccb9dc1f35c25cfd1a4981a78617e9ff",
        sizeBytes = 5_582_838_912,
        minMemoryGb = 16,
        multimodal = false,
        contextTokens = 4_096,
        runtime = ModelRuntime.LlamaCpp,
    ),

    /*
     * EuroMoE 2,6B (aktivních 0,6B) Instruct 2512 od utter-project: směs expertů, takže počítá
     * rychlostí malého modelu a zná jazyky jako větší. Apache 2.0, GGUF Q4_K_M od mradermacher.
     */
    EuroMoe2_6B(
        displayName = "EuroMoE 2.6B",
        provider = AiProvider.EuroLlm,
        repository = "mradermacher/EuroMoE-2.6B-A0.6B-Instruct-2512-GGUF",
        fileName = "EuroMoE-2.6B-A0.6B-Instruct-2512.Q4_K_M.gguf",
        commit = "11be4a9e68eb5aba18e4c3b341cbef2d018b2da0",
        sizeBytes = 1_616_056_480,
        minMemoryGb = 6,
        multimodal = false,
        contextTokens = 4_096,
        runtime = ModelRuntime.LlamaCpp,
    ),

    /*
     * Aya Expanse 8B od Cohere Labs (architektura Command R), CC BY-NC 4.0 a pravidla použití
     * Cohere Labs. GGUF Q4_K_M od bartowski — originál je za souhlasem a sdílením kontaktu,
     * kvantizace ne; licence nekomerční šíření s uvedením autora dovoluje. Jediná velikost
     * (druhá je 32B), paměť a kontext jako EuroLLM 9B: soubor má 5,1 GB.
     */
    AyaExpanse8B(
        displayName = "Aya Expanse 8B",
        provider = AiProvider.Aya,
        repository = "bartowski/aya-expanse-8b-GGUF",
        fileName = "aya-expanse-8b-Q4_K_M.gguf",
        commit = "f9d62ed0c58e6f2ae17975df990b1b8a4013b596",
        sizeBytes = 5_056_982_720,
        minMemoryGb = 16,
        multimodal = false,
        contextTokens = 4_096,
        runtime = ModelRuntime.LlamaCpp,
    ),
    ;

    /** Totéž pro knihovnu. Hodnota je stálá, stav stahování i engine jsou v jejích mapách podle modelu. */
    val downloaded: DownloadedModel = DownloadedModel(
        id = name,
        displayName = displayName,
        url = huggingFaceUrl(repo = repository, commit = commit, file = fileName),
        fileName = fileName,
        sizeBytes = sizeBytes,
        minMemoryGb = minMemoryGb,
        runtime = runtime,
        contextTokens = contextTokens,
        multimodal = multimodal,
    )

    /** Varianta, která tenhle soubor počítá — na dotazy po souborech (Úložiště, dostupnost). */
    val variant: ModelVariant get() = downloaded.variant
}

/** Položka katalogu podle [DownloadedModel.id]; `null`, když model z katalogu zmizel (stará úloha stahování). */
fun liteRtModelById(id: String): LiteRtModel? = LiteRtModel.entries.firstOrNull { it.name == id }

/**
 * Poskytovatel staženého modelu. **Do knihovny jdou jen modely z katalogu** ([LiteRtModel.downloaded]),
 * takže model mimo něj je chyba v kódu, ne stav, který by se dal obsloužit.
 */
val DownloadedModel.provider: AiProvider
    get() = checkNotNull(liteRtModelById(id)) { "Model mimo katalog: $id" }.provider

/** Poskytovatel varianty, opak [AiModels.variant]. */
val ModelVariant.provider: AiProvider
    get() = when (this) {
        is ModelVariant.GeminiNano -> AiProvider.GeminiNano
        ModelVariant.Apple -> AiProvider.Apple
        is ModelVariant.Downloaded -> model.provider
    }
