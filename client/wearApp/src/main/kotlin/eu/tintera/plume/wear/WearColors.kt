package eu.tintera.plume.wear

import androidx.compose.ui.graphics.Color
import androidx.wear.protolayout.types.LayoutColor
import com.materialkolor.hct.Hct
import com.materialkolor.scheme.DynamicScheme
import com.materialkolor.scheme.SchemeVibrant
import androidx.wear.compose.material3.ColorScheme as ComposeColorScheme
import androidx.wear.protolayout.material3.ColorScheme as TileColorScheme

/**
 * Barvy Plume na hodinkách, když je nedá ciferník (Wear OS do 5, nebo hodinky bez barev
 * z ciferníku).
 *
 * Dřív tu byla výchozí paleta Wear M3 (`ColorScheme()` v aplikaci, výchozí `materialScope`
 * u dlaždic) — fialová, takže hodinky bez barev z ciferníku neměly barvy aplikace. Paletu
 * dopočítá `material-color-utilities` z téže barvy a stylu jako telefon (`ThemeColor.BLUE.seed`,
 * `PaletteStyle.Vibrant` v `theme/ColorScheme.kt` ve `shared`; hodinky `shared` nevidí, proto
 * kopie seedu jako u televize). Bez Compose material3 telefonu — ten by hodinky táhly jen kvůli
 * přepočtu barev.
 *
 * **Role se berou ze stejných tónů jako `dynamicColorScheme` Wear M3** (z bajtkódu
 * `DynamicColorSchemeKt` 1.7.1): primární barva je `primary_fixed`, tlumená `primary_fixed_dim`,
 * na ní `on_primary_fixed`, kontejnery a plochy z tmavého schématu. Vzhled je tak stejný, jako
 * kdyby modrou dal ciferník. Pozadí (černé, OLED) a chybové barvy zůstávají z výchozí palety.
 * `onBackground` se přepisuje taky (výchozí je fialová; `primaryLayout` ji dává textům v okrajích).
 *
 * **Text v hlavní ploše dlaždice potřebuje barvu výslovně** (`color = colorScheme.onSurface`):
 * bez ní ji ProtoLayout Material3 1.4 bere natvrdo z výchozí palety (`TextElementStyle` čte
 * statické `ColorTokens.PRIMARY`, ne schéma) — fialovou i s touhle paletou, i s barvami
 * z ciferníku. Ověřeno v bajtkódu a na snímku z emulátoru (`#E9DDFF` pod modrým počtem).
 */
internal object WearColors {
    /** Seed jako `ThemeColor.BLUE.seed`; `:client:shared` hodinky nevidí, proto kopie. */
    private const val SEED = 0xFF2AABEE.toInt()

    private val scheme: DynamicScheme = SchemeVibrant(Hct.fromInt(SEED), isDark = true, contrastLevel = 0.0)

    val compose: ComposeColorScheme = with(scheme) {
        ComposeColorScheme(
            primary = Color(primaryFixed),
            primaryDim = Color(primaryFixedDim),
            primaryContainer = Color(primaryContainer),
            onPrimary = Color(onPrimaryFixed),
            onPrimaryContainer = Color(onPrimaryContainer),
            secondary = Color(secondaryFixed),
            secondaryDim = Color(secondaryFixedDim),
            secondaryContainer = Color(secondaryContainer),
            onSecondary = Color(onSecondaryFixed),
            onSecondaryContainer = Color(onSecondaryContainer),
            tertiary = Color(tertiaryFixed),
            tertiaryDim = Color(tertiaryFixedDim),
            tertiaryContainer = Color(tertiaryContainer),
            onTertiary = Color(onTertiaryFixed),
            onTertiaryContainer = Color(onTertiaryContainer),
            surfaceContainerLow = Color(surfaceContainerLow),
            surfaceContainer = Color(surfaceContainer),
            surfaceContainerHigh = Color(surfaceContainerHigh),
            onSurface = Color(onSurface),
            onSurfaceVariant = Color(onSurfaceVariant),
            outline = Color(outline),
            outlineVariant = Color(outlineVariant),
            onBackground = Color(onBackground),
        )
    }

    /** Totéž pro dlaždice: `materialScope` ji použije, jen když barvy nedá ciferník. */
    val tile: TileColorScheme = with(scheme) {
        TileColorScheme(
            primary = LayoutColor(primaryFixed),
            primaryDim = LayoutColor(primaryFixedDim),
            primaryContainer = LayoutColor(primaryContainer),
            onPrimary = LayoutColor(onPrimaryFixed),
            onPrimaryContainer = LayoutColor(onPrimaryContainer),
            secondary = LayoutColor(secondaryFixed),
            secondaryDim = LayoutColor(secondaryFixedDim),
            secondaryContainer = LayoutColor(secondaryContainer),
            onSecondary = LayoutColor(onSecondaryFixed),
            onSecondaryContainer = LayoutColor(onSecondaryContainer),
            tertiary = LayoutColor(tertiaryFixed),
            tertiaryDim = LayoutColor(tertiaryFixedDim),
            tertiaryContainer = LayoutColor(tertiaryContainer),
            onTertiary = LayoutColor(onTertiaryFixed),
            onTertiaryContainer = LayoutColor(onTertiaryContainer),
            surfaceContainerLow = LayoutColor(surfaceContainerLow),
            surfaceContainer = LayoutColor(surfaceContainer),
            surfaceContainerHigh = LayoutColor(surfaceContainerHigh),
            onSurface = LayoutColor(onSurface),
            onSurfaceVariant = LayoutColor(onSurfaceVariant),
            outline = LayoutColor(outline),
            outlineVariant = LayoutColor(outlineVariant),
            onBackground = LayoutColor(onBackground),
        )
    }
}
