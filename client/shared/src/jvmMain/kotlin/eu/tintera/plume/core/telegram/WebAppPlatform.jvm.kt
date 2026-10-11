package eu.tintera.plume.core.telegram

// Hodnoty, které Telegram pro mini aplikace zná, jsou „macos", „tdesktop", „android", „ios"…
// Desktopový klient se hlásí jako „tdesktop" bez ohledu na systém.
internal actual val webAppPlatform: String = "tdesktop"
