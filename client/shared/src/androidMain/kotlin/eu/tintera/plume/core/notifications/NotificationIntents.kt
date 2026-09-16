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

    /**
     * Holý intent na otevření chatu.
     *
     * Potřebuje ho zkratka konverzace (`ShortcutInfo`), která na rozdíl od notifikace nechce
     * `PendingIntent`. **Musí mít nastavenou akci** — bez ní `pushDynamicShortcut` vyhodí výjimku.
     */
    fun chatIntent(context: Context, chatId: Long): Intent? {
        val component = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?.component
            ?: return null

        return Intent(Intent.ACTION_VIEW)
            .setComponent(component)
            .putExtra(EXTRA_CHAT_ID, chatId)
            // SINGLE_TOP + launchMode singleTop v manifestu = běžící aplikace dostane intent do
            // `onNewIntent` místo toho, aby se activity složila znovu a ztratila stav.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    fun openChat(context: Context, notificationId: Int, chatId: Long): PendingIntent? {
        val intent = chatIntent(context, chatId) ?: return null

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
}
