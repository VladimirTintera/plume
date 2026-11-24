package eu.tintera.plume.core.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * Hlavička nad kartou ([Panes.headerOutside]): karta jí sem hlásí výšku a ořízne se až pod ní.
 * Jen vedle jiných panelů ([LocalPaneHeaderOutside]); jinak je hlavička obyčejná.
 */
@Stable
class PaneHeaderHost internal constructor() {
    /** Výška hlavičky v px; 0 = obrazovka hlavičku nemá a karta je celá. */
    var height by mutableIntStateOf(0)
        internal set
}

/**
 * Karta, které hlavička hlásí výšku — i když zrovna leží uvnitř ní (telefon): po rozložení okna
 * se velikost hlavičky nemusí změnit a `onSizeChanged` by se znovu neozval.
 */
internal val LocalPaneHeaderHost = staticCompositionLocalOf<PaneHeaderHost?> { null }

/** Hlavička leží nad kartou (vedle jiných panelů, [Panes.headerOutside]). */
internal val LocalPaneHeaderOutside = compositionLocalOf { false }

/**
 * Barvy plochy okna, na které hlavička leží — barva chatu (`ChatColorTheme`) končí u karty.
 * Zvlášť od [PaneHeaderHost], ne jeho parametr: při přechodu motivu je schéma v každém snímku
 * nové a nový držák by zapomněl výšku (`onSizeChanged` se při stejné velikosti znovu neozve) —
 * karta pak na dobu přechodu překryla hlavičku.
 */
internal val LocalPanePlaneScheme = compositionLocalOf<ColorScheme?> { null }

/** Lišta v hlavičce nemá vlastní plochu: leží na ploše okna, nebo na skle ([PaneHeader]). */
private val LocalInPaneHeader = staticCompositionLocalOf { false }

/**
 * Obal hlavičky obrazovky s [Panes.headerOutside]. Vedle jiných panelů leží hlavička na ploše
 * okna: průhledná ([paneHeaderContainerColor]), v barvách aplikace, a karta pod ní začíná.
 *
 * [glass] je plocha hlavičky, když leží v kartě (jeden panel, obrazovka je celá konverzace):
 * obsah pod ní zajíždí a lišta je průhledná, aby ho sklo ukázalo. Nad kartou se nepoužije — tam
 * pod hlavičkou nic nejezdí.
 *
 * **Struktura je pokaždé táž** (téma a `Box`), jen hodnoty se mění: při změně šířky okna by jinak
 * hlavička přišla o stav (rozepsané hledání v chatu).
 */
@Composable
fun PaneHeader(glass: Modifier? = null, content: @Composable () -> Unit) {
    val host = LocalPaneHeaderHost.current
    val outside = LocalPaneHeaderOutside.current
    val shownGlass = glass?.takeIf { !outside }
    val scheme = LocalPanePlaneScheme.current.takeIf { outside } ?: MaterialTheme.colorScheme
    // Obrazovka odešla, karta zůstává (jiná položka v ní není, ale pro jistotu): celá karta.
    DisposableEffect(host) { onDispose { host?.height = 0 } }
    MaterialExpressiveTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalInPaneHeader provides (outside || shownGlass != null),
            LocalContentColor provides scheme.onSurface,
        ) {
            Box(
                Modifier
                    .then(shownGlass ?: Modifier)
                    .onSizeChanged { host?.height = it.height }
                    .padding(bottom = if (outside) PANE_HEADER_GAP else 0.dp),
            ) { content() }
        }
    }
}

/**
 * Mezera mezi hlavičkou a kartou: karta tak začíná ve stejné výšce jako první řádek seznamu chatů
 * vedle (`ChatListScreen` odsadí seznam pod lištou hledání o 8 dp; lišta i hlavička mají 64 dp).
 * Patří k hlavičce, ne ke kartě: obsah Scaffoldu pak začíná až pod mezerou a nic se pod ní neschová.
 */
internal val PANE_HEADER_GAP: Dp = 8.dp

/** Plocha hlavičky: nad kartou a na skle průhledná (je vidět plocha okna, sklo), jinak `surface`. */
@Composable
@ReadOnlyComposable
fun paneHeaderContainerColor(): Color =
    if (LocalInPaneHeader.current) Color.Transparent else MaterialTheme.colorScheme.surface

/**
 * Plocha Scaffoldu obrazovky, která je stránkou s bloky řádků (profil a jeho podstránky): s hlavičkou
 * nad kartou průhledná, plochu dostane až obsah pod hlavičkou ([PaneBody]).
 */
@Composable
@ReadOnlyComposable
fun paneScaffoldColor(color: Color = MaterialTheme.colorScheme.background): Color =
    if (LocalPaneHeaderOutside.current) Color.Transparent else color

/**
 * Obsah Scaffoldu s hlavičkou nad kartou: začíná pod hlavičkou a má plochu stránky [color], takže
 * pod průhlednou hlavičkou nic neroluje (kartu ořezává `PaneCard`). Bez hlavičky venku je to jen
 * `Box` a [content] dostane [padding] beze změny — struktura je táž, ať obsah při změně šířky okna
 * nepřijde o stav.
 */
@Composable
fun PaneBody(
    padding: PaddingValues,
    color: Color = MaterialTheme.colorScheme.background,
    content: @Composable (PaddingValues) -> Unit,
) {
    val outside = LocalPaneHeaderOutside.current
    val direction = LocalLayoutDirection.current
    val top = if (outside) padding.calculateTopPadding() else 0.dp
    Box(
        Modifier
            .fillMaxSize()
            .padding(top = top)
            .then(if (outside) Modifier.background(color) else Modifier),
    ) {
        val inner = if (outside) {
            PaddingValues(
                start = padding.calculateStartPadding(direction),
                end = padding.calculateEndPadding(direction),
                bottom = padding.calculateBottomPadding(),
            )
        } else {
            padding
        }
        content(inner)
    }
}

/** Obrazovka má hlavičku nad kartou — plochu pod obsahem si kreslí jen pod ní. */
@Composable
@ReadOnlyComposable
fun paneHeaderOutside(): Boolean = LocalPaneHeaderOutside.current

/**
 * Ořez karty s hlavičkou venku: pruh hlavičky beze změny, pod ním zaoblený obdélník. Hlavička
 * tak smí kreslit (vlnka stisku, pilulka hledání) přes celou šířku a karta má rohy až pod ní.
 */
internal class HeaderOutsideShape(
    private val headerHeight: Float,
    private val corner: Float,
    /** Zaoblení spodních rohů; 0, když karta jde až pod spodní kraj okna. */
    private val bottomCorner: Float,
    /** Ořez (s pruhem hlavičky), nebo jen plocha karty (bez něj). */
    private val withHeader: Boolean,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val top = CornerRadius(corner)
        val bottom = CornerRadius(bottomCorner)
        path.addRoundRect(RoundRect(Rect(0f, headerHeight, size.width, size.height), top, top, bottom, bottom))
        if (withHeader) path.addRect(Rect(0f, 0f, size.width, headerHeight))
        return Outline.Generic(path)
    }
}
