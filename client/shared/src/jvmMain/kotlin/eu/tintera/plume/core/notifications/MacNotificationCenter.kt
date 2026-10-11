package eu.tintera.plume.core.notifications

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.platform.DesktopOs
import eu.tintera.plume.core.platform.desktopOs
import java.io.File
import kotlin.io.path.createTempDirectory

/**
 * JNI most k `UNUserNotificationCenter` (`jvmMain/objc/PlumeNotifications.m`).
 *
 * Funguje jen v zabalené aplikaci (`Plume.app`): holý `java` z ladicího běhu nemá vlastní
 * balíček a `UNUserNotificationCenter` by v něm shodil proces. Tehdy [available] je `false`
 * a na notifikace se nesahá vůbec (viz docs/poznatky/desktop.md, „Notifikace").
 */
internal object MacNotificationCenter {

    private val logger = Logger.withTag("MacNotifications")

    /** Klik na notifikaci: chat a téma (0 = bez tématu). Nastavuje presenter. */
    @Volatile
    var onActivated: ((chatId: Long, topicId: Int) -> Unit)? = null

    val available: Boolean by lazy { desktopOs == DesktopOs.MacOs && load() && nativeInit() }

    private fun load(): Boolean = runCatching {
        val resource = MacNotificationCenter::class.java.getResource("/macos/$LIBRARY") ?: run {
            logger.w { "Knihovna notifikací chybí v jaru (build mimo Mac?)." }
            return false
        }
        val directory = createTempDirectory("plume-notifications").toFile().apply { deleteOnExit() }
        val file = File(directory, LIBRARY).apply { deleteOnExit() }
        resource.openStream().use { input -> file.outputStream().use(input::copyTo) }
        System.load(file.absolutePath)
        true
    }.getOrElse {
        logger.w(it) { "Knihovnu notifikací nejde nahrát." }
        false
    }

    @JvmStatic
    fun onActivated(chatId: Long, topicId: Int) {
        onActivated?.invoke(chatId, topicId)
    }

    @JvmStatic
    external fun nativeInit(): Boolean

    /** 0 = nerozhodnuto, 1 = povoleno, 2 = zakázáno. */
    @JvmStatic
    external fun nativeAuthorizationStatus(): Int

    @JvmStatic
    external fun nativeRequestAuthorization(): Boolean

    @JvmStatic
    external fun nativeShow(
        identifier: String,
        thread: String,
        title: String,
        subtitle: String?,
        body: String,
        chatId: Long,
        topicId: Int,
        silent: Boolean,
        attachmentPath: String?,
    )

    @JvmStatic
    external fun nativeRemove(identifiers: Array<String>)

    @JvmStatic
    external fun nativeRemoveAll()

    private const val LIBRARY = "libplumenotifications.dylib"
}
