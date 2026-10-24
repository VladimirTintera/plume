package eu.tintera.plume.core.di

import eu.tintera.plume.core.connectivity.AppForeground
import eu.tintera.plume.core.connectivity.NetworkMonitor
import eu.tintera.plume.core.secrets.DatabaseKeyStore
import eu.tintera.plume.core.secrets.KeychainSecretStore
import eu.tintera.plume.core.secrets.SecretStore
import eu.tintera.plume.core.telegram.TelegramPaths
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun corePlatformModule(): Module = module {
    single { TelegramPaths() }
    single<DatabaseKeyStore> {
        object : DatabaseKeyStore, SecretStore by KeychainSecretStore(
            service = "eu.tintera.plume.tdlib", account = "database-key", logTag = "DatabaseKey",
        ) {}
    }
    single { NetworkMonitor() }
    single { AppForeground() }
}
