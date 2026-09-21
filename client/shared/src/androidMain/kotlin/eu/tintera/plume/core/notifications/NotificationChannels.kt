package eu.tintera.plume.core.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import eu.tintera.plume.core.share.shortcutId
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

/**
 * Kanál jedné konverzace pod kanálem [parent] (Android 11+), založený, když ještě není.
 *
 * Takový kanál si jinak zakládá systém sám, až když uživatel u notifikace zvolí „Prioritní"
 * nebo vlastní zvuk — dřív systémové nastavení s `EXTRA_CONVERSATION_ID` ukáže jen nadřazený
 * kanál a nastavit tam jde celá skupina chatů, ne ten jeden. Notifikace se zkratkou konverzace
 * (`setShortcutId`) do něj pak systém směruje sám. Nastavení se přebírá z nadřazeného kanálu,
 * ať se založením nic nezmění — i to je chování systému.
 *
 * Vrací id zkratky, které se předá do nastavení; `null` pod Androidem 11.
 */
fun ensureConversationChannel(context: Context, parent: NotificationChannelKind, chatId: Long, title: String): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    val manager = context.getSystemService(NotificationManager::class.java)
    val conversationId = shortcutId(chatId)
    // Pozor: bez kanálu konverzace vrátí tohle volání NADŘAZENÝ kanál, ne `null`.
    if (manager.getNotificationChannel(parent.channelId, conversationId)?.conversationId == conversationId) return conversationId
    val source = manager.getNotificationChannel(parent.channelId) ?: return null
    val channel = NotificationChannel("${parent.channelId}_$conversationId", title, source.importance).apply {
        description = source.description
        setSound(source.sound, source.audioAttributes)
        enableVibration(source.shouldVibrate())
        vibrationPattern = source.vibrationPattern
        enableLights(source.shouldShowLights())
        lightColor = source.lightColor
        setShowBadge(source.canShowBadge())
        lockscreenVisibility = source.lockscreenVisibility
        setBypassDnd(source.canBypassDnd())
        setConversationId(parent.channelId, conversationId)
    }
    manager.createNotificationChannel(channel)
    return conversationId
}

/**
 * Po odhlášení pryč i kanály konverzací — nesou názvy chatů odhlášeného účtu a v systémovém
 * nastavení by po něm visely.
 */
fun deleteConversationChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.notificationChannels
        .filter { it.conversationId != null }
        .forEach { manager.deleteNotificationChannel(it.id) }
}

private const val LEGACY_CHANNEL_ID = "messages"
