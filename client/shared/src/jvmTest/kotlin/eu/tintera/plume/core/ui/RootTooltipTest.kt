package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TooltipState
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Viditelná bublina, jejíž kotva zmizí z kompozice **během měření** (větev `BoxWithConstraints`
 * podle šířky — dva panely ↔ jeden), shodí na skiko vykreslování na `RootNodeOwner is already
 * disposed`. [RootTooltipBox] proto kreslí bublinu z kořene ([RootOverlay]).
 *
 * [rawTooltipRemovedDuringMeasureCrashes] dokládá chybu v Compose Multiplatform: **až začne
 * procházet, upstream ji opravil** a obal i kontrola `verifyTooltipsThroughRoot` můžou pryč.
 */
@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
class RootTooltipTest {

    /** Obsah jen v široké větvi, jako panel vedle seznamu chatů nad prahem dvou panelů. */
    @Composable
    private fun WideOnly(width: Dp, wide: @Composable () -> Unit) {
        Box(Modifier.width(width).height(800.dp)) {
            BoxWithConstraints {
                if (maxWidth >= 744.dp) wide() else Text("Úzké")
            }
        }
    }

    @Composable
    private fun Host(content: @Composable () -> Unit) {
        MaterialTheme { RootOverlayHost(content) }
    }

    /**
     * Holý M3 `TooltipBox`, jehož kotva zmizí během měření. Do Compose Multiplatform 1.12 to
     * padalo na „RootNodeOwner is already disposed" (proto `RootTooltipBox`); 1.13.0-rc01 to
     * opravuje (compose-multiplatform-core#3566). Kdyby test znovu padal, oprava se ztratila
     * a obal z kořene je zase nutnost, ne jen pojistka.
     */
    @Test
    fun rawTooltipRemovedDuringMeasureSurvives() = runComposeUiTest {
        var width by mutableStateOf(1000.dp)
        lateinit var state: TooltipState
        lateinit var scope: CoroutineScope
        setContent {
            scope = rememberCoroutineScope()
            WideOnly(width) {
                state = rememberTooltipState(isPersistent = true)
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = { PlainTooltip { Text("Bublina") } },
                    state = state,
                ) { Text("Kotva") }
            }
        }
        waitForIdle()
        scope.launch { state.show() }
        waitForIdle()
        width = 600.dp
        waitForIdle()
        onNodeWithText("Úzké").assertExists()
    }

    /** Nápověda gest: `RichTooltip` s akcí, trvalý stav řízený programem, gesta kotvy vypnutá. */
    @Composable
    private fun GestureHint(state: TooltipState, position: TooltipAnchorPosition = TooltipAnchorPosition.Above) {
        RootTooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(position),
            tooltip = {
                RichTooltip(
                    title = { Text("Nápověda") },
                    action = { TextButton(onClick = { state.dismiss() }) { Text("Rozumím") } },
                    caretShape = TooltipDefaults.caretShape(),
                ) { Text("Podrž tlačítko") }
            },
            state = state,
            enableUserInput = false,
            hasAction = true,
        ) {
            Button(onClick = {}) { Text("Kotva") }
        }
    }

    @Test
    fun rootTooltipSurvivesRemovalDuringMeasure() = runComposeUiTest {
        var width by mutableStateOf(1000.dp)
        lateinit var state: TooltipState
        lateinit var scope: CoroutineScope
        setContent {
            Host {
                scope = rememberCoroutineScope()
                WideOnly(width) {
                    state = rememberTooltipState(isPersistent = true)
                    Column(Modifier.padding(start = 200.dp, top = 300.dp)) { GestureHint(state) }
                }
            }
        }
        waitForIdle()
        scope.launch { state.show() }
        waitForIdle()
        onNodeWithText("Nápověda").assertExists()
        width = 600.dp
        waitForIdle()
        onNodeWithText("Úzké").assertExists()
        onNodeWithText("Nápověda").assertDoesNotExist()
    }

    @Test
    fun richTooltipSitsAtItsAnchor() = runComposeUiTest {
        lateinit var above: TooltipState
        lateinit var start: TooltipState
        lateinit var scope: CoroutineScope
        setContent {
            Host {
                scope = rememberCoroutineScope()
                above = rememberTooltipState(isPersistent = true)
                start = rememberTooltipState(isPersistent = true)
                // Odsazení, ať je poznat, že bublina z kořene sedí u kotvy, a ne v rohu okna.
                Column(Modifier.padding(start = 400.dp, top = 300.dp)) {
                    GestureHint(above)
                    Box(Modifier.padding(top = 200.dp)) { GestureHint(start, TooltipAnchorPosition.Start) }
                }
            }
        }
        waitForIdle()
        scope.launch { above.show() }
        waitForIdle()
        val anchors = onAllNodesWithText("Kotva")
        val first = anchors[0].fetchSemanticsNode().boundsInWindow
        var bubble = onNodeWithText("Nápověda").fetchSemanticsNode().boundsInWindow
        assertTrue(bubble.bottom <= first.top, "bublina nad kotvou: $bubble vs $first")
        assertTrue(bubble.left < first.right && bubble.right > first.left, "bublina u kotvy: $bubble vs $first")

        // Bublina z boku: šipku i stranu si M3 bere z typu poskytovatele a ze souřadnic kotvy.
        scope.launch { above.dismiss() }
        waitForIdle()
        scope.launch { start.show() }
        waitForIdle()
        val second = anchors[1].fetchSemanticsNode().boundsInWindow
        bubble = onNodeWithText("Nápověda").fetchSemanticsNode().boundsInWindow
        assertTrue(bubble.right <= second.left, "bublina vlevo od kotvy: $bubble vs $second")
    }

    @Test
    fun anchorStaysClickableUnderVisibleBubble() = runComposeUiTest {
        var clicks = 0
        lateinit var state: TooltipState
        lateinit var scope: CoroutineScope
        setContent {
            Host {
                scope = rememberCoroutineScope()
                state = rememberTooltipState(isPersistent = true)
                Column(Modifier.padding(start = 200.dp, top = 300.dp)) {
                    RootTooltipBox(
                        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                        tooltip = { PlainTooltip { Text("Bublina") } },
                        state = state,
                        enableUserInput = false,
                    ) {
                        Button(onClick = { clicks++ }) { Text("Kotva") }
                    }
                }
            }
        }
        waitForIdle()
        scope.launch { state.show() }
        waitForIdle()
        onNodeWithText("Bublina").assertExists()
        onNodeWithText("Kotva").performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun withTooltipShowsOnHoverAndHidesOnLeave() = runComposeUiTest {
        setContent {
            Host {
                Column(Modifier.padding(start = 200.dp, top = 300.dp)) {
                    WithTooltip("Tučně") { Button(onClick = {}) { Text("B") } }
                }
            }
        }
        onNodeWithText("Tučně").assertDoesNotExist()
        onNodeWithText("B").performMouseInput { moveTo(center) }
        waitForIdle()
        val anchor = onNodeWithText("B").fetchSemanticsNode().boundsInWindow
        val bubble = onNodeWithText("Tučně").fetchSemanticsNode().boundsInWindow
        assertTrue(bubble.bottom <= anchor.top, "bublina nad kotvou: $bubble vs $anchor")
        onNodeWithText("B").performMouseInput { moveTo(Offset(-50f, -50f)) }
        waitForIdle()
        onNodeWithText("Tučně").assertDoesNotExist()
    }
}
