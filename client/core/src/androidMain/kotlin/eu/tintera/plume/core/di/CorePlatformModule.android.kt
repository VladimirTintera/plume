package eu.tintera.plume.core.di

import eu.tintera.plume.core.connectivity.AppForeground
import eu.tintera.plume.core.connectivity.NetworkMonitor
import eu.tintera.plume.core.secrets.DatabaseKeyStore
import eu.tintera.plume.core.secrets.KeystoreSecretStore
import eu.tintera.plume.core.secrets.SecretStore
import eu.tintera.plume.core.telegram.TelegramPaths
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun corePlatformModule(): Module = module {
    single { TelegramPaths(androidContext()) }
    single<DatabaseKeyStore> {
        object : DatabaseKeyStore, SecretStore by KeystoreSecretStore(
            androidContext(), fileName = "tdlib-key", keyAlias = "plume_tdlib_database", logTag = "DatabaseKey",
        ) {}
    }
    single { NetworkMonitor(androidContext()) }
    single { AppForeground(androidContext()) }
}
