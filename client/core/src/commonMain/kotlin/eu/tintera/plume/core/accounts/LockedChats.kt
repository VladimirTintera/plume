package eu.tintera.plume.core.accounts

import kotlinx.coroutines.flow.Flow

/**
 * Chaty účtu [AccountSlot] zamčené v tomhle zařízení. Mimo vlastní seznam
 * (`ChatFolder.Locked`) se neukazují nikde: ve složkách, v hledání, v počtech nepřečtených,
 * v příbězích ani v notifikacích (ty jen „Nová zpráva" bez jména a textu).
 *
 * Seznam je v nastavení účtu (`:client:shared`), kam `core` nevidí; implementaci dodává
 * `shared`, jako u [AccountVisibility]. Bez ní (hodinky) je zamčeného nic.
 * Viz `docs/poznatky/zamcene-chaty.md`.
 */
fun interface LockedChats {
    fun locked(slot: AccountSlot): Flow<Set<Long>>
}
