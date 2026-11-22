package eu.tintera.plume.core.notifications

import eu.tintera.plume.core.accounts.AccountSlot
import android.net.Uri
import android.app.PendingIntent
import android.content.ComponentName
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

    /**
     * Účet chatu z [EXTRA_CHAT_ID]. Primární účet ho nenese, takže intenty a zkratky vytvořené
     * dřív znamenají totéž co dnešní. Čte se přes [accountOf].
     */
    const val EXTRA_ACCOUNT = "eu.tintera.plume.extra.ACCOUNT"

    /** Účet z intentu; `null` u nečitelného extra (aktivita je exportovaná). */
    fun accountOf(intent: Intent): AccountSlot? = AccountSlot.parse(intent.getStringExtra(EXTRA_ACCOUNT))

    private fun Intent.putAccount(slot: AccountSlot): Intent =
        if (slot.isPrimary) this else putExtra(EXTRA_ACCOUNT, slot.id)

    /**
     * Request code PendingIntentu: extras do rovnosti nevstupují (viz [openChat]) a [id] od TDLib
     * je unikátní jen v rámci účtu. Primární účet má dál holé [id].
     */
    private fun requestCode(slot: AccountSlot, id: Int): Int =
        if (slot.isPrimary) id else 31 * slot.id.hashCode() + id

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

    /** Uživatel notifikaci chatu odhrnul (delete intent). Týž příjemce jako [ACTION_REPLY]. */
    const val ACTION_DISMISS = "eu.tintera.plume.action.NOTIFICATION_DISMISS"

    /** Nejvyšší id notifikace od TDLib, které odhrnutá notifikace ukazovala. */
    const val EXTRA_MAX_NOTIFICATION_ID = "eu.tintera.plume.extra.MAX_NOTIFICATION_ID"

    /** Odmítnutí příchozího hovoru z notifikace. Týž příjemce jako [ACTION_REPLY]. */
    const val ACTION_DECLINE_CALL = "eu.tintera.plume.action.NOTIFICATION_DECLINE_CALL"

    /**
     * Odmítnutí hovoru se zprávou z notifikace (text v `RemoteInput` pod [KEY_REPLY_TEXT]).
     * Týž příjemce jako [ACTION_REPLY].
     */
    const val ACTION_DECLINE_CALL_WITH_MESSAGE = "eu.tintera.plume.action.NOTIFICATION_DECLINE_CALL_WITH_MESSAGE"

    const val EXTRA_CALL_ID = "eu.tintera.plume.extra.CALL_ID"
    const val EXTRA_IS_VIDEO = "eu.tintera.plume.extra.IS_VIDEO"

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
    fun chatIntent(context: Context, slot: AccountSlot, chatId: Long, topicId: Int? = null): Intent? {
        val component = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.component
            ?: return null

        return Intent(Intent.ACTION_VIEW)
            .setComponent(component)
            .putExtra(EXTRA_CHAT_ID, chatId)
            .putAccount(slot)
            .apply { topicId?.let { putExtra(EXTRA_TOPIC_ID, it) } }
            // Běžící aplikace dostane intent do `onNewIntent` (launchMode singleTask v manifestu)
            // místo toho, aby se activity složila znovu a ztratila stav.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    fun openChat(context: Context, slot: AccountSlot, notificationId: Int, chatId: Long, topicId: Int? = null): PendingIntent? {
        val intent = chatIntent(context, slot, chatId, topicId) ?: return null

        return PendingIntent.getActivity(
            context,
            // Request code MUSÍ být jedinečný. PendingIntenty se porovnávají podle request code
            // a `filterEquals` — a extras do rovnosti NEVSTUPUJÍ, takže se společným kódem by
            // všechny notifikace otevřely ten chat, který se zaregistroval první. Bere se
            // `notificationId` od TDLib, ne `chatId.hashCode()`: Long se do Intu nevejde bez
            // ztráty a kolize dvou chatů by tiše otevřela ten cizí.
            requestCode(slot, notificationId),
            intent,
            // UPDATE_CURRENT: existující intent pro tentýž chat se přepíše, ne zduplikuje.
            // IMMUTABLE je od Androidu 12 povinné, když intent nemá měnit příjemce.
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Akce aktivity bubliny (`BubbleActivity` ve vstupním bodu, intent-filter v manifestu). */
    const val ACTION_BUBBLE = "eu.tintera.plume.action.BUBBLE"

    /**
     * Konverzace v bublině (Android 11+). Na aktivitu se míří AKCÍ a balíčkem, ne třídou —
     * `:client:shared` ji nezná, stejně jako `MainActivity` (viz [reply]).
     *
     * - **Data nesou chatId** (`plume-bubble://chat/<id>`): bublina každého chatu musí být
     *   jiný `Intent` podle `filterEquals`, jinak by systém dvě bubliny spojil.
     * - **`FLAG_MUTABLE` je pro bublinu povinné** (Android 12+): systém do intentu doplňuje
     *   údaje o okně bubliny. S `IMMUTABLE` notifikace bublinu nedostane vůbec.
     */
    fun bubble(context: Context, slot: AccountSlot, notificationId: Int, chatId: Long): PendingIntent {
        val intent = Intent(ACTION_BUBBLE)
            .setPackage(context.packageName)
            .setData(Uri.parse(if (slot.isPrimary) "plume-bubble://chat/$chatId" else "plume-bubble://chat/${slot.id}/$chatId"))
            .putExtra(EXTRA_CHAT_ID, chatId)
            .putAccount(slot)
        return PendingIntent.getActivity(
            context,
            requestCode(slot, notificationId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    /**
     * Broadcast s odpovědí z notifikace.
     *
     * Příjemce je ve vstupním bodu (kvůli WorkManageru), takže se na něj míří AKCÍ a balíčkem,
     * ne třídou — `:client:shared` ho nezná, stejně jako `MainActivity`. Broadcast s balíčkem
     * je explicitní a dorazí i příjemci deklarovanému v manifestu.
     */
    fun reply(context: Context, slot: AccountSlot, groupId: Int, chatId: Long, replyToMessageId: Long?, topicId: Int? = null): PendingIntent {
        val intent = Intent(ACTION_REPLY)
            .setPackage(context.packageName)
            .putExtra(EXTRA_CHAT_ID, chatId)
            .putAccount(slot)
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
            requestCode(slot, groupId),
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
    fun markRead(context: Context, slot: AccountSlot, groupId: Int, chatId: Long, messageId: Long?): PendingIntent {
        val intent = Intent(ACTION_MARK_READ)
            .setPackage(context.packageName)
            .putExtra(EXTRA_CHAT_ID, chatId)
            .putAccount(slot)
            .putExtra(EXTRA_GROUP_ID, groupId)
            .apply { messageId?.let { putExtra(EXTRA_MESSAGE_ID, it) } }

        return PendingIntent.getBroadcast(
            context,
            requestCode(slot, groupId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Broadcast „Odmítnout" hovor. IMMUTABLE — do intentu nikdo nic nevkládá. */
    fun declineCall(context: Context, slot: AccountSlot, groupId: Int, chatId: Long, callId: Int, isVideo: Boolean): PendingIntent {
        val intent = Intent(ACTION_DECLINE_CALL)
            .setPackage(context.packageName)
            .putExtra(EXTRA_CHAT_ID, chatId)
            .putAccount(slot)
            .putExtra(EXTRA_GROUP_ID, groupId)
            .putExtra(EXTRA_CALL_ID, callId)
            .putExtra(EXTRA_IS_VIDEO, isVideo)

        return PendingIntent.getBroadcast(
            context,
            requestCode(slot, groupId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Broadcast „Odmítnout se zprávou". MUTABLE jako [reply]: systém do intentu vkládá text
     * (napsaný, nebo vybraný z hotových odpovědí).
     */
    fun declineCallWithMessage(context: Context, slot: AccountSlot, groupId: Int, chatId: Long, callId: Int, isVideo: Boolean): PendingIntent {
        val intent = Intent(ACTION_DECLINE_CALL_WITH_MESSAGE)
            .setPackage(context.packageName)
            .putExtra(EXTRA_CHAT_ID, chatId)
            .putAccount(slot)
            .putExtra(EXTRA_GROUP_ID, groupId)
            .putExtra(EXTRA_CALL_ID, callId)
            .putExtra(EXTRA_IS_VIDEO, isVideo)

        return PendingIntent.getBroadcast(
            context,
            requestCode(slot, groupId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
    }

    /** Hovor, který má aplikace po otevření přijmout (akce „Přijmout" v notifikaci). */
    const val EXTRA_ANSWER_CALL = "eu.tintera.plume.extra.ANSWER_CALL"

    /** Otevřít aplikaci na obrazovce hovoru (ťuknutí na notifikaci hovoru, celoobrazovková výzva). */
    const val EXTRA_SHOW_CALL = "eu.tintera.plume.extra.SHOW_CALL"

    /**
     * Neexportovaný alias `MainActivity` (`<activity-alias>` v manifestu vstupního bodu), přes
     * který chodí akce hovoru z notifikací — „Přijmout" a „Zavolat zpět".
     *
     * `MainActivity` je exportovaná (spouštěč, sdílení, odkazy), takže jí intent s libovolnými
     * extras pošle kterákoli aplikace v telefonu, i bez oprávnění, a webová stránka přes
     * `intent:` odkaz. Akce hovoru přitom nic nepotvrzuje: s povoleným mikrofonem a kamerou by
     * cizí aplikace vytočila hovor (i video), nebo přijala ten, který zrovna zvoní — id hovoru
     * od TDLib jsou malá pořadová čísla. Alias spustí jen sama aplikace (náš PendingIntent běží
     * pod naší identitou) a v `getComponent()` intentu pak zůstane jméno aliasu, ne aktivity.
     * Viz [isCallAction].
     */
    const val CALL_ACTION_ALIAS = "eu.tintera.plume.CallActionAlias"

    /** Přišel intent přes [CALL_ACTION_ALIAS], tedy z vlastní notifikace? Jinak akce hovoru nebrat. */
    fun isCallAction(intent: Intent): Boolean = intent.component?.className == CALL_ACTION_ALIAS

    private fun callActionComponent(context: Context) = ComponentName(context.packageName, CALL_ACTION_ALIAS)

    /**
     * „Přijmout" z notifikace otevírá ACTIVITY, ne broadcast: od Androidu 12 notifikace nesmí
     * spouštět aktivitu oklikou přes přijímač (trampolína), a přijetí hovoru stejně chce
     * aplikaci v popředí — mikrofon pro službu hovoru dá Android jen tehdy. Míří na
     * [CALL_ACTION_ALIAS], ne na spouštěcí aktivitu.
     */
    fun answerCall(context: Context, groupId: Int, callId: Int): PendingIntent? {
        val intent = Intent(ACTION_ANSWER_CALL)
            .setComponent(callActionComponent(context))
            .putExtra(EXTRA_ANSWER_CALL, callId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            groupId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Komu zavolat zpátky (akce „Zavolat zpět" u notifikace zmeškaného hovoru). */
    const val EXTRA_CALL_BACK_USER = "eu.tintera.plume.extra.CALL_BACK_USER"

    /**
     * „Zavolat zpět" u notifikace zmeškaného hovoru. Otevírá ACTIVITY ze stejného důvodu jako
     * [answerCall]: o mikrofon se umí zeptat jen obrazovka a hovor chce aplikaci v popředí.
     * Nese i chat, takže se pod obrazovkou hovoru otevře konverzace — po zavěšení je uživatel
     * tam, kde se o hovoru dozvěděl, a otevřením se zpráva přečte a TDLib notifikaci stáhne.
     * Míří na [CALL_ACTION_ALIAS], ne na spouštěcí aktivitu.
     */
    fun callBack(context: Context, slot: AccountSlot, groupId: Int, chatId: Long, userId: Long, isVideo: Boolean): PendingIntent? {
        val intent = chatIntent(context, slot, chatId) ?: return null
        intent.component = callActionComponent(context)
        intent.action = ACTION_CALL_BACK
        intent.putExtra(EXTRA_CALL_BACK_USER, userId).putExtra(EXTRA_IS_VIDEO, isVideo)
        return PendingIntent.getActivity(
            context,
            // Akce intentu vstupuje do `filterEquals`, takže se s otevřením chatu (týž kód)
            // nepřepíšou.
            requestCode(slot, groupId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val ACTION_CALL_BACK = "eu.tintera.plume.action.CALL_BACK"

    /** Akce intentu „Přijmout" — odliší ho od otevření hovoru podle `filterEquals`. */
    private const val ACTION_ANSWER_CALL = "eu.tintera.plume.action.ANSWER_CALL"
    private const val ACTION_SHOW_CALL = "eu.tintera.plume.action.SHOW_CALL"

    /** Otevře aplikaci na obrazovce hovoru. */
    fun showCall(context: Context): PendingIntent? {
        val component = context.packageManager.getLaunchIntentForPackage(context.packageName)?.component ?: return null
        val intent = Intent(ACTION_SHOW_CALL)
            .setComponent(component)
            .putExtra(EXTRA_SHOW_CALL, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            context,
            SHOW_CALL_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private const val SHOW_CALL_REQUEST = 7303

    /**
     * Broadcast při odhrnutí notifikace (`setDeleteIntent`). Systém ho pošle JEN při zavření
     * uživatelem (tah, „Vymazat vše"), ne při ťuknutí s auto-cancel ani při `cancel` z aplikace.
     * [maxNotificationId] jede v intentu, ne v paměti presenteru: notifikace přežije proces,
     * a odhrnout ji jde i po restartu. UPDATE_CURRENT ho přepíše každým překreslením.
     */
    fun dismiss(context: Context, slot: AccountSlot, groupId: Int, chatId: Long, maxNotificationId: Int?): PendingIntent {
        val intent = Intent(ACTION_DISMISS)
            .setPackage(context.packageName)
            .putExtra(EXTRA_CHAT_ID, chatId)
            .putAccount(slot)
            .putExtra(EXTRA_GROUP_ID, groupId)
            .apply { if (maxNotificationId != null) putExtra(EXTRA_MAX_NOTIFICATION_ID, maxNotificationId) else removeExtra(EXTRA_MAX_NOTIFICATION_ID) }

        return PendingIntent.getBroadcast(
            context,
            requestCode(slot, groupId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Broadcast „Ztlumit". IMMUTABLE — do intentu nikdo nic nevkládá. */
    fun mute(context: Context, slot: AccountSlot, groupId: Int, chatId: Long): PendingIntent {
        val intent = Intent(ACTION_MUTE)
            .setPackage(context.packageName)
            .putExtra(EXTRA_CHAT_ID, chatId)
            .putAccount(slot)
            .putExtra(EXTRA_GROUP_ID, groupId)

        return PendingIntent.getBroadcast(
            context,
            requestCode(slot, groupId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
