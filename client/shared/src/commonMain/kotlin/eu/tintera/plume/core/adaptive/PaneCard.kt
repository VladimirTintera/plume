package eu.tintera.plume.core.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
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
 * Co stojí vedle karty (konverzace, placeholder): seznam vlevo, profil vpravo. Poskytuje shell
 * podle počtu panelů; karta podle toho volí okraj.
 */
@Immutable
data class PaneNeighbours(
    /** Vlevo od karty je seznam — karta od něj odstoupí o [PANE_SPACING]. */
    val listBeside: Boolean = false,
    /** Vpravo od karty je profil (třetí panel). */
    val extraBeside: Boolean = false,
    /**
     * Třetí panel je karta jako konverzace (Chaty: profil a co se z něj otevírá), ne stránka na
     * ploše okna (podstránka Nastavení vedle sekce, která kartou není).
     */
    val extraCard: Boolean = false,
)

val LocalPaneNeighbours = compositionLocalOf { PaneNeighbours() }

/**
 * Panel podle toho, co v něm je, když panely stojí vedle sebe ([LocalPanesSideBySide]):
 *
 * - **Stránka s bloky řádků leží přímo na ploše okna** (`surfaceContainer`, kreslí ji shell pod
 *   scénou), bez karty: seznam chatů, archiv a Nastavení. Její `surface` a `background` jsou
 *   přemapované ([asListPage]) stejně jako na telefonu, řádky ([groupRow]) z ní vystupují.
 *   Dřív byl každý panel kartou zvlášť a uvnitř seznamu i profilu byly další karty (řádky, bloky
 *   informací) — tři vrstvy zaoblených ploch do sebe a mezi nimi 24 dp prázdna.
 * - **Karta je obrazovka s vlastní plochou** ([ownSurface]: konverzace, článek, placeholder)
 *   **a profil vedle ní** ([PaneNeighbours.extraCard]). Konverzace má `surface`, profil žádnou
 *   plochu navíc nekreslí: v barvách aplikace je jeho stránka táž jako plocha okna a karta není
 *   vidět, v barvě chatu (`ChatColorTheme`) je z něj zaoblená karta v odstínu chatu. Dřív stál
 *   profil přes celou výšku bez rohů a v barvě chatu z něj byl obdélník u kraje okna.
 * - **Karta má okraj [PANE_SPACING] ze všech stran**, i od kraje okna, a rohy `shapes.large`.
 *   Okraj jen od seznamu nechával kartu narážet nahoru a dolů a zaoblený roh u spodního kraje
 *   okna vypadal jako chyba. Mezi konverzací a profilem je mezera jedna (dělá ji profil).
 *
 * Mezera mezi panely proto NENÍ v direktivě scaffoldu (`horizontalPartitionSpacerSize = 0`):
 * mezera z knihovny je všude stejná a kreslit přes ni nejde (panely mají `clipToBounds`). Táhla
 * stojí uprostřed mezer.
 *
 * Na telefonu obsah jde od kraje ke kraji jako dřív. **Struktura je ve všech případech táž**
 * (téma, `Box`y) — kdyby se větvila, přišel by obsah při změně šířky okna o svůj stav.
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
    val card = sideBySide && (ownSurface || role == PaneRole.Extra && neighbours.extraCard)
    val detail = sideBySide && role == PaneRole.Detail
    val extra = sideBySide && role == PaneRole.Extra

    // Boky u kraje okna karta odsadí o jeho inset, ostatní inset zkonzumuje (viz [edge]).
    val start = when {
        detail && neighbours.listBeside || extra -> PaneEdgeInsets.Consume
        card -> PaneEdgeInsets.Pad
        else -> PaneEdgeInsets.None
    }
    val end = when {
        detail && neighbours.extraBeside -> PaneEdgeInsets.Consume
        card -> PaneEdgeInsets.Pad
        else -> PaneEdgeInsets.None
    }
    val spacing = if (card) PANE_SPACING else 0.dp
    val startSpacing = if (card || detail && neighbours.listBeside) PANE_SPACING else 0.dp
    // Mezeru mezi konverzací a profilem dělá profil, ať je jedna.
    val endSpacing = if (detail && neighbours.extraBeside && neighbours.extraCard) 0.dp else spacing
    val shape = RoundedCornerShape(if (card) CARD_CORNER else 0.dp)

    PageTheme(ownSurface) {
        // Každý bok ve vlastním uzlu: dvě úpravy insetů v jednom řetězci by si při změně scény
        // vyměnily místo a Compose by uzel přepsal jinými insety (viz [edge]).
        Box(Modifier.fillMaxSize().edge(start, WindowInsetsSides.Start)) {
            Box(Modifier.fillMaxSize().edge(end, WindowInsetsSides.End)) {
                Box(Modifier.fillMaxSize().edge(if (card) PaneEdgeInsets.Pad else PaneEdgeInsets.None, WindowInsetsSides.Vertical)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(start = startSpacing, end = endSpacing, top = spacing, bottom = spacing)
                            .clip(shape)
                            .then(if (sideBySide && ownSurface) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier),
                    ) {
                        content()
                        // Kde panel leží — táhlo šířky na předělu kreslí shell nad panely
                        // (`PaneHandleOverlay`); tady by ho ořez panelu uřízl. Jen karta zobrazené položky —
                        // odjíždějící scéna by táhlo přetáhla k sobě a po zániku smazala (`LocalPaneResizeOwners`).
                        val owned = entryKey != null && entryKey in LocalPaneResizeOwners.current
                        val resize = when {
                            !owned -> null
                            extra -> LocalProfilePaneResize.current
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
        }
    }
}

/** Co panel udělá s insetem na jednom boku. */
private enum class PaneEdgeInsets {
    /** Nic — obsah si inset vezme sám (telefon, stránka u kraje okna). */
    None,

    /**
     * Zkonzumuje: panel u toho okraje obrazovky nestojí. Insety nevědí, kde prvek leží:
     * konverzace vpravo od seznamu dostávala naležato výřez kamery z LEVÉHO kraje obrazovky
     * a hlavička, zprávy i pole od něj odskočily o jeho šířku. Seznam si pravý bok bere sám
     * (`listPaneInsets` v shellu).
     */
    Consume,

    /**
     * Odsadí o něj kartu: karta u kraje okna nesmí zajet pod systémové lišty, jinak by její rohy
     * ležely pod stavovou lištou. Klávesnice do něj nepatří — karta za ni zajede a vstupní pole
     * nad ni posune konverzace sama, jako bez karty.
     */
    Pad,
}

/**
 * Inset na boku [sides] podle [insets]. Každý druh je jiný modifikátor a podmínka ho celý přidá,
 * odebere nebo vymění; hodnota uvnitř je pro tentýž druh pořád tatáž. Změněné insety u uzlu,
 * který NavDisplay zrovna přesouvá do jiné scény, shodí aplikaci (Gotchas v CLAUDE.md).
 */
@Composable
private fun Modifier.edge(insets: PaneEdgeInsets, sides: WindowInsetsSides): Modifier = when (insets) {
    PaneEdgeInsets.None -> this
    PaneEdgeInsets.Consume -> consumeWindowInsets(WindowInsets.safeDrawing.only(sides))
    PaneEdgeInsets.Pad -> windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout).only(sides))
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
