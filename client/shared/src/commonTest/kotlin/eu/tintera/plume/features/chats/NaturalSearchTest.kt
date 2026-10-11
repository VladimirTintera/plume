package eu.tintera.plume.features.chats

import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.telegram.MessageSearchFilter
import eu.tintera.plume.core.telegram.SearchContent
import eu.tintera.plume.core.telegram.SearchPeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Hledání z věty: pole od modelu, pravidla nad větou a kdy se „Hledat s AI" nabízí. */
class NaturalSearchTest {
    private val today = LocalDate(2026, 10, 11)
    private val zone = TimeZone.of("Europe/Prague")
    private fun day(date: LocalDate) = date.atStartOfDayIn(zone).epochSeconds
    private fun range(from: LocalDate, to: LocalDate) = SearchPeriod.Custom(day(from), day(to))

    @Test
    fun poleOdModelu() {
        val found = naturalSearchFrom(
            mapOf("TEXT" to "dovolené", "CHATS" to "GROUPS", "CONTENT" to "PHOTOS", "FROM" to "2025-06-01", "TO" to "2025-08-31"),
            today,
            zone,
        )
        assertEquals("dovolené", found.text)
        assertEquals(MessageSearchFilter(ChatKind.Group, range(LocalDate(2025, 6, 1), LocalDate(2025, 8, 31)), content = SearchContent.Photos), found.filter)
    }

    @Test
    fun dataOdModeluSeOveruji() {
        // Budoucnost se zastropuje dneškem, obrácený rozsah se otočí, nesmysl se zahodí.
        assertEquals(
            range(LocalDate(2026, 10, 1), today),
            naturalSearchFrom(mapOf("FROM" to "2026-10-01", "TO" to "2027-01-01"), today, zone).filter.period,
        )
        assertEquals(
            range(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30)),
            naturalSearchFrom(mapOf("FROM" to "2026-09-30", "TO" to "2026-09-01"), today, zone).filter.period,
        )
        assertEquals(
            range(LocalDate(2026, 10, 5), today),
            naturalSearchFrom(mapOf("FROM" to "2026-10-05", "TO" to "NONE"), today, zone).filter.period,
        )
        assertNull(naturalSearchFrom(mapOf("FROM" to "2026-02-30"), today, zone).filter.period)
    }

    /** Skutečné odpovědi Gemmy 4 E2B z emulátoru — pravidla je musí opravit. */
    @Test
    fun pravidlaOpraviModel() {
        val photos = refineNaturalSearch(
            NaturalSearch("fotky", MessageSearchFilter(ChatKind.Channel, range(LocalDate(2025, 10, 12), today), content = SearchContent.Photos)),
            "fotky z kanalu za posledni mesic",
            today,
            zone,
        )!!
        assertEquals(NaturalSearch("", MessageSearchFilter(ChatKind.Channel, SearchPeriod.Month, content = SearchContent.Photos)), photos)

        val contract = refineNaturalSearch(
            NaturalSearch("souborech", MessageSearchFilter(period = range(LocalDate(2025, 10, 12), today))),
            "smlouva v souborech z lonskeho roku",
            today,
            zone,
        )!!
        assertEquals("smlouva", contract.text)
        assertEquals(SearchContent.Files, contract.filter.content)
        assertEquals(range(LocalDate(2025, 1, 1), LocalDate(2025, 12, 31)), contract.filter.period)

        val recipe = refineNaturalSearch(
            NaturalSearch("recept na gulas ze skupin", MessageSearchFilter(ChatKind.Group)),
            "recept na gulas ze skupin",
            today,
            zone,
        )!!
        assertEquals(NaturalSearch("recept na gulas", MessageSearchFilter(ChatKind.Group)), recipe)
    }

    @Test
    fun slovaJenZVety() {
        // Model slovo převedl na jiný tvar („dovolená") — TDLib by nenašel nic; bere se z věty.
        val found = refineNaturalSearch(NaturalSearch("dovolená", MessageSearchFilter()), "fotky z dovolené", today, zone)!!
        assertEquals("dovolené", found.text)
        assertEquals(SearchContent.Photos, found.filter.content)
    }

    @Test
    fun obdobiPravidly() {
        fun periodOf(query: String) = refineNaturalSearch(null, query, today, zone)?.filter?.period
        assertEquals(SearchPeriod.Week, periodOf("pizza minuly tyden"))
        assertEquals(range(LocalDate(2026, 1, 1), today), periodOf("pizza letos"))
        assertEquals(range(LocalDate(2026, 10, 10), LocalDate(2026, 10, 10)), periodOf("pizza vcera"))
    }

    @Test
    fun bezSlovIObsahuHledatNejde() {
        assertNull(refineNaturalSearch(NaturalSearch("skupiny", MessageSearchFilter()), "skupiny z minuleho tydne", today, zone))
        assertNull(refineNaturalSearch(null, "", today, zone))
    }

    @Test
    fun vetaNeJmeno() {
        assertTrue(naturalSentence("fotky ze skupin loni"))
        assertFalse(naturalSentence("Jana Nováková"))
        assertFalse(naturalSentence("  pizza  "))
    }
}
