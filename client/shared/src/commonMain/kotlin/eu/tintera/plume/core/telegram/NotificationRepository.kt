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

    /** Předá TDLib zašifrovaný obsah pushe; ten ho rozšifruje a vydá [NotificationUpdate]. */
    suspend fun processPush(payload: String): Result<Unit>

    /**
     * Počká, až TDLib nemá žádné nevydané notifikace a všechny vydané jsou zpracované odběratelem
     * [updates]. Vrací `false`, když to do [timeout] nestihne.
     *
     * Na tohle musí čekat příjem pushe. Proces v pozadí systém zmrazí, jakmile služba pushe
     * skončí — a notifikace, kterou TDLib ještě neposlal nebo presenter ještě neposkládal, se
     * pak zobrazí až při dalším probuzení, tedy často vůbec.
     */
    suspend fun awaitDelivered(timeout: Duration): Boolean
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
}

data class ChatNotification(
    val groupId: Int,
    val notificationId: Int,
    /**
     * Id zprávy, ke které notifikace patří. Jediné, co mají společné dočasná notifikace z pushe
     * a skutečná, která ji nahradí — id notifikace i text se liší (push u fotky předřazuje 🖼).
     */
    val messageId: Long,
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
 * Kanál pro notifikaci. [isMention] = skupina notifikací `NotificationGroupTypeMentions`
 * (zmínky a odpovědi na mě); má přednost před druhem chatu.
 */
fun notificationChannelFor(isMention: Boolean, kind: ChatKind): NotificationChannelKind = when {
    isMention -> NotificationChannelKind.Mentions
    kind == ChatKind.Group -> NotificationChannelKind.Groups
    kind == ChatKind.Channel -> NotificationChannelKind.Channels
    else -> NotificationChannelKind.Private
}
