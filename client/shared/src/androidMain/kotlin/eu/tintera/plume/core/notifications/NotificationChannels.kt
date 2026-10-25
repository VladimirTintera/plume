package eu.tintera.plume.core.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import eu.tintera.plume.core.share.shortcutId
import eu.tintera.plume.core.telegram.NO_SOUND_ID
import eu.tintera.plume.core.telegram.NotificationChannelKind
import eu.tintera.plume.core.telegram.NotificationSoundChoice
import eu.tintera.plume.core.telegram.isSoundChannel
import eu.tintera.plume.core.telegram.soundChannelKey
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.notification_channel_calls
import eu.tintera.plume.resources.notification_channel_calls_description
import eu.tintera.plume.resources.notification_channel_incoming_calls
import eu.tintera.plume.resources.notification_channel_incoming_calls_description
import eu.tintera.plume.resources.notification_channel_channels
import eu.tintera.plume.resources.notification_channel_channels_description
import eu.tintera.plume.resources.notification_channel_groups
import eu.tintera.plume.resources.notification_channel_groups_description
import eu.tintera.plume.resources.notification_channel_mentions
import eu.tintera.plume.resources.notification_channel_mentions_description
import eu.tintera.plume.resources.notification_channel_model_download
import eu.tintera.plume.resources.notification_channel_model_download_description
import eu.tintera.plume.resources.notification_channel_private
import eu.tintera.plume.resources.notification_channel_private_description
import eu.tintera.plume.resources.notification_channel_silent
import eu.tintera.plume.resources.notification_channel_snooze
import eu.tintera.plume.resources.notification_channel_watch_login_description
import eu.tintera.plume.resources.notification_channel_watch_login
import eu.tintera.plume.resources.notification_channel_snooze_description
import eu.tintera.plume.resources.notification_channel_with_sound
import java.io.File

/**
 * Id systémového kanálu. **Nesmí se měnit**: kanál s novým id je pro systém jiný kanál a co si
 * uživatel u starého nastavil (zvuk, ztišení), zůstane u něj.
 */
val NotificationChannelKind.channelId: String
    get() = when (this) {
        NotificationChannelKind.Private -> "private"
        NotificationChannelKind.Groups -> "groups"
        NotificationChannelKind.Channels -> "channels"
        NotificationChannelKind.Mentions -> "mentions"
    }

/**
 * Založí kanály notifikací, nebo jim přeloží název a popis.
 *
 * **Po založení patří kanál uživateli** — důležitost, zvuk ani vibrace už aplikace nepřepíše,
 * `createNotificationChannel` u existujícího kanálu změní jen název a popis. Volá se proto při
 * každém startu: po změně jazyka se přeloží, a nic jiného se tím nerozbije.
 */
suspend fun createNotificationChannels(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    NotificationChannelKind.entries.forEach { kind ->
        val (name, description) = when (kind) {
            NotificationChannelKind.Private ->
                Res.string.notification_channel_private to Res.string.notification_channel_private_description
            NotificationChannelKind.Groups ->
                Res.string.notification_channel_groups to Res.string.notification_channel_groups_description
            NotificationChannelKind.Channels ->
                Res.string.notification_channel_channels to Res.string.notification_channel_channels_description
            NotificationChannelKind.Mentions ->
                Res.string.notification_channel_mentions to Res.string.notification_channel_mentions_description
        }
        val channel = NotificationChannel(
            kind.channelId,
            readString(name),
            // Kanály telegramových kanálů jen tiše do lišty: příspěvků chodí hodně a málokdy
            // na ně člověk musí hned reagovat. Kdo chce víc, přepne si to v systému.
            if (kind == NotificationChannelKind.Channels) NotificationManager.IMPORTANCE_DEFAULT
            else NotificationManager.IMPORTANCE_HIGH,
        ).apply { this.description = readString(description) }
        manager.createNotificationChannel(channel)
    }
    // Hovory mají kanál zvlášť: zvoní jinak než zprávy a kdo hovory v Plume nechce (přijmout
    // je stejně nejde), vypne si jen tenhle.
    manager.createNotificationChannel(
        NotificationChannel(CALLS_CHANNEL_ID, readString(Res.string.notification_channel_calls), NotificationManager.IMPORTANCE_HIGH)
            .apply { description = readString(Res.string.notification_channel_calls_description) },
    )
    // Příchozí hovor, který Plume umí přijmout: vysoká důležitost (celoobrazovková výzva,
    // plovoucí notifikace), ale BEZ zvuku a vibrací — zvoní `CallRinger` vyzváněcím tónem ve
    // smyčce. Kanál zvlášť, protože `setSilent` u notifikace celoobrazovkovou výzvu zablokuje
    // (tichá notifikace „nevyrušuje" a systém ji nespustí) a zvuk kanálu nejde po založení změnit.
    manager.createNotificationChannel(
        NotificationChannel(INCOMING_CALLS_CHANNEL_ID, readString(Res.string.notification_channel_incoming_calls), NotificationManager.IMPORTANCE_HIGH)
            .apply {
                description = readString(Res.string.notification_channel_incoming_calls_description)
                setSound(null, null)
                enableVibration(false)
            },
    )
    createModelDownloadChannel(context)
    createSnoozeChannel(context)
    // Jediný kanál z dřívějška — jinak by v systémovém nastavení visel jako prázdná položka.
    manager.deleteNotificationChannel(LEGACY_CHANNEL_ID)
}

/**
 * Kanál jedné konverzace pod kanálem [parent] (Android 11+), založený, když ještě není.
 *
 * Takový kanál si jinak zakládá systém sám, až když uživatel u notifikace zvolí „Prioritní"
 * nebo vlastní zvuk — dřív systémové nastavení s `EXTRA_CONVERSATION_ID` ukáže jen nadřazený
 * kanál a nastavit tam jde celá skupina chatů, ne ten jeden. Notifikace se zkratkou konverzace
 * (`setShortcutId`) do něj pak systém směruje sám. Nastavení se přebírá z nadřazeného kanálu,
 * ať se založením nic nezmění — i to je chování systému.
 *
 * Vrací id zkratky, které se předá do nastavení; `null` pod Androidem 11.
 */
fun ensureConversationChannel(context: Context, parent: String, chatId: Long, title: String): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    val manager = context.getSystemService(NotificationManager::class.java)
    val conversationId = shortcutId(chatId)
    // Pozor: bez kanálu konverzace vrátí tohle volání NADŘAZENÝ kanál, ne `null`.
    if (manager.getNotificationChannel(parent, conversationId)?.conversationId == conversationId) return conversationId
    val source = manager.getNotificationChannel(parent) ?: return null
    val channel = NotificationChannel("${parent}_$conversationId", title, source.importance).apply {
        description = source.description
        setSound(source.sound, source.audioAttributes)
        enableVibration(source.shouldVibrate())
        vibrationPattern = source.vibrationPattern
        enableLights(source.shouldShowLights())
        lightColor = source.lightColor
        setShowBadge(source.canShowBadge())
        lockscreenVisibility = source.lockscreenVisibility
        setBypassDnd(source.canBypassDnd())
        setConversationId(parent, conversationId)
    }
    manager.createNotificationChannel(channel)
    return conversationId
}

/**
 * Po odhlášení pryč i kanály konverzací — nesou názvy chatů odhlášeného účtu a v systémovém
 * nastavení by po něm visely.
 */
fun deleteConversationChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.notificationChannels
        .filter { it.conversationId != null }
        .forEach { manager.deleteNotificationChannel(it.id) }
}

/**
 * Kanál pro notifikaci se zvukem z Telegramu ([sound], podle `notificationSoundId`).
 *
 * **Výchozí zvuk = výchozí kanál** ([channelId]) beze změny — co si v něm uživatel nastavil,
 * platí dál. Bez zvuku a tón účtu mají kanál vlastní (`private_silent`, `private_tone_<id>`):
 * zvuk kanálu po založení nastavuje už jen uživatel, takže jiný zvuk = jiný kanál. Nový kanál
 * převezme z výchozího všechno, co jde přečíst (důležitost, vibrace, světlo, odznak, zamykací
 * obrazovku, Nerušit), a přidá zvuk. Existující kanál se nechá být — i s tím, co v něm uživatel
 * mezitím změnil. Návrat k výchozímu zvuku vrátí notifikace do výchozího kanálu.
 *
 * Tón, který se nestihl stáhnout, nebo kanál, který nejde založit, znamená výchozí kanál:
 * notifikace má zaznít, i když ne tím tónem.
 */
suspend fun soundChannelId(context: Context, kind: NotificationChannelKind, sound: NotificationSoundChoice): String {
    val id = when (sound) {
        NotificationSoundChoice.Default -> return kind.channelId
        NotificationSoundChoice.Silent -> soundChannelKey(kind.channelId, NO_SOUND_ID)
        is NotificationSoundChoice.Tone -> soundChannelKey(kind.channelId, sound.id)
    }
    val manager = context.getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(id) != null) return id
    val base = manager.getNotificationChannel(kind.channelId) ?: return kind.channelId
    val (soundName, uri) = when (sound) {
        is NotificationSoundChoice.Tone -> sound.title to (toneUri(context, sound) ?: return kind.channelId)
        else -> readString(Res.string.notification_channel_silent) to null
    }
    val channel = NotificationChannel(id, readString(Res.string.notification_channel_with_sound, base.name.toString(), soundName), base.importance).apply {
        description = base.description
        setSound(uri, if (uri == null) null else base.audioAttributes ?: Notification.AUDIO_ATTRIBUTES_DEFAULT)
        enableVibration(base.shouldVibrate())
        vibrationPattern = base.vibrationPattern
        enableLights(base.shouldShowLights())
        lightColor = base.lightColor
        setShowBadge(base.canShowBadge())
        lockscreenVisibility = base.lockscreenVisibility
        setBypassDnd(base.canBypassDnd())
    }
    return runCatching { manager.createNotificationChannel(channel); id }.getOrElse { kind.channelId }
}

/** Kanál se zvukem [soundId], když už existuje; jinak výchozí kanál ([channelId]). */
fun existingSoundChannelId(context: Context, kind: NotificationChannelKind, soundId: Long): String {
    val id = soundChannelKey(kind.channelId, soundId)
    val manager = context.getSystemService(NotificationManager::class.java)
    return if (manager.getNotificationChannel(id) != null) id else kind.channelId
}

/**
 * Tón jako `content://` pro kanál. **Kopie v adresáři aplikace, ne soubor TDLib** — ten může
 * TDLib uklidit a provider by musel vydat celý jeho adresář. Systém zvuk kanálu přehrává
 * pokaždé znovu z URI, takže kopie musí zůstat; právo ke čtení dostane System UI, které zvuk
 * přehrává (jako to dělá Telegram Android).
 */
private fun toneUri(context: Context, tone: NotificationSoundChoice.Tone): Uri? = runCatching {
    val source = File(tone.path ?: return null)
    val directory = File(context.filesDir, SOUNDS_DIRECTORY).apply { mkdirs() }
    val target = File(directory, "${tone.id}.${source.extension.ifBlank { "mp3" }}")
    if (!target.exists() || target.length() != source.length()) source.copyTo(target, overwrite = true)
    val uri = FileProvider.getUriForFile(context, "${context.packageName}$SOUNDS_AUTHORITY_SUFFIX", target)
    SYSTEM_SOUND_PLAYERS.forEach { context.grantUriPermission(it, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
    uri
}.getOrNull()

/** Kanály tónu [soundId] (všechny druhy chatů) a jeho kopie pryč — tón už účet nemá. */
fun deleteToneChannels(context: Context, soundId: Long) {
    val manager = context.getSystemService(NotificationManager::class.java)
    val keys = NotificationChannelKind.entries.map { kind -> soundChannelKey(kind.channelId, soundId) }
    // S ním i kanály konverzací, které pod ním visí (zvuk jednoho chatu z profilu).
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        manager.notificationChannels
            .filter { it.conversationId != null && it.parentChannelId in keys }
            .forEach { manager.deleteNotificationChannel(it.id) }
    }
    keys.forEach { manager.deleteNotificationChannel(it) }
    File(context.filesDir, SOUNDS_DIRECTORY).listFiles()?.filter { it.nameWithoutExtension == soundId.toString() }?.forEach { it.delete() }
}

/** Kanály se zvukem z Telegramu po odhlášení pryč — patří účtu, ne aplikaci. */
fun deleteSoundChannels(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.notificationChannels
        .filter { isSoundChannel(it.id) }
        .forEach { manager.deleteNotificationChannel(it.id) }
    File(context.filesDir, SOUNDS_DIRECTORY).deleteRecursively()
}

/** Musí sedět s `notification_images.xml` (cesta) a autoritou provideru v manifestu. */
private const val SOUNDS_DIRECTORY = "notification_sounds"
private const val SOUNDS_AUTHORITY_SUFFIX = ".notificationimages"

/** Kdo zvuk kanálu přehrává: System UI a systém sám (podle verze Androidu). */
private val SYSTEM_SOUND_PLAYERS = listOf("com.android.systemui", "android")

private const val LEGACY_CHANNEL_ID = "messages"

/** Kanál příchozích hovorů. **Nesmí se měnit**, ze stejného důvodu jako [channelId]. */
const val CALLS_CHANNEL_ID = "calls"

/** Tichý kanál příchozích hovorů, které Plume přijímá sám (zvoní `CallRinger`). Nesmí se měnit. */
const val INCOMING_CALLS_CHANNEL_ID = "incoming-calls"

/** Průběh stahování modelu AI (`ModelDownloadWorker`). Stejně jako ostatní id se nesmí měnit. */
const val MODEL_DOWNLOAD_CHANNEL_ID = "ai-model-download"

/**
 * Kanál stahování modelu. Tichý (`IMPORTANCE_LOW`): průběh i výsledek jsou jen informace, ne
 * výzva k akci, a zvuk u něčeho, co si uživatel sám spustil, by obtěžoval. Volá ho i úloha
 * stahování sama — po zabití procesu ji WorkManager spustí dřív, než by kanál založil start
 * notifikací.
 */
suspend fun createModelDownloadChannel(context: Context) {
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(
            MODEL_DOWNLOAD_CHANNEL_ID,
            readString(Res.string.notification_channel_model_download),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { description = readString(Res.string.notification_channel_model_download_description) },
    )
}

/** Upozornění, že se odložený chat vrací (`SnoozeReminderWorker`). Id se nesmí měnit. */
const val SNOOZE_CHANNEL_ID = "snooze-reminders"

/**
 * Kanál upozornění na odložené chaty. Vlastní, ne kanál zpráv: připomínku si uživatel naplánoval
 * sám a může ji chtít jinak než zprávy (třeba zvonit, i když má soukromé chaty potichu).
 */
suspend fun createSnoozeChannel(context: Context) {
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(
            SNOOZE_CHANNEL_ID,
            readString(Res.string.notification_channel_snooze),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = readString(Res.string.notification_channel_snooze_description) },
    )
}

/** Potvrzení přihlášení hodinek (`WatchLoginService` v `androidApp`). Id se nesmí měnit. */
const val WATCH_LOGIN_CHANNEL_ID = "watch-login"

/**
 * Kanál žádosti hodinek o přihlášení. Důležitý (`IMPORTANCE_HIGH`, vyskočí přes obrazovku):
 * uživatel na odpověď čeká s hodinkami na ruce a přihlašovací kód po chvíli vyprší. Zakládá ho
 * služba sama až s první žádostí — kdo hodinky nemá, kanál v nastavení neuvidí.
 */
suspend fun createWatchLoginChannel(context: Context) {
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(
        NotificationChannel(
            WATCH_LOGIN_CHANNEL_ID,
            readString(Res.string.notification_channel_watch_login),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = readString(Res.string.notification_channel_watch_login_description) },
    )
}
