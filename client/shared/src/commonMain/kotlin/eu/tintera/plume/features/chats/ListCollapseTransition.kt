package eu.tintera.plume.features.chats

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.toSize
import eu.tintera.plume.core.accounts.LocalAccount
import eu.tintera.plume.core.ui.LocalSharedTransitionScope
import eu.tintera.plume.core.ui.sharedKey

/**
 * Přechod mezi plným seznamem chatů a sbaleným sloupcem jen s avatary (táhlo seznamu přes práh
 * `COLLAPSED_LIST_MAX_WIDTH`). Obě podoby jsou v jednom `AnimatedContent` a prvky, které mají
 * obě, přeletí na své nové místo: avatar účtu z konce řádku s pilulkou nahoru do sloupce, lupa
 * z pilulky do tlačítka hledání, tlačítko Vytvořit z plovoucí lišty dole nahoru pod účet
 * ([listCollapseElement]) a avatary chatů z řádků do sloupce ([collapseAvatarFlight]). Ostatní
 * (pilulka, texty řádků, lišta se složkami) se prolne. Dřív prvky na starém místě zmizely a na
 * novém se objevily v jednom snímku.
 *
 * Scope je [AnimatedVisibilityScope] toho `AnimatedContent`; mimo něj (archiv, telefon) je
 * `null` a modifikátory nedělají nic.
 */
internal val LocalListCollapseScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Prvek hlavičky, který má plný seznam i sbalený sloupec a mezi nimi přeletí (`sharedElement`):
 * avatar účtu, lupa, Vytvořit. [key] musí být stejný na obou stranách.
 *
 * **Na avatary chatů sdílený prvek nejde**, ty létají ručně ([collapseAvatarFlight]): v líném
 * seznamu se dvojice sice spárovaly (`isMatchFound`), ale let neběžel — avatary stály po celý
 * přechod na výchozím místě a na konci skočily do sloupce (zachyceno sérií snímků, i bez
 * `animateItem` a bez sdílené hlavičky).
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun Modifier.listCollapseElement(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val scope = LocalListCollapseScope.current ?: return this
    // Prostorový spec s jemným dojezdem, jako ostatní přesuny v aplikaci (`AppMotion`).
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Rect>()
    return with(shared) {
        this@listCollapseElement.sharedElement(
            sharedContentState = rememberSharedContentState(sharedKey("list-collapse-$key")),
            animatedVisibilityScope = scope,
            boundsTransform = { _, _ -> spec },
        )
    }
}

/** Která podoba seznamu prvek kreslí — avatary se párují s druhou. */
internal enum class CollapseSide { Full, Column }

internal val LocalCollapseSide = staticCompositionLocalOf { CollapseSide.Full }

/**
 * Kde naposledy v klidu stály avatary chatů v obou podobách seznamu (souřadnice kořene). Odtud
 * avatar přicházející podoby vyletí. Zapisuje se jen mimo přechod: odcházející podoba se během
 * něj rozkládá znovu a poloha z té doby by nebyla ta, kterou uživatel viděl. Položka, která
 * odjede ze seznamu (odrolování, konec přechodu), se maže, ať se neletí ze zastaralého místa.
 */
@Stable
internal class CollapseFlights {
    private val full = HashMap<Any, Rect>()
    private val column = HashMap<Any, Rect>()

    fun of(side: CollapseSide): HashMap<Any, Rect> = if (side == CollapseSide.Full) full else column

    fun other(side: CollapseSide): HashMap<Any, Rect> = if (side == CollapseSide.Full) column else full
}

internal val LocalCollapseFlights = staticCompositionLocalOf<CollapseFlights?> { null }

/**
 * Let avataru chatu mezi plným seznamem a sloupcem, ručně: přicházející avatar se při prvním
 * umístění posune a zvětší na místo protějšku v odcházející podobě ([CollapseFlights])
 * a s přechodem dojede domů. Během letu se kreslí v překryvu nad celou aplikací
 * (`renderInSharedTransitionScopeOverlay`), jinak by ho ořízl okraj seznamu, ze kterého vyjíždí —
 * do sloupce se letí shora zpod hlavičky. Avatar bez protějšku (nebyl vidět) se jen prolne
 * s ostatním obsahem.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun Modifier.collapseAvatarFlight(key: Any): Modifier {
    val flights = LocalCollapseFlights.current ?: return this
    val scope = LocalListCollapseScope.current ?: return this
    val side = LocalCollapseSide.current
    val transition = scope.transition
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    /*
     * Průběh letu je průběh TÉHOŽ přechodu (0 = na místě protějšku, 1 = doma), ne vlastní
     * `Animatable`: s vlastní pružinou doletěly avatary dřív než účet, lupa a Vytvořit
     * (sdílené prvky jedou s přechodem) a celé to působilo nesourodě. Přechod navíc počká, až
     * avatary doletí.
     */
    val progress by transition.animateFloat(transitionSpec = { spec }, label = "collapse-avatar") { state ->
        if (state == EnterExitState.Visible) 1f else 0f
    }
    var from by remember { mutableStateOf<Rect?>(null) }
    var home by remember { mutableStateOf<Rect?>(null) }
    DisposableEffect(flights, side, key) {
        onDispose { flights.of(side).remove(key) }
    }
    val overlay = LocalSharedTransitionScope.current?.let { shared ->
        with(shared) {
            Modifier.renderInSharedTransitionScopeOverlay(
                zIndexInOverlay = 1f,
                renderInOverlay = { from != null && transition.isRunning },
            )
        }
    } ?: Modifier
    return this
        .onGloballyPositioned { coordinates ->
            val rect = Rect(coordinates.positionInRoot(), coordinates.size.toSize())
            home = rect
            when {
                !transition.isRunning -> {
                    flights.of(side)[key] = rect
                    from = null
                }
                // Přicházející podoba: let z místa protějšku, jednou za přechod.
                transition.targetState == EnterExitState.Visible && from == null -> from = flights.other(side)[key]
            }
        }
        .then(overlay)
        .graphicsLayer {
            val start = from ?: return@graphicsLayer
            val end = home ?: return@graphicsLayer
            val p = progress
            // Pružina přestřelí přes 1 (dojezd), proto jen přesná rovnost; s `p >= 1` avatar
            // na konci letu poskočil.
            if (p == 1f || end.width <= 0f) return@graphicsLayer
            translationX = (start.center.x - end.center.x) * (1f - p)
            translationY = (start.center.y - end.center.y) * (1f - p)
            val scale = start.width / end.width
            scaleX = scale + (1f - scale) * p
            scaleY = scaleX
        }
}

/** Klíč avataru chatu pro [collapseAvatarFlight]: s účtem, ve společném seznamu víc účtů může mít totéž id chat ve dvou účtech. */
@Composable
internal fun collapseAvatarKey(chatId: Long): String = "avatar-${LocalAccount.current.slot}-$chatId"

/**
 * Klíč prvního celého chatu, který je v [state] vidět — kotva, na kterou dojede seznam na druhé
 * straně přechodu, aby avatary měly kam doletět (sloupec by jinak začínal nahoře a z odrolovaného
 * seznamu by nedoletěl žádný). Chat je `Long` (id), ve společném seznamu víc účtů `"zdroj:id"`
 * (`MergedChat.key`); obě podoby seznamu mají tytéž klíče. Hlavičky a karty kotvou nejsou.
 */
internal fun firstVisibleChatKey(state: LazyListState): Any? {
    val info = state.layoutInfo
    val top = info.viewportStartOffset + info.beforeContentPadding
    return info.visibleItemsInfo.firstOrNull { item ->
        item.offset >= top && (item.key is Long || (item.key as? String)?.matches(MERGED_KEY) == true)
    }?.key
}

private val MERGED_KEY = Regex("""\d+:-?\d+""")
