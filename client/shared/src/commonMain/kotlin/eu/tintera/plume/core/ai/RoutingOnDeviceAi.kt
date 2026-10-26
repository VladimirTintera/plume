package eu.tintera.plume.core.ai

import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

/**
 * Předá volání tomu runtime, který uživatel zvolil ([ModelVariant.provider]).
 *
 * Díky tomu je poskytovatel jedna volba pro CELOU aplikaci: shrnutí, návrhy odpovědí i každá
 * další funkce jen předají variantu z nastavení a nemusí vědět, že runtime jsou dva.
 *
 * **Variantu normalizuje** ([forGeminiNano], [forGemma]): každý runtime dostane jen pole, která
 * se ho týkají. ML Kit drží klienta na variantu v mapě, takže by jinak kvůli změně velikosti
 * Gemmy vznikl další klient Gemini Nano pro tentýž model.
 */
class RoutingOnDeviceAi(
    private val geminiNano: OnDeviceAi,
    private val gemma: OnDeviceAi,
) : OnDeviceAi {

    /** Runtime pro [variant] a varianta normalizovaná na jeho pole. */
    private fun target(variant: ModelVariant): Pair<OnDeviceAi, ModelVariant> = when (variant.provider) {
        AiProvider.GeminiNano -> geminiNano to variant.forGeminiNano()
        AiProvider.Gemma -> gemma to variant.forGemma()
        // Apple Intelligence je jen na iOS, kde se směrovač nepoužívá (`AppleOnDeviceAi`).
        AiProvider.Apple -> NoopOnDeviceAi to variant
    }

    private inline fun <T> route(variant: ModelVariant, call: OnDeviceAi.(ModelVariant) -> T): T {
        val (runtime, normalized) = target(variant)
        return runtime.call(normalized)
    }

    override suspend fun status(variant: ModelVariant): OnDeviceAiStatus = route(variant) { status(it) }

    override suspend fun info(variant: ModelVariant): ModelInfo? = route(variant) { info(it) }

    override fun download(variant: ModelVariant): Flow<ModelDownload> = route(variant) { download(it) }

    override suspend fun stopDownload(variant: ModelVariant) = route(variant) { stopDownload(it) }

    override suspend fun countTokens(variant: ModelVariant, prompt: String): Int? = route(variant) { countTokens(it, prompt) }

    override fun generate(variant: ModelVariant, prompt: String): Flow<Generated> =
        route(variant) { generate(it, prompt) }

    override suspend fun supportsStructuredOutput(variant: ModelVariant): Boolean =
        route(variant) { supportsStructuredOutput(it) }

    override suspend fun <T : Any> generateStructured(variant: ModelVariant, prompt: String, type: KClass<T>): T =
        route(variant) { generateStructured(it, prompt, type) }

    override suspend fun canTranscribe(variant: ModelVariant): Boolean = route(variant) { canTranscribe(it) }

    override fun transcribe(variant: ModelVariant, audioPath: String, languageCode: String): Flow<Generated> =
        route(variant) { transcribe(it, audioPath, languageCode) }

    override suspend fun canDescribeImage(variant: ModelVariant): Boolean = route(variant) { canDescribeImage(it) }

    override fun describeImage(variant: ModelVariant, imagePath: String, languageCode: String, prompt: String): Flow<Generated> =
        route(variant) { describeImage(it, imagePath, languageCode, prompt) }

    override suspend fun delete(variant: ModelVariant) = route(variant) { delete(it) }
}

/** Jen to, co se týká Gemini Nano: preview a rychlá varianta. */
internal fun ModelVariant.forGeminiNano() = ModelVariant(provider = AiProvider.GeminiNano, preview = preview, fast = fast)

/** Jen to, co se týká Gemmy: který model. */
internal fun ModelVariant.forGemma() = ModelVariant(provider = AiProvider.Gemma, gemma = gemma)
