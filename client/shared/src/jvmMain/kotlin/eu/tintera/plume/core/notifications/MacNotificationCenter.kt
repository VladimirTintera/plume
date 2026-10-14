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

    /**
     * Klik na notifikaci nebo její tlačítko: chat, téma (0 = bez tématu), tlačítko
     * ([ACTION_ANSWER], [ACTION_DECLINE], `null` = klik na oznámení) a hovor (0 = není hovor).
     * Nastavuje presenter.
     */
    @Volatile
    var onActivated: ((chatId: Long, topicId: Int, action: String?, callId: Int) -> Unit)? = null

    val available: Boolean by lazy { MacNative.loaded && nativeInit() }

    @JvmStatic
    fun onActivated(chatId: Long, topicId: Int, action: String?, callId: Int) {
        onActivated?.invoke(chatId, topicId, action, callId)
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
        /** Příchozí hovor (0 = zpráva): oznámení dostane kategorii s Přijmout a Odmítnout. */
        callId: Int,
    )

    /** Kategorie oznámení hovoru s tlačítky; popisky jsou v ní, ne v jednotlivém oznámení. */
    @JvmStatic
    external fun nativeSetCallActions(answer: String, decline: String)

    @JvmStatic
    external fun nativeRemove(identifiers: Array<String>)

    @JvmStatic
    external fun nativeRemoveAll()

    // Identifikátory tlačítek; tytéž jsou v PlumeNotifications.m.
    const val ACTION_ANSWER = "answer"
    const val ACTION_DECLINE = "decline"
}
