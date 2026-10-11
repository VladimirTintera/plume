package eu.tintera.plume.core.notifications

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.platform.DesktopOs
import eu.tintera.plume.core.platform.desktopOs
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/** Jedna notifikace, jak ji vidí systém. [id] musí být jedinečné, podle něj se ruší. */
internal data class DesktopNotification(
    val id: String,
    /** Seskupení v Centru oznámení (macOS): jeden chat = jedno vlákno. */
    val thread: String,
    val title: String,
    val subtitle: String?,
    val body: String,
    val chatId: Long,
    val topicId: Int?,
    val silent: Boolean,
    val imagePath: String?,
)

/** Systémová oznámení desktopu. Platformu vybírá [desktopNotifier]. */
internal interface DesktopNotifier {

    /** Umí to tahle instalace vůbec? Ladicí běh na macOS ne (viz [MacNotificationCenter]). */
    val available: Boolean

    /** `null` = uživatel se ještě nerozhodl. */
    fun isGranted(): Boolean?

    /** Systémový dotaz na povolení; čeká na odpověď, volat mimo hlavní vlákno. */
    fun requestPermission(): Boolean

    fun show(notification: DesktopNotification)

    fun remove(ids: List<String>)

    fun removeAll()

    /** Klik na notifikaci. */
    var onActivated: ((chatId: Long, topicId: Int?) -> Unit)?
}

internal fun desktopNotifier(): DesktopNotifier =
    if (desktopOs == DesktopOs.MacOs) MacNotifier() else TrayNotifier()

private class MacNotifier : DesktopNotifier {

    override val available: Boolean get() = MacNotificationCenter.available

    override fun isGranted(): Boolean? = if (!available) false else when (MacNotificationCenter.nativeAuthorizationStatus()) {
        1 -> true
        2 -> false
        else -> null
    }

    override fun requestPermission(): Boolean = available && MacNotificationCenter.nativeRequestAuthorization()

    override fun show(notification: DesktopNotification) {
        if (!available) return
        MacNotificationCenter.nativeShow(
            identifier = notification.id,
            thread = notification.thread,
            title = notification.title,
            subtitle = notification.subtitle,
            body = notification.body,
            chatId = notification.chatId,
            topicId = notification.topicId ?: 0,
            silent = notification.silent,
            attachmentPath = notification.imagePath?.let(::attachmentCopy),
        )
    }

    override fun remove(ids: List<String>) {
        if (available && ids.isNotEmpty()) MacNotificationCenter.nativeRemove(ids.toTypedArray())
    }

    override fun removeAll() {
        if (available) MacNotificationCenter.nativeRemoveAll()
    }

    override var onActivated: ((chatId: Long, topicId: Int?) -> Unit)? = null
        set(value) {
            field = value
            MacNotificationCenter.onActivated = { chatId, topicId -> value?.invoke(chatId, topicId.takeIf { it != 0 }) }
        }

    /**
     * Systém soubor přílohy PŘESOUVÁ do vlastního úložiště, takže dostane kopii — jinak by
     * z TDLib zmizela profilovka. Přípona musí zůstat, podle ní pozná typ.
     */
    private fun attachmentCopy(path: String): String? = runCatching {
        val source = File(path)
        if (!source.isFile) return null
        val copy = File.createTempFile("plume-notification", "." + source.extension.ifEmpty { "jpg" })
        source.copyTo(copy, overwrite = true)
        copy.deleteOnExit()
        copy.absolutePath
    }.getOrNull()
}

/**
 * Windows a Linux: AWT `TrayIcon.displayMessage` — na Windows bublina u ikony v oznamovací
 * oblasti (od Windows 10 jako oznámení systému), na Linuxu podle prostředí. Kliknutí hlásí
 * ikona jako akci a vztahuje se k poslední zobrazené zprávě. Zrušit zobrazené oznámení AWT
 * neumí, takže [remove] nic nedělá.
 */
private class TrayNotifier : DesktopNotifier {

    private val logger = Logger.withTag("TrayNotifier")

    @Volatile
    private var last: DesktopNotification? = null

    private val icon: TrayIcon? by lazy {
        if (!SystemTray.isSupported()) return@lazy null
        runCatching {
            // Ikonu aplikace nese vstupní bod (`:client:desktopApp`), tady je na classpath za běhu.
            val image = TrayNotifier::class.java.getResourceAsStream("/plume-icon.png")?.use(ImageIO::read)
                ?: BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
            TrayIcon(image, "Plume").apply {
                isImageAutoSize = true
                addActionListener { last?.let { onActivated?.invoke(it.chatId, it.topicId) } }
                SystemTray.getSystemTray().add(this)
            }
        }.onFailure { logger.w(it) { "Ikonu v oznamovací oblasti nejde přidat." } }.getOrNull()
    }

    override val available: Boolean get() = SystemTray.isSupported()

    override fun isGranted(): Boolean = available

    override fun requestPermission(): Boolean = available

    override fun show(notification: DesktopNotification) {
        val tray = icon ?: return
        last = notification
        val title = listOfNotNull(notification.title, notification.subtitle).joinToString(" · ")
        tray.displayMessage(title, notification.body, TrayIcon.MessageType.NONE)
    }

    override fun remove(ids: List<String>) = Unit

    override fun removeAll() = Unit

    override var onActivated: ((chatId: Long, topicId: Int?) -> Unit)? = null
}
