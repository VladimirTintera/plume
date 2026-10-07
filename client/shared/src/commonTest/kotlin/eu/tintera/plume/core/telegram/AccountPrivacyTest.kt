package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.telegram.PrivacyRule.AllowAll
import eu.tintera.plume.core.telegram.PrivacyRule.AllowBots
import eu.tintera.plume.core.telegram.PrivacyRule.AllowChatMembers
import eu.tintera.plume.core.telegram.PrivacyRule.AllowContacts
import eu.tintera.plume.core.telegram.PrivacyRule.AllowPremium
import eu.tintera.plume.core.telegram.PrivacyRule.AllowUsers
import eu.tintera.plume.core.telegram.PrivacyRule.RestrictAll
import eu.tintera.plume.core.telegram.PrivacyRule.RestrictChatMembers
import eu.tintera.plume.core.telegram.PrivacyRule.RestrictContacts
import eu.tintera.plume.core.telegram.PrivacyRule.RestrictUsers
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountPrivacyTest {

    @Test
    fun bezZakladuJeToNikdo() {
        // Nic nesedí = zákaz, tak to TDLib čte.
        assertEquals(PrivacyBase.Nobody, privacyChoiceOf(emptyList()).base)
        assertEquals(PrivacyBase.Nobody, privacyChoiceOf(listOf(AllowUsers(listOf(1)))).base)
    }

    @Test
    fun zakladJePrvniZeTriVoleb() {
        assertEquals(PrivacyBase.Everybody, privacyChoiceOf(listOf(AllowAll)).base)
        assertEquals(PrivacyBase.Contacts, privacyChoiceOf(listOf(AllowContacts, RestrictAll)).base)
        assertEquals(PrivacyBase.Nobody, privacyChoiceOf(listOf(RestrictAll, AllowAll)).base)
    }

    @Test
    fun zaUkoncujiciVolbouSeNecte() {
        // Za AllowAll už nic nesedí, takže výjimka za ním nic neznamená.
        val choice = privacyChoiceOf(listOf(AllowAll, RestrictUsers(listOf(5))))
        assertEquals(emptyList(), choice.neverUsers)
    }

    @Test
    fun zaKontaktySeVyjimkyCtou() {
        val choice = privacyChoiceOf(
            listOf(RestrictUsers(listOf(1)), AllowContacts, AllowUsers(listOf(2)), AllowChatMembers(listOf(-10))),
        )
        assertEquals(PrivacyBase.Contacts, choice.base)
        assertEquals(listOf(1L), choice.neverUsers)
        assertEquals(listOf(2L), choice.alwaysUsers)
        assertEquals(listOf(-10L), choice.alwaysChats)
    }

    @Test
    fun dvakratUvedenyPatriDrivejsimu() {
        val choice = privacyChoiceOf(listOf(AllowUsers(listOf(7)), RestrictUsers(listOf(7)), RestrictAll))
        assertEquals(listOf(7L), choice.alwaysUsers)
        assertEquals(emptyList(), choice.neverUsers)
    }

    @Test
    fun zapisMaVyjimkyLidiPredSkupinamiAZakladNaKonci() {
        val choice = PrivacyChoice(
            base = PrivacyBase.Contacts,
            alwaysUsers = listOf(1),
            alwaysChats = listOf(-10),
            neverUsers = listOf(2),
            neverChats = listOf(-20),
        )
        assertEquals(
            listOf(
                AllowUsers(listOf(1)),
                RestrictUsers(listOf(2)),
                AllowChatMembers(listOf(-10)),
                RestrictChatMembers(listOf(-20)),
                AllowContacts,
            ),
            privacyRulesOf(choice, previous = emptyList()),
        )
    }

    @Test
    fun neplatneVyjimkySeNezapisuji() {
        // „Všichni" nepotřebuje „vždy", „Nikdo" nepotřebuje „nikdy".
        val choice = PrivacyChoice(PrivacyBase.Everybody, alwaysUsers = listOf(1), neverUsers = listOf(2))
        assertEquals(listOf(RestrictUsers(listOf(2)), AllowAll), privacyRulesOf(choice, emptyList()))
        assertEquals(
            listOf(AllowUsers(listOf(1)), RestrictAll),
            privacyRulesOf(choice.withBase(PrivacyBase.Nobody), emptyList()),
        )
    }

    @Test
    fun nastaveniBezVyjimekJeNezapisuje() {
        val choice = PrivacyChoice(PrivacyBase.Contacts, neverUsers = listOf(2))
        assertEquals(listOf(AllowContacts), privacyRulesOf(choice, emptyList(), exceptions = false))
    }

    @Test
    fun neznamaPravidlaSePrenasejiJenPredUkoncujiciVolbou() {
        val previous = listOf(AllowBots, AllowPremium, AllowAll, RestrictContacts)
        val written = privacyRulesOf(PrivacyChoice(PrivacyBase.Nobody), previous)
        // RestrictContacts za AllowAll nikdy neplatilo — po přesunu před RestrictAll by začalo.
        assertEquals(listOf(AllowBots, AllowPremium, RestrictAll), written)
    }

    @Test
    fun cteniAZapisSeVraciNaStejnaPravidla() {
        val rules = listOf(
            AllowUsers(listOf(1, 2)),
            RestrictUsers(listOf(3)),
            AllowChatMembers(listOf(-10)),
            AllowPremium,
            AllowContacts,
        )
        assertEquals(rules, privacyRulesOf(privacyChoiceOf(rules), rules))
    }

    @Test
    fun pridanaVyjimkaZmiziZOpacneStrany() {
        val choice = PrivacyChoice(PrivacyBase.Contacts, neverUsers = listOf(1, 2), neverChats = listOf(-10))
            .withAdded(always = true, users = listOf(1, 1), chats = listOf(-10))
        assertEquals(listOf(1L), choice.alwaysUsers)
        assertEquals(listOf(2L), choice.neverUsers)
        assertEquals(listOf(-10L), choice.alwaysChats)
        assertEquals(emptyList(), choice.neverChats)
    }

    @Test
    fun odebraniRozlisujeCloverkaASkupinu() {
        // Člověk a skupina můžou mít stejné číslo.
        val choice = PrivacyChoice(PrivacyBase.Contacts, neverUsers = listOf(5), neverChats = listOf(5))
        val removed = choice.withRemoved(always = false, id = 5, isGroup = true)
        assertEquals(listOf(5L), removed.neverUsers)
        assertEquals(emptyList(), removed.neverChats)
    }

    @Test
    fun pocetVyjimekJenPlatnych() {
        val choice = PrivacyChoice(PrivacyBase.Everybody, alwaysUsers = listOf(1, 2), neverChats = listOf(-1))
        assertEquals(1, choice.exceptionCount)
        assertEquals(3, choice.withBase(PrivacyBase.Contacts).exceptionCount)
        assertEquals(2, choice.withBase(PrivacyBase.Nobody).exceptionCount)
    }

    @Test
    fun skryteVyjimkyPrezijiOdpovedServeru() {
        val local = PrivacyChoice(PrivacyBase.Everybody, alwaysUsers = listOf(1), neverUsers = listOf(2))
        // Server po přepnutí na „Všichni" vrátí jen platná pravidla: „vždy" v nich chybí.
        val server = PrivacyChoice(PrivacyBase.Everybody, neverUsers = listOf(2, 3))
        val merged = mergeHiddenExceptions(server, local)
        assertEquals(listOf(1L), merged.alwaysUsers)
        assertEquals(listOf(2L, 3L), merged.neverUsers)
        assertEquals(server, mergeHiddenExceptions(server, null))
    }
}
