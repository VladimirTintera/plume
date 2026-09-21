package eu.tintera.plume.app.shell

import eu.tintera.plume.core.navigation.ChatsRoute
import eu.tintera.plume.core.navigation.SettingsRoute
import eu.tintera.plume.core.navigation.TopLevelRoute
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.forum_24dp
import eu.tintera.plume.resources.forum_filled_24dp
import eu.tintera.plume.resources.settings_24dp
import eu.tintera.plume.resources.settings_filled_24dp
import eu.tintera.plume.resources.nav_chats
import eu.tintera.plume.resources.nav_settings
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/**
 * Sekce v railu (na telefonu navigační lišta není, Nastavení jsou v menu účtu).
 *
 * Ikony jsou **Material Symbols** jako vector drawables v `composeResources`
 * (`androidx.compose.material:material-icons-*` Google neudržuje, poslední verze je 1.7.3).
 * Každá sekce má nevyplněnou i vyplněnou variantu — M3 chce u vybrané položky vyplněnou.
 *
 * Nová ikona: stáhni z [Google Fonts](https://fonts.google.com/icons) jako Android Vector Drawable
 * a ulož sem. Pozor, ze staženého XML je potřeba vyhodit `android:tint="?attr/…"` a nahradit
 * `@android:color/white` literální barvou — resource reference commonMain parser nezná.
 */
enum class TopLevelDestination(
    val route: TopLevelRoute,
    val label: StringResource,
    val icon: DrawableResource,
    val selectedIcon: DrawableResource,
) {
    CHATS(ChatsRoute, Res.string.nav_chats, Res.drawable.forum_24dp, Res.drawable.forum_filled_24dp),
    SETTINGS(SettingsRoute, Res.string.nav_settings, Res.drawable.settings_24dp, Res.drawable.settings_filled_24dp),
}
