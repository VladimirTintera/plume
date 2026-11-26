package eu.tintera.plume.core.notifications

import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.share.shortcutChat
import eu.tintera.plume.core.share.shortcutLabel
import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
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
import eu.tintera.plume.core.calls.CallJournal
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.share.bookmarkIcon
import eu.tintera.plume.core.share.conversationShortcut
import eu.tintera.plume.core.share.pushConversationShortcut
import eu.tintera.plume.core.snooze.ChatSnoozes
import eu.tintera.plume.core.calls.CallQuickReplies
import eu.tintera.plume.core.telegram.CallBack
import eu.tintera.plume.core.telegram.ChatNotification
import eu.tintera.plume.core.telegram.IncomingCall
import eu.tintera.plume.core.telegram.NotificationChannelKind
import eu.tintera.plume.core.telegram.NotificationRepository
import eu.tintera.plume.core.telegram.NotificationSoundChoice
import eu.tintera.plume.core.telegram.NotificationUpdate
import eu.tintera.plume.core.telegram.dismissUpTo
import org.jetbrains.compose.resources.getString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.call_back
import eu.tintera.plume.resources.call_decline_with_message
import eu.tintera.plume.resources.call_message
import eu.tintera.plume.resources.notification_call_answer_elsewhere
import eu.tintera.plume.resources.notification_call_decline
import eu.tintera.plume.resources.notification_call_unknown
import eu.tintera.plume.resources.notification_call_video
import eu.tintera.plume.resources.notification_call_voice
import eu.tintera.plume.resources.notification_mark_read
import eu.tintera.plume.resources.notification_mute_hour
import eu.tintera.plume.resources.notification_reply
import eu.tintera.plume.resources.notification_reply_failed
import eu.tintera.plume.resources.notification_self_name
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

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
    private val slot: AccountSlot,
    private val context: Context,
    private val scope: CoroutineScope,
    private val settings: AppSettings,
    /** Odložené chaty: dokud spí, přijdou jejich notifikace potichu. */
    private val snoozes: ChatSnoozes? = null,
    /** Umí Plume hovor přijmout (mediální vrstva, `CallRepository.supported`)? */
    private val callsSupported: Boolean = false,
    private val now: () -> Instant = { Clock.System.now() },
    /** Deník hovorů: zvonění bez celoobrazovkové výzvy je stopa k „zamčený telefon jen zvonil". */
    private val journal: CallJournal? = null,
    /**
     * Jméno účtu, když je účtů víc; jinak `null`. Jde do podtextu notifikace (`setSubText`),
     * který systém ukáže v záhlaví vedle názvu aplikace — jinak není poznat, komu zpráva přišla.
     */
    private val accountName: suspend () -> String? = { null },
    /**
     * Má hovor jiný účet? Příchozí hovor pak repository účtu odmítne (hovor je přes účty jeden),
     * ale notifikaci hovoru vydá TDLib vlastní cestou, nezávisle na ní — bez tohohle se na
     * okamžik ukázala i s celoobrazovkovou výzvou (ověřeno na Pixelu 5).
     */
    private val callBusy: () -> Boolean = { false },
    /**
     * Smí se notifikace ukázat? Ve spojené společné skupině ji ohlásí každý účet, ukáže se jen
     * od prvního (`SharedChats.mayNotify`).
     */
    private val mayNotify: suspend (ChatNotification) -> Boolean = { true },
) {

    private val logger = Logger.withTag("Notifications")

    private val manager = NotificationManagerCompat.from(context)

    /**
     * Tag systémových notifikací účtu: `notificationGroupId` je jedinečné jen v rámci účtu, a se
     * dvěma účty by se notifikace přepisovaly. Primární účet tag nemá, takže jeho notifikace
     * z dřívějška (a z bublin) zůstávají tytéž.
     */
    private val tag: String? = slot.id.takeUnless { slot.isPrimary }

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
                    is NotificationUpdate.ChatLocked -> forgetChat(update.chatId)
                }
            }
        }
    }

    private suspend fun add(added: List<ChatNotification>) {
        val notifications = added.filter { mayNotify(it) }
        if (notifications.size < added.size) logger.d { "${added.size - notifications.size} notifikací ukazuje jiný účet." }
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
                    sound = newest.sound,
                    // Po restartu procesu je mapa prázdná, ale notifikace v liště zůstaly.
                    // Bez převzetí jejich řádků by nová zpráva přepsala předchozí.
                    lines = recoverLines(groupId),
                )
            }

            // Fotka chatu se doplňuje i u už rozepsané konverzace: na začátku nemusela být
            // stažená a nechat kvůli tomu notifikaci navždy bez ikony by byla škoda.
            conversation.avatarPath = newest.chatAvatarPath ?: conversation.avatarPath
            conversation.isLocked = newest.isLocked
            // Práva psát se můžou změnit; platí to, co TDLib řekl u nejnovější zprávy.
            conversation.canReply = newest.canReply
            conversation.canMute = newest.canMute
            conversation.channel = newest.channel
            conversation.sound = newest.sound
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
                        callBack = it.callBack,
                    )
                }

            conversation.lines = (conversation.lines + added)
                .sortedBy { it.timestampMs }
                .takeLast(MAX_LINES)

            // Potichu, když nic nového nepřibylo, nebo přibylo jen to, co TDLib právě nahradil.
            val onlyReplacements = added.all { line -> consumeRemoved(groupId, line.messageId) }
            // V tichých hodinách upozorní jen zmínky a odpovědi; zbytek přijde na lištu potichu.
            // Odložený chat se do konce odložení neozývá — potichu, ne vůbec, z téhož důvodu.
            val quiet = settings.quietHours.value.silences(conversation.channel, minuteOfDay()) ||
                snoozes?.isSleeping(conversation.chatId) == true
            if (newest.isSilent || onlyReplacements || quiet) {
                logger.d { "Notifikace potichu: od TDLib=${newest.isSilent}, jen náhrady=$onlyReplacements, ticho=$quiet" }
            }
            show(groupId, conversation, silent = newest.isSilent || onlyReplacements || quiet)
        }
    }

    /**
     * Notifikace, ze které je bublina, se NESMÍ zrušit: se zrušenou notifikací systém zruší
     * i bublinu (`reason=NOTIF_CANCEL`). A rušila by se hned — otevřená bublina zprávy přečte
     * a TDLib notifikaci stáhne. Místo zrušení se proto přepíše s potlačením v liště
     * (`setSuppressNotification`): z lišty zmizí, bublina zůstane. Další zpráva ji pošle znovu
     * normálně ([show]). Obnovuje se z AKTIVNÍ notifikace (`recoverBuilder`), takže to jde
     * i po restartu procesu, kdy presenter konverzaci v paměti nemá. Vrací, jestli tak udělal.
     */
    private fun keepBubble(groupId: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        val platform = context.getSystemService(NotificationManager::class.java) ?: return false
        val active = platform.activeNotifications.firstOrNull {
            it.id == groupId && it.tag == tag && it.notification.flags and Notification.FLAG_BUBBLE != 0
        } ?: return false
        val bubble = active.notification.bubbleMetadata ?: return false
        if (bubble.isNotificationSuppressed) return true
        val intent = bubble.intent ?: return false
        val icon = bubble.icon ?: return false
        val suppressed = Notification.Builder.recoverBuilder(context, active.notification)
            .setBubbleMetadata(
                Notification.BubbleMetadata.Builder(intent, icon)
                    .setDesiredHeight(bubble.desiredHeight)
                    .setSuppressNotification(true)
                    .build(),
            )
            // Přepis existující notifikace nesmí znovu upozornit.
            .setOnlyAlertOnce(true)
            .build()
        platform.notify(tag, groupId, suppressed)
        return true
    }

    /**
     * Bublina konverzace; `null` pod Androidem 11 a bez ikony. Ikona je fotka chatu, jako velký
     * kruh notifikace (`avatarBitmap`) — bez ní by všechny bubliny vypadaly stejně.
     */
    private fun bubbleMetadata(groupId: Int, conversation: Conversation): NotificationCompat.BubbleMetadata? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val icon = avatarBitmap(conversation.avatarPath)?.let(IconCompat::createWithAdaptiveBitmap)
            ?: IconCompat.createWithResource(context, R.drawable.ic_notification)
        return NotificationCompat.BubbleMetadata.Builder(
            NotificationIntents.bubble(context, slot, groupId, conversation.chatId),
            icon,
        )
            .setDesiredHeight(BUBBLE_HEIGHT_DP)
            .build()
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
     * Uživatel notifikaci odhrnul: zapomenout její řádky. [conversations] by je jinak držely
     * a další zpráva téhož chatu by notifikaci poskládala i s tím, co uživatel zavřel. TDLib
     * o odhrnutí řekne příjemce akce zvlášť (`NotificationActions.dismiss`).
     */
    suspend fun dismissed(groupId: Int) = withContext(dispatcher) {
        conversations.remove(groupId)
        recentlyRemoved.removeAll { it.groupId == groupId }
        logger.d { "Notifikace $groupId odhrnuta, řádky zapomenuty." }
    }

    /**
     * Úklid po odhlášení: notifikace i zkratky konverzací. Zkratky nesou názvy chatů a fotky
     * a visí v nabídce ikony aplikace — po odhlášení by tam zůstaly chaty cizího účtu.
     */
    private fun clear() {
        conversations.keys.forEach { manager.cancel(tag, it) }
        conversations.clear()
        bitmaps.clear()
        // Fotky z notifikací odhlášeného účtu nemají v cache co dělat.
        imagesDirectory.deleteRecursively()
        recentlyRemoved.clear()
        // Notifikace z minulého běhu procesu v `conversations` nejsou. Jen tohoto účtu (podle
        // tagu): ostatní účty zůstávají přihlášené.
        manager.activeNotifications.filter { it.tag == tag }.forEach { manager.cancel(tag, it.id) }
        runCatching { deleteConversationChannels(context, slot) }
            .onFailure { logger.w(it) { "Kanály konverzací se nepodařilo smazat." } }
        // Zvukové kanály tohoto účtu (nesou slot v id, primární účet bez něj).
        runCatching { deleteSoundChannels(context, slot) }
            .onFailure { logger.w(it) { "Kanály se zvukem se nepodařilo smazat." } }
        // Dlouhověké zkratky si systém drží i mimo dynamické, `removeAllDynamicShortcuts` by
        // je v keši nechal.
        runCatching {
            val ids = ShortcutManagerCompat.getShortcuts(
                context,
                ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_CACHED,
            ).map { it.id }.filter { shortcutChat(it)?.slot == slot }
            ShortcutManagerCompat.removeLongLivedShortcuts(context, ids)
        }
            .onFailure { logger.w(it) { "Zkratky konverzací se nepodařilo odebrat." } }
        logger.d { "Odhlášení: notifikace a zkratky uklizeny." }
    }

    /**
     * Chat se zamkl: pryč s jeho notifikacemi (nesou jméno a text) i se zkratkou konverzace, kterou
     * kvůli nim dostal systém (Direct Share, nabídka ikony, jméno v Android Auto). Notifikace
     * z minulého běhu procesu presenter v paměti nemá; pozná se podle zkratky, kterou nese.
     */
    private fun forgetChat(chatId: Long) {
        conversations.filterValues { it.chatId == chatId }.keys.toList().forEach { groupId ->
            manager.cancel(tag, groupId)
            conversations.remove(groupId)
        }
        manager.activeNotifications
            .filter { it.tag == tag && shortcutChat(it.notification.shortcutId)?.let { target -> target.slot == slot && target.chatId == chatId } == true }
            .forEach { manager.cancel(tag, it.id) }
        runCatching {
            val ids = ShortcutManagerCompat.getShortcuts(
                context,
                ShortcutManagerCompat.FLAG_MATCH_DYNAMIC or ShortcutManagerCompat.FLAG_MATCH_CACHED or ShortcutManagerCompat.FLAG_MATCH_PINNED,
            ).map { it.id }.filter { id -> shortcutChat(id)?.let { it.slot == slot && it.chatId == chatId } == true }
            ShortcutManagerCompat.removeLongLivedShortcuts(context, ids)
            // Zkratku připnutou na ploše aplikace odebrat nesmí, jen vypnout: ťuknutí pak nic neotevře.
            ShortcutManagerCompat.disableShortcuts(context, ids, null)
        }.onFailure { logger.w(it) { "Zkratku zamčeného chatu se nepodařilo odebrat." } }
        logger.d { "Chat $chatId zamčen: notifikace a zkratka uklizeny." }
    }

    private suspend fun remove(update: NotificationUpdate.Removed) {
        val conversation = conversations[update.groupId]

        // Neznámá skupina = notifikace z minulého běhu procesu. Její řádky nemáme s čím
        // spárovat, takže jde pryč celá; TDLib stejně ruší po přečtení celý chat naráz.
        if (conversation == null) {
            logger.d { "Skupina ${update.groupId} není v paměti, ruším celou notifikaci." }
            if (!keepBubble(update.groupId)) manager.cancel(tag, update.groupId)
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
                    if (keepBubble(update.groupId)) {
                        logger.d { "Skupina ${update.groupId} zůstala prázdná, ale je z ní bublina — jen ji schovávám z lišty." }
                    } else {
                        manager.cancel(tag, update.groupId)
                        logger.d { "Skupina ${update.groupId} zůstala prázdná, ruším notifikaci." }
                    }
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

        // S víc účty nese jméno konverzace i účet („Notifikator · VT2"). Podtext (`setSubText`)
        // konverzační notifikace nekreslí, ve skupině ani rozbalená, a jméno konverzace bere
        // Android 11+ ze zkratky (`publishShortcut`), ne z `conversationTitle` — ověřeno na
        // Pixelu 5 (Android 14). Do `conversationTitle` jde účet pro starší Android bez zkratek.
        val account = accountName()
        if (conversation.isLocked) return showLocked(groupId, conversation, silent, account)
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName(getString(Res.string.notification_self_name)).build())
            .setGroupConversation(isGroup)
            // V soukromém chatu je název chatu totéž co jméno odesílatele u každého řádku —
            // vypsat ho i do hlavičky by ho zdvojilo. Účet se tam ale vejít musí.
            .setConversationTitle(
                when {
                    account != null -> shortcutLabel(conversation.title, account)
                    isGroup -> conversation.title
                    else -> null
                },
            )

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

        val built = NotificationCompat.Builder(context, soundChannelId(context, slot, conversation.channel, conversation.sound, account))
            .setSmallIcon(R.drawable.ic_notification)
            // Velká ikona je záloha. U `MessagingStyle` si ji Android bere ze ZKRATKY (viz
            // `publishShortcut`) a tuhle ignoruje — uplatní se, jen když se zkratku nepodaří
            // publikovat.
            .setLargeIcon(avatarBitmap(conversation.avatarPath))
            // Bez zkratky není notifikace „konverzace": Android pak do velkého kruhu dosadí
            // ikonu aplikace a notifikace skončí mimo sekci konverzací.
            .setShortcutId(publishShortcut(conversation, account))
            .setStyle(style)
            // Bublina (Android 11+): konverzace v plovoucím okně (`BubbleShell`). Jestli se
            // nabídne, rozhoduje uživatel v systému (u aplikace / konverzace); aplikace ji jen
            // umožní. Sama se nerozbalí a notifikaci nepotlačí — bublina je volba, ne výchozí stav.
            .setBubbleMetadata(bubbleMetadata(groupId, conversation))
            // Podle kategorie se řídí výjimka „zprávy" v režimu Nerušit; čtou ji i aplikace,
            // které notifikace třídí (hodinky, LED).
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            // Systém si titulek a text doplní z `MessagingStyle`, ticker ne — a starší čtenáři
            // notifikací berou text právě odsud.
            .setTicker(conversation.lines.lastOrNull()?.let { "${it.sender ?: conversation.title}: ${it.text}" })
            .setSubText(account)
            .setSilent(silent)
            .setAutoCancel(true)
            .apply {
                // Zmeškaný hovor: zavolat zpátky je to, co uživatel nejspíš chce, proto první.
                // Řídí se nejnovějším zmeškaným hovorem v notifikaci, i když po něm přišla
                // zpráva („zavolej mi") — dokud ho TDLib neodebere, platí.
                val callBack = conversation.lines.lastOrNull { it.callBack != null }?.callBack?.takeIf { callsSupported }
                callBack?.let { callBackAction(groupId, conversation.chatId, it)?.let(::addAction) }
                if (conversation.canReply) {
                    // Ve skupině odpověď cituje nejnovější zprávu z notifikace, jako oficiální
                    // klient: bez citace by nebylo poznat, komu z více lidí se odpovídá.
                    // V soukromém chatu je to jasné a citace by byla šum.
                    val quoted = if (isGroup) conversation.lines.lastOrNull { !it.isOwn && it.messageId != null }?.messageId else null
                    replyAction(groupId, conversation.chatId, quoted, conversation.topicId)?.let(::addAction)
                }
                // Pořadí akcí: (zavolat zpět,) odpověď, přečtení, ztlumení — od nejčastější.
                // Android jich ukáže nejvýš tři, víc jich tedy být nesmí: se „Zavolat zpět"
                // ztlumení odpadne (zmeškaný hovor je soukromý chat, ztlumit jde v aplikaci).
                addAction(
                    markReadAction(
                        groupId = groupId,
                        chatId = conversation.chatId,
                        messageId = conversation.lines.lastOrNull { !it.isOwn && it.messageId != null }?.messageId,
                    ),
                )
                if (conversation.canMute && callBack == null) addAction(muteAction(groupId, conversation.chatId))
            }
            .setContentIntent(
                NotificationIntents.openChat(
                    context = context,
                    slot = slot,
                    notificationId = groupId,
                    chatId = conversation.chatId,
                    topicId = conversation.topicId,
                ),
            )
            // Odhrnutí řekne TDLib i presenteru, že notifikace je pryč ([dismissed]). Jinak se
            // odhrnuté zprávy s další zprávou téhož chatu vrátily do notifikace.
            .setDeleteIntent(
                NotificationIntents.dismiss(
                    context = context,
                    slot = slot,
                    groupId = groupId,
                    chatId = conversation.chatId,
                    maxNotificationId = dismissUpTo(conversation.lines.map { it.notificationId }),
                ),
            )
            .build()

        logger.d { "Zobrazuji $groupId: ${conversation.lines.size} zpráv z ${conversation.chatId}." }
        manager.notify(tag, groupId, built)
    }

    /**
     * Notifikace ze zamčeného chatu: jen „Zamčený chat — Nová zpráva" (texty začernil už `core`).
     * Bez `MessagingStyle` a zkratky — z nich systém skládá konverzaci se jménem a fotkou,
     * nabízí ji v Direct Share a Android Auto by ji přečetl nahlas. Bez bubliny a bez akcí
     * (Odpovědět i Přečteno by šlo bez PINu). Ťuknutí otevře chat přes bránu s PINem.
     */
    private suspend fun showLocked(groupId: Int, conversation: Conversation, silent: Boolean, account: String?) {
        val last = conversation.lines.lastOrNull() ?: return
        val built = NotificationCompat.Builder(context, soundChannelId(context, slot, conversation.channel, conversation.sound, account))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(conversation.title)
            .setContentText(last.text)
            .setNumber(conversation.lines.size)
            .setSubText(account)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            // Na zamykací obrazovce se neukáže nic víc než na liště; jméno chatu tam stejně není.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setSilent(silent)
            .setAutoCancel(true)
            .setContentIntent(
                NotificationIntents.openChat(
                    context = context,
                    slot = slot,
                    notificationId = groupId,
                    chatId = conversation.chatId,
                    topicId = null,
                ),
            )
            .setDeleteIntent(
                NotificationIntents.dismiss(
                    context = context,
                    slot = slot,
                    groupId = groupId,
                    chatId = conversation.chatId,
                    maxNotificationId = dismissUpTo(conversation.lines.map { it.notificationId }),
                ),
            )
            .build()
        manager.notify(tag, groupId, built)
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
        if (callBusy()) {
            journal?.record("Ringing notification skipped: a call is running in another account", call.callId)
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        if (callsSupported) {
            showAnswerableCall(call)
            return
        }
        val name = call.callerName.ifBlank { getString(Res.string.notification_call_unknown) }
        val built = NotificationCompat.Builder(context, CALLS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(avatarBitmap(call.avatarPath))
            .setContentTitle(name)
            .setContentText(getString(if (call.isVideo) Res.string.notification_call_video else Res.string.notification_call_voice))
            .setSubText(accountName())
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    getString(if (call.isVideo) Res.string.notification_call_video else Res.string.notification_call_voice) +
                        "\n" + getString(Res.string.notification_call_answer_elsewhere),
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
                    getString(Res.string.notification_call_decline),
                    NotificationIntents.declineCall(context, slot, call.groupId, call.chatId, call.callId, call.isVideo),
                )
                    .setShowsUserInterface(false)
                    .build(),
            )
            .addAction(declineWithMessageAction(call))
            .setContentIntent(NotificationIntents.openChat(context = context, slot = slot, notificationId = call.groupId, chatId = call.chatId))
            .build()
        logger.d { "Zobrazuji příchozí hovor ${call.callId} (${if (call.isVideo) "video" else "hlas"})." }
        manager.notify(tag, call.groupId, built)
    }

    /**
     * Příchozí hovor, který Plume umí přijmout: `CallStyle` s „Přijmout" a „Odmítnout" od
     * systému a celoobrazovková výzva (na zamčeném telefonu otevře aplikaci rovnou na hovoru).
     *
     * - **`CallStyle` chce službu v popředí, nebo celoobrazovkový intent** — služba při
     *   zvonění neběží (spouští se až přijetím), takže intent je povinný, ne ozdoba.
     * - **„Přijmout" otevírá aplikaci** ([NotificationIntents.answerCall]), přijetí pak zařídí
     *   shell po dotazu na mikrofon. Broadcast by aplikaci do popředí nedostal a bez popředí
     *   Android službě hovoru mikrofon nedá.
     * - **Celoobrazovková výzva se ukáže jen se zamčeným nebo zhasnutým displejem**; jinak
     *   systém dá místo ní plovoucí notifikaci. Od Androidu 14 ji smí jen aplikace s oprávněním
     *   `USE_FULL_SCREEN_INTENT` (Play ho dá aplikacím na hovory); bez něj zbude plovoucí.
     */
    private suspend fun showAnswerableCall(call: IncomingCall) {
        val name = call.callerName.ifBlank { getString(Res.string.notification_call_unknown) }
        val person = Person.Builder()
            .setName(name)
            .setIcon(avatarBitmap(call.avatarPath)?.let { IconCompat.createWithBitmap(it) })
            .setImportant(true)
            .build()
        val decline = NotificationIntents.declineCall(context, slot, call.groupId, call.chatId, call.callId, call.isVideo)
        val answer = NotificationIntents.answerCall(context, call.groupId, call.callId) ?: return
        // Tichý kanál: zvoní `CallRinger` tónem ve smyčce, zvuk notifikace by ho zdvojil.
        val built = NotificationCompat.Builder(context, INCOMING_CALLS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, decline, answer).setIsVideo(call.isVideo))
            // `CallStyle` k Odmítnout a Přijmout ukáže ještě jednu vlastní akci.
            .addAction(declineWithMessageAction(call))
            .setContentText(getString(if (call.isVideo) Res.string.notification_call_video else Res.string.notification_call_voice))
            .setSubText(accountName())
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setTimeoutAfter(CALL_TIMEOUT_MS)
            .setFullScreenIntent(NotificationIntents.showCall(context), true)
            .setContentIntent(NotificationIntents.showCall(context))
            .build()
        logger.d { "Zobrazuji příchozí hovor ${call.callId}, přijmout jde v Plume." }
        val fullScreen = manager.canUseFullScreenIntent()
        journal?.record("Ringing notification${if (fullScreen) "" else " WITHOUT full-screen intent (not allowed)"}", call.callId)
        manager.notify(tag, call.groupId, built)
    }

    /**
     * Akce „Odmítnout se zprávou" u příchozího hovoru: pole na text a hotové odpovědi jako volby
     * (`setChoices`, systém je ukáže jako čipy nad klávesnicí). Hovor odmítne a větu pošle
     * volajícímu bez otevření aplikace; notifikaci pak stáhne TDLib s koncem hovoru.
     */
    private suspend fun declineWithMessageAction(call: IncomingCall): NotificationCompat.Action {
        val choices = CallQuickReplies.map { getString(it) }.toTypedArray<CharSequence>()
        val input = RemoteInput.Builder(NotificationIntents.KEY_REPLY_TEXT)
            .setLabel(getString(Res.string.call_decline_with_message))
            .setChoices(choices)
            .build()
        return NotificationCompat.Action.Builder(
            IconCompat.createWithResource(context, R.drawable.ic_notification_reply),
            // Krátce, jako tlačítko na obrazovce hovoru: „Odmítnout se zprávou" se v řadě
            // tlačítek `CallStyle` vedle Odmítnout a Přijmout ořízlo na „Odmít…" (emulátor,
            // Android 16). Celá věta zůstává jako nápověda pole.
            getString(Res.string.call_message),
            NotificationIntents.declineCallWithMessage(context, slot, call.groupId, call.chatId, call.callId, call.isVideo),
        )
            .addRemoteInput(input)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .build()
    }

    /**
     * Stáhne notifikaci hovoru po „Odmítnout se zprávou".
     *
     * **Android 15+ drží notifikaci po přímé odpovědi naživu** (`FLAG_LIFETIME_EXTENDED_BY_DIRECT_REPLY`)
     * a `cancel` z aplikace ji nezruší, systém ji jen znovu vyvěsí (`ONLY_ALERT_ONCE`): TDLib
     * hovor po odmítnutí ukončil, presenter notifikaci zrušil ([remove]) a v liště přesto zůstala
     * s točícím se kolečkem a nešla smáznout (emulátor, Android 16). Příznak shodí až překreslení
     * z aplikace — u zpráv to dělá řádek s odpovědí ([showReply]). Hovor nemá co ukázat, takže
     * se vyvěsí jeho tichá kopie, počká se, až ji systém ukazuje bez příznaku, a pak se zruší.
     *
     * - **Kopie, ne holá notifikace.** Holou (bez `CallStyle`) systém tiše zahodil a v liště
     *   zůstala původní; kopie se stylem hovoru projde.
     * - **Napřed se čeká, až příznak System UI nastaví.** Přijímač akce je rychlejší a dřívější
     *   překreslení by nic nezměnilo.
     * - **`notify` i `cancel` jen zařadí do fronty systému**, proto se čeká na zveřejnění kopie
     *   a pak ještě [REPLY_SETTLE], než System UI zapomene rozepsanou odpověď.
     *
     * Pod Androidem 15 příznak není a nic se nedělá.
     */
    suspend fun dismissRepliedCall(groupId: Int) = withContext(dispatcher) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return@withContext
        val extended = waitForCallNotification(groupId) { it.flags and FLAG_LIFETIME_EXTENDED_BY_DIRECT_REPLY != 0 }
        logger.d { "Notifikace hovoru po odpovědi: prodloužená=$extended." }
        if (extended != true) return@withContext
        // Víc pokusů: konec hovoru od TDLib zruší notifikaci po svém ([remove]) a když to padne
        // mezi kopii a zrušení, systém ji vyvěsí znovu s příznakem (ověřeno na emulátoru).
        // Pořadí obou zrušení řídit nejde, tak se úklid opakuje, dokud příznak v liště je.
        repeat(REPLY_DISMISS_ATTEMPTS) {
            val existing = manager.activeNotifications.firstOrNull { it.id == groupId && it.tag == tag }?.notification
                ?: return@withContext
            if (existing.flags and FLAG_LIFETIME_EXTENDED_BY_DIRECT_REPLY == 0) {
                manager.cancel(tag, groupId)
                return@withContext
            }
            manager.notify(tag, groupId, NotificationCompat.Builder(context, existing).setSilent(true).build())
            waitForCallNotification(groupId) { it.flags and FLAG_LIFETIME_EXTENDED_BY_DIRECT_REPLY == 0 }
            // System UI drží u notifikace stav rozepsané odpovědi a zruší ho až po překreslení;
            // zrušení hned po zveřejnění kopie systém obešel stejně jako výš.
            delay(REPLY_SETTLE)
            manager.cancel(tag, groupId)
            delay(REPLY_SETTLE)
        }
        logger.w { "Notifikaci hovoru $groupId se po odpovědi nepodařilo stáhnout." }
    }

    /**
     * Počká nejvýš [REPLY_EXTENSION_WAIT], až notifikace [groupId] splní [condition]: `true`,
     * `false` když z lišty zmizela, `null` když čas vypršel.
     */
    private suspend fun waitForCallNotification(groupId: Int, condition: (Notification) -> Boolean): Boolean? =
        withTimeoutOrNull(REPLY_EXTENSION_WAIT) {
            var met = false
            while (!met) {
                val active = manager.activeNotifications.firstOrNull { it.id == groupId && it.tag == tag }
                    ?: return@withTimeoutOrNull false
                met = condition(active.notification)
                if (!met) delay(REPLY_EXTENSION_POLL)
            }
            true
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
        appendOwnLine(groupId, getString(Res.string.notification_reply_failed, text), silent = false, replacing = text)
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
        val existing = manager.activeNotifications.firstOrNull { it.id == groupId && it.tag == tag }?.notification ?: return
        val style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(existing) ?: return
        if (replacing != null) style.messages.removeAll { it.person == null && it.text?.toString() == replacing }
        style.addMessage(text, now, null as Person?)
        val rebuilt = NotificationCompat.Builder(context, existing)
            .setStyle(style)
            .setSilent(silent)
            .build()
        manager.notify(tag, groupId, rebuilt)
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
            slot = slot,
            groupId = groupId,
            chatId = chatId,
            replyToMessageId = replyToMessageId,
            topicId = topicId,
        )
        val label = getString(Res.string.notification_reply)
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
            getString(Res.string.notification_mark_read),
            NotificationIntents.markRead(context, slot = slot, groupId = groupId, chatId = chatId, messageId = messageId),
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()

    /**
     * Akce „Zavolat zpět" u zmeškaného hovoru. Otevírá aplikaci (`showsUserInterface`): hovor
     * chce dotaz na mikrofon a aplikaci v popředí, viz [NotificationIntents.callBack]. Sémantika
     * `CALL` pro hodinky a Android Auto.
     */
    private suspend fun callBackAction(groupId: Int, chatId: Long, callBack: CallBack): NotificationCompat.Action? {
        val intent = NotificationIntents.callBack(
            context,
            slot = slot,
            groupId = groupId,
            chatId = chatId,
            userId = callBack.userId,
            isVideo = callBack.isVideo,
        ) ?: return null
        return NotificationCompat.Action.Builder(
            IconCompat.createWithResource(context, R.drawable.ic_notification_call),
            getString(Res.string.call_back),
            intent,
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_CALL)
            .setShowsUserInterface(true)
            .build()
    }

    /**
     * Akce „Ztlumit na hodinu". Sémantika `MUTE` pro hodinky a Android Auto.
     */
    private suspend fun muteAction(groupId: Int, chatId: Long): NotificationCompat.Action =
        NotificationCompat.Action.Builder(
            IconCompat.createWithResource(context, R.drawable.ic_notification_mute),
            getString(Res.string.notification_mute_hour),
            NotificationIntents.mute(context, slot = slot, groupId = groupId, chatId = chatId),
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
        val existing = manager.activeNotifications.firstOrNull { it.id == groupId && it.tag == tag } ?: return emptyList()

        val style = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(existing.notification)
            ?: return emptyList()

        // Jméno u řádku je odesílatel jen ve skupině. V soukromém chatu nese osoba řádku název
        // chatu (viz `show`), a převzaté jako odesílatel by z konverzace udělalo skupinu:
        // „Pixel 5: Zmeškaný hovor" pod nadpisem „Pixel 5: Pixel 5".
        val isGroup = style.isGroupConversation
        return style.messages.map { message ->
            Line(
                notificationId = null,
                sender = message.person?.name?.toString()?.takeIf { isGroup },
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
    private fun publishShortcut(conversation: Conversation, account: String?): String? {
        // Tatáž zkratka slouží i sdílení (Direct Share), viz `conversationShortcut`. Z jejího
        // názvu bere Android jméno konverzace, takže v něm je i účet ([shortcutLabel]).
        val shortcut = conversationShortcut(
            context = context,
            slot = slot,
            chatId = conversation.chatId,
            title = shortcutLabel(conversation.title, account),
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
        /** Zvuk podle Telegramu; spolu s [channel] vybírá systémový kanál (`soundChannelId`). */
        var sound: NotificationSoundChoice = NotificationSoundChoice.Default,
        /** Téma nejnovější zprávy ve skupině s tématy; `null` = celý chat. */
        var topicId: Int? = null,
        /** Zamčený chat: notifikace bez jména a textu ([showLocked]). */
        var isLocked: Boolean = false,
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
        /** Zmeškaný hovor, kterému jde zavolat zpátky; po restartu procesu se nepřevezme. */
        val callBack: CallBack? = null,
    )

    private class RemovedLine(val groupId: Int, val messageId: Long, val removedAtMs: Long)

    private companion object {
        /** Kolik zpráv se v notifikaci drží. Android jich stejně vykreslí jen posledních pár. */
        const val MAX_LINES = 10

        /** Déle než minutu nikdo nevyzvání; notifikace hovoru pak zmizí i bez zrušení od TDLib. */
        const val CALL_TIMEOUT_MS = 90_000L

        /** Jak dlouho čekat, než System UI označí notifikaci po přímé odpovědi ([dismissRepliedCall]). */
        val REPLY_EXTENSION_WAIT = 2.seconds
        val REPLY_EXTENSION_POLL = 100.milliseconds

        /** Pauza mezi překreslením notifikace hovoru a jejím zrušením ([dismissRepliedCall]). */
        val REPLY_SETTLE = 1.seconds

        const val REPLY_DISMISS_ATTEMPTS = 3

        /**
         * `Notification.FLAG_LIFETIME_EXTENDED_BY_DIRECT_REPLY` (Android 15+). V SDK je skrytá
         * (`@TestApi`), v `dumpsys notification` se ukazuje jako `LIFETIME_EXTENDED_BY_DIRECT_REPLY`.
         */
        const val FLAG_LIFETIME_EXTENDED_BY_DIRECT_REPLY = 1 shl 16

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

/** Výška bubliny konverzace; systém ji smí zmenšit, když se nevejde. */
private const val BUBBLE_HEIGHT_DP = 600
