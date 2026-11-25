package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.AccountRepository
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.settings_account_badge
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Obrazovka patří jednomu z víc přihlášených účtů a má to říct ve své liště
 * ([AccountBadgeAction]). Podává ji shell u sekcí a podstránek Nastavení (`SettingsAccountScope`),
 * a jen tam, kde fotka má být (viz [AccountBadgeAction]); jinde je `false`.
 */
val LocalShowAccountBadge = compositionLocalOf { false }

/**
 * Fotka účtu, jehož obrazovka to je, na konci horní lišty — s víc účty u sekcí a podstránek
 * Nastavení ([LocalShowAccountBadge]).
 *
 * - **Jen když vedle není vidět seznam Nastavení** (`LocalPaneNeighbours.listBeside`): tam účet
 *   říká karta účtu a vybraný řádek pod ní. Na telefonu a ve dvou panelech s podstránkou
 *   (sekce + podstránka, seznam schovaný) by se jinak ztratilo, čí nastavení člověk mění —
 *   u soukromí, odhlášení zařízení nebo smazání účtu na tom záleží.
 * - **Z panelů vedle sebe jen v pravém**: sekce vedle podstránky ji nemá, dvě stejné fotky
 *   v sousedních lištách byly navíc. Kdy přesně, rozhoduje shell ([LocalShowAccountBadge]).
 * - **Není to tlačítko.** Přepnout účet uprostřed podstránky by ji zavřelo (patří předchozímu
 *   účtu); přepíná se v seznamu Nastavení. Jméno ukáže bublina po podržení a čtečka obrazovky.
 * - Účet bere z kontejneru, ve kterém obrazovka je (`koinInject`), takže sedí i bez parametru.
 */
@Composable
fun AccountBadgeAction() {
    if (!LocalShowAccountBadge.current) return
    val repository = koinInject<AccountRepository>()
    val account by remember(repository) { repository.account }.collectAsStateWithLifecycle(null)
    val current = account ?: return
    val label = stringResource(Res.string.settings_account_badge, current.displayName)
    WithTooltip(label = label) {
        // Velikost tlačítka v liště, ať fotka stojí v řadě s ikonami akcí a neposouvá nadpis.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(48.dp).semantics { contentDescription = label },
        ) {
            Avatar(
                chatId = current.id,
                title = current.displayName,
                avatarFileId = current.photoFileId,
                size = 32.dp,
            )
        }
    }
}
