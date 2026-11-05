package eu.tintera.plume.core.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.ui.LocalListPage
import eu.tintera.plume.core.ui.LocalPanesSideBySide
import eu.tintera.plume.core.ui.asListPage
import eu.tintera.plume.core.ui.groupRow

/**
 * Co stojí vedle karty (konverzace, placeholder): seznam vlevo. Poskytuje shell podle počtu
 * panelů; karta podle toho volí okraj.
 */
@Immutable
data class PaneNeighbours(
    /** Vlevo od karty je seznam — karta od něj odstoupí o [PANE_SPACING]. */
    val listBeside: Boolean = false,
)

val LocalPaneNeighbours = compositionLocalOf { PaneNeighbours() }

/**
 * Panel podle toho, co v něm je, když panely stojí vedle sebe ([LocalPanesSideBySide]):
 *
 * - **Stránka s bloky řádků leží přímo na ploše okna** (`surfaceContainer`, kreslí ji shell pod
 *   scénou), bez karty: seznam chatů, archiv, Nastavení i profil. Její `surface` a `background`
 *   jsou přemapované ([asListPage]) stejně jako na telefonu, řádky ([groupRow]) z ní vystupují.
 *   Dřív byl každý panel kartou zvlášť a uvnitř seznamu i profilu byly další karty (řádky, bloky
 *   informací) — tři vrstvy zaoblených ploch do sebe a mezi nimi 24 dp prázdna.
 * - **Karta je jen obrazovka s vlastní plochou** ([ownSurface]: konverzace, článek, placeholder).
 *   Má `surface` a rohy `shapes.large` ze všech stran, i vedle profilu.
 *
 * Mezera mezi panely proto NENÍ v direktivě scaffoldu (`horizontalPartitionSpacerSize = 0`):
 * mezera z knihovny je všude stejná a kreslit přes ni nejde (panely mají `clipToBounds`). Okraj
 * si dělá detail sám ([PANE_SPACING] od seznamu) a táhlo stojí na hranici panelů, tedy v té mezeře.
 *
 * Na telefonu obsah jde od kraje ke kraji jako dřív. **Struktura je ve všech případech táž**
 * (téma, `Box`) — kdyby se větvila, přišel by obsah při změně šířky okna o svůj stav.
 */
@Composable
fun PaneCard(
    role: PaneRole,
    /** `NavEntry.contentKey` položky v kartě; hlásit polohu táhlu smí jen ta z [LocalPaneResizeOwners]. */
    entryKey: Any? = null,
    /** Obrazovka si nechává `surface` ([Panes.ownSurface]); vedle jiných panelů je z ní karta. */
    ownSurface: Boolean = false,
    content: @Composable () -> Unit,
) {
    val sideBySide = LocalPanesSideBySide.current
    val neighbours = LocalPaneNeighbours.current
    val card = sideBySide && ownSurface
    val shape = RoundedCornerShape(if (card) CARD_CORNER else 0.dp)

    PageTheme(ownSurface) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = if (sideBySide && role == PaneRole.Detail && neighbours.listBeside) PANE_SPACING else 0.dp)
                .clip(shape)
                .then(if (card) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier),
        ) {
            content()
            // Kde panel leží — táhlo šířky na předělu kreslí shell nad panely
            // (`PaneHandleOverlay`); tady by ho ořez panelu uřízl. Jen karta zobrazené položky —
            // odjíždějící scéna by táhlo přetáhla k sobě a po zániku smazala (`LocalPaneResizeOwners`).
            val owned = entryKey != null && entryKey in LocalPaneResizeOwners.current
            val resize = when {
                !owned -> null
                sideBySide && role == PaneRole.Extra -> LocalProfilePaneResize.current
                sideBySide && role == PaneRole.List -> LocalListPaneResize.current
                else -> null
            }
            if (resize != null) {
                val token = remember { Any() }
                Box(Modifier.matchParentSize().onGloballyPositioned { resize.report(token, it.boundsInWindow()) })
                DisposableEffect(resize) { onDispose { resize.release(token) } }
            }
        }
    }
}

/** Téma obrazovky: stránka s bloky řádků ([asListPage]), nebo vlastní plocha. */
@Composable
private fun PageTheme(ownSurface: Boolean, content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme
    val scheme = if (ownSurface) base else remember(base) { base.asListPage() }
    MaterialExpressiveTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalListPage provides !ownSurface, content = content)
    }
}

/** Zaoblení karty: `shapes.large` M3. */
private val CARD_CORNER: Dp = 16.dp

/**
 * Obalí do [PaneCard] každou položku, která patří do panelu list-detail scény ([paneInfo]).
 * Obrazovky přes celou plochu panelem nejsou a zůstávají bez rohů; i ony jsou ale stránkou
 * s bloky řádků (výběr chatu, nová skupina), pokud nejde o [Panes.ownSurface].
 */
@Composable
fun rememberPaneCardDecorator(): NavEntryDecorator<NavKey> = remember {
    NavEntryDecorator { entry ->
        val info = entry.paneInfo
        if (info == null) {
            PageTheme(entry.ownSurface) { entry.Content() }
            return@NavEntryDecorator
        }
        PaneCard(info.role, entry.contentKey, ownSurface = entry.ownSurface) {
            val maxWidth = entry.contentMaxWidth
            if (maxWidth == null) entry.Content() else Box(Modifier.centeredColumn(maxWidth)) { entry.Content() }
        }
    }
}
