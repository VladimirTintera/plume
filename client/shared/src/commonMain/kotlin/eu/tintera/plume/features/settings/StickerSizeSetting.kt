package eu.tintera.plume.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.settings.DEFAULT_STICKER_SIZE
import eu.tintera.plume.core.settings.STICKER_SIZES
import eu.tintera.plume.core.settings.STICKER_SIZE_STEP
import eu.tintera.plume.core.ui.highlightAnchor
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.settings_sticker_size
import eu.tintera.plume.resources.settings_sticker_size_preview
import org.jetbrains.compose.resources.stringResource

/**
 * Velikost samolepek ve zprávách: podobizna samolepky a posuvník pod ní.
 *
 * **Během tahu se mění jen náhled**, uloží se až po puštění — ze stejného důvodu jako u
 * [MessageTextSizeSetting].
 */
@Composable
internal fun StickerSizeSetting(stored: Int, onChange: (Int) -> Unit) {
    // Klíč `stored`: změna odjinud (obnovené nastavení) přepíše rozpracovaný tah.
    var size by remember(stored) { mutableIntStateOf(stored) }

    Column(Modifier.highlightAnchor(stringResource(Res.string.settings_sticker_size)).padding(vertical = 12.dp)) {
        StickerLikeness(size.dp, Modifier.padding(horizontal = 16.dp))
        SizeSlider(
            title = Res.string.settings_sticker_size,
            size = size,
            default = DEFAULT_STICKER_SIZE,
            range = STICKER_SIZES,
            step = STICKER_SIZE_STEP,
            onSizeChange = { size = it },
            onChangeFinished = { onChange(size) },
        )
    }
}

/**
 * Emoji ve velikosti samolepky. Jen podobizna — skutečnou samolepku by bylo potřeba stáhnout.
 *
 * Plocha má **pevnou výšku největší samolepky**: rostoucí náhled by posuvník pod prstem odsouval
 * až o sto dp. Leží na `surface` jako v konverzaci, takže je vidět poměr k šířce obrazovky.
 */
@Composable
private fun StickerLikeness(size: Dp, modifier: Modifier = Modifier) {
    val fontSize = with(LocalDensity.current) { (size * GLYPH_TO_STICKER).toSp() }
    Box(
        modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, ShapeDefaults.Large)
            .padding(12.dp)
            .height(STICKER_SIZES.last.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            Text(
                stringResource(Res.string.settings_sticker_size_preview),
                // Výška řádku z role je pevná; emoji se musí vejít v každé velikosti.
                style = MaterialTheme.typography.displayLarge.copy(fontSize = fontSize, lineHeight = TextUnit.Unspecified),
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/** Velikost písma vůči samolepce: obrázek emoji je širší než jeho `fontSize`. */
private const val GLYPH_TO_STICKER = 0.75f
