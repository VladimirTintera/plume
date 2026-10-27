package eu.tintera.plume.features.media

import androidx.compose.ui.Modifier
import eu.tintera.plume.core.ui.DraggedFile
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.blur.HazeBlurStyle
import eu.tintera.plume.features.conversation.items.spoilerBlur

/*
 * Co mají společné stránky carouselu s fotkou ([ZoomableMediaPage]) i videem ([VideoPage])
 * v prohlížeči médií, ve sdílení do chatu i v náhledu přílohy nad vstupním polem.
 *
 * `data class` kvůli přeskakování (viz `ConversationNavigation`): volající je skládá při každé
 * rekompozici znovu a lambdy si Compose pamatuje, takže dvě po sobě složené instance jsou si
 * rovné. Proto tu není zavírací tah (`Animatable`): kompilátor ho za stabilní nemá, skupina by
 * se pak porovnávala identitou a stránka by se skládala pokaždé.
 */

/**
 * Kde stránka v carouselu stojí a kolik místa má. Lambdy se čtou až při kreslení, takže se
 * stránka během přechodu galerie ↔ celá obrazovka nerekomponuje.
 */
internal data class MediaPagePlacement(
    /** Stránka, na které pager stojí. Odlistovaná fotka se vrací do základní velikosti. */
    val isCurrent: Boolean,
    /** Celá obrazovka; návrat do galerie vrací přiblížení na základní velikost. */
    val immersive: Boolean,
    val immersion: () -> Float,
    val reservedTop: Dp,
    val reservedBottom: Dp,
    val captionAlpha: () -> Float = { 1f },
    val cardBias: () -> Float = { 0f },
)

/** Co stránka udělá na gesto: zavírací tah, ťuknutí a přepnutí na celou obrazovku. */
internal data class MediaPageGestures(
    /**
     * Svislý tah přes práh. Prohlížeč se jím zavírá, sdílení jím opouští celou obrazovku;
     * `null` = svislý tah nic nedělá (sdílení v galerii — není kam odejít).
     */
    val onDismiss: (() -> Unit)?,
    /** Ťuknutí: přepne galerii a celou obrazovku, nebo odkryje spoiler — rozhodne volající. */
    val onTap: () -> Unit,
    /** Přepne na celou obrazovku: přiblížení gestem u fotky, tlačítko přehrání v galerii u videa. */
    val onEngage: () -> Unit,
    /**
     * Tah myší vytáhne snímek nebo video z okna ven (`fileDragSource`, desktop); ptá se až při
     * začátku tahu, `null` z něj = teď není co táhnout (nestažené). `null` celé = nejde vůbec
     * (sdílení, zakrytý, chráněný nebo mizející obsah). Kde jde, myš stránku tahem nezavírá —
     * na to je Esc a ✕.
     */
    val dragOut: (() -> DraggedFile?)? = null,
)

/** Snímek zakrytý spoilerem nebo kvůli citlivému obsahu, a jak se odkryje. */
internal data class MediaSpoiler(
    val revealed: Boolean,
    val onReveal: () -> Unit,
    /** Skryté kvůli citlivému obsahu — mění jen štítek (viz `SpoilerOverlay`). */
    val sensitive: Boolean = false,
    val style: HazeBlurStyle = HazeBlurStyle,
)

/** Snímek je teď zakrytý; `null` = nic nezakrývá. */
internal val MediaSpoiler?.hidden: Boolean get() = this != null && !revealed

/** Rozostření zakrytého snímku ([spoilerBlur]); bez spoileru nic. */
internal fun Modifier.spoilerBlur(spoiler: MediaSpoiler?): Modifier =
    if (spoiler == null) this else spoilerBlur(hasSpoiler = true, revealed = spoiler.revealed, style = spoiler.style)
