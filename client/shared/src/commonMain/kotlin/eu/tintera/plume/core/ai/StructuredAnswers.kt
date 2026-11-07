package eu.tintera.plume.core.ai

/*
 * Typy odpovědí pro strukturovaný výstup (`OnDeviceAi.generateStructured`) u klasifikací
 * a výtahů z textu. Každý zrcadlí řádky textového promptu téže funkce — a typovaná odpověď
 * jde přes TENTÝŽ parser jako text (`parseScamVerdict`, `parseReminderTime`, …). Ověření
 * obsahu (místo musí být ve zprávě, termín v budoucnu, verdikt jen jasný) tak zůstává na
 * jednom místě; strukturovaný výstup jistí jen tvar.
 *
 * Pole jsou nullable ze stejného důvodu jako u `ReplySuggestionsOutput`: Gson ML Kitu staví
 * objekt bez konstruktoru a vynechané pole je `null` navzdory typu. Jména polí jsou schéma,
 * které vidí model; popis tvaru (povolené hodnoty, formát data) je v `StructuredOutputProviders`
 * (Android) a každé pole drží keep pravidlo v `proguard-rules.pro`.
 */

/** Odpověď na [scamPrompt]: PHISHING, PRIZE, MONEY, SAFE nebo UNSURE. */
data class ScamAnswer(val verdict: String? = null)

/** Odpověď na [triagePrompt]: REPLY, INFO nebo UNSURE. */
data class TriageAnswer(val verdict: String? = null)

/** Odpověď na [tonePrompt]: FINE, IRRITATED, RUDE nebo UNSURE. */
data class ToneAnswer(val verdict: String? = null)

/** Odpověď na [reminderPrompt]: okamžik jako `YYYY-MM-DD HH:MM`, nebo NONE. */
data class ReminderAnswer(val moment: String? = null)

/** Odpověď na [eventPrompt]: tytéž čtyři údaje jako řádky WHEN / EVENT / TITLE / PLACE. */
data class EventAnswer(
    val `when`: String? = null,
    val event: String? = null,
    val title: String? = null,
    val place: String? = null,
)

/** Odpověď na [searchPrompt]: tytéž pole jako řádky TEXT / CHATS / CONTENT / FROM / TO. */
data class SearchAnswer(
    val text: String? = null,
    val chats: String? = null,
    val content: String? = null,
    val from: String? = null,
    val to: String? = null,
)
