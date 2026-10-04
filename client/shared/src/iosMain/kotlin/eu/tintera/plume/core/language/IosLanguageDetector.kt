package eu.tintera.plume.core.language

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSNumber
import platform.NaturalLanguage.NLLanguageRecognizer

/** `NLLanguageRecognizer` z NaturalLanguage — model je v systému, nic se nestahuje. */
class IosLanguageDetector : LanguageDetector {
    override suspend fun detect(text: String): String? = withContext(Dispatchers.Default) {
        val recognizer = NLLanguageRecognizer()
        recognizer.processString(text)
        val (language, probability) = recognizer.languageHypothesesWithMaximum(1u).entries
            .firstOrNull() ?: return@withContext null
        val confidence = (probability as? NSNumber)?.doubleValue ?: return@withContext null
        (language as? String)?.takeIf { confidence >= MIN_LANGUAGE_CONFIDENCE }
    }
}
