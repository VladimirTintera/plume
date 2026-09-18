package eu.tintera.plume.features.conversation

import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph

/**
 * Tvar tlačítka u vstupního pole: kruh pro odeslání, „sušenka" z M3 Expressive
 * ([MaterialShapes.Cookie9Sided]) pro mikrofon, a morf mezi nimi při změně.
 *
 * Tvar tu nese stav, jako u přepínacích tlačítek M3 Expressive. Při přechodu se sušenka trochu
 * pootočí, takže změna je vidět i koutkem oka. [progress] 0 je kruh, 1 sušenka.
 *
 * Tvar je nový objekt pro každou hodnotu průběhu: Compose si obrys drží podle instance a velikosti,
 * takže tvar, který by průběh četl sám, by se nepřekreslil.
 */
internal class SendButtonMorphShape(private val progress: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = SEND_BUTTON_MORPH.toPath(progress, Path())
        path.transform(
            Matrix().apply {
                scale(x = size.width, y = size.height)
            },
        )
        path.translate(size.center - path.getBounds().center)
        // Pootočení kolem středu tlačítka; kruh se otáčením nemění, takže začátek i konec sedí.
        path.transform(
            Matrix().apply {
                translate(size.width / 2f, size.height / 2f)
                rotateZ(progress * SEND_BUTTON_MORPH_ROTATION)
                translate(-size.width / 2f, -size.height / 2f)
            },
        )
        return Outline.Generic(path)
    }
}

/** Oba tvary jsou v `MaterialShapes` normalizované na jednotkový čtverec, takže do sebe přejdou bez škálování. */
private val SEND_BUTTON_MORPH = Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided)

/** O kolik stupňů se sušenka při plném tvaru pootočí. */
private const val SEND_BUTTON_MORPH_ROTATION = 60f
