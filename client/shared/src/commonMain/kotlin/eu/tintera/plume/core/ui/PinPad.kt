package eu.tintera.plume.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import eu.tintera.plume.core.lock.APP_LOCK_PIN_LENGTH
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.backspace_24dp
import eu.tintera.plume.resources.fingerprint_24dp
import kotlinx.coroutines.delay
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
 * Tlačítka při stisku dynamicky mění tvar z kruhu na zaoblenou sušenku
 * se snížením měřítka a barevným zvýrazněním.
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
                ) { tint ->
                    Icon(
                        painter = painterResource(Res.drawable.fingerprint_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = tint,
                    )
                }
            } else {
                Box(Modifier.size(76.dp))
            }

            // Uprostřed: nula
            PinDigitButton(
                text = "0",
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
            ) { tint ->
                Icon(
                    painter = painterResource(Res.drawable.backspace_24dp),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                    tint = tint,
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isPulsing by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            isPulsing = true
        } else if (isPulsing) {
            delay(120)
            isPulsing = false
        }
    }

    val active = (isPressed || isPulsing) && enabled
    val morphProgress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "digitMorph",
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 0.92f else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "digitScale",
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.38f)
            active -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium),
        label = "digitContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            active -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium),
        label = "digitContent",
    )

    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie4Sided) }
    val shape = remember(morphProgress) {
        MorphShape(morph, progress = morphProgress, rotation = morphProgress * 15f)
    }

    Box(
        modifier = Modifier
            .size(76.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(containerColor)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = 38.dp),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineMedium,
            color = contentColor,
        )
    }
}

@Composable
private fun PinIconButton(
    enabled: Boolean,
    onClick: () -> Unit,
    content: @Composable (tint: Color) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var isPulsing by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            isPulsing = true
        } else if (isPulsing) {
            delay(120)
            isPulsing = false
        }
    }

    val active = (isPressed || isPulsing) && enabled
    val morphProgress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "iconMorph",
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 0.90f else 1f,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "iconScale",
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Transparent
            active -> MaterialTheme.colorScheme.secondaryContainer
            else -> Color.Transparent
        },
        animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium),
        label = "iconContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            active -> MaterialTheme.colorScheme.onSecondaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium),
        label = "iconContent",
    )

    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie4Sided) }
    val shape = remember(morphProgress) {
        MorphShape(morph, progress = morphProgress, rotation = morphProgress * 15f)
    }

    Box(
        modifier = Modifier
            .size(76.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(containerColor)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = 38.dp),
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content(contentColor)
    }
}

