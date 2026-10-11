package eu.tintera.plume.app.root.presentation.theme

import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.materialkolor.ktx.animateColorScheme
import eu.tintera.plume.app.root.presentation.LocalAppColorScheme
import eu.tintera.plume.app.root.presentation.LocalIsDarkTheme
import eu.tintera.plume.core.chatcolor.ChatColors
import eu.tintera.plume.core.settings.ThemeColor
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

/**
 * Konverzace (a její profil) v barvě, kterou jí uživatel dal ([ChatColors]); bez barvy v barvách
 * aplikace.
 *
 * **Mění se celé schéma, ne jednotlivé plochy.** Z barvy chatu se dopočítá táž paleta, jakou by
 * dostala celá aplikace ([expressiveColorScheme], Vibrant), takže bubliny, vstupní pole, hlavička
 * i plocha konverzace spolu ladí a kontrast mezi plochou a její `on*` rolí garantuje M3 — žádná
 * barva navíc, kterou by bylo potřeba ladit ručně. Neutrální plochy palety nesou nádech odstínu,
 * takže se chat pozná i podle pozadí, a pořád to není tapeta.
 *
 * Změna barvy (a její zrušení) se prolíná stejně jako přepnutí motivu. Cílem bez barvy je
 * CÍLOVÉ schéma aplikace ([LocalAppColorScheme]), ne prolínané.
 */
@Composable
fun ChatColorTheme(chatId: Long, content: @Composable () -> Unit) {
    val chatColors = koinInject<ChatColors>()
    val flow = remember(chatColors, chatId) { chatColors.colors.map { it[chatId] } }
    val color by flow.collectAsState(initial = remember(chatColors, chatId) { chatColors.current()[chatId] })
    ChatColorTheme(color, content)
}

/** Konverzace v barvě [color]; `null` = barvy aplikace. */
@Composable
fun ChatColorTheme(color: ThemeColor?, content: @Composable () -> Unit) {
    val app = LocalAppColorScheme.current
    if (app == null) {
        // Mimo `AppTheme` (náhledy) není k čemu se vracet.
        content()
        return
    }
    val target = if (color == null) app else expressiveColorScheme(LocalIsDarkTheme.current, color.seed)
    val scheme = animateColorScheme(target, animationSpec = { AppMotion.defaultEffectsSpec<Color>() })
    // Písmo, tvary i pohyb zůstávají z nadřazeného tématu; `MaterialExpressiveTheme` bere jen,
    // co se mu předá.
    MaterialExpressiveTheme(colorScheme = scheme, content = content)
}
