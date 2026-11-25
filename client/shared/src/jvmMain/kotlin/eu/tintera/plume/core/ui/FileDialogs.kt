package eu.tintera.plume.core.ui

import java.awt.EventQueue
import java.awt.FileDialog

/**
 * Otevře modální [FileDialog] až z vlastní události AWT a po zavření zavolá [onClosed]
 * (na vlákně AWT, tedy na `Dispatchers.Main`).
 *
 * **Přímo z kódu Compose se modální dialog otevírat NESMÍ.** `isVisible = true` blokuje a uvnitř
 * spustí vnořenou smyčku událostí, která dál kreslí snímky. Běží-li volání v korutině, kterou
 * zrovna vykonává `FlushCoroutineDispatcher` Compose (`scope.launch` po suspendu, klik), vyprázdní
 * vykreslený snímek tentýž dispatcher znovu, uprostřed jeho vlastního běhu — a korutiny se
 * rozsypou: `CoroutinesInternalError` s `ClassCastException` (`CompletedContinuation` →
 * `DispatchedContinuation`, `Symbol` → typ výsledku), pak `ConcurrentModificationException`
 * ve `FlushCoroutineDispatcher`. Tak padalo „Uložit jako…" v prohlížeči médií.
 * `invokeLater` dialog odloží za konec probíhajícího vyprázdnění.
 */
internal fun <T : FileDialog> T.showLater(onClosed: (T) -> Unit) {
    EventQueue.invokeLater {
        isVisible = true
        onClosed(this)
    }
}
