package eu.tintera.plume.features.profile

import eu.tintera.plume.core.adaptive.paneHeaderContainerColor
import eu.tintera.plume.core.adaptive.PaneHeader
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.ui.LocalPanesSideBySide
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.common_back
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * **Hlavička podstránky profilu** (pozvánky, žádosti, poslední akce, omezení, práva člena, úprava):
 * střední flexibilní bar M3 Expressive s podtitulkem = název chatu.
 *
 * Dřív to byl malý `TopAppBar` jen s názvem obrazovky, takže z hlavičky nebylo poznat, čeho se
 * týká — „Žádosti o vstup" vypadají u každé skupiny stejně a do podstránek se chodí i z jiné
 * skupiny, než je ta, jejíž konverzace leží pod nimi. Na jednom panelu se bar při posunu sbalí
 * (jako Nastavení, `exitUntilCollapsed`); v dvoupanelu ne, vedle by stály dvě hlavičky různé
 * výšky. Sbalený bar barvu nemění (`scrolledContainerColor = surface`), stejně jako Nastavení.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ProfileSubpageTopBar(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior?,
    actions: @Composable RowScope.() -> Unit = {},
) {
    // Vedle konverzace leží nad kartou profilu (`PaneHeader`), jinak je obyčejná.
    PaneHeader {
        MediumFlexibleTopAppBar(
            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            subtitle = subtitle?.let { { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) } },
            navigationIcon = {
                IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(Res.drawable.arrow_back_24dp), contentDescription = stringResource(Res.string.common_back))
                }
            },
            actions = actions,
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = paneHeaderContainerColor(),
                scrolledContainerColor = paneHeaderContainerColor(),
            ),
            scrollBehavior = scrollBehavior,
        )
    }
}

/** Sbalování hlavičky podstránky: na jednom panelu ano, v dvoupanelu ne (`null`). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun profileSubpageScrollBehavior(): TopAppBarScrollBehavior? =
    if (LocalPanesSideBySide.current) null else TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

/** Obal Scaffoldu, přes který se posun obsahu dostane k hlavičce. */
@OptIn(ExperimentalMaterial3Api::class)
internal fun Modifier.profileSubpageScroll(behavior: TopAppBarScrollBehavior?): Modifier =
    if (behavior == null) this else nestedScroll(behavior.nestedScrollConnection)

/**
 * Název chatu pro podtitulek hlavičky, z místního přehledu (`summaries`). **Dokud nedorazí, je
 * prázdný řetězec, ne `null`**: flexibilní bar s podtitulkem je vyšší než bez něj, takže by
 * po načtení názvu poskočil.
 */
@Composable
internal fun rememberChatTitle(chatId: Long): String {
    val chats = koinInject<ChatRepository>()
    val title by produceState(initialValue = "", chatId) {
        value = chats.summaries(listOf(chatId)).firstOrNull()?.title.orEmpty()
    }
    return title
}
