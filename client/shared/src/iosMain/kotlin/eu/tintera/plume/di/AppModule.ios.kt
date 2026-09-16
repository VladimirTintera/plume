package eu.tintera.plume.di

import eu.tintera.plume.core.notifications.NotificationPermission
import eu.tintera.plume.core.telegram.NotificationPresenter
import eu.tintera.plume.core.telegram.TelegramPaths
import eu.tintera.plume.core.ui.UrlOpener
import eu.tintera.plume.core.ui.TextClipboard
import org.koin.core.module.Module

actual fun Module.platformModule() {
    single { TelegramPaths() }
    single { NotificationPermission() }
    single { NotificationPresenter() }
    single { UrlOpener() }
    single { TextClipboard() }
}
