package eu.tintera.plume.core.di

import eu.tintera.plume.core.accounts.AccountSlot
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.error.NoDefinitionFoundException
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Kontejner účtu vidí do globálního Koinu, naopak to nejde. Moduly se v testu jen skládají
 * a kontejnery staví z náhražek: klient TDLib by v testu na JVM nevznikl.
 */
@OptIn(KoinInternalApi::class)
class AccountContainerTest {

    private class GlobalThing
    private class AccountThing(val global: GlobalThing)
    private class OptionalThing

    private val global = koinApplication {
        modules(
            module {
                single { GlobalThing() }
                single(named("qualified")) { "global" }
            },
        )
    }.koin

    private fun account() = openAccount(global, listOf(module { single { AccountThing(get()) } })).koin

    @Test
    fun uctovaDefiniceBereGlobalniInstanci() {
        assertSame(global.get<GlobalThing>(), account().get<AccountThing>().global)
    }

    @Test
    fun globalniDefiniceJeVidetIsKvalifikatorem() {
        assertEquals("global", account().get<String>(named("qualified")))
    }

    @Test
    fun coNikdeNeniJeNull() {
        assertNull(account().getOrNull<OptionalThing>())
        assertFailsWith<NoDefinitionFoundException> { account().get<OptionalThing>() }
    }

    @Test
    fun globalniKoinNaUcetNevidi() {
        account().get<AccountThing>()
        assertNull(global.getOrNull<AccountThing>())
    }

    @Test
    fun kazdyUcetMaVlastniInstance() {
        assertNotSame(account().get<AccountThing>(), account().get<AccountThing>())
    }

    /** Definice účtu v globální části by ve dvou účtech ukazovala na totéž. */
    @Test
    fun definiceUctuNejsouVGlobalniCasti() {
        val shared = accountModule(AccountSlot.Primary).keys() intersect coreGlobalModule().keys()
        assertEquals(emptySet(), shared)
    }

    /** Pojistka, že porovnání výš neprošlo jen proto, že jsou seznamy prázdné. */
    @Test
    fun modulUctuNeniPrazdny() {
        assertTrue(accountModule(AccountSlot.Primary).keys().size > 40)
    }

    private fun Module.keys(): Set<String> =
        (mappings.keys + includedModules.flatMap { it.keys() }).toSet()
}
