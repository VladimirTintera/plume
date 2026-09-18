package eu.tintera.plume.core.ui

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star

/**
 * Tvar v průběhu morfu mezi dvěma tvary z M3 Expressive ([MaterialShapes]).
 *
 * **Tvar nese stav** — tak s tvary M3 Expressive zacházíme v celé aplikaci. Každý má jeden
 * význam, aby se jazyk nerozmělnil:
 * - [ShapeMorphs.Rest] kruh ↔ šestistranná „sušenka": sušenka je klid / připraveno (mikrofon, pozastavené
 *   přehrávání), kruh probíhající akce (odeslat, hraje).
 * - [ShapeMorphs.Live] kruh ↔ SoftBurst: živý signál, tlačítko při nahrávání dýchá.
 * - [ShapeMorphs.Highlight] kruh ↔ dvanáctistranná sušenka: podbarvení podržené samolepky.
 *
 * Tam, kde se stav nemění (avatary, položky seznamu, reakce), se tvary nepoužívají — kdyby se
 * hýbalo všechno, přestalo by to cokoli znamenat.
 *
 * Instance je pro jednu hodnotu [progress]: Compose si obrys drží podle instance a velikosti,
 * takže tvar, který by průběh četl sám, by se nepřekreslil. Kde se průběh čte až při kreslení,
 * stačí zavolat [createOutline] na nové instanci v `drawBehind`.
 *
 * [rotation] ve stupních při plném tvaru (progress 1); kruh se otáčením nemění, takže začátek
 * sedí vždy.
 */
class MorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotation: Float = 0f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = morph.toPath(progress.coerceIn(0f, 1f), Path())
        path.transform(Matrix().apply { scale(x = size.width, y = size.height) })
        path.translate(size.center - path.getBounds().center)
        if (rotation != 0f) {
            path.transform(
                Matrix().apply {
                    translate(size.width / 2f, size.height / 2f)
                    rotateZ(progress * rotation)
                    translate(-size.width / 2f, -size.height / 2f)
                },
            )
        }
        return Outline.Generic(path)
    }
}

/** Morfy, které aplikace používá. Oba konce jsou v `MaterialShapes` normalizované, přejdou do sebe bez škálování. */
object ShapeMorphs {
    /**
     * Vlastní šestistranná sušenka s hlubšími vlnkami (vnitřní poloměr 0,7), ne `Cookie9Sided`
     * z M3 (devět vlnek, 0,8). S devíti mělkými vlnkami se tvar na telefonu od kruhu skoro
     * nelišil a morf nebyl vidět. Zaoblení je stejné jako u sušenek M3.
     */
    val Rest = Morph(
        MaterialShapes.Circle,
        RoundedPolygon.star(numVerticesPerRadius = 6, innerRadius = 0.7f, rounding = CornerRounding(0.5f))
            .normalized(),
    )
    val Live = Morph(MaterialShapes.Circle, MaterialShapes.SoftBurst)
    val Highlight = Morph(MaterialShapes.Circle, MaterialShapes.Cookie12Sided)

    /** Pootočení sušenky, když se z kruhu stává (a zpátky). */
    const val REST_ROTATION = 60f
}

/**
 * Průběh přechodu kruh (false) ↔ tvar (true) pružinou z `MotionScheme`. Pružina přestřelí,
 * [MorphShape] si průběh ořízne sám.
 */
@Composable
fun animateMorph(toShape: Boolean, label: String): Float =
    animateFloatAsState(
        targetValue = if (toShape) 1f else 0f,
        animationSpec = morphSpring(),
        label = label,
    ).value

/**
 * Pružina morfu tvarů. **Pomalejší než `defaultSpatialSpec`** (tuhost 70 místo ~380): s výchozí
 * doběhl morf na skutečném telefonu za zlomek vteřiny a nebyl vidět (na emulátoru v debugu ano,
 * ten je pomalejší). Takhle trvá zhruba 0,7 s a tvar se před dosednutím lehce zhoupne. Směr
 * sušenka → kruh působí rychleji i tak: blízko kruhu se vlnky rychle vytrácejí.
 * Stejnou pružinou jede i výměna ikony, aby tvar a ikona běžely spolu.
 */
fun <T> morphSpring(): SpringSpec<T> = spring(dampingRatio = MORPH_DAMPING, stiffness = MORPH_STIFFNESS)

private const val MORPH_DAMPING = 0.75f
private const val MORPH_STIFFNESS = 70f
