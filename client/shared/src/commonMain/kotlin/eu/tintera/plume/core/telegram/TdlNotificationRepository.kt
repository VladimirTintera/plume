package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.ui.UiText

import dev.g000sha256.tdl.dto.Message

import dev.g000sha256.tdl.dto.MessagePinMessage

import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.resources.saved_messages
import dev.g000sha256.tdl.dto.ChatTypePrivate
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.NotificationTypeNewCall
import dev.g000sha256.tdl.dto.NotificationGroupTypeCalls
import dev.g000sha256.tdl.dto.Call
import dev.g000sha256.tdl.dto.CallStateError
import dev.g000sha256.tdl.dto.CallStateDiscarded
import dev.g000sha256.tdl.dto.CallStatePending
import dev.g000sha256.tdl.dto.DeviceToken
import dev.g000sha256.tdl.dto.DeviceTokenApplePush
import dev.g000sha256.tdl.dto.DeviceTokenFirebaseCloudMessaging
import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageSender
import dev.g000sha256.tdl.dto.MessageSenderChat
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.Notification
import dev.g000sha256.tdl.dto.NotificationGroupTypeMentions
import dev.g000sha256.tdl.dto.NotificationGroupTypeMessages
import dev.g000sha256.tdl.dto.MessageTopicForum
import dev.g000sha256.tdl.dto.NotificationTypeNewMessage
import dev.g000sha256.tdl.dto.NotificationTypeNewPushMessage
import dev.g000sha256.tdl.dto.NotificationTypeNewSecretChat
import dev.g000sha256.tdl.dto.PushMessageContentPhoto
import dev.g000sha256.tdl.dto.UpdateNotificationGroup
import dev.g000sha256.tdl.toResult
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.notification_new_secret_chat
import eu.tintera.plume.resources.notification_new_message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Notifikace nad TDLib. */
class TdlNotificationRepository(
    private val holder: TelegramClientHolder,
    scope: CoroutineScope,
) : NotificationRepository {

    private val logger = Logger.withTag("Notifications")

    private val client: TdlClient get() = holder.client

    /**
     * Má TDLib notifikace, které ještě nevydal (odložené, nebo čekající na zprávu ze serveru)?
     *
     * Odebírá se od vzniku repository: update chodí jen na ZMĚNU, takže kdo by se přihlásil až
     * s příchodem pushe, aktuální hodnotu by se nedozvěděl.
     */
    private val tdlibPending = MutableStateFlow(false)

    /**
     * Kolik notifikačních updatů je rozpracovaných: přijaté od TDLib a ještě nevyřízené
     * presenterem. Viz [awaitDelivered].
     */
    private val inFlight = MutableStateFlow(0)

    /**
     * Hovory podle id — notifikace hovoru (`NotificationTypeNewCall`) nese jen id, video a stav
     * jsou na `Call`. TDLib pošle `UpdateCall` dřív než notifikaci, takže stačí si ho pamatovat.
     */
    private val calls = MutableStateFlow<Map<Int, Call>>(emptyMap())

    /**
     * Kolik `UpdateCall` přišlo od vzniku. Push hovoru si zapamatuje stav před sebou
     * ([callPushMark]) a [awaitIncomingCall] čeká na první další — i na zrušený hovor, ten
     * znamená, že TDLib je se serverem srovnaný a zmeškaný hovor oznámí sám.
     */
    private val callUpdateCount = MutableStateFlow(0L)

    private var callPushMark = 0L

    init {
        holder.updates { callUpdates }
            .onEach { update ->
                callUpdateCount.update { it + 1 }
                val call = update.call
                // Skončený hovor už notifikaci nedostane — mapa by jinak rostla s každým hovorem.
                val ended = call.state is CallStateDiscarded || call.state is CallStateError
                calls.update { if (ended) it - call.id else it + (call.id to call) }
            }
            .launchIn(scope)
        holder.updates { havePendingNotificationsUpdates }
            .onEach {
                logger.d { "Nevydané notifikace: odložené=${it.haveDelayedNotifications}, nepřijaté=${it.haveUnreceivedNotifications}" }
                tdlibPending.value = it.haveDelayedNotifications || it.haveUnreceivedNotifications
            }
            .launchIn(scope)
    }

    override val updates: Flow<NotificationUpdate> = merge(
        // Po odhlášení nesmí na liště zůstat zprávy účtu, který už v aplikaci není.
        holder.replaced.map { NotificationUpdate.Cleared },
        messageNotifications(),
    )
        // `emit` v `transform` se vrátí až ve chvíli, kdy odběratel (presenter) update zpracoval —
        // mezi nimi už žádný buffer není. Teprve tady je notifikace opravdu na liště.
        .transform { update ->
            emit(update)
            if (update !is NotificationUpdate.Cleared) inFlight.update { it - 1 }
        }

    private fun messageNotifications(): Flow<NotificationUpdate> = flow {
        holder.updates { notificationGroupUpdates }.collect { update ->
            // Rozpracovaný je update od příchodu: skládání notifikace čte chat, odesílatele
            // a profilovku, a to trvá.
            inFlight.update { it + 1 }
            try {
                groupUpdate(update)
            } finally {
                inFlight.update { it - 1 }
            }
        }
    }

    /** Každý vydaný update se počítá do [inFlight]; odečte se až po zpracování presenterem. */
    private suspend fun FlowCollector<NotificationUpdate>.emitTracked(
        update: NotificationUpdate,
    ) {
        inFlight.update { it + 1 }
        emit(update)
    }

    private suspend fun FlowCollector<NotificationUpdate>.groupUpdate(
        update: UpdateNotificationGroup,
    ) {
        // Jeden update může naráz přidávat i odebírat; pořadí je podstatné, aby po přečtení
        // jinde nezůstala viset stará notifikace, kterou právě nahradila novější.
        val removed = update.removedNotificationIds.toList()
        if (removed.isNotEmpty()) {
            emitTracked(NotificationUpdate.Removed(update.notificationGroupId, removed))
        }

        val added = update.addedNotifications.filterNotNull()
        if (added.isEmpty()) return

        // Hovory mají vlastní skupinu (`NotificationGroupTypeCalls`) a vlastní podobu notifikace.
        if (update.type is NotificationGroupTypeCalls) {
            val chat = client.getChat(update.chatId).toResult().getOrNull()
            added.mapNotNull { it.type as? NotificationTypeNewCall }.forEach { type ->
                emitTracked(
                    NotificationUpdate.Call(
                        IncomingCall(
                            groupId = update.notificationGroupId,
                            callId = type.callId,
                            chatId = update.chatId,
                            callerName = chat?.title.orEmpty(),
                            avatarPath = downloadedPath(chat?.photo?.small),
                            isVideo = calls.value[type.callId]?.isVideo == true,
                        ),
                    ),
                )
            }
            return
        }

        // Chat se načte JEDNOU — je z něj potřeba název i fotka, a dvě volání kvůli jedné
        // notifikaci by byla zbytečná.
        val chat = client.getChat(update.chatId).toResult().getOrNull()
        // Uložené zprávy (připomínka naplánovaná v nich) nesou jméno vlastního účtu; notifikace
        // i zkratka konverzace je mají ukázat pod vlastním názvem a se záložkou, jako aplikace.
        val isSavedMessages = (chat?.type as? ChatTypePrivate)?.userId
            ?.let { it == client.getMe().toResult().getOrNull()?.id } == true
        // Chat, který lokální databáze nezná (první zpráva od nového kontaktu, spojení mrtvé),
        // se pojmenuje podle odesílatele z pushe — notifikace bez jména by nikomu nic neřekla.
        val chatTitle = (if (isSavedMessages) readString(Res.string.saved_messages) else chat?.title)
            ?: added.firstNotNullOfOrNull { (it.type as? NotificationTypeNewPushMessage)?.senderName }
            ?: ""

        // Odpověď z notifikace jen tam, kam se smí psát (kanál bez práva publikovat, omezená
        // skupina) — akce, která skončí chybou, je horší než žádná. Neznámý chat psaní
        // neblokuje, stejně jako vstupní pole v konverzaci.
        val canReply = chat == null || writeAccess(
            isChannel = (chat.type as? ChatTypeSupergroup)?.isChannel == true,
            status = client.ownMemberStatus(chat),
            membersCanSend = chat.permissions.canSendBasicMessages,
        ) == WriteAccess.Allowed

        // Ztlumit má smysl jen u běžných zpráv. Zmínky a odpovědi (`Mentions`) chodí i ze
        // ztlumeného chatu — ztlumení na hodinu by mu po hodině zvuk VRÁTILO, i když ho měl
        // uživatel ztlumený natrvalo nebo výchozím nastavením.
        val canMute = update.type is NotificationGroupTypeMessages

        // Druh chatu podle toho, čím se notifikace řídí (`notificationSettingsChatId`), ne podle
        // chatu samotného — tak si ho vybírá i TDLib, když rozhoduje o ztlumení a zvuku.
        val settingsChat = if (update.notificationSettingsChatId == update.chatId) chat
        else client.getChat(update.notificationSettingsChatId).toResult().getOrNull() ?: chat
        val channel = notificationChannelFor(
            isMention = update.type is NotificationGroupTypeMentions,
            kind = settingsChat?.type?.chatKind() ?: ChatKind.Private,
        )
        val sound = soundOf(update.notificationSoundId)

        val notifications = added.mapNotNull { notification ->
            val content = notification.content() ?: return@mapNotNull null

            ChatNotification(
                groupId = update.notificationGroupId,
                notificationId = notification.id,
                messageId = content.messageId,
                chatId = update.chatId,
                chatTitle = chatTitle,
                senderName = content.senderName?.takeIf { it != chatTitle },
                text = content.text,
                isSilent = notification.isSilent,
                timestamp = notification.date,
                // Ve skupině patří k řádku profilovka toho, kdo psal; v soukromém chatu
                // je to totéž jako fotka chatu. Fotka chatu je záloha pro kanály a boty,
                // kde odesílatel vlastní profilovku nemá.
                avatarPath = downloadedPath(content.senderPhoto ?: chat?.photo?.small),
                chatAvatarPath = if (isSavedMessages) null else downloadedPath(chat?.photo?.small),
                isSavedMessages = isSavedMessages,
                imagePath = downloadedPath(content.image, IMAGE_TIMEOUT),
                imageCaption = content.imageCaption,
                canReply = canReply,
                canMute = canMute,
                channel = channel,
                sound = sound,
                topicId = content.topicId,
            )
        }

        if (notifications.isNotEmpty()) emitTracked(NotificationUpdate.Added(notifications))
    }

    /**
     * [notificationText], jen připnutí řekne CO se připnulo („Připnuto: „Sraz v 8""). Jméno
     * toho, kdo připnul, nese řádek notifikace, proto věta bez něj. Text se bere z lokální
     * databáze; zpráva, která v ní není, dostane „Připnuta zpráva" jako dřív.
     */
    private suspend fun Message.detailedNotificationText(): UiText {
        val pin = content as? MessagePinMessage ?: return notificationText()
        val pinnedText = pin.messageId.takeIf { it != 0L }?.let { client.localMessageText(chatId, it) }
        return pinServiceText(actor = null, pinnedText = pinnedText)
    }

    /**
     * Odesílatel a text notifikace, nebo `null` u typu, který nezobrazujeme.
     *
     * **Dva typy, ne jeden.** `NotificationTypeNewMessage` vzniká, když TDLib zprávu má.
     * Z pushe, ke kterému zprávu ještě nemá — a to je v pozadí skoro vždycky, protože zmrazený
     * proces spojení nedrží — vydá `NotificationTypeNewPushMessage` s obsahem rozšifrovaným
     * přímo z pushe. Dokud se zobrazoval jen první typ, push dorazil, TDLib ho zpracoval
     * a notifikace se zahodila: z pozadí prakticky nechodily.
     */
    private suspend fun Notification.content(): NotificationContent? =
        when (val type = type) {
            is NotificationTypeNewMessage -> {
                val sender = resolveSender(type.message.senderId)
                val message = type.message
                // Fotka pod spoilerem, mizející nebo citlivá se do notifikace nedává: má být
                // vidět až na výslovné ťuknutí, ne na zamykací obrazovce. Citlivá ani tehdy,
                // když si ji uživatel v Telegramu povolil — zamykací obrazovku vidí i jiní.
                val shownPhoto = (message.content as? MessagePhoto)
                    ?.takeIf {
                        type.showPreview && !it.hasSpoiler && !it.isSecret && message.selfDestructType == null &&
                            message.restrictionInfo?.hasSensitiveContent != true && message.restrictionText == null
                    }
                NotificationContent(
                    messageId = message.id,
                    senderName = sender?.name,
                    senderPhoto = sender?.photo,
                    // `showPreview = false` znamená, že si uživatel nepřeje vidět obsah
                    // na zamykací obrazovce. Musíme to respektovat — i u obrázku.
                    text = if (type.showPreview) message.detailedNotificationText().resolveNow() else readString(Res.string.notification_new_message),
                    image = shownPhoto?.photo?.notificationImage(),
                    imageCaption = shownPhoto?.caption?.text?.takeIf { it.isNotBlank() },
                    topicId = (message.topicId as? MessageTopicForum)?.forumTopicId,
                )
            }

            is NotificationTypeNewPushMessage -> {
                // Jméno přišlo v pushi; dotaz na uživatele jde jen do lokální databáze, takže
                // funguje i bez spojení — a když uživatele nezná, zůstane jméno z pushe.
                val sender = resolveSender(type.senderId)
                val pushPhoto = (type.content as? PushMessageContentPhoto)?.takeIf { !it.isSecret }
                NotificationContent(
                    messageId = type.messageId,
                    senderName = type.senderName.ifBlank { null } ?: sender?.name,
                    senderPhoto = sender?.photo,
                    text = type.content.previewText().resolveNow(),
                    // Push nese i popis fotky se soubory; stáhnout ji ale jde až po připojení.
                    // Nestihne-li to, obrázek doplní skutečná notifikace, která dočasnou nahradí.
                    image = pushPhoto?.photo?.notificationImage(),
                    imageCaption = pushPhoto?.caption?.takeIf { it.isNotBlank() },
                )
            }

            // Protějšek založil tajný chat. Zpráva k tomu žádná není; bez oznámení by se o chatu
            // dozvěděl, až by mu napsal.
            is NotificationTypeNewSecretChat -> NotificationContent(
                messageId = null,
                senderName = null,
                senderPhoto = null,
                text = readString(Res.string.notification_new_secret_chat),
            )

            // Hovory jdou vlastní cestou (`NotificationUpdate.Call`, výš).
            else -> null
        }

    override suspend fun awaitDelivered(timeout: Duration): Boolean =
        withTimeoutOrNull(timeout) {
            /*
             * Klid musí vydržet SOUVISLE [QUIET]; každá změna odpočet restartuje.
             *
             * Nestačí první okamžik, kdy nic nečeká. Z pushe vydá TDLib dočasnou notifikaci
             * hned, jenže o pár set milisekund později se připojí, dostane skutečnou zprávu,
             * dočasnou notifikaci zruší a novou si odloží. Ověřeno na zařízení: služba skončila
             * v mezeře mezi tím, proces zamrzl a na liště nezůstalo nic.
             */
            combine(tdlibPending, inFlight) { pending, running -> !pending && running == 0 }
                .transformLatest { idle ->
                    if (idle) {
                        delay(QUIET)
                        emit(Unit)
                    }
                }
                .first()
            true
        } ?: false

    override suspend fun declineCall(callId: Int, isVideo: Boolean): Result<Unit> =
        client.discardCall(
            callId = callId,
            isDisconnected = false,
            inviteLink = "",
            duration = 0,
            isVideo = isVideo,
            connectionId = 0L,
        )
            .toResult()
            .map { }
            .onFailure { logger.w(it) { "Hovor $callId se nepodařilo odmítnout." } }

    override suspend fun registerDevice(token: PushToken): Result<Unit> {
        val deviceToken: DeviceToken = when (token) {
            is PushToken.Firebase ->
                DeviceTokenFirebaseCloudMessaging(token = token.token, encrypt = token.encrypt)

            is PushToken.ApplePush ->
                DeviceTokenApplePush(deviceToken = token.token, isAppSandbox = token.sandbox)
        }

        return client.registerDevice(
            deviceToken = deviceToken,
            // Ostatní účty na zařízení (multi-account) zatím nepodporujeme.
            otherUserIds = LongArray(0),
        )
            .toResult()
            .map { }
            .onFailure { logger.e(it) { "registerDevice selhalo." } }
    }

    override suspend fun processPush(payload: String): Result<PushKind> {
        val mark = callUpdateCount.value
        return client.processPushNotification(payload = payload)
            .toResult()
            .map { PushKind.Notification }
            .recoverCatching { failure ->
                // Push hovoru TDLib odmítne vždycky (hlášku má přímo v knihovně): hovor se dá
                // zjistit jen od serveru. Chyba to není — jen se na hovor musí počkat.
                if (failure.message != CALL_PUSH_UNSUPPORTED) throw failure
                callPushMark = mark
                PushKind.Call
            }
            .onFailure { failure ->
                // Push o novém tajném chatu TDLib zpracovat neumí (hlášku má přímo v knihovně);
                // chat dorazí updatem, až se připojí, a oznámí ho `NotificationTypeNewSecretChat`.
                // Není to chyba, kvůli které by se mělo hlásit do Crashlytics.
                if (failure.message == SECRET_CHAT_PUSH_UNSUPPORTED) {
                    logger.i { "Push o novém tajném chatu TDLib nezpracuje, chat dorazí updatem." }
                } else {
                    logger.e(failure) { "processPushNotification selhalo." }
                }
            }
    }

    override suspend fun awaitIncomingCall(timeout: Duration): Boolean {
        val mark = callPushMark
        // Běžící aplikace dostane hovor spojením dřív než push — pak už zvoní a čekat není na co.
        val arrived = combine(callUpdateCount, calls) { count, known ->
            count > mark || known.values.any { !it.isOutgoing && it.state is CallStatePending }
        }
        // Napřed bez čekání: `withTimeoutOrNull` s nulovým limitem blok vůbec nespustí.
        if (arrived.first()) return true
        return withTimeoutOrNull(timeout) { arrived.first { it } } ?: false
    }

    /**
     * Odesílatel zprávy.
     *
     * Jméno i profilovka se berou z jednoho dotazu. Volající si pak rozhodne, co s nimi:
     * jméno se v soukromém chatu zahazuje (opakovalo by název chatu), profilovka ne — tam
     * naopak je tou správnou ikonou notifikace.
     */
    private suspend fun resolveSender(senderId: MessageSender): Sender? {
        val userId = when (val sender = senderId) {
            is MessageSenderUser -> sender.userId
            is MessageSenderChat -> sender.chatId
            else -> return null
        }

        val user = client.getUser(userId).toResult().getOrNull() ?: return null
        val name = listOf(user.firstName, user.lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        return Sender(name = name.takeIf { it.isNotBlank() }, photo = user.profilePhoto?.small)
    }

    /**
     * Cesta k souboru na disku, v případě potřeby ho nejdřív stáhne.
     *
     * **Tady se stahuje synchronně**, na rozdíl od obrázků v UI. Notifikace se skládá naráz
     * a ikonu na ni nejde doplnit dodatečně — bez čekání by prostě nebyla. Profilovka je
     * pár kilobajtů a většinou už na disku je, protože ji ukazuje seznam chatů.
     */
    /**
     * Zvuk notifikace z `notificationSoundId`: 0 = bez zvuku, záporné = výchozí (TDLib ho tak
     * hlásí, když chat i rozsah jedou podle aplikace), jinak tón účtu. Tón se stáhne se stropem
     * jako obrázky — nestihne-li se, notifikace zazní výchozím zvukem, ne vůbec.
     */
    private suspend fun soundOf(id: Long): NotificationSoundChoice = when {
        id == NO_SOUND_ID -> NotificationSoundChoice.Silent
        id < 0 -> NotificationSoundChoice.Default
        else -> client.getSavedNotificationSound(notificationSoundId = id).toResult().getOrNull()
            ?.let { NotificationSoundChoice.Tone(id = id, title = it.title, path = downloadedPath(it.sound, IMAGE_TIMEOUT)) }
            ?: NotificationSoundChoice.Default
    }

    private suspend fun downloadedPath(file: File?, timeout: Duration = AVATAR_TIMEOUT): String? {
        if (file == null) return null
        if (file.local.isDownloadingCompleted && file.local.path.isNotEmpty()) return file.local.path

        // Se stropem: z pozadí na stažení může čekat, dokud se TDLib nepřipojí, a notifikace
        // bez obrázku je pořád lepší než notifikace, která nestihne okno pushe.
        val downloaded = withTimeoutOrNull(timeout) {
            client.downloadFile(
                fileId = file.id,
                priority = AVATAR_PRIORITY,
                offset = 0,
                limit = 0,
                synchronous = true,
            )
                .toResult()
                .getOrNull()
        }
            ?: return null

        return downloaded.local.path.takeIf {
            downloaded.local.isDownloadingCompleted && it.isNotEmpty()
        }
    }

    private class Sender(val name: String?, val photo: File?)

    private class NotificationContent(
        val messageId: Long?,
        val senderName: String?,
        val senderPhoto: File?,
        val text: String,
        /** Fotka ze zprávy, nebo `null`, když žádná není nebo se ukázat nesmí. */
        val image: File? = null,
        val imageCaption: String? = null,
        /** Téma skupiny s tématy; z pushe ho neznáme. */
        val topicId: Int? = null,
    )

    private companion object {
        /** Hláška TDLib (ověřeno `strings` nad knihovnou) u pushe o novém tajném chatu. */
        const val SECRET_CHAT_PUSH_UNSUPPORTED = "New secret chat notification is not supported"

        /** Hláška TDLib (ověřeno `strings` nad knihovnou) u pushe o příchozím hovoru. */
        const val CALL_PUSH_UNSUPPORTED = "Phone call notification is not supported"

        /** Nejvyšší priorita: notifikace čeká, takže má smysl ji vyřídit dřív než zbytek fronty. */
        const val AVATAR_PRIORITY = 32

        /** Jak dlouho notifikace počká na profilovku, kterou TDLib nemá na disku. */
        val AVATAR_TIMEOUT = 2.seconds

        /**
         * Jak dlouho notifikace počká na fotku ze zprávy. Víc než u profilovky: fotka má desítky
         * kilobajtů a skoro nikdy na disku není — ale pořád se to musí vejít do okna pushe.
         */
        val IMAGE_TIMEOUT = 3.seconds

        /** Jak dlouho musí být v notifikacích klid, než se push považuje za vyřízený. */
        val QUIET = 2.seconds
    }
}
