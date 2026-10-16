package eu.tintera.plume.features.conversation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.ui.wheelScrollsHorizontally
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Řádek záložek výběru samolepek, který skládá jen VIDITELNÉ záložky.
 *
 * `PrimaryScrollableTabRow` skládá a měří všechny záložky naráz, a záložka sady nese obálku
 * (`RemoteImage`). S desítkami sad to byla největší část prvního snímku sheetu: 32 záložek
 * ~106 ms jen skládání (Perfetto, emulátor), tedy zdržení, než se sheet vůbec pohne. Tady leží
 * tytéž M3 [Tab]y v [LazyRow] a zbytek řádku se dělá jako v M3: záložka aspoň
 * [TabRowDefaults.ScrollableTabRowMinTabWidth] široká, oddělovač pod řádkem, indikátor vybrané
 * záložky, který k ní dojede `defaultSpatialSpec`, a vybraná záložka posunutá doprostřed řádku.
 *
 * Indikátor se kreslí přes řádek (`drawWithContent`) a polohu záložek čte z `layoutInfo` až při
 * kreslení — posun řádku ani jízda indikátoru tak nic neskládají.
 */
@Composable
internal fun StickerTabRow(
    tabs: List<StickerTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0))
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    // Poloha indikátoru jako „index" se zlomkem: mezi dvěma záložkami jede plynule.
    val indicator = remember { Animatable(selectedIndex.toFloat()) }
    var centered by remember { mutableStateOf(false) }
    LaunchedEffect(selectedIndex) {
        // Před prvním rozměřením řádek o svých záložkách nic neví.
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.isNotEmpty() }.first { it }
        // Napoprvé (výběr se otevírá na poslední sadě) bez animace — sheet teprve vyjíždí.
        listState.centerOn(selectedIndex, spec = spatial.takeIf { centered })
        centered = true
    }
    LaunchedEffect(selectedIndex) { indicator.animateTo(selectedIndex.toFloat(), spatial) }

    val indicatorColor = MaterialTheme.colorScheme.primary
    Box(
        modifier.drawWithContent {
            drawContent()
            val center = listState.centerOf(indicator.value) ?: return@drawWithContent
            val width = INDICATOR_WIDTH.toPx()
            val height = INDICATOR_HEIGHT.toPx()
            val x = if (layoutDirection == LayoutDirection.Rtl) size.width - center else center
            drawRoundRect(
                color = indicatorColor,
                topLeft = Offset(x - width / 2, size.height - height),
                size = Size(width, height),
                cornerRadius = CornerRadius(height),
            )
        },
    ) {
        CompositionLocalProvider(LocalContentColor provides TabRowDefaults.primaryContentColor) {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier.selectableGroup().wheelScrollsHorizontally(listState),
            ) {
                itemsIndexed(tabs, key = { _, tab -> tab.storageKey() }) { index, tab ->
                    Tab(
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) },
                        icon = { StickerTabIcon(tab) },
                        modifier = Modifier.widthIn(min = TabRowDefaults.ScrollableTabRowMinTabWidth),
                    )
                }
            }
        }
        HorizontalDivider(Modifier.align(Alignment.BottomStart))
    }
}

/**
 * Střed záložky na pozici [position] (index se zlomkem) od začátku řádku, v pixelech.
 * Záložka mimo obrazovku se dopočítá od nejbližší viditelné — záložky jsou stejně široké.
 * `null` = řádek ještě není rozměřený.
 */
private fun LazyListState.centerOf(position: Float): Float? {
    val info = layoutInfo
    val visible = info.visibleItemsInfo.ifEmpty { return null }
    fun centerOf(index: Int): Float {
        val near = visible.firstOrNull { it.index == index } ?: visible.minBy { abs(it.index - index) }
        return info.beforeContentPadding + near.offset + near.size / 2f + (index - near.index) * near.size
    }
    val from = floor(position).toInt()
    val to = ceil(position).toInt()
    val start = centerOf(from)
    return if (from == to) start else start + (centerOf(to) - start) * (position - from)
}

/**
 * Posune řádek tak, aby záložka [index] stála uprostřed (u krajů, kam až to jde) — jako
 * `ScrollableTabRow`. Bez [spec] skokem.
 */
private suspend fun LazyListState.centerOn(index: Int, spec: AnimationSpec<Float>?) {
    if (index < 0) return
    if (layoutInfo.visibleItemsInfo.none { it.index == index }) {
        if (spec == null) scrollToItem(index) else animateScrollToItem(index)
    }
    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val delta = info.beforeContentPadding + item.offset + item.size / 2f - info.viewportSize.width / 2f
    if (spec == null) scrollBy(delta) else animateScrollBy(delta, spec)
}

/**
 * Šířka indikátoru jako u `PrimaryScrollableTabRow` s ikonovými záložkami: M3 bere šířku obsahu
 * bez vodorovného odsazení textu (2 × 16 dp) a nejméně 24 dp — u ikony 24–28 dp tedy 24 dp.
 */
private val INDICATOR_WIDTH = 24.dp

/** `PrimaryNavigationTabTokens.ActiveIndicatorHeight`. */
private val INDICATOR_HEIGHT = 3.dp
