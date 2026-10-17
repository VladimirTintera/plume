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

    private fun <T> route(variant: ModelVariant, call: OnDeviceAi.(ModelVariant) -> T): T =
        when (variant.provider) {
            AiProvider.GeminiNano -> geminiNano.call(variant.forGeminiNano())
            AiProvider.Gemma -> gemma.call(variant.forGemma())
        }

    override suspend fun status(variant: ModelVariant): OnDeviceAiStatus = when (variant.provider) {
        AiProvider.GeminiNano -> geminiNano.status(variant.forGeminiNano())
        AiProvider.Gemma -> gemma.status(variant.forGemma())
    }

    override suspend fun info(variant: ModelVariant): ModelInfo? = when (variant.provider) {
        AiProvider.GeminiNano -> geminiNano.info(variant.forGeminiNano())
        AiProvider.Gemma -> gemma.info(variant.forGemma())
    }

    override fun download(variant: ModelVariant): Flow<ModelDownload> = route(variant) { download(it) }

    override suspend fun stopDownload(variant: ModelVariant) = when (variant.provider) {
        AiProvider.GeminiNano -> geminiNano.stopDownload(variant.forGeminiNano())
        AiProvider.Gemma -> gemma.stopDownload(variant.forGemma())
    }

    override suspend fun countTokens(variant: ModelVariant, prompt: String): Int? = when (variant.provider) {
        AiProvider.GeminiNano -> geminiNano.countTokens(variant.forGeminiNano(), prompt)
        AiProvider.Gemma -> gemma.countTokens(variant.forGemma(), prompt)
    }

    override fun generate(variant: ModelVariant, prompt: String): Flow<Generated> =
        route(variant) { generate(it, prompt) }

    override suspend fun supportsStructuredOutput(variant: ModelVariant): Boolean = when (variant.provider) {
        AiProvider.GeminiNano -> geminiNano.supportsStructuredOutput(variant.forGeminiNano())
        AiProvider.Gemma -> gemma.supportsStructuredOutput(variant.forGemma())
    }

    override suspend fun <T : Any> generateStructured(variant: ModelVariant, prompt: String, type: KClass<T>): T =
        when (variant.provider) {
            AiProvider.GeminiNano -> geminiNano.generateStructured(variant.forGeminiNano(), prompt, type)
            AiProvider.Gemma -> gemma.generateStructured(variant.forGemma(), prompt, type)
        }

    override suspend fun canTranscribe(variant: ModelVariant): Boolean = when (variant.provider) {
        AiProvider.GeminiNano -> geminiNano.canTranscribe(variant.forGeminiNano())
        AiProvider.Gemma -> gemma.canTranscribe(variant.forGemma())
    }

    override fun transcribe(variant: ModelVariant, audioPath: String, languageCode: String): Flow<Generated> =
        route(variant) { transcribe(it, audioPath, languageCode) }

    override suspend fun canDescribeImage(variant: ModelVariant): Boolean = when (variant.provider) {
        AiProvider.GeminiNano -> geminiNano.canDescribeImage(variant.forGeminiNano())
        AiProvider.Gemma -> gemma.canDescribeImage(variant.forGemma())
    }

    override fun describeImage(variant: ModelVariant, imagePath: String, languageCode: String): Flow<Generated> =
        route(variant) { describeImage(it, imagePath, languageCode) }

    override suspend fun delete(variant: ModelVariant) = when (variant.provider) {
        AiProvider.GeminiNano -> geminiNano.delete(variant.forGeminiNano())
        AiProvider.Gemma -> gemma.delete(variant.forGemma())
    }
}

/** Jen to, co se týká Gemini Nano: preview a rychlá varianta. */
internal fun ModelVariant.forGeminiNano() = ModelVariant(provider = AiProvider.GeminiNano, preview = preview, fast = fast)

/** Jen to, co se týká Gemmy: který model. */
internal fun ModelVariant.forGemma() = ModelVariant(provider = AiProvider.Gemma, gemma = gemma)
