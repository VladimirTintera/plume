package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.tick
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.composer_show_attachments
import eu.tintera.plume.resources.composer_show_formatting
import eu.tintera.plume.resources.format_open
import eu.tintera.plume.resources.text_format_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/**
 * Levé místo v kartě pole: sponka, nebo „Aa" (lišta formátování i bez označeného textu).
 *
 * Smajlík (výběr emoji, samolepek a GIFů) stojí vpravo VŽDYCKY — potřebuje se hlavně uprostřed
 * psaní. Dřív sdílel místo s „Aa" a s otevřenou klávesnicí nebyl k mání vůbec. Vyměňuje se proto
 * „Aa" se sponkou: přílohu člověk přidává skoro vždycky na začátku, se zavřenou klávesnicí.
 *
 * - **„Aa" automaticky jen s otevřenou klávesnicí A textem v poli** ([formatAuto]). Samotná
 *   klávesnice nestačí: kdo pole otevře, aby přiložil fotku s popiskem, chce sponku.
 * - **Svislý tah po ikoně je přepíná** jako buben: nahoru přijede ta další zdola, dolů shora.
 *   Ruční volba platí, dokud se automatické pravidlo samo nezmění — pak má zase přednost ono.
 *   Šířka pole se tím nemění nikdy, mění se jen ikona v místě stejné velikosti.
 * - **Bez sponky ([attach] `null` — úprava zprávy, chat bez příloh) je tu „Aa" trvale.** Kdyby
 *   naskakovalo s klávesnicí, ubralo by poli šířku a rozepsaný text by se při ťuknutí přeskládal.
 * - **„Aa" bez klávesnice ji otevře** ([onFormatWithoutKeyboard]) — jinak by ťuknutí na ikonu
 *   přepnuté tahem nebo trvalou při úpravě neudělalo nic viditelného.
 */
@Composable
internal fun ComposerStartSlot(
    /** Sponka; `null`, kde přílohy nejdou. */
    attach: (@Composable () -> Unit)?,
    /** Mají teď být vidět „Aa"? Otevřená klávesnice a text v poli. */
    formatAuto: Boolean,
    /** Je klávesnice otevřená (lišta formátování se bez ní nekreslí)? */
    typing: Boolean,
    /** „Aa" ukazuje, že lišta formátování je venku (otevřená, nebo je v poli označený text). */
    formattingShown: Boolean,
    onToggleFormatting: () -> Unit,
    /** Ťuknutí na „Aa" se zavřenou klávesnicí: otevřít ji i s lištou. */
    onFormatWithoutKeyboard: () -> Unit,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    // Ruční volba tahem; `null` = platí automatické pravidlo.
    var manual by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(formatAuto) { manual = null }
    val showFormat = attach == null || (manual ?: formatAuto)
    // Odkud přijede nová ikona. Tah ho určí sám; automatická změna jede po bubnu: „Aa" leží pod
    // sponkou. Obyčejné pole, ne stav — čte se jen při výběru přechodu.
    val fromBelow = remember { BooleanArray(1) { true } }
    val currentShowFormat by rememberUpdatedState(showFormat)
    val haptics = LocalHapticFeedback.current
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    val swipeLabelFormat = stringResource(Res.string.composer_show_formatting)
    val swipeLabelAttach = stringResource(Res.string.composer_show_attachments)
    val swipe = if (attach == null) {
        Modifier
    } else {
        Modifier
            .pointerInput(Unit) {
                val threshold = SWIPE_THRESHOLD.toPx()
                var dragged = 0f
                var switched = false
                detectVerticalDragGestures(
                    onDragStart = {
                        dragged = 0f
                        switched = false
                    },
                    onVerticalDrag = { change, amount ->
                        change.consume()
                        dragged += amount
                        // Jeden tah = jedno přepnutí, i když prst jede dál.
                        if (!switched && abs(dragged) > threshold) {
                            switched = true
                            fromBelow[0] = dragged < 0
                            manual = !currentShowFormat
                            haptics.tick()
                        }
                    },
                )
            }
            // Tah čtečka obrazovky neudělá; přepnutí jí jde jako vlastní akce.
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(if (showFormat) swipeLabelAttach else swipeLabelFormat) {
                        fromBelow[0] = !showFormat
                        manual = !showFormat
                        true
                    },
                )
            }
    }

    AnimatedContent(
        targetState = showFormat,
        transitionSpec = {
            val below = fromBelow[0]
            // Tah si směr nastaví sám před změnou; další automatická změna jede zase po bubnu.
            fromBelow[0] = !targetState
            (slideInVertically(spatial) { if (below) it else -it } + fadeIn(effects))
                .togetherWith(slideOutVertically(spatial) { if (below) -it else it } + fadeOut(effects))
        },
        modifier = modifier.then(swipe).clipToBounds(),
        label = "field-start-slot",
    ) { format ->
        if (format || attach == null) {
            IconToggleButton(
                checked = formattingShown,
                onCheckedChange = { if (typing) onToggleFormatting() else onFormatWithoutKeyboard() },
                colors = IconButtonDefaults.iconToggleButtonColors(
                    contentColor = contentColor,
                    checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                    checkedContainerColor = MaterialTheme.colorScheme.primary,
                ),
                shapes = IconButtonDefaults.toggleableShapes(),
            ) {
                Icon(painterResource(Res.drawable.text_format_24dp), contentDescription = stringResource(Res.string.format_open))
            }
        } else {
            Box { attach() }
        }
    }
}

/** Kolik musí prst ujet, než se ikona přepne — zhruba polovina tlačítka. */
private val SWIPE_THRESHOLD = 20.dp
