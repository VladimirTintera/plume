package eu.tintera.plume

import androidx.compose.ui.window.ComposeUIViewController
import eu.tintera.plume.di.startKoin

fun MainViewController() = ComposeUIViewController { App() }

/** Volá se z iOSApp.swift při startu — Koin musí běžet dřív, než se složí první obrazovka. */
fun initKoin() {
    startKoin { }
}
