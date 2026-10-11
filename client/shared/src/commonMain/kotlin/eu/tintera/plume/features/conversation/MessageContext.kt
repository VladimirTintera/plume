package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.MessageEntity
import eu.tintera.plume.core.telegram.MessageEntityType
import eu.tintera.plume.core.ui.foldDiacritics
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Něco ve zprávě, s čím jde něco udělat — za tím je ikonka u zprávy a sheet s akcemi
 * (`MessageContextSheet`).
 */
sealed interface ContextFinding {
    /** Termín → událost v kalendáři. */
    data class Event(
        val start: LocalDateTime,
        /** Datum bez času („12. 10.") — celodenní událost. */
        val allDay: Boolean,
        val title: String,
        /** Místo; jen od modelu, pravidla ho nehledají. */
        val place: String? = null,
        /** Celý text zprávy, jde do popisu události. */
        val description: String,
        /** Název a místo doplnil a termín potvrdil model v zařízení. */
        val fromModel: Boolean = false,
    ) : ContextFinding

    /** Číslo účtu → QR platba a kopírování údajů. */
    data class Payment(
        /** Účet jako IBAN bez mezer; QR platba jinou podobu nezná. */
        val iban: String,
        /** Účet tak, jak se ukazuje: české číslo („19-2000145399/0800"), jinak IBAN po čtveřicích. */
        val account: String,
        /** Částka s desetinnou tečkou („1250.50"), nebo `null`, když ve zprávě není jednoznačná. */
        val amount: String? = null,
        /** Měna k [amount] (ISO 4217). */
        val currency: String? = null,
        val variableSymbol: String? = null,
    ) : ContextFinding

    /** Adresa → mapy. [text] je adresa tak, jak ve zprávě stojí („Vinohradská 12, Praha 2"). */
    data class Address(val text: String) : ContextFinding

    /**
     * Telefon nebo e-mail → uložit do kontaktů. [name] jen tam, kde ve zprávě jasně stojí u čísla
     * („Jan Novák: 602 123 456"); jinak ho uživatel vyplní v editoru kontaktu.
     */
    data class Contact(val name: String?, val phone: String?, val email: String?) : ContextFinding

    /** Číslo zásilky → sledování u dopravce. */
    data class Parcel(val carrier: ParcelCarrier, val number: String) : ContextFinding
}

enum class ParcelCarrier {
    /** Česká pošta: mezinárodní formát S10 končící `CZ`. */
    CzechPost,

    /** Zásilkovna (Packeta): `Z` a deset číslic. */
    Packeta,

    /** S10 z jiné země; sleduje se přes 17TRACK, který zná pošty celého světa. */
    International,
}

/** Termín ze zprávy, než ho případně posoudí model. */
data class EventCandidate(
    val start: LocalDateTime,
    val allDay: Boolean,
    /**
     * Pravidla si jsou jistá sama: den I čas, datum číslem, nebo termín od Telegramu. Jen čas
     * („v 15:00") nebo jen den („v pátek") jistý není — „v 15:00 jsem byl u doktora" schůzka
     * není. Nejistý termín se nabídne, jen když ho potvrdí model.
     */
    val certain: Boolean,
)

/** Co pravidla ve zprávě našla. Levné a bez modelu; zbytek skládá [contextFindings]. */
data class MessageContext(
    val event: EventCandidate? = null,
    val payments: List<ContextFinding.Payment> = emptyList(),
    val addresses: List<ContextFinding.Address> = emptyList(),
    val contacts: List<ContextFinding.Contact> = emptyList(),
    val parcels: List<ContextFinding.Parcel> = emptyList(),
) {
    val isEmpty: Boolean
        get() = event == null && payments.isEmpty() && addresses.isEmpty() && contacts.isEmpty() && parcels.isEmpty()
}

/**
 * Rozpoznání kontextu zprávy pravidly — termín, účet, zásilka. Česky i anglicky, bez modelu:
 * pravidla jdou otestovat, běží všude (i na iOS a bez AI) a nic se kvůli nim nikam neposílá.
 *
 * - **Termín** bere pravidla připomínky ([textTime]) a termín, který ve zprávě vyznačil sám
 *   Telegram (`MessageEntityType.DateTime`, přesný okamžik).
 * - **Účet** jen ověřený kontrolním součtem: české číslo s kódem banky ze seznamu ČNB
 *   ([BANK_CODES]), IBAN přes modulo 97. „Č. j. 1234/2025" tak účtem není.
 * - **Adresa** jen s jasnou stavbou: ulice, číslo, čárka a město, a k tomu PSČ, nebo ulice, která
 *   jako ulice vypadá („Vinohradská", „nám. Míru", „Na Příkopě"); anglicky číslo, název a druh
 *   ulice („221B Baker Street"). „Kapitola 12, Praha" adresou není.
 * - **Kontakt** z telefonů a e-mailů, které ve zprávě vyznačil Telegram (`tel:`, `mailto:`), a kde
 *   značka chybí, z mezinárodního čísla nebo českých devíti číslic. Číslo, které je kusem účtu,
 *   kontaktem není.
 * - **Zásilka** jen ve formátu, který dopravce jednoznačně určí: S10 s kontrolní číslicí
 *   a Zásilkovna. Holá čísla PPL nebo DPD od telefonu nerozeznáš.
 */
fun findMessageContext(
    text: String,
    entities: List<MessageEntity>,
    sentAt: LocalDateTime,
    zone: TimeZone,
): MessageContext {
    if (text.isBlank()) return MessageContext()
    val payments = payments(text)
    return MessageContext(
        event = eventCandidate(text, entities, sentAt, zone),
        payments = payments,
        addresses = addresses(text),
        contacts = contacts(text, entities, payments),
        parcels = parcels(text),
    )
}

/**
 * Co se uživateli nabídne. Termín podle posudku modelu [verdict]:
 *
 * - **bez posudku** (AI vypnutá, model nepřipravený) a **dokud model posuzuje** jen jistý termín
 *   z pravidel. Nejistý by naskočil a po odmítnutí zmizel; jistý model odmítne zřídka, a čekat
 *   na něj by znamenalo ikonku o vteřiny později u každé schůzky,
 * - **potvrzený** i s názvem a místem od modelu, **odmítnutý** vůbec.
 *
 * Termín, který už minul ([now]), se nenabízí: do kalendáře nepatří.
 */
fun MessageContext.contextFindings(
    text: String,
    verdict: EventVerdict?,
    now: LocalDateTime,
): List<ContextFinding> = buildList {
    event?.takeIf { if (it.allDay) it.start.date >= now.date else it.start > now }?.let { candidate ->
        val fallback = ContextFinding.Event(
            start = candidate.start,
            allDay = candidate.allDay,
            title = eventTitle(text),
            description = text.trim(),
        )
        when (verdict) {
            null, EventVerdict.Unavailable, EventVerdict.Pending -> if (candidate.certain) add(fallback)
            EventVerdict.Rejected -> Unit
            is EventVerdict.Confirmed -> add(
                fallback.copy(
                    title = verdict.title ?: fallback.title,
                    place = verdict.place,
                    fromModel = true,
                ),
            )
        }
    }
    addAll(payments)
    addAll(addresses)
    addAll(contacts)
    addAll(parcels)
}

/** Posudek termínu modelem v zařízení (`MessageContexts`). */
sealed interface EventVerdict {
    /** Model zprávu teprve posoudí. */
    data object Pending : EventVerdict

    /** Model nejde použít (není připravený, selhal) — platí pravidla. */
    data object Unavailable : EventVerdict

    /** Je to schůzka nebo událost; název a místo, když je model dal. */
    data class Confirmed(val title: String?, val place: String?) : EventVerdict

    /** Není to nic do kalendáře („v 15:00 jsem byl u doktora"). */
    data object Rejected : EventVerdict
}

/** Odkaz na sledování zásilky; [language] je jazyk aplikace (Zásilkovna má stránku česky i anglicky). */
fun ContextFinding.Parcel.trackingUrl(language: String): String = when (carrier) {
    ParcelCarrier.CzechPost -> "https://www.postaonline.cz/trackandtrace/-/zasilka/cislo?parcelNumbers=$number"
    ParcelCarrier.Packeta -> "https://tracking.packeta.com/${if (language == "cs") "cs" else "en"}/$number"
    ParcelCarrier.International -> "https://t.17track.net/en#nums=$number"
}

/**
 * QR platba (formát SPAYD, „QR Platba" České bankovní asociace), jak ji čtou české bankovní
 * aplikace. Hvězdička je v SPAYD oddělovač, takže v hodnotách být nesmí — žádná z nich ji
 * nenese (IBAN, číslo, kód měny, číslice).
 */
fun ContextFinding.Payment.spayd(): String = buildString {
    append("SPD*1.0*ACC:").append(iban)
    amount?.let { append("*AM:").append(it) }
    currency?.takeIf { amount != null }?.let { append("*CC:").append(it) }
    variableSymbol?.let { append("*X-VS:").append(it) }
}

/** Název události bez modelu: první řádek zprávy, zkrácený na celé slovo. */
internal fun eventTitle(text: String): String {
    val line = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        .replace(WHITESPACE, " ")
    if (line.length <= MAX_TITLE_CHARS) return line
    val cut = line.take(MAX_TITLE_CHARS).substringBeforeLast(' ').ifEmpty { line.take(MAX_TITLE_CHARS) }
    return cut.trimEnd(',', '.', ';', ':', '-', ' ') + "…"
}

private fun eventCandidate(
    text: String,
    entities: List<MessageEntity>,
    sentAt: LocalDateTime,
    zone: TimeZone,
): EventCandidate? {
    // Termín od Telegramu je přesný okamžik, který pisatel výslovně vložil — jistější už nebude.
    entities.firstNotNullOfOrNull { it.type as? MessageEntityType.DateTime }?.let { stamp ->
        val at = Instant.fromEpochSeconds(stamp.unixTime).toLocalDateTime(zone)
        return EventCandidate(at, allDay = false, certain = true)
    }
    // Minulost („včera v 15:00 jsem byl…") do kalendáře nepatří. Pravidla vztažné „včera" neznají
    // a čas by posunula na příští den; model to pak posoudil jako schůzku (Gemma 4 E2B).
    if (PAST.containsMatchIn(foldDiacritics(text).lowercase())) return null
    val time = textTime(text, sentAt) ?: return null
    return when {
        time.hasDay && (time.hasClock || time.hasPart) -> EventCandidate(time.at, allDay = false, certain = true)
        // Datum číslem bez času („narozeniny 12. 10.") je celodenní událost.
        time.hasDate -> EventCandidate(time.at, allDay = true, certain = true)
        // Jen čas: rozhodne model. Český minulý čas („volal jsem ti v 9:30") ale ne — model ho
        // za schůzku vydával i s příkladem v promptu (Gemma 4 E2B). U termínu se dnem se
        // nekontroluje: „domluvili jsme se na zítra v 15" je minulý čas a přitom schůzka.
        time.hasClock && !PAST_TENSE.containsMatchIn(foldDiacritics(text).lowercase()) ->
            EventCandidate(time.at, allDay = false, certain = false)
        else -> null
    }
}

// --- Účet ------------------------------------------------------------------------------------

private fun payments(text: String): List<ContextFinding.Payment> {
    val accounts = (czechAccounts(text) + ibans(text)).distinctBy { it.iban }.take(MAX_ACCOUNTS)
    if (accounts.isEmpty()) return emptyList()
    // Částka a symbol patří k účtu, jen když je účet jediný — u dvou by se nevědělo, ke kterému.
    if (accounts.size > 1) return accounts
    val amount = amount(text)
    return listOf(
        accounts.single().copy(
            amount = amount?.first,
            currency = amount?.second,
            variableSymbol = variableSymbol(text),
        ),
    )
}

private fun czechAccounts(text: String): List<ContextFinding.Payment> = CZECH_ACCOUNT.findAll(text).mapNotNull { match ->
    val prefix = match.groupValues[1]
    val number = match.groupValues[2]
    val bank = match.groupValues[3]
    if (bank !in BANK_CODES || !mod11(prefix.padStart(6, '0')) || !mod11(number.padStart(10, '0'))) return@mapNotNull null
    // Číslo z jedné nenulové číslice by kontrolou prošlo, účet to ale není.
    if (number.count { it != '0' } < 2) return@mapNotNull null
    ContextFinding.Payment(
        iban = czechIban(prefix, number, bank),
        account = (if (prefix.isNotEmpty()) "$prefix-" else "") + "$number/$bank",
    )
}.toList()

private fun ibans(text: String): List<ContextFinding.Payment> = IBAN.findAll(text).mapNotNull { match ->
    val compact = match.value.filterNot { it.isWhitespace() }
    // Regulár může spolknout i slovo za IBANem („… 5399 PROSIM"); zkouší se postupně kratší.
    (compact.length downTo MIN_IBAN).firstNotNullOfOrNull { length ->
        compact.take(length).takeIf { ibanValid(it) }
    }?.let { iban -> ContextFinding.Payment(iban = iban, account = iban.chunked(4).joinToString(" ")) }
}.toList()

/** Vážený součet číslic českého účtu (vyhláška ČNB): musí být dělitelný jedenácti. */
private fun mod11(digits: String): Boolean {
    val weights = ACCOUNT_WEIGHTS.takeLast(digits.length)
    return digits.mapIndexed { index, digit -> digit.digitToInt() * weights[index] }.sum() % 11 == 0
}

/** České číslo účtu jako IBAN: `CZkk` + kód banky + předčíslí (6) + číslo (10). */
internal fun czechIban(prefix: String, number: String, bank: String): String {
    val bban = bank + prefix.padStart(6, '0') + number.padStart(10, '0')
    val check = 98 - mod97(bban + "CZ00")
    return "CZ" + check.toString().padStart(2, '0') + bban
}

internal fun ibanValid(iban: String): Boolean =
    iban.length in MIN_IBAN..MAX_IBAN && iban.all { it.isLetterOrDigit() } &&
        mod97(iban.drop(4) + iban.take(4)) == 1

/** Zbytek po dělení 97 čísla, kde písmena znamenají 10–35 (ISO 13616). */
private fun mod97(value: String): Int = value.fold(0) { rest, char ->
    val digits = if (char.isDigit()) char.digitToInt().toString() else (char.uppercaseChar() - 'A' + 10).toString()
    digits.fold(rest) { acc, digit -> (acc * 10 + digit.digitToInt()) % 97 }
}

/** Částka a měna, když je ve zprávě právě jedna. */
private fun amount(text: String): Pair<String, String>? {
    val found = buildSet {
        AMOUNT_AFTER.findAll(text).forEach { match ->
            val currency = if (match.groupValues[4].lowercase().let { it == "eur" || it == "€" }) "EUR" else "CZK"
            add(normalizeAmount(match.groupValues[1], match.groupValues[2].ifEmpty { match.groupValues[3] }) to currency)
        }
        EURO_BEFORE.findAll(text).forEach { match ->
            add(normalizeAmount(match.groupValues[1], match.groupValues[2].ifEmpty { match.groupValues[3] }) to "EUR")
        }
    }
    return found.singleOrNull()
}

private fun normalizeAmount(whole: String, fraction: String): String =
    whole.filter { it.isDigit() }.trimStart('0').ifEmpty { "0" } + "." + fraction.padEnd(2, '0')

private fun variableSymbol(text: String): String? =
    VARIABLE_SYMBOL.findAll(foldDiacritics(text).lowercase()).map { it.groupValues[1] }.distinct().singleOrNull()

// --- Adresa ----------------------------------------------------------------------------------

/**
 * Adresy ze zprávy. Hledá se v textu bez diakritiky ([foldDiacritics] délku ani velikost písmen
 * nemění), takže klíčová slova jdou psát bez háčků; vrací se ale úsek PŮVODNÍHO textu.
 */
private fun addresses(text: String): List<ContextFinding.Address> {
    val folded = foldDiacritics(text)
    val czech = CZECH_ADDRESS.findAll(folded).filter { match ->
        val hasPostal = match.groups[POSTAL_GROUP] != null
        val keyword = match.groups[KEYWORD_GROUP] != null || match.groups[PREPOSITION_GROUP] != null
        val street = match.groups[STREET_GROUP]?.value.orEmpty().lowercase()
        hasPostal || keyword || STREET_SUFFIXES.any { street.endsWith(it) }
    }
    val english = ENGLISH_ADDRESS.findAll(folded)
    return (czech + english)
        .map { ContextFinding.Address(text.substring(it.range).trimEnd(',', '.', ' ')) }
        .distinctBy { it.text }
        .take(MAX_ADDRESSES)
        .toList()
}

// --- Kontakt ---------------------------------------------------------------------------------

/** Telefon nebo e-mail ve zprávě: kde začíná a jak ho psal pisatel. */
private class Reachable(val start: Int, val value: String, val isPhone: Boolean)

/**
 * Kontakty ze zprávy. Jeden telefon a nejvýš jeden e-mail jsou JEDEN kontakt (vizitka „Jan Novák,
 * 602 123 456, jan@firma.cz"); víc telefonů je víc kontaktů, každý se jménem u sebe.
 */
private fun contacts(
    text: String,
    entities: List<MessageEntity>,
    payments: List<ContextFinding.Payment>,
): List<ContextFinding.Contact> {
    // Číslo účtu i variabilní symbol mívají devět číslic jako telefon.
    val accountDigits = payments.flatMap { listOfNotNull(it.iban.filter(Char::isDigit), it.variableSymbol) }
    val found = buildList {
        entities.forEach { entity ->
            val url = (entity.type as? MessageEntityType.Link)?.url ?: return@forEach
            val start = entity.offset.coerceIn(0, text.length)
            when {
                url.startsWith("tel:") -> add(Reachable(start, url.removePrefix("tel:"), isPhone = true))
                url.startsWith("mailto:") -> add(Reachable(start, url.removePrefix("mailto:"), isPhone = false))
            }
        }
        // Kde Telegram číslo nevyznačil (odesílaná zpráva, starší klient), poslouží regulár.
        PHONE.findAll(text).forEach { add(Reachable(it.range.first, it.value, isPhone = true)) }
        EMAIL.findAll(text).forEach { add(Reachable(it.range.first, it.value, isPhone = false)) }
    }
        .filterNot { it.isPhone && accountDigits.any { account -> it.value.filter(Char::isDigit) in account } }
        .filter { !it.isPhone || it.value.count(Char::isDigit) in MIN_PHONE_DIGITS..MAX_PHONE_DIGITS }
        .distinctBy { if (it.isPhone) it.value.filter(Char::isDigit).takeLast(9) else it.value.lowercase() }
        .sortedBy { it.start }

    val phones = found.filter { it.isPhone }
    val emails = found.filterNot { it.isPhone }
    if (phones.size <= 1 && emails.size <= 1) {
        val first = phones.firstOrNull() ?: emails.firstOrNull() ?: return emptyList()
        return listOf(ContextFinding.Contact(nameBefore(text, first.start), phones.firstOrNull()?.value, emails.firstOrNull()?.value))
    }
    return found.take(MAX_CONTACTS).map {
        ContextFinding.Contact(nameBefore(text, it.start), phone = it.value.takeIf { _ -> it.isPhone }, email = it.value.takeUnless { _ -> it.isPhone })
    }
}

/**
 * Jméno těsně před číslem, jen v jasné stavbě: dvě slova velkým písmenem a za nimi oddělovač
 * nebo „tel." („Jan Novák: 602…", „Jan Novák, tel. 602…", „Jan Novák (602…"). Bez oddělovače
 * by se jménem stalo „Zavolej Petrovi 602…".
 */
private fun nameBefore(text: String, start: Int): String? =
    NAME_BEFORE.find(text.substring(0, start))?.let { "${it.groupValues[1]} ${it.groupValues[2]}" }

// --- Zásilka ---------------------------------------------------------------------------------

private fun parcels(text: String): List<ContextFinding.Parcel> = buildList {
    S10.findAll(text).forEach { match ->
        val digits = match.groupValues[2]
        if (!s10Valid(digits)) return@forEach
        val country = match.groupValues[3]
        val carrier = if (country == "CZ") ParcelCarrier.CzechPost else ParcelCarrier.International
        add(ContextFinding.Parcel(carrier, match.value))
    }
    PACKETA.findAll(text).forEach { match ->
        add(ContextFinding.Parcel(ParcelCarrier.Packeta, "Z" + match.groupValues.drop(1).joinToString("")))
    }
}.distinctBy { it.number }.take(MAX_PARCELS)

/** Kontrolní číslice S10 (UPU): váhy 8 6 4 2 3 5 9 7, 11 − zbytek; 10 → 0, 11 → 5. */
internal fun s10Valid(digits: String): Boolean {
    if (digits.length != 9) return false
    val sum = digits.take(8).mapIndexed { index, digit -> digit.digitToInt() * S10_WEIGHTS[index] }.sum()
    val check = when (val raw = 11 - sum % 11) {
        10 -> 0
        11 -> 5
        else -> raw
    }
    return digits.last().digitToInt() == check
}

private const val MAX_TITLE_CHARS = 60
private const val MAX_ACCOUNTS = 2
private const val MAX_PARCELS = 3
private const val MAX_ADDRESSES = 2
private const val MAX_CONTACTS = 2
private const val MIN_PHONE_DIGITS = 9
private const val MAX_PHONE_DIGITS = 15
private const val MIN_IBAN = 15
private const val MAX_IBAN = 34

private val WHITESPACE = Regex("\\s+")

private val ACCOUNT_WEIGHTS = listOf(6, 3, 7, 9, 10, 5, 8, 4, 2, 1)
private val S10_WEIGHTS = listOf(8, 6, 4, 2, 3, 5, 9, 7)

private val CZECH_ACCOUNT = Regex("(?<![\\d/-])(?:(\\d{1,6})-)?(\\d{2,10})/(\\d{4})(?![\\d/])")
private val IBAN = Regex("(?<![A-Za-z0-9])[A-Z]{2}\\d{2}(?: ?[A-Z0-9]{4}){2,7}(?: ?[A-Z0-9]{1,3})?(?![A-Za-z0-9])")

/**
 * „1 250 Kč", „1.250,50 CZK", „250,- Kč", „30 EUR", „12.50 €". Tisíce mezerou nebo tečkou.
 * „Kč" je escapované (`\u010d`): je to data pro regulár, a kontrola textu v kódu
 * (`verifyNoHardcodedText`) diakritiku mimo komentáře nepustí — stejně jako u `SearchHighlight`.
 */
private val AMOUNT_AFTER = Regex(
    "(?<![\\d.,])(\\d{1,3}(?:[ \\u00A0.]\\d{3})+|\\d+)(?:,(\\d{1,2})|\\.(\\d{1,2}))?(?:,-|,–)?\\s?(K\u010d|Kc|CZK|korun\\p{L}*|EUR|€)(?![\\p{L}])",
    RegexOption.IGNORE_CASE,
)
private val EURO_BEFORE = Regex("€\\s?(\\d{1,3}(?:[ \\u00A0,]\\d{3})+|\\d+)(?:,(\\d{1,2})|\\.(\\d{1,2}))?(?![\\d])")

/** „VS: 123", „v. s. 123", „variabilní symbol 123" — v textu bez diakritiky a malými písmeny. */
private val VARIABLE_SYMBOL = Regex(
    "(?<![\\p{L}])(?:vs|v\\.\\s?s\\.|variabilni symbol|var\\.\\s?symbol|variable symbol)\\s*[:.=]?\\s*(\\d{1,10})(?!\\d)",
)

/**
 * Český minulý čas: příčestí na -l s pomocným slovesem („volal jsem", „jsem tam byl"). Jen pro
 * termín bez dne, viz [eventCandidate].
 */
private val PAST_TENSE = Regex(
    "(?<![\\p{L}])(?:\\p{L}+(?:l|la|li|lo|ly)\\s+(?:jsem|jsi|jsme|jste)|(?:jsem|jsi|jsme|jste)\\s+(?:\\p{L}+\\s+)?\\p{L}+(?:l|la|li|lo|ly))(?![\\p{L}])",
)

/** Slova minulosti, česky bez diakritiky a anglicky. */
private val PAST = Regex(
    "(?<![\\p{L}])(?:vcera|vcerejs\\p{L}*|predevcirem|minul(?:y|ou|e|eho|ym)|yesterday|last (?:week|night|month|year|monday|tuesday|wednesday|thursday|friday|saturday|sunday)|ago)(?![\\p{L}])",
)

/** Velkým písmenem začínající slovo, i se spojovníkem („Frydek-Mistek"). */
private const val CAP_WORD = "\\p{Lu}[\\p{L}\\-]*"

/** Město: slovo, a za ním nejvýš číslo obvodu („Praha 10") nebo „nad/pod X" („Usti nad Labem"). */
private const val CITY = "$CAP_WORD(?:\\s\\d{1,2}(?![\\d/])|\\s(?:nad|pod)\\s$CAP_WORD)?"

/**
 * Česká adresa: [klíčové slovo] [předložka] Ulice číslo, [PSČ] Město. Ulice je JEDNO slovo
 * velkým písmenem (s předložkou nebo klíčovým slovem před ním) — víc slov by spolklo i slovo
 * před ulicí („Sraz Vinohradska 12"). Víceslovné ulice („Karla Englise 3") tak nenajde; radši nic.
 */
private val CZECH_ADDRESS = Regex(
    "(?<![\\p{L}\\d])" +
        // Velikost písmen ručně, ne `(?i:…)`: vložené příznaky regexů nejsou na všech platformách stejné.
        "(?<keyword>(?:[Uu]l\\.|[Uu]lice|[Nn]am\\.|[Nn]amesti|[Tt]r\\.|[Tt]rida|[Nn]abr\\.|[Nn]abrezi)\\s+)?" +
        "(?<preposition>(?:[Nn]a|[Nn]ad|[Pp]od|[Uu]|[Vv]|[Vv]e|[Zz]a|[Pp]red|[Kk])\\s+)?" +
        "(?<street>$CAP_WORD)\\s+\\d{1,5}(?:/\\d{1,5})?[a-zA-Z]?,\\s*" +
        "(?:(?<postal>\\d{3}\\s?\\d{2})\\s+)?$CITY",
)

private const val KEYWORD_GROUP = "keyword"
private const val PREPOSITION_GROUP = "preposition"
private const val STREET_GROUP = "street"
private const val POSTAL_GROUP = "postal"

/** Koncovky, podle kterých slovo vypadá jako název ulice (Vinohradska, Palackeho, Karlova…). */
private val STREET_SUFFIXES = listOf("ska", "cka", "ova", "ovo", "ni", "eho", "ych", "ich", "ske", "strasse")

/** Anglická adresa: číslo, jedno až tři slova a druh ulice („221B Baker Street, London"). */
private val ENGLISH_ADDRESS = Regex(
    "(?<![\\p{L}\\d])\\d{1,5}[A-Za-z]?\\s+(?:\\p{Lu}[\\p{L}'\\-]*\\s+){1,3}" +
        "(?:Street|St|Road|Rd|Avenue|Ave|Lane|Ln|Boulevard|Blvd|Drive|Square|Sq|Place|Way|Court|Terrace|Crescent|Close)\\.?" +
        "(?![\\p{L}])(?:,\\s*$CAP_WORD(?:\\s$CAP_WORD)?)?",
)

/** Mezinárodní číslo („+420 602 123 456") nebo české devítimístné („602 123 456", začíná 2–9). */
private val PHONE = Regex(
    // Písmeno těsně u čísla znamená kód (zásilka „RR223456785CZ"), ne telefon.
    "(?<![\\p{L}\\d+/\\-])(?:\\+\\d{1,3}(?:[ \\u00A0-]?\\d{2,4}){2,5}|[2-9]\\d{2}[ \\u00A0]?\\d{3}[ \\u00A0]?\\d{3})(?![\\p{L}\\d/])",
)

private val EMAIL = Regex("(?<![\\w.+-])[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+(?![\\w-])")

/** Dvě slova jména a za nimi oddělovač nebo „tel." — na KONCI textu před číslem. */
private val NAME_BEFORE = Regex(
    "(\\p{Lu}\\p{Ll}+)\\s+(\\p{Lu}\\p{Ll}+)\\s*(?:[,:(\\-\\u2013]\\s*(?:(?:tel|mob|mobil|telefon|phone)\\.?:?\\s*)?|(?:tel|mob|mobil|telefon|phone)\\.?:?\\s*)$",
)

private val S10 = Regex("(?<![A-Za-z0-9])([A-Z]{2})(\\d{9})([A-Z]{2})(?![A-Za-z0-9])")
private val PACKETA = Regex("(?<![A-Za-z0-9])Z ?(\\d{3}) ?(\\d{4}) ?(\\d{3})(?![\\d])")

/**
 * Kódy bank a spořitelen v Česku (číselník ČNB). Chybějící kód znamená jen to, že se účet
 * nerozpozná — bezpečný směr; seznam je tu kvůli tomu, aby „č. j. 1234/2025" účtem nebylo.
 */
private val BANK_CODES = setOf(
    "0100", "0300", "0600", "0710", "0800", "2010", "2020", "2030", "2060", "2070", "2100", "2200",
    "2220", "2250", "2260", "2275", "2600", "2700", "3030", "3050", "3060", "3500", "4000", "4300",
    "5500", "5800", "6000", "6100", "6200", "6210", "6300", "6363", "6700", "6800", "7910", "7950",
    "7960", "7970", "7990", "8030", "8040", "8060", "8090", "8150", "8190", "8198", "8199", "8200",
    "8220", "8230", "8240", "8250", "8255", "8265", "8270", "8280", "8293", "8299", "8500",
)
