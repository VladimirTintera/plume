package eu.tintera.plume.features.settings

import androidx.lifecycle.ViewModel
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.ThemeMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Sekce „Vzhled".
 *
 * Hodnoty se berou rovnou z [AppSettings] — jsou to `StateFlow` načtené při startu, takže je
 * obrazovka má v prvním snímku a přepínač neproblikne z výchozí hodnoty na uloženou.
 *
 * Zápis NEBĚŽÍ ve `viewModelScope`: ten se při odchodu z obrazovky ruší a přepnutí těsně předtím
 * by se neuložilo. `AppSettings` proto ukládá ve svém vlastním (aplikačním) rozsahu a setter
 * nic nevrací.
 */
class AppearanceViewModel(
    private val settings: AppSettings,
) : ViewModel() {

    /** `null` znamená „uživatel si nevybral" — výchozí motiv dopočítá až obrazovka podle platformy. */
    val themeMode: StateFlow<ThemeMode?> = settings.themeMode

    val separateUnreadChats: StateFlow<Boolean> = settings.separateUnreadChats

    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)

    fun setSeparateUnreadChats(separate: Boolean) = settings.setSeparateUnreadChats(separate)
}
