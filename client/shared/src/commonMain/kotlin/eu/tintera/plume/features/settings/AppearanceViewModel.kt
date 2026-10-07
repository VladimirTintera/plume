package eu.tintera.plume.features.settings

import androidx.lifecycle.ViewModel
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.ThemeColor
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

    /** `null` znamená „uživatel si nevybral" — výchozí je `supportsDynamicColor`. */
    val dynamicColor: StateFlow<Boolean?> = settings.dynamicColor

    /** `null` znamená „uživatel si nevybral" — výchozí je značková [ThemeColor.BLUE]. */
    val themeColor: StateFlow<ThemeColor?> = settings.themeColor

    val separateUnreadChats: StateFlow<Boolean> = settings.separateUnreadChats

    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)

    fun setDynamicColor(enabled: Boolean) = settings.setDynamicColor(enabled)

    /**
     * Vybraný vzorek platí, jen když neplatí barvy z tapety — proto je výběr barvy zároveň
     * vypíná. Kdo ťukne na červenou, chce červenou aplikaci; nechat zapnutou tapetu by znamenalo
     * ťuknutí, po kterém se nic nestane.
     */
    fun chooseThemeColor(color: ThemeColor, dynamicActive: Boolean) {
        settings.setThemeColor(color)
        if (dynamicActive) settings.setDynamicColor(false)
    }

    fun setSeparateUnreadChats(separate: Boolean) = settings.setSeparateUnreadChats(separate)
}
