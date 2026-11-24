package eu.tintera.plume.features.conversation

import androidx.compose.foundation.border
import androidx.compose.material3.ColorScheme
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
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import eu.tintera.plume.core.ui.LocalBackgroundBlurAllowed

/**
 * Mléčné sklo nad seznamem zpráv. JEDEN materiál ve dvou podobách:
 *
 * - **pilulka** ([pillGlass], [pillGlassStyle]; bílá ve světlém motivu): plovoucí plochy s textem — pruhy nad konverzací
 *   (připnuté, spam, videochat…), plovoucí datum, docky polohy a přehrávače, napovídání nad polem;
 * - **pás** ([bandGlassStyle]): pod vstupním polem a lištami místo něj; vlastní text nenese;
 * - **hlavička** ([headerGlass]): pás v barvě stránky, ale s únikem pilulky, protože nese text.
 *
 * Liší se jen barvou a tím, kolik jasu z pozadí propustí ([PILL_GLASS_LEAK], [BAND_GLASS_LEAK]);
 * rozostření, zesílení barev, zrnitost a krycí podklad mají společné. Pilulky navíc nesou
 * světlou hranu ([glassRim]) a stín 3 dp.
 *
 * Plochy leží NAD projíždějícím obsahem a dřív ho jen zakrývaly barvou. Rozostření dělá totéž
 * čitelněji: je vidět, že pod nimi obsah pokračuje, a přitom nepřebíjí to, co na nich stojí.
 *
 * **Zdrojem je jen seznam zpráv, ne celý Scaffold.** Závoj nabídky rozostřuje Scaffold celý —
 * je nad ním. Tyhle plochy jsou ale jeho součástí, takže by se s ním rozostřovaly samy: pruh
 * připnutých by nasál plovoucí datum a pás pod polem sám sebe. Proto má seznam vlastní
 * `HazeState`.
 *
 * **Barvu nese tint stylu, ne plocha.** Kdyby si ji plocha kreslila sama, překryla by rozostřený
 * obraz pod sebou; průhledná plocha s tintem dělá sklo.
 *
 * **Jas pozadí hlídá matice, ne krytí.** Rozostřený obraz se napřed stáhne k barvě plochy
 * ([backdropMatrix]) a teprve pak se přes něj položí tint s krytím [GLASS_TINT_ALPHA]; barvy pod
 * sklem se přitom zesílí ([GLASS_SATURATION]). Fotka, samolepka nebo odkaz tak prosvítají jako
 * barevná skvrna, kdežto jas — a tím kontrast textu — se propíše jen v míře „úniku“.
 */
@Composable
private fun glassStyle(color: Color, leak: Float, fallbackAlpha: Float): HazeBlurStyle {
    /*
     * Podklad pod rozostřeným obrazem: bez něj byl obraz u okrajů plochy průhledný (na šíři
     * poloměru rozostření) a pod sklem prosvítal OSTRÝ seznam, jen ztlumený tintem.
     */
    val page = MaterialTheme.colorScheme.background
    val blur = LocalBackgroundBlurAllowed.current
    return remember(color, leak, fallbackAlpha, blur, page) {
        val backdrop = ColorFilter.colorMatrix(backdropMatrix(color, glassContrast(leak), GLASS_SATURATION))
        HazeBlurStyle {
            backgroundColor(page)
            // Systém rozostření nechce („Omezit efekty rozmazání") — kreslí se náhradní plocha.
            // Jen vypnout: zapnuté rozostření rozhoduje Haze sám (pod Androidem 12 ho nemá).
            if (!blur) blurEnabled(false)
            colorEffects(
                listOf(
                    HazeColorEffect.colorFilter(backdrop),
                    HazeColorEffect.tint(color.copy(alpha = GLASS_TINT_ALPHA)),
                ),
            )
            blurRadius(GLASS_BLUR_RADIUS)
            noiseFactor(GLASS_NOISE)
            // Kde rozostření není (Android 11 a starší), zůstane krycí plocha.
            fallbackColorEffect(HazeColorEffect.tint(color.copy(alpha = fallbackAlpha)))
        }
    }
}

/**
 * Styl skla pilulky s textem (viz [glassStyle]).
 *
 * **Plocha NENÍ v roli příchozí bubliny.** Ta je `surfaceContainerHigh`, a pilulka v téže roli
 * se přes ni slévala — ve světlém motivu to bylo vidět nejvíc (oddělil ji jen stín a hrana).
 * Ve světlém motivu je proto bílá (`surfaceContainerLowest`): matné bílé sklo vystoupí z šedých
 * bublin i ze stránky a tmavému textu přidá kontrast. V tmavém zůstává `surfaceContainerHigh`;
 * světlejší role by ubrala kontrast světlému textu, který v nejhorším případě drží těsně.
 */
@Composable
internal fun pillGlassStyle(): HazeBlurStyle {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.isDarkSurface()
    val color = if (dark) scheme.surfaceContainerHigh else scheme.surfaceContainerLowest
    return glassStyle(color, if (dark) PILL_GLASS_LEAK else PILL_GLASS_LEAK_LIGHT, fallbackAlpha = 1f)
}

/** Tmavý motiv podle plochy — `ColorScheme` sám příznak nenese. */
private fun ColorScheme.isDarkSurface(): Boolean = surface.luminance() < 0.5f

/** Sklo pilulky nad seznamem [source] — modifikátor pro `glass` plovoucích ploch. */
@Composable
internal fun pillGlass(source: HazeState): Modifier =
    Modifier.hazeBlur(input = HazeInput.Sources(source), style = pillGlassStyle())

/**
 * Styl skla hlavičky konverzace (viz [glassStyle]), jen když leží v kartě — na telefonu a na
 * jednom panelu, kde je konverzace celá obrazovka a obsah pod hlavičku zajíždí jako pod pole.
 *
 * **Barva je plocha stránky (`surface`), ne bílá pilulka:** v klidu (nic pod hlavičkou) má
 * vypadat jako dřív a lišta přes celou šířku je pás, ne plovoucí plocha. Únik je ale pilulkový
 * ([PILL_GLASS_LEAK], [PILL_GLASS_LEAK_LIGHT]), ne pásový: hlavička nese jméno a podtitul.
 * Kde rozostření není, kryje úplně — jako hlavička bez skla.
 */
@Composable
internal fun headerGlassStyle(): HazeBlurStyle {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.isDarkSurface()
    return glassStyle(scheme.surface, if (dark) PILL_GLASS_LEAK else PILL_GLASS_LEAK_LIGHT, fallbackAlpha = 1f)
}

/** Sklo hlavičky nad seznamem [source] — pro `PaneHeader(glass = …)`. */
@Composable
internal fun headerGlass(source: HazeState): Modifier =
    Modifier.hazeBlur(input = HazeInput.Sources(source), style = headerGlassStyle())

/** Styl pásu pod vstupním polem (viz [glassStyle]); plochu kreslí `surface` jako stránka. */
@Composable
internal fun bandGlassStyle(): HazeBlurStyle =
    glassStyle(MaterialTheme.colorScheme.surface, BAND_GLASS_LEAK, BAND_FALLBACK_ALPHA)

/** Na jaký podíl se rozsah jasu pod sklem stáhne, aby po tintu propustil [leak]. */
internal fun glassContrast(leak: Float): Float = leak / (1 - GLASS_TINT_ALPHA)

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
    val dark = scheme.isDarkSurface()
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

/** Krytí tintu skla; jas pozadí řídí zvlášť „únik“ ([PILL_GLASS_LEAK], [BAND_GLASS_LEAK]). */
internal const val GLASS_TINT_ALPHA = 0.6f

/**
 * Kolik jasu z pozadí propustí pilulka s textem v TMAVÉM motivu ([PILL_GLASS_LEAK_LIGHT] ve
 * světlém). Dřív 0,12 (jako krytí 0,88) a obsah pod sklem prosvítal jen velmi decentně.
 *
 * **Kontrast hlídá `GlassTest` nad všemi osmi paletami** v nejhorším případě — pod
 * celým textem úplně bílá plocha (tmavý motiv), úplně černá (světlý). S 0,18 v tmavém a 0,25
 * ve světlém drží `onSurface` i `onSurfaceVariant` AA (≥ 4,5); primární popisek („Připnutá
 * zpráva“) v tom nejhorším případě klesne na 3,7 / 3,5 — vědomě, nad skutečným obsahem
 * (rozostřená bublina, fotka) drží kolem 4,8. Pro přísné AA i u popisku by stačilo 0,18 / 0,15.
 */
internal const val PILL_GLASS_LEAK = 0.18f

/**
 * Únik jasu bílé pilulky ve světlém motivu — víc než v tmavém: bílá plocha s tmavým textem má
 * rezervu a kontrast jí ubírá jen TMAVÉ pozadí. Viz [PILL_GLASS_LEAK].
 */
internal const val PILL_GLASS_LEAK_LIGHT = 0.25f

/**
 * Kolik jasu propustí pás pod polem — víc než pilulka, protože vlastní text nenese (karta pole
 * má svou plochu). Odpovídá dřívějšímu krytí 0,72; při krytí 0,93 nebylo rozostření vidět
 * vůbec a text zprávy končil na hraně pásu, jako by ho něco ořízlo.
 */
internal const val BAND_GLASS_LEAK = 0.28f

/**
 * Krytí pásu tam, kde rozostření není (Android 11 a starší): dost na to, aby text pod ním
 * nerušil, ale ne plná barva — ta by udělala ostrý předěl přes celou obrazovku.
 */
private const val BAND_FALLBACK_ALPHA = 0.93f

/** Kolikrát se zesílí barvy pod sklem — jas se tím nemění. */
internal const val GLASS_SATURATION = 2.4f

/** Zrnitost matného skla: i nad jednolitým pozadím je plocha poznat jako materiál, ne barva. */
private const val GLASS_NOISE = 0.08f

private val GLASS_RIM_WIDTH = 1.dp
private const val GLASS_RIM_ALPHA_DARK = 0.18f
private const val GLASS_RIM_ALPHA_LIGHT = 0.9f
