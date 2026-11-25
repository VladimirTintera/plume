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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.ui.LocalListPage
import eu.tintera.plume.core.ui.LocalPanesSideBySide
import eu.tintera.plume.core.ui.asListPage
import eu.tintera.plume.core.ui.groupRow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

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
 *   **Nahoře jen [PANE_TOP_SPACING]**, ať horní hrana lícuje s pilulkou hledání v seznamu.
 * - **S hlavičkou venku** ([Panes.headerOutside]: konverzace, placeholder, profil a jeho podstránky) leží hlavička na ploše
 *   okna v jedné řadě s pilulkou hledání, karta začíná až pod ní ve výšce prvního řádku seznamu
 *   ([PaneHeader], [HeaderOutsideShape]) a jde bez okraje a dolních rohů až pod spodní kraj okna.
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
    /** Hlavička leží nad kartou ([Panes.headerOutside], [PaneHeader]). */
    headerOutside: Boolean = false,
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
    // Horní inset patří jen panelu, nad kterým leží tlačítka okna (desktop s vlastním titulkem,
    // [LocalWindowControls]); ostatní ho zkonzumují a hlavička jde až k hornímu kraji okna.
    // Na telefonu jde stavová lišta přes celou šířku a patří všem.
    val controlsAbove = windowControlsAbove(
        atStart = !sideBySide || role == PaneRole.List || detail && !neighbours.listBeside,
        atEnd = !sideBySide || extra || detail && !neighbours.extraBeside || role == PaneRole.List && !neighbours.listBeside,
    )
    // S tlačítky okna přes obsah si panely titulek berou celý: hlavičky jdou do řádku s tlačítky
    // a panel pod nimi jim nechá místo ([WindowControlsSpace], [WindowControlsEndSpace]) — na Macu
    // začátek seznamu (i jediného panelu úzkého okna), na Windows konec nejpravějšího panelu.
    val controls = LocalWindowControls.current
    val titleInRow = controls.side != WindowControlsSide.None
    // Tlačítka na fyzické levé straně jsou v LTR na začátku řádku, v RTL na konci.
    val controlsAtStart = (controls.side == WindowControlsSide.Left) != (LocalLayoutDirection.current == LayoutDirection.Rtl)
    val under = titleInRow && controlsAbove
    val clearance = WindowControlsClearance(
        start = if (under && controlsAtStart) controls.width else 0.dp,
        end = if (under && !controlsAtStart) controls.width else 0.dp,
        height = if (under) controls.height else 0.dp,
    )
    val top = when {
        !controlsAbove || titleInRow -> PaneEdgeInsets.Consume
        card -> PaneEdgeInsets.Pad
        else -> PaneEdgeInsets.None
    }
    // Barvy plochy okna pro hlavičku nad kartou — tady jsou ještě barvy aplikace, ne chatu.
    val planeScheme = MaterialTheme.colorScheme
    val header = remember { PaneHeaderHost() }
    // Pás hlavičky ([PaneBand]): boční panel vedle konverzace má hlavičku nad kartou, v jedné
    // řadě s hlavičkou konverzace, a na její konec přidá akce konverzace ([PaneBandActions]).
    // Jen karta zobrazené položky — odjíždějící by akce zdvojila.
    val band = LocalPaneBand.current
    val shown = entryKey != null && entryKey in LocalPaneResizeOwners.current
    val bandActions = band?.actions.takeIf { extra && neighbours.extraCard && shown }
    val detailCardTop = band?.detailCardTop
    var ownTop by remember { mutableFloatStateOf(0f) }
    // Hlavička panelu sahá až k hornímu okraji karty konverzace, ať karty lícují, ať je hlavička
    // konverzace vysoká, jak chce (místo pro tlačítka okna, stavová lišta).
    val bandHeight = if (bandActions != null && detailCardTop != null) {
        with(LocalDensity.current) { (detailCardTop - ownTop).coerceAtLeast(0f).toDp() }
    } else {
        0.dp
    }
    val outside = card && headerOutside
    // Karta s hlavičkou venku jde až pod spodní kraj okna.
    val flushBottom = card && headerOutside
    val spacing = if (card) PANE_SPACING else 0.dp
    // Hlavička nad kartou stojí v jedné řadě s pilulkou hledání: obě začínají u stavové lišty.
    val topSpacing = if (flushBottom) 0.dp else if (card) PANE_TOP_SPACING else 0.dp
    val startSpacing = if (card || detail && neighbours.listBeside) PANE_SPACING else 0.dp
    // Mezeru mezi konverzací a profilem dělá profil, ať je jedna.
    val endSpacing = if (detail && neighbours.extraBeside && neighbours.extraCard) 0.dp else spacing
    val density = LocalDensity.current
    val headerHeight = if (outside) header.height.toFloat() else 0f
    val cornerPx = with(density) { (if (card) CARD_CORNER else 0.dp).toPx() }
    // Karta s hlavičkou venku jde až pod spodní kraj okna — spodní rohy nejsou vidět.
    val bottomCornerPx = if (flushBottom) 0f else cornerPx
    val clipShape = remember(headerHeight, cornerPx, bottomCornerPx) { HeaderOutsideShape(headerHeight, cornerPx, bottomCornerPx, withHeader = true) }
    val surfaceShape = remember(headerHeight, cornerPx, bottomCornerPx) { HeaderOutsideShape(headerHeight, cornerPx, bottomCornerPx, withHeader = false) }

    PageTheme(ownSurface) {
        // Každý bok ve vlastním uzlu: dvě úpravy insetů v jednom řetězci by si při změně scény
        // vyměnily místo a Compose by uzel přepsal jinými insety (viz [edge]).
        Box(Modifier.fillMaxSize().edge(start, WindowInsetsSides.Start)) {
            Box(Modifier.fillMaxSize().edge(end, WindowInsetsSides.End)) {
                // Shora a zdola zvlášť: karta s hlavičkou venku jde dolů až pod navigační lištu
                // (jako v Google Zprávách) a inset si bere vstupní pole samo, jako na telefonu.
                Box(Modifier.fillMaxSize().edge(top, WindowInsetsSides.Top)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .edge(if (card && !flushBottom) PaneEdgeInsets.Pad else PaneEdgeInsets.None, WindowInsetsSides.Bottom)
                            .then(if (extra && band != null) Modifier.onGloballyPositioned { ownTop = it.boundsInWindow().top } else Modifier),
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(start = startSpacing, end = endSpacing, top = topSpacing, bottom = if (flushBottom) 0.dp else spacing)
                                .then(
                                    if (detail && band != null) {
                                        Modifier.onGloballyPositioned { band.detailCardTop = it.boundsInWindow().top + header.height }
                                    } else {
                                        Modifier
                                    },
                                )
                                .clip(clipShape)
                                .then(if (sideBySide && ownSurface) Modifier.background(MaterialTheme.colorScheme.surface, surfaceShape) else Modifier),
                        ) {
                            CompositionLocalProvider(
                                LocalPaneHeaderHost provides header,
                                LocalPaneHeaderOutside provides outside,
                                LocalPanePlaneScheme provides planeScheme,
                                LocalWindowControlsClearance provides clearance,
                                LocalPaneBandActions provides bandActions,
                                LocalPaneHeaderMinHeight provides bandHeight,
                            ) {
                                content()
                            }
                            // Kde panel leží — táhlo šířky na předělu kreslí shell nad panely
                            // (`PaneHandleOverlay`); tady by ho ořez panelu uřízl. Jen karta zobrazené položky —
                            // odjíždějící scéna by táhlo přetáhla k sobě a po zániku smazala (`LocalPaneResizeOwners`).
                            val resize = when {
                                !shown -> null
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
        PaneCard(info.role, entry.contentKey, ownSurface = entry.ownSurface, headerOutside = entry.headerOutside) {
            val maxWidth = entry.contentMaxWidth
            if (maxWidth == null) entry.Content() else Box(Modifier.centeredColumn(maxWidth)) { entry.Content() }
        }
    }
}
