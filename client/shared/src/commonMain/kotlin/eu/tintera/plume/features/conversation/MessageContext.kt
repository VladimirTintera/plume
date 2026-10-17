package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.MessageEntity
import eu.tintera.plume.core.telegram.MessageEntityType
import eu.tintera.plume.core.ui.foldDiacritics
import kotlinx.datetime.LocalDateTime
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.days
import kotlinx.datetime.toInstant
import kotlinx.datetime.atTime
import kotlinx.datetime.LocalTime
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

    /** Číslo letu → stav letu. [airline] je kód IATA („OK"), [number] číslo („123"). */
    data class Flight(
        val airline: String,
        val number: String,
        /**
         * Čas letu ze zprávy (termín, který v ní našla pravidla), nebo `null` — stav letu se jinde
         * nezjistí. S ním jde let do kalendáře.
         */
        val at: LocalDateTime? = null,
        /** Ze zprávy je jen den, ne čas. */
        val allDay: Boolean = false,
        /** Čas je PŘÍLET („přistání ve 14:30"), ne odlet. */
        val arrival: Boolean = false,
        /** Letiště odletu a příletu z trasy ve zprávě („PRG → LHR"), kódy IATA z [AIRPORTS]. */
        val from: String? = null,
        val to: String? = null,
        /** Druhý čas ve zprávě s odletem — přistání („odlet 9:40, přílet 10:55"), místní čas letiště [to]. */
        val landsAt: LocalDateTime? = null,
    ) : ContextFinding {
        /** Jak se let píše na palubní vstupence a hledá: „OK123". */
        val code: String get() = airline + number
    }

    /** Wi-Fi s heslem → připojit (Android), zkopírovat heslo, QR kód sítě pro jiný telefon. */
    data class Wifi(val ssid: String, val password: String) : ContextFinding

    /** Kód rezervace (letenka, hotel, vlak) — do schránky, kde ho chce web nebo aplikace dopravce. */
    data class Booking(val value: String) : ContextFinding

    /** Kód ke zkopírování (slevový, ke dveřím, ověřovací) — jedním ťuknutím do schránky. */
    data class Code(val value: String) : ContextFinding

    /**
     * Částka v cizí měně → převod na měnu uživatele. [shown] je, jak ve zprávě stála („120 €"),
     * [amount] totéž jako číslo, [currency] kód ISO 4217.
     */
    data class Money(
        val shown: String,
        val amount: Double,
        val currency: String,
        /**
         * Měna je odhad z dvojznačné značky (`¥` = jen, nebo jüan; „pounds" = libry, nebo váha),
         * kterou okolí zprávy nerozhodlo. Sheet pak měnu řekne a nabídne i druhou možnost.
         */
        val assumed: Boolean = false,
    ) : ContextFinding

    /**
     * Míra v jednotkách „druhé" soustavy → převod („65 palců" ≈ 165,1 cm metrickému uživateli,
     * „5 km" ≈ 3,1 mi americkému). [shown] jak stála ve zprávě, [value] číslo, [unit] jednotka.
     */
    data class Measure(val shown: String, val value: Double, val unit: MeasureUnit) : ContextFinding

    /** Číslo zásilky → sledování u dopravce. */
    data class Parcel(val carrier: ParcelCarrier, val number: String) : ContextFinding
}

enum class ParcelCarrier {
    /** Česká pošta: mezinárodní formát S10 končící `CZ`. */
    CzechPost,

    /** Zásilkovna (Packeta): `Z` a deset číslic. */
    Packeta,

    /** PPL: jedenáct číslic, jen se slovem „PPL" poblíž (holé číslo od telefonu nerozeznáš). */
    Ppl,

    /** DPD: čtrnáct číslic, jen se slovem „DPD" poblíž. */
    Dpd,

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
    val flights: List<ContextFinding.Flight> = emptyList(),
    val wifi: ContextFinding.Wifi? = null,
    val bookings: List<ContextFinding.Booking> = emptyList(),
    val codes: List<ContextFinding.Code> = emptyList(),
    val money: List<ContextFinding.Money> = emptyList(),
    val measures: List<ContextFinding.Measure> = emptyList(),
    val parcels: List<ContextFinding.Parcel> = emptyList(),
) {
    val isEmpty: Boolean
        get() = event == null && payments.isEmpty() && addresses.isEmpty() && contacts.isEmpty() &&
            flights.isEmpty() && wifi == null && bookings.isEmpty() && codes.isEmpty() && money.isEmpty() && measures.isEmpty() &&
            parcels.isEmpty()
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
 * - **Let** jen s kódem známé letecké společnosti ([AIRLINES]) a slovem o letu ve zprávě („let",
 *   „odlet", „letiště", „flight", „gate"…). Samo „OK 123" nebo „LO 5" je spíš cokoli jiného.
 * - **Wi-Fi** jen s názvem sítě I heslem o 8–63 znacích (víc ani míň WPA nedovolí):
 *   „Wi-Fi: Plume_Home, heslo: tajne1234", nebo zápis z QR kódu `WIFI:S:…;P:…;;`.
 * - **Kód rezervace** za „rezervace", „číslo rezervace", „booking", „confirmation", „PNR"…:
 *   5–16 znaků z velkých písmen a číslic (i s tečkami, „4123.456.789"), čistě číselný 6–12 číslic.
 *   Tatáž hodnota se pak nenabízí ještě jako obecný kód.
 * - **Kód** jen za klíčovým slovem („kód", „code", „voucher", „promo", „kupón", „PIN") a jen
 *   z velkých písmen a číslic: číselný 3–8 číslic, smíšený 4–16 znaků. **Ve zprávě, která
 *   zmiňuje Telegram, žádný** — přihlašovací kód Telegramu se nikam nekopíruje (ověřovací
 *   kód je přesně to, co podvodník chce, a zprávy od služby Telegramu akce nemají vůbec).
 * - **Cizí měna** jen se značkou nebo kódem u čísla („120 €", „$45", „300 PLN", „2 500 Ft")
 *   a jen jiná než [homeCurrency] — korunu Čechovi převádět nemá smysl.
 * - **Jednotky** jen té soustavy, kterou uživatel NEpoužívá ([metric]): metrickému míle, stopy,
 *   palce, libry, unce, galony, °F a mph; americkému km, m, cm, kg, g, litry, °C a km/h.
 * - **Zásilka** jen ve formátu, který dopravce jednoznačně určí: S10 s kontrolní číslicí
 *   a Zásilkovna. Holá čísla PPL (11 číslic) a DPD (14) kontrolní součet nemají a od telefonu
 *   nebo čísla objednávky je nerozeznáš — berou se jen se jménem dopravce poblíž
 *   ([CARRIER_WINDOW] znaků), napsaným velkými písmeny („ppl" je v angličtině „people").
 */
fun findMessageContext(
    text: String,
    entities: List<MessageEntity>,
    sentAt: LocalDateTime,
    zone: TimeZone,
    /** Měna uživatele (`homeCurrency`); částky v ní se nepřevádějí. `null` = převádí se všechno. */
    homeCurrency: String? = null,
    /** Používá uživatel metrické jednotky? Převádějí se jen jednotky té druhé soustavy. */
    metric: Boolean = true,
): MessageContext {
    if (text.isBlank()) return MessageContext()
    val payments = payments(text)
    val bookings = bookings(text)
    val event = eventCandidate(text, entities, sentAt, zone)
    return MessageContext(
        event = event,
        payments = payments,
        addresses = addresses(text),
        contacts = contacts(text, entities, payments),
        // Termín ve zprávě s letem je čas letu. Stačí i nejistý („let OK 123 v 9:40"): že jde
        // o let, víme z čísla letu, model to potvrzovat nemusí.
        flights = flights(text).map { flight ->
            val timed = event?.let { flight.copy(at = it.start, allDay = it.allDay) } ?: flight
            // Dva časy u odletu: druhý je přistání, ve stejný den (přes půlnoc dopočítá [schedule]).
            val landing = CLOCK_TIME.findAll(text).drop(1).firstOrNull()
                ?.takeIf { !timed.arrival && !timed.allDay && timed.at != null }
                ?.let { match -> runCatching { LocalTime(match.groupValues[1].toInt(), match.groupValues[2].toInt()) }.getOrNull() }
            timed.copy(landsAt = landing?.let { timed.at!!.date.atTime(it) })
        },
        wifi = wifi(text),
        bookings = bookings,
        codes = codes(text, payments).filterNot { code -> bookings.any { it.value == code.value } },
        money = money(text, homeCurrency),
        measures = measures(text, metric),
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
    // Let, jehož čas už minul, do kalendáře nepatří; stav letu a číslo ke zkopírování zůstanou.
    val timedFlights = flights.map { flight ->
        val at = flight.at ?: return@map flight
        val passed = if (flight.allDay) at.date < now.date else at <= now
        if (passed) flight.copy(at = null) else flight
    }
    // Termín, který nese let, se nenabízí ještě jednou jako obecná událost („přistání na letišti").
    val flightCarriesTime = timedFlights.any { it.at != null }
    event?.takeIf { !flightCarriesTime && if (it.allDay) it.start.date >= now.date else it.start > now }?.let { candidate ->
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
    addAll(timedFlights)
    wifi?.let(::add)
    addAll(bookings)
    addAll(codes)
    addAll(money)
    addAll(measures)
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
/**
 * Místo události pro mapy, nebo `null`, když s ním mapy neporadí. Model ho bere ze zprávy, jak
 * stálo, a bývá vágní („letišti", „u kina", samotné „Kotvy") — mapy pak ukážou něco náhodného
 * v okolí. Nabízí se jen místo, které vypadá jako adresa nebo vlastní jméno: s číslem („Vinohradská
 * 12", PSČ) nebo víc slov s vlastním jménem dál než na začátku („na Černém Mostě u kina",
 * „Café Louvre", „U Fleků"). Samotné místo se v kartě ukazuje dál — jen tlačítko map ne.
 */
fun ContextFinding.Event.mapsQuery(): String? {
    val text = place?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    if (text.any { it.isDigit() }) return text
    val words = text.split(' ', ',').filter { it.isNotBlank() }
    // Velké písmeno až od druhého slova: první může být velké jen proto, že model začal větou
    // („Na letišti"), kdežto „U Fleků" nebo „Náměstí Míru" mají vlastní jméno dál.
    return text.takeIf { words.drop(1).any { it.first().isUpperCase() } }
}

/**
 * Stav letu na FlightAware. Bere kód IATA („BA117") a sám ho převede na ICAO; ověřeno curlem
 * (Flightradar24 robotům vrací 403, takže se ověřit nedal).
 */
fun ContextFinding.Flight.trackingUrl(): String = "https://www.flightaware.com/live/flight/$code"

/**
 * Síť jako text QR kódu (`WIFI:T:WPA;S:…;P:…;;`), jak ho čte fotoaparát Androidu i iPhonu.
 * Zpětné lomítko, středník, čárka, dvojtečka a uvozovky se escapují lomítkem.
 */
fun ContextFinding.Wifi.qrText(): String = "WIFI:T:WPA;S:${wifiEscape(ssid)};P:${wifiEscape(password)};;"

private fun wifiEscape(value: String): String = buildString {
    value.forEach { char ->
        if (char in WIFI_SPECIAL) append('\\')
        append(char)
    }
}

private const val WIFI_SPECIAL = "\\;,:\""

fun ContextFinding.Parcel.trackingUrl(language: String): String = when (carrier) {
    ParcelCarrier.CzechPost -> "https://www.postaonline.cz/trackandtrace/-/zasilka/cislo?parcelNumbers=$number"
    ParcelCarrier.Packeta -> "https://tracking.packeta.com/${if (language == "cs") "cs" else "en"}/$number"
    ParcelCarrier.International -> "https://t.17track.net/en#nums=$number"
    // Adresy z vlastních stránek dopravců: PPL `tntSearchUrlCs/En` na ppl.cz s parametrem
    // `shipmentId`, DPD z vyhledávacího pole na dpd.com/cz (`lang=cz`, ne `cs`).
    ParcelCarrier.Ppl ->
        if (language == "cs") "https://www.ppl.cz/vyhledat-zasilku?shipmentId=$number"
        else "https://www.ppl.cz/en/track-a-shipment?shipmentId=$number"
    ParcelCarrier.Dpd ->
        "https://www.dpdgroup.com/cz/mydpd/my-parcels/search?lang=${if (language == "cs") "cz" else "en"}&parcelNumber=$number"
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

// --- Let -------------------------------------------------------------------------------------

private fun flights(text: String): List<ContextFinding.Flight> {
    val words = WORD.findAll(foldDiacritics(text).lowercase()).map { it.value }.toList()
    val aboutFlight = words.any { word -> word in FLIGHT_WORDS || FLIGHT_STEMS.any { word.startsWith(it) } }
    if (!aboutFlight) return emptyList()
    // Přílet jen tehdy, když zpráva o odletu nemluví vůbec („odlet v 9:40, přílet ve 12:00" je odlet).
    val arrival = words.any { word -> ARRIVAL_STEMS.any { word.startsWith(it) } } &&
        words.none { word -> DEPARTURE_STEMS.any { word.startsWith(it) } }
    val (from, to) = route(text)
    return FLIGHT.findAll(text)
        .filter { it.groupValues[1] in AIRLINES }
        .map { ContextFinding.Flight(it.groupValues[1], it.groupValues[2], arrival = arrival, from = from, to = to) }
        .distinctBy { it.code }
        .take(MAX_FLIGHTS)
        .toList()
}

/**
 * Trasa letu ze zprávy: dvojice kódů („PRG → LHR", „PRG-LHR", „PRG to LHR", „z PRG do LHR"), nebo
 * jen jeden s předložkou („from JFK", „do LHR"), případně město („z Prahy do Londýna", „z Ruzyně",
 * [citiesOf]). Kódem jen letiště
 * z tabulky [AIRPORTS] — tři velká písmena bývají i zkratky („MAN", „SIN").
 */
private fun route(text: String): Pair<String?, String?> {
    ROUTE_PAIR.find(text)?.let { match ->
        val (from, to) = match.destructured
        if (from in AIRPORTS && to in AIRPORTS && from != to) return from to to
    }
    val (cityFrom, cityTo) = citiesOf(foldDiacritics(text).lowercase())
    val from = ROUTE_FROM.find(text)?.groupValues?.get(1)?.takeIf { it in AIRPORTS } ?: cityFrom
    val to = (ROUTE_TO.find(text)?.groupValues?.get(1)?.takeIf { it in AIRPORTS } ?: cityTo)?.takeIf { it != from }
    return from to to
}

/**
 * Letiště podle měst za předložkou („z Prahy do Londýna", „from Prague to London"). Odlet za
 * „z / ze / from", přílet za „do / na / to"; první nález vyhrává. Za předložkou se zkouší jedno
 * i dvě slova („do New Yorku", „from Los Angeles").
 */
private fun citiesOf(folded: String): Pair<String?, String?> {
    var from: String? = null
    var to: String? = null
    // Po slovech, ne regulárem: ten by k předložce spolkl i další slovo a „z Prahy do Londýna"
    // by „do" snědlo „Prahy" (překrývající se nálezy regulár nevrací).
    val words = WORD.findAll(folded).map { it.value }.toList()
    words.forEachIndexed { index, preposition ->
        if (preposition !in CITY_PREPOSITIONS) return@forEachIndexed
        val first = words.getOrNull(index + 1) ?: return@forEachIndexed
        val phrase = listOfNotNull(first, words.getOrNull(index + 2)).joinToString(" ")
        val code = if (preposition == "from" || preposition == "to") {
            ENGLISH_CITIES[phrase] ?: ENGLISH_CITIES[first]
        } else {
            CZECH_CITY_WORDS[first]
                ?: CZECH_CITY_STEMS.entries.firstOrNull { (stem, _) -> phrase.startsWith(stem) }?.value
        } ?: return@forEachIndexed
        when (preposition) {
            "z", "ze", "from" -> if (from == null) from = code
            else -> if (to == null) to = code
        }
    }
    return from to to
}

/**
 * Kdy let poletí a přistane, jako okamžiky: odlet je místní čas letiště odletu, přistání místní
 * čas letiště příletu (Praha 9:40 → Londýn 10:55 je 2 h 15 min, ne 1 h 15 min). Bez letiště se
 * bere pásmo uživatele [zone]. Délka je PŘESNÁ s oběma časy, ODHAD jen s trasou
 * ([estimatedFlightMinutes]), jinak žádná. `null` bez času.
 */
fun ContextFinding.Flight.schedule(zone: TimeZone): FlightSchedule? {
    val at = at?.takeIf { !allDay } ?: return null
    val fromAirport = from?.let(AIRPORTS::get)
    val toAirport = to?.let(AIRPORTS::get)
    val startZone = (if (arrival) toAirport else fromAirport)?.let { TimeZone.of(it.zone) } ?: zone
    val start = at.toInstant(startZone)
    if (arrival) return FlightSchedule(start, end = null, minutes = null, estimated = false)
    landsAt?.let { landing ->
        var end = landing.toInstant(toAirport?.let { TimeZone.of(it.zone) } ?: zone)
        // Přistání „dřív" než odlet je přistání druhý den (noční let).
        while (end <= start) end += 1.days
        return FlightSchedule(start, end, ((end - start).inWholeMinutes).toInt(), estimated = false)
    }
    if (fromAirport != null && toAirport != null) {
        val minutes = estimatedFlightMinutes(fromAirport, toAirport)
        return FlightSchedule(start, start + minutes.minutes, minutes, estimated = true)
    }
    return FlightSchedule(start, end = null, minutes = null, estimated = false)
}

/** Rozvrh letu pro kalendář a sheet; [end] a [minutes] `null`, když délku neznáme. */
data class FlightSchedule(val start: Instant, val end: Instant?, val minutes: Int?, val estimated: Boolean)

// --- Wi-Fi -----------------------------------------------------------------------------------

private fun wifi(text: String): ContextFinding.Wifi? {
    WIFI_QR.find(text)?.let { match ->
        val fields = WIFI_FIELD.findAll(match.value.removePrefix("WIFI:")).associate { field ->
            field.groupValues[1].uppercase() to field.groupValues[2].replace(WIFI_UNESCAPE, "$1")
        }
        val ssid = fields["S"]
        val password = fields["P"]
        if (!ssid.isNullOrEmpty() && password != null && password.length in WPA_PASSWORD) {
            return ContextFinding.Wifi(ssid, password)
        }
    }
    val ssid = WIFI_NAME.findAll(text).map { quotedOrBare(it).trimEnd('.', ')', ':') }
        .firstOrNull { it.isNotEmpty() && foldDiacritics(it).lowercase() !in WIFI_NOT_A_NAME } ?: return null
    val password = WIFI_PASSWORD.findAll(text).map { quotedOrBare(it).trimEnd('.', ')') }
        .firstOrNull { it.length in WPA_PASSWORD && it != ssid } ?: return null
    return ContextFinding.Wifi(ssid, password)
}

/** Hodnota v uvozovkách („…", "…"), nebo bez nich do mezery, čárky či středníku. */
private fun quotedOrBare(match: MatchResult): String =
    match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }.orEmpty()

// --- Kód rezervace ---------------------------------------------------------------------------

private fun bookings(text: String): List<ContextFinding.Booking> {
    // Kód je ASCII, takže je v textu bez diakritiky stejný jako v původním (jako u [codes]).
    if ("telegram" in foldDiacritics(text).lowercase()) return emptyList()
    return BOOKING.findAll(foldDiacritics(text))
        .map { it.groupValues[1].trimEnd('.', '-') }
        .filter { value ->
            val alnum = value.filter { it.isLetterOrDigit() }
            if (alnum.all { it.isDigit() }) alnum.length in 6..12 else alnum.length in 5..16
        }
        .distinct()
        .take(MAX_BOOKINGS)
        .map { ContextFinding.Booking(it) }
        .toList()
}

// --- Kód ------------------------------------------------------------------------------------

private fun codes(text: String, payments: List<ContextFinding.Payment>): List<ContextFinding.Code> {
    val folded = foldDiacritics(text)
    if ("telegram" in folded.lowercase()) return emptyList()
    val symbols = payments.mapNotNull { it.variableSymbol }.toSet()
    return CODE.findAll(folded)
        // Kód je jen z ASCII, takže je v textu bez diakritiky stejný jako v původním.
        .map { it.groupValues[1] }
        .filter { value ->
            val digitsOnly = value.all { it.isDigit() }
            (if (digitsOnly) value.length in 3..8 else value.length in 4..16 && value.any { it.isLetter() }) &&
                value !in symbols
        }
        .distinct()
        .take(MAX_CODES)
        .map { ContextFinding.Code(it) }
        .toList()
}

// --- Cizí měna -------------------------------------------------------------------------------

private fun money(text: String, homeCurrency: String?): List<ContextFinding.Money> {
    val yen = yenSense(foldDiacritics(text).lowercase())
    val prefixed = MONEY_BEFORE.findAll(text).flatMap { match ->
        val amount = parseAmount(match.groupValues[2]) ?: return@flatMap emptySequence()
        moneyOf(match.value.trim(), amount, match.groupValues[1].trim(), yen)
    }
    val suffixed = MONEY_AFTER.findAll(text).flatMap { match ->
        val amount = parseAmount(match.groupValues[1]) ?: return@flatMap emptySequence()
        moneyOf(match.value.trim(), amount, match.groupValues[2], yen)
    }
    return (prefixed + suffixed + moneyInWords(text))
        .filter { it.currency != homeCurrency && it.amount > 0 }
        .distinctBy { it.currency to it.amount }
        .take(MAX_MONEY)
        .toList()
}

/**
 * Částka podle značky nebo kódu. `¥` rozhodne okolí zprávy ([yenSense]); když nerozhodne, jsou
 * to DVĚ možnosti — jen i jüan, obě s [ContextFinding.Money.assumed].
 */
private fun moneyOf(shown: String, amount: Double, unit: String, yen: Set<String>): Sequence<ContextFinding.Money> {
    if (unit == "\u00A5") {
        return yen.asSequence().map { ContextFinding.Money(shown, amount, it, assumed = yen.size > 1) }
    }
    val currency = CURRENCY_SYMBOLS[unit] ?: CURRENCY_WORDS[unit.lowercase()] ?: unit.takeIf { it in CURRENCY_CODES }
        ?: return emptySequence()
    return sequenceOf(ContextFinding.Money(shown, amount, currency))
}

/**
 * Jeny, nebo jüany? Podle zmínky o Japonsku, nebo o Číně, v textu bez diakritiky malými.
 * Bez zmínky (nebo s oběma) obojí.
 */
private fun yenSense(folded: String): Set<String> {
    val words = WORD.findAll(folded).map { it.value }.toList()
    val japan = words.any { word -> JAPAN_STEMS.any { word.startsWith(it) } }
    val china = words.any { word -> CHINA_STEMS.any { word.startsWith(it) } }
    return when {
        japan && !china -> setOf("JPY")
        china && !japan -> setOf("CNY")
        else -> linkedSetOf("JPY", "CNY")
    }
}

/**
 * „Pounds" jsou libry, nebo váha? Podle slov o penězích a o váze, v textu bez diakritiky malými.
 * Bez nich (nebo s oběma) obojí — sheet nabídne převod váhy i měny.
 */
internal enum class PoundSense { Money, Weight, Both }

internal fun poundSense(folded: String): PoundSense {
    val words = WORD.findAll(folded).map { it.value }.toList()
    val money = "\u00A3" in folded || words.any { word -> MONEY_CONTEXT_STEMS.any { word.startsWith(it) } }
    val weight = words.any { word -> WEIGHT_CONTEXT_STEMS.any { word.startsWith(it) } }
    return when {
        money && !weight -> PoundSense.Money
        weight && !money -> PoundSense.Weight
        else -> PoundSense.Both
    }
}

/**
 * Částka s měnou SLOVY: číslo a za ním nejvýš tři slova — řád („1,5 milionu dolarů"), přívlastek
 * („švédských korun", „kanadských dolarů") a měna. Hledá se v textu bez diakritiky malými, ven
 * jde úsek původního textu (délka se nemění).
 *
 * Vynechané je, co je dvojznačné: samotné „jen" (= pouze) a anglické „pounds" (spíš váha, převádí
 * se jako jednotka). Řád se musí počítat — bez něj by „1,5 milionu dolarů" bylo 1,50 USD.
 */
private fun moneyInWords(text: String): List<ContextFinding.Money> {
    val folded = foldDiacritics(text).lowercase()
    val pounds = poundSense(folded)
    return MONEY_WORDS.findAll(folded).mapNotNull { match ->
        val number = parseAmount(match.groupValues[1]) ?: return@mapNotNull null
        val words = WORD_WITH_DOT.findAll(match.groupValues[2]).toList()
        var index = 0
        val scale = words.getOrNull(index)?.let { scaleOf(it.value) }?.also { index++ } ?: 1.0
        var adjective: String? = null
        var currency = words.getOrNull(index)?.let { currencyWordOf(it.value) }
        if (currency == null) {
            adjective = words.getOrNull(index)?.value
            index++
            currency = words.getOrNull(index)?.let { currencyWordOf(it.value) } ?: return@mapNotNull null
        }
        val code = adjective?.let { adjectiveCurrency(currency, it) } ?: currency
        // Anglické „pounds" jsou peníze jen tam, kde zpráva o váze nemluví (viz [poundSense]).
        val englishPound = words[index].value.startsWith("pound")
        if (englishPound && pounds == PoundSense.Weight) return@mapNotNull null
        // Konec úseku = konec slova s měnou; slova jsou na konci nálezu, hned za číslem.
        val wordsStart = match.range.last + 1 - match.groupValues[2].length
        val end = wordsStart + words[index].range.last + 1
        ContextFinding.Money(
            text.substring(match.range.first, end).trim(),
            number * scale,
            code,
            assumed = englishPound && pounds == PoundSense.Both,
        )
    }.toList()
}

private fun scaleOf(word: String): Double? = when {
    word == "tis" || word == "tis." || word.startsWith("tisic") || word == "thousand" -> 1e3
    word == "mil" || word == "mil." || word.startsWith("milion") || word == "million" || word == "millions" -> 1e6
    word.startsWith("miliard") || word == "billion" || word == "billions" -> 1e9
    else -> null
}

/** Měna podle slova (bez diakritiky, malými). Česky podle začátku slova kvůli skloňování. */
private fun currencyWordOf(word: String): String? = CURRENCY_WORD_EXACT[word]
    ?: CURRENCY_WORD_STEMS.entries.firstOrNull { (stem, _) -> word.startsWith(stem) }?.value

/** Přívlastek, který z obecné měny dělá jinou: „kanadských dolarů" = CAD, „švédských korun" = SEK. */
private fun adjectiveCurrency(currency: String, adjective: String): String? = when (currency) {
    "USD" -> when {
        adjective.startsWith("kanad") || adjective.startsWith("canad") -> "CAD"
        adjective.startsWith("austral") -> "AUD"
        adjective.startsWith("novozeland") -> "NZD"
        adjective.startsWith("singapur") || adjective.startsWith("singapor") -> "SGD"
        adjective.startsWith("hongkong") -> "HKD"
        else -> null
    }
    "CZK" -> when {
        adjective.startsWith("svedsk") || adjective.startsWith("swedish") -> "SEK"
        adjective.startsWith("norsk") || adjective.startsWith("norwegian") -> "NOK"
        adjective.startsWith("dansk") || adjective.startsWith("danish") -> "DKK"
        adjective.startsWith("islandsk") || adjective.startsWith("icelandic") -> "ISK"
        else -> null
    }
    else -> null
}

/**
 * Číslo, jak ho píšou různé jazyky: „1 250,50", „1.250,50", „1,250.50", „12,5". Poslední
 * oddělovač s jednou nebo dvěma číslicemi za sebou je desetinný, ostatní oddělují tisíce
 * („1,250" je tisíc dvě stě padesát, ne jedna celá dvě).
 */
internal fun parseAmount(raw: String): Double? {
    val compact = raw.filterNot { it == ' ' || it == '\u00A0' || it == '\u202F' }
    val separator = maxOf(compact.lastIndexOf('.'), compact.lastIndexOf(','))
    val decimals = if (separator >= 0) compact.length - separator - 1 else 0
    val (whole, fraction) = if (separator >= 0 && decimals in 1..2) {
        compact.substring(0, separator).filter(Char::isDigit) to compact.substring(separator + 1)
    } else {
        compact.filter(Char::isDigit) to ""
    }
    if (whole.isEmpty()) return null
    return "$whole.${fraction.ifEmpty { "0" }}".toDoubleOrNull()
}

// --- Jednotky --------------------------------------------------------------------------------

/**
 * Jednotka, kterou umíme převést, a kam: imperiální na metrickou a naopak. [symbol] je, jak se
 * píše za číslo (mezinárodní značky se nepřekládají), [target] cílová jednotka.
 */
enum class MeasureUnit(val symbol: String, val metric: Boolean, private val toTarget: (Double) -> Double, private val targetName: String) {
    Mile("mi", false, { it * 1.609344 }, "Kilometer"),
    MilesPerHour("mph", false, { it * 1.609344 }, "KilometersPerHour"),
    Foot("ft", false, { it * 0.3048 }, "Meter"),
    Inch("in", false, { it * 2.54 }, "Centimeter"),
    Yard("yd", false, { it * 0.9144 }, "Meter"),
    Pound("lb", false, { it * 0.45359237 }, "Kilogram"),
    Ounce("oz", false, { it * 28.349523125 }, "Gram"),
    Gallon("gal", false, { it * 3.785411784 }, "Liter"),
    FluidOunce("fl oz", false, { it * 29.5735295625 }, "Milliliter"),
    Fahrenheit("\u00B0F", false, { (it - 32) * 5 / 9 }, "Celsius"),
    Kilometer("km", true, { it / 1.609344 }, "Mile"),
    KilometersPerHour("km/h", true, { it / 1.609344 }, "MilesPerHour"),
    Meter("m", true, { it / 0.3048 }, "Foot"),
    Centimeter("cm", true, { it / 2.54 }, "Inch"),
    Kilogram("kg", true, { it / 0.45359237 }, "Pound"),
    Gram("g", true, { it / 28.349523125 }, "Ounce"),
    Liter("l", true, { it / 3.785411784 }, "Gallon"),
    Milliliter("ml", true, { it / 29.5735295625 }, "FluidOunce"),
    Celsius("\u00B0C", true, { it * 9 / 5 + 32 }, "Fahrenheit"),
    ;

    /** Kam se převádí. Jménem, protože enum se v konstruktoru na pozdější hodnoty odkázat nemůže. */
    val target: MeasureUnit get() = valueOf(targetName)

    fun convert(value: Double): Double = toTarget(value)
}

private fun measures(text: String, metric: Boolean): List<ContextFinding.Measure> =
    MEASURE.findAll(text).mapNotNull { match ->
        val unit = unitOf(match.groupValues[3]) ?: return@mapNotNull null
        // Převádí se jen to, co uživatel nepoužívá: Čech nepotřebuje přepočítat kilometry.
        if (unit.metric == metric) return@mapNotNull null
        // „Pounds" ve zprávě o penězích nejsou váha (viz [poundSense]); zkratka „lb" váha je vždy.
        if (unit == MeasureUnit.Pound && match.groupValues[3].lowercase().startsWith("pound") &&
            poundSense(foldDiacritics(text).lowercase()) == PoundSense.Money
        ) {
            return@mapNotNull null
        }
        val number = parseAmount(match.groupValues[2]) ?: return@mapNotNull null
        val value = if (match.groupValues[1].isNotEmpty()) -number else number
        // Záporná čísla dávají smysl jen u teploty („-5 °F").
        if (value < 0 && unit != MeasureUnit.Fahrenheit && unit != MeasureUnit.Celsius) return@mapNotNull null
        ContextFinding.Measure(match.value.trim(), value, unit)
    }.distinctBy { it.unit to it.value }.take(MAX_MEASURES).toList()

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
    // PPL a DPD jsou holé číslice bez kontrolní číslice — jen se jménem dopravce poblíž.
    CARRIER_NUMBER.findAll(text).forEach { match ->
        val (carrier, name) = when (match.value.length) {
            PPL_DIGITS -> ParcelCarrier.Ppl to PPL
            DPD_DIGITS -> ParcelCarrier.Dpd to DPD
            else -> return@forEach
        }
        val near = name.findAll(text).any { word ->
            word.range.first - match.range.last <= CARRIER_WINDOW && match.range.first - word.range.last <= CARRIER_WINDOW
        }
        if (near) add(ContextFinding.Parcel(carrier, match.value))
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
private const val PPL_DIGITS = 11
private const val DPD_DIGITS = 14

/** Jak daleko (ve znacích) od čísla smí stát jméno dopravce, aby se číslo bralo jako zásilka. */
private const val CARRIER_WINDOW = 80
private const val MAX_ADDRESSES = 2
private const val MAX_CONTACTS = 2
private const val MAX_FLIGHTS = 2
private const val MAX_CODES = 2
private const val MAX_BOOKINGS = 2
private const val MAX_MONEY = 2
private const val MAX_MEASURES = 3
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

private val WORD = Regex("[\\p{L}\\p{N}]+")

/** Kód dopravce (dvě písmena, nebo písmeno a číslice) a číslo letu, s mezerou i bez. */
private val FLIGHT = Regex("(?<![\\p{L}\\d])([A-Z]{2}|[A-Z]\\d|\\d[A-Z])\\s?(\\d{1,4})(?![\\p{L}\\d])")

/** Slova o letu, celá (bez diakritiky, malými). */
private val FLIGHT_WORDS = setOf(
    "let", "letu", "letem", "lety", "gate", "gejt", "flug",
    // Tvary slovesa „letět" („Letím zítra OK 123").
    "leti", "letim", "letis", "letime", "letite", "letet", "poletim", "poletime", "letel", "letela",
)

/** Čas s dvojtečkou („9:40", „10:55") — pro druhý čas ve zprávě s letem (přistání). */
private val CLOCK_TIME = Regex("(?<![\\d.:])([01]?\\d|2[0-3]):([0-5]\\d)(?![\\d.:])")

/** Dvojice kódů letišť s oddělovačem: pomlčka, šipka, „to", „do". */
private val ROUTE_PAIR = Regex("(?<![A-Za-z])([A-Z]{3})\\s?(?:-|\u2013|\u2014|\u2192|->|>|\\s(?:to|do)\\s)\\s?([A-Z]{3})(?![A-Za-z])")
/** Předložky, za kterými se hledá město: odlet „z / ze / from", přílet „do / na / to". */
private val CITY_PREPOSITIONS = setOf("z", "ze", "do", "na", "from", "to")

private val ROUTE_FROM = Regex("(?<![\\p{L}])(?:z|ze|from|[Zz] letiste)\\s+([A-Z]{3})(?![A-Za-z])")
private val ROUTE_TO = Regex("(?<![\\p{L}])(?:do|to|na)\\s+([A-Z]{3})(?![A-Za-z])")

/** Začátky slov o příletu a odletu (bez diakritiky, malými); rozhodují, co je čas ve zprávě. */
private val ARRIVAL_STEMS = listOf("prilet", "pristan", "arriv", "landing", "lands")
private val DEPARTURE_STEMS = listOf("odlet", "odlit", "vzlet", "depart", "takeoff", "boarding")

/** Začátky slov o letu („letiště", „odletu", „přílet", „boarding"). */
private val FLIGHT_STEMS = listOf("letist", "letadl", "odlet", "prilet", "flight", "airport", "boarding", "terminal", "check-in", "checkin")

/**
 * Kódy IATA běžných leteckých společností (Evropa a největší světové). Chybějící kód jen znamená,
 * že se let nerozpozná. `VS` (Virgin Atlantic) tu schválně není: v češtině je to variabilní symbol.
 */
private val AIRLINES = setOf(
    "OK", "QS", "FR", "W6", "W4", "U2", "LH", "LX", "OS", "LO", "AF", "KL", "BA", "IB", "VY", "SK",
    "AY", "TK", "PC", "EK", "QR", "EY", "LY", "A3", "TP", "SN", "EI", "DY", "D8", "EW", "HV", "TO",
    "X3", "BY", "LS", "AZ", "JU", "RO", "FB", "OU", "BT", "LG", "UA", "AA", "DL", "AC", "B6", "NH",
    "JL", "CX", "SQ", "QF", "ET", "MS", "SV", "AI", "CA", "MU", "CZ", "KE", "OZ", "TG", "VN", "NZ",
    "LA", "AV", "AM", "WN", "G9", "FZ", "XQ", "KM", "V7", "EN", "DE", "PS", "4U", "WK", "BJ", "J2",
)

private val WPA_PASSWORD = 8..63

/** Zápis sítě z QR kódu; pole jsou `T:`, `S:`, `P:`, `H:` oddělená středníkem. */
private val WIFI_QR = Regex("WIFI:(?:[A-Za-z]:(?:\\\\.|[^;\\\\])*;)+;?")
private val WIFI_FIELD = Regex("([A-Za-z]):((?:\\\\.|[^;\\\\])*);")
private val WIFI_UNESCAPE = Regex("\\\\(.)")

/** Hodnota: v uvozovkách (i českých), nebo bez nich do mezery, čárky či středníku. */
private const val WIFI_VALUE = "(?:\"([^\"\\n]{1,63})\"|\u201e([^\u201c\"\\n]{1,63})[\u201c\"]|([^\\s,;\"]{1,63}))"

/**
 * Pár slov mezi klíčovým slovem a dvojtečkou („Wi-Fi na chatě: …", „heslo do sítě doma: …").
 * V alternativě PRVNÍ: kratší varianta by jinak uspěla s hodnotou „na" a regulár by se k této
 * už nevrátil (filtr hodnoty běží až po nálezu).
 */
private const val WIFI_WORDS_COLON = "(?:\\s+[\\p{L}\\-]+){1,3}\\s*:"

/**
 * Název sítě: „Wi-Fi", „WLAN", „SSID", „síť" nebo „network", případně „se jmenuje / je / is",
 * a hodnota. Diakritika je ve třídách znaků escapovaná (`verifyNoHardcodedText`).
 */
private val WIFI_NAME = Regex(
    "(?<![\\p{L}])(?:wi-?fi|wlan|ssid|s[i\u00ed][t\u0165]|network)(?![\\p{L}])" +
        "(?:$WIFI_WORDS_COLON|(?:\\s+(?:se\\s+jmenuje|je|is|name|n[a\u00e1]zev))?\\s*[:=\\-\u2013]?)\\s*$WIFI_VALUE",
    RegexOption.IGNORE_CASE,
)

/** Heslo: „heslo", „password", „pass", „pw", „klíč", případně „k wifi / je / is", a hodnota. */
private val WIFI_PASSWORD = Regex(
    "(?<![\\p{L}])(?:heslo|hesla|password|passwd|pass|pw|kl[i\u00ed][c\u010d])(?![\\p{L}])" +
        "(?:\\s+(?:k|ke|do|na|pro|to|for)\\s+(?:wi-?fi|wlan|s[i\u00ed]ti|s[i\u00ed][t\u0165]))?" +
        "(?:$WIFI_WORDS_COLON|(?:\\s+(?:je|is))?\\s*[:=\\-\u2013]?)\\s*$WIFI_VALUE",
    RegexOption.IGNORE_CASE,
)

/** Slova, která za „wifi" stojí místo názvu („wifi heslo je…", „síť je dole"). */
private val WIFI_NOT_A_NAME = setOf("heslo", "hesla", "password", "pass", "pw", "klic", "je", "is", "k", "na", "nefunguje", "nejde")

/**
 * Kód rezervace: klíčové slovo („rezervace" ve všech tvarech, „booking", „reservation",
 * „confirmation", „PNR", „locator"), nejvýš tři slova („číslo vaší rezervace je") a kód z VELKÝCH
 * písmen a číslic. Velikost písmen ručně ve třídě znaků, ze stejného důvodu jako u [CODE].
 */
private val BOOKING = Regex(
    "(?<![\\p{L}])(?:[Rr]ezervac\\p{L}*|REZERVAC\\p{L}*|[Bb]ooking|BOOKING|[Rr]eservation|RESERVATION|" +
        "[Cc]onfirmation|CONFIRMATION|PNR|[Ll]ocator)(?![\\p{L}])" +
        "(?:\\s+[\\p{L}.]+){0,3}?\\s*[:#=\\-\u2013]?\\s*[\"\u201e]?([A-Z0-9][A-Z0-9.\\-]{4,15})(?![\\p{L}\\d])",
)

/**
 * Klíčové slovo, nejvýš tři slova mezi ním a kódem („slevový kód na první nákup: LETO20") a kód
 * z VELKÝCH písmen a číslic. Velikost písmen se hlídá ve třídě znaků, ne příznakem: s
 * `IGNORE_CASE` by kódem bylo i „na" a regulár by se k delší variantě už nevrátil (jako u Wi-Fi).
 * Hledá se v textu bez diakritiky, takže „kód" je „kod".
 */
private val CODE = Regex(
    "(?<![\\p{L}])(?:[Kk]od\\p{L}*|KOD\\p{L}*|[Cc]ode|CODE|[Vv]oucher\\p{L}*|[Pp]romo\\p{L}*|[Kk]upon\\p{L}*|[Cc]oupon|PIN|OTP)" +
        "(?![\\p{L}])(?:\\s+[\\p{L}]+){0,3}?\\s*[:=\\-\u2013]?\\s*[\"\u201e]?([A-Z0-9][A-Z0-9\\-]{2,15})(?![\\p{L}\\d\\-])",
)

/** Číslo s tisíci (mezerou, tečkou, čárkou) a nejvýš dvěma desetinnými místy. */
private const val MONEY_NUMBER = "(\\d{1,3}(?:[ \\u00A0\\u202F,.]\\d{3})+(?:[.,]\\d{1,2})?|\\d+(?:[.,]\\d{1,2})?)"

/** Značka před číslem: „€120", „$ 45", „£9.99". */
private val MONEY_BEFORE = Regex("(?<![\\p{L}\\d])(€|\\$|£|\u00A5|CN\u00A5|JP\u00A5|RMB\\s?)\\s?$MONEY_NUMBER(?![\\d])")

/**
 * Značka nebo kód za číslem: „120 €", „300 PLN", „2 500 Ft", „15 eur". Bez `IGNORE_CASE`:
 * „5 ft" jsou stopy, ne forinty, a kódy ISO se píšou velkými.
 */
private val MONEY_AFTER = Regex(
    "(?<![\\p{L}\\d.,])$MONEY_NUMBER\\s?(?:,-\\s?)?" +
        "(€|\\$|£|\u00A5|\u5143|\u5186|K\u010d|z\u0142|Ft|[Ee]ur[oa]?|EUR|[A-Z]{3})(?![\\p{L}])",
)

private val CURRENCY_SYMBOLS = mapOf(
    "€" to "EUR", "$" to "USD", "£" to "GBP", "K\u010d" to "CZK", "z\u0142" to "PLN", "Ft" to "HUF",
    // Jen jednoznačné značky: 元 je jüan, 円 jen, CN¥ / JP¥ / RMB podle předpony. Holé ¥ viz [yenSense].
    "\u5143" to "CNY", "\u5186" to "JPY", "CN\u00A5" to "CNY", "JP\u00A5" to "JPY", "RMB" to "CNY",
)

/** Zmínky o Japonsku a Číně pro `¥` (bez diakritiky, malými, začátek slova). */
private val JAPAN_STEMS = listOf("japon", "japan", "toki", "tokyo", "osak", "kjot", "kyoto", "jenu", "jeny", "yen")
private val CHINA_STEMS = listOf("cina", "ciny", "cine", "cinu", "cinsk", "china", "chines", "peking", "beijing", "sanghaj", "shanghai", "juan", "yuan", "rmb", "renminbi")

/** Slova o penězích a o váze, která rozhodnou, co jsou „pounds" (bez diakritiky, malými, začátek slova). */
private val MONEY_CONTEXT_STEMS = listOf(
    "cost", "price", "pay", "paid", "spent", "spend", "earn", "salary", "rent", "worth", "buy", "bought",
    "sell", "sold", "ticket", "fee", "budget", "cash", "owe", "charge", "bill", "sterling", "quid",
    "stoj", "cen", "zaplat", "plat", "kup", "prodal", "najem", "vydel", "dluz", "listek",
)
private val WEIGHT_CONTEXT_STEMS = listOf(
    "weigh", "weight", "heavy", "flour", "sugar", "butter", "meat", "lost", "gained", "gain", "lose",
    "vah", "vaz", "tezk", "hubn", "pribral", "zhubl", "mouk", "cukr", "maso",
)

private val CURRENCY_WORDS = mapOf("eur" to "EUR", "euro" to "EUR", "eura" to "EUR")

/** Číslo a za ním nejvýš tři slova (řád, přívlastek, měna) — v textu bez diakritiky malými. */
private val MONEY_WORDS = Regex("(?<![\\p{L}\\d.,])$MONEY_NUMBER((?:\\s+[\\p{L}.]+){1,3})")
private val WORD_WITH_DOT = Regex("[\\p{L}]+\\.?")

/** Měny slovy podle začátku slova (dolarů, liber, zlotých, forintů, franků…). */
private val CURRENCY_WORD_STEMS: Map<String, String> = linkedMapOf(
    "dolar" to "USD", "dollar" to "USD", "bucks" to "USD", "eur" to "EUR", "libr" to "GBP", "liber" to "GBP",
    "pound" to "GBP", "quid" to "GBP",
    "sterling" to "GBP", "frank" to "CHF", "franc" to "CHF", "zlot" to "PLN", "forint" to "HUF", "korun" to "CZK",
    "juan" to "CNY", "yuan" to "CNY", "rupi" to "INR", "rupee" to "INR", "baht" to "THB",
)

/** Krátká slova jen celými tvary: „jen" samo je „pouze", „lir" by chytilo i jiná slova. */
private val CURRENCY_WORD_EXACT: Map<String, String> = mapOf(
    "jenu" to "JPY", "jeny" to "JPY", "jenech" to "JPY", "yen" to "JPY",
    "lira" to "TRY", "lir" to "TRY", "liry" to "TRY", "lire" to "TRY", "lirach" to "TRY",
    "koruna" to "CZK", "koruny" to "CZK",
)

/**
 * Kódy, které se převádějí: měny z lístku ČNB. Kód mimo seznam („ABC") měnou není — tři velká
 * písmena za číslem bývají i zkratky („5 GB" ne, ale „10 SMS" ano).
 */
private val CURRENCY_CODES = setOf(
    "EUR", "USD", "GBP", "CHF", "PLN", "HUF", "CZK", "JPY", "CAD", "AUD", "SEK", "NOK", "DKK",
    "CNY", "TRY", "RON", "BGN", "ISK", "ILS", "INR", "KRW", "MXN", "NZD", "SGD", "ZAR", "THB",
    "BRL", "HKD", "IDR", "MYR", "PHP",
)

/**
 * Číslo (i záporné, kvůli teplotě) a jednotka. Delší jednotky v alternaci napřed, aby „mi"
 * nevyhrálo nad „miles" a „m" nad „mph". Bez `IGNORE_CASE`: „5 G" je síť, ne gramy. Anglické „in"
 * schválně chybí — „2 in 1" palce nejsou; palce jen celým slovem („inch", „palců").
 */
private val MEASURE = Regex(
    "(?<![\\p{L}\\d.,])(-|\u2212)?$MONEY_NUMBER\\s?" +
        "(km/h|kmh|mph|\u00B0\\s?F|\u00B0\\s?C|\u2109|\u2103|miles|mile|mi|feet|foot|ft|inches|inch|palc\\p{L}*|palec|" +
        "yards|yard|yd|pounds|pound|lbs|lb|ounces|ounce|fl\\.?\\s?oz|oz|gallons|gallon|gal|" +
        "km|cm|kg|ml|m|g|l|L|litr\\p{L}*|liters|litres|liter|litre)(?![\\p{L}\\d/])",
)

/**
 * Zápis jednotky ve zprávě → jednotka. Mezery a tečky se zahodí („fl. oz", „° F"), pak se hledá
 * přesně (velké „L" pro litr) a malými; české tvary podle začátku („palců", „litrů").
 */
private fun unitOf(raw: String): MeasureUnit? {
    val key = raw.replace(" ", "").replace(".", "")
    return UNIT_WORDS[key] ?: UNIT_WORDS[key.lowercase()] ?: when {
        key.lowercase().startsWith("palc") -> MeasureUnit.Inch
        key.lowercase().startsWith("litr") -> MeasureUnit.Liter
        else -> null
    }
}

private val UNIT_WORDS: Map<String, MeasureUnit> = buildMap {
    listOf("km/h", "kmh").forEach { put(it, MeasureUnit.KilometersPerHour) }
    put("mph", MeasureUnit.MilesPerHour)
    listOf("\u00B0F", "\u2109").forEach { put(it, MeasureUnit.Fahrenheit) }
    listOf("\u00B0C", "\u2103").forEach { put(it, MeasureUnit.Celsius) }
    listOf("miles", "mile", "mi").forEach { put(it, MeasureUnit.Mile) }
    listOf("feet", "foot", "ft").forEach { put(it, MeasureUnit.Foot) }
    listOf("inches", "inch", "palec").forEach { put(it, MeasureUnit.Inch) }
    listOf("yards", "yard", "yd").forEach { put(it, MeasureUnit.Yard) }
    listOf("pounds", "pound", "lbs", "lb").forEach { put(it, MeasureUnit.Pound) }
    listOf("ounces", "ounce", "oz").forEach { put(it, MeasureUnit.Ounce) }
    put("floz", MeasureUnit.FluidOunce)
    listOf("gallons", "gallon", "gal").forEach { put(it, MeasureUnit.Gallon) }
    put("km", MeasureUnit.Kilometer)
    put("m", MeasureUnit.Meter)
    put("cm", MeasureUnit.Centimeter)
    put("kg", MeasureUnit.Kilogram)
    put("g", MeasureUnit.Gram)
    put("ml", MeasureUnit.Milliliter)
    listOf("l", "L", "liters", "litres", "liter", "litre").forEach { put(it, MeasureUnit.Liter) }
}

private val S10 = Regex("(?<![A-Za-z0-9])([A-Z]{2})(\\d{9})([A-Z]{2})(?![A-Za-z0-9])")
/**
 * Jedenáct (PPL) nebo čtrnáct (DPD) číslic v kuse. Nesmí být kusem delšího čísla, účtu
 * („…/0800", „19-…"), kódu s písmeny ani desetinného čísla.
 */
private val CARRIER_NUMBER = Regex("(?<![\\p{L}\\d+/\\-])(?:\\d{14}|\\d{11})(?![\\p{L}\\d/]|[.,]\\d)")

/** Jméno dopravce jako samostatné slovo, velkými písmeny. */
private val PPL = Regex("(?<![\\p{L}\\d])PPL(?![\\p{L}\\d])")
private val DPD = Regex("(?<![\\p{L}\\d])DPD(?![\\p{L}\\d])")
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
