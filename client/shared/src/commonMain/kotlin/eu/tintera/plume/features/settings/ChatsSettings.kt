package eu.tintera.plume.features.settings

import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.features.proxy.ProxyStatus
import eu.tintera.plume.features.proxy.ProxyStatusViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chat_folders_row_help
import eu.tintera.plume.resources.chat_folders_title
import eu.tintera.plume.resources.folder_24dp
import eu.tintera.plume.resources.proxy_row_direct
import eu.tintera.plume.resources.proxy_row_help
import eu.tintera.plume.resources.proxy_row_via
import eu.tintera.plume.resources.proxy_title
import eu.tintera.plume.resources.settings_connection_group
import eu.tintera.plume.resources.vpn_lock_24dp
import eu.tintera.plume.resources.settings_chat_folders_group
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Skupina „Chaty" v Obecných: zatím jen složky. Samostatná sekce by měla jednu položku. */
@Composable
internal fun ChatsSettings(onOpenFolders: () -> Unit) {
    SettingsGroup(Res.string.settings_chat_folders_group) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(painterResource(Res.drawable.folder_24dp), contentDescription = null) },
            headlineContent = { Text(stringResource(Res.string.chat_folders_title)) },
            supportingContent = { Text(stringResource(Res.string.chat_folders_row_help)) },
            modifier = Modifier.clickable(onClick = onOpenFolders),
        )
    }
}

/**
 * Připojení: proxy. Vlastní skupina, protože nesouvisí s chaty ani se soukromím — řeší, jestli
 * se aplikace k Telegramu vůbec dostane.
 *
 * Podtitul říká, přes kterou proxy se aplikace připojuje. Zapnutá proxy, která nefunguje, je
 * nejčastější důvod „Připojování…", a bez stavu v řádku by se na ni přišlo až na její obrazovce.
 */
@Composable
internal fun ConnectionSettings(viewModel: ProxyStatusViewModel, onOpenProxy: () -> Unit) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.reload()
        onPauseOrDispose {}
    }
    SettingsGroup(Res.string.settings_connection_group) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = { Icon(painterResource(Res.drawable.vpn_lock_24dp), contentDescription = null) },
            headlineContent = { Text(stringResource(Res.string.proxy_title)) },
            supportingContent = {
                when (val current = status) {
                    // Dokud stav nedorazí, řádek nic netvrdí — „vypnutá" by mohla být lež.
                    null -> Unit
                    ProxyStatus.Direct -> Text(stringResource(Res.string.proxy_row_direct))
                    is ProxyStatus.Via -> Text(
                        stringResource(
                            Res.string.proxy_row_via,
                            "${current.config.server}:${current.config.port}",
                        ),
                    )
                    ProxyStatus.Unknown -> Text(stringResource(Res.string.proxy_row_help))
                }
            },
            modifier = Modifier.clickable(onClick = onOpenProxy),
        )
    }
}
