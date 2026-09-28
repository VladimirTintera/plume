package eu.tintera.plume.core.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Intent, který z notifikace otevře konkrétní chat.
 *
 * Cíl se hledá přes `getLaunchIntentForPackage`, aby `:client:shared` nemusela znát `MainActivity`
 * ze vstupního bodu. Bere se z něj ale **jen komponenta**: hotový launcher intent nese
 * `ACTION_MAIN` + `CATEGORY_LAUNCHER` a systém ho vyhodnotí jako „spuštění z plochy" — běžící
 * úlohu vytáhne dopředu s PŮVODNÍM intentem a `onNewIntent` s naším chatId by nikdy nedorazilo.
 */
object NotificationIntents {

    const val EXTRA_CHAT_ID = "eu.tintera.plume.extra.CHAT_ID"

    /** Téma skupiny s tématy, které má notifikace otevřít; chybí = celý chat. */
    const val EXTRA_TOPIC_ID = "eu.tintera.plume.extra.TOPIC_ID"

    const val EXTRA_GROUP_ID = "eu.tintera.plume.extra.NOTIFICATION_GROUP_ID"

    /** Citovaná zpráva odpovědi z notifikace; chybí = bez citace. */
    const val EXTRA_REPLY_TO_MESSAGE_ID = "eu.tintera.plume.extra.REPLY_TO_MESSAGE_ID"

    /** Akce odpovědi z notifikace. Přijímá ji `NotificationActionReceiver` ve vstupním bodu. */
    const val ACTION_REPLY = "eu.tintera.plume.action.NOTIFICATION_REPLY"

    /** Označení chatu za přečtený z notifikace. Týž příjemce jako [ACTION_REPLY]. */
    const val ACTION_MARK_READ = "eu.tintera.plume.action.NOTIFICATION_MARK_READ"

    /** Ztlumení chatu z notifikace. Týž příjemce jako [ACTION_REPLY]. */
    const val ACTION_MUTE = "eu.tintera.plume.action.NOTIFICATION_MUTE"

    /** Nejnovější zpráva notifikace, po kterou se čte; chybí = po nejnovější v chatu. */
    const val EXTRA_MESSAGE_ID = "eu.tintera.plume.extra.MESSAGE_ID"

    /** Klíč textu v `RemoteInput`. */
    const val KEY_REPLY_TEXT = "eu.tintera.plume.extra.REPLY_TEXT"

    /**
     * Holý intent na otevření chatu.
     *
     * Potřebuje ho zkratka konverzace (`ShortcutInfo`), která na rozdíl od notifikace nechce
     * `PendingIntent`. **Musí mít nastavenou akci** — bez ní `pushDynamicShortcut` vyhodí výjimku.
     */
    fun chatIntent(context: Context, chatId: Long, topicId: Int? = null): Intent? {
        val component = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.component
            ?: return null

        return Intent(Intent.ACTION_VIEW)
            .setComponent(component)
            .putExtra(EXTRA_CHAT_ID, chatId)
            .apply { topicId?.let { putExtra(EXTRA_TOPIC_ID, it) } }
            // Běžící aplikace dostane intent do `onNewIntent` (launchMode singleTask v manifestu)
            // místo toho, aby se activity složila znovu a ztratila stav.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    fun openChat(context: Context, notificationId: Int, chatId: Long, topicId: Int? = null): PendingIntent? {
        val intent = chatIntent(context, chatId, topicId) ?: return null

        return PendingIntent.getActivity(
            context,
            // Request code MUSÍ být jedinečný. PendingIntenty se porovnávají podle request code
            // a `filterEquals` — a extras do rovnosti NEVSTUPUJÍ, takže se společným kódem by
            // všechny notifikace otevřely ten chat, který se zaregistroval první. Bere se
            // `notificationId` od TDLib, ne `chatId.hashCode()`: Long se do Intu nevejde bez
            // ztráty a kolize dvou chatů by tiše otevřela ten cizí.
            notificationId,
            intent,
            // UPDATE_CURRENT: existující intent pro tentýž chat se přepíše, ne zduplikuje.
            // IMMUTABLE je od Androidu 12 povinné, když intent nemá měnit příjemce.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Broadcast s odpovědí z notifikace.
     *
     * Příjemce je ve vstupním bodu (kvůli WorkManageru), takže se na něj míří AKCÍ a balíčkem,
     * ne třídou — `:client:shared` ho nezná, stejně jako `MainActivity`. Broadcast s balíčkem
     * je explicitní a dorazí i příjemci deklarovanému v manifestu.
     */
    fun reply(context: Context, groupId: Int, chatId: Long, replyToMessageId: Long?, topicId: Int? = null): PendingIntent {
        val intent = Intent(ACTION_REPLY)
            .setPackage(context.packageName)
            .putExtra(EXTRA_CHAT_ID, chatId)
            .putExtra(EXTRA_GROUP_ID, groupId)
            // Bez tématu by odpověď ve skupině s tématy skončila v obecném tématu. Extras se
            // přepíšou každým překreslením, jinak by u odpovědi zůstalo téma předchozí zprávy.
            .apply { if (topicId != null) putExtra(EXTRA_TOPIC_ID, topicId) else removeExtra(EXTRA_TOPIC_ID) }
            // UPDATE_CURRENT přepíše extras při každém překreslení, takže cituje vždy tu
            // nejnovější zprávu, kterou má uživatel před očima.
            .apply { replyToMessageId?.let { putExtra(EXTRA_REPLY_TO_MESSAGE_ID, it) } }

        return PendingIntent.getBroadcast(
            context,
            // Jedinečný kód z téhož důvodu jako u [openChat]; broadcast a activity se
            // navzájem nepřepíšou, typ PendingIntentu vstupuje do rovnosti.
            groupId,
            intent,
            // MUTABLE je u `RemoteInput` povinné: systém do intentu vkládá napsaný text.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    /**
     * Broadcast „Označit jako přečtené". Na rozdíl od odpovědi do intentu nikdo nic nevkládá,
     * takže IMMUTABLE. Se stejným request code jako odpověď se nepřepíšou — akce intentu
     * vstupuje do `filterEquals`.
     */
    fun markRead(context: Context, groupId: Int, chatId: Long, messageId: Long?): PendingIntent {
        val intent = Intent(ACTION_MARK_READ)
            .setPackage(context.packageName)
            .putExtra(EXTRA_CHAT_ID, chatId)
            .putExtra(EXTRA_GROUP_ID, groupId)
            .apply { messageId?.let { putExtra(EXTRA_MESSAGE_ID, it) } }

        return PendingIntent.getBroadcast(
            context,
            groupId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Broadcast „Ztlumit". IMMUTABLE — do intentu nikdo nic nevkládá. */
    fun mute(context: Context, groupId: Int, chatId: Long): PendingIntent {
        val intent = Intent(ACTION_MUTE)
            .setPackage(context.packageName)
            .putExtra(EXTRA_CHAT_ID, chatId)
            .putExtra(EXTRA_GROUP_ID, groupId)

        return PendingIntent.getBroadcast(
            context,
            groupId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
