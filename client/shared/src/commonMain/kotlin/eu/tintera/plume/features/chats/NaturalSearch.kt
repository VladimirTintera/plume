package eu.tintera.plume.features.chats

import kotlinx.datetime.minus
import kotlinx.datetime.DateTimeUnit
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.ai.searchFieldsOf
import eu.tintera.plume.core.ai.generateAnswer
import eu.tintera.plume.core.ai.SearchAnswer
import eu.tintera.plume.core.ai.OnDeviceAi
import eu.tintera.plume.core.ai.OnDeviceAiStatus
import eu.tintera.plume.core.ai.searchFields
import eu.tintera.plume.core.ai.searchPrompt
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.MessageSearchFilter
import eu.tintera.plume.core.telegram.SearchContent
import eu.tintera.plume.core.telegram.SearchPeriod
import eu.tintera.plume.core.ui.foldDiacritics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn

/** Hledání vyčtené z věty: slova k hledání a filtr zpráv. */
data class NaturalSearch(val text: String, val filter: MessageSearchFilter)

/**
 * Výstup modelu ([searchFields]) → hledání, jak ho model vidí. Jestli z něj jde hledat
 * a co se opraví pravidly, rozhoduje až [refineNaturalSearch].
 *
 * - **Data se ověřují, ne berou**: nesmyslné datum se zahodí, budoucnost se zastropuje dneškem
 *   a obrácený rozsah se otočí. Jen „OD" znamená do dneška, jen „DO" od začátku Telegramu.
 * - Rozsah je tentýž [SearchPeriod.Custom] jako z výběru dat: půlnoci dnů v místním pásmu.
 */
fun naturalSearchFrom(fields: Map<String, String>, today: LocalDate, timeZone: TimeZone): NaturalSearch {
    val text = fields["TEXT"].orEmpty().takeUnless { it.uppercase() in EMPTY_VALUES }.orEmpty()
    val chatKind = when (fields["CHATS"]?.uppercase()) {
        "PRIVATE" -> ChatKind.Private
        "GROUPS", "GROUP" -> ChatKind.Group
        "CHANNELS", "CHANNEL" -> ChatKind.Channel
        else -> null
    }
    val content = when (fields["CONTENT"]?.uppercase()) {
        "PHOTOS", "PHOTO" -> SearchContent.Photos
        "VIDEOS", "VIDEO" -> SearchContent.Videos
        "FILES", "FILE" -> SearchContent.Files
        "LINKS", "LINK" -> SearchContent.Links
        "VOICE" -> SearchContent.Voice
        "MUSIC" -> SearchContent.Music
        else -> null
    }
    val period = period(fields["FROM"]?.let(::date), fields["TO"]?.let(::date), today, timeZone)
    return NaturalSearch(text.trim(), MessageSearchFilter(chatKind = chatKind, period = period, content = content))
}

/**
 * Hledání z věty: **pravidla tam, kde jsou si jistá, model na zbytek.** Gemma 4 E2B na tohle
 * sama nestačí (ověřeno na emulátoru): do slov k hledání dávala celou větu nebo jen slovo
 * filtru („souborech"), soubory ani „smlouvu" nepoznala a „poslední měsíc" spočítala jako rok.
 *
 * - **Druh obsahu a chatu** podle slov v PŮVODNÍ větě ([query]) — „fotky", „kanálu",
 *   „soubory", i bez diakritiky a anglicky. Když věta žádné nemá, platí, co řekl model.
 * - **Běžná období** („poslední týden / měsíc / rok", „loni", „letos", „včera", „dnes") taky
 *   pravidly; předvolba se navíc v čipu ukáže slovem, ne rozsahem dat. Volnější formulace
 *   („loni v létě") zůstávají na modelu ([naturalSearchFrom]).
 * - **Slova k hledání jen z věty**: od modelu se berou jen slova, která ve větě opravdu stojí
 *   (TDLib hledá podle začátku slova a „dovolená" místo „dovolené" by nenašlo nic). Když
 *   model žádné nedal, vezmou se slova věty bez slov filtru a období. Předložky na krajích
 *   („recept na guláš ze [skupin]") jdou pryč, uprostřed patří k tématu.
 *
 * Vrací `null`, když nezbyla slova ani druh obsahu (hledat by nešlo).
 */
fun refineNaturalSearch(
    search: NaturalSearch?,
    query: String,
    today: LocalDate,
    timeZone: TimeZone,
): NaturalSearch? {
    val queryWords = query.split(WHITESPACE).map { it.trim(',', '.', '?', '!', '"') }.filter { it.isNotEmpty() }
    val foldedQuery = queryWords.map { foldDiacritics(it).lowercase() }
    val phrase = foldedQuery.joinToString(" ")

    val content = CONTENT_STEMS.entries.firstOrNull { (stems, _) -> foldedQuery.any { word -> stems.any(word::startsWith) } }?.value
        ?: search?.filter?.content
    val chatKind = CHAT_STEMS.entries.firstOrNull { (stems, _) -> foldedQuery.any { word -> stems.any(word::startsWith) } }?.value
        ?: search?.filter?.chatKind
    val period = rulePeriod(phrase, foldedQuery, today, timeZone) ?: search?.filter?.period

    fun topical(folded: String) = FILTER_STEMS.none(folded::startsWith) && folded !in PERIOD_WORDS
    val fromModel = search?.text.orEmpty().split(WHITESPACE)
        .map { it.trim(',', '.', '?', '!', '"') }
        .filter { word -> foldDiacritics(word).lowercase().let { it in foldedQuery && topical(it) } }
    val words = fromModel.ifEmpty { queryWords.filterIndexed { index, _ -> topical(foldedQuery[index]) } }
        .dropWhile { foldDiacritics(it).lowercase() in EDGE_WORDS }
        .dropLastWhile { foldDiacritics(it).lowercase() in EDGE_WORDS }
    val text = words.joinToString(" ")
    if (text.isBlank() && content == null) return null
    return NaturalSearch(text, MessageSearchFilter(chatKind = chatKind, period = period, content = content))
}

private fun rulePeriod(phrase: String, words: List<String>, today: LocalDate, timeZone: TimeZone): SearchPeriod? {
    RELATIVE_PERIODS.entries.firstOrNull { (phrases, _) -> phrases.any { it in phrase } }?.let { return it.value }
    fun days(from: LocalDate, to: LocalDate) =
        SearchPeriod.Custom(from.atStartOfDayIn(timeZone).epochSeconds, to.atStartOfDayIn(timeZone).epochSeconds)
    return when {
        words.any { it == "loni" || it.startsWith("lonsk") } || "last year" in phrase ->
            days(LocalDate(today.year - 1, 1, 1), LocalDate(today.year - 1, 12, 31))
        words.any { it == "letos" || it.startsWith("letosn") } || "this year" in phrase -> days(LocalDate(today.year, 1, 1), today)
        words.any { it == "vcera" || it.startsWith("vcerej") } || "yesterday" in phrase ->
            today.minus(1, DateTimeUnit.DAY).let { days(it, it) }
        words.any { it == "dnes" || it.startsWith("dnesn") } || "today" in words -> days(today, today)
        else -> null
    }
}

private val WHITESPACE = Regex("\\s+")

private val RELATIVE_PERIODS: Map<List<String>, SearchPeriod> = mapOf(
    listOf("posledni tyden", "minuly tyden", "za tyden", "tento tyden", "last week", "past week", "this week") to SearchPeriod.Week,
    listOf("posledni mesic", "minuly mesic", "za mesic", "tento mesic", "last month", "past month", "this month") to SearchPeriod.Month,
    listOf("posledni rok", "za rok", "past year") to SearchPeriod.Year,
)

/** Slova období, která do slov k hledání nepatří (celá slova bez diakritiky). */
private val PERIOD_WORDS = setOf(
    "posledni", "posledniho", "minuly", "minuleho", "tento", "tento", "tyden", "tydne", "mesic", "mesice", "rok", "roku",
    "loni", "lonsky", "lonskeho", "lonske", "letos", "letosni", "letosniho", "vcera", "vcerejsi", "dnes", "dnesni",
    "last", "past", "this", "week", "month", "year", "yesterday", "today",
)

/** Předložky a spojky, které na kraji dotazu nic nehledají (celá slova bez diakritiky). */
private val EDGE_WORDS = setOf(
    "z", "ze", "v", "ve", "na", "s", "se", "od", "do", "za", "o", "a", "k", "ke", "u", "po", "pro", "mezi",
    "from", "in", "on", "of", "with", "for", "about", "the", "an",
)

private val CONTENT_STEMS: Map<List<String>, SearchContent> = mapOf(
    listOf("fotk", "foto", "obrazk", "obrazek", "photo", "picture") to SearchContent.Photos,
    listOf("video", "videa", "videi") to SearchContent.Videos,
    listOf("soubor", "dokument", "file", "pdf") to SearchContent.Files,
    listOf("odkaz", "link") to SearchContent.Links,
    listOf("hlasov", "voice") to SearchContent.Voice,
    listOf("hudb", "pisnic", "music", "song") to SearchContent.Music,
)

private val CHAT_STEMS: Map<List<String>, ChatKind> = mapOf(
    listOf("kanal", "channel") to ChatKind.Channel,
    listOf("skupin", "group") to ChatKind.Group,
    listOf("soukrom", "private") to ChatKind.Private,
)

/** Začátky slov, která popisují filtr, ne téma. */
private val FILTER_STEMS = CONTENT_STEMS.keys.flatten() + CHAT_STEMS.keys.flatten()

private fun period(from: LocalDate?, to: LocalDate?, today: LocalDate, timeZone: TimeZone): SearchPeriod? {
    if (from == null && to == null) return null
    var start = (from ?: TELEGRAM_START).coerceIn(TELEGRAM_START, today)
    var end = (to ?: today).coerceIn(TELEGRAM_START, today)
    if (start > end) start = end.also { end = start }
    return SearchPeriod.Custom(start.atStartOfDayIn(timeZone).epochSeconds, end.atStartOfDayIn(timeZone).epochSeconds)
}

private fun date(value: String): LocalDate? = DATE.find(value)?.let { match ->
    val (year, month, day) = match.destructured
    runCatching { LocalDate(year.toInt(), month.toInt(), day.toInt()) }.getOrNull()
}

private val DATE = Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})")

private val EMPTY_VALUES = setOf("", "NONE", "EMPTY", "-")

/** Dřív zprávy v Telegramu nejsou; „do loňska" bez začátku se počítá od tehdy. */
private val TELEGRAM_START = LocalDate(2013, 8, 1)

/**
 * Hledání přirozeným jazykem modelem v zařízení. Jako termín připomínky (`ReminderTimeModel`):
 * jen se zapnutou AI a modelem, který je PŘIPRAVENÝ — kvůli hledání se nic nestahuje.
 */
class NaturalSearchModel(private val ai: OnDeviceAi, private val settings: AppSettings) {
    private val logger = Logger.withTag("NaturalSearch")

    /** Nabídnout „Hledat s AI"? Mění se se zapnutím AI a se stažením modelu. */
    val available: Flow<Boolean> = combine(settings.onDeviceAiEnabled, settings.onDeviceAiVariant) { enabled, variant ->
        enabled to variant
    }.let { input ->
        flow {
            input.collect { (enabled, variant) ->
                emit(enabled && runCatching { ai.status(variant) == OnDeviceAiStatus.Ready }.getOrDefault(false))
            }
        }.catch { emit(false) }
    }

    suspend fun parse(query: String, today: LocalDate, timeZone: TimeZone): NaturalSearch? {
        if (query.isBlank()) return null
        return try {
            withTimeoutOrNull(TIMEOUT_MS) {
                val fields = ai.generateAnswer(
                    settings.onDeviceAiVariant.value,
                    SearchAnswer::class,
                    prompt = { structured -> searchPrompt(query, today, structured) },
                    fromStructured = ::searchFieldsOf,
                    fromText = ::searchFields,
                ) ?: return@withTimeoutOrNull null
                refineNaturalSearch(naturalSearchFrom(fields, today, timeZone), query, today, timeZone)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.i(e) { "Hledání z věty se nepodařilo vyčíst" }
            null
        }
    }

    private companion object {
        const val TIMEOUT_MS = 60_000L
    }
}
