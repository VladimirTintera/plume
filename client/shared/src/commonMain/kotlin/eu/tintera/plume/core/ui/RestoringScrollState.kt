package eu.tintera.plume.core.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * `rememberScrollState()` pro svislý obsah obrazovky, který po návratu z jiné obrazovky drží
 * pozici, i když obsah doroste až po prvním snímku.
 *
 * **Proč nestačí `rememberScrollState()`:** obrazovka pod jinou obrazovkou není složená
 * (`NavDisplay` kreslí jen horní scénu), po návratu se složí znovu a `ScrollState` obnoví
 * uloženou pozici v pixelech. Při prvním měření ji ale ořízne na `maxValue` — a to TRVALE
 * (setter `maxValue` srazí `value`). Je-li obsah v prvním snímku kratší než při odchodu, seznam
 * po dorůstání obsahu zůstane posunutý o chybějící kus. V Nastavení to byl řádek „AI v zařízení"
 * (dostupnost se čte z flow a první snímek ji nezná): po návratu z „O aplikaci" uskočil seznam
 * o 74 dp; v Soukromí po návratu z dvoufázového ověření o 16 dp. Zdroj kratšího prvního snímku
 * se po obrazovkách liší a hledat ho v každé zvlášť by nikdy neskončilo, proto se to řeší tady.
 *
 * **Jak:** uložená pozice se pamatuje jako cíl a dokud jí seznam nedosáhne, dorovnává se
 * s každým zvětšením obsahu (`dispatchRawDelta`, tedy bez `isScrollInProgress`).
 * Dorovnávání skončí, jakmile se seznam posune jinak — tah, fling, kolečko myši
 * i programový posun obrazovky (doscrollování na řádek z hledání v Nastavení) mají přednost —
 * nebo po [RESTORE_WINDOW], aby obsah, který přijde až za dlouho, neškubl seznamem pod rukou.
 * Krátký snímek s ořezanou pozicí zapadne do přechodu mezi obrazovkami.
 *
 * Hlídá to `verifyRestoringScroll` na `check`. Vodorovné řady (`horizontalScroll`) se
 * neobnovují ve stejném smyslu a zůstávají na `rememberScrollState()`.
 */
@Composable
fun rememberRestoringScrollState(): ScrollState {
    val holder = rememberSaveable(saver = RestoringScroll.Saver) { RestoringScroll(saved = 0) }
    LaunchedEffect(holder) { holder.restore() }
    return holder.state
}

private val RESTORE_WINDOW = 3.seconds

@Stable
private class RestoringScroll(saved: Int) {
    val state = ScrollState(saved)

    /** Pozice, na kterou se dorovnává; po dosažení nebo vzdání 0. */
    private var target = saved

    suspend fun restore() {
        val goal = target
        if (goal <= 0) return
        withTimeoutOrNull(RESTORE_WINDOW) {
            snapshotFlow { Triple(state.maxValue, state.value, state.isScrollInProgress) }
                .first { (max, value, scrolling) ->
                    when {
                        scrolling -> true
                        // Ještě neměřeno: maxValue je do prvního měření Int.MAX_VALUE.
                        max == Int.MAX_VALUE -> false
                        value >= goal -> true
                        else -> {
                            val wanted = minOf(goal, max)
                            if (value < wanted) state.dispatchRawDelta((wanted - value).toFloat())
                            false
                        }
                    }
                }
        }
        target = 0
    }

    companion object {
        /**
         * Neukládá se ořezaná pozice, dokud se dorovnává: odejde-li uživatel znovu dřív,
         * než obsah doroste, vrátí se příště na původní místo, ne na ořezané.
         */
        val Saver: Saver<RestoringScroll, Int> = Saver(
            save = { maxOf(it.state.value, it.target) },
            restore = { RestoringScroll(saved = it) },
        )
    }
}
