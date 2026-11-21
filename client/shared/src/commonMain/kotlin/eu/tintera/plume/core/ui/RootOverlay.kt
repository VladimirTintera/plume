package eu.tintera.plume.core.ui

import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.currentCompositionLocalContext
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Hostitel překryvů vykreslovaných z **kořene kompozice** ([RootOverlay]). Sedí v `AppTheme`;
 * bez něj se [RootOverlay] vykreslí na místě.
 *
 * Převzato z KMPMlog (`core/ui/components/RootOverlay.kt`), kde se na tuhle chybu přišlo dřív.
 */
@Stable
class RootOverlayHost internal constructor() {
    internal val entries = mutableStateListOf<Entry>()

    internal class Entry(context: CompositionLocalContext, content: @Composable () -> Unit) {
        var context by mutableStateOf(context)
        var content by mutableStateOf(content)
    }
}

val LocalRootOverlayHost = staticCompositionLocalOf<RootOverlayHost?> { null }

/**
 * Poskytne [RootOverlayHost] obsahu a za ním vykreslí zaregistrované překryvy. Musí ležet v **hlavní**
 * kompozici (ne pod `SubcomposeLayout` – `BoxWithConstraints`, `Scaffold`, lazy seznam), jinak by se
 * překryvy zase odebíraly během měření; proto je v `AppTheme` hned pod kořenem.
 */
@Composable
fun RootOverlayHost(content: @Composable () -> Unit) {
    val host = remember { RootOverlayHost() }
    CompositionLocalProvider(LocalRootOverlayHost provides host, content = content)
    host.entries.forEach { entry ->
        key(entry) {
            CompositionLocalProvider(entry.context) { DisableSelection { entry.content() } }
        }
    }
}

/**
 * Vykreslí [content] z kořene kompozice, s composition localy volajícího. Existuje kvůli chybě
 * Compose Multiplatform na skiko (desktop): vrstva překryvu, jejíž hostitel zmizí z kompozice
 * **během měření** (větev `BoxWithConstraints` podle šířky, slot `Scaffold`u, položka lazy seznamu),
 * se zlikviduje uprostřed `CanvasLayersComposeSceneImpl.doMeasureAndLayout`, který ji pak změří –
 * `IllegalArgumentException: RootNodeOwner is already disposed`. Tady volající při odchodu
 * z kompozice jen odhlásí záznam a vrstvu zruší až běžná rekompozice hostitele v dalším snímku,
 * mimo měření.
 *
 * **Jen pro neukotvené překryvy.** `Popup` (menu) se polohuje vůči rodiči, takže z kořene by skončil
 * v rohu okna — a právě to dnešní jediný uživatel chce (snackbar sbaleného seznamu u rohu okna).
 * Pád výš Compose Multiplatform 1.13 opravuje (compose-multiplatform-core#3566); bubliny se proto
 * z kořene už nekreslí (`WithTooltip`), překryv zůstává kvůli poloze vůči oknu.
 *
 * **Výběr textu je v překryvu vypnutý** (`DisableSelection`): mezi localy volajícího je
 * i `LocalSelectionRegistrar` a texty překryvu by se registrovaly do cizího výběru v jiném stromu
 * layoutu.
 */
@Composable
fun RootOverlay(content: @Composable () -> Unit) {
    val host = LocalRootOverlayHost.current
    if (host == null) {
        DisableSelection(content)
        return
    }
    val context = currentCompositionLocalContext
    val entry = remember(host) { RootOverlayHost.Entry(context, content) }
    SideEffect {
        entry.context = context
        entry.content = content
    }
    DisposableEffect(host, entry) {
        host.entries += entry
        onDispose { host.entries -= entry }
    }
}
