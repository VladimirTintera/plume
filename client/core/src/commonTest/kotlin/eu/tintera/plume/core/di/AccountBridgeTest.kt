package eu.tintera.plume.core.di

import eu.tintera.plume.core.accounts.AccountSlot
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Most z globálního Koinu do kontejneru primárního účtu (`accountBridgeModule`) musí nabízet
 * přesně to, co kontejner účtu definuje. Chybějící řádek by se ukázal až za běhu jako „No
 * definition found" u obrazovky, která repository chce, a přebývající by spadl při prvním použití.
 *
 * Moduly se jen skládají, nic se z nich nevyrábí: klient TDLib by v testu na JVM nevznikl.
 */
@OptIn(KoinInternalApi::class)
class AccountBridgeTest {

    private val global = koinApplication { }.koin

    @Test
    fun mostZrcadliVsechnyDefiniceUctu() {
        val account = accountModule(AccountSlot.Primary, global).keys() - globalBridgeModule(global).keys()
        val bridge = accountBridgeModule().keys()

        assertEquals(account.sorted(), bridge.sorted())
    }

    /** Pojistka, že porovnání výš neprošlo jen proto, že oba seznamy vyšly prázdné. */
    @Test
    fun modulUctuNeniPrazdny() {
        assertTrue(accountBridgeModule().keys().size > 40)
    }

    private fun Module.keys(): Set<String> =
        (mappings.keys + includedModules.flatMap { it.keys() }).toSet()
}
