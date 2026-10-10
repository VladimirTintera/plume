package eu.tintera.plume.core.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import eu.tintera.plume.client.R
import android.text.format.DateFormat
import androidx.compose.runtime.remember
import androidx.datastore.preferences.core.Preferences
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import eu.tintera.plume.core.notifications.NotificationIntents
import eu.tintera.plume.core.ui.asTimeOfDay
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.widget_chats_empty
import eu.tintera.plume.resources.widget_chats_logged_out
import eu.tintera.plume.resources.widget_chats_title
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Widget na plochu s chaty: napřed připnuté a nepřečtené, ťuknutí otevře chat, záhlaví aplikaci.
 *
 * Kreslí se z podkladů, které zapisuje [ChatsWidgetUpdater] — launcher ho kreslí i bez běžícího
 * procesu. Texty se překládají tady, před složením obsahu: Glance skládá vlastní kompozici bez
 * prostředí Compose Resources, `stringResource` v ní nefunguje.
 *
 * Barvy jsou z `GlanceTheme` — na Androidu 12+ z tapety, jako zbytek systému, níž výchozí paleta
 * Glance. Fotky chatů zatím ne: launcher je kreslí z bitmap v paměti widgetu a pro dvanáct chatů
 * by to bylo dvanáct dekódování obrázku při každém překreslení; místo nich iniciála.
 */
class ChatsWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    /**
     * Podklady nese stav widgetu (`currentState`), ne jen soubor. Glance drží kompozici widgetu
     * po obnově ještě chvíli otevřenou a další obnova v tu dobu jen překreslí tutéž kompozici;
     * podklady přečtené jednou před `provideContent` by zůstaly staré (stalo se na emulátoru:
     * zpráva od bota byla v souboru, widget ji neukázal). Stav se při obnově přepíše
     * ([ChatsWidgetUpdater]) a kompozice si ho přečte znovu.
     */
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Soubor jen pro první kreslení widgetu, do kterého aktualizace ještě nic nezapsala.
        val initial = readSnapshot(context)
        val texts = WidgetTexts(
            title = readString(Res.string.widget_chats_title),
            loggedOut = readString(Res.string.widget_chats_logged_out),
            empty = readString(Res.string.widget_chats_empty),
        )
        provideContent {
            val stored = currentState<Preferences>()[SNAPSHOT_KEY]
            val snapshot = remember(stored) { stored?.let(::decodeSnapshot) ?: initial }
            GlanceTheme {
                ChatsWidgetContent(context, snapshot, texts)
            }
        }
    }
}

/** Přijímač widgetu — v manifestu vstupního bodu (`androidApp`), odtud jen třída. */
class ChatsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ChatsWidget()
}

private class WidgetTexts(
    val title: String,
    val loggedOut: String,
    val empty: String,
)

@Composable
private fun ChatsWidgetContent(context: Context, snapshot: WidgetSnapshot, texts: WidgetTexts) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(GlanceTheme.colors.widgetBackground)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 4.dp).clickable(openApp(context)),
        ) {
            Text(
                text = texts.title,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.defaultWeight(),
            )
            if (snapshot.unread > 0) UnreadBadge(snapshot.unread)
        }
        Spacer(GlanceModifier.height(8.dp))
        when {
            !snapshot.loggedIn -> Message(texts.loggedOut, context)
            snapshot.chats.isEmpty() -> Message(texts.empty, context)
            else -> LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                items(snapshot.chats, itemId = { it.id }) { chat ->
                    ChatRow(context, chat, chat.timestamp?.let(::widgetTime))
                }
            }
        }
    }
}

@Composable
private fun ChatRow(context: Context, chat: WidgetChat, time: String?) {
    val unread = chat.unread > 0
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .cornerRadius(16.dp)
            .clickable(openChat(context, chat.id)),
    ) {
        ChatAvatar(chat)
        Spacer(GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            // Řádek přes celou šířku, jinak se jméno nevyplní a čas se na něj nalepí.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = GlanceModifier.fillMaxWidth()) {
                Text(
                    text = chat.title,
                    maxLines = 1,
                    // Nepřečtený chat tučně, jako v seznamu chatů.
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 15.sp,
                        fontWeight = if (unread && !chat.muted) FontWeight.Bold else FontWeight.Normal,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
                time?.let {
                    Text(
                        text = it,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                        modifier = GlanceModifier.padding(start = 8.dp),
                    )
                }
            }
            if (chat.preview != null) {
                Text(
                    text = chat.preview,
                    maxLines = 1,
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
                )
            }
        }
        if (unread) {
            Spacer(GlanceModifier.width(8.dp))
            UnreadBadge(chat.unread, quiet = chat.muted)
        }
    }
}

/**
 * Fotka chatu (miniatura už oříznutá do kruhu, viz `WidgetAvatars`), u Uložených zpráv záložka,
 * jinak iniciála. Miniatura je pár kB, dekódování v kompozici widgetu nic nestojí.
 */
@Composable
private fun ChatAvatar(chat: WidgetChat) {
    val bitmap = remember(chat.avatar) { chat.avatar?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() } }
    val modifier = GlanceModifier.size(AVATAR_SIZE).cornerRadius(AVATAR_SIZE / 2)
    when {
        chat.savedMessages -> Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.background(GlanceTheme.colors.primary),
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_widget_saved_messages),
                contentDescription = null,
                colorFilter = ColorFilter.tint(GlanceTheme.colors.onPrimary),
                modifier = GlanceModifier.size(20.dp),
            )
        }
        bitmap != null -> Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            modifier = modifier,
        )
        else -> Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.background(GlanceTheme.colors.primaryContainer),
        ) {
            Text(
                text = initial(chat.title),
                style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontSize = 16.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

/** Počet nepřečtených; u ztlumeného chatu tlumeně, jako v seznamu chatů. */
@Composable
private fun UnreadBadge(count: Int, quiet: Boolean = false) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = GlanceModifier
            .height(22.dp)
            .cornerRadius(11.dp)
            .background(if (quiet) GlanceTheme.colors.surfaceVariant else GlanceTheme.colors.primary)
            .padding(horizontal = 7.dp),
    ) {
        Text(
            text = if (count > MAX_BADGE) "$MAX_BADGE+" else count.toString(),
            style = TextStyle(
                color = if (quiet) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun Message(text: String, context: Context) {
    Box(contentAlignment = Alignment.Center, modifier = GlanceModifier.fillMaxSize().clickable(openApp(context))) {
        Text(text = text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp))
    }
}

/** První písmeno názvu (i složené z víc znaků, třeba emoji), velké. */
private fun initial(title: String): String {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return "?"
    val end = if (trimmed[0].isHighSurrogate() && trimmed.length > 1) 2 else 1
    return trimmed.substring(0, end).uppercase()
}

/**
 * Dnešní zpráva hodinou, starší datem — jako v seznamu chatů, jen bez slov („včera"). Datum
 * formátuje systém podle jazyka (`getBestDateTimePattern`): počítá se v kompozici widgetu, kde
 * suspend čtení prostředků nejde.
 */
private fun widgetTime(timestamp: Int): String {
    val zone = TimeZone.currentSystemDefault()
    val date = Instant.fromEpochSeconds(timestamp.toLong()).toLocalDateTime(zone).date
    val today = Clock.System.now().toLocalDateTime(zone).date
    if (date == today) return timestamp.asTimeOfDay(zone)
    val locale = Locale.getDefault()
    val pattern = DateFormat.getBestDateTimePattern(locale, "dM")
    return SimpleDateFormat(pattern, locale).format(Date(timestamp * 1000L))
}

/**
 * Otevře chat stejnou cestou jako notifikace. **Data nesou chatId**, aby byl intent každého
 * řádku jiný podle `filterEquals` — jinak by se intenty řádků slily a všechny by otevřely týž chat.
 */
private fun openChat(context: Context, chatId: Long): Action {
    val intent = NotificationIntents.chatIntent(context, chatId)
        ?.setData(Uri.parse("plume-widget://chat/$chatId"))
        ?: return openApp(context)
    return actionStartActivity(intent)
}

private fun openApp(context: Context): Action {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: Intent().setPackage(context.packageName)
    return actionStartActivity(intent)
}

private const val MAX_BADGE = 999

private val AVATAR_SIZE = 40.dp
