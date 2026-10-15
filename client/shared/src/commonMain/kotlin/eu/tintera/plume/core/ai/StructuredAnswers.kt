package eu.tintera.plume.core.ai

import co.touchlab.kermit.Logger
import kotlin.reflect.KClass

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

/**
 * Odpověď modelu strukturovaně, kde to model umí ([OnDeviceAi.supportsStructuredOutput]),
 * jinak textem — výsledek je v obou případech týž [R].
 *
 * [prompt] dostane, jestli jde o strukturovaný dotaz: ten o formátu mlčí, protože schéma do
 * promptu přidá runtime sám a pokyny k řádkům by se s ním praly.
 *
 * **Na text se jde jen při špatném tvaru** ([StructuredOutputException]). `null` ze
 * [fromStructured] je platná odpověď („nevím", „žádný termín") — kdyby se po ní zkoušel
 * text, platilo by se dvakrát a opatrnost klasifikace by přebil druhý pokus. Rozbitý model
 * ([OnDeviceAiException]) letí ven jako u textu.
 */
suspend fun <A : Any, R> OnDeviceAi.generateAnswer(
    variant: ModelVariant,
    type: KClass<A>,
    prompt: (structured: Boolean) -> String,
    fromStructured: (A) -> R?,
    fromText: (String) -> R?,
): R? {
    if (supportsStructuredOutput(variant)) {
        try {
            val answer = generateStructured(variant, prompt(true), type)
            logger.d { "Strukturovaná odpověď: $answer" }
            return fromStructured(answer)
        } catch (e: StructuredOutputException) {
            logger.w(e) { "Strukturovaná odpověď (${type.simpleName}) se nepovedla, zkouším text" }
        }
    }
    var last = ""
    generate(variant, prompt(false)).collect { last = it.text }
    logger.d { "Model odpověděl: $last" }
    return fromText(last)
}

private val logger = Logger.withTag("OnDeviceAi")
