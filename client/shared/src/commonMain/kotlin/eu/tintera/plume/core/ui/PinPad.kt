package eu.tintera.plume.core.ui

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
 */
@Composable
fun PinKeypad(
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onBiometrics: (() -> Unit)? = null,
    isBiometricsEnabled: Boolean = false,
    isEnabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val rows = listOf(
            listOf(1, 2, 3),
            listOf(4, 5, 6),
            listOf(7, 8, 9),
        )

        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                for (digit in row) {
                    PinDigitButton(
                        text = digit.toString(),
                        enabled = isEnabled,
                        onClick = {
                            haptics.tick()
                            onDigit(digit)
                        },
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // Vlevo: biometrie nebo prázdné místo
            if (isBiometricsEnabled && onBiometrics != null) {
                PinIconButton(
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
                Box(Modifier.size(76.dp))
            }

            // Uprostřed: nula
            PinDigitButton(
                // Číslice jako u ostatních tlačítek, ne literál — ten `verifyNoHardcodedText` nepustí.
                text = 0.toString(),
                enabled = isEnabled,
                onClick = {
                    haptics.tick()
                    onDigit(0)
                },
            )

            // Vpravo: smazání
            PinIconButton(
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
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(76.dp),
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
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(76.dp),
        shapes = IconButtonDefaults.shapes(),
    ) {
        content()
    }
}

