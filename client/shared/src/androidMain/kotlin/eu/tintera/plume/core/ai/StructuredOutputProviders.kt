package eu.tintera.plume.core.ai

import com.google.mlkit.genai.schema.guided.GenerableDetail
import com.google.mlkit.genai.schema.guided.GenerableProvider
import kotlin.reflect.KClass

/*
 * Popisy tvaru pro strukturovaný výstup Gemini Nano (`OnDeviceAi.generateStructured`).
 *
 * ML Kit k typu odpovědi hledá `GenerableProvider` přes `ServiceLoader` (mapa `targetClass` →
 * provider, ověřeno v bajtkódu `genai-prompt` 1.0.0-beta4) a z jeho popisu skládá schéma, které
 * dá modelu. Normálně by providery vygeneroval KSP procesor `genai-schema-compiler` z anotací
 * `@Generable` / `@Guide` — jenže typy odpovědí leží v `commonMain` (čte je ViewModel) a anotace
 * ML Kitu tam být nemůžou. Providery jsou proto psané ručně, po vzoru toho, co procesor generuje,
 * a KSP v projektu není.
 *
 * Nový typ = třída tady, řádek v `src/androidMain/resources/META-INF/services/
 * com.google.mlkit.genai.schema.guided.GenerableProvider` a keep pravidlo na jeho pole
 * v `proguard-rules.pro` (Gson ML Kitu plní objekt podle JMEN polí). Bez řádku ve services ML Kit
 * typ nezná a generování selže; bez keep pravidla R8 pole přejmenuje a objekt zůstane prázdný.
 */

/** Tvar [ReplySuggestionsOutput]: jedno pole `replies`, seznam krátkých vět. */
class ReplySuggestionsOutputProvider : GenerableProvider {
    override val targetClass: KClass<*> = ReplySuggestionsOutput::class

    override fun getGenerableDetail(): GenerableDetail<*> = DETAIL

    private companion object {
        val DETAIL = GenerableDetail<ReplySuggestionsOutput>(
            description = "Short replies the user could send to the last message",
            guideDetails = listOf(
                GenerableDetail.GuideDetail(
                    name = "replies",
                    type = List::class,
                    nullable = false,
                    isList = true,
                    listItemType = String::class,
                    description = "Each reply is one complete sentence of at most 8 words, " +
                        "written as the user, ready to send",
                    maxItems = MAX_REPLY_SUGGESTIONS,
                    minItems = 1,
                    maximum = null,
                    minimum = null,
                    enumValues = null,
                ),
            ),
        )
    }
}
