package eu.tintera.plume.features.conversation

import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import eu.tintera.plume.core.ui.LocalBackgroundBlurAllowed

/**
 * Mléčné sklo nad seznamem zpráv: pás pod vstupním polem, pruh připnutých zpráv a plovoucí datum.
 *
 * Všechny tři plochy leží NAD projíždějícím obsahem a dosud ho jen zakrývaly barvou. Rozostření
 * dělá totéž čitelněji: je vidět, že pod nimi obsah pokračuje, a přitom nepřebíjí to, co na nich
 * stojí. Tentýž Haze jako závoj pod nabídkou nad zprávou (`docs/poznatky/konverzace-akce.md`, „Nabídka nad zprávou“).
 *
 * **Zdrojem je jen seznam zpráv, ne celý Scaffold.** Závoj nabídky rozostřuje Scaffold celý —
 * je nad ním. Tyhle plochy jsou ale jeho součástí, takže by se s ním rozostřovaly samy: pruh
 * připnutých by nasál plovoucí datum a pás pod polem sám sebe. Proto má seznam vlastní
 * `HazeState`.
 *
 * **Barvu nese tint stylu, ne plocha.** Kdyby si ji plocha kreslila sama, překryla by rozostřený
 * obraz pod sebou; průhledná plocha s tintem dělá sklo.
 */
@Composable
internal fun glassStyle(
    color: Color,
    alpha: Float,
    fallbackAlpha: Float,
    /** Úprava rozostřeného obrazu PŘED tintem (viz [textGlassStyle]); `null` = beze změny. */
    backdrop: ColorFilter? = null,
): HazeBlurStyle {
    val page = MaterialTheme.colorScheme.background
    return LocalBackgroundBlurAllowed.current.let { blur ->
        remember(color, alpha, fallbackAlpha, blur, backdrop, page) {
            HazeBlurStyle {
                backgroundColor(page)
                // Systém rozostření nechce („Omezit efekty rozmazání") — kreslí se náhradní plocha.
                // Jen vypnout: zapnuté rozostření rozhoduje Haze sám (pod Androidem 12 ho nemá).
                if (!blur) blurEnabled(false)
                colorEffects(
                    listOfNotNull(
                        backdrop?.let { HazeColorEffect.colorFilter(it) },
                        HazeColorEffect.tint(color.copy(alpha = alpha)),
                    ),
                )
                blurRadius(GLASS_BLUR_RADIUS)
                noiseFactor(GLASS_NOISE)
                // Kde rozostření není (Android 11 a starší), zůstane dřívější krycí plocha.
                fallbackColorEffect(HazeColorEffect.tint(color.copy(alpha = fallbackAlpha)))
            }
        }
    }
}

/**
 * Sklo pod plochou, která nese VLASTNÍ TEXT: pruhy nad konverzací, plovoucí datum, dock přehrávače.
 *
 * **Kontrast textu hlídá matice, ne krytí.** Dřív to dělalo krytí 0,88 a z pozadí tak
 * prosvítalo 12 % — rozostření nebylo poznat. Teď se rozostřený obraz napřed stáhne k barvě
 * plochy ([TEXT_GLASS_BACKDROP_CONTRAST]) a teprve pak se přes něj položí tint s nižším krytím.
 * Jas pozadí se do plochy propíše STEJNĚ jako dřív (`(1 − krytí) × stažení` = 0,12), takže
 * naměřené kontrasty platí dál, ale barvy pod sklem se zesílí ([GLASS_SATURATION]) — fotka,
 * samolepka nebo odkaz pod pruhem prosvítají jako barevná skvrna, ne jako šedá.
 */
@Composable
internal fun textGlassStyle(color: Color): HazeBlurStyle {
    val backdrop = remember(color) {
        ColorFilter.colorMatrix(backdropMatrix(color, TEXT_GLASS_BACKDROP_CONTRAST, GLASS_SATURATION))
    }
    return glassStyle(color, TEXT_GLASS_TINT_ALPHA, GLASS_FALLBACK_ALPHA, backdrop)
}

/**
 * Matice, která rozostřený obraz pod sklem zesytí ([saturation]) a jeho rozsah stáhne
 * k barvě [toward] na podíl [contrast]: `out = contrast × sytost(in) + (1 − contrast) × toward`.
 * Sytost drží jas (váhy podle Rec. 709), takže jas mění jen stažení.
 */
internal fun backdropMatrix(toward: Color, contrast: Float, saturation: Float): ColorMatrix {
    val s = saturation
    val c = contrast
    val r = 0.2126f * (1 - s)
    val g = 0.7152f * (1 - s)
    val b = 0.0722f * (1 - s)
    // Posun je v ColorMatrix v rozsahu 0–255 (jako na Androidu).
    val k = (1 - c) * 255f
    return ColorMatrix(
        floatArrayOf(
            c * (r + s), c * g, c * b, 0f, k * toward.red,
            c * r, c * (g + s), c * b, 0f, k * toward.green,
            c * r, c * g, c * (b + s), 0f, k * toward.blue,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}

/**
 * Světlá hrana skla: tenký okraj, nahoře světlejší a dolů mizí. Pod tmavou plochou v tmavém
 * motivu projíždí tmavý obsah a rozostření samo nemá co ukázat; hrana říká „sklo“ i tehdy.
 * Patří za `clip(tvar).then(glass)` uvnitř plochy, se stejným tvarem.
 */
@Composable
internal fun Modifier.glassRim(shape: Shape): Modifier {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    // V tmavém motivu světlá barva popředí, ve světlém nejsvětlejší plocha — obojí „odlesk“.
    val light = if (dark) scheme.onSurface else scheme.surfaceContainerLowest
    val brush = remember(light, dark) {
        Brush.verticalGradient(
            0f to light.copy(alpha = if (dark) GLASS_RIM_ALPHA_DARK else GLASS_RIM_ALPHA_LIGHT),
            0.5f to light.copy(alpha = 0f),
            1f to light.copy(alpha = (if (dark) GLASS_RIM_ALPHA_DARK else GLASS_RIM_ALPHA_LIGHT) / 3),
        )
    }
    return border(GLASS_RIM_WIDTH, brush, shape)
}

/**
 * Větší poloměr než u závoje nabídky (12 dp).
 *
 * Tam jde o odsunutí celé konverzace do pozadí, takže silné rozostření z ní udělá kaši a nepozná
 * se ani hlavička. Tady prosvítá pruh vysoký pár desítek dp a jde o dojem skla — při malém
 * poloměru je pod ním text ještě čitelný a plocha vypadá jen zašpiněně.
 */
private val GLASS_BLUR_RADIUS = 20.dp

/**
 * Jak moc plocha kryje.
 *
 * **Nižší než dřívější krycí plocha, a v tom je celá pointa.** Pás pod polem měl krytí 0,93
 * a rozostření pod ním nebylo vidět vůbec — text zprávy končil na jeho hraně, jako by ho něco
 * ořízlo. Oddělování teď nese rozostření, takže plocha smí prosvítat; víc už ne, text na ní musí
 * zůstat čitelný nad čímkoli, co pod ní projede.
 */
internal const val GLASS_TINT_ALPHA = 0.72f

/** Krytí tintu u skla s textem ([textGlassStyle]); kontrast hlídá spolu s [TEXT_GLASS_BACKDROP_CONTRAST]. */
internal const val TEXT_GLASS_TINT_ALPHA = 0.6f

/**
 * Na jaký podíl se rozsah jasu pod sklem s textem stáhne. S krytím [TEXT_GLASS_TINT_ALPHA]
 * dává `(1 − 0,6) × 0,3 = 0,12` — tolik, kolik dřív propouštělo krytí 0,88, při kterém se měřil
 * kontrast: „Včera“ nad černým náhledem videa (při 0,72 jen 2,85) a v tmavém režimu nad světlou
 * fotkou 5,89. Kdo jedno z čísel mění, musí kontrast změřit znovu.
 */
internal const val TEXT_GLASS_BACKDROP_CONTRAST = 0.3f

/** Kolikrát se zesílí barvy pod sklem s textem — jas se tím nemění. */
internal const val GLASS_SATURATION = 2.4f

/** Zrnitost matného skla: i nad jednolitým pozadím je plocha poznat jako materiál, ne barva. */
private const val GLASS_NOISE = 0.08f

private val GLASS_RIM_WIDTH = 1.dp
private const val GLASS_RIM_ALPHA_DARK = 0.18f
private const val GLASS_RIM_ALPHA_LIGHT = 0.9f

/**
 * Krytí skla plovoucího napovídání nad polem (`FloatingSuggestion`), barva `inverseSurface`.
 *
 * Víc než u ostatních skel, protože inverzní plocha nese text opačné světlosti než to, co pod ní
 * projede: ve světlém motivu světlé písmo nad bílou fotkou, v tmavém tmavé nad černou. S 0,8 vyjde
 * v obou nejhůř kolem 6,5 : 1; s 0,66 (dřívější tmavé sklo z `scrim`) by `inverseSurface`, která
 * je světlejší než černá, nad bílou fotkou spadla pod 4 : 1.
 */
internal const val FLOATING_SUGGESTION_TINT_ALPHA = 0.8f

/** Bez rozostření (Android 11 a starší) plocha kryje úplně — jinak by pod ní text prosvítal. */
internal const val GLASS_FALLBACK_ALPHA = 1f
