package eu.tintera.plume.core.telegram

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.notifications.NotificationIntents
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.notification_channel_description
import eu.tintera.plume.resources.notification_channel_name
import eu.tintera.plume.resources.notification_self_name
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

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
     * Čte i zapisuje se jen na [dispatcher] — zámek proto není potřeba.
     */
    private val conversations = mutableMapOf<Int, Conversation>()

    /**
     * Dekódované profilovky podle cesty.
     *
     * Drží se bitmapy, ne hotové ikony: tatáž fotka jde jednou k řádku jako `IconCompat`
     * a podruhé jako velká ikona notifikace, která bere `Bitmap`. Notifikace se navíc
     * překresluje při každé nové zprávě, takže číst soubor pokaždé znovu by bylo zbytečné.
     *
     * Sahá se na ně ze stejné korutiny jako na [conversations], takže bez zámku.
     */
    private val bitmaps = mutableMapOf<String, Bitmap>()

    /**
     * Řádky, které TDLib nedávno odebral — kvůli rozpoznání NÁHRADY.
     *
     * Z pushe vydá TDLib dočasnou notifikaci a jakmile dostane skutečnou zprávu, dočasnou odebere
     * a přidá novou se stejným textem. Pro systém je to nová zpráva, takže bez tohohle telefon
     * upozornil dvakrát (ověřeno v `notification_alert` event logu). Sahá se sem ze stejné
     * korutiny jako na [conversations].
     */
    private val recentlyRemoved = mutableListOf<RemovedLine>()

    /**
     * Všechno, co sahá na stav presenteru, běží na jednom vlákně naráz — kolektor updatů
     * i odložené rušení prázdných notifikací.
     */
    private val dispatcher = Dispatchers.Default.limitedParallelism(1)

    actual fun start(repository: NotificationRepository) {
        scope.launch(dispatcher) {
            // Kanál i jméno „Já" se čtou z prostředků (`suspend`), takže se zakládá až tady.
            createChannel()
            logger.d { "Kanál vytvořen, odebírám notifikace." }

            repository.updates.collect { update ->
                when (update) {
                    is NotificationUpdate.Added -> add(update.notifications)
                    is NotificationUpdate.Removed -> remove(update)
                    NotificationUpdate.Cleared -> clear()
                }
            }
        }
    }

    private suspend fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(Res.string.notification_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(Res.string.notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    private suspend fun add(notifications: List<ChatNotification>) {
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

            // Fotka chatu se doplňuje i u už rozepsané konverzace: na začátku nemusela být
            // stažená a nechat kvůli tomu notifikaci navždy bez ikony by byla škoda.
            conversation.avatarPath = newest.chatAvatarPath ?: conversation.avatarPath

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

            // Potichu, když nic nového nepřibylo, nebo přibylo jen to, co TDLib právě nahradil.
            val onlyReplacements = added.all { line -> consumeRemoved(groupId, line.text) }
            show(groupId, conversation, silent = newest.isSilent || onlyReplacements)
        }
    }

    /** Najde a spotřebuje nedávno odebraný řádek se stejným textem. */
    private fun consumeRemoved(groupId: Int, text: String): Boolean {
        val now = SystemClock.elapsedRealtime()
        recentlyRemoved.removeAll { now - it.removedAtMs > REPLACEMENT_WINDOW_MS }
        val match = recentlyRemoved.firstOrNull { it.groupId == groupId && it.text == text } ?: return false
        recentlyRemoved.remove(match)
        return true
    }

    /**
     * Úklid po odhlášení: notifikace i zkratky konverzací. Zkratky nesou názvy chatů a fotky
     * a visí v nabídce ikony aplikace — po odhlášení by tam zůstaly chaty cizího účtu.
     */
    private fun clear() {
        conversations.keys.forEach { manager.cancel(it) }
        conversations.clear()
        bitmaps.clear()
        recentlyRemoved.clear()
        // Notifikace z minulého běhu procesu v `conversations` nejsou.
        manager.cancelAll()
        // Dlouhověké zkratky si systém drží i mimo dynamické, `removeAllDynamicShortcuts` by
        // je v keši nechal.
        runCatching {
            val ids = ShortcutManagerCompat.getShortcuts(
                context,
                ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_CACHED,
            ).map { it.id }
            ShortcutManagerCompat.removeLongLivedShortcuts(context, ids)
        }
            .onFailure { logger.w(it) { "Zkratky konverzací se nepodařilo odebrat." } }
        logger.d { "Odhlášení: notifikace a zkratky uklizeny." }
    }

    private suspend fun remove(update: NotificationUpdate.Removed) {
        val conversation = conversations[update.groupId]

        // Neznámá skupina = notifikace z minulého běhu procesu. Její řádky nemáme s čím
        // spárovat, takže jde pryč celá; TDLib stejně ruší po přečtení celý chat naráz.
        if (conversation == null) {
            manager.cancel(update.groupId)
            return
        }

        val removed = update.notificationIds.toSet()
        val now = SystemClock.elapsedRealtime()
        conversation.lines
            .filter { it.notificationId in removed }
            .forEach { recentlyRemoved += RemovedLine(update.groupId, it.text, now) }
        conversation.lines = conversation.lines.filterNot { it.notificationId in removed }

        if (conversation.lines.isEmpty()) {
            /*
             * Prázdná notifikace se ruší AŽ PO CHVILCE. TDLib dočasnou notifikaci z pushe
             * nahrazuje odebráním a hned přidáním skutečné — naměřeno 5 ms od sebe. Okamžité
             * zrušení smazalo notifikaci dřív, než systém stihl upozornit, a náhrada je tichá:
             * telefon pak nezavibroval vůbec. Když náhrada dorazí, jen stávající notifikaci
             * přepíše a původní upozornění zůstane. Stejný dispatcher jako kolektor, takže
             * se úloha s ním o stav nepřetahuje.
             */
            scope.launch(dispatcher) {
                delay(EMPTY_CANCEL_DELAY_MS)
                if (conversations[update.groupId] === conversation && conversation.lines.isEmpty()) {
                    conversations.remove(update.groupId)
                    manager.cancel(update.groupId)
                    logger.d { "Skupina ${update.groupId} zůstala prázdná, ruším notifikaci." }
                }
            }
        } else {
            show(update.groupId, conversation, silent = true)
        }
    }

    private suspend fun show(groupId: Int, conversation: Conversation, silent: Boolean) {
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

        val style = NotificationCompat.MessagingStyle(Person.Builder().setName(getString(Res.string.notification_self_name)).build())
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
                    // Profilovka u řádku jen ve SKUPINĚ. V soukromém chatu je to tatáž fotka,
                    // kterou nese ikona konverzace — dvě stejné fotky v jedné notifikaci nad
                    // sebou nic nepřidají, jen zabírají místo.
                    .setIcon(line.icon.takeIf { isGroup })
                    .build(),
            )
        }

        val built = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            // Velká ikona je záloha. U `MessagingStyle` si ji Android bere ze ZKRATKY (viz
            // `publishShortcut`) a tuhle ignoruje — uplatní se, jen když se zkratku nepodaří
            // publikovat.
            .setLargeIcon(avatarBitmap(conversation.avatarPath))
            // Bez zkratky není notifikace „konverzace": Android pak do velkého kruhu dosadí
            // ikonu aplikace a notifikace skončí mimo sekci konverzací.
            .setShortcutId(publishShortcut(conversation))
            .setStyle(style)
            // Podle kategorie se řídí výjimka „zprávy" v režimu Nerušit; čtou ji i aplikace,
            // které notifikace třídí (hodinky, LED).
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            // Systém si titulek a text doplní z `MessagingStyle`, ticker ne — a starší čtenáři
            // notifikací berou text právě odsud.
            .setTicker(conversation.lines.lastOrNull()?.let { "${it.sender ?: conversation.title}: ${it.text}" })
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
     * Zkratka konverzace. Vrací její id, nebo `null`, když se ji nepodařilo publikovat.
     *
     * **Tohle je to, odkud Android bere velký kruh v notifikaci.** `setLargeIcon` u `MessagingStyle`
     * ignoruje; bez zkratky notifikaci nepovažuje za konverzaci a do kruhu dosadí ikonu aplikace.
     * Tak vznikly dvě ikony naráz: panáček nahoře a profilovka u řádku.
     *
     * Zkratka musí být **dlouhověká** (`setLongLived`) — jinak ji systém po čase zahodí a velký
     * kruh se u starších notifikací vrátí k ikoně aplikace. Zároveň se objeví v nabídce po
     * podržení ikony aplikace, což je záměr: přesně takhle to dělá Telegram i Messages.
     *
     * `pushDynamicShortcut` sám hlídá systémový strop na počet zkratek a nejstarší odsouvá,
     * takže se tu nic nepočítá.
     */
    private fun publishShortcut(conversation: Conversation): String? {
        val intent = NotificationIntents.chatIntent(context, conversation.chatId) ?: return null
        val id = SHORTCUT_PREFIX + conversation.chatId

        val person = Person.Builder()
            .setName(conversation.title)
            .setKey(id)
            .setIcon(avatarIcon(conversation.avatarPath))
            .build()

        val shortcut = ShortcutInfoCompat.Builder(context, id)
            .setLongLived(true)
            .setShortLabel(conversation.title)
            .setIntent(intent)
            .setPerson(person)
            .apply { avatarIcon(conversation.avatarPath)?.let { setIcon(it) } }
            .build()

        // Chybu polyká schválně: notifikace bez zkratky je pořád lepší než žádná notifikace.
        val pushed = runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
            .onFailure { logger.w(it) { "Zkratku konverzace se nepodařilo publikovat." } }
            .getOrDefault(false)

        return id.takeIf { pushed }
    }

    /** Profilovka jako ikona řádku v notifikaci. */
    private fun avatarIcon(path: String?): IconCompat? {
        // `createWithAdaptiveBitmap` kvůli kulatému ořezu — Android profilovky v notifikacích
        // kreslí v kruhu a čtvercová fotka by z řady vyčnívala.
        val bitmap = avatarBitmap(path) ?: return null
        return IconCompat.createWithAdaptiveBitmap(bitmap)
    }

    /** Chybu polyká schválně — soubor mohl zmizet a notifikace bez profilovky je lepší než žádná. */
    private fun avatarBitmap(path: String?): Bitmap? {
        if (path == null) return null

        bitmaps[path]?.let { return it }

        val bitmap = runCatching { BitmapFactory.decodeFile(path) }.getOrNull() ?: return null
        bitmaps[path] = bitmap
        return bitmap
    }

    /** Rozepsaná notifikace jednoho chatu. */
    private class Conversation(
        val chatId: Long,
        val title: String,
        var lines: List<Line>,
        /** Fotka chatu pro velkou ikonu notifikace. */
        var avatarPath: String? = null,
    )

    /** Jedna zpráva v notifikaci. [notificationId] je `null` u řádku převzatého po restartu. */
    private class Line(
        val notificationId: Int?,
        val sender: String?,
        val text: String,
        val timestampMs: Long,
        val icon: IconCompat?,
    )

    private class RemovedLine(val groupId: Int, val text: String, val removedAtMs: Long)

    private companion object {
        const val CHANNEL_ID = "messages"

        /** Kolik zpráv se v notifikaci drží. Android jich stejně vykreslí jen posledních pár. */
        const val MAX_LINES = 10

        /** Jak dlouho po odebrání se nový řádek se stejným textem považuje za náhradu. */
        const val REPLACEMENT_WINDOW_MS = 60_000L

        /** Jak dlouho prázdná notifikace čeká na náhradu, než se zruší. */
        const val EMPTY_CANCEL_DELAY_MS = 1_000L

        /** Prefix id zkratky konverzace. Musí být stabilní, aby se zkratka přepisovala, ne množila. */
        const val SHORTCUT_PREFIX = "chat_"
    }
}
