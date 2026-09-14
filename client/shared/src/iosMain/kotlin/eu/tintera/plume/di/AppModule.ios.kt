package eu.tintera.plume.di

import eu.tintera.plume.core.telegram.TelegramPaths
import org.koin.core.module.Module

actual fun Module.platformModule() {
    single { TelegramPaths() }
}
