package eu.tintera.plume.core.ai

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Jazykový model běžící v zařízení.
 *
 * Hranice stejně jako `core/telegram` nebo `core/logging`: na Androidu je za ní Gemini Nano
 * v AICore přes ML Kit Prompt API a `com.google.mlkit.genai.*` smí být importované jen
 * v `androidMain/core/ai`. Zbytek aplikace vidí tohle rozhraní, takže jde model vyměnit
 * (Gemma přes LiteRT-LM, Apple Foundation Models na iOS) bez zásahu do obrazovek.
 *
 * Rozhraní, ne `expect class`: ViewModel se pak dá testovat s podstrčeným modelem.
 *
 * Každé volání bere [ModelVariant] — varianty jsou pro AICore různé modely, každá se stahuje
 * zvlášť a má vlastní stav i limit.
 */
interface OnDeviceAi {

    /** V jakém stavu je model. Levný dotaz na systém, nic nestahuje. */
    suspend fun status(variant: ModelVariant): OnDeviceAiStatus

    /** Název a limit vstupu; `null`, když model v zařízení není (limit zná až stažený model). */
    suspend fun info(variant: ModelVariant): ModelInfo?

    /**
     * Stáhne model a hlásí průběh. Tok skončí po dokončení, selhání se vyhodí jako výjimka.
     * Volání ve stavu [OnDeviceAiStatus.Downloading] se připojí k běžícímu stahování.
     */
    fun download(variant: ModelVariant): Flow<ModelDownload>

    /** Kolik tokenů zabere [prompt] na vstupu; `null`, když to model neumí říct. */
    suspend fun countTokens(variant: ModelVariant, prompt: String): Int?

    /**
     * Vygeneruje odpověď na [prompt] a vydává ji postupně.
     *
     * Každá hodnota je CELÝ dosavadní text, ne přírůstek — obrazovka ho jen vykreslí a nemusí
     * nic skládat.
     */
    fun generate(variant: ModelVariant, prompt: String): Flow<String>
}

/**
 * Kterou podobu modelu chceme. Obě volby jsou jen PŘÁNÍ — co opravdu běží, řekne [ModelInfo.name].
 *
 * @property preview nejnovější verze v předběžném vydání místo stabilní. Bývá jen na novějších
 *   zařízeních a stahuje se zvlášť.
 * @property fast rychlejší a méně přesná varianta místo plné.
 */
data class ModelVariant(val preview: Boolean = false, val fast: Boolean = false)

/** Co o modelu víme. [tokenLimit] je strop VSTUPU v tokenech. */
data class ModelInfo(val name: String?, val tokenLimit: Int?)

enum class OnDeviceAiStatus {
    /** Zařízení model (v téhle variantě) nemá a mít nebude (bez AICore, nepodporovaný čip, iOS). */
    Unsupported,

    /** Model jde stáhnout. */
    Downloadable,

    /** Model se právě stahuje. */
    Downloading,

    /** Model je v zařízení a jde hned použít. */
    Ready,
}

/** Průběh stahování modelu. [totalBytes] je `null`, dokud ho systém neohlásí. */
data class ModelDownload(val downloadedBytes: Long, val totalBytes: Long?) {
    val fraction: Float?
        get() = totalBytes?.takeIf { it > 0 }?.let { (downloadedBytes.toFloat() / it).coerceIn(0f, 1f) }
}

/** Platforma bez modelu v zařízení (zatím iOS). Funkce se pak v UI vůbec nenabízí. */
object NoopOnDeviceAi : OnDeviceAi {
    override suspend fun status(variant: ModelVariant) = OnDeviceAiStatus.Unsupported
    override suspend fun info(variant: ModelVariant): ModelInfo? = null
    override fun download(variant: ModelVariant): Flow<ModelDownload> = emptyFlow()
    override suspend fun countTokens(variant: ModelVariant, prompt: String): Int? = null
    override fun generate(variant: ModelVariant, prompt: String): Flow<String> = emptyFlow()
}
