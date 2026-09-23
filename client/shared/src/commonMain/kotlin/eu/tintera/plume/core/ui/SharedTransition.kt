package eu.tintera.plume.core.ui

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation3.ui.LocalNavAnimatedContentScope

/**
 * Scope pro sdílené prvky mezi obrazovkami. Poskytuje ho kořenové téma, protože
 * [androidx.compose.animation.SharedTransitionLayout] musí obalovat celý navigační strom — jinak
 * sdílený prvek nepřežije výměnu obrazovky.
 */
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }

/**
 * Sdílený prvek, jehož viditelnost řídí volající.
 *
 * Běžné `sharedElement` chce `AnimatedVisibilityScope`, který vzniká jen uvnitř `AnimatedVisibility`
 * nebo `AnimatedContent`. Položka `LazyColumn` ani obsah vstupního pole v takovém scope nejsou,
 * takže se páruje přes variantu s ručně řízenou viditelností: právě jedna strana je `visible`,
 * druhá `false`, a přepnutí spustí přechod mezi jejich pozicemi.
 *
 * Když scope chybí (teoreticky mimo `AppTheme`), modifikátor se neaplikuje a vykreslení
 * funguje dál bez animace.
 */
@Composable
fun Modifier.sharedFlight(key: String, visible: Boolean): Modifier {
    val scope = LocalSharedTransitionScope.current ?: return this

    return with(scope) {
        this@sharedFlight.sharedElementWithCallerManagedVisibility(
            sharedContentState = rememberSharedContentState(key = key),
            visible = visible,
            boundsTransform = { _, _ -> flightSpec },
        )
    }
}

/**
 * Průlet textu z pole do bubliny. Jemný bounce jako zbytek aplikace, ale kratší — je to dlouhá
 * dráha přes celou obrazovku a delší doba by působila líně.
 */
private val flightSpec = spring<Rect>(
    dampingRatio = 0.8f,
    stiffness = Spring.StiffnessMediumLow,
)

/**
 * Je právě vidět dvoupanelové rozvržení?
 *
 * Sdílený prvek má smysl jen tam, kde se obrazovky **vyměňují**. V dvoupanelovém rozvržení zůstává
 * seznam vedle konverzace, takže obě strany páru existují naráz — avatar by odletěl z položky,
 * která je pořád vidět, a navíc by se o jeden klíč přetahovaly dva prvky. Shell tenhle příznak
 * poskytuje, aby o něm obrazovky nemusely nic vědět.
 */
val LocalTwoPane = compositionLocalOf { false }

/**
 * Plocha, ve které se sdílený prvek letící do téhle obrazovky smí kreslit.
 *
 * **Sdílený prvek se během letu kreslí v PŘEKRYVU nad celou aplikací, mimo ořezy rodičů** — tedy
 * i přes hlavičku obrazovky. Fotka, která v konverzaci zpola mizí pod hlavičkou, tak po návratu
 * z prohlížeče dosedla na své místo, ale nakreslila se PŘES hlavičku, a teprve na konci přechodu
 * (kdy se kreslení vrátí do seznamu) hlavička blikla přes ni. Obrazovka proto řekne, kde ji
 * ořezává vlastní seznam, a [sharedNavElement] ten ořez použije i v překryvu.
 *
 * Hranice se drží v OBYČEJNÉM POLI, ne ve stavu: zapisují se při každém layoutu a zápis stavu by
 * tím rekomponoval celou obrazovku — stejně jako kotva nabídky nad zprávou.
 */
@Stable
class SharedElementClip {
    /** Hranice plochy v souřadnicích kořene kompozice; `null`, dokud se plocha nezměřila. */
    var bounds: Rect? = null
}

@Composable
fun rememberSharedElementClip(): SharedElementClip = remember { SharedElementClip() }

/**
 * Změří plochu, do které se ořezávají sdílené prvky pod ní (viz [SharedElementClip]).
 *
 * Bere se [boundsInRoot], ne `boundsInWindow`: překryv kreslí `SharedTransitionLayout` ve SVÝCH
 * souřadnicích, a ten stojí hned v kořeni kompozice (viz `AppTheme`).
 */
fun Modifier.sharedElementClipArea(clip: SharedElementClip): Modifier =
    onGloballyPositioned { clip.bounds = it.boundsInRoot() }

val LocalSharedElementClip = compositionLocalOf<SharedElementClip?> { null }

/**
 * Sdílený prvek napříč obrazovkami `NavDisplay` (položka seznamu → detail).
 *
 * Na rozdíl od [sharedFlight] se viditelnost neřídí ručně: obě strany jsou v `AnimatedContent`
 * NavDisplaye, takže si scope řekne sám, která odchází a která přichází.
 *
 * Když obrazovka poskytne [LocalSharedElementClip], ořezává se prvek v překryvu do té plochy —
 * ne skokem, ale **spolu s letem** (viz [sharedElementClipRect]): na začátku, kdy je prvek ještě
 * na druhé obrazovce, neořezává nic, a dosedne přesně tam, kde ho ořezává seznam. Skokový ořez by
 * hned v prvním snímku ukrojil kus celoobrazovkové fotky.
 */
@Composable
fun Modifier.sharedNavElement(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    if (LocalTwoPane.current) return this
    val scope = LocalNavAnimatedContentScope.current
    val area = LocalSharedElementClip.current

    return with(shared) {
        val state = rememberSharedContentState(key = key)
        if (area == null) {
            this@sharedNavElement.sharedElement(
                sharedContentState = state,
                animatedVisibilityScope = scope,
                boundsTransform = { _, _ -> flightSpec },
            )
        } else {
            // Kreslí se prvek, který se stává viditelným, takže jeho přechod jde PreEnter → Visible.
            // Táž pružina jako let, aby ořez dosedl ve stejnou chvíli jako prvek sám.
            val settled = scope.transition.animateFloat(
                transitionSpec = { clipSpec },
                label = "shared-element-clip",
            ) { if (it == EnterExitState.Visible) 1f else 0f }
            val clip = remember(area, settled) { ContentAreaClip(area, settled) }
            this@sharedNavElement.sharedElement(
                sharedContentState = state,
                animatedVisibilityScope = scope,
                boundsTransform = { _, _ -> flightSpec },
                clipInOverlayDuringTransition = clip,
            )
        }
    }
}

private val clipSpec = spring<Float>(
    dampingRatio = 0.8f,
    stiffness = Spring.StiffnessMediumLow,
)

private class ContentAreaClip(
    private val area: SharedElementClip,
    private val settled: State<Float>,
) : SharedTransitionScope.OverlayClip {
    // Tentýž objekt, jak doporučuje dokumentace — `getClipPath` běží při každém snímku letu.
    private val path = Path()

    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path? {
        val content = area.bounds ?: return null
        path.reset()
        path.addRect(sharedElementClipRect(content, bounds, settled.value))
        return path
    }
}

/**
 * Ořez prvku v překryvu: od „neořezává nic" (`settled` = 0) k ploše obrazovky (`settled` = 1).
 *
 * Otevřený ořez se nebere jako nějaké velké číslo, ale jako **obal plochy a samotného prvku** —
 * ten prvek z definice obsahuje celý, takže na začátku letu neukrojí nic, a s průběhem se stáhne
 * na plochu. Fotka tím podjede pod hlavičku, místo aby se přes ni nakreslila.
 */
internal fun sharedElementClipRect(content: Rect, bounds: Rect, settled: Float): Rect {
    val open = Rect(
        left = minOf(content.left, bounds.left),
        top = minOf(content.top, bounds.top),
        right = maxOf(content.right, bounds.right),
        bottom = maxOf(content.bottom, bounds.bottom),
    )
    return lerp(open, content, settled.coerceIn(0f, 1f))
}

/**
 * Totéž pro text.
 *
 * Text se sdílí přes `sharedBounds` + `ScaleToBounds`, ne přes `sharedElement`: název chatu má
 * v seznamu jinou velikost písma než v hlavičce konverzace a prosté sdílení prvku by nechalo text
 * přeskládat skokem na konci letu. `ScaleToBounds` místo toho celý text plynule zvětší.
 */
@Composable
fun Modifier.sharedNavText(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    if (LocalTwoPane.current) return this

    return with(shared) {
        this@sharedNavText.sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = LocalNavAnimatedContentScope.current,
            boundsTransform = { _, _ -> flightSpec },
            resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds(
                contentScale = ContentScale.FillWidth,
                alignment = Alignment.CenterStart,
            ),
        )
    }
}

/*
 * Klíče páru seznam ↔ konverzace. Obsahují id chatu, jinak by se při rychlém přepnutí mezi chaty
 * spároval avatar jednoho chatu s avatarem druhého.
 */
fun chatAvatarKey(chatId: Long): String = "chat-avatar-$chatId"

fun chatTitleKey(chatId: Long): String = "chat-title-$chatId"

/**
 * Letí fotka z mřížky sdílených médií v profilu, ne z bubliny v konverzaci?
 *
 * Poskytuje ho mřížka a prohlížeč otevřený z ní (podle `MediaRoute`), viz [photoKey].
 */
val LocalSharedMediaFlight = compositionLocalOf { false }

/**
 * Klíč páru bublina ↔ prohlížeč fotky.
 *
 * Id zprávy stačí — fotka je ve zprávě nejvýš jedna a v rámci chatu jsou id jedinečná.
 *
 * **Táž fotka je ale v aplikaci na DVOU místech: v bublině a v mřížce sdílených médií v profilu.**
 * Se společným klíčem se spárovaly navzájem: otevření profilu (a návrat z něj) je výměna obrazovek
 * jako každá jiná, takže při ní bublina i dlaždice existují naráz a fotka létala mezi nimi —
 * přes celou obrazovku a přes hlavičku. Klíč proto nese i to, ODKUD se letí; která strana platí,
 * říká [LocalSharedMediaFlight]. Prohlížeč ho dostane z routy, takže páruje s tou, ze které
 * se otevřel.
 */
@Composable
fun photoKey(messageId: Long): String =
    if (LocalSharedMediaFlight.current) "shared-media-photo-$messageId" else "photo-$messageId"

/**
 * Klíč páru náhled přílohy nad vstupem ↔ celoobrazovkový náhled.
 */
fun attachmentKey(path: String): String = "attachment-$path"
