package eu.tintera.plume.core.di

import eu.tintera.plume.core.connectivity.AppForeground
import eu.tintera.plume.core.connectivity.NetworkMonitor
import eu.tintera.plume.core.secrets.DatabaseKeyStore
import eu.tintera.plume.core.secrets.SecretStore
import eu.tintera.plume.core.secrets.desktopSecretStore
import eu.tintera.plume.core.secrets.secretService
import eu.tintera.plume.core.telegram.TelegramPaths
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun corePlatformModule(): Module = module {
    single { TelegramPaths() }
    single<DatabaseKeyStore> {
        object : DatabaseKeyStore, SecretStore by desktopSecretStore(
            service = secretService("eu.tintera.plume.tdlib"), account = "database-key", logTag = "DatabaseKey",
        ) {}
    }
    single { NetworkMonitor() }
    single { AppForeground() }
}
