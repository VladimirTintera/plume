package eu.tintera.plume.features.chats

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import eu.tintera.plume.core.ui.morphSpring
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
internal fun rememberShownConnection(state: ConnectionState): ConnectionState? {
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
 * Stav spojení na fotce účtu: fotka naroste, zvlní se, po jejím okraji vyjede vlnitý prstenec
 * a obojí se točí. Po obnovení spojení se fotka vrátí do kruhu a velikosti. Text stavu nese
 * nápověda v pilulce hledání.
 *
 * **Prstenec je obrys fotky, ne rám kolem ní.** Mezera mezi fotkou a prstencem vypadala jako
 * díra; fotka proto místo zmenšení naroste až k prstenci ([GROWN_AVATAR_SCALE]).
 *
 * - **Připojování a aktualizace: otáčka za [CONNECTING_TURN_MS]**, prstenec v `primary` — něco
 *   se děje, stačí počkat.
 * - **Čekání na síť: otáčka za [WAITING_TURN_MS]**, prstenec v `outline`. Na nic se nečeká,
 *   dokud se síť nevrátí; tlumená barva a pomalé točení to říkají, a přitom je pořád vidět, že
 *   stav trvá. (Zkoušelo se i úplné zastavení — stojící vlnka působila jako zamrzlá.)
 *
 * **Točí se obrys, fotka ne** — obrys je ořez ve vrstvě ([clip]), obsah pod ním stojí.
 * Zvlnění, zvětšení fotky i prstenec jdou z JEDNOHO průběhu morfu, takže se nemůžou rozejít;
 * úhel se čte až při kreslení a točení nic neskládá.
 */
@Stable
internal class ConnectionIndicator(
    private val progress: State<Float>,
    private val angle: () -> Float,
    private val color: State<Color>,
    private val avatarSize: Dp,
) {
    /**
     * Fotka: naroste a ořízne se kruh → vlnitý tvar. Jen na fotku, ne na odznaky — odznak
     * v rohu zvětšené fotky by vyjel mimo 40dp ořez `IconButton`.
     */
    fun Modifier.avatar(): Modifier = graphicsLayer {
        val p = progress.value.coerceIn(0f, 1f)
        val scale = lerp(1f, GROWN_AVATAR_SCALE, p)
        scaleX = scale
        scaleY = scale
        if (p > 0f) {
            shape = WavyMorphShape(p, angle())
            clip = true
        }
    }

    /**
     * Prstenec po okraji fotky, nad ní. Kreslí se na ploše velké [RING_SIZE] se středem ve
     * středu fotky a roste spolu s ní. Tah leží napůl přes okraj fotky, takže mezi nimi nic
     * neprosvítá. Nad `IconButton`, ne v něm — ten obsah ořezává na svůj 40dp kruh.
     */
    fun Modifier.ring(): Modifier = drawWithContent {
        drawContent()
        val p = progress.value.coerceIn(0f, 1f)
        if (p <= 0f) return@drawWithContent
        val strokeWidth = RING_STROKE.toPx()
        val diameter = avatarSize.toPx() * lerp(1f, GROWN_AVATAR_SCALE, p)
        val path = wavyPath(p, angle(), Size(diameter, diameter))
        path.translate(size.center - Size(diameter, diameter).center)
        drawPath(
            path = path,
            color = color.value,
            alpha = p,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )
    }
}

@Composable
internal fun rememberConnectionIndicator(connection: ConnectionState?, avatarSize: Dp): ConnectionIndicator {
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
     * o snímky říkala pořád. Konec nechá tvar tam, kde je; při návratu do kruhu na úhlu nezáleží.
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
    return remember(progress, angle, color, avatarSize) {
        ConnectionIndicator(progress, { angle.floatValue }, color, avatarSize)
    }
}

/** Obrys v průběhu morfu kruh → vlnky, otočený o [degrees]. Instance je pro jednu hodnotu. */
private class WavyMorphShape(private val progress: Float, private val degrees: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(wavyPath(progress, degrees, size))
    }
}

private fun wavyPath(progress: Float, degrees: Float, size: Size): Path {
    val path = WavyMorph.toPath(progress, Path())
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
 * Kruh → vlnitý kruh. Mělké vlnky (vnitřní poloměr 0,82): hlubší by z 38dp fotky ukusovaly
 * tvář, mělčí by se od kruhu nedaly rozeznat. Prstenec je tentýž obrys, tahem po okraji fotky.
 */
private val WavyMorph = Morph(
    MaterialShapes.Circle,
    RoundedPolygon.star(
        numVerticesPerRadius = WAVES,
        innerRadius = 0.82f,
        rounding = CornerRounding(radius = 0.2f, smoothing = 0.4f),
        innerRounding = CornerRounding(radius = 0.2f),
    ).normalized(),
)

/** Obyčejné pole, ne stav — čte se jen při skládání obsahu, který se kvůli němu nepřekresluje. */
private class LastValue<T> {
    var value: T? = null
}

private const val CONNECTION_SHOW_DELAY_MS = 600L

/** Otáčka při připojování. Pomalu — je to čekání, ne práce, a rychlé točení vedle hledání ruší. */
private const val CONNECTING_TURN_MS = 9000

/** Otáčka při čekání na síť: víc než dvakrát pomalejší, aby se stavy daly rozeznat i bez textu. */
private const val WAITING_TURN_MS = 20000

private const val SPEED_CHANGE_MS = 600

private const val WAVES = 10

/** Plocha prstence: zvětšená fotka i s tahem. Vejde se do 48dp slotu tlačítka i do 56dp pilulky. */
internal val RING_SIZE = 42.dp

private val RING_STROKE = 2.5.dp

/** Zvětšení fotky při výpadku: 30 → 38 dp, pod 40dp ořezem `IconButton`. */
private const val GROWN_AVATAR_SCALE = 38f / 30f
