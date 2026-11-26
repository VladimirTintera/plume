package eu.tintera.plume.core.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalContext
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout

/**
 * Jedna lišta oblasti chatu, kterou kreslí scéna Chatů (`PaneScene`), ne obrazovky.
 *
 * Hlavička nad kartou ([PaneHeader]) se ve scéně nekreslí v obrazovce, ale v řadě nad kartami:
 * obrazovka ji jen popíše a [PaneHeader] ji sem pošle i s kompozičním kontextem své položky
 * (`currentCompositionLocalContext`). Akce v liště tak dál mají úložiště ViewModelů, barvy a účet
 * své položky a `koinViewModel` v nich vrátí správnou instanci. Scéna lištu vykreslí v místě
 * ([SceneBarSlot]) nad kartou téže položky; úseky všech sloupců mají jednu výšku, takže lícují
 * rozvržením, ne měřením v okně.
 *
 * Mimo scénu ([LocalSceneBar] `null`: telefon, knihovní třípanel, Nastavení) kreslí [PaneHeader]
 * hlavičku na místě jako dřív.
 *
 * **Při přechodu mezi scénou a ostatními rozvrženími vzniká hlavička znovu** (jiné místo
 * v kompozici). Rozepsané hledání v chatu přežije, protože text drží ViewModel (`initialQuery`);
 * fokus pole ne.
 */
@Stable
class SceneBar internal constructor() {
    private val contents = mutableStateMapOf<Any, SceneBarContent>()
    private val heights = mutableStateMapOf<Any, Int>()

    internal fun set(key: Any, content: SceneBarContent) {
        if (contents[key] != content) contents[key] = content
    }

    internal fun clear(key: Any, owner: Any) {
        if (contents[key]?.owner === owner) contents.remove(key)
    }

    internal fun content(key: Any): SceneBarContent? = contents[key]

    /** Má položka [key] hlavičku v liště? Pak karta pod ní horní inset nepotřebuje. */
    fun has(key: Any): Boolean = key in contents

    internal fun reportHeight(key: Any, height: Int) {
        if (heights[key] != height) heights[key] = height
    }

    internal fun forgetHeight(key: Any) {
        heights.remove(key)
    }

    /** Výška řady: nejvyšší úsek, ať úseky všech sloupců (a karty pod nimi) lícují. */
    internal val rowHeight: Int get() = heights.values.maxOrNull() ?: 0
}

/** Co hlavička položky poslala do lišty: obsah a kontext, ve kterém se má složit. */
internal class SceneBarContent(
    val owner: Any,
    /** Horní inset okna lišta zkonzumuje, jako by to udělala karta položky (`PaneCard`). */
    val consumeTop: Boolean,
    val context: CompositionLocalContext,
    val content: @Composable () -> Unit,
)

/** Lišta scény Chatů; `null` mimo ni — hlavička se pak kreslí na místě. */
val LocalSceneBar = staticCompositionLocalOf<SceneBar?> { null }

@Composable
fun rememberSceneBar(): SceneBar = remember { SceneBar() }

/** Kam pošle [PaneHeader] svou hlavičku: lišta scény a klíč položky; `null` = kreslí ji na místě. */
internal class SceneBarTarget(val bar: SceneBar, val key: Any, val consumeTop: Boolean)

internal val LocalSceneBarTarget = staticCompositionLocalOf<SceneBarTarget?> { null }

/**
 * Pošle hlavičku [body] do lišty scény pod klíčem cíle. Kreslí ji [SceneBarSlot] v kontextu
 * položky; na místě nezůstane nic.
 */
@Composable
internal fun PublishToSceneBar(target: SceneBarTarget, context: CompositionLocalContext, body: @Composable () -> Unit) {
    val owner = remember { Any() }
    val latest by rememberUpdatedState(body)
    val stable: @Composable () -> Unit = remember { { latest() } }
    // Kontext je po každé změně lokálu nový objekt; obsah zůstává táž lambda, takže se hlavička
    // v liště jen překreslí, nevznikne znovu.
    SideEffect { target.bar.set(target.key, SceneBarContent(owner, target.consumeTop, context, stable)) }
    DisposableEffect(target.bar, target.key) {
        onDispose { target.bar.clear(target.key, owner) }
    }
}

/**
 * Úsek lišty nad kartou položky [key]: hlavička, kterou sem poslala ([PaneHeader]), složená
 * v jejím kontextu. Výška je výška celé řady ([SceneBar.rowHeight]), obsah sedí dole — u karty.
 */
@Composable
fun SceneBarSlot(bar: SceneBar, key: Any, modifier: Modifier = Modifier) {
    val content = bar.content(key)
    DisposableEffect(bar, key) { onDispose { bar.forgetHeight(key) } }
    Box(
        modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                // Vlastní výška se hlásí bez vlivu řady, jinak by řada nikdy neklesla.
                val placeable = measurable.measure(constraints.copy(minHeight = 0))
                bar.reportHeight(key, if (content == null) 0 else placeable.height)
                val height = maxOf(placeable.height, if (content == null) 0 else bar.rowHeight)
                layout(placeable.width, height) { placeable.place(0, height - placeable.height) }
            },
        contentAlignment = Alignment.BottomStart,
    ) {
        if (content != null) {
            // Konstantní popis insetů, podmínka modifikátor jen celý přidá (Gotchas v CLAUDE.md).
            Box(if (content.consumeTop) Modifier.consumeWindowInsets(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)) else Modifier) {
                CompositionLocalProvider(content.context) { content.content() }
            }
        }
    }
}
