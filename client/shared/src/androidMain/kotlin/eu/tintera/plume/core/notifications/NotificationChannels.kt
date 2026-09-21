package eu.tintera.plume.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import eu.tintera.plume.core.telegram.NotificationChannelKind
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.notification_channel_channels
import eu.tintera.plume.resources.notification_channel_channels_description
import eu.tintera.plume.resources.notification_channel_groups
import eu.tintera.plume.resources.notification_channel_groups_description
import eu.tintera.plume.resources.notification_channel_mentions
import eu.tintera.plume.resources.notification_channel_mentions_description
import eu.tintera.plume.resources.notification_channel_private
import eu.tintera.plume.resources.notification_channel_private_description
import org.jetbrains.compose.resources.getString

/**
 * Id systémového kanálu. **Nesmí se měnit**: kanál s novým id je pro systém jiný kanál a co si
 * uživatel u starého nastavil (zvuk, ztišení), zůstane u něj.
 */
val NotificationChannelKind.channelId: String
    get() = when (this) {
        NotificationChannelKind.Private -> "private"
        NotificationChannelKind.Groups -> "groups"
        NotificationChannelKind.Channels -> "channels"
        NotificationChannelKind.Mentions -> "mentions"
    }

/**
 * Založí kanály notifikací, nebo jim přeloží název a popis.
 *
 * **Po založení patří kanál uživateli** — důležitost, zvuk ani vibrace už aplikace nepřepíše,
 * `createNotificationChannel` u existujícího kanálu změní jen název a popis. Volá se proto při
 * každém startu: po změně jazyka se přeloží, a nic jiného se tím nerozbije.
 */
suspend fun createNotificationChannels(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    NotificationChannelKind.entries.forEach { kind ->
        val (name, description) = when (kind) {
            NotificationChannelKind.Private ->
                Res.string.notification_channel_private to Res.string.notification_channel_private_description
            NotificationChannelKind.Groups ->
                Res.string.notification_channel_groups to Res.string.notification_channel_groups_description
            NotificationChannelKind.Channels ->
                Res.string.notification_channel_channels to Res.string.notification_channel_channels_description
            NotificationChannelKind.Mentions ->
                Res.string.notification_channel_mentions to Res.string.notification_channel_mentions_description
        }
        val channel = NotificationChannel(
            kind.channelId,
            getString(name),
            // Kanály telegramových kanálů jen tiše do lišty: příspěvků chodí hodně a málokdy
            // na ně člověk musí hned reagovat. Kdo chce víc, přepne si to v systému.
            if (kind == NotificationChannelKind.Channels) NotificationManager.IMPORTANCE_DEFAULT
            else NotificationManager.IMPORTANCE_HIGH,
        ).apply { this.description = getString(description) }
        manager.createNotificationChannel(channel)
    }
    // Jediný kanál z dřívějška — jinak by v systémovém nastavení visel jako prázdná položka.
    manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
}

private const val LEGACY_CHANNEL_ID = "messages"
