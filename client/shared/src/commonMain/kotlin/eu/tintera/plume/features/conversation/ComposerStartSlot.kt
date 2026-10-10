package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
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
 * - **U víceřádkového textu stojí obě pod sebou** ([fieldHeight] aspoň [STACKED_HEIGHT], tedy
 *   od tří řádků). Sloupec je pořád jedna ikona široký, takže se pole nerozšíří a nic se
 *   nepřeskládá — dřív zamítnuté svislé skládání naráželo právě na to, protože ikony stály VEDLE
 *   sebe. Ikona, která byla vidět, zůstane dole u posledního řádku a druhá zpoza ní vyjede nahoru
 *   (a při zkrácení textu zajede zpátky); buben se tím „rozvine" a tah nemá co přepínat. Karta se kvůli tomu nikdy nezvětší.
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
    /** Výška pole vedle; od [STACKED_HEIGHT] se ikony vejdou pod sebe. */
    fieldHeight: Dp,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    // Ruční volba tahem; `null` = platí automatické pravidlo.
    var manual by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(formatAuto) { manual = null }
    val showFormat = attach == null || (manual ?: formatAuto)
    val stacked = attach != null && fieldHeight >= STACKED_HEIGHT
    // Směr bubnu z tahu (true = nahoru, nová ikona přijede zdola); `null` = automatická změna,
    // ta jede po bubnu: „Aa" leží pod sponkou. Oba bubny (i horní v rozvinutém sloupci) se točí
    // stejně, proto se směr nemaže v přechodu, ale až po kompozici. Obyčejné pole, ne stav.
    val swipeUp = remember { arrayOfNulls<Boolean>(1) }
    LaunchedEffect(showFormat) { swipeUp[0] = null }
    val currentShowFormat by rememberUpdatedState(showFormat)
    val haptics = LocalHapticFeedback.current
    val spatial = MaterialTheme.motionScheme.fastSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

    val swipeLabelFormat = stringResource(Res.string.composer_show_formatting)
    val swipeLabelAttach = stringResource(Res.string.composer_show_attachments)
    val swipe = if (attach == null || stacked) {
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
                            swipeUp[0] = dragged < 0
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
                        swipeUp[0] = !showFormat
                        manual = !showFormat
                        true
                    },
                )
            }
    }

    // Jedna tvář bubnu: „Aa", nebo sponka. [format] je to, co má být vidět.
    @Composable
    fun Face(format: Boolean, faceModifier: Modifier) {
        AnimatedContent(
            targetState = format,
            transitionSpec = {
                // Nahoru = nová přijede zdola. Bez tahu po bubnu: „Aa" leží pod sponkou, takže
                // točení k „Aa" jde nahoru — pro horní tvář, která ukazuje opak, totéž otočení.
                val up = swipeUp[0] ?: (targetState == showFormat)
                (slideInVertically(spatial) { if (up) it else -it } + fadeIn(effects))
                    .togetherWith(slideOutVertically(spatial) { if (up) -it else it } + fadeOut(effects))
            },
            modifier = faceModifier.clipToBounds(),
            label = "field-start-slot",
        ) { showsFormat ->
            if (showsFormat || attach == null) {
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

    /*
     * Místo nikdy nenahlásí víc výšky, než má pole vedle (bez 4 dp odsazení pod ním). Mizející
     * horní ikona si jinak drží místo, dokud nedoběhne: po smazání na dva řádky zůstala karta
     * o 20 dp vyšší a na konci skočila (zjištěno sérií snímků). Co se nevejde, kreslí se nahoru
     * přes okraj — týká se to jen mizející ikony, ustálený sloupec se do pole vejde vždycky.
     */
    val limit = fieldHeight - SLOT_BOTTOM_PADDING
    Column(
        modifier.layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val height = placeable.height.coerceAtMost(limit.roundToPx().coerceAtLeast(FACE_HEIGHT.roundToPx()))
            layout(placeable.width, height) { placeable.place(0, height - placeable.height) }
        },
    ) {
        /*
         * Druhá tvář nad tou, která je vidět. VYJEDE zpoza ní nahoru a při zkrácení textu zase
         * zajede zpátky — ořízne se na hraně mezi nimi, takže se vysouvá zpod dolní ikony. Tak je
         * vidět, že ikony leží na sobě a u kratšího textu se dají přepínat tahem. Pohyb zůstává
         * v pruhu ikon, přes pole nejede nic.
         */
        AnimatedVisibility(
            visible = stacked,
            enter = slideInVertically(spatial) { it } + fadeIn(effects),
            exit = slideOutVertically(spatial) { it } + fadeOut(effects),
            modifier = Modifier.clipToBounds(),
        ) {
            Face(!showFormat, Modifier.overlapBelow(STACK_OVERLAP))
        }
        Face(showFormat, swipe)
    }
}

/**
 * Nahlásí se o [overlap] nižší, takže prvek pod ním ho o tolik překryje. Dotyková plocha
 * tlačítka (48 dp) je větší než jeho kontejner (40 dp), překryv tedy padne do vzduchu kolem.
 */
private fun Modifier.overlapBelow(overlap: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, (placeable.height - overlap.roundToPx()).coerceAtLeast(0)) {
        placeable.place(0, 0)
    }
}

/** Jedna ikona: minimální dotyková plocha tlačítka. */
private val FACE_HEIGHT = 48.dp

/** Odsazení pod místem (dává ho volající), které jednu ikonu centruje na jednořádkové pole. */
private val SLOT_BOTTOM_PADDING = 4.dp

/** O kolik se tlačítka pod sebou překryjí — jinak by se vešla až od čtyř řádků. */
private val STACK_OVERLAP = 4.dp

/**
 * Výška pole, od které se ikony postaví pod sebe: dvě tlačítka 48 dp bez překryvu a 4 dp pod
 * nimi (odsazení, které jednu ikonu centruje na jednořádkové pole). Tři řádky textu 16/22 mají
 * s vnitřním odsazením pole 98 dp, dva 76 — skládá se tedy od tří.
 */
private val STACKED_HEIGHT = FACE_HEIGHT * 2 - STACK_OVERLAP + SLOT_BOTTOM_PADDING


/** Kolik musí prst ujet, než se ikona přepne — zhruba polovina tlačítka. */
private val SWIPE_THRESHOLD = 20.dp
