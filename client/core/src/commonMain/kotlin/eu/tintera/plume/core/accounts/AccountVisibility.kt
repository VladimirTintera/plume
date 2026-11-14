package eu.tintera.plume.core.accounts

import kotlinx.coroutines.flow.Flow

/**
 * Ukazuje aplikace chaty účtu [AccountSlot] v seznamu chatů? V režimu Všechny účty všech účtů,
 * jinak jen výchozího (návrh `docs/navrh-vice-uctu.md`, „Výkon a paměť"). Podle toho je účet
 * v popředí online: jeho chaty uživatel vidí, takže je i pro protějšky. Účet mimo filtr online
 * není — protějšky by jinak viděly online účet, který uživatel zrovna nepoužívá.
 *
 * Filtr je v nastavení aplikace (`:client:shared`), kam `core` nevidí; implementaci dodává
 * `shared`. Bez ní (hodinky) je účet zobrazený vždycky, jako dřív.
 */
fun interface AccountVisibility {
    fun shown(slot: AccountSlot): Flow<Boolean>
}
