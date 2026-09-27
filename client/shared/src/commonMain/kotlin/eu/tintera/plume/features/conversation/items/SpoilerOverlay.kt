package eu.tintera.plume.features.conversation.items

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.koin.compose.koinInject
import eu.tintera.plume.core.telegram.RevealedSpoilers
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.media_sensitive
import eu.tintera.plume.resources.media_spoiler
import org.jetbrains.compose.resources.stringResource

/**
 * Styl rozostření pro skrytá média (spoiler).
 */
@Composable
internal fun rememberSpoilerBlurStyle(): HazeBlurStyle {
    val scrim = MaterialTheme.colorScheme.scrim
    return remember(scrim) {
        HazeBlurStyle {
            colorEffects(listOf(HazeColorEffect.tint(scrim.copy(alpha = SPOILER_TINT_ALPHA))))
            blurRadius(SPOILER_BLUR_RADIUS)
            noiseFactor(0f)
            fallbackColorEffect(HazeColorEffect.tint(scrim.copy(alpha = SPOILER_FALLBACK_ALPHA)))
        }
    }
}

/**
 * Rozostří médium pomocí Haze přímo na jeho obsahu ([HazeInput.Content]), pokud má spoiler
 * a ještě nebylo odhaleno.
 */
internal fun Modifier.spoilerBlur(
    hasSpoiler: Boolean,
    revealed: Boolean,
    style: HazeBlurStyle,
): Modifier = if (hasSpoiler && !revealed) {
    hazeBlur(input = HazeInput.Content, style = style)
} else {
    this
}

/**
 * Odznak „Spoiler" nad skrytým médiem, u citlivého obsahu „Citlivý obsah" ([sensitive]).
 *
 * Citlivé médium se skrývá toutéž cestou jako spoiler (viz `SensitiveContent`); liší se jen
 * tím, co štítek říká — „spoiler" by u něj lhal o tom, proč je rozostřené.
 *
 * Samotné rozostření fotky nebo videa řeší [spoilerBlur] přímo na jejich obsahu,
 * tato vrstva zobrazuje pouze středový štítek s plynulým mizení při odhalení.
 * Kliknutí řeší rodič: první ťuknutí přepne viditelnost na `false`, druhé otevře médium.
 */
@Composable
internal fun SpoilerOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
    sensitive: Boolean = false,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                contentColor = Color.White,
                modifier = Modifier.padding(8.dp),
            ) {
                Text(
                    text = stringResource(if (sensitive) Res.string.media_sensitive else Res.string.media_spoiler),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

/**
 * Obsah skrytý do prvního ťuknutí — rozostření, štítek „Citlivý obsah" a odkrytí jako u fotky
 * se spoilerem (odkrytí se pamatuje v [RevealedSpoilers] podle zprávy, takže platí pro celou
 * zprávu naráz).
 *
 * **Jedna cesta pro všechno, co fotkou není** — samolepku, náhled souboru, obrázek náhledu
 * odkazu. Dokud se citlivost řešila jen u fotky, videa a GIFu, zbytek citlivé zprávy zůstal ostrý.
 *
 * Skrytý obsah bere ťuknutí sám (odkryje ho), odkrytý ho pustí vlastnímu klikání obsahu. [onLongClick] se musí
 * zopakovat: potomek s vlastním klikáním by dlouhý stisk k bublině nepustil a nad skrytým
 * obsahem by nešla otevřít nabídka.
 */
@Composable
internal fun SensitiveBox(
    messageId: Long,
    hidden: Boolean,
    modifier: Modifier = Modifier,
    /** Štítek se nevejde na malý náhled (soubor); tam stačí rozostření. */
    label: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    /**
     * Ořez skrytého obsahu. Samolepka je průhledná a závoj rozostření pokryje celý její
     * obdélník — hranatý šedý čtverec vypadal jako chyba; odkrytá samolepka se neořezává.
     */
    coveredShape: Shape? = null,
    content: @Composable (blur: Modifier) -> Unit,
) {
    val revealedSpoilers = koinInject<RevealedSpoilers>()
    val revealedIds by revealedSpoilers.revealed.collectAsState()
    val covered = hidden && messageId !in revealedIds
    val style = rememberSpoilerBlurStyle()
    Box(modifier, contentAlignment = Alignment.Center) {
        val clip = if (covered && coveredShape != null) Modifier.clip(coveredShape) else Modifier
        content(clip.spoilerBlur(covered, revealed = false, style))
        if (hidden && label) SpoilerOverlay(visible = covered, sensitive = true)
        /*
         * Ťuknutí bere VRSTVA NAD obsahem, ne obal kolem něj. Obsah má často vlastní klikání
         * (samolepka otevírá sadu, obrázek náhledu odkaz) a potomek dostává dotyk dřív než
         * rodič — skrytá samolepka by se místo odkrytí otevřela. Poslední potomek v `Box` je
         * na řadě první.
         */
        if (covered) {
            Box(
                Modifier
                    .matchParentSize()
                    .combinedClickable(
                        interactionSource = null,
                        indication = null,
                        onLongClick = onLongClick,
                        onClick = { revealedSpoilers.reveal(messageId) },
                    ),
            )
        }
    }
}

private val SPOILER_BLUR_RADIUS = 48.dp
private const val SPOILER_TINT_ALPHA = 0.25f
private const val SPOILER_FALLBACK_ALPHA = 0.6f
