package eu.tintera.plume.core.notifications

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.platform.DesktopOs
import eu.tintera.plume.core.platform.DesktopTray
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
    /** Fotka ze zprávy. */
    val imagePath: String?,
    /** Profilovka chatu (u hovoru volajícího). */
    val avatarPath: String?,
    /** Čas zprávy v sekundách; `null` = teď. */
    val timestamp: Long? = null,
    /** Tlačítka u oznámení chatu (zatím jen toasty Windows); `null` = žádná. */
    val buttons: NotificationButtons? = null,
)

/** Tlačítka toastu chatu, jak je má Android: odpověď, přečtení, ztlumení. */
internal data class NotificationButtons(
    /** Nejnovější zpráva oznámení — po ni se čte. `null` = po nejnovější v chatu. */
    val messageId: Long?,
    /** Citovaná zpráva odpovědi: ve skupině nejnovější z oznámení, v soukromém chatu nic. */
    val quoteMessageId: Long?,
    val canReply: Boolean,
    val canMute: Boolean,
    val labels: Labels,
) {
    /** Popisky přeložené v okamžiku zobrazení; toast je hotový text, ne odkaz do prostředků. */
    data class Labels(val reply: String, val send: String, val markRead: String, val mute: String)
}

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

    /**
     * Klik na oznámení ([ToastActivation.Open]) nebo na jeho tlačítko (odpověď, přečtení,
     * ztlumení — ta běží na pozadí). Tlačítka mají zatím jen toasty Windows.
     */
    var onActivated: ((ToastActivation) -> Unit)?
}

internal fun desktopNotifier(): DesktopNotifier = when {
    desktopOs == DesktopOs.MacOs -> MacNotifier()
    // Bez knihovny toastů (build na stroji bez MSVC) zůstává AWT jako dřív.
    desktopOs == DesktopOs.Windows && WindowsToasts.available -> WindowsNotifier()
    else -> TrayNotifier()
}

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
            attachmentPath = (notification.imagePath ?: notification.avatarPath)?.let(::attachmentCopy),
        )
    }

    override fun remove(ids: List<String>) {
        if (available && ids.isNotEmpty()) MacNotificationCenter.nativeRemove(ids.toTypedArray())
    }

    override fun removeAll() {
        if (available) MacNotificationCenter.nativeRemoveAll()
    }

    override var onActivated: ((ToastActivation) -> Unit)? = null
        set(value) {
            field = value
            MacNotificationCenter.onActivated = { chatId, topicId ->
                value?.invoke(ToastActivation.Open(chatId, topicId.takeIf { it != 0 }))
            }
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
 * Windows: toasty WinRT přes [WindowsToasts]. Na rozdíl od AWT se dají stáhnout (přečtení
 * jinde), seskupují se po chatech, nesou profilovku a klik otevře právě ten chat.
 */
private class WindowsNotifier : DesktopNotifier {

    private val logger = Logger.withTag("WindowsNotifier")

    override val available: Boolean get() = WindowsToasts.available

    /** Windows se na povolení neptá; vypnout jde jen v Nastavení systému. */
    override fun isGranted(): Boolean = WindowsToasts.nativeSetting() == 0

    override fun requestPermission(): Boolean = isGranted()

    override fun show(notification: DesktopNotification) {
        check("zobrazit", WindowsToasts.nativeShow(toastXml(notification), notification.id, GROUP))
    }

    override fun remove(ids: List<String>) {
        if (ids.isNotEmpty()) check("stáhnout", WindowsToasts.nativeRemove(ids.toTypedArray(), GROUP))
    }

    override fun removeAll() {
        check("smazat", WindowsToasts.nativeRemoveAll())
    }

    override var onActivated: ((ToastActivation) -> Unit)? = null
        set(value) {
            field = value
            // Až teď: kliknutí ze studeného startu čeká ve WindowsToasts, dokud tu někdo není.
            WindowsToasts.onActivated = { arguments, reply ->
                val activation = parseActivation(arguments, reply)
                if (activation == null) logger.d { "Toast bez akce: $arguments" } else value?.invoke(activation)
            }
        }

    private fun check(action: String, result: Int) {
        if (result != 0) logger.w { "Toast nejde $action: 0x${result.toUInt().toString(16)}." }
    }

    private companion object {
        /**
         * Skupina v historii toastů. Jedna pro všechno: tag (`g<skupina>-n<notifikace>`) je
         * jedinečný sám a nezabalená aplikace musí skupinu uvést i při rušení, kde ji presenter
         * nezná. Seskupení po chatech nese hlavička v XML, ne skupina.
         */
        const val GROUP = "plume"
    }
}

/**
 * Linux (a Windows bez knihovny toastů): AWT `TrayIcon.displayMessage` — na Windows bublina u ikony v oznamovací
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
                addActionListener { last?.let { onActivated?.invoke(ToastActivation.Open(it.chatId, it.topicId)) } }
                SystemTray.getSystemTray().add(this)
            }
        }.onFailure { logger.w(it) { "Ikonu v oznamovací oblasti nejde přidat." } }.getOrNull()
    }

    override val available: Boolean get() = SystemTray.isSupported()

    override fun isGranted(): Boolean = available

    override fun requestPermission(): Boolean = available

    override fun show(notification: DesktopNotification) {
        // Na Windows je v oznamovací oblasti už ikona aplikace (DesktopTray); druhá by tam visela vedle.
        val tray = DesktopTray.icon ?: icon ?: return
        last = notification
        val title = listOfNotNull(notification.title, notification.subtitle).joinToString(" · ")
        tray.displayMessage(title, notification.body, TrayIcon.MessageType.NONE)
    }

    override fun remove(ids: List<String>) = Unit

    override fun removeAll() = Unit

    override var onActivated: ((ToastActivation) -> Unit)? = null
}
