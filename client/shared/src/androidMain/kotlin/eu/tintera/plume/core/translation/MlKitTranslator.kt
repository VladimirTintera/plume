package eu.tintera.plume.core.translation

import android.content.Context
import co.touchlab.kermit.Logger
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
 * ML Kit Translation: balíček na jazyk má kolem 40 MB a stahuje se jen na pokyn uživatele
 * ([download]), bez podmínky Wi-Fi — o stažení uživatel ví, protože ho sám potvrdil.
 *
 * - **Překládá přes angličtinu**, takže pár „čeština → polština" chce tři balíčky; angličtina
 *   je v ML Kit vždycky (model se nestahuje), proto ji [hasModels] nekontroluje.
 * - **Klient se po překladu zavírá** (`close`): drží v paměti načtený model a v konverzaci se
 *   překládá jednou za čas, ne pořád.
 */
class MlKitTranslator(private val context: Context) : OnDeviceTranslator {
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

    override suspend fun downloadedPacks(): List<TranslationPack> {
        val downloaded = runCatching { models.getDownloadedModels(TranslateRemoteModel::class.java).await() }
            .onFailure { logger.w(it) { "Stažené balíčky překladu nejde zjistit." } }
            .getOrDefault(emptySet())
        return downloaded
            .map { it.language }
            .filterNot { it == TranslateLanguage.ENGLISH }
            .sorted()
            .map { language -> TranslationPack(language, withContext(Dispatchers.IO) { packBytes(language) }) }
    }

    override suspend fun deletePack(language: String): Result<Unit> {
        val tag = TranslateLanguage.fromLanguageTag(language)
            ?: return Result.failure(IllegalArgumentException("Nepodporovaný jazyk $language"))
        return runCatching { models.deleteDownloadedModel(TranslateRemoteModel.Builder(tag).build()).await() }
            .map { removeLeftovers(tag) }
            .onFailure { logger.w(it) { "Balíček $language se nepodařilo smazat." } }
    }

    /**
     * ML Kit při smazání uklidí jen část: ze 40 MB češtiny na disku nechal 12 MB (modely obou
     * směrů, `translate_csen` a `translate_encs`), přestože balíček už jako stažený nevede a jeho
     * evidence (`download_manager`) je prázdná. Byla by to mrtvá data navždy — složka se proto
     * smaže, ale jen když ji ML Kit opravdu přestal vést.
     */
    private suspend fun removeLeftovers(language: String) {
        val stillDownloaded = runCatching { models.getDownloadedModels(TranslateRemoteModel::class.java).await() }
            .getOrNull()
            ?.any { it.language == language }
            ?: return
        if (stillDownloaded) return
        withContext(Dispatchers.IO) { packFolder(language).deleteRecursively() }
    }

    /**
     * Místo balíčku na disku. ML Kit velikost nehlásí; balíček leží v `no_backup` ve složce
     * `<jazyk>_en` (oba směry přes angličtinu a společná data — čeština na emulátoru 40 MB).
     * Je to vnitřní uspořádání knihovny, takže když tam složka není, velikost se neukáže.
     */
    private fun packBytes(language: String): Long? {
        val folder = packFolder(language)
        if (!folder.isDirectory) return null
        return folder.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    private fun packFolder(language: String) = File(context.noBackupFilesDir, "com.google.mlkit.translate.models/${language}_en")

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
