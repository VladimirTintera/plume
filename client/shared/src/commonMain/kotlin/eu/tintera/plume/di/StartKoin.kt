package eu.tintera.plume.di

import org.koin.core.KoinApplication
import org.koin.core.context.startKoin

fun startKoin(configure: KoinApplication.() -> Unit = {}) = startKoin {
    configure()
    modules(appModule())
}
