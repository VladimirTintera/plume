package eu.tintera.plume.core.notifications

import eu.tintera.plume.core.platform.MacNative

/**
 * JNI most k `UNUserNotificationCenter` (`jvmMain/objc/PlumeNotifications.m`).
 *
 * Funguje jen v zabalené aplikaci (`Plume.app`): holý `java` z ladicího běhu nemá vlastní
 * balíček a `UNUserNotificationCenter` by v něm shodil proces. Tehdy [available] je `false`
 * a na notifikace se nesahá vůbec (viz docs/poznatky/desktop.md, „Notifikace").
 */
internal object MacNotificationCenter {

    /** Klik na notifikaci: chat a téma (0 = bez tématu). Nastavuje presenter. */
    @Volatile
    var onActivated: ((chatId: Long, topicId: Int) -> Unit)? = null

    val available: Boolean by lazy { MacNative.loaded && nativeInit() }

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
}
