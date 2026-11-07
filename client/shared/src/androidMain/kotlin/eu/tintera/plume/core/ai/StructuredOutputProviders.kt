package eu.tintera.plume.core.ai

import com.google.mlkit.genai.schema.guided.GenerableDetail
import com.google.mlkit.genai.schema.guided.GenerableProvider
import eu.tintera.ai.core.generableStringField
import kotlin.reflect.KClass

/*
 * Popisy tvaru pro strukturovaný výstup Gemini Nano (`OnDeviceAi.generateStructured`).
 *
 * ML Kit k typu odpovědi hledá `GenerableProvider` přes `ServiceLoader` (mapa `targetClass` →
 * provider, ověřeno v bajtkódu `genai-prompt` 1.0.0-beta4 i beta5) a z jeho popisu skládá schéma, které
 * dá modelu. Normálně by providery vygeneroval KSP procesor `genai-schema-compiler` z anotací
 * `@Generable` / `@Guide` — jenže typy odpovědí leží v `commonMain` (čte je ViewModel) a anotace
 * ML Kitu tam být nemůžou. Providery jsou proto psané ručně, po vzoru toho, co procesor generuje,
 * a KSP v projektu není.
 *
 * Textová pole skládá `generableStringField` z :client:ai-core. Nový typ = třída tady, řádek
 * v `src/androidMain/resources/META-INF/services/com.google.mlkit.genai.schema.guided.GenerableProvider`
 * a keep pravidlo na jeho pole
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

/** Tvar [ScamAnswer]: jeden verdikt z výčtu (význam hodnot říká prompt). */
class ScamAnswerProvider : GenerableProvider {
    override val targetClass: KClass<*> = ScamAnswer::class

    override fun getGenerableDetail(): GenerableDetail<*> = DETAIL

    private companion object {
        val DETAIL = GenerableDetail<ScamAnswer>(
            description = "Verdict on whether the message is a scam",
            guideDetails = listOf(
                generableStringField("verdict", "One verdict as described above", listOf("PHISHING", "PRIZE", "MONEY", "SAFE", "UNSURE")),
            ),
        )
    }
}

/** Tvar [TriageAnswer]: jeden verdikt z výčtu. */
class TriageAnswerProvider : GenerableProvider {
    override val targetClass: KClass<*> = TriageAnswer::class

    override fun getGenerableDetail(): GenerableDetail<*> = DETAIL

    private companion object {
        val DETAIL = GenerableDetail<TriageAnswer>(
            description = "Whether the chat waits for the user's answer",
            guideDetails = listOf(
                generableStringField("verdict", "One verdict as described above", listOf("REPLY", "INFO", "UNSURE")),
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
                generableStringField("verdict", "One verdict as described above", listOf("FINE", "IRRITATED", "RUDE", "UNSURE")),
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
                generableStringField("moment", "The moment as YYYY-MM-DD HH:MM in 24-hour time, or NONE"),
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
                generableStringField("when", "Whether that time is before (PAST) or after (FUTURE) the message was sent", listOf("PAST", "FUTURE")),
                generableStringField("event", "YES if the message plans a meeting, appointment, deadline or event at that time", listOf("YES", "NO")),
                generableStringField("title", "A short name for the calendar event in the language of the message, at most 5 words"),
                generableStringField("place", "The place or address exactly as written in the message, or NONE"),
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
                generableStringField("text", "Only the topic words to look for inside the messages, copied as the user wrote them; empty if there is no topic"),
                generableStringField("chats", "Which chats to search", listOf("PRIVATE", "GROUPS", "CHANNELS", "ANY")),
                generableStringField("content", "Which content to search", listOf("PHOTOS", "VIDEOS", "FILES", "LINKS", "VOICE", "MUSIC", "ANY")),
                generableStringField("from", "The first day as YYYY-MM-DD, or NONE"),
                generableStringField("to", "The last day as YYYY-MM-DD, or NONE"),
            ),
        )
    }
}
