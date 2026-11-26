package eu.tintera.plume.app.shell

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.CompositionLocalProvider
import eu.tintera.plume.core.adaptive.LocalSceneBar
import eu.tintera.plume.core.adaptive.SceneBar
import eu.tintera.plume.core.adaptive.SceneBarSlot
import eu.tintera.plume.core.adaptive.rememberSceneBar
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import eu.tintera.plume.core.adaptive.PaneInfo
import eu.tintera.plume.core.adaptive.PaneRole
import eu.tintera.plume.core.adaptive.paneInfo

/*
 * Vlastní scéna Chatů: seznam | oblast chatu (`docs/navrh-scena-chatu.md`, varianta D).
 *
 * PROTOTYP za přepínačem (`AppSettings.chatScene`). Vypnutý = dosavadní třípanel z knihovny
 * (`rememberListDetailSceneStrategy`), strategie vrací `null`.
 *
 * Co scéna dělá jinak než `ThreePaneScaffoldScene`:
 * - **Boční sloupec patří k detailu pod sebou.** Kreslí se jen boční položka NAD posledním
 *   detailem; zakrytý profil jiného chatu se nekreslí, i kdyby v zásobníku zůstal.
 * - **Odjezd sloupce drží scéna sama** (`AnimatedVisibility` s poslední položkou), takže
 *   `rememberWithLeavingExtras` a „prázdný třetí sloupec" tu nejsou potřeba.
 * - **Gesto zpět uvnitř scény zavírá sloupec už během tahu** (dnes nejde: předchozí stav se
 *   v knihovně počítá s toutéž direktivou tří oddílů).
 * - **Šířky jsou naše** (táhla `PaneResize`), knihovna z rozvržení nic nepočítá.
 *
 * Karty, hlavičky a okraje kreslí dál dekorátor položek (`PaneCard`) podle `LocalPaneNeighbours`,
 * takže vzhled zůstává stejný jako s knihovnou.
 */

/** Co scéna Chatů z položek kreslí. */
internal class ChatPanes<E>(
    /** Klíč scény z metadat (`PaneInfo.scene`): hlavní seznam, nebo archiv. */
    val sceneKey: Any,
    /** Všechny položky téže scény v pořadí zásobníku, i ty, které se nekreslí (zakryté). */
    val sceneEntries: List<E>,
    val list: E,
    /** `null` = placeholder „vyber chat". */
    val detail: E?,
    /** Boční sloupec nad [detail]; `null` = zavřený. */
    val side: E?,
)

/**
 * Položky scény Chatů z konce zásobníku, jako je bere knihovna (`ListDetailSceneStrategy`):
 * souvislý konec zásobníku s rolí panelu, z něj položky scény poslední položky. Scénu vrací jen
 * pro scénu, kterou [isChatScene] uzná (ne Nastavení), a jen se seznamem.
 *
 * **Boční sloupec je poslední boční položka NAD posledním detailem.** Boční položka pod ním
 * patří k jinému chatu (profil, nad který se otevřela jiná konverzace) a nekreslí se.
 */
internal fun <E> selectChatPanes(
    entries: List<E>,
    paneOf: (E) -> PaneInfo?,
    isChatScene: (Any) -> Boolean,
): ChatPanes<E>? {
    val top = entries.lastOrNull() ?: return null
    val sceneKey = paneOf(top)?.scene ?: return null
    if (!isChatScene(sceneKey)) return null
    val scene = ArrayList<E>()
    val roles = ArrayList<PaneRole>()
    for (index in entries.indices.reversed()) {
        val info = paneOf(entries[index]) ?: break
        if (info.scene != sceneKey) continue
        scene += entries[index]
        roles += info.role
    }
    scene.reverse()
    roles.reverse()
    val listIndex = roles.lastIndexOf(PaneRole.List)
    if (listIndex < 0) return null
    val detailIndex = roles.lastIndexOf(PaneRole.Detail)
    val sideIndex = roles.lastIndexOf(PaneRole.Extra).takeIf { it > detailIndex }
    return ChatPanes(
        sceneKey = sceneKey,
        sceneEntries = scene,
        list = scene[listIndex],
        detail = scene.getOrNull(detailIndex),
        side = sideIndex?.let(scene::get),
    )
}

/** Rozměry, které scéně dává shell (táhla, šířka okna). */
@Immutable
internal data class ChatSceneLayout(
    /** `false` = jen seznam přes celou plochu: není z čeho vybrat (prázdný archiv), viz `nothingToPick`. */
    val twoPane: Boolean,
    /** Boční sloupec se vejde vedle seznamu i konverzace; jinak seznam při otevřeném sloupci ustoupí. */
    val sideBesideList: Boolean,
    /** Šířka seznamu (táhlo seznamu, i ve dvou panelech). */
    val listWidth: Dp,
    /** Šířka bočního sloupce včetně okrajů karty (táhlo profilu). */
    val sideWidth: Dp,
    /** Za tahu táhlem a jeho dojezdu jdou šířky přímo, bez animace (`DraggedWidth`). */
    val instant: Boolean,
)

/**
 * Scéna Chatů na široké ploše. [layout] `null` = vypnutá (přepínač, telefon): rozhoduje další
 * strategie. Stojí za `SheetSceneStrategy` (sheet se kreslí nad ní) a před knihovní list-detail,
 * která dál kreslí Nastavení.
 */
internal class ChatSceneStrategy(
    private val layout: ChatSceneLayout?,
    private val isChatScene: (Any) -> Boolean,
    private val detailPlaceholder: @Composable () -> Unit,
) : SceneStrategy<NavKey> {
    override fun SceneStrategyScope<NavKey>.calculateScene(entries: List<NavEntry<NavKey>>): Scene<NavKey>? {
        val layout = layout ?: return null
        val panes = selectChatPanes(entries, { it.paneInfo }, isChatScene) ?: return null
        val previousEntries = entries.dropLast(1)
        // Zpět, po kterém zůstane táž scéna, obslouží scéna sama (viz ChatScene.content).
        val previous = selectChatPanes(previousEntries, { it.paneInfo }, isChatScene)
            ?.takeIf { it.sceneKey == panes.sceneKey }
        return ChatScene(
            key = ChatSceneKey(panes.sceneKey),
            panes = panes,
            previous = previous,
            allEntries = entries,
            layout = layout,
            detailPlaceholder = detailPlaceholder,
            onBack = onBack,
        )
    }
}

/**
 * Jeden klíč pro všechny stavy téže scény (jen seznam, s placeholderem, s chatem, s bočním
 * sloupcem): `NavDisplay` mezi nimi nepřechází jako mezi obrazovkami, mění se jen obsah scény.
 */
private data class ChatSceneKey(val scene: Any)

private class ChatScene(
    override val key: Any,
    private val panes: ChatPanes<NavEntry<NavKey>>,
    /** Stav po zpět, zůstane-li táž scéna; `null` = zpět scénu opouští a řeší ho `NavDisplay`. */
    private val previous: ChatPanes<NavEntry<NavKey>>?,
    private val allEntries: List<NavEntry<NavKey>>,
    private val layout: ChatSceneLayout,
    private val detailPlaceholder: @Composable () -> Unit,
    private val onBack: () -> Unit,
) : Scene<NavKey> {
    override val entries: List<NavEntry<NavKey>> = panes.sceneEntries
    override val previousEntries: List<NavEntry<NavKey>> = allEntries.dropLast(1)

    override val content: @Composable () -> Unit = {
        ChatSceneContent(key, panes, previous, layout, detailPlaceholder, onBack)
    }

    // Scéna se porovnává při každém přepočtu (`SceneState`), jako knihovní: táž položka = táž scéna.
    override fun equals(other: Any?): Boolean =
        this === other || (
            other is ChatScene &&
                key == other.key &&
                layout == other.layout &&
                allEntries == other.allEntries &&
                panes.sceneEntries == other.panes.sceneEntries &&
                (previous == null) == (other.previous == null)
            )

    override fun hashCode(): Int = ((key.hashCode() * 31 + layout.hashCode()) * 31 + allEntries.hashCode()) * 31 +
        panes.sceneEntries.hashCode()
}

/** Gesto zpět uvnitř scény se hlásí pod klíčem scény a jejími položkami (jako `ThreePaneScaffoldSceneInfo`). */
private data class ChatSceneInfo(val key: Any, val entries: List<Any>) : NavigationEventInfo()

@Composable
private fun ChatSceneContent(
    key: Any,
    panes: ChatPanes<NavEntry<NavKey>>,
    previous: ChatPanes<NavEntry<NavKey>>?,
    layout: ChatSceneLayout,
    detailPlaceholder: @Composable () -> Unit,
    onBack: () -> Unit,
) {
    /*
     * Zpět, po kterém zůstane táž scéna (zavřít sloupec, podstránka sloupce → profil, konverzace →
     * placeholder), obslouží scéna sama, stejně jako knihovní: `NavDisplay` by mezi dvěma stavy
     * téže scény nic animovat neuměl. Za tahu se sloupec zužuje k zavření, konverzace slábne.
     */
    val gestureInfo = remember(key, panes.sceneEntries) { ChatSceneInfo(key, panes.sceneEntries.map { it.contentKey }) }
    val gestureState = rememberNavigationEventState(currentInfo = gestureInfo)
    NavigationBackHandler(
        state = gestureState,
        isBackEnabled = previous != null,
        onBackCompleted = onBack,
    )
    val transitionState = gestureState.transitionState
    val sideCloses = previous != null && panes.side != null && previous.side == null
    val detailChanges = previous != null && panes.side == null &&
        previous.detail?.contentKey != panes.detail?.contentKey
    // Kolik z cesty k předchozímu stavu gesto ukazuje (0 = nic); po zrušení se vrátí na nulu.
    val backProgress = remember { Animatable(0f) }
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    LaunchedEffect(transitionState) {
        if (transitionState is NavigationEventTransitionState.InProgress) {
            backProgress.snapTo(BackEasing.transform(transitionState.latestEvent.progress) * BACK_PROGRESS_RATIO)
        } else {
            backProgress.animateTo(0f, effects)
        }
    }

    if (!layout.twoPane) {
        Box(Modifier.fillMaxSize()) { PaneSlot(panes.list, placeholder = null) }
        return
    }

    val listShown = panes.side == null || layout.sideBesideList
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<Dp>()
    val listWidth by animateDpAsState(layout.listWidth, if (layout.instant) snap() else spatial, label = "chat-scene-list")
    val sideWidth by animateDpAsState(layout.sideWidth, if (layout.instant) snap() else spatial, label = "chat-scene-side")

    // Jedna lišta oblasti chatu: hlavičky detailu a bočního sloupce kreslí scéna (`SceneBar`).
    val sceneBar = rememberSceneBar()
    Row(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = listShown,
            enter = expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec(), expandFrom = Alignment.Start) +
                fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            // K nule bez překmitu (CLAUDE.md, „Animace nechávej na MotionScheme").
            exit = shrinkHorizontally(MaterialTheme.motionScheme.fastEffectsSpec(), shrinkTowards = Alignment.Start) +
                fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Box(Modifier.width(listWidth).fillMaxHeight()) { PaneSlot(panes.list, placeholder = null) }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .graphicsLayer { if (detailChanges) alpha = 1f - backProgress.value },
        ) {
            CompositionLocalProvider(LocalSceneBar provides sceneBar) {
                PaneSlot(panes.detail, placeholder = detailPlaceholder, bar = sceneBar)
            }
        }
        // Zavíraný sloupec odjíždí s poslední položkou, kterou ukazoval.
        val lastSide = remember { mutableStateOf<NavEntry<NavKey>?>(null) }
        SideEffect { if (panes.side != null) lastSide.value = panes.side }
        val side = panes.side ?: lastSide.value
        // Po dokončeném gestu sloupec odjíždí z šířky, na kterou ho gesto zúžilo, ne z plné.
        val sideFactor = remember { mutableFloatStateOf(1f) }
        val factor = when {
            panes.side == null -> sideFactor.floatValue
            sideCloses -> 1f - backProgress.value
            else -> 1f
        }
        SideEffect { if (panes.side != null) sideFactor.floatValue = factor }
        AnimatedVisibility(
            visible = panes.side != null,
            enter = expandHorizontally(MaterialTheme.motionScheme.defaultSpatialSpec(), expandFrom = Alignment.Start) +
                fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
            exit = shrinkHorizontally(MaterialTheme.motionScheme.fastEffectsSpec(), shrinkTowards = Alignment.Start) +
                fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Box(Modifier.width(sideWidth * factor).fillMaxHeight()) {
                if (side != null) {
                    CompositionLocalProvider(LocalSceneBar provides sceneBar) {
                        PaneSlot(side, placeholder = null, bar = sceneBar)
                    }
                }
            }
        }
    }
}

/**
 * Obsah jednoho místa scény: položka [entry], bez ní [placeholder]. Výměna položky (jiný chat,
 * profil → úprava) se prolne, jako `AnimatedPane` v knihovně.
 *
 * Přechod jde po `contentKey`, ne po položce: dekorovaná položka je v každé kompozici nový objekt
 * a `AnimatedContent` nad ní by „přecházel" i mezi dvěma kopiemi téhož. Položka se proto hledá
 * podle klíče; odcházející se kreslí svou poslední kopií, dokud nedojede.
 */
@Composable
private fun PaneSlot(
    entry: NavEntry<NavKey>?,
    placeholder: (@Composable () -> Unit)?,
    /** Lišta scény: nad kartou položky se kreslí její úsek; `null` = seznam, hlavička je v něm. */
    bar: SceneBar? = null,
) {
    val known = remember { HashMap<Any, NavEntry<NavKey>>() }
    val target: Any = entry?.contentKey ?: NoEntry
    if (entry != null) known[entry.contentKey] = entry
    val transition = updateTransition(target, label = "chat-scene-slot")
    val enter = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val exit = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    transition.AnimatedContent(
        modifier = Modifier.fillMaxSize(),
        transitionSpec = { fadeIn(enter) togetherWith fadeOut(exit) },
    ) { key ->
        val shown = known[key]
        when {
            shown != null && bar != null -> Column(Modifier.fillMaxSize()) {
                SceneBarSlot(bar, key)
                // Pod lištou už stavová lišta není: karta horní inset dostat nesmí. Popis insetů je
                // konstantní, podmínka modifikátor jen celý přidá nebo odebere (Gotchas v CLAUDE.md).
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(if (bar.has(key)) Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)) else Modifier),
                ) { shown.Content() }
            }
            shown != null -> shown.Content()
            key == NoEntry -> placeholder?.invoke()
        }
    }
    // Po dojetí si místo pamatuje jen to, co ukazuje.
    SideEffect {
        if (transition.currentState == transition.targetState && !transition.isRunning) {
            known.keys.retainAll(setOf(target))
        }
    }
}

/** Klíč místa bez položky (placeholder). */
private object NoEntry

/** Průběh gesta zpět → kolik z cesty k předchozímu stavu ukázat; jako `ThreePaneScaffoldPredictiveBackEasing`. */
private val BackEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

/**
 * Jak daleko gesto zavírání vede: knihovna posouvá scaffold o 10–20 % ke stavu po zpět
 * (`TriplePaneProgressRatio`). Sloupec tu jde o čtvrtinu šířky — zúžení je vidět, ale nezavře
 * se celý dřív, než uživatel gesto dokončí.
 */
private const val BACK_PROGRESS_RATIO = 0.25f
