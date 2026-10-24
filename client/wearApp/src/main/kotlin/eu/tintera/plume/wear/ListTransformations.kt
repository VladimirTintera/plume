package eu.tintera.plume.wear

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight

/**
 * Vlastní obsah položky (bublina zprávy, poznámka, řádek s tlačítkem) se u horního a dolního
 * okraje kulatého displeje zmenší a zprůhlední stejně jako karty a tlačítka (M3 Expressive pro
 * hodinky). Komponenty s parametrem `transformation` (`TitleCard`, `Button`, `ListHeader`) se
 * místo toho transformují samy přes `SurfaceTransformation` — tvar plochy pak morfují celý, ne
 * jen zmenšením.
 *
 * Dvě vrstvy: kontejner (zmenšení, posun a průhlednost plochy) a obsah (průhlednost textu).
 * Na jedné vrstvě by průhlednost obsahu přepsala tu kontejneru, ve dvou se násobí.
 */
fun Modifier.transformedItem(scope: TransformingLazyColumnItemScope, spec: TransformationSpec): Modifier =
    transformedHeight(scope, spec)
        .graphicsLayer { with(scope) { with(spec) { applyContainerTransformation(scrollProgress) } } }
        .graphicsLayer { with(scope) { with(spec) { applyContentTransformation(scrollProgress) } } }
