package eu.tintera.plume.app.root.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import eu.tintera.plume.core.settings.ThemeColor

/**
 * Schéma generované ze seed barvy, kterou si uživatel vybral v Nastavení → Vzhled.
 *
 * Tohle je náhrada Material You na platformách, kde systém tapetovou paletu nedává (iOS, Android
 * do 11), a volba pro toho, kdo barvy z tapety vypnul. Plnohodnotné M3 schéma se dopočítá
 * z jediné barvy, takže iOS build není „Android bez barev", ale má vlastní konzistentní paletu.
 */
@Composable
fun expressiveColorScheme(isDark: Boolean, seed: Color): ColorScheme = rememberDynamicColorScheme(
    seedColor = seed,
    isDark = isDark,
    style = BrandPaletteStyle,
)

/**
 * Styl generování palety.
 *
 * **Vibrant, ne Expressive.** Expressive (ve specifikaci 2021, výchozí v materialkoloru) posouvá
 * odstín primární barvy o 240° od seedu (`ColorSpec2021`), takže by vzorek „Červená" nebyl
 * červený. Vibrant drží odstín seedu u primární barvy a sytost dává
 * nejvyšší, jakou odstín unese — výrazný vzhled zůstane, jen barva odpovídá tomu, co si
 * uživatel vybral.
 */
private val BrandPaletteStyle = PaletteStyle.Vibrant

/**
 * Odstín, ze kterého se paleta dopočítá. Vzorky v Nastavení se kreslí z TÉHOŽ schématu jako
 * aplikace, takže na seedu záleží jen jako na výchozím bodu — co uživatel uvidí, je primární
 * barva palety.
 */
val ThemeColor.seed: Color
    get() = when (this) {
        ThemeColor.BLUE -> Color(0xFF2AABEE)
        ThemeColor.TEAL -> Color(0xFF00A3A3)
        ThemeColor.GREEN -> Color(0xFF43A047)
        ThemeColor.YELLOW -> Color(0xFFE8B400)
        ThemeColor.ORANGE -> Color(0xFFF57C00)
        ThemeColor.RED -> Color(0xFFE53935)
        ThemeColor.PINK -> Color(0xFFD81B60)
        ThemeColor.PURPLE -> Color(0xFF7E57C2)
    }
