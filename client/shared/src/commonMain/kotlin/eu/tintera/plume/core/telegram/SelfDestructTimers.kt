package eu.tintera.plume.core.telegram

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

/**
 * Spravuje stav běžících odpočtů a jednorázově zhlédnutých (view-once) médií v paměti aplikace.
 *
 * TDLib při zavolání `openMessageContent` spustí interní odpočet, ale neposílá update na každou
 * vteřinu. Tato komponenta drží čas expirace (`expiresAtEpochSeconds`), zajišťuje plynulý odpočet
 * pro UI a garantuje, že pokud uživatel z fullscreenu odejde a vrátí se, odpočet pokračuje
 * ze zbývajícího času a neresetuje se.
 *
 * Po vypršení času nebo po opuštění jednorázového zhlédnutí je zpráva označena za spotřebovanou.
 */
class SelfDestructTimers(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {

    /** Mapování messageId na epoch sekundu expirace. */
    private val _timerExpirations = MutableStateFlow<Map<Long, Long>>(emptyMap())
    val timerExpirations: StateFlow<Map<Long, Long>> = _timerExpirations.asStateFlow()

    /** Zprávy s jednorázovým zhlédnutím nebo vypršeným časovačem, které již nelze zobrazit. */
    private val _consumedIds = MutableStateFlow<Set<Long>>(emptySet())
    val consumedIds: StateFlow<Set<Long>> = _consumedIds.asStateFlow()

    /**
     * Spustí odpočet pro danou zprávu, nebo ho zkrátí, když [durationSeconds] končí dřív než
     * běžící. Vrátí čas expirace v epoch sekundách.
     *
     * **Prodloužit odpočet nejde.** [durationSeconds] je zbývající čas od TDLib, a ten může být
     * kratší než lokální odhad (odpočet začal na jiném zařízení); delší nikdy — obsah by pak byl
     * vidět déle, než odesílatel dovolil.
     */
    fun startTimer(messageId: Long, durationSeconds: Int): Long {
        val now = Clock.System.now().epochSeconds
        val expiresAt = now + durationSeconds
        val existing = _timerExpirations.value[messageId]
        if (existing != null && existing <= expiresAt) return existing

        _timerExpirations.update { it + (messageId to expiresAt) }

        scope.launch {
            delay((durationSeconds * 1000L).milliseconds)
            markConsumed(messageId)
        }

        return expiresAt
    }

    /**
     * Zbývající sekundy do vypršení, nebo `null`, pokud časovač neběží.
     */
    fun remainingSeconds(messageId: Long): Int? {
        val expiresAt = _timerExpirations.value[messageId] ?: return null
        val now = Clock.System.now().epochSeconds
        return maxOf(0, (expiresAt - now).toInt())
    }

    /**
     * Označí zprávu (view-once nebo po vypršení časovače) za spotřebovanou.
     */
    fun markConsumed(messageId: Long) {
        _consumedIds.update { it + messageId }
    }

    /**
     * Vrátí `true`, pokud zpráva již vypršela nebo byla spotřebována.
     */
    fun isConsumed(messageId: Long): Boolean {
        if (messageId in _consumedIds.value) return true
        val expiresAt = _timerExpirations.value[messageId]
        if (expiresAt != null && Clock.System.now().epochSeconds >= expiresAt) {
            markConsumed(messageId)
            return true
        }
        return false
    }

    /**
     * Flow zbývajících sekund pro aktivní zobrazení ve fullscreenu, tikající každých ~250ms.
     * Emituje hodnotu od zbývajícího času dolů k 0.
     */
    fun observeRemainingSeconds(messageId: Long, initialSeconds: Int): Flow<Int> = flow {
        val expiresAt = startTimer(messageId, initialSeconds)
        while (true) {
            val now = Clock.System.now().epochSeconds
            val remaining = maxOf(0, (expiresAt - now).toInt())
            emit(remaining)
            if (remaining <= 0) {
                markConsumed(messageId)
                break
            }
            delay(250.milliseconds)
        }
    }
}
