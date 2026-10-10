package eu.tintera.plume.core.ocr

import co.touchlab.kermit.Logger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.Foundation.NSURL
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate

/**
 * Apple Vision (`VNRecognizeTextRequest`), přesný režim s automatickým jazykem.
 *
 * - **Vision vrací ŘÁDKY, ne bloky**, a počátek souřadnic má VLEVO DOLE. Řádky se proto převrátí
 *   na počátek nahoře a seřadí do pořadí čtení ([inReadingOrder]).
 * - Běží na `Dispatchers.Default`: `performRequests` je synchronní a trvá stovky milisekund.
 */
@OptIn(ExperimentalForeignApi::class)
class VisionTextRecognizer : TextRecognizer {
    private val logger = Logger.withTag("TextRecognizer")

    override suspend fun recognize(path: String): RecognizedText? = withContext(Dispatchers.Default) {
        val request = VNRecognizeTextRequest().apply {
            recognitionLevel = VNRequestTextRecognitionLevelAccurate
            usesLanguageCorrection = true
            automaticallyDetectsLanguage = true
        }
        val handler = VNImageRequestHandler(uRL = NSURL.fileURLWithPath(path), options = emptyMap<Any?, Any?>())
        if (!handler.performRequests(listOf(request), error = null)) {
            logger.w { "Rozpoznání textu v $path selhalo." }
            return@withContext null
        }
        val blocks = request.results.orEmpty().mapNotNull { result ->
            val observation = result as? VNRecognizedTextObservation ?: return@mapNotNull null
            val text = (observation.topCandidates(1u).firstOrNull() as? VNRecognizedText)?.string ?: return@mapNotNull null
            observation.boundingBox.useContents {
                RecognizedBlock(
                    text = text,
                    left = origin.x.toFloat(),
                    top = (1 - origin.y - size.height).toFloat(),
                    right = (origin.x + size.width).toFloat(),
                    bottom = (1 - origin.y).toFloat(),
                )
            }
        }
        RecognizedText(inReadingOrder(blocks))
    }
}
