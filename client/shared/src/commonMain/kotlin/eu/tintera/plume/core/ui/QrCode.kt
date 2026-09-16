package eu.tintera.plume.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.floor

/**
 * Mřížka modulů QR kódu, bez klidové zóny. `modules[y][x] == true` je tmavý modul.
 */
class QrModules(val modules: Array<BooleanArray>) {
    val size: Int get() = modules.size
}

/** Zakóduje [content] do QR mřížky, `null` při selhání. Na platformě, protože kodér v commonMain není. */
expect fun encodeQrCode(content: String): QrModules?

/**
 * QR kód kreslený po modulech, takže je ostrý v každé velikosti.
 *
 * Barvy jsou natvrdo černá na bílé: čtečky počítají s tmavými moduly na světlém podkladu
 * a inverzní kód z tmavého režimu spousta z nich nepřečte.
 */
@Composable
fun QrCode(
    content: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val qr = remember(content) { encodeQrCode(content) } ?: return
    Surface(
        color = Color.White,
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .aspectRatio(1f)
            .semantics { contentDescription?.let { this.contentDescription = it } },
    ) {
        // Klidová zóna 16 dp — standard chce 4 moduly, u zaoblené karty stačí méně.
        Canvas(Modifier.padding(16.dp)) {
            // Modul na celé pixely: se zlomkovou velikostí vyhlazování hran nechává mezi
            // sousedními moduly vlasové spáry. Zbytek se rozdělí do okrajů.
            val cell = floor(size.minDimension / qr.size)
            val origin = (size.minDimension - cell * qr.size) / 2
            val cellSize = Size(cell, cell)
            qr.modules.forEachIndexed { y, row ->
                row.forEachIndexed { x, dark ->
                    if (dark) drawRect(Color.Black, Offset(origin + x * cell, origin + y * cell), cellSize)
                }
            }
        }
    }
}

/** Ořízne okraj kolem tmavých modulů; generátory ho přidávají různě velký. */
internal fun trimQrMargin(width: Int, height: Int, isDark: (x: Int, y: Int) -> Boolean): QrModules? {
    var minX = width; var minY = height; var maxX = -1; var maxY = -1
    for (y in 0 until height) for (x in 0 until width) {
        if (isDark(x, y)) {
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
    }
    if (maxX < 0) return null
    return QrModules(Array(maxY - minY + 1) { y -> BooleanArray(maxX - minX + 1) { x -> isDark(minX + x, minY + y) } })
}
