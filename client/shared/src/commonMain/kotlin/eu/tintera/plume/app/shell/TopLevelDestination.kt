package eu.tintera.plume.app.shell

import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.SettingsRoute
import eu.tintera.plume.core.navigation.TopLevelRoute

/**
 * Sekce aplikace, každá s vlastním zásobníkem ([AppNavigation]). Přepínač sekcí v UI není —
 * do Nastavení se chodí z menu účtu v seznamu chatů a zpět.
 *
 * Výčet je tu kvůli uložení vybrané sekce (podle názvu) a kvůli tomu, aby zásobník dostala
 * každá sekce, ne jen ta, na kterou se zrovna myslelo.
 */
enum class TopLevelDestination(val route: TopLevelRoute) {
    CHATS(ChatsRoute),
    SETTINGS(SettingsRoute),
}
