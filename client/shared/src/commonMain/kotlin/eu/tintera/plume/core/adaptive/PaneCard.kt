package eu.tintera.plume.core.adaptive

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.ui.LocalPanesSideBySide

/**
 * Co stojí vedle karty (konverzace, sekce Nastavení, placeholder): seznam vlevo a profil
 * vpravo. Poskytuje shell podle počtu panelů; karta podle toho volí okraj a rohy.
 */
@Immutable
data class PaneNeighbours(
    /** Vlevo od karty je seznam — karta od něj odstoupí o [PANE_SPACING]. */
    val listBeside: Boolean = false,
    /** Vpravo od karty je profil — karta s ním tvoří jednu plochu (rovné rohy, předěl). */
    val extraBeside: Boolean = false,
)

val LocalPaneNeighbours = compositionLocalOf { PaneNeighbours() }

/**
 * Panel podle role, když panely stojí vedle sebe ([LocalPanesSideBySide]):
 *
 * - **Seznam leží přímo na ploše okna** (`surfaceContainer`, kreslí ji shell pod scénou), bez
 *   karty. Jeho `surface` a `background` se proto přemapují na tutéž barvu: obrazovky seznamů
 *   (chaty, archiv, sekce Nastavení) si pozadí kreslí samy a jinak by na ploše okna stál
 *   obdélník o tón jinou barvou — tedy zase karta, jen bez rohů. Řádky (`surfaceContainerLowest`)
 *   tak sedí na ploše okna stejně jako segmentovaný seznam v M3.
 * - **Konverzace a profil jsou JEDNA karta** (`surface`, rohy `shapes.large`): u sebe rovné rohy
 *   a mezi sebou jen tenký předěl. Dřív byl každý panel kartou zvlášť a uvnitř seznamu i profilu
 *   byly další karty (řádky, bloky informací) — tři vrstvy zaoblených ploch do sebe a mezi nimi
 *   24 dp prázdna.
 *
 * Mezera mezi panely proto NENÍ v direktivě scaffoldu (`horizontalPartitionSpacerSize = 0`):
 * mezera z knihovny je všude stejná, takže by rozdělila i konverzaci od profilu, a kreslit přes
 * ni nejde (panely mají `clipToBounds`). Okraj si dělá karta sama ([PANE_SPACING] od seznamu)
 * a táhlo stojí na hranici panelů, tedy v té mezeře.
 *
 * Na telefonu obsah jde od kraje ke kraji jako dřív. **Struktura je ve všech případech táž**
 * (téma, `Box`) — kdyby se větvila, přišel by obsah při změně šířky okna o svůj stav.
 */
@Composable
fun PaneCard(
    role: PaneRole,
    /** `NavEntry.contentKey` položky v kartě; hlásit polohu táhlu smí jen ta z [LocalPaneResizeOwners]. */
    entryKey: Any? = null,
    content: @Composable () -> Unit,
) {
    val sideBySide = LocalPanesSideBySide.current
    val neighbours = LocalPaneNeighbours.current
    val flat = sideBySide && role == PaneRole.List
    val card = sideBySide && role != PaneRole.List

    // Rohy u profilu se zarovnají postupně: profil vjíždí animací scaffoldu a skokem rovné rohy
    // by na okamžik trčely do prázdna.
    val corner = if (card) CARD_CORNER else 0.dp
    // Bez překmitu (effects, ne spatial): roh jedoucí k nule by pružinou spadl pod ni a záporné
    // zaoblení `RoundedCornerShape` neunese.
    val endCorner by animateDpAsState(
        if (card && !(role == PaneRole.Detail && neighbours.extraBeside)) CARD_CORNER else 0.dp,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "pane-end-corner",
    )
    val startCorner = if (role == PaneRole.Extra) 0.dp else corner
    val shape = RoundedCornerShape(
        topStart = startCorner,
        bottomStart = startCorner,
        topEnd = endCorner,
        bottomEnd = endCorner,
    )
    val divider = MaterialTheme.colorScheme.outlineVariant

    val base = MaterialTheme.colorScheme
    val scheme = if (flat) {
        remember(base) { base.copy(surface = base.surfaceContainer, background = base.surfaceContainer) }
    } else {
        base
    }
    MaterialExpressiveTheme(colorScheme = scheme) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = if (card && role == PaneRole.Detail && neighbours.listBeside) PANE_SPACING else 0.dp)
                .clip(shape)
                .then(if (card) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier)
                .then(
                    if (card && role == PaneRole.Extra) {
                        Modifier.drawWithContent {
                            drawContent()
                            val x = 0.5.dp.toPx()
                            drawLine(divider, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                        }
                    } else {
                        Modifier
                    },
                ),
        ) {
            content()
            // Kde panel leží — táhlo šířky na předělu kreslí shell nad panely
            // (`PaneHandleOverlay`); tady by ho ořez panelu uřízl. Jen karta zobrazené položky —
            // odjíždějící scéna by táhlo přetáhla k sobě a po zániku smazala (`LocalPaneResizeOwners`).
            val owned = entryKey != null && entryKey in LocalPaneResizeOwners.current
            val resize = when {
                !owned -> null
                card && role == PaneRole.Extra -> LocalProfilePaneResize.current
                flat -> LocalListPaneResize.current
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

/** Zaoblení karty: `shapes.large` M3. */
private val CARD_CORNER: Dp = 16.dp

/**
 * Obalí do [PaneCard] každou položku, která patří do panelu list-detail scény ([paneInfo]).
 * Obrazovky přes celou plochu (prohlížeč, mini aplikace) panelem nejsou a zůstávají bez rohů.
 */
@Composable
fun rememberPaneCardDecorator(): NavEntryDecorator<NavKey> = remember {
    NavEntryDecorator { entry ->
        val info = entry.paneInfo
        if (info == null) {
            entry.Content()
            return@NavEntryDecorator
        }
        PaneCard(info.role, entry.contentKey) {
            val maxWidth = entry.contentMaxWidth
            if (maxWidth == null) entry.Content() else Box(Modifier.centeredColumn(maxWidth)) { entry.Content() }
        }
    }
}
