package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Drží stav odhalených spoilerů (fotek a videí) v paměti aplikace po dobu jejího běhu.
 *
 * Shodně s oficiálním Telegramem se odhalení spoileru synchronizuje mezi konverzací
 * a prohlížečem médií. Jakmile uživatel fotku nebo video odhalí kdekoliv, zůstává
 * odhalené i na ostatních místech a po návratu do chatu.
 *
 * Po ukončení a novém spuštění aplikace se média se spoilerem opět zakryjí.
 */
class RevealedSpoilers {
    private val _revealed = MutableStateFlow<Set<Long>>(emptySet())
    val revealed: StateFlow<Set<Long>> = _revealed.asStateFlow()

    fun isRevealed(messageId: Long): Boolean = messageId in _revealed.value

    fun reveal(messageId: Long) {
        _revealed.update { it + messageId }
    }
}
