package eu.tintera.plume.core.telegram

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import co.touchlab.kermit.Logger
import eu.tintera.plume.client.R
import eu.tintera.plume.core.notifications.NotificationIntents
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.notifications.channelId
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.resources.notification_call_decline
import eu.tintera.plume.resources.notification_call_answer_elsewhere
import eu.tintera.plume.resources.notification_call_voice
import eu.tintera.plume.resources.notification_call_video
import eu.tintera.plume.resources.notification_call_unknown
import eu.tintera.plume.core.notifications.CALLS_CHANNEL_ID
import eu.tintera.plume.core.notifications.createNotificationChannels
import eu.tintera.plume.core.notifications.deleteConversationChannels
import eu.tintera.plume.core.share.bookmarkIcon
import eu.tintera.plume.core.share.conversationShortcut
import eu.tintera.plume.core.share.pushConversationShortcut
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.notification_mark_read
import eu.tintera.plume.resources.notification_mute_hour
import eu.tintera.plume.resources.notification_reply
import eu.tintera.plume.resources.notification_reply_failed
import eu.tintera.plume.resources.notification_self_name
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

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
    private val settings: AppSettings,
    private val now: () -> Instant = { Clock.System.now() },
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
     * a přidá novou k téže zprávě. Pozná se podle id zprávy, ne podle textu — push u fotky
     * předřazuje popisku 🖼, takže se texty neshodují. Pro systém je to nová zpráva, takže bez tohohle telefon
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
            // Názvy kanálů i jméno „Já" se čtou z prostředků (`suspend`), takže se zakládá až tady.
            createNotificationChannels(context)
            logger.d { "Kanály vytvořeny, odebírám notifikace." }

            repository.updates.collect { update ->
                when (update) {
                    is NotificationUpdate.Added -> add(update.notifications)
                    is NotificationUpdate.Removed -> remove(update)
                    is NotificationUpdate.Call -> showCall(update.call)
                    NotificationUpdate.Cleared -> clear()
                }
            }
        }
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
                    isSavedMessages = newest.isSavedMessages,
                    canReply = newest.canReply,
                    canMute = newest.canMute,
                    channel = newest.channel,
                    // Po restartu procesu je mapa prázdná, ale notifikace v liště zůstaly.
                    // Bez převzetí jejich řádků by nová zpráva přepsala předchozí.
                    lines = recoverLines(groupId),
                )
            }

            // Fotka chatu se doplňuje i u už rozepsané konverzace: na začátku nemusela být
            // stažená a nechat kvůli tomu notifikaci navždy bez ikony by byla škoda.
            conversation.avatarPath = newest.chatAvatarPath ?: conversation.avatarPath
            // Práva psát se můžou změnit; platí to, co TDLib řekl u nejnovější zprávy.
            conversation.canReply = newest.canReply
            conversation.canMute = newest.canMute
            conversation.channel = newest.channel
            // Téma nejnovější zprávy: ťuknutí i odpověď míří tam, kam se uživatel právě dívá.
            // Z pushe téma nepřijde (`null` = celá skupina), a staré téma se nedrží: zpráva
            // z jiného tématu by jinak otevřela to předchozí.
            conversation.topicId = newest.topicId

            val added = batch
                .filter { line -> conversation.lines.none { it.notificationId == line.notificationId } }
                .map {
                    Line(
                        notificationId = it.notificationId,
                        messageId = it.messageId,
                        sender = it.senderName,
                        text = it.text,
                        timestampMs = it.timestamp * 1000L,
                        icon = avatarIcon(it.avatarPath),
                        image = it.imagePath?.let(::notificationImage),
                        imageCaption = it.imageCaption,
                    )
                }

            conversation.lines = (conversation.lines + added)
                .sortedBy { it.timestampMs }
                .takeLast(MAX_LINES)

            // Potichu, když nic nového nepřibylo, nebo přibylo jen to, co TDLib právě nahradil.
            val onlyReplacements = added.all { line -> consumeRemoved(groupId, line.messageId) }
            // V tichých hodinách upozorní jen zmínky a odpovědi; zbytek přijde na lištu potichu.
            val quiet = settings.quietHours.value.silences(conversation.channel, minuteOfDay())
            show(groupId, conversation, silent = newest.isSilent || onlyReplacements || quiet)
        }
    }

    /** Minuta dne v místním čase zařízení — v něm si uživatel tiché hodiny nastavil. */
    private fun minuteOfDay(): Int {
        val time = now().toLocalDateTime(TimeZone.currentSystemDefault())
        return time.hour * 60 + time.minute
    }

    /** Najde a spotřebuje nedávno odebraný řádek téže zprávy. */
    private fun consumeRemoved(groupId: Int, messageId: Long?): Boolean {
        if (messageId == null) return false
        val now = SystemClock.elapsedRealtime()
        recentlyRemoved.removeAll { now - it.removedAtMs > REPLACEMENT_WINDOW_MS }
        val match = recentlyRemoved.firstOrNull { it.groupId == groupId && it.messageId == messageId } ?: return false
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
        // Fotky z notifikací odhlášeného účtu nemají v cache co dělat.
        imagesDirectory.deleteRecursively()
        recentlyRemoved.clear()
        // Notifikace z minulého běhu procesu v `conversations` nejsou.
        manager.cancelAll()
        runCatching { deleteConversationChannels(context) }
            .onFailure { logger.w(it) { "Kanály konverzací se nepodařilo smazat." } }
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
            logger.d { "Skupina ${update.groupId} není v paměti, ruším celou notifikaci." }
            manager.cancel(update.groupId)
            return
        }

        val removed = update.notificationIds.toSet()
        val now = SystemClock.elapsedRealtime()
        conversation.lines
            .filter { it.notificationId in removed }
            .forEach { line ->
                line.messageId?.let { recentlyRemoved += RemovedLine(update.groupId, it, now) }
            }
        conversation.lines = conversation.lines.filterNot { it.notificationId in removed }

        // Vlastní odpověď z notifikace TDLib nezná, takže ji nikdy neodebere. Přečtení chatu
        // (a to odpověď dělá) má notifikaci zavřít, i když v ní odpověď ještě visí.
        if (conversation.lines.all { it.isOwn }) {
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
                if (conversations[update.groupId] === conversation && conversation.lines.all { it.isOwn }) {
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
        val isGroup = conversation.lines.any { !it.isOwn && it.sender != null }

        val style = NotificationCompat.MessagingStyle(Person.Builder().setName(readString(Res.string.notification_self_name)).build())
            .setGroupConversation(isGroup)
            // V soukromém chatu je název chatu totéž co jméno odesílatele u každého řádku —
            // vypsat ho i do hlavičky by ho zdvojilo.
            .setConversationTitle(conversation.title.takeIf { isGroup })

        conversation.lines.forEach { line ->
            val message = NotificationCompat.MessagingStyle.Message(
                line.text,
                line.timestampMs,
                // `null` = uživatel sám (`MessagingStyle` ho kreslí jako „Já").
                if (line.isOwn) null else Person.Builder()
                    .setName(line.sender ?: conversation.title)
                    // Profilovka u řádku jen ve SKUPINĚ. V soukromém chatu je to tatáž fotka,
                    // kterou nese ikona konverzace — dvě stejné fotky v jedné notifikaci nad
                    // sebou nic nepřidají, jen zabírají místo.
                    .setIcon(line.icon.takeIf { isGroup })
                    .build(),
            )
            // Fotka se ukáže přímo v řádku; text zůstává jako popisek a záloha pro čtenáře
            // notifikací, kteří obrázky nekreslí.
            line.image?.let { message.setData(IMAGE_MIME_TYPE, it) }
            style.addMessage(message)
            // Popisek jako řádek POD obrázkem, jako v bublině. Bez obrázku ho nese text řádku.
            if (line.image != null && line.imageCaption != null) {
                style.addMessage(line.imageCaption, line.timestampMs, message.person)
            }
        }

        val built = NotificationCompat.Builder(context, conversation.channel.channelId)
            .setSmallIcon(R.drawable.ic_notification)
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
            .apply {
                if (conversation.canReply) {
                    // Ve skupině odpověď cituje nejnovější zprávu z notifikace, jako oficiální
                    // klient: bez citace by nebylo poznat, komu z více lidí se odpovídá.
                    // V soukromém chatu je to jasné a citace by byla šum.
                    val quoted = if (isGroup) conversation.lines.lastOrNull { !it.isOwn && it.messageId != null }?.messageId else null
                    replyAction(groupId, conversation.chatId, quoted, conversation.topicId)?.let(::addAction)
                }
                // Pořadí akcí: odpověď, přečtení, ztlumení — od nejčastější. Android jich ukáže
                // nejvýš tři, víc jich tedy být nesmí.
                addAction(
                    markReadAction(
                        groupId = groupId,
                        chatId = conversation.chatId,
                        messageId = conversation.lines.lastOrNull { !it.isOwn && it.messageId != null }?.messageId,
                    ),
                )
                if (conversation.canMute) addAction(muteAction(groupId, conversation.chatId))
            }
            .setContentIntent(
                NotificationIntents.openChat(
                    context = context,
                    notificationId = groupId,
                    chatId = conversation.chatId,
                    topicId = conversation.topicId,
                ),
            )
            .build()

        logger.d { "Zobrazuji $groupId: ${conversation.lines.size} zpráv z ${conversation.chatId}." }
        manager.notify(groupId, built)
    }

    /**
     * Příchozí hovor. Vlastní notifikace v kanálu hovorů, ne řádek konverzace: hovor není zpráva
     * a „Odpovědět" ani „Přečteno" k němu nepatří.
     *
     * - **`CallStyle` z Androidu ne**: od Androidu 12 chce službu v popředí, nebo celoobrazovkový
     *   intent, a přijmout hovor stejně nejde. Text proto řekne, kde ho přijmout.
     * - **Id je id skupiny notifikací**, jako u zpráv: konec hovoru přijde jako `Removed` téže
     *   skupiny a [remove] ji zruší celou (skupinu hovoru v [conversations] nedržíme).
     * - **Stáhne se i sama po [CALL_TIMEOUT_MS]** — vyzvánění dávno skončí, kdyby se zrušení
     *   z nějakého důvodu ztratilo.
     */
    private suspend fun showCall(call: IncomingCall) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val name = call.callerName.ifBlank { readString(Res.string.notification_call_unknown) }
        val built = NotificationCompat.Builder(context, CALLS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(avatarBitmap(call.avatarPath))
            .setContentTitle(name)
            .setContentText(readString(if (call.isVideo) Res.string.notification_call_video else Res.string.notification_call_voice))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    readString(if (call.isVideo) Res.string.notification_call_video else Res.string.notification_call_voice) +
                        "\n" + readString(Res.string.notification_call_answer_elsewhere),
                ),
            )
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            // Dokud zvoní, nemá jít smáznout omylem; konec hovoru ji stáhne sám.
            .setOngoing(true)
            .setTimeoutAfter(CALL_TIMEOUT_MS)
            .addAction(
                NotificationCompat.Action.Builder(
                    IconCompat.createWithResource(context, R.drawable.ic_notification_mute),
                    readString(Res.string.notification_call_decline),
                    NotificationIntents.declineCall(context, call.groupId, call.chatId, call.callId, call.isVideo),
                )
                    .setShowsUserInterface(false)
                    .build(),
            )
            .setContentIntent(NotificationIntents.openChat(context = context, notificationId = call.groupId, chatId = call.chatId))
            .build()
        logger.d { "Zobrazuji příchozí hovor ${call.callId} (${if (call.isVideo) "video" else "hlas"})." }
        manager.notify(call.groupId, built)
    }

    /**
     * Připíše do notifikace odpověď, kterou uživatel právě odeslal z notifikace.
     *
     * **Android čeká, až aplikace notifikaci po odpovědi překreslí** — do té doby se v ní točí
     * kolečko. Překreslit ji je tedy povinnost, a vlastní řádek zároveň ukáže, co odešlo;
     * po potvrzení chat přečteme a TDLib notifikaci stáhne.
     */
    suspend fun showReply(groupId: Int, text: String) = withContext(dispatcher) {
        appendOwnLine(groupId, text, silent = true)
    }

    /**
     * Odpověď neodešla. Řádek s ní se nahradí hláškou i s textem — ten by se jinak ztratil —
     * a notifikace upozorní znovu: selhání je jediné, co uživatel zaregistrovat musí.
     */
    suspend fun showReplyFailed(groupId: Int, text: String) = withContext(dispatcher) {
        appendOwnLine(groupId, readString(Res.string.notification_reply_failed, text), silent = false, replacing = text)
    }

    private suspend fun appendOwnLine(groupId: Int, text: String, silent: Boolean, replacing: String? = null) {
        val now = System.currentTimeMillis()
        val conversation = conversations[groupId]
        if (conversation != null) {
            val kept = conversation.lines.filterNot { it.isOwn && it.text == replacing }
            conversation.lines = (kept + Line(notificationId = null, sender = null, text = text, timestampMs = now, icon = null, isOwn = true))
                .takeLast(MAX_LINES)
            show(groupId, conversation, silent)
            return
        }

        /*
         * Notifikaci z minulého běhu procesu v paměti nemáme — akce z notifikace umí proces
         * probudit, takže to je běžný případ. Poskládat ji znovu z `ChatNotification` nejde (fotku
         * chatu ani zkratku už nemáme), přestaví se tedy z té, která je v liště.
         */
        val existing = manager.activeNotifications.firstOrNull { it.id == groupId }?.notification ?: return
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(existing) ?: return
        if (replacing != null) style.messages.removeAll { it.person == null && it.text?.toString() == replacing }
        style.addMessage(text, now, null as Person?)
        val rebuilt = NotificationCompat.Builder(context, existing)
            .setStyle(style)
            .setSilent(silent)
            .build()
        manager.notify(groupId, rebuilt)
    }

    /**
     * Akce „Odpovědět" s polem na text.
     *
     * `PendingIntent` musí být **MUTABLE** — systém do něj vkládá napsaný text; s IMMUTABLE by
     * příjemce dostal prázdné výsledky. Sémantika `REPLY` a `showsUserInterface = false` říkají
     * hodinkám a Android Auto, že jde o odpověď bez otevření aplikace.
     */
    private suspend fun replyAction(groupId: Int, chatId: Long, replyToMessageId: Long?, topicId: Int?): NotificationCompat.Action? {
        val intent = NotificationIntents.reply(
            context,
            groupId = groupId,
            chatId = chatId,
            replyToMessageId = replyToMessageId,
            topicId = topicId,
        )
        val label = readString(Res.string.notification_reply)
        val input = RemoteInput.Builder(NotificationIntents.KEY_REPLY_TEXT)
            .setLabel(label)
            .build()
        return NotificationCompat.Action.Builder(
            IconCompat.createWithResource(context, R.drawable.ic_notification_reply),
            label,
            intent,
        )
            .addRemoteInput(input)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            // Chytré návrhy odpovědí od systému (Android 10+) a na hodinkách.
            .setAllowGeneratedReplies(true)
            .build()
    }

    /**
     * Akce „Označit jako přečtené". Nabízí se vždycky, i v kanálu — číst jde všude.
     *
     * Sémantika `MARK_AS_READ` a `showsUserInterface = false`: hodinky a Android Auto ji pak
     * nabídnou jako přečtení, ne jako obecné tlačítko, a systém kvůli ní neodemyká obrazovku.
     */
    private suspend fun markReadAction(groupId: Int, chatId: Long, messageId: Long?): NotificationCompat.Action =
        NotificationCompat.Action.Builder(
            IconCompat.createWithResource(context, R.drawable.ic_notification_mark_read),
            readString(Res.string.notification_mark_read),
            NotificationIntents.markRead(context, groupId = groupId, chatId = chatId, messageId = messageId),
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

    /**
     * Akce „Ztlumit na hodinu". Sémantika `MUTE` pro hodinky a Android Auto.
     */
    private suspend fun muteAction(groupId: Int, chatId: Long): NotificationCompat.Action =
        NotificationCompat.Action.Builder(
            IconCompat.createWithResource(context, R.drawable.ic_notification_mute),
            readString(Res.string.notification_mute_hour),
            NotificationIntents.mute(context, groupId = groupId, chatId = chatId),
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MUTE)
            .setShowsUserInterface(false)
            .build()

    /**
     * Fotka ze zprávy pro notifikaci jako `content://` URI.
     *
     * **Notifikaci kreslí jiný proces** (System UI), a ten do souborů aplikace nesmí — cesta
     * k souboru TDLib by mu nebyla k ničemu. Obrázek se proto vydává přes [FileProvider];
     * oprávnění ke čtení přidělí System UI sám `NotificationManager`, protože URI patří
     * aplikaci, která notifikaci posílá. Čtečkám notifikací (hodinky) se přiděluje ručně.
     *
     * **Vydává se zmenšená kopie v cache, ne soubor TDLib.** Provider tak nemusí zpřístupnit
     * celý adresář TDLib, kopie nezmizí, když TDLib uklidí soubory, a System UI nedekóduje víc
     * pixelů, než do notifikace vejde. Když se kopie nepovede, notifikace zůstane bez fotky.
     */
    private fun notificationImage(path: String): Uri? = runCatching {
        imagesDirectory.mkdirs()
        pruneImages()

        val target = java.io.File(imagesDirectory, "${path.hashCode().toUInt()}.jpg")
        if (!target.exists()) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_EDGE) sample *= 2

            val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, it) }
            bitmap.recycle()
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}$IMAGE_AUTHORITY_SUFFIX", target)
        // System UI oprávnění dostane automaticky, aplikace přeposílající notifikace ne —
        // hodinky (Wear) pak hlásily SecurityException a obrázek vynechaly. Obsah notifikace
        // tyhle aplikace čtou i tak, fotka navíc nic dalšího neprozradí.
        NotificationManagerCompat.getEnabledListenerPackages(context).forEach { listener ->
            context.grantUriPermission(listener, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        uri
    }
        .onFailure { logger.w(it) { "Fotku do notifikace se nepodařilo připravit." } }
        .getOrNull()

    /**
     * Zahodí kopie starší než [IMAGE_MAX_AGE_MS]. Notifikace tak dlouho na liště nevisí, a kdyby
     * přece, přijde jen o obrázek — text zůstane.
     */
    private fun pruneImages() {
        val threshold = System.currentTimeMillis() - IMAGE_MAX_AGE_MS
        imagesDirectory.listFiles()?.filter { it.lastModified() < threshold }?.forEach { it.delete() }
    }

    /** Musí sedět s `notification_images.xml` v manifestu aplikace. */
    private val imagesDirectory: java.io.File get() = java.io.File(context.cacheDir, IMAGES_DIRECTORY)

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
                // Bez osoby = uživatel sám, tedy odpověď odeslaná z notifikace.
                isOwn = message.person == null,
                image = message.dataUri?.takeIf { message.dataMimeType?.startsWith("image/") == true },
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
        // Tatáž zkratka slouží i sdílení (Direct Share), viz `conversationShortcut`.
        val shortcut = conversationShortcut(
            context = context,
            chatId = conversation.chatId,
            title = conversation.title,
            photo = if (conversation.isSavedMessages) bookmarkIcon(context) else avatarIcon(conversation.avatarPath),
        ) ?: return null
        return shortcut.id.takeIf { pushConversationShortcut(context, shortcut) }
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
        /** Uložené zprávy: zkratka se záložkou, jako ve sdílení (`AndroidShareTargetPublisher`). */
        val isSavedMessages: Boolean = false,
        /** Nabídnout akci „Odpovědět". */
        var canReply: Boolean = true,
        /** Nabídnout akci „Ztlumit". */
        var canMute: Boolean = true,
        /** Systémový kanál; TDLib drží zmínky v jiné skupině než běžné zprávy téhož chatu. */
        var channel: NotificationChannelKind = NotificationChannelKind.Private,
        /** Téma nejnovější zprávy ve skupině s tématy; `null` = celý chat. */
        var topicId: Int? = null,
    )

    /** Jedna zpráva v notifikaci. [notificationId] je `null` u řádku převzatého po restartu. */
    private class Line(
        val notificationId: Int?,
        /** `null` u řádku převzatého po restartu, stejně jako [notificationId]. */
        val messageId: Long? = null,
        val sender: String?,
        val text: String,
        val timestampMs: Long,
        val icon: IconCompat?,
        /** Fotka ze zprávy jako `content://` URI z [FileProvider]. */
        val image: Uri? = null,
        val imageCaption: String? = null,
        /** Odpověď odeslaná z notifikace. TDLib o ní neví, takže ji neodebere. */
        val isOwn: Boolean = false,
    )

    private class RemovedLine(val groupId: Int, val messageId: Long, val removedAtMs: Long)

    private companion object {
        /** Kolik zpráv se v notifikaci drží. Android jich stejně vykreslí jen posledních pár. */
        const val MAX_LINES = 10

        /** Déle než minutu nikdo nevyzvání; notifikace hovoru pak zmizí i bez zrušení od TDLib. */
        const val CALL_TIMEOUT_MS = 90_000L

        /** Podadresář cache s fotkami pro notifikace. Sdílí ho `res/xml/notification_images.xml`. */
        const val IMAGES_DIRECTORY = "notification_images"

        /** Přípona autority [FileProvider]u za `packageName`; stejná jako v manifestu. */
        const val IMAGE_AUTHORITY_SUFFIX = ".notificationimages"

        const val IMAGE_MIME_TYPE = "image/jpeg"

        /** Delší hrana kopie. Notifikace obrázek ukáže nejvýš přes šířku lišty. */
        const val MAX_IMAGE_EDGE = 1024

        const val IMAGE_QUALITY = 85

        const val IMAGE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

        /** Jak dlouho po odebrání se nový řádek se stejným textem považuje za náhradu. */
        const val REPLACEMENT_WINDOW_MS = 60_000L

        /** Jak dlouho prázdná notifikace čeká na náhradu, než se zruší. */
        const val EMPTY_CANCEL_DELAY_MS = 1_000L

        /** Prefix id zkratky konverzace. Musí být stabilní, aby se zkratka přepisovala, ne množila. */
    }
}
