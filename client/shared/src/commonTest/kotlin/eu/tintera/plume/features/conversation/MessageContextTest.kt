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
    fun obycejnaZpravaNicNema() {
        assertTrue(context("Ahoj, jak se máš?").isEmpty)
        assertTrue(context("").isEmpty)
    }
}
