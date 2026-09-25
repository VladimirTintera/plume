package eu.tintera.plume.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import eu.tintera.plume.core.telegram.ConnectionState
import kotlinx.coroutines.delay

/**
 * Stav spojení, jak se má ukázat: `null`, dokud je všechno v pořádku.
 *
 * **Výpadek se ukáže až po [CONNECTION_SHOW_DELAY_MS], návrat hned.** Po každém návratu
 * z pozadí TDLib na zlomek vteřiny hlásí `Updating` a fotka by se při každém otevření aplikace
 * zavlnila. Změna mezi dvěma stavy výpadku (čekání na síť → připojování) je už vidět, takže
 * se nečeká.
 */
@Composable
fun rememberShownConnection(state: ConnectionState): ConnectionState? {
    val shown by produceState<ConnectionState?>(initialValue = null, state) {
        if (state == ConnectionState.Ready) {
            value = null
            return@produceState
        }
        if (value == null) delay(CONNECTION_SHOW_DELAY_MS)
        value = state
    }
    return shown
}

/**
 * Stav spojení na avataru: avatar naroste, zvlní se, po jeho okraji vyjede vlnitý prstenec
 * a obrys se točí. Po obnovení spojení se vrátí do svého tvaru a velikosti. Stejně na fotce účtu
 * v seznamu chatů i na avataru v hlavičce konverzace — jeden znak pro „nejsem připojený" napříč
 * aplikací. Text stavu nese jiné místo (nápověda pilulky hledání, popis pro čtečku).
 *
 * **Prstenec je obrys avataru, ne rám kolem něj.** Mezera mezi fotkou a prstencem vypadala jako
 * díra; avatar proto místo zmenšení naroste až k prstenci.
 *
 * - **Připojování a aktualizace: otáčka za [CONNECTING_TURN_MS]**, prstenec v `primary` — něco
 *   se děje, stačí počkat.
 * - **Čekání na síť: otáčka za [WAITING_TURN_MS]**, prstenec v `outline`. Na nic se nečeká,
 *   dokud se síť nevrátí; tlumená barva a pomalé točení to říkají, a přitom je pořád vidět, že
 *   stav trvá. (Zkoušelo se i úplné zastavení — stojící vlnka působila jako zamrzlá.)
 *
 * **Točí se obrys, fotka ne** — obrys je ořez ve vrstvě ([avatar]), obsah pod ním stojí.
 * Zvlnění, zvětšení i prstenec jdou z JEDNOHO průběhu morfu, takže se nemůžou rozejít; úhel se
 * čte až při kreslení a točení nic neskládá.
 */
@Stable
class ConnectionIndicator internal constructor(
    private val progress: State<Float>,
    private val angle: () -> Float,
    private val color: State<Color>,
    private val morph: Morph,
    private val avatarSize: Dp,
    private val grownSize: Dp,
) {
    private val shaping = derivedStateOf { progress.value > 0f }

    /**
     * Avatar má teď tvar z indikátoru, ne svůj. Avatar si obsah ořezává i vlastním tvarem
     * (`Avatar(shape)`), a u skupiny by tak vlnitý ořez zůstal uvnitř sušenky — fotka by se
     * nezvlnila. Dokud je tohle `true`, patří avataru obdélník. Na začátku morfu je ořez
     * indikátoru ještě totožný s původním tvarem, takže výměna není vidět.
     */
    val isShaping: Boolean get() = shaping.value

    /** Avatar: naroste a ořízne se ze svého tvaru do vlnitého. Jen na avatar, ne na odznaky. */
    fun Modifier.avatar(): Modifier = graphicsLayer {
        val p = progress.value.coerceIn(0f, 1f)
        val scale = lerp(1f, grownSize / avatarSize, p)
        scaleX = scale
        scaleY = scale
        if (p > 0f) {
            shape = MorphRotatedShape(morph, p, angle() * p)
            clip = true
        }
    }

    /**
     * Prstenec po okraji avataru, nad ním. Kreslí se se středem ve středu prvku, na kterém je,
     * a smí přesahovat jeho hranice — stačí ho dát na obal velký jako avatar. Tah leží napůl
     * přes okraj avataru, takže mezi nimi nic neprosvítá.
     */
    fun Modifier.ring(): Modifier = drawWithContent {
        drawContent()
        val p = progress.value.coerceIn(0f, 1f)
        if (p <= 0f) return@drawWithContent
        val diameter = lerp(avatarSize.toPx(), grownSize.toPx(), p)
        val ring = Size(diameter, diameter)
        val path = morphPath(morph, p, angle() * p, ring)
        path.translate(size.center - ring.center)
        drawPath(
            path = path,
            color = color.value,
            alpha = p,
            style = Stroke(width = RING_STROKE.toPx(), cap = StrokeCap.Round),
        )
    }
}

/**
 * @param base výchozí tvar avataru jako polygon (kruh, sušenka skupiny, čtverec kanálu); z něj se
 *   morfuje do vlnek, takže se avatar nepřepne skokem do kruhu.
 * @param grownSize velikost avataru při výpadku. Musí se vejít tam, kde avatar stojí — prstenec
 *   přidává ještě půl tahu ([RING_STROKE]) na každou stranu.
 */
@Composable
fun rememberConnectionIndicator(
    connection: ConnectionState?,
    avatarSize: Dp,
    grownSize: Dp,
    base: RoundedPolygon = MaterialShapes.Circle,
): ConnectionIndicator {
    val progress = animateFloatAsState(
        targetValue = if (connection == null) 0f else 1f,
        animationSpec = morphSpring(),
        label = "connection-morph",
    )
    /*
     * Rychlost ve stupních za vteřinu, ne doba otáčky: mezi stavy se tak dá plynule přelít
     * a otáčení při přechodu z čekání na síť do připojování neškubne.
     */
    val speed by animateFloatAsState(
        targetValue = when (connection) {
            null -> 0f
            ConnectionState.WaitingForNetwork -> 360_000f / WAITING_TURN_MS
            else -> 360_000f / CONNECTING_TURN_MS
        },
        animationSpec = tween(SPEED_CHANGE_MS),
        label = "connection-speed",
    )
    val angle = remember { mutableFloatStateOf(0f) }
    /*
     * Úhel přibývá po snímcích podle rychlosti. Běží jen při výpadku — se spojením by si smyčka
     * o snímky říkala pořád. Konec nechá úhel, kde je; použije se násobený průběhem morfu, takže
     * se tvar při zvlňování nepřetočí skokem (u čtverce kanálu by to bylo vidět).
     */
    LaunchedEffect(connection != null) {
        if (connection == null) return@LaunchedEffect
        var last = -1L
        while (true) {
            withFrameNanos { now ->
                if (last >= 0) angle.floatValue = (angle.floatValue + speed * (now - last) / 1e9f) % 360f
                last = now
            }
        }
    }
    /*
     * Při zasouvání drží prstenec barvu posledního stavu — `null` žádnou nemá. Obyčejné pole,
     * ne stav: čte se jen při skládání a kvůli němu se nic nepřekresluje.
     */
    val last = remember { LastValue<ConnectionState>() }
    connection?.let { last.value = it }
    val color = animateColorAsState(
        targetValue = if (last.value == ConnectionState.WaitingForNetwork) {
            MaterialTheme.colorScheme.outline
        } else {
            MaterialTheme.colorScheme.primary
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "connection-ring-color",
    )
    val morph = remember(base) { Morph(base, Wavy) }
    return remember(progress, angle, color, morph, avatarSize, grownSize) {
        ConnectionIndicator(progress, { angle.floatValue }, color, morph, avatarSize, grownSize)
    }
}

/** Obrys v průběhu morfu, otočený o [degrees]. Instance je pro jednu hodnotu. */
private class MorphRotatedShape(
    private val morph: Morph,
    private val progress: Float,
    private val degrees: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(morphPath(morph, progress, degrees, size))
}

private fun morphPath(morph: Morph, progress: Float, degrees: Float, size: Size): Path {
    val path = morph.toPath(progress, Path())
    path.transform(
        Matrix().apply {
            translate(size.width / 2f, size.height / 2f)
            rotateZ(degrees)
            scale(x = size.width, y = size.height)
            translate(-0.5f, -0.5f)
        },
    )
    return path
}

/**
 * Vlnitý kruh, do kterého se avatar při výpadku zvlní. Mělké vlnky (vnitřní poloměr 0,82):
 * hlubší by z 38dp fotky ukusovaly tvář, mělčí by se od kruhu nedaly rozeznat. Prstenec je tentýž
 * obrys, tahem po okraji avataru.
 */
private val Wavy: RoundedPolygon = RoundedPolygon.star(
    numVerticesPerRadius = WAVES,
    innerRadius = 0.82f,
    rounding = CornerRounding(radius = 0.2f, smoothing = 0.4f),
    innerRounding = CornerRounding(radius = 0.2f),
).normalized()

/** Obyčejné pole, ne stav — čte se jen při skládání obsahu, který se kvůli němu nepřekresluje. */
private class LastValue<T> {
    var value: T? = null
}

private const val CONNECTION_SHOW_DELAY_MS = 600L

/** Otáčka při připojování. Pomalu — je to čekání, ne práce, a rychlé točení ruší. */
private const val CONNECTING_TURN_MS = 9000

/** Otáčka při čekání na síť: víc než dvakrát pomalejší, aby se stavy daly rozeznat i bez textu. */
private const val WAITING_TURN_MS = 20000

private const val SPEED_CHANGE_MS = 600

private const val WAVES = 10

/** Tah prstence. Kdo avatar zvětšuje, musí počítat s půlkou tahu navíc na každé straně. */
val RING_STROKE = 2.5.dp
