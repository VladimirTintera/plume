package eu.tintera.plume.tv

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.ColorScheme
import androidx.tv.material3.darkColorScheme
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import androidx.compose.material3.ColorScheme as PhoneColorScheme

/**
 * Barvy Plume na televizi: tmavé schéma z téže barvy a téhož stylu palety jako telefon
 * (`ThemeColor.BLUE.seed`, `PaletteStyle.Vibrant` v `theme/ColorScheme.kt` ve `shared`).
 *
 * Dřív tu bylo holé `darkColorScheme()` z tv-material, tedy základní paleta Material 3:
 * fialová primární barva a plochy s fialovým nádechem. Televize pak jako jediná část Plume
 * neměla barvy aplikace. Volba barvy z Nastavení telefonu sem nejde: televize má vlastní
 * sezení a nastavení telefonu nevidí, proto výchozí modrá.
 *
 * Paletu dopočítá materialkolor jako schéma M3 telefonu ([PhoneColorScheme]) a role se
 * přepíšou do schématu tv-material jedna k jedné. To má užší sadu rolí: chybí stupně
 * `surfaceContainer*`, na které M3 klade dialogy a nabídky. Ty nese [TvSurfaces].
 */
internal object TvColors {
    /** Seed jako `ThemeColor.BLUE.seed`; `:client:shared` televize nevidí, proto kopie. */
    private val Seed = Color(0xFF2AABEE)

    private val phone: PhoneColorScheme = dynamicColorScheme(
        seedColor = Seed,
        isDark = true,
        style = PaletteStyle.Vibrant,
    )

    val scheme: ColorScheme = darkColorScheme(
        primary = phone.primary,
        onPrimary = phone.onPrimary,
        primaryContainer = phone.primaryContainer,
        onPrimaryContainer = phone.onPrimaryContainer,
        inversePrimary = phone.inversePrimary,
        secondary = phone.secondary,
        onSecondary = phone.onSecondary,
        secondaryContainer = phone.secondaryContainer,
        onSecondaryContainer = phone.onSecondaryContainer,
        tertiary = phone.tertiary,
        onTertiary = phone.onTertiary,
        tertiaryContainer = phone.tertiaryContainer,
        onTertiaryContainer = phone.onTertiaryContainer,
        background = phone.background,
        onBackground = phone.onBackground,
        surface = phone.surface,
        onSurface = phone.onSurface,
        surfaceVariant = phone.surfaceVariant,
        onSurfaceVariant = phone.onSurfaceVariant,
        surfaceTint = phone.surfaceTint,
        inverseSurface = phone.inverseSurface,
        inverseOnSurface = phone.inverseOnSurface,
        error = phone.error,
        onError = phone.onError,
        errorContainer = phone.errorContainer,
        onErrorContainer = phone.onErrorContainer,
        border = phone.outline,
        borderVariant = phone.outlineVariant,
        scrim = phone.scrim,
    )

    val surfaces = TvSurfaces(
        container = phone.surfaceContainer,
        containerHigh = phone.surfaceContainerHigh,
        containerHighest = phone.surfaceContainerHighest,
    )
}

/**
 * Stupně plochy M3, které tv-material ve schématu nemá. Dialog stojí na [containerHigh] jako
 * `AlertDialog` na telefonu.
 */
@Immutable
internal data class TvSurfaces(
    val container: Color,
    val containerHigh: Color,
    val containerHighest: Color,
)
