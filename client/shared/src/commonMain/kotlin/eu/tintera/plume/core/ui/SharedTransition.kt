package eu.tintera.plume.core.ui

import eu.tintera.plume.core.accounts.LocalAccount
import eu.tintera.plume.core.telegram.NO_ARTICLE_MEDIA
import eu.tintera.plume.core.telegram.NO_POLL_MEDIA
import eu.tintera.plume.core.telegram.POLL_QUESTION_MEDIA
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.lerp
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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
internal val flightSpec = spring<Rect>(
    dampingRatio = 0.8f,
    stiffness = Spring.StiffnessMediumLow,
)

/**
 * Stojí panely vedle sebe (dva nebo tři), místo aby se obrazovky vyměňovaly?
 *
 * Sdílený prvek má smysl jen tam, kde se obrazovky **vyměňují**. Vedle sebe zůstává seznam
 * u konverzace a konverzace u profilu, takže obě strany páru existují naráz — avatar by odletěl
 * z položky, která je pořád vidět, a navíc by se o jeden klíč přetahovaly dva prvky. Shell tenhle
 * příznak poskytuje, aby o něm obrazovky nemusely nic vědět.
 *
 * Je to příznak široké plochy, ne počtu panelů: platí i tam, kde scéna na široké ploše dočasně
 * ukazuje jeden panel (prázdný seznam bez placeholderu vedle).
 */
val LocalPanesSideBySide = compositionLocalOf { false }

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

    /**
     * Pásy nahoře a dole, které obrazovka kreslí PŘES seznam (px) — v konverzaci pruh připnutých
     * zpráv a vstupní pole. Seznam pod ně zajíždí, takže jeho vlastní hranice je nezahrnují:
     * fotka letící na místo pod polem se v překryvu kreslila přes pole a na konci letu skočila
     * za něj. Zapisuje je obrazovka po každé kompozici (výška pole se mění s klávesnicí).
     */
    var obscuredTop: Float = 0f
    var obscuredBottom: Float = 0f

    /**
     * Průhledný pás dole, přes který fotka smí letět (sklo pod vstupním polem), a tvary v něm,
     * které ji zakrývají (karta pole, tlačítko odeslání). Viz [sharedElementOcclusionBand].
     *
     * Dokud se ořezávalo rovnou čarou u horní hrany pásu, fotka letící na místo zpola pod polem
     * se v půlce letu useknula nad kartou a vypadalo to, že do pole zalézá. S pásem a tvary
     * zajede POD kartu, jako by ji karta přikryla — tak, jak v klidu leží i seznam.
     */
    var band: Rect? = null
    internal val occluders = mutableListOf<Occluder>()

    /** Část plochy, kterou je opravdu vidět; `null`, dokud se plocha nezměřila. */
    fun visibleBounds(): Rect? = bounds?.let { visibleArea(it, obscuredTop, obscuredBottom) }

    /**
     * Pás, přes který se smí letět, jen když leží hned pod viditelnou plochou. Nad polem můžou
     * být panely (návrhy odpovědí, klávesnice bota) a přes ty se letět nesmí — fotka by pak byla
     * vidět nad nimi i pod nimi zároveň. Tehdy platí ořez rovnou čarou jako dřív.
     */
    internal fun openBand(visible: Rect): Rect? {
        val band = band ?: return null
        if (occluders.none { it.bounds != null }) return null
        return band.takeIf { it.top <= visible.bottom + 1f }
    }
}

/** Tvar, který v pásu [SharedElementClip.band] přikrývá letící prvek. */
@Stable
internal class Occluder {
    var bounds: Rect? = null
    var shape: Shape = RectangleShape
}

/**
 * [SharedElementClip] pro prvky PŘES seznam, které se o letící fotku mají ořezávat svým tvarem
 * ([sharedElementOcclusionBand], [occludesSharedElements]). Zvlášť od [LocalSharedElementClip],
 * protože ten mění chování [sharedNavElement] pod sebou — a ve vstupním poli se nic nevrací.
 */
val LocalSharedElementOcclusion = compositionLocalOf<SharedElementClip?> { null }

/** Pás, přes který letící fotka smí zajet (viz [SharedElementClip.band]). */
@Composable
fun Modifier.sharedElementOcclusionBand(): Modifier {
    val clip = LocalSharedElementOcclusion.current ?: return this
    DisposableEffect(clip) { onDispose { clip.band = null } }
    return onGloballyPositioned { clip.band = it.boundsInRoot() }
}

/** Prvek v pásu, pod který letící fotka zajede — kreslí se jako by ležel nad ní. */
@Composable
fun Modifier.occludesSharedElements(shape: Shape): Modifier {
    val clip = LocalSharedElementOcclusion.current ?: return this
    val occluder = remember(clip) { Occluder() }
    // Tvar se mění (morf tlačítka, vlnka karty při nahrávání); ořez si ho čte při kreslení.
    SideEffect { occluder.shape = shape }
    DisposableEffect(clip, occluder) {
        clip.occluders += occluder
        onDispose { clip.occluders -= occluder }
    }
    return onGloballyPositioned { occluder.bounds = it.boundsInRoot() }
}

/** [area] bez pásů nahoře a dole; překrývají-li se pásy, zbyde čára, ne převrácený obdélník. */
internal fun visibleArea(area: Rect, obscuredTop: Float, obscuredBottom: Float): Rect {
    val top = area.top + obscuredTop.coerceAtLeast(0f)
    val bottom = (area.bottom - obscuredBottom.coerceAtLeast(0f)).coerceAtLeast(top)
    return Rect(area.left, top, area.right, bottom)
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
 *
 * [zIndexInOverlay] řadí prvky v překryvu: co leží na jiném sdíleném prvku (tečka dostupnosti
 * na avataru), musí mít vyšší, jinak ho letící avatar přikryje.
 */
@Composable
fun Modifier.sharedNavElement(
    key: String,
    zIndexInOverlay: Float = 0f,
    /**
     * Tvar, do kterého se prvek ořezává. **Musí se předat, i když ho ořezává už rodič:** během
     * letu se prvek kreslí v překryvu, mimo rodiče, a ořez nad ním se neuplatní. Fotka se proto
     * z prohlížeče vracela do bubliny s hranatými rohy a zaoblila se až po dosednutí. Tvar
     * přidaný sem letí s prvkem a jeho rohy se během letu přelijí z [flightFromCorner].
     */
    shape: Shape? = null,
    /** Zaoblení druhé strany páru, ze kterého se rohy přelévají do [shape]. */
    flightFromCorner: Dp = MEDIA_CARD_CORNER,
    /**
     * Letět i tam, kde panely stojí vedle sebe. Smí jen pár, jehož druhá strana panely NAHRADÍ —
     * prohlížeč médií jde přes celou plochu, takže fotka z bubliny nemá s kým se přetahovat
     * (seznam vlevo klíč fotky nenese). Avatar a název chatu letět nesmí, viz [LocalPanesSideBySide].
     */
    besidePanes: Boolean = false,
): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    if (LocalPanesSideBySide.current && !besidePanes) return this
    val scope = LocalNavAnimatedContentScope.current
    val area = LocalSharedElementClip.current

    return with(shared) {
        val state = rememberSharedContentState(key = key)
        // Kreslí se prvek, který se stává viditelným, takže jeho přechod jde PreEnter → Visible.
        // Táž pružina jako let, aby ořez i rohy dosedly ve stejnou chvíli jako prvek sám.
        val settled = scope.transition.animateFloat(
            transitionSpec = { clipSpec },
            label = "shared-element-clip",
        ) { if (it == EnterExitState.Visible) 1f else 0f }
        val element = if (area == null) {
            this@sharedNavElement.sharedElement(
                sharedContentState = state,
                animatedVisibilityScope = scope,
                boundsTransform = { _, _ -> flightSpec },
                zIndexInOverlay = zIndexInOverlay,
            )
        } else {
            val clip = remember(area, settled) { ContentAreaClip(area, settled) }
            this@sharedNavElement.sharedElement(
                sharedContentState = state,
                animatedVisibilityScope = scope,
                boundsTransform = { _, _ -> flightSpec },
                zIndexInOverlay = zIndexInOverlay,
                clipInOverlayDuringTransition = clip,
            )
        }
        if (shape == null) {
            element
        } else {
            // Ve vrstvě, ne `Modifier.clip`: průběh se mění každý snímek a čte se až tady,
            // takže let nic nerekomponuje.
            element.graphicsLayer {
                val progress = if (isTransitionActive && state.isMatchFound) settled.value else 1f
                this.shape = flightShape(shape, flightFromCorner.toPx(), progress, size, this)
                this.clip = true
            }
        }
    }
}

/**
 * Tvar letícího prvku: rohy se přelévají z [fromCornerPx] (druhá strana páru) do [target].
 *
 * Umí jen tvary s rohy ([CornerBasedShape] — bublina, dlaždice, karta); jiný tvar platí rovnou
 * cílový. Rohy se počítají v px z velikosti prvku, protože ta se během letu mění.
 */
internal fun flightShape(target: Shape, fromCornerPx: Float, progress: Float, size: Size, density: Density): Shape {
    if (progress >= 1f || target !is CornerBasedShape) return target
    val t = progress.coerceIn(0f, 1f)
    fun corner(size0: CornerSize) = CornerSize(fromCornerPx + (size0.toPx(size, density) - fromCornerPx) * t)
    return RoundedCornerShape(
        topStart = corner(target.topStart),
        topEnd = corner(target.topEnd),
        bottomEnd = corner(target.bottomEnd),
        bottomStart = corner(target.bottomStart),
    )
}

internal val clipSpec = spring<Float>(
    dampingRatio = 0.8f,
    stiffness = Spring.StiffnessMediumLow,
)

private class ContentAreaClip(
    private val area: SharedElementClip,
    private val settled: State<Float>,
) : SharedTransitionScope.OverlayClip {
    // Tytéž objekty, jak doporučuje dokumentace — `getClipPath` běží při každém snímku letu.
    private val path = Path()
    private val cuts = Path()
    private val shape = Path()

    override fun getClipPath(
        sharedContentState: SharedTransitionScope.SharedContentState,
        bounds: Rect,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Path? {
        val visible = area.visibleBounds() ?: return null
        val band = area.openBand(visible)
        path.reset()
        if (band == null) {
            path.addRect(sharedElementClipRect(visible, bounds, settled.value))
            return path
        }

        /*
         * Pás pod polem je průhledný, takže se ořez rozšíří až na jeho spodek a vyříznou se z něj
         * jen tvary, které fotku přikrývají, a všechno pod nimi (navigační lišta). Výřezy se
         * přidávají se stejným průběhem jako ořez: na začátku letu je fotka ještě v prohlížeči
         * a díra ve tvaru pole by v ní byla vidět dřív, než pole samo. Proto platí jen pod čarou,
         * která s letem stoupá od spodku k hraně pásu.
         */
        val extended = visible.copy(bottom = maxOf(visible.bottom, band.bottom))
        val clipRect = sharedElementClipRect(extended, bounds, settled.value)
        path.addRect(clipRect)

        cuts.reset()
        var shapesBottom = band.top
        for (occluder in area.occluders) {
            val at = occluder.bounds ?: continue
            shape.reset()
            shape.addOutline(occluder.shape.createOutline(at.size, layoutDirection, density))
            shape.translate(at.topLeft)
            cuts.op(cuts, shape, PathOperation.Union)
            shapesBottom = maxOf(shapesBottom, at.bottom)
        }
        cuts.addRect(Rect(band.left, shapesBottom, band.right, band.bottom))

        val line = lerpFloat(clipRect.bottom, band.top, settled.value.coerceIn(0f, 1f))
        shape.reset()
        shape.addRect(Rect(clipRect.left, line, clipRect.right, clipRect.bottom))
        cuts.op(cuts, shape, PathOperation.Intersect)
        path.op(path, cuts, PathOperation.Difference)
        return path
    }
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float) = start + (stop - start) * fraction

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
    if (LocalPanesSideBySide.current) return this

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
/**
 * Klíče letu avataru a názvu chatu. Nesou slot účtu: ve společném seznamu chatů je tentýž chat
 * (společná skupina, soukromý chat s týmž člověkem) dvakrát, z každého účtu jednou, a dva prvky
 * s jedním klíčem by letěly jako jeden.
 */
@Composable
fun chatAvatarKey(chatId: Long): String = "chat-avatar-${LocalAccount.current.slot.id}-$chatId"

@Composable
fun chatTitleKey(chatId: Long): String = "chat-title-${LocalAccount.current.slot.id}-$chatId"

/**
 * Tečka „je online" na avataru letí VLASTNÍM sdíleným prvkem, ne s avatarem. Avatar se sdílí
 * sám (profil a tvář odesílatele tečku nemají), takže tečka mimo něj zůstávala na obrazovce:
 * avatar odletěl bez ní a tečka se k němu přilepila, až dosedl. Obě strany mají tutéž pružinu,
 * takže tečka drží roh avataru po celou dobu letu. Viz [OnlineDot].
 */
fun chatOnlineDotKey(chatId: Long): String = "chat-online-dot-$chatId"

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
fun photoKey(messageId: Long): String {
    val base = if (LocalSharedMediaFlight.current) "shared-media-photo-$messageId" else "photo-$messageId"
    val poll = LocalPollMediaFlight.current
    val article = LocalArticleMediaFlight.current
    return when {
        poll != NO_POLL_MEDIA -> "$base-poll-$poll"
        article != NO_ARTICLE_MEDIA -> "$base-article-$article"
        else -> base
    }
}

/**
 * Které médium článku letí do prohlížeče (`ArticleMedia.index`); [NO_ARTICLE_MEDIA] příloha
 * zprávy. Článek nese víc obrázků pod JEDNÍM id zprávy — tatáž úvaha jako [LocalPollMediaFlight].
 */
val LocalArticleMediaFlight = compositionLocalOf { NO_ARTICLE_MEDIA }

/**
 * Která příloha ankety letí do prohlížeče: [POLL_QUESTION_MEDIA] otázka, jinak PŮVODNÍ index
 * odpovědi; [NO_POLL_MEDIA] příloha zprávy.
 *
 * **Anketa nese víc obrázků pod JEDNÍM id zprávy** (otázka a každá odpověď), takže samotné id,
 * které u zprávy stačí, by spárovalo fotku odpovědi s fotkou otázky. Poskytuje ho blok ankety
 * a prohlížeč otevřený z něj (podle `MediaRoute`).
 */
val LocalPollMediaFlight = compositionLocalOf { NO_POLL_MEDIA }

/**
 * Klíč páru náhled přílohy nad vstupem ↔ celoobrazovkový náhled.
 */
fun attachmentKey(path: String): String = "attachment-$path"
