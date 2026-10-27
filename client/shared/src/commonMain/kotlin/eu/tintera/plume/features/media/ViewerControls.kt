package eu.tintera.plume.features.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.isBackPressed
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isForwardPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import eu.tintera.plume.core.ui.WithTooltip
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chevron_left_24dp
import eu.tintera.plume.resources.chevron_right_24dp
import eu.tintera.plume.resources.media_next
import eu.tintera.plume.resources.media_previous
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Co udělá klávesa v prohlížeči médií. */
internal enum class ViewerKey {
    Previous, Next, ZoomIn, ZoomOut, ZoomReset,
    /** Home / End: první a poslední NAČTENÉ médium (výřez se pak dotáhne dál). */
    First, Last,
    /** F: celá obrazovka okna (desktop), jinde celá obrazovka prohlížeče. */
    ToggleFullscreen,
    // Jen u videa:
    PlayPause, SeekBack, SeekForward, ToggleMute,
    /** Ctrl+C (⌘C): médium do schránky. */
    Copy,
    /** Ctrl+S (⌘S): uložit do galerie (na desktopu do Stažených). */
    Save,
    /** Delete (na macOS ⌘⌫): smazat — s potvrzením, jako z nabídky. */
    Delete,
}

/**
 * Klávesa v prohlížeči: ←/→ listují (v RTL obráceně, jako pager), +/−/0 přibližují. Esc tu není
 * — zavírá prohlížeč jako Zpět, na desktopu ho převádí Compose sám, na Androidu `MainActivity`.
 *
 * - **Šipky jen bez modifikátorů.** Alt+šipka je přepínání chatů, Shift/Ctrl+šipka patří textu.
 * - **+/−/0 i s Ctrl** (Ctrl+= / Ctrl+− / Ctrl+0 jako v prohlížeči), ne s Alt ani ⌘.
 * - **Plus a minus i podle znaku** ([char]), ne jen podle klávesy: na české klávesnici je „+"
 *   na klávese, kde anglická má „1", a „=" jinde. Numerická klávesnice má kódy vlastní.
 * - **PageUp/PageDown listují, Home/End skočí na kraj načteného výřezu.** Pořadí je logické
 *   (dřívější/pozdější), v RTL se neobrací — na rozdíl od šipek to nejsou směry na obrazovce.
 * - **F přepne celou obrazovku** u fotky i videa. Písmena podle klávesy, ne znaku — na každém
 *   rozložení jsou tam, kde je uživatel čeká.
 * - **U videa ([video]) jako na YouTube**: mezerník pauza a přehrávání, M ztlumení. Šipky
 *   posouvají ([seekWithArrows]), kde by tah nelistoval — hrající video na celé obrazovce;
 *   doleva je zpět i v RTL, jako na časové ose přehrávače.
 * - **Ctrl+C / Ctrl+S kopírují a ukládají**, i s ⌘ — podle klávesy, ne znaku (na azbuce je
 *   tam „с"). Jsou to jediné zkratky tady, které macOS bere přes ⌘.
 * - **Delete smaže**, na macOS i ⌘⌫ (tamní klávesa „delete" je Backspace a Finder maže takhle).
 *   Potvrzení obstará obrazovka, stejně jako u smazání z nabídky.
 */
internal fun viewerKey(
    key: Key,
    char: Char?,
    ctrl: Boolean,
    alt: Boolean,
    shift: Boolean,
    meta: Boolean,
    rtl: Boolean,
    video: Boolean = false,
    seekWithArrows: Boolean = false,
): ViewerKey? {
    val command = (ctrl != meta) && !alt && !shift
    if (command && key == Key.C) return ViewerKey.Copy
    if (command && key == Key.S) return ViewerKey.Save
    if (meta && !ctrl && !alt && !shift && key == Key.Backspace) return ViewerKey.Delete
    if (alt || meta) return null
    val plain = !ctrl && !shift
    return when {
        plain && seekWithArrows && key == Key.DirectionLeft -> ViewerKey.SeekBack
        plain && seekWithArrows && key == Key.DirectionRight -> ViewerKey.SeekForward
        plain && key == Key.DirectionLeft -> if (rtl) ViewerKey.Next else ViewerKey.Previous
        plain && key == Key.DirectionRight -> if (rtl) ViewerKey.Previous else ViewerKey.Next
        plain && key == Key.PageUp -> ViewerKey.Previous
        plain && key == Key.PageDown -> ViewerKey.Next
        plain && key == Key.MoveHome -> ViewerKey.First
        plain && key == Key.MoveEnd -> ViewerKey.Last
        plain && key == Key.F -> ViewerKey.ToggleFullscreen
        plain && key == Key.Delete -> ViewerKey.Delete
        plain && video && key == Key.Spacebar -> ViewerKey.PlayPause
        plain && video && key == Key.M -> ViewerKey.ToggleMute
        key == Key.NumPadAdd || key == Key.Plus || char == '+' -> ViewerKey.ZoomIn
        key == Key.Equals && !shift || char == '=' -> ViewerKey.ZoomIn
        key == Key.NumPadSubtract || key == Key.Minus || char == '-' -> ViewerKey.ZoomOut
        key == Key.NumPad0 || (key == Key.Zero && !shift) || char == '0' -> ViewerKey.ZoomReset
        else -> null
    }
}

/**
 * Pohyb myši v prohlížeči: `true` po pohybu, `false` po [HIDE_AFTER_MS] klidu. Ukazuje šipky
 * po stranách jen tomu, kdo má myš — dotyk je nepotřebuje (listuje tahem) a překážely by.
 * Stejný vzor jako ovládání hovoru (`CallControls`). Událost se jen pozoruje (`Initial`,
 * nic se nekonzumuje).
 */
internal class MouseActivity {
    var active by mutableStateOf(false)
        private set
    var moves by mutableIntStateOf(0)
        private set

    fun onMouseMove() {
        active = true
        moves++
    }

    fun hide() {
        active = false
    }
}

internal fun Modifier.trackMouse(activity: MouseActivity): Modifier = pointerInput(activity) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type == PointerEventType.Move && event.changes.any { it.type == PointerType.Mouse }) activity.onMouseMove()
        }
    }
}

/**
 * Kdy svislé kolečko myši přelistuje: Windows Fotky i Telegram Desktop listují obyčejným
 * kolečkem, kdežto pager bere jen Shift + kolečko (svislé u vodorovného posunu zahodí).
 *
 * Kolečko a touchpad chodí jako tatáž událost a rozeznat se nedají, liší se jen krokem:
 * **zub kolečka je celé číslo, touchpad posílá zlomky** (`scrollDelta` je v zubech).
 * - **Gesto** jsou události bez mezery delší než [GESTURE_GAP_MS]; dojezd touchpadu (setrvačnost
 *   na macOS) do gesta patří taky.
 * - **Poprvé v gestu se listuje po nasčítání jednoho zubu** — zub kolečka hned, touchpad po
 *   kousku tahu, ne po prvním drobném pohybu.
 * - **Další stránka v témže gestu jen za celý zub**, a ne častěji než po [NOTCH_INTERVAL_MS].
 *   Roztočené kolečko tak listuje dál (jako ve Fotkách), tah po touchpadu přelistuje jen jednou
 *   — jinak by delší tah se setrvačností přeskočil půl chatu.
 *
 * Kladný krok = kolečko dolů = další (novější) médium, jako posun v konverzaci.
 */
internal class WheelPaging {
    private var accumulated = 0f
    private var lastEventAt: Long? = null
    private var pagedAt: Long? = null

    /** Krok kolečka [delta] (v zubech, kladný dolů) v čase [atMillis]; vrací −1, 0, nebo 1 stránku. */
    fun onScroll(delta: Float, atMillis: Long): Int {
        val last = lastEventAt
        if (last == null || atMillis - last > GESTURE_GAP_MS) {
            accumulated = 0f
            pagedAt = null
        }
        lastEventAt = atMillis
        val paged = pagedAt
        if (paged != null) {
            val notch = abs(delta) >= 1f && abs(delta - delta.roundToInt()) < NOTCH_TOLERANCE
            if (!notch || atMillis - paged < NOTCH_INTERVAL_MS) return 0
            pagedAt = atMillis
            return if (delta > 0) 1 else -1
        }
        accumulated += delta
        if (abs(accumulated) < 1f) return 0
        pagedAt = atMillis
        return if (accumulated > 0) 1 else -1
    }
}

/**
 * Myš nad snímky, co neudělá stránka sama:
 * - **svislé kolečko listuje** ([WheelPaging]) — jen událost, kterou nikdo nespotřeboval: Ctrl +
 *   kolečko a kolečko nad přiblíženou fotkou si bere stránka (přiblížení, posun), Shift +
 *   kolečko pager,
 * - **boční tlačítka myši** (zpět / vpřed) listují na předchozí a další,
 * - **pravé tlačítko** otevře nabídku u kurzoru ([onContextMenu], souřadnice v prvku).
 *
 * Tlačítka se berou v průchodu `Initial` (od kořene k listům) a spotřebují se celá, i pohyb
 * mezi stisknutím a puštěním: stránka by jinak pravé tlačítko vzala jako ťuknutí (přepnutí
 * celé obrazovky) a tah s ním jako posun přiblížené fotky. Kolečko naopak až v `Main`, po
 * stránce, ať se pozná, jestli ho spotřebovala.
 */
internal fun Modifier.viewerMouse(
    paging: WheelPaging,
    onPage: (step: Int) -> Unit,
    onContextMenu: (Offset) -> Unit,
): Modifier = pointerInput(paging) {
    awaitPointerEventScope {
        // Pravé tlačítko je stisknuté nad prohlížečem a stisk jsme si vzali.
        var secondary = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull() ?: continue
            if (change.type != PointerType.Mouse) continue
            val buttons = event.buttons
            when {
                event.type == PointerEventType.Press && buttons.isBackPressed -> {
                    change.consume()
                    onPage(-1)
                }
                event.type == PointerEventType.Press && buttons.isForwardPressed -> {
                    change.consume()
                    onPage(1)
                }
                event.type == PointerEventType.Press && buttons.isSecondaryPressed && !buttons.isPrimaryPressed -> {
                    secondary = true
                    change.consume()
                }
                secondary && event.type == PointerEventType.Release && !buttons.isSecondaryPressed -> {
                    secondary = false
                    change.consume()
                    onContextMenu(change.position)
                }
                secondary -> change.consume()
            }
        }
    }
}.pointerInput(paging) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent()
            if (event.type != PointerEventType.Scroll) continue
            val change = event.changes.firstOrNull() ?: continue
            if (change.isConsumed) continue
            val modifiers = event.keyboardModifiers
            if (modifiers.isCtrlPressed || modifiers.isMetaPressed || modifiers.isShiftPressed) continue
            val delta = event.changes.fold(Offset.Zero) { sum, it -> sum + it.scrollDelta }
            if (abs(delta.y) <= abs(delta.x)) continue
            val step = paging.onScroll(delta.y, change.uptimeMillis)
            if (step != 0) onPage(step)
            change.consume()
        }
    }
}

/**
 * Šipky na předchozí a další stránku u okrajů, svisle uprostřed. Ukážou se pohybem myši
 * ([activity]) a po chvíli klidu zmizí — nad šipkou zůstávají, dokud je na ní kurzor. Na kraji
 * galerie šipka do prázdna není.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BoxScope.PageArrows(
    activity: MouseActivity,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val previousHover = remember { MutableInteractionSource() }
    val nextHover = remember { MutableInteractionSource() }
    val hovered = previousHover.collectIsHoveredAsState().value || nextHover.collectIsHoveredAsState().value
    LaunchedEffect(activity.moves, hovered) {
        if (hovered) return@LaunchedEffect
        delay(HIDE_AFTER_MS)
        activity.hide()
    }
    val enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec())
    val exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec())
    Box(modifier.align(Alignment.CenterStart).padding(start = ARROW_EDGE)) {
        AnimatedVisibility(visible = activity.active && hasPrevious, enter = enter, exit = exit) {
            PageArrow(Res.drawable.chevron_left_24dp, stringResource(Res.string.media_previous), previousHover, onPrevious)
        }
    }
    Box(modifier.align(Alignment.CenterEnd).padding(end = ARROW_EDGE)) {
        AnimatedVisibility(visible = activity.active && hasNext, enter = enter, exit = exit) {
            PageArrow(Res.drawable.chevron_right_24dp, stringResource(Res.string.media_next), nextHover, onNext)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PageArrow(
    icon: org.jetbrains.compose.resources.DrawableResource,
    label: String,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
) {
    WithTooltip(label) {
        // Tonální plocha: přes světlou fotku by holá ikona zmizela.
        FilledTonalIconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            interactionSource = interactionSource,
        ) {
            Icon(painterResource(icon), contentDescription = label)
        }
    }
}

/** Jak dlouho po posledním pohybu myši šipky zůstanou. */
private const val HIDE_AFTER_MS = 2_000L

/** Odstup šipky od kraje okna. */
private val ARROW_EDGE = 12.dp

/** Mezera mezi událostmi kolečka, po které začíná nové gesto. */
private const val GESTURE_GAP_MS = 200L

/** Nejkratší odstup dvou stránek v jednom gestu kolečka — jinak by roztočené kolečko ujelo. */
private const val NOTCH_INTERVAL_MS = 150L

/** Odchylka od celého čísla, kterou ještě bereme jako zub kolečka. */
private const val NOTCH_TOLERANCE = 0.01f
