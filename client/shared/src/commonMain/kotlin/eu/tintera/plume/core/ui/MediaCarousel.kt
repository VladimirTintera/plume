package eu.tintera.plume.core.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.max
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.abs

/**
 * Stránka carouselu, jak ji vidí obsah: kde leží a jak se má chovat vůči sousedům.
 *
 * Hodnoty, které se mění s tahem ([cardBias], [captionAlpha]), jsou funkce čtené až při
 * umístění a kreslení — posun pageru nic nerekomponuje. Předávají se rovnou do [MediaPageLayout].
 */
class MediaCarouselPage internal constructor(
    val index: Int,
    /** Stránka, na které pager stojí. */
    val isCurrent: Boolean,
    /**
     * Kam se karta ve stránce srovná (−1 vlevo, 0 na střed, 1 vpravo). Karta užší než stránka
     * (fotka na výšku, široký displej) by vycentrovaná ležela daleko od okraje a soused by nebyl
     * vidět vůbec — sousední karta se proto přisouvá k okraji blíž aktuální stránce, plynule
     * s tahem, takže uprostřed obrazovky stojí vždy na středu.
     */
    val cardBias: () -> Float,
    /** Popisek sousední stránky mizí, jak stránka odjíždí ze středu — u okraje jen ruší. */
    val captionAlpha: () -> Float,
)

/**
 * Snímky vedle sebe na rozostřeném pozadí: prohlížeč médií i potvrzení sdílení do chatu.
 *
 * **Jedna komponenta pro obojí, žádná kopie.** Dřív měl Direct Share vlastní pager přes celou
 * šířku a vlastní prolínané pozadí; na prvním doladění prohlížeče (sousedé u okrajů, strop šířky)
 * se to rozešlo a sdílení vypadalo jako jiná aplikace. Obrazovka dodá jen obsah stránky
 * ([page], většinou [MediaPageLayout] s kartou) a pozadí stránky ([backdrop]); lišty, popisek,
 * pole a akce kreslí nad carouselem sama.
 *
 * Co carousel dělá:
 * - **sousední snímky vykukují u okrajů** (`contentPadding`), na širokém displeji má stránka
 *   strop [MAX_PAGE_WIDTH] a sousedé dostanou zbytek,
 * - **rozostřené pozadí jezdí s tahem pod prstem** — kreslí se pozadí aktuální stránky i sousedů
 *   vedle sebe, posunutá o průběh pageru; šev mezi nimi změkčí rozostření samo,
 * - [immersion] (0 = galerie, 1 = celá obrazovka) sousedy i pozadí plynule schová — používá ho
 *   prohlížeč, sdílení ho nechává na nule,
 * - s [animateArrivals] stránka, která PŘIBYLA v datech, přijede ze své strany (viz
 *   [PageArrivals]) — prohlížeč ukáže otevřenou fotku hned a sousedy dotahuje až pod ní.
 *
 * [pageKey] a počet stránek v [pagerState] musí číst TÝŽ stav (viz `MediaViewerScreen`): pager
 * leží v `BoxWithConstraints` a jeho klíče se obnovují až v subkompozici.
 */
@Composable
fun MediaCarousel(
    pagerState: PagerState,
    pageKey: (index: Int) -> Any,
    modifier: Modifier = Modifier,
    immersion: () -> Float = { 0f },
    /** Modifikátor jen pro pozadí — prohlížeč ho s ním zprůhledňuje při zavíracím tahu. */
    backdropModifier: Modifier = Modifier,
    animateArrivals: Boolean = false,
    /** Pozadí stránky přes celou plochu (malý náhled stačí), nebo nic. */
    backdrop: @Composable (index: Int) -> Unit,
    page: @Composable (MediaCarouselPage) -> Unit,
) {
    BoxWithConstraints(modifier) {
        MediaBackdrop(pagerState, pageKey, immersion, backdrop, Modifier.fillMaxSize().then(backdropModifier))

        // Na širokém displeji má stránka strop, jinak by sousedé leželi daleko za okrajem.
        val galleryInset = max(PAGE_INSET, (maxWidth - MAX_PAGE_WIDTH) / 2)
        // Na širokém displeji je kolem karty místa dost a těsná mezera by z ní dělala stěnu.
        val galleryGap = if (galleryInset > PAGE_INSET) WIDE_PAGE_GAP else PAGE_GAP
        /*
         * Na celé obrazovce má stránka celou šířku. Pager bere odsazení jen jako hodnotu, takže
         * se tady čte v kompozici — přechod mezi režimy tím rekomponuje, ale jen po dobu animace.
         */
        val progress = immersion()
        val pageInset = lerp(galleryInset, 0.dp, progress)
        val pageGap = lerp(galleryGap, IMMERSIVE_PAGE_GAP, progress)

        val arrivals = remember { PageArrivals() }
        if (animateArrivals) {
            // Stránky se skládají až v layoutu, tedy po SideEffectu: vidí už nový stav dat.
            val keys = (0 until pagerState.pageCount).mapTo(HashSet()) { pageKey(it) }
            SideEffect { arrivals.onKeys(keys) }
            LaunchedEffect(keys) {
                // Příchod patří jen stránkám složeným se změnou dat; co se složí později při
                // listování, už je „známé" — jinak by přijížděla každá další fotka.
                withFrameNanos {}
                withFrameNanos {}
                arrivals.settle()
            }
        }
        val arrivalSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

        HorizontalPager(
            state = pagerState,
            key = pageKey,
            // Sousední stránky vykukují u okrajů.
            contentPadding = PaddingValues(horizontal = pageInset),
            pageSpacing = pageGap,
            // Soused se skládá dopředu, ať při listování naskočí hned i s už staženým náhledem.
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            val arrival = remember {
                Animatable(if (animateArrivals && arrivals.isNew(pageKey(index))) 0f else 1f)
            }
            LaunchedEffect(arrival) { if (arrival.value < 1f) arrival.animateTo(1f, arrivalSpec) }
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val p = arrival.value
                    if (p < 1f) {
                        alpha = p.coerceIn(0f, 1f)
                        // Přijíždí ze strany, na které leží. Čte se až tady: po rozšíření dat
                        // přesune pager `currentPage` na otevřenou fotku až v layoutu.
                        // Posun je přesně to, o co soused vykukuje: vyjíždí od okraje obrazovky.
                        // O kus šířky stránky (první pokus) startoval hluboko za okrajem, vidět
                        // byl jen konec pohybu a vypadalo to jako pouhé prolnutí.
                        val side = if (index < pagerState.currentPage) -1f else 1f
                        translationX = side * (1f - p) * (pageInset - pageGap).toPx()
                    }
                },
                propagateMinConstraints = true,
            ) {
                page(
                    MediaCarouselPage(
                        index = index,
                        isCurrent = index == pagerState.currentPage,
                        // Na celé obrazovce se nepřisouvá: sousedé vidět nemají.
                        cardBias = {
                            (-pagerState.getOffsetDistanceInPages(index)).coerceIn(-1f, 1f) * (1f - immersion())
                        },
                        captionAlpha = { 1f - abs(pagerState.getOffsetDistanceInPages(index)).coerceIn(0f, 1f) },
                    ),
                )
            }
        }
    }
}

/**
 * Které stránky přibyly v datech, a mají proto přijet, místo aby naskočily.
 *
 * Nestačí „stránka se skládá poprvé": pager skládá stránky i při listování (soused dopředu)
 * a ty by pak přijížděly při každém švihnutí. Přijede jen stránka, jejíž klíč v PŘEDCHOZÍM
 * stavu dat nebyl a která se složila hned se změnou; po pár snímcích ([settle]) se nový stav
 * bere jako známý. První stav dat se neanimuje nikdy — to je otevření obrazovky.
 */
private class PageArrivals {
    private var known: Set<Any>? = null
    private var previous: Set<Any>? = null

    fun onKeys(keys: Set<Any>) {
        if (keys == known) return
        previous = known
        known = keys
    }

    fun settle() {
        previous = known
    }

    fun isNew(key: Any): Boolean = previous?.let { key !in it } == true
}

/**
 * Rozostřený snímek pod kartou.
 *
 * Kreslí se pozadí aktuální stránky i obou sousedů vedle sebe, posunutá o průběh pageru (čteno
 * až v `graphicsLayer`). Dokud se pozadí jen prolínalo podle `currentPage`, měnilo se skokem až
 * po přeskočení na další stránku, zatímco karta jela pod prstem.
 *
 * Styl rozostření je [ambientBlurStyle] — i s náhradou pro Android 11 a starší, kde se kreslí
 * jen tmavý závoj.
 */
@Composable
private fun MediaBackdrop(
    pagerState: PagerState,
    pageKey: (index: Int) -> Any,
    immersion: () -> Float,
    backdrop: @Composable (index: Int) -> Unit,
    modifier: Modifier,
) {
    val hazeState = rememberHazeState()
    val style = ambientBlurStyle()
    val current = pagerState.currentPage

    // Ořez je nutný: pozadí sousedů leží posunuté mimo plochu a prosvítalo by nerozostřené
    // (rozostření pokrývá jen plochu carouselu).
    Box(modifier.clipToBounds().background(Color.Black)) {
        // Zmizelé pozadí se nekreslí vůbec — rozostření by jinak běželo pod černou dál.
        if (immersion() >= 1f) return@Box
        Box(Modifier.fillMaxSize().hazeSource(hazeState).graphicsLayer { alpha = 1f - immersion() }) {
            for (index in (current - 1)..(current + 1)) {
                if (index !in 0 until pagerState.pageCount) continue
                key(pageKey(index)) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = (index - pagerState.currentPage - pagerState.currentPageOffsetFraction) * size.width
                            },
                    ) {
                        backdrop(index)
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1f - immersion() }
                .hazeBlur(input = HazeInput.Sources(hazeState), style = style),
        )
    }
}

/**
 * Vzduch mezi kartou a lištami nad a pod ní. Obrazovka ho přičítá k místu, které lištám
 * vyhrazuje v [MediaPageLayout] — stejně v prohlížeči i ve sdílení.
 */
val MEDIA_PAGE_VERTICAL_GAP = 8.dp

/**
 * Od kraje obrazovky ke kartě; sousední stránka vykukuje o [PAGE_INSET] − [PAGE_GAP] (24 dp).
 * Dost na to, aby bylo vidět, že vedle jsou další fotky; mezera mezi kartami jen oddělí.
 */
private val PAGE_INSET = 32.dp
private val PAGE_GAP = 8.dp

/** Nejširší stránka. Širší displej (rozložený Fold, tablet) dostane po stranách víc souseda. */
private val MAX_PAGE_WIDTH = 640.dp

/** Mezera mezi kartami, když je stránka zastropovaná a místa je dost. */
private val WIDE_PAGE_GAP = 24.dp

/** Na celé obrazovce mezi fotkami při listování — jen černý pruh, aby na sebe nenalepily. */
private val IMMERSIVE_PAGE_GAP = 16.dp
