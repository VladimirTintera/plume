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

/** Textové pole schématu; [values] = povolené hodnoty, které model nesmí opustit. */
private fun stringField(name: String, description: String, values: List<String>? = null) =
    GenerableDetail.GuideDetail(
        name = name,
        type = String::class,
        nullable = false,
        isList = false,
        listItemType = null,
        description = description,
        maxItems = null,
        minItems = null,
        maximum = null,
        minimum = null,
        enumValues = values?.toTypedArray(),
    )

/** Tvar [ScamAnswer]: jeden verdikt z výčtu (význam hodnot říká prompt). */
class ScamAnswerProvider : GenerableProvider {
    override val targetClass: KClass<*> = ScamAnswer::class

    override fun getGenerableDetail(): GenerableDetail<*> = DETAIL

    private companion object {
        val DETAIL = GenerableDetail<ScamAnswer>(
            description = "Verdict on whether the message is a scam",
            guideDetails = listOf(
                stringField("verdict", "One verdict as described above", listOf("PHISHING", "PRIZE", "MONEY", "SAFE", "UNSURE")),
            ),
        )
    }
}

/** Tvar [ToneAnswer]: jeden verdikt z výčtu. */
class ToneAnswerProvider : GenerableProvider {
    override val targetClass: KClass<*> = ToneAnswer::class

    override fun getGenerableDetail(): GenerableDetail<*> = DETAIL

    private companion object {
        val DETAIL = GenerableDetail<ToneAnswer>(
            description = "How the message may come across to the reader",
            guideDetails = listOf(
                stringField("verdict", "One verdict as described above", listOf("FINE", "IRRITATED", "RUDE", "UNSURE")),
            ),
        )
    }
}

/** Tvar [ReminderAnswer]: okamžik, nebo NONE. */
class ReminderAnswerProvider : GenerableProvider {
    override val targetClass: KClass<*> = ReminderAnswer::class

    override fun getGenerableDetail(): GenerableDetail<*> = DETAIL

    private companion object {
        val DETAIL = GenerableDetail<ReminderAnswer>(
            description = "The moment the message refers to",
            guideDetails = listOf(
                stringField("moment", "The moment as YYYY-MM-DD HH:MM in 24-hour time, or NONE"),
            ),
        )
    }
}

/** Tvar [EventAnswer]: čas, rozhodnutí, název a místo. */
class EventAnswerProvider : GenerableProvider {
    override val targetClass: KClass<*> = EventAnswer::class

    override fun getGenerableDetail(): GenerableDetail<*> = DETAIL

    private companion object {
        val DETAIL = GenerableDetail<EventAnswer>(
            description = "Whether the message plans an event for the calendar",
            guideDetails = listOf(
                stringField("when", "Whether that time is before (PAST) or after (FUTURE) the message was sent", listOf("PAST", "FUTURE")),
                stringField("event", "YES if the message plans a meeting, appointment, deadline or event at that time", listOf("YES", "NO")),
                stringField("title", "A short name for the calendar event in the language of the message, at most 5 words"),
                stringField("place", "The place or address exactly as written in the message, or NONE"),
            ),
        )
    }
}

/** Tvar [SearchAnswer]: tytéž pole jako textové řádky. */
class SearchAnswerProvider : GenerableProvider {
    override val targetClass: KClass<*> = SearchAnswer::class

    override fun getGenerableDetail(): GenerableDetail<*> = DETAIL

    private companion object {
        val DETAIL = GenerableDetail<SearchAnswer>(
            description = "Search filters for a chat app",
            guideDetails = listOf(
                stringField("text", "Only the topic words to look for inside the messages, copied as the user wrote them; empty if there is no topic"),
                stringField("chats", "Which chats to search", listOf("PRIVATE", "GROUPS", "CHANNELS", "ANY")),
                stringField("content", "Which content to search", listOf("PHOTOS", "VIDEOS", "FILES", "LINKS", "VOICE", "MUSIC", "ANY")),
                stringField("from", "The first day as YYYY-MM-DD, or NONE"),
                stringField("to", "The last day as YYYY-MM-DD, or NONE"),
            ),
        )
    }
}
