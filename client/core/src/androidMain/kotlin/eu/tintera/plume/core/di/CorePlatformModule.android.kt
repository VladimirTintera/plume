package eu.tintera.plume.core.di

import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.accounts.ACCOUNTS_REGISTRY_FILE
import eu.tintera.plume.core.accounts.AccountRegistry
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
    single { NetworkMonitor(androidContext()) }
    single { AppForeground(androidContext()) }
    single { AccountRegistry(androidContext().filesDir.resolve(ACCOUNTS_REGISTRY_FILE).absolutePath) }
}

internal actual fun accountPlatformModule(slot: AccountSlot): Module = module {
    single { TelegramPaths(androidContext(), slot) }
    single<DatabaseKeyStore> {
        object : DatabaseKeyStore, SecretStore by KeystoreSecretStore(
            androidContext(),
            fileName = slot.secretName("tdlib-key", separator = "-"),
            keyAlias = slot.secretName("plume_tdlib_database"),
            logTag = "DatabaseKey",
        ) {}
    }
}
