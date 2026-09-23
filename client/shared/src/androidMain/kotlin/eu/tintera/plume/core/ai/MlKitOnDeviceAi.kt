package eu.tintera.plume.core.ai

import co.touchlab.kermit.Logger
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Gemini Nano v AICore přes ML Kit Prompt API.
 *
 * - **Inferenci AICore pustí jen aplikaci v popředí.** Z pozadí (notifikace, úloha) skončí
 *   chybou — proto se volá jen z obrazovky.
 * - **Model drží a aktualizuje systém**, aplikace nic nebalí. Na zařízení bez AICore
 *   `checkStatus` vrátí `UNAVAILABLE`, nebo rovnou vyhodí — obojí je [OnDeviceAiStatus.Unsupported].
 * - **Klient je jeden na proces** a nezavírá se: `Generation.getClient` je levné volání, ale
 *   model se v AICore načítá při prvním požadavku a zavřením by se o to přišlo.
 */
class MlKitOnDeviceAi : OnDeviceAi {

    private val logger = Logger.withTag("OnDeviceAi")

    private val model: GenerativeModel by lazy { Generation.getClient(generationConfig { }) }

    override suspend fun status(): OnDeviceAiStatus = try {
        when (model.checkStatus()) {
            FeatureStatus.AVAILABLE -> OnDeviceAiStatus.Ready
            FeatureStatus.DOWNLOADABLE -> OnDeviceAiStatus.Downloadable
            FeatureStatus.DOWNLOADING -> OnDeviceAiStatus.Downloading
            else -> OnDeviceAiStatus.Unsupported
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Bez AICore to není chyba, jen zařízení, které model nemá.
        logger.i(e) { "Gemini Nano není k dispozici" }
        OnDeviceAiStatus.Unsupported
    }

    override suspend fun modelName(): String? = try {
        model.getBaseModelName()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.i(e) { "Název modelu nejde zjistit" }
        null
    }

    override fun download(): Flow<ModelDownload> = flow {
        var total: Long? = null
        model.download().collect { status ->
            when (status) {
                is DownloadStatus.DownloadStarted -> {
                    total = status.bytesToDownload
                    emit(ModelDownload(0, total))
                }
                is DownloadStatus.DownloadProgress -> emit(ModelDownload(status.totalBytesDownloaded, total))
                is DownloadStatus.DownloadFailed -> throw status.e
                is DownloadStatus.DownloadCompleted -> logger.i { "Gemini Nano stažený" }
            }
        }
    }

    override fun generate(prompt: String): Flow<String> = flow {
        val request = generateContentRequest(TextPart(prompt)) {
            // Shrnutí má držet fakta, ne vymýšlet — nízká teplota.
            temperature = 0.2f
            maxOutputTokens = MAX_OUTPUT_TOKENS
        }
        // Stream vydává PŘÍRŮSTKY (tak je skládá i ukázka od Googlu), rozhraní slibuje celý text.
        val text = StringBuilder()
        model.generateContentStream(request).collect { chunk ->
            val piece = chunk.candidates.firstOrNull()?.text ?: return@collect
            text.append(piece)
            emit(text.toString())
        }
    }

    private companion object {
        const val MAX_OUTPUT_TOKENS = 400
    }
}
