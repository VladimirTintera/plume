package eu.tintera.plume.core.platform

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Most mezi oknem aplikace (`:client:desktopApp`) a platformní vrstvou ve sdíleném modulu.
 * Okno vzniká až ve vstupním bodu, kdežto notifikace a jejich proklik žijí tady.
 */
object DesktopWindow {

    private val _focused = MutableStateFlow(false)

    /** Okno aplikace má fokus — uživatel se dívá do Plume. Zapisuje vstupní bod. */
    val focused: StateFlow<Boolean> = _focused.asStateFlow()

    fun setFocused(focused: Boolean) {
        _focused.value = focused
    }

    private val _shown = MutableStateFlow(true)

    /**
     * Okno je na obrazovce — ne schované (Dock, oznamovací oblast) ani minimalizované. Podle toho
     * je aplikace v popředí ([eu.tintera.plume.core.connectivity.AppForeground]). Zapisuje
     * vstupní bod.
     */
    val shown: StateFlow<Boolean> = _shown.asStateFlow()

    fun setShown(shown: Boolean) {
        _shown.value = shown
    }

    private val _activations = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Žádosti o vytažení okna dopředu (klik na notifikaci). Vyřizuje vstupní bod. */
    val activations: SharedFlow<Unit> = _activations.asSharedFlow()

    fun activate() {
        _activations.tryEmit(Unit)
    }
}
