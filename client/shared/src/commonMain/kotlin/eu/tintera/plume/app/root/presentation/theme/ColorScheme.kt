package eu.tintera.plume.app.root.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme

/**
 * Vibrant expressive schéma generované ze seed barvy.
 *
 * Tohle je náhrada Material You na platformách, kde systém tapetovou paletu nedává (iOS). Na Androidu
 * 12+ vyhrává systémové dynamic color; tady jen dopočítáme plnohodnotné M3 schéma ze značkové barvy,
 * takže iOS build není „Android bez barev", ale má vlastní konzistentní paletu.
 *
 * Až bude výběr seed barvy v Nastavení (nebo extrakce z avataru chatu), mění se jen [BrandSeed] —
 * zbytek schématu se dopočítá.
 */
val BrandSeed = Color(0xFF2AABEE)

/** Styl generování palety (Expressive/Vibrant/Rainbow/FruitSalad…). */
val BrandPaletteStyle = PaletteStyle.Expressive

@Composable
fun expressiveColorScheme(isDark: Boolean) = rememberDynamicColorScheme(
    seedColor = BrandSeed,
    isDark = isDark,
    style = BrandPaletteStyle,
)
