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
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Bubliny na desktopu (skiko scéna bez okna). */
@OptIn(ExperimentalTestApi::class, ExperimentalMaterial3Api::class)
class TooltipTest {

    /** Obsah jen v široké větvi, jako panel vedle seznamu chatů nad prahem dvou panelů. */
    @Composable
    private fun WideOnly(width: Dp, wide: @Composable () -> Unit) {
        Box(Modifier.width(width).height(800.dp)) {
            BoxWithConstraints {
                if (maxWidth >= 744.dp) wide() else Text("Úzké")
            }
        }
    }

    /**
     * Holý M3 `TooltipBox`, jehož kotva s viditelnou bublinou zmizí během měření (zúžení okna pod
     * práh dvou panelů). Do Compose Multiplatform 1.12 to padalo na „RootNodeOwner is already
     * disposed" a bubliny se kvůli tomu kreslily z kořene kompozice; 1.13 to opravuje
     * (compose-multiplatform-core#3566). **Když test začne padat, oprava se ztratila** — viz
     * „Bubliny (tooltip)" v `docs/poznatky/desktop.md`.
     */
    @Test
    fun tooltipRemovedDuringMeasureSurvives() = runComposeUiTest {
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
        onNodeWithText("Bublina").assertExists()
        width = 600.dp
        waitForIdle()
        onNodeWithText("Úzké").assertExists()
    }

    @Test
    fun withTooltipShowsOnHoverAndHidesOnLeave() = runComposeUiTest {
        setContent {
            MaterialTheme {
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

    /**
     * Esc nad zaostřenou kotvou s viditelnou bublinou bublinu schová a projde dál — na desktopu
     * ho scéna převede na Zpět, jen když ho nikdo nespotřeboval. Holý M3 `TooltipBox` ho spotřebuje
     * (`keyboardBehavior`), takže po kliknutí na přepínač bočního panelu se první Esc ztratil.
     * **Když test začne padat, M3 změnilo obsluhu klávesnice bubliny** a `EscapeTransparentTooltipState`
     * je potřeba přepsat — viz „Vlastní scéna Chatů" v `docs/poznatky/navigace.md`.
     */
    @Test
    fun escapeOverTooltipGoesThrough() = runComposeUiTest {
        var escapesBelow = 0
        val focus = FocusRequester()
        setContent {
            MaterialTheme {
                Column(
                    Modifier
                        .padding(start = 200.dp, top = 300.dp)
                        .onKeyEvent { event ->
                            if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) escapesBelow++
                            false
                        },
                ) {
                    WithTooltip("Zavřít panel") {
                        Button(onClick = {}, modifier = Modifier.focusRequester(focus)) { Text("P") }
                    }
                }
            }
        }
        runOnIdle { focus.requestFocus() }
        onNodeWithText("P").assertIsFocused()
        onNodeWithText("P").performMouseInput { moveTo(center) }
        waitForIdle()
        onNodeWithText("Zavřít panel").assertExists()

        onNodeWithText("P").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()

        assertEquals(1, escapesBelow, "Esc nad bublinou musí projít až k předkům (a dál na Zpět)")
        onNodeWithText("Zavřít panel").assertDoesNotExist()
    }
}
