package eu.tintera.plume.features.conversation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import eu.tintera.plume.core.ui.LocalBackgroundBlurAllowed

/**
 * Mléčné sklo nad seznamem zpráv: pás pod vstupním polem, pruh připnutých zpráv a plovoucí datum.
 *
 * Všechny tři plochy leží NAD projíždějícím obsahem a dosud ho jen zakrývaly barvou. Rozostření
 * dělá totéž čitelněji: je vidět, že pod nimi obsah pokračuje, a přitom nepřebíjí to, co na nich
 * stojí. Tentýž Haze jako závoj pod nabídkou nad zprávou (`CLAUDE.md`, „Nabídka nad zprávou“).
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
internal fun glassStyle(color: Color, alpha: Float, fallbackAlpha: Float): HazeBlurStyle =
    LocalBackgroundBlurAllowed.current.let { blur ->
        remember(color, alpha, fallbackAlpha, blur) {
            HazeBlurStyle {
                // Systém rozostření nechce („Omezit efekty rozmazání") — kreslí se náhradní plocha.
                // Jen vypnout: zapnuté rozostření rozhoduje Haze sám (pod Androidem 12 ho nemá).
                if (!blur) blurEnabled(false)
                colorEffects(listOf(HazeColorEffect.tint(color.copy(alpha = alpha))))
                blurRadius(GLASS_BLUR_RADIUS)
                noiseFactor(0f)
                // Kde rozostření není (Android 11 a starší), zůstane dřívější krycí plocha.
                fallbackColorEffect(HazeColorEffect.tint(color.copy(alpha = fallbackAlpha)))
            }
        }
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

/**
 * Krytí ploch, které nesou VLASTNÍ TEXT (pruh připnutých, plovoucí datum).
 *
 * Musí být vyšší: pod nimi projede i tmavá fotka, sklo nad ní ztmavne a písmo se v něm ztratí.
 * Změřeno na emulátoru — s [GLASS_TINT_ALPHA] mělo „Včera" nad černým náhledem videa kontrast
 * 2,85, tedy hluboko pod AA; s tímhle krytím vychází kolem 6. Pás pod vstupním polem vlastní text
 * nenese (karta pole má svoji plochu), takže tam stačí nižší krytí a sklo je znát víc.
 */
internal const val GLASS_TEXT_TINT_ALPHA = 0.88f

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
