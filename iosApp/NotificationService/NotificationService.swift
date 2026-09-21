import UserNotifications

/// Rozšíření notifikací: doplní pushi od Telegramu to, co aplikace potřebuje a Telegram neposílá.
///
/// - **kategorii** s akcemi (odpovědět / přečíst / ztlumit), podle druhu chatu a zmínky,
/// - **vlákno** podle chatu, aby se notifikace jednoho chatu v centru seskupily,
/// - **klíče `plume_*`** v `userInfo` — id chatu a zprávy v číslování TDLib.
///
/// Text notifikace neskládá: ten iOS poskládá sám z `loc-key` podle `Localizable.strings`
/// aplikace. TDLib tu neběží — na databázi aplikace nedosáhne a limit paměti rozšíření by ji
/// stejně nesnesl; plain APNs od Telegramu šifrování nemá, takže není co rozšifrovat.
///
/// **Totéž pravidlo má Kotlin** (`TelegramPush.kt` a `NotificationCategories.kt`) pro případ,
/// kdy rozšíření neběží (push bez `mutable-content`). Změna jedné strany musí jít i do druhé.
final class NotificationService: UNNotificationServiceExtension {

    override func didReceive(
        _ request: UNNotificationRequest,
        withContentHandler contentHandler: @escaping (UNNotificationContent) -> Void
    ) {
        guard let content = request.content.mutableCopy() as? UNMutableNotificationContent else {
            contentHandler(request.content)
            return
        }
        let userInfo = request.content.userInfo
        #if DEBUG
        savePayloadForInspection(userInfo)
        #endif

        let alert = (userInfo["aps"] as? [String: Any])?["alert"] as? [String: Any]
        let locKey = alert?["loc-key"] as? String ?? ""
        let custom = userInfo["custom"] as? [String: Any] ?? [:]

        if let chatId = Self.chatId(custom) {
            var info = userInfo
            info["plume_chat_id"] = NSNumber(value: chatId)
            let messageId = Self.number(custom["msg_id"]).map { $0 << 20 } ?? 0
            info["plume_message_id"] = NSNumber(value: messageId)
            info["plume_is_group"] = NSNumber(value: locKey.hasPrefix("CHAT_"))
            content.userInfo = info
            content.threadIdentifier = String(chatId)
            if Self.isMessage(locKey) {
                content.categoryIdentifier = Self.category(
                    canReply: !locKey.hasPrefix("CHANNEL_"),
                    canMute: Self.number(custom["mention"]) != 1
                )
            }
        }
        contentHandler(content)
    }

    /// Chat v číslování TDLib: uživatel kladně, základní skupina záporně, supergroup a kanál −10¹² − id.
    private static func chatId(_ custom: [String: Any]) -> Int64? {
        if let channel = number(custom["channel_id"]), channel > 0 { return -1_000_000_000_000 - channel }
        if let group = number(custom["chat_id"]), group > 0 { return -group }
        if let user = number(custom["from_id"]), user > 0 { return user }
        return nil
    }

    /// Čísla chodí jako JSON čísla i jako řetězce.
    private static func number(_ value: Any?) -> Int64? {
        if let number = value as? NSNumber { return number.int64Value }
        if let text = value as? String { return Int64(text) }
        return nil
    }

    private static func isMessage(_ locKey: String) -> Bool {
        locKey == "MESSAGES" || ["MESSAGE_", "CHAT_MESSAGE", "CHANNEL_MESSAGE", "REACT_", "CHAT_REACT_", "PINNED_"]
            .contains { locKey.hasPrefix($0) }
    }

    /// Stejné id jako `NotificationCategories.chatCategory` v Kotlinu — kategorie registruje aplikace.
    private static func category(canReply: Bool, canMute: Bool) -> String {
        "plume.chat" + (canReply ? ".reply" : "") + (canMute ? ".mute" : "")
    }

    #if DEBUG
    /// Poslední push do kontejneru App Group (`push/last.json`), ať jde ověřit, co Telegram posílá.
    private func savePayloadForInspection(_ userInfo: [AnyHashable: Any]) {
        guard
            let group = Bundle.main.object(forInfoDictionaryKey: "PlumeAppGroup") as? String,
            let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group),
            JSONSerialization.isValidJSONObject(userInfo),
            let data = try? JSONSerialization.data(withJSONObject: userInfo, options: [.prettyPrinted, .sortedKeys])
        else { return }
        let directory = container.appendingPathComponent("push", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try? data.write(to: directory.appendingPathComponent("last.json"))
    }
    #endif
}
