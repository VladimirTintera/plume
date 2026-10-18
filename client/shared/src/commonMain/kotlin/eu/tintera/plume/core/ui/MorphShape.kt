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
 * - [ShapeMorphs.Live] kruh ↔ SoftBurst: živý signál, tlačítko při nahrávání dýchá; za fotkou
 *   protějšku dýchá, dokud hovor čeká (zvoní, vytáčí se, spojuje) — fotka sama zůstává kulatá.
 * - [ShapeMorphs.Highlight] kruh ↔ dvanáctistranná sušenka: podbarvení podržené samolepky,
 *   vybraný vzorek barvy motivu.
 * - [ShapeMorphs.Intro] kruh → čtyřlístek → pětiúhelník → zaoblená hvězda: uvítání prázdného
 *   stavu (detail bez otevřeného chatu). Jednou při zobrazení, pak tvar stojí.
 * - `Cookie7Sided` (bez morfu): odznak připnutých zpráv; při přechodu na jinou připnutou se
 *   pootočí (`PinnedBadge`).
 * - Tvar avataru → vlnitý kruh (deset vlnek) s otáčením: výpadek spojení (`ConnectionIndicator`,
 *   fotka účtu v seznamu chatů a avatar v hlavičce konverzace). Jediná výjimka z pravidla
 *   o avatarech níž — tam se stav opravdu mění.
 *
 * Tam, kde se stav nemění (avatary, položky seznamu, reakce), se morfy nepoužívají — kdyby se
 * hýbalo všechno, přestalo by to cokoli znamenat. Avatary mají jen STATICKÝ tvar podle druhu
 * chatu (`avatarShape`), a to z tvarů, které tady žádný stav neznamenají. Řádek bloku při stisku
 * jen změní zaoblení (`animatedGroupItemShape`), stejně jako stisknuté tlačítko — to je odezva
 * na prst, ne tvar z [MaterialShapes] s vlastním významem.
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

    /**
     * Zaoblená hvězda, na které se zastaví [Intro]. Cípy jsou zaoblené víc než žlábky mezi nimi —
     * se stejným zaoblením obou vyšla spíš kytička.
     *
     * **Sedm cípů, ne pět.** Pěticípá hvězda má jeden cíp nahoře a dva dole, takže její optický
     * střed nesedí se středem obrysu, do kterého se vepisuje ikona — tvar pak působí
     * nevystředěně. S lichým, ale vyšším počtem cípů je rozdíl neznatelný a tvar zůstane
     * hvězdou, ne kolečkem. Mělčí žlábky (vnitřní poloměr 0,68 místo 0,58) k tomu patří:
     * se sedmi hlubokými cípy by z toho byl ježek.
     */
    val Star: RoundedPolygon =
        RoundedPolygon.star(
            numVerticesPerRadius = STAR_POINTS,
            innerRadius = 0.68f,
            rounding = CornerRounding(0.24f),
            innerRounding = CornerRounding(0.1f),
        ).normalized()

    /**
     * Mnohoúhelník před hvězdou. Má **stejný počet vrcholů jako [Star]**, takže se při morfu
     * nepřeskupují — proto se nebere `MaterialShapes.Pentagon`, který sedl jen k pěti cípům.
     */
    private val PreStar: RoundedPolygon =
        RoundedPolygon(numVertices = STAR_POINTS, rounding = CornerRounding(0.2f)).normalized()

    /**
     * Úvodní sled tvarů, po úsecích: průběh 0..[Intro].size, úsek `i` je morf mezi `i`-tým
     * a dalším tvarem. Končí na [Star].
     */
    val Intro: List<Morph> = listOf(
        Morph(MaterialShapes.Circle, MaterialShapes.Clover4Leaf),
        Morph(MaterialShapes.Clover4Leaf, PreStar),
        Morph(PreStar, Star),
    )

    /** Pootočení sušenky, když se z kruhu stává (a zpátky). */
    const val REST_ROTATION = 60f

    private const val STAR_POINTS = 7
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
