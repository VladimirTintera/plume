package eu.tintera.plume.features.conversation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.settings.DEFAULT_MESSAGE_TEXT_SIZE
import eu.tintera.plume.core.settings.MESSAGE_TEXT_SIZES
import eu.tintera.plume.core.ui.LocalMessageTextSize
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.core.ui.tick
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_text_size
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * **Velikost písma zpráv přímo v konverzaci** — štípnutím nad seznamem a Ctrl (na Macu ⌘)
 * s `+` / `−` / `0`, jako v Google Messages a v prohlížeči. Je to tatáž hodnota jako
 * Nastavení → Vzhled → Velikost písma ([AppSettings.messageTextSize]), jen po ruce.
 *
 * - **Během štípnutí se mění jen [preview]**, uloží se až po puštění: velikost jde z kořene
 *   aplikace statickým localem a každý zápis by překreslil celou aplikaci na každý dílek.
 *   Náhled platí jen pro seznam zpráv téhle konverzace ([LocalMessageTextSize] kolem něj).
 * - **Náhled drží až do příchodu uložené hodnoty** ([settle]): DataStore ji vrátí o pár snímků
 *   později a písmo by mezitím skočilo zpátky.
 */
@Stable
internal class MessageTextZoom(private val settings: AppSettings) {
    /** Velikost, kterou seznam ukazuje místo uložené; `null` = uložená (viz [current]). */
    var preview: Int? by mutableStateOf(null)
        private set

    /** Prst štípe — ukazuje se štítek s velikostí. */
    var pinching: Boolean by mutableStateOf(false)
        private set

    /** Klávesová změna — štítek se ukáže na chvíli i bez prstů. */
    var keyChange: Int by mutableStateOf(0)
        private set

    private var stored = DEFAULT_MESSAGE_TEXT_SIZE

    val current: Int get() = preview ?: stored

    internal fun settle(stored: Int) {
        this.stored = stored
        if (!pinching && preview == stored) preview = null
    }

    internal fun startPinch(): Int {
        pinching = true
        return current
    }

    /** Vrací `true`, když se velikost změnila o dílek (haptika). */
    internal fun pinchTo(size: Int): Boolean {
        if (size == current) return false
        preview = size
        return true
    }

    internal fun endPinch() {
        pinching = false
        commit()
    }

    /** Ctrl `+` / `−` (o bod), Ctrl `0` ([delta] `null`) zpět na výchozí. Ukládá se hned. */
    internal fun step(delta: Int?) {
        val next = if (delta == null) DEFAULT_MESSAGE_TEXT_SIZE else (current + delta).coerceIn(MESSAGE_TEXT_SIZES)
        preview = next
        keyChange++
        commit()
    }

    private fun commit() {
        val size = preview ?: return
        if (size == stored) preview = null else settings.setMessageTextSize(size)
    }
}

@Composable
internal fun rememberMessageTextZoom(): MessageTextZoom {
    val settings = koinInject<AppSettings>()
    val zoom = remember(settings) { MessageTextZoom(settings) }
    val stored = LocalMessageTextSize.current
    // Mimo kompozici: `settle` může náhled zahodit, a zápis stavu za kompozice se nedělá.
    SideEffect { zoom.settle(stored) }
    return zoom
}

/**
 * Velikost po štípnutí: počáteční [start] násobená poměrem vzdálenosti prstů [scale],
 * zaokrouhlená na celé body a přitažená k rozsahu Nastavení. Z výchozích 16 tak vede
 * rozevření o čtvrtinu na největší a stažení o čtvrtinu na nejmenší písmo.
 */
internal fun pinchedTextSize(start: Int, scale: Float): Int =
    (start * scale).roundToInt().coerceIn(MESSAGE_TEXT_SIZES)

/**
 * Krok z klávesnice pro Ctrl / ⌘ + klávesu: `+1` / `-1`, `null` = zpět na výchozí, a pro jinou
 * klávesu [NO_STEP]. Plus jde psát s Shiftem i bez něj (`=`), a z numerické klávesnice.
 */
internal fun textSizeKeyStep(key: Key): Int? = when (key) {
    Key.Equals, Key.Plus, Key.NumPadAdd -> 1
    Key.Minus, Key.NumPadSubtract -> -1
    Key.Zero, Key.NumPad0 -> null
    else -> NO_STEP
}

internal const val NO_STEP = Int.MIN_VALUE

/**
 * Štípnutí nad seznamem zpráv. Čte v průchodu `Initial` (dřív než seznam a bubliny), ale
 * **nic nespotřebuje, dokud se vzdálenost prstů nezmění o víc než `touchSlop`** — jeden prst
 * i dva prsty posouvající seznam fungují jako dřív. Pak bere všechny pohyby až do puštění,
 * takže seznam během štípnutí nejede.
 */
internal fun Modifier.messageTextPinch(zoom: MessageTextZoom, haptics: HapticFeedback): Modifier =
    pointerInput(zoom) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            var startDistance = 0f
            var startSize = 0
            var active = false
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.filter { it.pressed }
                    if (pressed.size < 2) {
                        if (pressed.isEmpty() || active) break
                        startDistance = 0f
                        continue
                    }
                    val distance = (pressed[0].position - pressed[1].position).getDistance()
                    if (startDistance == 0f) {
                        startDistance = distance
                        continue
                    }
                    if (!active) {
                        if (abs(distance - startDistance) < viewConfiguration.touchSlop) continue
                        active = true
                        startSize = zoom.startPinch()
                        // Od teď se počítá od místa, kde štípnutí poznal — bez skoku o slop.
                        startDistance = distance
                    }
                    event.changes.forEach { it.consume() }
                    if (zoom.pinchTo(pinchedTextSize(startSize, distance / startDistance))) haptics.tick()
                }
            } finally {
                if (active) zoom.endPinch()
            }
        }
    }

/** Ctrl / ⌘ + `+` / `−` / `0` kdekoli v konverzaci (i s fokusem ve vstupním poli). */
internal fun Modifier.messageTextZoomKeys(zoom: MessageTextZoom): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown || !(event.isCtrlPressed || event.isMetaPressed)) return@onPreviewKeyEvent false
    val step = textSizeKeyStep(event.key)
    if (step == NO_STEP) return@onPreviewKeyEvent false
    zoom.step(step)
    true
}

/**
 * Štítek s velikostí nahoře nad seznamem, dokud prsty štípou, a chvíli po změně z klávesnice.
 * Hlásí se čtečce obrazovky (`liveRegion`).
 */
@Composable
internal fun BoxScope.MessageTextZoomLabel(zoom: MessageTextZoom, topPadding: Dp) {
    var keyVisible by remember { mutableStateOf(false) }
    LaunchedEffect(zoom.keyChange) {
        if (zoom.keyChange == 0) return@LaunchedEffect
        keyVisible = true
        delay(KEY_LABEL_MS)
        keyVisible = false
    }
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = zoom.pinching || keyVisible,
        enter = fadeIn(motion.defaultEffectsSpec()),
        exit = fadeOut(motion.fastEffectsSpec()),
        modifier = Modifier.align(Alignment.TopCenter).padding(top = topPadding + 16.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ) {
            Text(
                stringResource(Res.string.conversation_text_size, zoom.current),
                style = MaterialTheme.typography.labelLarge.tabularNumbers(),
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

/** Jak dlouho štítek zůstane po změně z klávesnice. */
private const val KEY_LABEL_MS = 1_200L
