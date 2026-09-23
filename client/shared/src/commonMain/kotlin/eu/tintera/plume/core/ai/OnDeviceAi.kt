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
 */
interface OnDeviceAi {

    /** V jakém stavu je model. Levný dotaz na systém, nic nestahuje. */
    suspend fun status(): OnDeviceAiStatus

    /** Název modelu, jak ho hlásí systém (u Gemini Nano i jeho verze). `null`, když není. */
    suspend fun modelName(): String?

    /**
     * Stáhne model a hlásí průběh. Tok skončí po dokončení, selhání se vyhodí jako výjimka.
     * Volání ve stavu [OnDeviceAiStatus.Downloading] se připojí k běžícímu stahování.
     */
    fun download(): Flow<ModelDownload>

    /**
     * Vygeneruje odpověď na [prompt] a vydává ji postupně.
     *
     * Každá hodnota je CELÝ dosavadní text, ne přírůstek — obrazovka ho jen vykreslí a nemusí
     * nic skládat.
     */
    fun generate(prompt: String): Flow<String>
}

enum class OnDeviceAiStatus {
    /** Zařízení model nemá a mít nebude (bez AICore, nepodporovaný čip, iOS). */
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
    override suspend fun status() = OnDeviceAiStatus.Unsupported
    override suspend fun modelName(): String? = null
    override fun download(): Flow<ModelDownload> = emptyFlow()
    override fun generate(prompt: String): Flow<String> = emptyFlow()
}
