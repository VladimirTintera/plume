package eu.tintera.plume.core.adaptive

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

/**
 * Extra položky, které jsou navrchu zásobníku (podstránka Nastavení) — tedy to, co je ve
 * třetím panelu vidět. Položka přes celou plochu (prohlížeč nad profilem) se přeskočí: panel
 * pod ní pořád je.
 */
fun <K> List<K>.shownExtras(paneOf: (K) -> PaneInfo?): List<K> {
    val shown = ArrayList<K>()
    for (index in indices.reversed()) {
        val info = paneOf(this[index]) ?: continue
        if (info.role != PaneRole.Extra) break
        shown += this[index]
    }
    return shown.asReversed()
}

/**
 * Zásobník pro scénu, ve kterém třetí panel, který se právě zavřel, ještě chvíli zůstane —
 * aby odjel animací, ne zmizel.
 *
 * `ThreePaneScaffoldScene` kreslí do třetího panelu POSLEDNÍ extra položku scény. Jakmile ze
 * zásobníku zmizí, dostane scaffold místo obsahu `null` a panel zmizí v jednom snímku (problik),
 * i když se rozvržení samo animuje — odjezd nemá co animovat. Odcházející položky se proto ještě
 * [PANE_EXIT_MILLIS] drží ve scéně, a to NA ZAČÁTKU zásobníku: scaffold rozhoduje o panelech
 * podle pořadí v historii odzadu, takže položka vepředu se do volných oddílů nedostane, je
 * `Hidden` a `AnimatedPane` ji nechá odjet. Kdyby zůstala navrchu, panel by se neskryl vůbec.
 *
 * Jen pro kreslení: skutečný zásobník (zpět, ukládání) ji už nemá. Znovu otevřený panel
 * odcházející položky hned zahodí.
 *
 * Jen Nastavení: Chaty kreslí scéna Chatů (`ChatScene`), která odjezd bočního sloupce drží sama.
 */
@Composable
fun <K : Any> rememberWithLeavingExtras(stack: List<K>, paneOf: (K) -> PaneInfo?): List<K> {
    val memory = remember { LeavingMemory<K>() }
    val shown = stack.shownExtras(paneOf)
    // V tomhle snímku zmizely: panel byl otevřený a teď není žádný.
    val justLeft = if (shown.isEmpty()) memory.shown.filter { it !in stack } else emptyList()
    SideEffect {
        memory.shown = shown
        when {
            shown.isNotEmpty() -> memory.leaving.value = emptyList()
            justLeft.isNotEmpty() -> memory.leaving.value = justLeft
        }
    }
    val leaving = when {
        shown.isNotEmpty() -> emptyList()
        justLeft.isNotEmpty() -> justLeft
        else -> memory.leaving.value.filter { it !in stack }
    }
    LaunchedEffect(memory.leaving.value) {
        if (memory.leaving.value.isEmpty()) return@LaunchedEffect
        delay(PANE_EXIT_MILLIS)
        memory.leaving.value = emptyList()
    }
    return if (leaving.isEmpty()) stack else leaving + stack
}

/** Co bylo ve třetím panelu v minulém snímku a co z něj právě odjíždí. */
private class LeavingMemory<K> {
    var shown: List<K> = emptyList()
    val leaving: MutableState<List<K>> = mutableStateOf(emptyList())
}

/**
 * Jak dlouho odjíždějící panel zůstane ve scéně: pohyb panelů v knihovně je pružina
 * (`PaneMotionDefaults`, tuhost 380, tlumení 0,8), která se usadí zhruba za půl vteřiny.
 * Déle neškodí — skrytý panel se nekreslí.
 */
private const val PANE_EXIT_MILLIS = 600L
