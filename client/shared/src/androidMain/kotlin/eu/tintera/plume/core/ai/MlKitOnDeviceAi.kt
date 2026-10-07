package eu.tintera.plume.core.ai

import co.touchlab.kermit.Logger
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Candidate
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.ModelPreference
import com.google.mlkit.genai.prompt.ModelReleaseStage
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import com.google.mlkit.genai.prompt.generationConfig
import com.google.mlkit.genai.prompt.modelConfig
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow

/**
 * Gemini Nano v AICore přes ML Kit Prompt API.
 *
 * - **Inferenci AICore pustí jen aplikaci v popředí.** Z pozadí (notifikace, úloha) skončí
 *   chybou — proto se volá jen z obrazovky.
 * - **Model drží a aktualizuje systém**, aplikace nic nebalí. Na zařízení bez AICore
 *   `checkStatus` vrátí `UNAVAILABLE`, nebo rovnou vyhodí — obojí je [OnDeviceAiStatus.Unsupported].
 * - **Klient je jeden na variantu a proces** a nezavírá se: model se v AICore načítá při prvním
 *   požadavku a zavřením by se o to přišlo. Variant jsou nejvýš čtyři.
 * - **Varianta je `ModelConfig`** (`ModelReleaseStage` STABLE / PREVIEW, `ModelPreference`
 *   FULL / FAST). Je to přání: na zařízení, kde preview není, hlásí `checkStatus` nedostupnost.
 */
class MlKitOnDeviceAi : OnDeviceAi {

    private val logger = Logger.withTag("OnDeviceAi")

    private val clients = ConcurrentHashMap<ModelVariant, GenerativeModel>()

    private fun client(variant: ModelVariant): GenerativeModel = clients.getOrPut(variant) {
        Generation.getClient(
            generationConfig {
                modelConfig = modelConfig {
                    releaseStage = if (variant.preview) ModelReleaseStage.PREVIEW else ModelReleaseStage.STABLE
                    preference = if (variant.fast) ModelPreference.FAST else ModelPreference.FULL
                }
            },
        )
    }

    override suspend fun status(variant: ModelVariant): OnDeviceAiStatus = try {
        when (client(variant).checkStatus()) {
            FeatureStatus.AVAILABLE -> OnDeviceAiStatus.Ready
            FeatureStatus.DOWNLOADABLE -> OnDeviceAiStatus.Downloadable
            FeatureStatus.DOWNLOADING -> OnDeviceAiStatus.Downloading
            else -> OnDeviceAiStatus.Unsupported
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // Bez AICore to není chyba, jen zařízení, které model nemá.
        logger.i(e) { "Gemini Nano není k dispozici ($variant)" }
        OnDeviceAiStatus.Unsupported
    } catch (e: LinkageError) {
        /*
         * Na zařízení, kde chybí systémová část (staré Play služby, ořezaný ROM, chybějící
         * nativní knihovna), nepřijde výjimka, ale ERROR — `NoClassDefFoundError`,
         * `UnsatisfiedLinkError`, `VerifyError`. `catch (Exception)` ho NECHYTÍ a aplikace by
         * spadla jen proto, že si někdo zapnul volitelnou funkci. Pro nás je to totéž co
         * „zařízení model nemá".
         */
        logger.i(e) { "Gemini Nano nejde načíst ($variant)" }
        OnDeviceAiStatus.Unsupported
    }

    override suspend fun info(variant: ModelVariant): ModelInfo? {
        val model = client(variant)
        val name = quietly("base model name") { model.getBaseModelName() }
        val limit = quietly("token limit") { model.getTokenLimit() }
        return if (name == null && limit == null) null else ModelInfo(name, limit)
    }

    override fun download(variant: ModelVariant): Flow<ModelDownload> = flow {
        var total: Long? = null
        client(variant).download().collect { status ->
            when (status) {
                is DownloadStatus.DownloadStarted -> {
                    total = status.bytesToDownload
                    emit(ModelDownload(0, total))
                }
                is DownloadStatus.DownloadProgress -> emit(ModelDownload(status.totalBytesDownloaded, total))
                is DownloadStatus.DownloadFailed -> throw status.e
                is DownloadStatus.DownloadCompleted -> logger.i { "Gemini Nano stažený ($variant)" }
            }
        }
    }

    override suspend fun countTokens(variant: ModelVariant, prompt: String): Int? =
        quietly("token count") { client(variant).countTokens(request(prompt)).totalTokens }

    override fun generate(variant: ModelVariant, prompt: String): Flow<Generated> = flow {
        // Stream vydává PŘÍRŮSTKY (tak je skládá i ukázka od Googlu), rozhraní slibuje celý text.
        val text = StringBuilder()
        var truncated = false
        client(variant).generateContentStream(request(prompt)).collect { chunk ->
            val candidate = chunk.candidates.firstOrNull() ?: return@collect
            /*
             * MAX_TOKENS = modelu došel strop výstupu (MAX_OUTPUT_TOKENS), takže text končí,
             * kde zrovna byl. Příznak se jen zvedá a nikdy neruší: chodí na posledním kusu
             * a další už nepřijde.
             */
            if (candidate.finishReason == Candidate.FinishReason.MAX_TOKENS) truncated = true
            // Kus BEZ textu nese jen finishReason, takže se nesmí přeskočit — jinak by se
            // useknutí ztratilo právě u zprávy, která ho hlásí.
            candidate.text?.let { text.append(it) }
            emit(Generated(text.toString(), truncated))
        }
    }.catch { e ->
        // CancellationException `catch` na toku nechytá — zrušení projde, jak má.
        throw if (e is Exception) e.toAiFailure() else e
    }

    private fun request(prompt: String) = generateContentRequest(TextPart(prompt)) {
        // Shrnutí má držet fakta, ne vymýšlet — nízká teplota.
        temperature = 0.2f
        maxOutputTokens = MAX_OUTPUT_TOKENS
    }

    /** Doplňkové údaje: když je model neřekne, nic se neděje, jen se neukážou. */
    private suspend fun <T> quietly(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.i(e) { "Model neřekl údaj: $what" }
        null
    } catch (e: LinkageError) {
        // Viz [status] — na zařízení bez systémové části chodí Error, ne výjimka.
        logger.i(e) { "Model neřekl údaj: $what" }
        null
    }

    /**
     * Chyba ML Kitu → důvod pro uživatele a Crashlytics. Kód z AICore, který ML Kit nezná
     * (na Foldu `5` = COMPUTE_ERROR, když modelu chyběl soubor), je [AiFailureReason.ModelError]
     * — i s číslem, ať je v hlášení vidět.
     */
    private fun Exception.toAiFailure(): OnDeviceAiException {
        val code = (this as? GenAiException)?.errorCode
        val reason = when (code) {
            GenAiException.ErrorCode.BUSY,
            GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED,
            GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED,
            -> AiFailureReason.Busy
            GenAiException.ErrorCode.REQUEST_TOO_LARGE -> AiFailureReason.TooLarge
            GenAiException.ErrorCode.NOT_ENOUGH_DISK_SPACE -> AiFailureReason.NotEnoughSpace
            GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE,
            GenAiException.ErrorCode.AICORE_INCOMPATIBLE,
            -> AiFailureReason.NeedsUpdate
            GenAiException.ErrorCode.NOT_AVAILABLE,
            GenAiException.ErrorCode.NOT_SUPPORTED,
            -> AiFailureReason.Unavailable
            else -> AiFailureReason.ModelError
        }
        return OnDeviceAiException(reason, AiProvider.GeminiNano, code, this)
    }

    private companion object {
        /**
         * Strop výstupu. Se 400 se shrnutí dvanácti zpráv usekávalo v půlce (ověřeno na
         * Pixelu 11 Pro Foldu) — čeština zabere víc tokenů než angličtina, na kterou bývají
         * výchozí hodnoty laděné. Platí se za to delším generováním, takže to není „čím víc,
         * tím líp"; co se přesto nevejde, ohlásí [Generated.truncated].
         */
        const val MAX_OUTPUT_TOKENS = 800
    }
}
