package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.DateTimeFormat
import eu.tintera.plume.core.telegram.MessageEntity
import eu.tintera.plume.core.telegram.MessageEntityType
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Kontext zprávy pravidly. Zpráva odeslaná ve středu 14. 10. 2026 v 10:00. */
class MessageContextTest {
    private val wednesday = LocalDateTime(2026, 10, 14, 10, 0)
    private val zone = TimeZone.UTC

    private fun context(text: String, entities: List<MessageEntity> = emptyList()) =
        findMessageContext(text, entities, wednesday, zone)

    private fun findings(text: String, verdict: EventVerdict? = null, now: LocalDateTime = wednesday) =
        context(text).contextFindings(text, verdict, now)

    @Test
    fun denACasJsouJistyTermin() {
        val event = context("Sraz zítra v 15:00 na Černém Mostě").event
        assertEquals(EventCandidate(LocalDateTime(2026, 10, 15, 15, 0), allDay = false, certain = true), event)
        assertEquals(LocalDateTime(2026, 10, 16, 19, 0), context("v pátek večer?").event?.start)
    }

    @Test
    fun datumBezCasuJeCelodenni() {
        val event = context("Narozeniny má 20. 10.").event
        assertEquals(EventCandidate(LocalDateTime(2026, 10, 20, 8, 0), allDay = true, certain = true), event)
    }

    @Test
    fun jenCasJeNejistyAJenDenNicNeni() {
        assertEquals(false, context("Sraz v 15:00 u kina").event?.certain)
        assertNull(context("ozvu se v pondělí").event)
        assertNull(context("Přijde nás 5").event)
    }

    @Test
    fun minulostNeniTermin() {
        assertNull(context("Včera v 15:00 jsem byl u doktora").event)
        assertNull(context("minulý pátek v 18:00 jsme hráli").event)
        assertNull(context("I was there yesterday at 3pm").event)
        // Minulý čas u termínu bez dne; se dnem jde o domluvu.
        assertNull(context("Volal jsem ti v 9:30, ale nebral jsi to").event)
        assertNull(context("Jsem tam byl v 15:00").event)
        assertEquals(true, context("Domluvili jsme se na zítra v 15:00").event?.certain)
    }

    @Test
    fun terminOdTelegramuJeJisty() {
        val at = LocalDateTime(2026, 10, 20, 18, 30)
        val entity = MessageEntity(0, 5, MessageEntityType.DateTime(at.toInstant(zone).epochSeconds, DateTimeFormat.Relative))
        assertEquals(EventCandidate(at, allDay = false, certain = true), context("Sraz!", listOf(entity)).event)
    }

    @Test
    fun nejistyTerminJenSPotvrzenimModelu() {
        val text = "Sraz v 15:00 u kina"
        assertTrue(findings(text).isEmpty())
        assertTrue(findings(text, EventVerdict.Pending).isEmpty())
        assertTrue(findings(text, EventVerdict.Rejected).isEmpty())
        val confirmed = findings(text, EventVerdict.Confirmed(title = "Kino", place = "kino")).single() as ContextFinding.Event
        assertEquals("Kino", confirmed.title)
        assertEquals("kino", confirmed.place)
        assertTrue(confirmed.fromModel)
    }

    @Test
    fun jistyTerminBezModeluIBehemPosuzovani() {
        val text = "Sraz zítra v 15:00 na Černém Mostě"
        val event = findings(text).single() as ContextFinding.Event
        assertEquals(text, event.title)
        assertFalse(event.fromModel)
        assertEquals(1, findings(text, EventVerdict.Pending).size)
        assertEquals(1, findings(text, EventVerdict.Unavailable).size)
        // Model řekl, že to schůzka není („v 15:00 jsem byl…").
        assertTrue(findings(text, EventVerdict.Rejected).isEmpty())
    }

    @Test
    fun minulyTerminSeNenabizi() {
        val text = "Sraz zítra v 15:00"
        assertTrue(findings(text, now = LocalDateTime(2026, 10, 16, 9, 0)).isEmpty())
        // Celodenní platí celý svůj den.
        assertEquals(1, findings("Narozeniny 20. 10.", now = LocalDateTime(2026, 10, 20, 23, 0)).size)
    }

    @Test
    fun nazevJePrvniRadekZkracenyNaSlovo() {
        assertEquals("Sraz", eventTitle("\n  Sraz  \nzítra v 15"))
        val long = "Ahoj, tak jak jsme se bavili, sejdeme se zítra v 15:00 před hlavním vchodem do obchodního centra"
        val title = eventTitle(long)
        assertTrue(title.endsWith("…"))
        assertTrue(title.length <= 61)
        assertTrue(long.startsWith(title.removeSuffix("…")))
    }

    @Test
    fun ceskyUcetSKontrolouABankou() {
        val payment = context("Pošli to prosím na 19-2000145399/0800, díky").payments.single()
        assertEquals("CZ6508000000192000145399", payment.iban)
        assertEquals("19-2000145399/0800", payment.account)
        // Kontrolní součet nesedí / neznámý kód banky / číslo jednací.
        assertTrue(context("účet 19-2000145398/0800").payments.isEmpty())
        assertTrue(context("č. j. 2000145399/2025").payments.isEmpty())
        assertTrue(context("Vyřízeno pod č. 1234/2025").payments.isEmpty())
    }

    @Test
    fun iban() {
        val payment = context("IBAN: CZ65 0800 0000 1920 0014 5399 prosím").payments.single()
        assertEquals("CZ6508000000192000145399", payment.iban)
        assertEquals("CZ65 0800 0000 1920 0014 5399", payment.account)
        // Slovo velkými písmeny za IBANem ho nerozbije.
        assertEquals("CZ6508000000192000145399", context("CZ6508000000192000145399 DIKY").payments.single().iban)
        assertTrue(context("CZ65 0800 0000 1920 0014 5398").payments.isEmpty())
    }

    @Test
    fun castkaASymbolKJedinemuUctu() {
        val payment = context("Za lístky 1 250 Kč na 19-2000145399/0800, VS: 20261014").payments.single()
        assertEquals("1250.00", payment.amount)
        assertEquals("CZK", payment.currency)
        assertEquals("20261014", payment.variableSymbol)
        assertEquals("SPD*1.0*ACC:CZ6508000000192000145399*AM:1250.00*CC:CZK*X-VS:20261014", payment.spayd())

        assertEquals("480.50", context("480,50 Kč na 19-2000145399/0800").payments.single().amount)
        assertEquals("EUR", context("€30 na CZ6508000000192000145399").payments.single().currency)
        // Dvě různé částky: nevíme, která to je.
        assertNull(context("buď 300 Kč, nebo 450 Kč na 19-2000145399/0800").payments.single().amount)
    }

    @Test
    fun spaydBezCastky() {
        assertEquals("SPD*1.0*ACC:CZ6508000000192000145399", context("19-2000145399/0800").payments.single().spayd())
    }

    @Test
    fun zasilky() {
        val post = context("Balík má číslo RR123456785CZ").parcels.single()
        assertEquals(ParcelCarrier.CzechPost, post.carrier)
        assertEquals("RR123456785CZ", post.number)
        assertEquals(ParcelCarrier.International, context("LX123456785CN").parcels.single().carrier)
        // Špatná kontrolní číslice.
        assertTrue(context("RR123456784CZ").parcels.isEmpty())

        val packeta = context("Zásilkovna Z 123 4567 890 je na cestě").parcels.single()
        assertEquals(ParcelCarrier.Packeta, packeta.carrier)
        assertEquals("Z1234567890", packeta.number)
        assertEquals("https://tracking.packeta.com/cs/Z1234567890", packeta.trackingUrl("cs"))
        assertEquals("https://tracking.packeta.com/en/Z1234567890", packeta.trackingUrl("de"))
    }

    @Test
    fun zasilkyPplADpdJenSeJmenemDopravce() {
        val ppl = context("PPL: Vaše zásilka 40012345678 bude doručena dnes mezi 9 a 12").parcels.single()
        assertEquals(ParcelCarrier.Ppl, ppl.carrier)
        assertEquals("40012345678", ppl.number)
        assertEquals("https://www.ppl.cz/vyhledat-zasilku?shipmentId=40012345678", ppl.trackingUrl("cs"))
        assertEquals("https://www.ppl.cz/en/track-a-shipment?shipmentId=40012345678", ppl.trackingUrl("en"))
        // Jméno dopravce smí stát i za číslem.
        assertEquals(ParcelCarrier.Ppl, context("Balík 40012345678 veze PPL.").parcels.single().carrier)

        val dpd = context("Číslo zásilky DPD: 01234567890123").parcels.single()
        assertEquals(ParcelCarrier.Dpd, dpd.carrier)
        assertEquals("01234567890123", dpd.number)
        assertEquals("https://www.dpdgroup.com/cz/mydpd/my-parcels/search?lang=cz&parcelNumber=01234567890123", dpd.trackingUrl("cs"))
        assertEquals("https://www.dpdgroup.com/cz/mydpd/my-parcels/search?lang=en&parcelNumber=01234567890123", dpd.trackingUrl("de"))

        // Bez jména dopravce je to jen číslo — objednávka, telefon, cokoli.
        assertTrue(context("Objednávka 40012345678 je zaplacená").parcels.isEmpty())
        assertTrue(context("Číslo zásilky: 01234567890123").parcels.isEmpty())
        // „ppl" malými je v angličtině „people", ne dopravce.
        assertTrue(context("ppl can text me at 12025550123").parcels.isEmpty())
        // Délka musí sedět k dopravci: 11 číslic s DPD ani 14 s PPL nic není.
        assertTrue(context("DPD 40012345678").parcels.isEmpty())
        assertTrue(context("PPL 01234567890123").parcels.isEmpty())
        // Kus delšího čísla, účtu nebo desetinného čísla zásilkou není.
        assertTrue(context("PPL 400123456789").parcels.isEmpty())
        assertTrue(context("PPL platba na 19-40012345678/0800").parcels.isEmpty())
        assertTrue(context("PPL 40012345678,50 Kč").parcels.isEmpty())
        // Jméno dopravce daleko od čísla nestačí.
        val far = "PPL " + "a".repeat(100) + " 40012345678"
        assertTrue(context(far).parcels.isEmpty())
        // Číslo zásilky není telefon.
        assertTrue(context("PPL: Vaše zásilka 40012345678").contacts.isEmpty())
    }

    @Test
    fun s10KontrolniCislice() {
        assertTrue(s10Valid("123456785"))
        assertFalse(s10Valid("123456789"))
    }

    private fun addresses(text: String) = context(text).addresses.map { it.text }

    @Test
    fun ceskaAdresa() {
        assertEquals(listOf("Vinohradská 1511/230, 100 00 Praha 10"), addresses("Jsme na adrese Vinohradská 1511/230, 100 00 Praha 10."))
        assertEquals(listOf("Vinohradská 12, Praha 2"), addresses("Sraz Vinohradská 12, Praha 2 u vchodu"))
        assertEquals(listOf("Na Příkopě 22, Praha"), addresses("Kancelář je Na Příkopě 22, Praha"))
        assertEquals(listOf("nám. Míru 5, Brno"), addresses("Bydlím nám. Míru 5, Brno"))
        assertEquals(listOf("Masarykova 3, Ústí nad Labem"), addresses("Masarykova 3, Ústí nad Labem"))
        assertEquals(listOf("Hauptstraße 5, 10115 Berlin"), addresses("Hotel: Hauptstraße 5, 10115 Berlin"))
    }

    @Test
    fun anglickaAdresa() {
        assertEquals(listOf("221B Baker Street, London"), addresses("Visit us at 221B Baker Street, London."))
        assertEquals(listOf("5 Main St"), addresses("Pick me up at 5 Main St."))
    }

    @Test
    fun coAdresaNeni() {
        // Bez PSČ, klíčového slova i koncovky ulice.
        assertTrue(addresses("Kapitola 12, Praha je dobrá").isEmpty())
        assertTrue(addresses("Přijde nás 12, Petr taky").isEmpty())
        // Bez města.
        assertTrue(addresses("Vinohradská 12").isEmpty())
        assertTrue(addresses("I have 3 Big Dogs").isEmpty())
    }

    @Test
    fun kontaktSeJmenemTelefonemAMailem() {
        val contact = context("Kontakt na instalatéra: Jan Novák, tel. +420 602 123 456, jan.novak@firma.cz").contacts.single()
        assertEquals(ContextFinding.Contact("Jan Novák", "+420 602 123 456", "jan.novak@firma.cz"), contact)
        assertEquals("Petra Malá", context("Petra Malá: 777 888 999").contacts.single().name)
    }

    @Test
    fun kontaktBezJmena() {
        // Bez oddělovače jméno není: „Zavolej Petrovi" jméno není.
        assertEquals(ContextFinding.Contact(null, "602 123 456", null), context("Zavolej Petrovi 602 123 456").contacts.single())
        assertEquals(ContextFinding.Contact(null, null, "info@plume.app"), context("piš na info@plume.app").contacts.single())
    }

    @Test
    fun telefonZEntityTelegramu() {
        val text = "Volej 00420602123456"
        val entity = MessageEntity(6, 14, MessageEntityType.Link("tel:00420602123456"))
        assertEquals("00420602123456", context(text, listOf(entity)).contacts.single().phone)
        // Totéž číslo podruhé (entita i regulár) je jeden kontakt.
        val twice = "Volej +420 602 123 456"
        val tel = MessageEntity(6, 16, MessageEntityType.Link("tel:+420 602 123 456"))
        assertEquals(1, context(twice, listOf(tel)).contacts.size)
    }

    @Test
    fun dvaTelefonyJsouDvaKontakty() {
        val contacts = context("Jan Novák: 602 123 456, Petra Malá: 777 888 999").contacts
        assertEquals(listOf("Jan Novák", "Petra Malá"), contacts.map { it.name })
    }

    @Test
    fun coKontaktemNeni() {
        // Účet, variabilní symbol, zásilka, krátké číslo.
        assertTrue(context("Pošli na 19-2000145399/0800, VS 602123456").contacts.isEmpty())
        assertTrue(context("Balík RR223456785CZ").contacts.isEmpty())
        assertTrue(context("PIN je 1234 a kód 123 456").contacts.isEmpty())
    }

    @Test
    fun cisloLetu() {
        val flight = context("Letím zítra, let OK 123 v 9:40 z Ruzyně").flights.single()
        assertEquals(ContextFinding.Flight("OK", "123", at = LocalDateTime(2026, 10, 15, 9, 40), from = "PRG"), flight)
        assertEquals("https://www.flightaware.com/live/flight/OK123", flight.trackingUrl())
        assertEquals("FR1234", context("Flight FR1234 is delayed, gate B12").flights.single().code)
        assertEquals("W62345", context("Odlet W6 2345 ve 14:00").flights.single().code)
    }

    @Test
    fun letDoKalendare() {
        // Stačí i samotný čas — že jde o let, víme z čísla letu.
        val departure = context("Let FR1234 odlétá v 6:15").flights.single()
        assertEquals(LocalDateTime(2026, 10, 15, 6, 15), departure.at)
        assertFalse(departure.arrival)
        // Přistání = přílet; termín pak nese let a obecná událost se nenabízí.
        val text = "Přiletím letem BA 117, přistání na letišti zítra ve 14:30"
        val arrival = findings(text).filterIsInstance<ContextFinding.Flight>().single()
        assertTrue(arrival.arrival)
        assertEquals(LocalDateTime(2026, 10, 15, 14, 30), arrival.at)
        assertTrue(findings(text).none { it is ContextFinding.Event })
        // Odlet i přílet ve zprávě: čas patří odletu.
        assertFalse(context("Odlet OK 123 zítra v 9:40, přílet ve 12:00").flights.single().arrival)
    }

    @Test
    fun trasaADelkaLetu() {
        // Dva časy v pásmech letišť: Praha 9:40 → Londýn 10:55 jsou 2 h 15 min, ne 1 h 15 min.
        val flight = context("Let OK 123 PRG → LHR zítra, odlet 9:40, přílet 10:55").flights.single()
        assertEquals("PRG", flight.from)
        assertEquals("LHR", flight.to)
        val schedule = flight.schedule(TimeZone.of("Europe/Prague"))!!
        assertEquals(135, schedule.minutes)
        assertFalse(schedule.estimated)
        assertEquals(LocalDateTime(2026, 10, 15, 7, 40).toInstant(TimeZone.UTC), schedule.start)
    }

    @Test
    fun odhadDelkyZTrasy() {
        val flight = context("Let FR1234 z Ruzyně do LHR zítra v 6:15").flights.single()
        assertEquals("PRG", flight.from)
        val schedule = flight.schedule(TimeZone.of("Europe/Prague"))!!
        assertTrue(schedule.estimated)
        assertEquals(120, schedule.minutes)
    }

    @Test
    fun ceskaMestaLetist() {
        val flight = context("Letím zítra OK 123 z Prahy do Londýna v 9:40").flights.single()
        assertEquals("PRG" to "LHR", flight.from to flight.to)
        assertEquals("VIE" to "CDG", context("Let OS 251 z Vídně do Paříže").flights.single().let { it.from to it.to })
        assertEquals("FCO", context("Let FR1234 do Říma").flights.single().to)
        assertEquals("HER", context("Odlet QS 1234 na Krétu").flights.single().to)
        assertEquals("JFK", context("Let OK 52 do New Yorku").flights.single().to)
        // Anglicky jen za from / to.
        assertEquals("PRG" to "LHR", context("Flight BA 855 from Prague to London").flights.single().let { it.from to it.to })
        // Kód letiště má přednost před městem.
        assertEquals("LGW", context("Let U2 1234 z PRG do LGW, přes Londýn").flights.single().to)
    }

    @Test
    fun mestoBezLetuNeniLetiste() {
        // Bez čísla letu se trasa nehledá vůbec; „to split" není Split.
        assertTrue(context("Jedu z Prahy do Brna").flights.isEmpty())
        assertNull(context("Flight BA 855, we need to split the bill").flights.single().to)
    }

    @Test
    fun nocniLetPresPulnoc() {
        val schedule = context("Let OK 123 PRG-DXB zítra, odlet 22:30, přílet 6:20").flights.single()
            .schedule(TimeZone.of("Europe/Prague"))!!
        // 22:30 v Praze (20:30 UTC) → 6:20 v Dubaji (2:20 UTC) druhý den.
        assertEquals(350, schedule.minutes)
    }

    @Test
    fun letBezTrasyMaJenCas() {
        val schedule = context("Let OK 123 zítra v 9:40").flights.single().schedule(TimeZone.of("Europe/Prague"))!!
        assertNull(schedule.end)
        assertNull(schedule.minutes)
        // Neznámé letiště ani zkratka trasou nejsou.
        assertNull(context("Let OK 123 ABC-XYZ zítra v 9:40").flights.single().from)
    }

    @Test
    fun minulyLetDoKalendareNe() {
        val flight = findings("Let OK 123 zítra v 9:40", now = LocalDateTime(2026, 10, 16, 8, 0))
            .filterIsInstance<ContextFinding.Flight>().single()
        assertNull(flight.at)
    }

    @Test
    fun coLetemNeni() {
        // Bez slova o letu, neznámý dopravce, variabilní symbol.
        assertTrue(context("Ok 2 lidi, OK 123").flights.isEmpty())
        assertTrue(context("Náš let XX 123").flights.isEmpty())
        assertTrue(context("Let mi zaplatit, VS 1234").flights.isEmpty())
    }

    @Test
    fun wifiSHeslem() {
        assertEquals(ContextFinding.Wifi("Plume_Home", "tajne1234"), context("Wi-Fi: Plume_Home, heslo: tajne1234").wifi)
        assertEquals(ContextFinding.Wifi("Chata U Lesa", "medved2026"), context("Síť se jmenuje „Chata U Lesa“ a heslo k wifi je medved2026.").wifi)
        assertEquals(ContextFinding.Wifi("Office", "p@ss;word"), context("SSID: Office password: \"p@ss;word\"").wifi)
        assertEquals(ContextFinding.Wifi("Chata_Lesni", "medved2026"), context("Wi-Fi na chatě: Chata_Lesni, heslo: medved2026").wifi)
    }

    @Test
    fun wifiZQrKodu() {
        val wifi = context("WIFI:T:WPA;S:My\\;Net;P:secret12;;").wifi
        assertEquals(ContextFinding.Wifi("My;Net", "secret12"), wifi)
        assertEquals("WIFI:T:WPA;S:My\\;Net;P:secret12;;", wifi?.qrText())
    }

    @Test
    fun coWifiNeni() {
        // Bez hesla, heslo kratší než 8 znaků, „wifi heslo" bez názvu sítě.
        assertNull(context("Wi-Fi Plume_Home nefunguje").wifi)
        assertNull(context("Wi-Fi: Plume_Home, heslo: abc").wifi)
        assertNull(context("Wifi heslo je tajne1234").wifi)
    }

    private fun codes(text: String) = context(text).codes.map { it.value }

    @Test
    fun kodKeZkopirovani() {
        assertEquals(listOf("LETO20"), codes("Slevový kód na první nákup: LETO20, platí do neděle"))
        assertEquals(listOf("4821"), codes("Kód ke dveřím je 4821"))
        assertEquals(listOf("SAVE-10"), codes("Use promo code SAVE-10 at checkout"))
        assertEquals(listOf("482913"), codes("Váš ověřovací kód: 482913"))
    }

    @Test
    fun coKodemNeni() {
        // Přihlašovací kód Telegramu, obyčejné slovo, příliš dlouhé číslo, bez klíčového slova.
        assertTrue(codes("Login code: 48291. Do not give this code to anyone, even if they say they are from Telegram!").isEmpty())
        assertTrue(codes("Kód je jednoduchý, napíšu ti").isEmpty())
        assertTrue(codes("kód 602123456").isEmpty())
        assertTrue(codes("Sleva LETO20").isEmpty())
    }

    private fun money(text: String, home: String? = "CZK") =
        findMessageContext(text, emptyList(), wednesday, zone, home).money.map { it.currency to it.amount }

    @Test
    fun ciziMena() {
        assertEquals(listOf("EUR" to 120.0), money("Hotel stojí 120 € na noc"))
        assertEquals(listOf("USD" to 45.5), money("It's \$45.50 per person"))
        assertEquals(listOf("PLN" to 300.0), money("Vstupné 300 PLN"))
        assertEquals(listOf("HUF" to 2500.0), money("Parkování 2 500 Ft"))
        assertEquals(listOf("EUR" to 1250.5), money("Celkem 1.250,50 EUR"))
        assertEquals(listOf("GBP" to 1250.0), money("Rent is £1,250 a month"))
        assertEquals(listOf("EUR" to 15.0), money("Dej mi 15 eur"))
    }

    @Test
    fun menaSlovy() {
        assertEquals(listOf("USD" to 50.0), money("Stálo to 50 dolarů"))
        assertEquals(listOf("GBP" to 100.0), money("Půjčil jsem mu 100 liber"))
        assertEquals(listOf("PLN" to 300.0), money("Vstupenka za 300 zlotých"))
        assertEquals(listOf("HUF" to 450.0), money("Benzín 450 forintů za litr"))
        assertEquals(listOf("SEK" to 1000.0), money("Stojí 1 000 švédských korun"))
        assertEquals(listOf("CAD" to 50.0), money("Dal mi 50 kanadských dolarů"))
        assertEquals(listOf("JPY" to 2_000_000.0), money("Byt za 2 miliony jenů"))
        assertEquals(listOf("USD" to 1_500_000.0), money("Firma má 1,5 milionu dolarů"))
        assertEquals(listOf("USD" to 30.0), money("It costs 30 dollars"))
        assertEquals("1,5 milionu dolarů", findMessageContext("Firma má 1,5 milionu dolarů", emptyList(), wednesday, zone, "CZK").money.single().shown)
    }

    private fun moneyAssumed(text: String) =
        findMessageContext(text, emptyList(), wednesday, zone, "CZK").money.map { Triple(it.currency, it.amount, it.assumed) }

    @Test
    fun jenNeboJuan() {
        assertEquals(listOf(Triple("JPY", 500.0, false)), moneyAssumed("Oběd v Tokiu stál ¥500"))
        assertEquals(listOf(Triple("CNY", 50.0, false)), moneyAssumed("V Pekingu jsem dal za čaj ¥50"))
        // Bez kontextu obě možnosti, se značkou „odhad".
        assertEquals(listOf(Triple("JPY", 800.0, true), Triple("CNY", 800.0, true)), moneyAssumed("Stálo to ¥800"))
        assertEquals(listOf(Triple("CNY", 100.0, false)), moneyAssumed("Zaplatil jsem 100元"))
        assertEquals(listOf(Triple("JPY", 1200.0, false)), moneyAssumed("Ramen za 1200円"))
    }

    @Test
    fun poundsJakoPenize() {
        assertEquals(listOf(Triple("GBP", 30.0, false)), moneyAssumed("The ticket costs 30 pounds"))
        assertEquals(listOf(Triple("GBP", 20.0, false)), moneyAssumed("Lend me 20 quid"))
        // Váha penězi není, bez kontextu obojí (a váha se nabídne jako jednotka).
        assertTrue(moneyAssumed("It weighs 5 pounds").isEmpty())
        assertEquals(listOf(Triple("GBP", 5.0, true)), moneyAssumed("I have 5 pounds"))
        assertEquals(listOf(MeasureUnit.Pound to 5.0), measures("I have 5 pounds"))
        assertTrue(measures("The ticket costs 30 pounds").isEmpty())
    }

    @Test
    fun menaSlovyCoNeni() {
        // „jen" je „pouze", koruny Čechovi nic, pounds ve zprávě o váze nejsou peníze.
        assertTrue(money("Přišlo 100 jen tak").isEmpty())
        assertTrue(money("Stojí 300 korun").isEmpty())
        assertTrue(money("It weighs 5 pounds").isEmpty())
    }

    @Test
    fun coSeNeprevadi() {
        // Vlastní měna, neznámý kód, stopy místo forintů.
        assertTrue(money("Stojí to 250 Kč").isEmpty())
        assertTrue(money("Zbývá 10 SMS a 5 GB").isEmpty())
        assertTrue(money("Strop je 8 ft vysoký").isEmpty())
        // Bez známé vlastní měny se převádí i koruna.
        assertEquals(listOf("CZK" to 250.0), money("Stojí to 250 Kč", home = null))
    }

    @Test
    fun cisloVRuznychZapisech() {
        assertEquals(1250.5, parseAmount("1 250,50"))
        assertEquals(1250.5, parseAmount("1,250.50"))
        assertEquals(1250.0, parseAmount("1,250"))
        assertEquals(12.5, parseAmount("12,5"))
    }

    @Test
    fun castkaNaDveMista() {
        assertEquals("2\u202F923" to "80", moneyParts(2923.8))
        assertEquals("0" to "05", moneyParts(0.049))
    }

    private fun measures(text: String, metric: Boolean = true) =
        findMessageContext(text, emptyList(), wednesday, zone, metric = metric).measures.map { it.unit to it.value }

    @Test
    fun imperialniJednotkyMetrickemu() {
        assertEquals(listOf(MeasureUnit.Inch to 65.0), measures("Koupil jsem televizi 65 palců"))
        assertEquals(listOf(MeasureUnit.Mile to 26.2), measures("The marathon is 26.2 miles"))
        assertEquals(listOf(MeasureUnit.Fahrenheit to -5.0), measures("It's -5 °F outside"))
        assertEquals(listOf(MeasureUnit.Pound to 2.0, MeasureUnit.Ounce to 8.0), measures("2 lbs and 8 oz of flour"))
        assertEquals(listOf(MeasureUnit.MilesPerHour to 70.0), measures("Speed limit 70 mph"))
        // Metrické jednotky Čechovi nic nepřevádí.
        assertTrue(measures("Uběhl jsem 10 km a vážím 80 kg").isEmpty())
    }

    @Test
    fun metrickeJednotkyAmericanovi() {
        assertEquals(listOf(MeasureUnit.Kilometer to 5.0, MeasureUnit.Celsius to 20.0), measures("Run 5 km, it's 20 °C", metric = false))
        assertTrue(measures("Speed limit 70 mph", metric = false).isEmpty())
    }

    @Test
    fun coJednotkouNeni() {
        // „2 in 1" nejsou palce, „5G" síť, záporná délka nedává smysl.
        assertTrue(measures("2 in 1 shampoo").isEmpty())
        assertTrue(measures("Mám 5G a 10 GB", metric = false).isEmpty())
        assertTrue(measures("-3 miles").isEmpty())
    }

    @Test
    fun prevodJednotek() {
        assertEquals(165.1, MeasureUnit.Inch.convert(65.0), 1e-9)
        assertEquals(MeasureUnit.Centimeter, MeasureUnit.Inch.target)
        assertEquals(-20.0, MeasureUnit.Fahrenheit.convert(-4.0), 1e-9)
        assertEquals("165" to null, measureParts(165.1))
        assertEquals("42" to "2", measureParts(42.16))
        assertEquals("20" to null, measureParts(20.0))
        assertEquals("\u221220" to null, measureParts(-20.0))
        assertEquals("1\u202F609" to null, measureParts(1609.3))
    }

    private fun bookings(text: String) = context(text).bookings.map { it.value }

    @Test
    fun kodRezervace() {
        assertEquals(listOf("XKQ7ZT"), bookings("Kód rezervace: XKQ7ZT, let OK 123"))
        assertEquals(listOf("4123.456.789"), bookings("Číslo vaší rezervace je 4123.456.789."))
        assertEquals(listOf("AB12CD"), bookings("Booking reference AB12CD"))
        assertEquals(listOf("K8M2P"), bookings("PNR: K8M2P"))
        // Tentýž kód se nenabídne ještě jednou jako obecný kód.
        assertTrue(context("Kód rezervace: XKQ7ZT").codes.isEmpty())
    }

    @Test
    fun coRezervaciNeni() {
        assertTrue(bookings("Rezervace je na pátek").isEmpty())
        assertTrue(bookings("Booking ABC").isEmpty())
        assertTrue(bookings("Rezervace 12345").isEmpty())
    }

    @Test
    fun obycejnaZpravaNicNema() {
        assertTrue(context("Ahoj, jak se máš?").isEmpty)
        assertTrue(context("").isEmpty)
    }

    @Test
    fun mapySeNabidnouJenUMistaKtereVypadaJakoAdresa() {
        fun query(place: String?) = ContextFinding.Event(
            start = LocalDateTime(2026, 10, 20, 18, 0),
            allDay = false,
            title = "Sraz",
            place = place,
            description = "",
            fromModel = true,
        ).mapsQuery()
        assertEquals("Vinohradská 12", query("Vinohradská 12"))
        assertEquals("na Černém Mostě u kina", query("na Černém Mostě u kina"))
        assertEquals("U Fleků", query("U Fleků"))
        assertEquals("Café Louvre", query(" Café Louvre "))
        assertEquals("120 00 Praha", query("120 00 Praha"))
        // Vágní místa: mapy by ukázaly něco náhodného v okolí.
        assertNull(query("letišti"))
        assertNull(query("Kotvy"))
        assertNull(query("u kina"))
        assertNull(query("Na letišti"))
        assertNull(query(" "))
        assertNull(query(null))
    }

    @Test
    fun dvouslovnaUliceSPsc() {
        assertEquals(listOf("Karla Engliše 3, 150 00 Praha 5"), addresses("Sídlo: Karla Engliše 3, 150 00 Praha 5."))
        assertEquals(listOf("Boženy Němcové 12, 602 00 Brno"), addresses("Kancelář Boženy Němcové 12, 602 00 Brno"))
        assertEquals(listOf("Kpt. Jaroše 7, 602 00 Brno"), addresses("Přijďte na Kpt. Jaroše 7, 602 00 Brno"))
        // Slovo před jednoslovnou ulicí se nespolkne.
        assertEquals(listOf("Vinohradská 12, 120 00 Praha"), addresses("Sraz Vinohradská 12, 120 00 Praha"))
        assertEquals(listOf("Vinohradská 12, 120 00 Praha"), addresses("Adresa Vinohradská 12, 120 00 Praha"))
        // Bez PSČ je to moc nejisté — dvě slova velkým písmenem před číslem jsou skoro cokoli.
        assertEquals(emptyList(), addresses("Karla Engliše 3, Praha"))
        // První slovo nevypadá jako jméno ve 2. pádě: „Hotel" se k ulici nepřilepí (zbytek
        // s PSČ najde jednoslovné pravidlo jako dřív).
        assertEquals(listOf("Praha 3, 150 00 Praha"), addresses("Hotel Praha 3, 150 00 Praha"))
    }
}
