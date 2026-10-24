package eu.tintera.plume.wear

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ScreenScaffold

/**
 * Fotka přes celý displej, zpět tahem doprava jako všude na hodinkách. Bere tutéž variantu jako
 * bublina (`fileId`), jen dekódovanou na velikost displeje — po bublině je obvykle stažená
 * a otevře se hned. Černé pozadí kolem: fotka je obsah, ne prvek rozhraní.
 */
@Composable
fun PhotoScreen(fileId: Int) {
    val screen = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val bitmap = rememberPhotoBitmap(fileId, screen)
    ScreenScaffold {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            if (bitmap == null) {
                CircularProgressIndicator()
            } else {
                Image(bitmap, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
