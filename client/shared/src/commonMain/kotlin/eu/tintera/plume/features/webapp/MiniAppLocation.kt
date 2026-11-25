package eu.tintera.plume.features.webapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.WebAppRepository
import eu.tintera.plume.core.ui.SwitchListItem
import eu.tintera.plume.core.ui.TitledGroup
import eu.tintera.plume.core.webapp.MiniAppStorage
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.location_on_24dp
import eu.tintera.plume.resources.settings_mini_app_location
import eu.tintera.plume.resources.webapp_location_row
import eu.tintera.plume.resources.webapp_location_row_off
import eu.tintera.plume.resources.webapp_location_row_on
import eu.tintera.plume.resources.webapp_title
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Souhlasy s polohou pro mini aplikace botů v tomhle účtu (`MiniAppStorage.locationPermissions`):
 * id bota → smí znát polohu, a zápis změny. Dřív se souhlas uložil navždy a odvolat nešel.
 */
private class LocationPermissions(val permissions: Map<Long, Boolean>, val set: (bot: Long, allowed: Boolean) -> Unit)

@Composable
private fun rememberLocationPermissions(): LocationPermissions? {
    val storage = koinInject<MiniAppStorage>()
    val chats = koinInject<ChatRepository>()
    val scope = rememberCoroutineScope()
    val account by chats.savedMessagesChatId.collectAsStateWithLifecycle()
    val permissions by remember(account) {
        account?.let(storage::locationPermissions) ?: flowOf(null)
    }.collectAsStateWithLifecycle(null)
    val id = account ?: return null
    val current = permissions ?: return null
    return LocationPermissions(current) { bot, allowed -> scope.launch { storage.setLocationAllowed(id, bot, allowed) } }
}

/** Souhlas s polohou pro jednoho bota: [allowed] a jeho změna. */
class BotLocation(val allowed: Boolean, val set: (Boolean) -> Unit)

/** `null` = bot se na polohu ještě neptal (nebo účet není načtený) a profil řádek nemá. */
@Composable
fun rememberBotLocation(botUserId: Long): BotLocation? {
    val location = rememberLocationPermissions() ?: return null
    val allowed = location.permissions[botUserId] ?: return null
    return BotLocation(allowed) { on -> location.set(botUserId, on) }
}

/**
 * Řádek v profilu bota: smí jeho mini aplikace znát polohu. Jen u bota, který se už ptal
 * ([rememberBotLocation]) — jinak by přepínač tvrdil „nesmí" u bota, který polohu nikdy
 * nechtěl. Vypnutí neznamená zákaz navždy: příští žádost se zeptá znovu, stejně jako po
 * odmítnutí v dialogu.
 */
@Composable
fun BotLocationRow(location: BotLocation, modifier: Modifier = Modifier) {
    SwitchListItem(
        headline = stringResource(Res.string.webapp_location_row),
        checked = location.allowed,
        supporting = stringResource(if (location.allowed) Res.string.webapp_location_row_on else Res.string.webapp_location_row_off),
        // Bez ikony: ostatní řádky v Informacích profilu ji nemají.
        modifier = modifier,
        onCheckedChange = location.set,
    )
}

/**
 * Nastavení → Soukromí: všichni boti, kteří se na polohu ptali, s přepínačem. Blok chybí, dokud
 * se žádný nezeptal. Jména se čtou lokálně (`WebAppRepository.botName`), jako v hlavičce mini aplikace.
 */
@Composable
fun MiniAppLocationGroup() {
    val location = rememberLocationPermissions() ?: return
    if (location.permissions.isEmpty()) return
    val repository = koinInject<WebAppRepository>()
    var names by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    LaunchedEffect(location.permissions.keys) {
        val missing = location.permissions.keys - names.keys
        if (missing.isNotEmpty()) names = names + missing.associateWith { repository.botName(it).orEmpty() }
    }
    val fallback = stringResource(Res.string.webapp_title)
    TitledGroup(Res.string.settings_mini_app_location) {
        location.permissions.entries
            .sortedBy { names[it.key]?.lowercase() }
            .forEach { (bot, allowed) ->
                SwitchListItem(
                    headline = names[bot]?.takeIf { it.isNotBlank() } ?: fallback,
                    checked = allowed,
                    supporting = stringResource(if (allowed) Res.string.webapp_location_row_on else Res.string.webapp_location_row_off),
                    icon = Res.drawable.location_on_24dp,
                ) { on -> location.set(bot, on) }
            }
    }
}
