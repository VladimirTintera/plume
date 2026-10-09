package eu.tintera.plume.features.settings

import androidx.lifecycle.ViewModel
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.QuickReaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Rychlá reakce (Obecné → Chaty): jestli ji dvojité ťuknutí přidává (aplikace) a kterou (účet).
 *
 * Výběr reakce běží v rozsahu aplikace, ne ve `viewModelScope` — stejná úvaha jako u zápisu
 * nastavení (viz [AppearanceViewModel]). Při selhání se reakce v [QuickReaction] sama vrátí.
 */
class QuickReactionSettingsViewModel(
    private val settings: AppSettings,
    private val quickReaction: QuickReaction,
    private val appScope: CoroutineScope,
) : ViewModel() {

    val enabled: StateFlow<Boolean> = settings.doubleTapReaction

    val reaction: StateFlow<String?> = quickReaction.reaction

    val choices: StateFlow<List<String>> = quickReaction.choices

    fun setEnabled(on: Boolean) = settings.setDoubleTapReaction(on)

    fun choose(reaction: String) {
        appScope.launch { quickReaction.set(reaction) }
    }
}
