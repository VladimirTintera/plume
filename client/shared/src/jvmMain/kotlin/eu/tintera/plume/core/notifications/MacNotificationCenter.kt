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
     * Klik na notifikaci nebo její tlačítko: argumenty oznámení (tytéž jako u toastu na Windows,
     * [notificationArguments]), tlačítko (`reply`, `read`, `mute`, `answer`, `decline`; `null` = klik na oznámení)
     * a text napsaný do pole odpovědi. Nastavuje presenter.
     */
    @Volatile
    var onActivated: ((arguments: String, action: String?, text: String?) -> Unit)? = null

    val available: Boolean by lazy { MacNative.loaded && nativeInit() }

    @JvmStatic
    fun onActivated(arguments: String, action: String?, text: String?) {
        onActivated?.invoke(arguments, action, text)
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
        /** Vrátí se v [onActivated]; most mu nerozumí. */
        arguments: String,
        /** Kategorie s tlačítky ([CATEGORY_CALL], [CATEGORY_MESSAGE]…); `null` = bez tlačítek. */
        category: String?,
        silent: Boolean,
        attachmentPath: String?,
    )

    /**
     * Kategorie oznámení s tlačítky; popisky jsou v nich, ne v jednotlivém oznámení. Registruje
     * se celá sada naráz: `null` u hovoru nebo u zpráv tu skupinu vynechá.
     */
    @JvmStatic
    external fun nativeSetActions(
        answer: String?,
        decline: String?,
        reply: String?,
        send: String?,
        markRead: String?,
        mute: String?,
    )

    @JvmStatic
    external fun nativeRemove(identifiers: Array<String>)

    @JvmStatic
    external fun nativeRemoveAll()

    // Kategorie; tytéž jsou v PlumeNotifications.m. Identifikátory tlačítek v ní jsou hodnoty
    // `action=` z argumentů toastu (`reply`, `read`, `answer`…), takže je rozebere parseActivation.
    const val CATEGORY_CALL = "call"
    const val CATEGORY_MESSAGE = "message"

    /** Kategorie zprávy podle tlačítek, která jí patří (`message`, `message-reply`, …). */
    fun messageCategory(canReply: Boolean, canMute: Boolean): String = buildString {
        append(CATEGORY_MESSAGE)
        if (canReply) append("-reply")
        if (canMute) append("-mute")
    }
}
