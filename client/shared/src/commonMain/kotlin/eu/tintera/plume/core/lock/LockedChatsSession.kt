package eu.tintera.plume.core.lock

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Odemčení zamčených chatů (`AccountSettings.lockedChats`). Ověřuje se PINem a biometrií ZÁMKU
 * APLIKACE ([AppLock]) — jeden PIN, jedna brzda pokusů, totéž svázání biometrie se seznamem
 * otisků. Zamknout chat proto jde jen se zapnutým zámkem aplikace.
 *
 * - **Odemčené jsou všechny zamčené chaty všech účtů najednou** a jen do odchodu aplikace do
 *   pozadí (nebo do zamčení celé aplikace). Delší doba by z odemčení udělala trvalý stav:
 *   telefon půjčený „na chvilku" by zamčené chaty ukazoval.
 * - **Zamčené chaty se z ostatních seznamů neukazují ani odemčené** — to filtruje `core`
 *   (`LockedChats`). Odemčení jen pustí do seznamu zamčených a do jejich konverzací.
 *
 * Viz `docs/poznatky/zamcene-chaty.md`.
 */
class LockedChatsSession(
    foreground: Flow<Boolean>,
    lock: Flow<AppLockState>,
    scope: CoroutineScope,
) {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    init {
        combine(foreground, lock) { inForeground, state -> inForeground && state !is AppLockState.Locked && state !is AppLockState.Disabled }
            .onEach { allowed -> if (!allowed) _unlocked.value = false }
            .launchIn(scope)
    }

    /** Ověření prošlo (PIN nebo biometrie zámku aplikace). */
    fun unlock() {
        _unlocked.value = true
    }

    /** Zamknout hned, třeba tlačítkem v seznamu zamčených. */
    fun lock() {
        _unlocked.value = false
    }
}
