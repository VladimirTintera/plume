package eu.tintera.plume.core.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import eu.tintera.plume.core.ui.LocalPanesSideBySide

/**
 * Panel jako karta na tónované ploše, jak je má M3 Expressive: zaoblená plocha `surface` na
 * `surfaceContainer` (tu kreslí shell pod celou scénou) a mezi panely mezera [PANE_SPACING],
 * ve které stojí táhlo. Bez mezery by táhlo leželo přes okraj konverzace a panely by se
 * slévaly do jedné plochy.
 *
 * Jen když panely stojí vedle sebe ([LocalPanesSideBySide]); na telefonu obsah jde od kraje
 * ke kraji jako dřív. Struktura je v obou případech táž — kdyby se větvila, přišel by obsah
 * při změně šířky okna o svůj stav.
 */
@Composable
fun PaneCard(content: @Composable () -> Unit) {
    val sideBySide = LocalPanesSideBySide.current
    val shape = if (sideBySide) MaterialTheme.shapes.large else RectangleShape
    Box(
        Modifier
            .fillMaxSize()
            .clip(shape)
            .then(if (sideBySide) Modifier.background(MaterialTheme.colorScheme.surface) else Modifier),
    ) {
        content()
    }
}

/**
 * Obalí do [PaneCard] každou položku, která patří do panelu list-detail scény ([paneInfo]).
 * Obrazovky přes celou plochu (prohlížeč, mini aplikace) panelem nejsou a zůstávají bez rohů.
 */
@Composable
fun rememberPaneCardDecorator(): NavEntryDecorator<NavKey> = remember {
    NavEntryDecorator { entry ->
        if (entry.paneInfo != null) PaneCard { entry.Content() } else entry.Content()
    }
}
