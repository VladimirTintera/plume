package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * Notifikace o nových zprávách.
 *
 * **TDLib je vydává i bez push služby**, kdykoli proces běží a je připojený — push je jen doprava
 * pro případ, kdy aplikace neběží. Díky tomu tahle vrstva funguje sama o sobě; registrace u FCM
 * a APNs ([registerDevice]) je nezávislá nadstavba.
 *
 * Co se opravdu zobrazí, rozhoduje TDLib podle nastavení chatu (ztlumené chaty, náhledy) — klient
 * jen vykresluje. Proto se tu nic nefiltruje.
 */
interface NotificationRepository {

    val updates: Flow<NotificationUpdate>

    /**
     * Zaregistruje zařízení pro push.
     *
     * Musí se opakovat po každém startu aplikace (Telegram doporučuje i jednou za 24 hodin),
     * protože platformní token se může kdykoli změnit.
     */
    suspend fun registerDevice(token: PushToken): Result<Unit>

    /**
     * Předá TDLib zašifrovaný obsah pushe; ten ho rozšifruje a vydá [NotificationUpdate].
     *
     * Push o příchozím hovoru TDLib rozšifrovat neumí ([PushKind.Call]) — hovor se dozví až od
     * serveru, takže na něj musí příjem pushe počkat ([awaitIncomingCall]).
     */
    suspend fun processPush(payload: String): Result<PushKind>

    /**
     * Počká na `UpdateCall` po posledním pushi hovoru ([PushKind.Call]), nebo vrátí `true` hned,
     * když příchozí hovor už zvoní. `false`, když do [timeout] nepřišel.
     *
     * Bez toho se hovor na spící aplikaci ztratil: služba pushe skončila hned, systém proces
     * za pár vteřin zmrazil a připojit se k serveru, od kterého jediného se hovor dá zjistit,
     * nestihl. Volající se pak dozvěděl jen „Zmeškaný hovor".
     */
    suspend fun awaitIncomingCall(timeout: Duration): Boolean

    /**
     * Počká, až TDLib nemá žádné nevydané notifikace a všechny vydané jsou zpracované odběratelem
     * [updates]. Vrací `false`, když to do [timeout] nestihne.
     *
     * Na tohle musí čekat příjem pushe. Proces v pozadí systém zmrazí, jakmile služba pushe
     * skončí — a notifikace, kterou TDLib ještě neposlal nebo presenter ještě neposkládal, se
     * pak zobrazí až při dalším probuzení, tedy často vůbec.
     */
    suspend fun awaitDelivered(timeout: Duration): Boolean

    /**
     * Odmítne příchozí hovor (`discardCall`). Přijmout ho Plume neumí — hovory nemají přenos
     * zvuku —, ale odmítnout ano: volající to pozná hned a nečeká na vyzvánění do ztracena.
     */
    suspend fun declineCall(callId: Int, isVideo: Boolean): Result<Unit>

    /**
     * Uživatel notifikace skupiny [groupId] odhrnul (`removeNotificationGroup`). TDLib je jinak
     * drží jako aktivní, dokud se chat nepřečte, a dokumentace volání předepisuje právě pro
     * zavření uživatelem. Odeberou se notifikace po [maxNotificationId] včetně — novější, které
     * mezitím přibyly, zůstanou.
     */
    suspend fun dismissGroup(groupId: Int, maxNotificationId: Int): Result<Unit>
}

/**
 * Po které id notifikace se odhrnutá skupina odebere ([NotificationRepository.dismissGroup]):
 * nejvyšší id mezi řádky, které uživatel viděl. `null`, když žádný řádek id od TDLib nenese
 * (jen vlastní odpovědi a řádky převzaté po restartu) — pak TDLib nemáme co říct.
 */
fun dismissUpTo(notificationIds: List<Int?>): Int? = notificationIds.filterNotNull().maxOrNull()

/** Co push nesl, podle toho, jak s ním TDLib naložil. */
enum class PushKind {
    /** Rozšifrovaný; notifikaci vydá TDLib sám ([NotificationRepository.awaitDelivered]). */
    Notification,

    /** Příchozí hovor; ten přijde až spojením se serverem ([NotificationRepository.awaitIncomingCall]). */
    Call,
}

/** Platformní push token. */
sealed interface PushToken {

    /** Android. [encrypt] zapíná šifrování obsahu — Telegram pak posílá jen zašifrovaný blob. */
    data class Firebase(val token: String, val encrypt: Boolean = true) : PushToken

    /** iOS. [sandbox] musí odpovídat prostředí buildu, jinak APNs zprávy zahodí. */
    data class ApplePush(val token: String, val sandbox: Boolean) : PushToken
}

sealed interface NotificationUpdate {

    data class Added(val notifications: List<ChatNotification>) : NotificationUpdate

    /**
     * Notifikace, které se mají zrušit — typicky proto, že si uživatel zprávy přečetl jinde.
     * Bez tohohle by na zamykací obrazovce zůstaly viset i po přečtení na počítači.
     */
    data class Removed(val groupId: Int, val notificationIds: List<Int>) : NotificationUpdate

    /** Účet se odhlásil — pryč musí všechno, co o něm aplikace systému předala. */
    data object Cleared : NotificationUpdate

    /**
     * Příchozí hovor (`NotificationTypeNewCall`). Zruší ho [Removed] se stejným [IncomingCall.groupId],
     * jakmile hovor skončí (přijatý jinde, odmítnutý, zmeškaný) — to hlídá TDLib.
     */
    data class Call(val call: IncomingCall) : NotificationUpdate
}

/**
 * Příchozí hovor pro notifikaci: kdo volá a jestli videem. Kde Plume hovory umí (Android,
 * `NotificationPresenter(callsSupported)`), nabídne notifikace Přijmout i Odmítnout; jinde jen
 * ohlásí, kdo volá, a nabídne odmítnutí — kdo má jen Plume, se o hovoru jinak nedozví vůbec.
 */
data class IncomingCall(
    val groupId: Int,
    val callId: Int,
    /** Soukromý chat s volajícím; ťuknutí na notifikaci ho otevře. */
    val chatId: Long,
    val callerName: String,
    val avatarPath: String?,
    val isVideo: Boolean,
)

data class ChatNotification(
    val groupId: Int,
    val notificationId: Int,
    /**
     * Id zprávy, ke které notifikace patří. Jediné, co mají společné dočasná notifikace z pushe
     * a skutečná, která ji nahradí — id notifikace i text se liší (push u fotky předřazuje 🖼).
     * `null` u oznámení bez zprávy (nový tajný chat).
     */
    val messageId: Long?,
    val chatId: Long,
    val chatTitle: String,
    /** `null` v soukromém chatu, kde je odesílatel totožný s chatem. */
    val senderName: String?,
    val text: String,
    /** `true` = zobrazit bez zvuku; TDLib to rozhoduje podle nastavení chatu. */
    val isSilent: Boolean,
    /** Unixový čas v sekundách. Řadí řádky v notifikaci jednoho chatu. */
    val timestamp: Int,
    /**
     * Cesta k profilovce na disku, nebo `null` když ji nemá.
     *
     * Cesta, ne `fileId`: notifikace se skládá naráz a nemá kam čekat na stažení, takže se
     * soubor musí vyřídit dřív, než se sem hodnota dostane.
     */
    val avatarPath: String?,
    /**
     * Profilovka CHATU, pro velkou ikonu notifikace.
     *
     * Liší se od [avatarPath] ve skupině: u řádku patří profilovka toho, kdo psal, ale velká
     * ikona reprezentuje celou konverzaci, takže tam patří fotka skupiny.
     */
    val chatAvatarPath: String?,
    /** Uložené zprávy: zkratka konverzace se záložkou místo fotky vlastního účtu. */
    val isSavedMessages: Boolean = false,
    /**
     * Cesta k fotce ze zprávy, kterou notifikace ukáže u řádku. `null` u zprávy bez fotky, při
     * vypnutém náhledu, u spoileru a mizejících fotek — a když se nestihla stáhnout.
     */
    val imagePath: String? = null,
    /**
     * Popisek fotky, když nějaký má. Android u řádku s obrázkem text nezobrazí, takže popisek
     * jde do notifikace jako řádek navíc; [text] zůstává kvůli tickeru a čtečkám notifikací.
     */
    val imageCaption: String? = null,
    /** Nabídnout v notifikaci „Odpovědět"? Ne tam, kam uživatel psát nesmí. */
    val canReply: Boolean = true,
    /** Nabídnout „Ztlumit"? Jen u běžných zpráv, ne u zmínek, které projdou i ztlumeným chatem. */
    val canMute: Boolean = true,
    /** Systémový kanál, kterým notifikace půjde (Android). Viz [notificationChannelFor]. */
    val channel: NotificationChannelKind = NotificationChannelKind.Private,
    /**
     * Zvuk, který pro notifikaci určila TDLib (`notificationSoundId` — chat, téma, nebo rozsah).
     * Na Androidu vybírá kanál: výchozí, tichý, nebo kanál s tónem (viz `soundChannelId`).
     */
    val sound: NotificationSoundChoice = NotificationSoundChoice.Default,
    /**
     * Téma skupiny s tématy, ve kterém zpráva je; ťuknutí na notifikaci ho otevře. `null` mimo
     * fórum — a u notifikace z pushe: push téma nenese, takže ta otevře skupinu celou, dokud ji
     * nenahradí skutečná notifikace.
     */
    val topicId: Int? = null,
)

/**
 * Systémový kanál notifikace — na Androidu jde každý zvlášť ztišit nebo nastavit zvuk.
 *
 * Tři odpovídají rozsahům nastavení TDLib (soukromé chaty, skupiny, kanály), takže u každého
 * rozsahu v Nastavení vede odkaz „Zvuk a vibrace" do jeho kanálu. Zmínky mají vlastní: projdou
 * i ztlumeným chatem a v tiché skupině jsou to jediné, co má zvonit.
 */
enum class NotificationChannelKind { Private, Groups, Channels, Mentions }

/**
 * Id kanálu pro zvuk [soundId] pod výchozím kanálem [baseId]: výchozí zvuk (záporné id) = výchozí
 * kanál, bez zvuku `<base>_silent`, tón `<base>_tone_<id>`. Id musí být stabilní — stejný zvuk
 * se musí trefit do téhož kanálu, i s tím, co si v něm uživatel nastavil.
 */
fun soundChannelKey(baseId: String, soundId: Long): String = when {
    soundId < 0 -> baseId
    soundId == NO_SOUND_ID -> "${baseId}_silent"
    else -> "${baseId}_tone_$soundId"
}

/** Je kanál [id] kanálem se zvukem z Telegramu (ne výchozí)? Takové se po odhlášení mažou. */
fun isSoundChannel(id: String): Boolean = id.endsWith("_silent") || id.contains("_tone_")

/** Zvuk notifikace podle Telegramu. */
sealed interface NotificationSoundChoice {
    /** Výchozí zvuk aplikace — výchozí kanál, jak si ho uživatel nastavil. */
    data object Default : NotificationSoundChoice

    data object Silent : NotificationSoundChoice

    /** Tón účtu; [path] = soubor na disku, `null`, když se nestihl stáhnout (pak jako [Default]). */
    data class Tone(val id: Long, val title: String, val path: String?) : NotificationSoundChoice
}

/**
 * Kanál pro notifikaci. [isMention] = skupina notifikací `NotificationGroupTypeMentions`
 * (zmínky a odpovědi na mě); má přednost před druhem chatu.
 */
fun notificationChannelFor(isMention: Boolean, kind: ChatKind): NotificationChannelKind = when {
    isMention -> NotificationChannelKind.Mentions
    kind == ChatKind.Group -> NotificationChannelKind.Groups
    kind == ChatKind.Channel -> NotificationChannelKind.Channels
    else -> NotificationChannelKind.Private
}
