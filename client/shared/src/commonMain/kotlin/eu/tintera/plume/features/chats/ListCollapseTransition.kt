package eu.tintera.plume.features.chats

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import eu.tintera.plume.core.accounts.LocalAccount
import eu.tintera.plume.core.ui.LocalSharedTransitionScope
import eu.tintera.plume.core.ui.sharedKey

/**
 * Přechod mezi plným seznamem chatů a sbaleným sloupcem jen s avatary (táhlo seznamu přes práh
 * `COLLAPSED_LIST_MAX_WIDTH`). Obě podoby jsou v jednom `AnimatedContent` a prvky, které mají
 * obě, přeletí na své nové místo: avatar účtu z konce řádku s pilulkou nahoru do sloupce, lupa
 * z pilulky do tlačítka hledání, tlačítko Vytvořit z plovoucí lišty dole nahoru pod účet
 * a avatary chatů z řádků do sloupce. Ostatní (pilulka, texty řádků, lišta se složkami) se
 * prolne. Dřív prvky na starém místě zmizely a na novém se objevily v jednom snímku.
 *
 * Scope je [AnimatedVisibilityScope] toho `AnimatedContent`; mimo něj (archiv, telefon) je
 * `null` a [listCollapseElement] nedělá nic.
 */
internal val LocalListCollapseScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Prvek, který má plný seznam i sbalený sloupec a mezi nimi přeletí. [key] musí být stejný na
 * obou stranách a v jedné podobě jedinečný (avatary chatů proto nesou i účet, ve společném
 * seznamu víc účtů může mít totéž id chat ve dvou účtech).
 *
 * Nekoliduje s letem avataru do hlavičky konverzace (`sharedNavElement`): sbalený sloupec je
 * jen vedle konverzace a tam se avatar do hlavičky nesdílí (`LocalPanesSideBySide`).
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

/** Klíč avataru chatu pro [listCollapseElement]: s účtem, viz tam. */
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
