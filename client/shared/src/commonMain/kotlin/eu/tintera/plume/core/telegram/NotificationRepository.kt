package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow

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
}

data class ChatNotification(
    val groupId: Int,
    val notificationId: Int,
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
)
