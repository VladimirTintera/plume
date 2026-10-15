package eu.tintera.plume.features.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.WithTooltip
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chevron_left_24dp
import eu.tintera.plume.resources.chevron_right_24dp
import eu.tintera.plume.resources.media_next
import eu.tintera.plume.resources.media_previous
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Co udělá klávesa v prohlížeči médií. */
internal enum class ViewerKey { Previous, Next, ZoomIn, ZoomOut, ZoomReset }

/**
 * Klávesa v prohlížeči: ←/→ listují (v RTL obráceně, jako pager), +/−/0 přibližují. Esc tu není
 * — zavírá prohlížeč jako Zpět, na desktopu ho převádí Compose sám, na Androidu `MainActivity`.
 *
 * - **Šipky jen bez modifikátorů.** Alt+šipka je přepínání chatů, Shift/Ctrl+šipka patří textu.
 * - **+/−/0 i s Ctrl** (Ctrl+= / Ctrl+− / Ctrl+0 jako v prohlížeči), ne s Alt ani ⌘.
 * - **Plus a minus i podle znaku** ([char]), ne jen podle klávesy: na české klávesnici je „+"
 *   na klávese, kde anglická má „1", a „=" jinde. Numerická klávesnice má kódy vlastní.
 */
internal fun viewerKey(key: Key, char: Char?, ctrl: Boolean, alt: Boolean, shift: Boolean, meta: Boolean, rtl: Boolean): ViewerKey? {
    if (alt || meta) return null
    val plain = !ctrl && !shift
    return when {
        plain && key == Key.DirectionLeft -> if (rtl) ViewerKey.Next else ViewerKey.Previous
        plain && key == Key.DirectionRight -> if (rtl) ViewerKey.Previous else ViewerKey.Next
        key == Key.NumPadAdd || key == Key.Plus || char == '+' -> ViewerKey.ZoomIn
        key == Key.Equals && !shift || char == '=' -> ViewerKey.ZoomIn
        key == Key.NumPadSubtract || key == Key.Minus || char == '-' -> ViewerKey.ZoomOut
        key == Key.NumPad0 || (key == Key.Zero && !shift) || char == '0' -> ViewerKey.ZoomReset
        else -> null
    }
}

/**
 * Pohyb myši v prohlížeči: `true` po pohybu, `false` po [HIDE_AFTER_MS] klidu. Ukazuje šipky
 * po stranách jen tomu, kdo má myš — dotyk je nepotřebuje (listuje tahem) a překážely by.
 * Stejný vzor jako ovládání hovoru (`CallControls`). Událost se jen pozoruje (`Initial`,
 * nic se nekonzumuje).
 */
internal class MouseActivity {
    var active by mutableStateOf(false)
        private set
    var moves by mutableIntStateOf(0)
        private set

    fun onMouseMove() {
        active = true
        moves++
    }

    fun hide() {
        active = false
    }
}

internal fun Modifier.trackMouse(activity: MouseActivity): Modifier = pointerInput(activity) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type == PointerEventType.Move && event.changes.any { it.type == PointerType.Mouse }) activity.onMouseMove()
        }
    }
}

/**
 * Šipky na předchozí a další stránku u okrajů, svisle uprostřed. Ukážou se pohybem myši
 * ([activity]) a po chvíli klidu zmizí — nad šipkou zůstávají, dokud je na ní kurzor. Na kraji
 * galerie šipka do prázdna není.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BoxScope.PageArrows(
    activity: MouseActivity,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previousHover = remember { MutableInteractionSource() }
    val nextHover = remember { MutableInteractionSource() }
    val hovered = previousHover.collectIsHoveredAsState().value || nextHover.collectIsHoveredAsState().value
    LaunchedEffect(activity.moves, hovered) {
        if (hovered) return@LaunchedEffect
        delay(HIDE_AFTER_MS)
        activity.hide()
    }
    val enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec())
    val exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())
    Box(modifier.align(Alignment.CenterStart).padding(start = ARROW_EDGE)) {
        AnimatedVisibility(visible = activity.active && hasPrevious, enter = enter, exit = exit) {
            PageArrow(Res.drawable.chevron_left_24dp, stringResource(Res.string.media_previous), previousHover, onPrevious)
        }
    }
    Box(modifier.align(Alignment.CenterEnd).padding(end = ARROW_EDGE)) {
        AnimatedVisibility(visible = activity.active && hasNext, enter = enter, exit = exit) {
            PageArrow(Res.drawable.chevron_right_24dp, stringResource(Res.string.media_next), nextHover, onNext)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PageArrow(
    icon: org.jetbrains.compose.resources.DrawableResource,
    label: String,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
) {
    WithTooltip(label) {
        // Tonální plocha: přes světlou fotku by holá ikona zmizela.
        FilledTonalIconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            interactionSource = interactionSource,
        ) {
            Icon(painterResource(icon), contentDescription = label)
        }
    }
}

/** Jak dlouho po posledním pohybu myši šipky zůstanou. */
private const val HIDE_AFTER_MS = 2_000L

/** Odstup šipky od kraje okna. */
private val ARROW_EDGE = 12.dp
