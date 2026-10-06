package eu.tintera.plume.features.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import eu.tintera.plume.core.ui.LocalTwoPane
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.arrow_back_24dp
import eu.tintera.plume.resources.common_back
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Jak se hlavička Nastavení chová při scrollu: na jednom panelu se velký nadpis sbalí do 64dp
 * lišty, v dvoupanelu zůstává velký (`null`).
 *
 * **Sbalovat se musí.** Nesbalitelný velký nadpis bral na telefonu trvale sedminu výšky, u sekce
 * s dlouhým obsahem (Notifikace) tedy místo na dva řádky voleb.
 *
 * **V dvoupanelu ne.** Seznam sekcí vlevo je krátký a scrollovat nemá co, takže by zůstal velký,
 * zatímco sekce vpravo by se sbalila — dvě hlavičky různé výšky vedle sebe, tatáž vada, kvůli
 * které seznam chatů opustil velký bar (vedle 64dp hlavičky konverzace).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun settingsScrollBehavior(): TopAppBarScrollBehavior? =
    if (LocalTwoPane.current) null else TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

/** Obal Scaffoldu, přes který se scroll obsahu dostane k hlavičce. */
@OptIn(ExperimentalMaterial3Api::class)
internal fun Modifier.settingsScroll(behavior: TopAppBarScrollBehavior?): Modifier =
    if (behavior == null) this else nestedScroll(behavior.nestedScrollConnection)

/**
 * Velký nadpis sekce nebo seznamu sekcí.
 *
 * **Sbalený bar NEMĚNÍ barvu** (`scrolledContainerColor = surface`). Výchozí `surfaceContainer`
 * dělá ze sbalené hlavičky tónovaný pruh přes celou šířku — role, která jinde na obrazovce není;
 * seznam chatů i výběr chatu to mají stejně.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsTopBar(
    title: String,
    onBack: (() -> Unit)?,
    scrollBehavior: TopAppBarScrollBehavior?,
) {
    LargeFlexibleTopAppBar(
        title = { Text(title) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        painterResource(Res.drawable.arrow_back_24dp),
                        contentDescription = stringResource(Res.string.common_back),
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface,
        ),
        scrollBehavior = scrollBehavior,
    )
}

/**
 * Odsazení obsahu Scaffoldu BEZ spodku. Spodní inset (navigační lišta) patří do scrollovaného
 * obsahu (`settingsContentBottom`), ne na kontejner: s ním obsah končil ostrou hranou nad lištou,
 * místo aby pod ni zajel a poslední řádek se nad ni doroloval — stejný vzor jako seznam chatů.
 */
@Composable
internal fun Modifier.settingsContentPadding(padding: PaddingValues): Modifier {
    val direction = LocalLayoutDirection.current
    return padding(
        top = padding.calculateTopPadding(),
        start = padding.calculateStartPadding(direction),
        end = padding.calculateEndPadding(direction),
    )
}

/** Spodek scrollovaného obsahu: navigační lišta ze Scaffoldu plus vzduch pod posledním blokem. */
internal fun settingsContentBottom(padding: PaddingValues, extra: Dp): Dp = padding.calculateBottomPadding() + extra
