package eu.tintera.plume.core.telegram

/**
 * Desktop notifikace zatím nezobrazuje (viz docs/roadmap.md). TDLib je vydává jen za běhu
 * aplikace, takže až přibudou, půjdou přímo z [NotificationRepository] do systémových oznámení.
 */
actual class NotificationPresenter {
    actual fun start(repository: NotificationRepository) = Unit
}
