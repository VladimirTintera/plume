package eu.tintera.plume.core.di

import android.content.Context
import eu.tintera.plume.core.accounts.AccountSlot
import eu.tintera.plume.core.connectivity.AppForeground
import eu.tintera.plume.core.connectivity.NetworkMonitor
import eu.tintera.plume.core.secrets.DatabaseKeyStore
import eu.tintera.plume.core.secrets.KeystoreSecretStore
import eu.tintera.plume.core.secrets.SecretStore
import eu.tintera.plume.core.telegram.TelegramPaths
import org.koin.android.ext.koin.androidContext
import org.koin.core.Koin
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun corePlatformModule(): Module = module {
    single { NetworkMonitor(androidContext()) }
    single { AppForeground(androidContext()) }
}

/** `Context` z globálního Koinu: izolovaný kontejner účtu `androidContext()` nemá. */
internal actual fun accountPlatformModule(slot: AccountSlot, global: Koin): Module = module {
    single { TelegramPaths(global.get<Context>(), slot) }
    single<DatabaseKeyStore> {
        object : DatabaseKeyStore, SecretStore by KeystoreSecretStore(
            global.get<Context>(),
            fileName = slot.secretName("tdlib-key", separator = "-"),
            keyAlias = slot.secretName("plume_tdlib_database"),
            logTag = "DatabaseKey",
        ) {}
    }
}
