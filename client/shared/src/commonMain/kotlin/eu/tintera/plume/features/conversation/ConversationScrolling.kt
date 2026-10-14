package eu.tintera.plume.features.conversation

import androidx.compose.ui.input.key.key
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.dp

/**
 * Odroluje tak, aby položka seděla u horní hrany, nebo na středu.
 *
 * `scrollToItem` v obráceném seznamu posadí položku ke SPODNÍ hraně, protože tam je jeho začátek.
 * U skoku na nepřečtené je to přesně naopak, než je potřeba: první nepřečtená má být nahoře
 * a nepřečtené pod ní. U citace zase patří na střed, aby bylo vidět, na co navazuje. Dorovnání se
 * počítá až ze **změřeného** layoutu, protože výška bubliny se zprávu od zprávy liší.
 *
 * Počítá se s **odsazením obsahu**. Dole je pod ním vstupní pole, takže viditelná plocha začíná
 * nad ním; dřív se horní hrana brala z celé výšky viewportu a položka zajela o výšku pole pod
 * horní okraj.
 */
internal suspend fun LazyListState.scrollToAnchor(index: Int, anchor: ScrollAnchor) {
    if (anchor == ScrollAnchor.Bottom) {
        scrollToItem(0)
        return
    }
    scrollToItem(index)

    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    // Offset roste od začátku seznamu, tedy v obráceném rozvržení odspodu nahoru. Nula je horní
    // hrana spodního odsazení, `visibleHeight` spodní hrana horního.
    val visibleHeight = info.viewportEndOffset - info.afterContentPadding
    val desired = when (anchor) {
        ScrollAnchor.Top -> visibleHeight - item.size
        ScrollAnchor.Center -> (visibleHeight - item.size) / 2
        ScrollAnchor.Bottom -> 0
    }.coerceAtLeast(0)
    scrollBy((item.offset - desired).toFloat())
}

/** O kolik položek je [index] mimo viditelnou část seznamu; 0 = je vidět. */
internal fun LazyListState.itemsAwayFrom(index: Int): Int {
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return Int.MAX_VALUE
    return when {
        index < visible.first().index -> visible.first().index - index
        index > visible.last().index -> index - visible.last().index
        else -> 0
    }
}

/** Jak daleko seznam při přechodu odjede, než zmizí. */
internal val JUMP_LEAVE_DISTANCE = 96.dp

/** Z jaké vzdálenosti k nalezené zprávě sjedou sousedé (nejbližší; vzdálenější z dál). */
internal val JUMP_GATHER_DISTANCE = 56.dp

/**
 * Jako [scrollToAnchor], ale seznam k položce DOJEDE — u skoku na citaci a mezi výsledky
 * hledání je jinak vidět jen okamžité přeskočení a ztratí se, kterým směrem a jak daleko zpráva je.
 *
 * Položka mimo obrazovku nemá změřenou polohu, takže se napřed dojede na odhad z průměrné výšky
 * viditelných položek a teprve pak se poloha doladí podle skutečného layoutu. Daleko
 * ([MAX_ANIMATED_JUMP_ITEMS]) se nedojíždí: rolování přes desítky zpráv by trvalo a nic by
 * neukázalo, a navíc by skládalo všechno mezi.
 */
internal suspend fun LazyListState.animateToAnchor(
    index: Int,
    anchor: ScrollAnchor,
    spec: AnimationSpec<Float>,
    /** Index cíle v AKTUÁLNÍM seznamu. Během jízdy se mohou dotáhnout stránky a index se posune. */
    currentIndex: () -> Int,
) {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return scrollToAnchor(index, anchor)
    val first = visible.first()
    val last = visible.last()
    val distance = when {
        index < first.index -> first.index - index
        index > last.index -> index - last.index
        else -> 0
    }
    if (distance > MAX_ANIMATED_JUMP_ITEMS) return scrollToAnchor(index, anchor)

    val visibleHeight = info.viewportEndOffset - info.afterContentPadding
    fun desired(size: Int) = when (anchor) {
        ScrollAnchor.Top -> visibleHeight - size
        ScrollAnchor.Center -> (visibleHeight - size) / 2
        ScrollAnchor.Bottom -> 0
    }.coerceAtLeast(0)

    val item = visible.firstOrNull { it.index == index }
    if (item == null) {
        // Odhad polohy: průměrná položka krát počet položek k cíli. Offset roste od spodní hrany
        // nahoru (obrácený seznam), vyšší index je výš.
        val average = visible.sumOf { it.size } / visible.size
        val estimatedOffset = if (index > last.index) {
            last.offset + last.size + (distance - 1) * average
        } else {
            first.offset - distance * average
        }
        animateScrollBy((estimatedOffset - desired(average)).toFloat(), spec)
    }
    // Doladění podle skutečného layoutu; odhad se mohl minout, nebo cíl vůbec není vidět.
    val now = currentIndex().takeIf { it >= 0 } ?: return
    val landed = layoutInfo.visibleItemsInfo.firstOrNull { it.index == now }
        ?: return scrollToAnchor(now, anchor)
    val correction = (landed.offset - desired(landed.size)).toFloat()
    if (correction != 0f) animateScrollBy(correction, spec)
}

/** Nejvýš o tolik položek se na citaci / výsledek hledání dojíždí; dál se skočí. */
internal const val MAX_ANIMATED_JUMP_ITEMS = 40

/**
 * Výšky položek, které seznam už někdy rozvrhl, podle klíče.
 *
 * Položka pod spodní hranou z `layoutInfo` vypadne, i když přes ni uživatel před chvílí
 * vyroloval nahoru. Návrat dolů ([animateScrollToBottom]) z nich spočítá přesnou vzdálenost.
 */
@Composable
internal fun rememberMeasuredSizes(listState: LazyListState): Map<Any, Int> {
    val sizes = remember(listState) { HashMap<Any, Int>() }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .collect { visible -> visible.forEach { sizes[it.key] = it.size } }
    }
    return sizes
}

/**
 * Tlačítko „dolů": dojede k nejnovější zprávě obyčejným rolováním o spočítanou vzdálenost,
 * NE přes `animateScrollToItem`.
 *
 * `animateScrollToItem` o pár zpráv nad spodkem skončil poskočením: seznam dojel dolů, chvíli
 * stál, pak všechny bubliny kromě nejnovější vyskočily o ~330 px nahoru a sjely zpátky
 * (`animateItem`). Spouští to jeho závěrečné `snapToItem` — na pozici, kde už seznam stojí,
 * podle zdroje Compose animátor položek záměrně NEresetuje, a seznam je v lookahead scope
 * `SharedTransitionLayout`. Že přichází až po doběhnutí rolování, ukázal záznam obrazovky
 * rozložený po snímcích; poslední krok mechanismu ověřený není. Rolování o vzdálenost
 * (`animateScrollBy`) je pro seznam totéž co tah prstem a nic takového nespouští.
 *
 * Vzdálenost se skládá z výšek položek pod obrazovkou ([sizeAt], viz [rememberMeasuredSizes]).
 * Zdaleka ([MAX_ANIMATED_JUMP_ITEMS]) a tam, kde některou výšku neznáme (seznam se po otočení
 * obrazovky obnovil uprostřed historie), zůstává `animateScrollToItem` — rolovat přes desítky
 * zpráv by je všechny skládalo, a zdaleka se poskočení neukazovalo.
 */
internal suspend fun LazyListState.animateScrollToBottom(sizeAt: (index: Int) -> Int?) {
    if (firstVisibleItemIndex > MAX_ANIMATED_JUMP_ITEMS) return animateScrollToItem(0)
    val spacing = layoutInfo.mainAxisItemSpacing
    var distance = firstVisibleItemScrollOffset
    for (index in 0 until firstVisibleItemIndex) {
        distance += (sizeAt(index) ?: return animateScrollToItem(0)) + spacing
    }
    // Bez odskoku: za spodní hranou seznam nepokračuje, překmit by se o ni zarazil a pružina
    // by se pak vracela nahoru.
    val spec = spring<Float>(stiffness = Spring.StiffnessMediumLow)
    animateScrollBy(-distance.toFloat(), spec)
    // Výška se mohla mezitím změnit (dotažený náhled, obrázek), nebo dole přibyla zpráva.
    val rest = layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.offset
    if (rest == null) scrollToItem(0) else if (rest != 0) animateScrollBy(rest.toFloat(), spec)
}
