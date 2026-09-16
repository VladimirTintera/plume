package eu.tintera.plume.di

import eu.tintera.plume.core.notifications.NotificationPermission
import eu.tintera.plume.core.settings.SETTINGS_FILE_NAME
import eu.tintera.plume.core.settings.createSettingsDataStore
import eu.tintera.plume.core.telegram.applicationSupportDirectory
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

    // Application Support, stejně jako databáze TDLib: data aplikace, ne dokumenty uživatele.
    single { createSettingsDataStore(applicationSupportDirectory() + "/" + SETTINGS_FILE_NAME) }
}
