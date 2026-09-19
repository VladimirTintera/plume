package eu.tintera.plume.features.conversation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min

/**
 * Přechod na zprávu, ke které se nedá dojet: je daleko, nebo ještě není načtená.
 *
 * Místo okamžitého přeskočení seznam **odjede** směrem k ní a zprůhlední ([leave]). Mezitím se
 * načte okno kolem zprávy, seznam se na ni neviditelně přesune a pak [arrive]: nalezená zpráva
 * stojí rovnou na svém místě a zvýrazněná, sousedé k ní **sjedou** shora i zdola. Působí to jako
 * jízda, ne skok, a přitom se nic nerolovalo — rolování přes stovky zpráv by trvalo a skládalo
 * by všechno mezi, a do nenačtené historie se dojet nedá vůbec.
 *
 * Všechno se čte až při kreslení ([listAlpha], [listShift], [shiftOf], [alphaOf]).
 */
@Stable
internal class JumpTransition {
    private val veil = Animatable(1f)
    private val shift = Animatable(0f)
    private val gather = Animatable(1f)

    /** Zpráva, ke které sousedé sjíždějí; `null` mimo příjezd. */
    private var pinnedKey by mutableStateOf<Any?>(null)
    private var pinnedIndex by mutableStateOf(-1)

    /** Index položky podle klíče v aktuálním seznamu. Obrazovka ho nastavuje při každé změně seznamu. */
    var indexOf: (Any) -> Int = { -1 }

    val listAlpha: Float get() = veil.value.coerceIn(0f, 1f)
    val listShift: Float get() = shift.value

    /**
     * Seznam odjede o [distance] (kladná = dolů, tedy ke starším zprávám nahoře) a zprůhlední.
     * Volá se i podruhé nad rozjetým odjezdem — animace pak jen pokračuje ze současné hodnoty.
     */
    suspend fun leave(distance: Float, moveSpec: AnimationSpec<Float>, fadeSpec: AnimationSpec<Float>) = coroutineScope {
        launch { shift.animateTo(distance, moveSpec) }
        veil.animateTo(0f, fadeSpec)
    }

    /** Zpráva s [key] na indexu [index] je na místě; sousedé k ní sjedou z [distance] daleko. */
    suspend fun arrive(key: Any, index: Int, distance: Float, spec: AnimationSpec<Float>) {
        gatherDistance = distance
        pinnedIndex = index
        pinnedKey = key
        gather.snapTo(0f)
        shift.snapTo(0f)
        veil.snapTo(1f)
        try {
            gather.animateTo(1f, spec)
        } finally {
            // I při přerušení dalším skokem: bez zapíchnuté zprávy se nikdo neposouvá.
            pinnedKey = null
        }
    }

    /** Odjezd nedoběhl do příjezdu (zpráva se nenašla, přišel jiný skok) — seznam se vrátí. */
    suspend fun restore(spec: AnimationSpec<Float>) = coroutineScope {
        if (pinnedKey != null) return@coroutineScope
        launch { shift.animateTo(0f, spec) }
        veil.animateTo(1f, spec)
    }

    /** Okamžitý návrat do klidu, když se místo přechodu dojíždí nebo skáče. */
    suspend fun reset() {
        if (veil.value != 1f) veil.snapTo(1f)
        if (shift.value != 0f) shift.snapTo(0f)
    }

    private var gatherDistance = 0f

    /**
     * Svislý posun položky během příjezdu. Položky nad zprávou (v obráceném seznamu vyšší index)
     * přijíždějí shora, pod ní zdola — sjíždějí se k ní. Vzdálenější jedou z dál, takže se to
     * rozvine od zprávy ven.
     */
    fun shiftOf(key: Any): Float {
        val pinned = pinnedKey ?: return 0f
        if (key == pinned) return 0f
        val index = indexOf(key)
        if (index < 0) return 0f
        val steps = min(abs(index - pinnedIndex), MAX_SPREAD_STEPS)
        val direction = if (index > pinnedIndex) -1f else 1f
        return (1f - gather.value) * gatherDistance * (1f + SPREAD_PER_STEP * (steps - 1)) * direction
    }

    fun alphaOf(key: Any): Float {
        val pinned = pinnedKey ?: return 1f
        return if (key == pinned) 1f else gather.value.coerceIn(0f, 1f)
    }
}

/** Jak moc jede dál každá další položka od zprávy; od [MAX_SPREAD_STEPS] už stejně. */
private const val SPREAD_PER_STEP = 0.35f
private const val MAX_SPREAD_STEPS = 5
