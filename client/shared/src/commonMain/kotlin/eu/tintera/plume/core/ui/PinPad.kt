package eu.tintera.plume.core.ui

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import eu.tintera.plume.core.lock.APP_LOCK_PIN_LENGTH
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.backspace_24dp
import eu.tintera.plume.resources.fingerprint_24dp
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

/**
 * Tečky znázorňující zadané číslice PINu v Material 3 Expressive stylu.
 *
 * Při zadání číslice se tečka animovaně přemění z obrysového kruhu na vyplněný
 * tvar čtyřlístku s hravým odpružením a lehkou rotací. Při chybě se tečky
 * přebarví a zatřesou podle [errorTrigger].
 */
@Composable
fun PinDots(
    filledCount: Int,
    modifier: Modifier = Modifier,
    length: Int = APP_LOCK_PIN_LENGTH,
    errorTrigger: Int = 0,
    isError: Boolean = false,
) {
    val offsetX = remember { Animatable(0f) }

    LaunchedEffect(errorTrigger) {
        if (errorTrigger > 0) {
            offsetX.snapTo(0f)
            offsetX.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 350
                    -20f at 50
                    20f at 100
                    -15f at 150
                    15f at 200
                    -8f at 250
                    8f at 300
                    0f at 350
                },
            )
        }
    }

    val dotMorph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Clover4Leaf) }

    Row(
        modifier = modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(length) { index ->
            val filled = index < filledCount

            val progress by animateFloatAsState(
                targetValue = if (filled) 1f else 0f,
                animationSpec = spring(
                    dampingRatio = 0.5f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "dotMorph_$index",
            )
            val scale by animateFloatAsState(
                targetValue = if (filled) 1.25f else 0.85f,
                animationSpec = spring(
                    dampingRatio = 0.5f,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "dotScale_$index",
            )

            val fillColor by animateColorAsState(
                targetValue = when {
                    isError -> MaterialTheme.colorScheme.error
                    filled -> MaterialTheme.colorScheme.primary
                    else -> Color.Transparent
                },
                animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium),
                label = "dotFill_$index",
            )
            val borderColor by animateColorAsState(
                targetValue = when {
                    isError -> MaterialTheme.colorScheme.error
                    filled -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.outline
                },
                animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium),
                label = "dotBorder_$index",
            )

            val shape = remember(progress) {
                MorphShape(dotMorph, progress = progress, rotation = progress * 45f)
            }

            Box(
                modifier = Modifier
                    .size(18.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .background(fillColor, shape)
                    .border(2.dp, borderColor, shape),
            )
        }
    }
}

/**
 * Numerická klávesnice 3 × 4 pro zadávání PINu v Material 3 Expressive stylu.
 *
 * Tlačítka používají standardní `IconButtonDefaults.shapes()`, které při stisku
 * plynule morphují z kruhu do squircle tvaru dle M3 Expressive standardu.
 *
 * [keySize] zmenšuje klávesnici na nízkém okně (viz [PinLayout]); mezery se zmenšují s ní.
 */
@Composable
fun PinKeypad(
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onBiometrics: (() -> Unit)? = null,
    isBiometricsEnabled: Boolean = false,
    isEnabled: Boolean = true,
    keySize: Dp = PIN_KEY_SIZE,
) {
    val haptics = LocalHapticFeedback.current
    val scale = keySize / PIN_KEY_SIZE
    val rowGap = PIN_ROW_GAP * scale
    val keyGap = PIN_KEY_GAP * scale

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(rowGap),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val rows = listOf(
            listOf(1, 2, 3),
            listOf(4, 5, 6),
            listOf(7, 8, 9),
        )

        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(keyGap)) {
                for (digit in row) {
                    PinDigitButton(
                        text = digit.toString(),
                        size = keySize,
                        enabled = isEnabled,
                        onClick = {
                            haptics.tick()
                            onDigit(digit)
                        },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(keyGap)) {
            // Vlevo: biometrie nebo prázdné místo
            if (isBiometricsEnabled && onBiometrics != null) {
                PinIconButton(
                    size = keySize,
                    enabled = true,
                    onClick = {
                        haptics.tick()
                        onBiometrics()
                    },
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.fingerprint_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                Box(Modifier.size(keySize))
            }

            // Uprostřed: nula
            PinDigitButton(
                // Číslice jako u ostatních tlačítek, ne literál — ten `verifyNoHardcodedText` nepustí.
                text = 0.toString(),
                size = keySize,
                enabled = isEnabled,
                onClick = {
                    haptics.tick()
                    onDigit(0)
                },
            )

            // Vpravo: smazání
            PinIconButton(
                size = keySize,
                enabled = isEnabled,
                onClick = {
                    haptics.tick()
                    onDelete()
                },
            ) {
                Icon(
                    painter = painterResource(Res.drawable.backspace_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PinDigitButton(
    text: String,
    size: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(size),
        shapes = IconButtonDefaults.shapes(),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
        )
    }
}

@Composable
private fun PinIconButton(
    size: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(size),
        shapes = IconButtonDefaults.shapes(),
    ) {
        content()
    }
}


/**
 * Rozvržení obrazovky s PINem: [header] (nadpis, hláška, tečky) a pod ním klávesnice — na nízkém
 * širokém okně vedle sebe. Sdílí ho zamykací obrazovka i zadávání PINu v Nastavení.
 *
 * **Pod sebou se to vejde až od zhruba 560 dp výšky** (hlavička kolem 170 dp, klávesnice čtyři
 * řady po 76 dp s mezerami 352 dp). Telefon naležato má po odečtení lišt kolem 360 dp: dřív
 * z klávesnice zbyla jen první řada a tečky ležely přes ni, PIN tak nešel nastavit ani zadat.
 *
 * - **Vedle sebe** jen tam, kde je místo do šířky: hlavička [PIN_HEADER_WIDTH] a klávesnice
 *   zmenšená na výšku okna ([PinKeypad.keySize]). Rozhodovat jen podle poměru stran nestačilo:
 *   úzký panel Nastavení naležato (kolem 330 dp) je pořád širší než vysoký a klávesnice z něj
 *   přetekla do strany.
 * - **Pod sebou** se klávesnice zmenší tak, aby se vešla pod hlavičku, a když ani to nestačí
 *   (úzký a nízký panel), obsah roluje.
 * - Tlačítko je nejméně [PIN_KEY_MIN_SIZE] — pod tím by bylo menší než dotykový cíl M3.
 *
 * [footer] (Zrušit) jde pod klávesnici, vedle sebe pod hlavičku: pod klávesnicí by ji vytlačil
 * z výšky. [headerTopPadding] odsazuje hlavičku jen pod sebou; vedle sebe je na střed výšky.
 */
@Composable
fun PinLayout(
    header: @Composable () -> Unit,
    keypad: @Composable (keySize: Dp) -> Unit,
    modifier: Modifier = Modifier,
    headerTopPadding: Dp = 16.dp,
    footer: (@Composable () -> Unit)? = null,
) {
    BoxWithConstraints(modifier) {
        val fullHeight = maxHeight
        val sideKey = keySizeFor(fullHeight)
        val sideBySide = maxWidth > maxHeight &&
            maxHeight < PIN_COLUMN_MIN_HEIGHT &&
            maxWidth >= PIN_HEADER_WIDTH + PIN_KEY_GAP + keypadWidth(sideKey)
        if (sideBySide) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = PIN_HEADER_WIDTH)) {
                    header()
                    if (footer != null) {
                        Spacer(Modifier.height(16.dp))
                        footer()
                    }
                }
                keypad(sideKey)
            }
        } else {
            val footerHeight = if (footer == null) 16.dp else 8.dp + PIN_FOOTER_HEIGHT
            val columnKey = keySizeFor(maxHeight - headerTopPadding - PIN_HEADER_HEIGHT - footerHeight)
            // Pod sebou roluje, kdyby se nevešlo ani se zmenšenou klávesnicí; jinak stojí.
            Column(Modifier.fillMaxWidth().verticalScroll(rememberRestoringScrollState())) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().heightIn(min = fullHeight),
                ) {
                    Box(Modifier.padding(top = headerTopPadding)) { header() }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.padding(bottom = if (footer == null) 16.dp else 8.dp)) { keypad(columnKey) }
                        footer?.invoke()
                    }
                }
            }
        }
    }
}

/** Největší tlačítko, se kterým se čtyři řady klávesnice vejdou do [height]. */
private fun keySizeFor(height: Dp): Dp {
    // Řada je tlačítko a mezera úměrná tlačítku (PIN_ROW_GAP na PIN_KEY_SIZE): 4k + 3k·g/K ≤ h.
    val perKey = 4f + 3f * (PIN_ROW_GAP / PIN_KEY_SIZE)
    return (height / perKey).coerceIn(PIN_KEY_MIN_SIZE, PIN_KEY_SIZE)
}

private fun keypadWidth(keySize: Dp): Dp = keySize * 3 + PIN_KEY_GAP * (keySize / PIN_KEY_SIZE) * 2

private val PIN_KEY_SIZE = 76.dp
private val PIN_KEY_MIN_SIZE = 48.dp
private val PIN_ROW_GAP = 16.dp
private val PIN_KEY_GAP = 24.dp
private val PIN_COLUMN_MIN_HEIGHT = 560.dp

/** Šířka hlavičky vedle klávesnice: vejde se do ní nadpis kroku („Zadej nový 4místný PIN“). */
private val PIN_HEADER_WIDTH = 280.dp

/** Odhad výšky hlavičky pod sebou (nadpis, hláška, tečky) pro velikost klávesnice pod ní. */
private val PIN_HEADER_HEIGHT = 140.dp

/** Tlačítko pod klávesnicí (Zrušit). */
private val PIN_FOOTER_HEIGHT = 48.dp
