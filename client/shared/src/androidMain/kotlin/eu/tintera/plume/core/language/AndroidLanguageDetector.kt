package eu.tintera.plume.core.language

import android.content.Context
import android.os.Build
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextLanguage
import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Systémový `TextClassifier` — model jazyků má Android v sobě (LangId), aplikace nic nepřibaluje.
 * `detectLanguage` je až od Androidu 10; na starším se jazyk nerozpoznává (`null`) a „Přeložit"
 * zůstává vždycky. Volat se nesmí na hlavním vlákně, proto `Dispatchers.Default`.
 */
class AndroidLanguageDetector(private val context: Context) : LanguageDetector {
    private val logger = Logger.withTag("LanguageDetector")

    override suspend fun detect(text: String): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return withContext(Dispatchers.Default) {
            runCatching {
                val manager = context.getSystemService(TextClassificationManager::class.java)
                    ?: return@runCatching null
                val result = manager.textClassifier.detectLanguage(TextLanguage.Request.Builder(text).build())
                if (result.localeHypothesisCount == 0) return@runCatching null
                val locale = result.getLocale(0)
                locale.toLanguageTag().takeIf { result.getConfidenceScore(locale) >= MIN_LANGUAGE_CONFIDENCE }
            }
                .onFailure { failure -> logger.w(failure) { "Jazyk textu nejde rozpoznat." } }
                .getOrNull()
        }
    }
}
