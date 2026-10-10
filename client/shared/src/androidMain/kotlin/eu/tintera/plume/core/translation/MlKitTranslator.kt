package eu.tintera.plume.core.translation

import co.touchlab.kermit.Logger
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * ML Kit Translation: model na jazyk má kolem 30 MB a stahuje se jen na pokyn uživatele
 * ([download]), bez podmínky Wi-Fi — o stažení uživatel ví, protože ho sám potvrdil.
 *
 * - **Překládá přes angličtinu**, takže pár „čeština → polština" chce tři balíčky; angličtina
 *   je v ML Kit vždycky (model se nestahuje), proto ji [hasModels] nekontroluje.
 * - **Klient se po překladu zavírá** (`close`): drží v paměti načtený model a v konverzaci se
 *   překládá jednou za čas, ne pořád.
 */
class MlKitTranslator : OnDeviceTranslator {
    private val logger = Logger.withTag("Translator")
    private val models = RemoteModelManager.getInstance()

    override fun supports(language: String): Boolean = TranslateLanguage.fromLanguageTag(language) != null

    override suspend fun hasModels(source: String, target: String): Boolean {
        val downloaded = runCatching { models.getDownloadedModels(TranslateRemoteModel::class.java).await() }
            .onFailure { logger.w(it) { "Stažené balíčky překladu nejde zjistit." } }
            .getOrNull()
            ?.map { it.language }
            ?.toSet()
            ?: return false
        return listOf(source, target)
            .mapNotNull(TranslateLanguage::fromLanguageTag)
            .filterNot { it == TranslateLanguage.ENGLISH }
            .all { it in downloaded }
    }

    override suspend fun download(source: String, target: String): Result<Unit> = withClient(source, target) { client ->
        client.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        Unit
    }

    override suspend fun translate(text: String, source: String, target: String): Result<String> =
        withClient(source, target) { client -> client.translate(text).await() }

    private suspend fun <T> withClient(
        source: String,
        target: String,
        block: suspend (com.google.mlkit.nl.translate.Translator) -> T,
    ): Result<T> {
        val from = TranslateLanguage.fromLanguageTag(source)
        val to = TranslateLanguage.fromLanguageTag(target)
        if (from == null || to == null) return Result.failure(IllegalArgumentException("Nepodporovaný pár $source → $target"))
        val client = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build())
        return try {
            runCatching { block(client) }.onFailure { logger.w(it) { "Překlad v telefonu $source → $target selhal." } }
        } finally {
            client.close()
        }
    }
}

/** Task API bez `coroutines-play-services` — stejně jako v `core/ocr`. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { continuation.resumeWith(Result.failure(it)) }
    addOnCanceledListener { continuation.cancel() }
}
