package eu.tintera.plume.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect

/**
 * Rozostřené pozadí pod snímkem: prohlížeč médií i potvrzení sdílení do jednoho chatu.
 *
 * Z fotky má zbýt jen barevná nálada, ne tvary, které by soupeřily s kartou nad ní — proto velký
 * poloměr. Rozostřuje Haze jako závoj pod nabídkou nad zprávou, takže to platí i tady: kde
 * rozostření není (Android 11 a starší), kreslí se místo něj skoro krycí závoj, jinak by ostrá
 * fotka za kartou rušila.
 *
 * **Bydlí v `core/ui`, ne v prohlížeči.** Sdílení by si jinak buď importovalo `features/media`,
 * nebo (hůř) opsalo styl i s fallbackem — a dvě kopie by se rozešly na prvním doladění.
 */
@Composable
fun ambientBlurStyle(): HazeBlurStyle {
    val scrim = MaterialTheme.colorScheme.scrim
    val blur = LocalBackgroundBlurAllowed.current
    return remember(scrim, blur) {
        HazeBlurStyle {
            // Systém rozostření nechce („Omezit efekty rozmazání") — kreslí se náhradní závoj.
            // Jen vypnout: zapnuté rozostření rozhoduje Haze sám (pod Androidem 12 ho nemá).
            if (!blur) blurEnabled(false)
            colorEffects(listOf(HazeColorEffect.tint(scrim.copy(alpha = AMBIENT_TINT_ALPHA))))
            blurRadius(AMBIENT_BLUR_RADIUS)
            noiseFactor(0f)
            fallbackColorEffect(HazeColorEffect.tint(scrim.copy(alpha = AMBIENT_FALLBACK_ALPHA)))
        }
    }
}

/** Zaoblení karty se snímkem (`shapes.large`); v prohlížeči jde na celé obrazovce k nule. */
val MEDIA_CARD_CORNER = 16.dp

private val AMBIENT_BLUR_RADIUS = 48.dp
private const val AMBIENT_TINT_ALPHA = 0.45f
private const val AMBIENT_FALLBACK_ALPHA = 0.85f
