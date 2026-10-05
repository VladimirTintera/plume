package eu.tintera.plume.features.proxy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ProxyConfig
import eu.tintera.plume.core.telegram.ProxyKind
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.common_cancel
import eu.tintera.plume.resources.proxy_kind_http
import eu.tintera.plume.resources.proxy_kind_mtproto
import eu.tintera.plume.resources.proxy_kind_socks5
import eu.tintera.plume.resources.proxy_link_body
import eu.tintera.plume.resources.proxy_link_connect
import eu.tintera.plume.resources.proxy_link_port
import eu.tintera.plume.resources.proxy_link_server
import eu.tintera.plume.resources.proxy_link_status
import eu.tintera.plume.resources.proxy_link_title
import eu.tintera.plume.resources.proxy_link_type
import eu.tintera.plume.resources.proxy_ping_failed
import eu.tintera.plume.resources.proxy_ping_measuring
import eu.tintera.plume.resources.proxy_ping_ms
import org.jetbrains.compose.resources.stringResource

/** Odezva proxy (`pingProxy`): měří se, změřená, nebo přes ni Telegram nedosažitelný. */
sealed interface ProxyPing {
    data object Measuring : ProxyPing
    data class Measured(val millis: Int) : ProxyPing
    data object Failed : ProxyPing
}

@Composable
fun proxyKindLabel(kind: ProxyKind): String = stringResource(
    when (kind) {
        is ProxyKind.Mtproto -> Res.string.proxy_kind_mtproto
        is ProxyKind.Socks5 -> Res.string.proxy_kind_socks5
        is ProxyKind.Http -> Res.string.proxy_kind_http
    },
)

@Composable
fun proxyPingLabel(ping: ProxyPing): String = when (ping) {
    ProxyPing.Measuring -> stringResource(Res.string.proxy_ping_measuring)
    is ProxyPing.Measured -> stringResource(Res.string.proxy_ping_ms, ping.millis)
    ProxyPing.Failed -> stringResource(Res.string.proxy_ping_failed)
}

/**
 * Proxy z odkazu (`t.me/proxy?…`): kam vede a jestli přes ni Telegram odpovídá, a teprve
 * po potvrzení se přidá a zapne. Odkaz může poslat kdokoli a proxy pak vidí, odkud a kdy se
 * aplikace připojuje — proto se nic nezapíná samo.
 */
@Composable
fun ProxyLinkDialog(
    config: ProxyConfig,
    ping: ProxyPing,
    connecting: Boolean,
    error: UiText?,
    onConnect: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.proxy_link_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.proxy_link_body))
                ProxyDetail(stringResource(Res.string.proxy_link_server), config.server)
                ProxyDetail(stringResource(Res.string.proxy_link_port), config.port.toString())
                ProxyDetail(stringResource(Res.string.proxy_link_type), proxyKindLabel(config.kind))
                ProxyDetail(stringResource(Res.string.proxy_link_status), proxyPingLabel(ping))
                error?.let { Text(it.resolve(), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConnect, enabled = !connecting) {
                Text(stringResource(Res.string.proxy_link_connect))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.common_cancel)) }
        },
    )
}

@Composable
private fun ProxyDetail(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
