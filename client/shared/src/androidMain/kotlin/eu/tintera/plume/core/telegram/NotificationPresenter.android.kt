package eu.tintera.plume.core.telegram

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.core.graphics.drawable.IconCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.notifications.NotificationIntents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Zobrazuje notifikace v Androidu.
 *
 * **Jeden chat = jedna notifikace.** Dřív měla každá zpráva vlastní notifikaci, seskupenou přes
 * `setGroup` a doplněnou hlavičkou skupiny — jenže seskupení znamená hromádku, kterou musí
 * uživatel rozbalit, ne jedno oznámení. Tři zprávy z jednoho chatu tak byly tři řádky v liště.
 * Teď se zprávy sbírají do `MessagingStyle` jedné notifikace, jak je na Androidu u chatů zvykem.
 *
 * **Id systémové notifikace je TDLib `notificationGroupId`**, protože ten je jeden na chat.
 * Jednotlivá `notificationId` se drží u řádků — TDLib podle nich ruší (`removedNotificationIds`)
 * a bez nich by se po přečtení jedné zprávy musela zahodit celá notifikace.
 */
actual class NotificationPresenter(
    private val context: Context,
    private val scope: CoroutineScope,
) {

    private val logger = Logger.withTag("Notifications")

    private val manager = NotificationManagerCompat.from(context)

    /**
     * Rozepsané notifikace podle `groupId`.
     *
     * Čte i zapisuje jen kolektor updatů, tedy jedna korutina — zámek proto není potřeba.
     */
    private val conversations = mutableMapOf<Int, Conversation>()

    /** Dekódované profilovky podle cesty. Sahá se na ně ze stejné korutiny jako na [conversations]. */
    private val icons = mutableMapOf<String, IconCompat>()

    actual fun start(repository: NotificationRepository) {
        createChannel()
        logger.d { "Kanál vytvořen, odebírám notifikace." }

        scope.launch {
            repository.updates.collect { update ->
                when (update) {
                    is NotificationUpdate.Added -> add(update.notifications)
                    is NotificationUpdate.Removed -> remove(update)
                }
            }
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Zprávy",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Nové zprávy z Telegramu"
        }
        manager.createNotificationChannel(channel)
    }

    private fun add(notifications: List<ChatNotification>) {
        // Jeden update může nést zprávy z několika chatů; každý z nich se překreslí jednou,
        // ne jednou za zprávu.
        notifications.groupBy { it.groupId }.forEach { (groupId, batch) ->
            val newest = batch.last()

            val conversation = conversations.getOrPut(groupId) {
                Conversation(
                    chatId = newest.chatId,
                    title = newest.chatTitle,
                    // Po restartu procesu je mapa prázdná, ale notifikace v liště zůstaly.
                    // Bez převzetí jejich řádků by nová zpráva přepsala předchozí.
                    lines = recoverLines(groupId),
                )
            }

            val added = batch
                .filter { line -> conversation.lines.none { it.notificationId == line.notificationId } }
                .map {
                    Line(
                        notificationId = it.notificationId,
                        sender = it.senderName,
                        text = it.text,
                        timestampMs = it.timestamp * 1000L,
                        icon = avatarIcon(it.avatarPath),
                    )
                }

            conversation.lines = (conversation.lines + added)
                .sortedBy { it.timestampMs }
                .takeLast(MAX_LINES)

            show(groupId, conversation, silent = newest.isSilent)
        }
    }

    private fun remove(update: NotificationUpdate.Removed) {
        val conversation = conversations[update.groupId]

        // Neznámá skupina = notifikace z minulého běhu procesu. Její řádky nemáme s čím
        // spárovat, takže jde pryč celá; TDLib stejně ruší po přečtení celý chat naráz.
        if (conversation == null) {
            manager.cancel(update.groupId)
            return
        }

        val removed = update.notificationIds.toSet()
        conversation.lines = conversation.lines.filterNot { it.notificationId in removed }

        if (conversation.lines.isEmpty()) {
            conversations.remove(update.groupId)
            manager.cancel(update.groupId)
            logger.d { "Skupina ${update.groupId} zůstala prázdná, ruším notifikaci." }
        } else {
            show(update.groupId, conversation, silent = true)
        }
    }

    private fun show(groupId: Int, conversation: Conversation, silent: Boolean) {
        // Od Androidu 13 je POST_NOTIFICATIONS běhové oprávnění. Bez kontroly by `notify`
        // tiše selhalo a nebylo by poznat proč.
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            logger.d { "Notifikace se nezobrazí — chybí oprávnění POST_NOTIFICATIONS." }
            return
        }

        // Jméno odesílatele vyplňuje TDLib jen tam, kde není totožné s chatem — tedy ve
        // skupinách. Jeho přítomnost je proto spolehlivější příznak skupiny než cokoli,
        // co bychom dopočítávali z id chatu.
        val isGroup = conversation.lines.any { it.sender != null }

        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("Já").build())
            .setGroupConversation(isGroup)
            // V soukromém chatu je název chatu totéž co jméno odesílatele u každého řádku —
            // vypsat ho i do hlavičky by ho zdvojilo.
            .setConversationTitle(conversation.title.takeIf { isGroup })

        conversation.lines.forEach { line ->
            style.addMessage(
                line.text,
                line.timestampMs,
                Person.Builder()
                    .setName(line.sender ?: conversation.title)
                    .setIcon(line.icon)
                    .build(),
            )
        }

        val built = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setStyle(style)
            .setSilent(silent)
            .setAutoCancel(true)
            .setContentIntent(
                NotificationIntents.openChat(
                    context = context,
                    notificationId = groupId,
                    chatId = conversation.chatId,
                ),
            )
            .build()

        logger.d { "Zobrazuji $groupId: ${conversation.lines.size} zpráv z ${conversation.chatId}." }
        manager.notify(groupId, built)
    }

    /**
     * Řádky notifikace, která v liště zůstala po minulém běhu procesu.
     *
     * Push probouzí mrtvý proces, takže tohle není okrajový případ: bez převzetí by třetí zpráva
     * nahradila notifikaci se dvěma předchozími. Obnovené řádky nemají `notificationId` — TDLib ho
     * do notifikace nepředává — takže se jednotlivě zrušit nedají a odejdou až s celou notifikací.
     */
    private fun recoverLines(groupId: Int): List<Line> {
        val existing = manager.activeNotifications.firstOrNull { it.id == groupId } ?: return emptyList()

        val style = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(existing.notification)
            ?: return emptyList()

        return style.messages.map { message ->
            Line(
                notificationId = null,
                sender = message.person?.name?.toString(),
                text = message.text?.toString().orEmpty(),
                timestampMs = message.timestamp,
                // Profilovka se převezme taky — znovu ji z cesty poskládat nejde, tu už nemáme.
                icon = message.person?.icon,
            )
        }
    }

    /**
     * Profilovka jako ikona notifikace.
     *
     * `createWithAdaptiveBitmap` kvůli kulatému ořezu — Android profilovky v notifikacích kreslí
     * v kruhu a čtvercová fotka by z řady vyčnívala. Dekódování se cachuje: notifikace se
     * překresluje při každé nové zprávě a načítat pokaždé tentýž soubor z disku by bylo zbytečné.
     *
     * Chybu polyká schválně — soubor mohl mezitím zmizet a notifikace bez profilovky je pořád
     * lepší než žádná.
     */
    private fun avatarIcon(path: String?): IconCompat? {
        if (path == null) return null

        icons[path]?.let { return it }

        val bitmap = runCatching { BitmapFactory.decodeFile(path) }.getOrNull() ?: return null
        val icon = IconCompat.createWithAdaptiveBitmap(bitmap)
        icons[path] = icon
        return icon
    }

    /** Rozepsaná notifikace jednoho chatu. */
    private class Conversation(
        val chatId: Long,
        val title: String,
        var lines: List<Line>,
    )

    /** Jedna zpráva v notifikaci. [notificationId] je `null` u řádku převzatého po restartu. */
    private class Line(
        val notificationId: Int?,
        val sender: String?,
        val text: String,
        val timestampMs: Long,
        val icon: IconCompat?,
    )

    private companion object {
        const val CHANNEL_ID = "messages"

        /** Kolik zpráv se v notifikaci drží. Android jich stejně vykreslí jen posledních pár. */
        const val MAX_LINES = 10
    }
}
