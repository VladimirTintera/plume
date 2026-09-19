package eu.tintera.plume.core.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.notifications.NotificationIntents

/**
 * Dlouhověká zkratka konverzace — tatáž pro notifikace (velký kruh, konverzace v systému)
 * i pro Direct Share (chat v nabídce „Sdílet").
 *
 * **Kategorie [SHARE_CATEGORY] ji přihlásí ke sdílení**; párovat ji musí `share-target`
 * v `res/xml/shortcuts.xml` vstupního bodu. Bez kategorie by se zkratka ve sdílení neukázala,
 * bez `share-target` taky ne — a nic z toho nevyhodí chybu.
 */
internal fun conversationShortcut(context: Context, chatId: Long, title: String, photo: IconCompat?): ShortcutInfoCompat? {
    val intent = NotificationIntents.chatIntent(context, chatId) ?: return null
    val id = shortcutId(chatId)
    // Bez fotky iniciála, jako v aplikaci. Prázdné kolečko ve sdílení i v notifikaci vypadalo
    // jako nenačtený obrázek.
    val icon = photo ?: initialIcon(context, title)

    val person = Person.Builder()
        .setName(title)
        .setKey(id)
        .setIcon(icon)
        .build()

    return ShortcutInfoCompat.Builder(context, id)
        .setLongLived(true)
        .setShortLabel(title)
        .setIntent(intent)
        .setPerson(person)
        .setCategories(setOf(SHARE_CATEGORY))
        .apply { icon?.let { setIcon(it) } }
        .build()
}

/**
 * Iniciála chatu jako ikona. Barvy ze systémové palety Material You (`system_accent1_*`) —
 * zkratka se kreslí mimo aplikaci, takže téma aplikace k dispozici není, a barva natvrdo by se
 * s tapetou a motivem systému tloukla. Ta paleta je až od Androidu 12; na starším zůstane ikona
 * prázdná, jako byla.
 */
private fun initialIcon(context: Context, title: String): IconCompat? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val initial = title.trim().takeIf { it.isNotEmpty() }
        ?.let { String(Character.toChars(it.codePointAt(0))).uppercase() }
        ?: return null

    // Adaptivní ikona má 108 dp a viditelný střed 72 dp; písmeno se měří na ten střed.
    val size = (ADAPTIVE_ICON_DP * context.resources.displayMetrics.density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(context.getColor(android.R.color.system_accent1_100))
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColor(android.R.color.system_accent1_900)
        textSize = size * INITIAL_TEXT_FRACTION
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val baseline = size / 2f - (paint.descent() + paint.ascent()) / 2f
    canvas.drawText(initial, size / 2f, baseline, paint)
    return IconCompat.createWithAdaptiveBitmap(bitmap)
}

/**
 * Záložka Uložených zpráv jako ikona zkratky — jako `Avatar(isSavedMessages = true)` v aplikaci:
 * plná `primary` s ikonou v `onPrimary`, tady ze systémové palety (viz [initialIcon]).
 */
internal fun bookmarkIcon(context: Context): IconCompat? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val size = (ADAPTIVE_ICON_DP * context.resources.displayMetrics.density).toInt()
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(context.getColor(android.R.color.system_accent1_600))
    // Tvar záložky z Material Symbols (bookmark, filled) ve viewportu 960, zmenšený na 36 %
    // ikony — viditelný střed adaptivní ikony je dvě třetiny.
    val unit = size * 0.36f / 960f
    val left = (size - 560 * unit) / 2f
    val top = (size - 720 * unit) / 2f
    val path = Path().apply {
        moveTo(left, top + 720 * unit)
        lineTo(left, top)
        lineTo(left + 560 * unit, top)
        lineTo(left + 560 * unit, top + 720 * unit)
        lineTo(left + 280 * unit, top + 600 * unit)
        close()
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = context.getColor(android.R.color.system_accent1_0) }
    canvas.drawPath(path, paint)
    return IconCompat.createWithAdaptiveBitmap(bitmap)
}

private const val ADAPTIVE_ICON_DP = 108
private const val INITIAL_TEXT_FRACTION = 0.3f

/**
 * Publikuje zkratku. `pushDynamicShortcut` se zároveň počítá jako POUŽITÍ zkratky — právě
 * z toho si systém skládá pořadí chatů v nabídce „Sdílet". Sám hlídá strop počtu zkratek
 * a nejstarší odsouvá. Chybu polyká: bez zkratky se nic nerozbije.
 */
internal fun pushConversationShortcut(context: Context, shortcut: ShortcutInfoCompat): Boolean =
    runCatching { ShortcutManagerCompat.pushDynamicShortcut(context, shortcut) }
        .onFailure { Logger.withTag("Shortcuts").w(it) { "Zkratku konverzace se nepodařilo publikovat." } }
        .getOrDefault(false)

internal fun shortcutId(chatId: Long): String = SHORTCUT_PREFIX + chatId

/** Chat ze zkratky, přes kterou uživatel sdílel; `null` u cizího nebo nečitelného id. */
internal fun chatIdFromShortcut(id: String?): Long? = id?.removePrefix(SHORTCUT_PREFIX)?.takeIf { it != id }?.toLongOrNull()

/** Kategorie zkratek, které nabízí systémová nabídka „Sdílet". Stejná jako v `shortcuts.xml`. */
const val SHARE_CATEGORY = "eu.tintera.plume.category.SHARE_TARGET"

private const val SHORTCUT_PREFIX = "chat_"

/** Android: chat jako zkratka konverzace s kategorií ke sdílení. */
class AndroidShareTargetPublisher(private val context: Context) : ShareTargetPublisher {
    override fun publish(chatId: Long, title: String, avatarPath: String?, isSavedMessages: Boolean) {
        if (isSavedMessages) {
            conversationShortcut(context, chatId, title, bookmarkIcon(context))?.let { pushConversationShortcut(context, it) }
            return
        }
        // Kulatý ořez jako u notifikací — Android zkratky kreslí v kruhu.
        val icon = avatarPath
            ?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
            ?.let(IconCompat::createWithAdaptiveBitmap)
        conversationShortcut(context, chatId, title, icon)?.let { pushConversationShortcut(context, it) }
    }
}
