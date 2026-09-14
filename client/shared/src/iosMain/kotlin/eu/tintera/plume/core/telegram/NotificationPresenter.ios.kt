package eu.tintera.plume.core.telegram

/**
 * iOS zatím notifikace nezobrazuje.
 *
 * Lokální notifikace z běžící aplikace by šly přes `UNUserNotificationCenter`, ale užitek je malý —
 * v popředí je uživatel v aplikaci a na pozadí iOS proces uspí, takže `UpdateNotificationGroup`
 * stejně nikdo nesbírá. Smysl dává až APNs push s notification service extension, která obsah
 * rozšifruje přes `processPushNotification`.
 */
actual class NotificationPresenter {
    actual fun start(repository: NotificationRepository) = Unit
}
