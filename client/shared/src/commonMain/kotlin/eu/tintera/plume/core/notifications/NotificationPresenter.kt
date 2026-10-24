package eu.tintera.plume.core.notifications

import eu.tintera.plume.core.telegram.NotificationRepository

/**
 * Zobrazuje notifikace v systému.
 *
 * Platformní část — Android posílá systémové notifikace, na iOS je to zatím no-op: notifikace
 * z běžící aplikace tam nemají velkou cenu a skutečná cesta vede přes APNs a notification
 * service extension, což je samostatný kus práce.
 */
expect class NotificationPresenter {


    /** Napojí se na [NotificationRepository] a začne zobrazovat. Volá se jednou při startu. */
    fun start(repository: NotificationRepository)
}
