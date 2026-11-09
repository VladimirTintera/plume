package eu.tintera.plume.core.accounts

import org.koin.core.Koin
import org.koin.core.KoinApplication

/**
 * Přihlášené účty a jejich kontejnery (návrh `docs/navrh-vice-uctu.md`, „Architektura:
 * kontejner účtů").
 *
 * **Zatím jen primární účet.** Přidání a odhlášení dalšího účtu, výchozí účet a seznam jako tok
 * přijdou s fází 1; teď jde o to, aby repository účtu žily v jeho kontejneru, ne v globálním
 * Koinu.
 *
 * Kontejner účtu vzniká hned v konstruktoru, protože v něm vznikají i eager repository
 * (`createdAtStart`): odběry updatů musí běžet dřív, než TDLib pošle první dávku (CLAUDE.md,
 * „Repository seznamu chatů MUSÍ vznikat eagerně"). Proto je i `Accounts` v Koinu eager.
 *
 * @param open vyrobí izolovaný kontejner Koinu pro slot (`openAccount`), i s eager instancemi.
 */
class Accounts internal constructor(open: (AccountSlot) -> KoinApplication) {

    val primary: AccountSession = AccountSession(AccountSlot.Primary, open(AccountSlot.Primary))

    /** Přihlášené účty v pořadí uživatele; zatím jen [primary]. */
    val all: List<AccountSession> get() = listOf(primary)

    /** Účet podle slotu. Neznámý slot je chyba v kódu, ne stav: slot se dostane jen od účtu. */
    operator fun get(slot: AccountSlot): AccountSession =
        find(slot) ?: error("Neznámý slot účtu: ${slot.id}")

    /** Účet podle slotu ze vstupního bodu: notifikace nebo zástupce můžou přežít odhlášení účtu. */
    fun find(slot: AccountSlot): AccountSession? = all.firstOrNull { it.slot == slot }
}

/**
 * Jeden účet: jeho slot a izolovaný kontejner Koinu se vším, co mu patří (`accountModule`).
 *
 * Izolovaný kontejner ([KoinApplication] mimo globální `startKoin`), ne Koin scope: scope by
 * byl uvnitř globálního Koinu, takže by `get()` bez scope tiše vrátil globální instanci,
 * a `createdAtStart` ve scope nefunguje (návrh, „Proč ne Koin scopes").
 */
class AccountSession internal constructor(val slot: AccountSlot, val application: KoinApplication) {

    /** Kontejner účtu. Co v něm není, hledá v globálním Koinu (`openAccount`). */
    val koin: Koin get() = application.koin

    inline fun <reified T : Any> get(): T = koin.get()
}

/**
 * Kontejner primárního účtu pro vstupní bod bez kompozice a bez slotu (služba, přijímač, úloha).
 * Patří sem push a co má být přes všechny účty jednou (služby hovoru, hudby): řeší je fáze 1.
 */
fun Koin.primaryAccount(): Koin = get<Accounts>().primary.koin

/** Kontejner účtu [slot] pro vstupní bod bez kompozice; `null`, když už není přihlášený. */
fun Koin.account(slot: AccountSlot): Koin? = get<Accounts>().find(slot)?.koin
