package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.window.PopupProperties

// Drobné platformní kousky UI, které na desktopu nemají co dělat nebo platí automaticky.

/** Okno nabídky klávesnici brát nesmí, stejně jako na ostatních platformách (viz [MenuPopupProperties]). */
actual val MenuPopupProperties: PopupProperties = PopupProperties(focusable = true)

/** Na desktopu jiná klávesnice než fyzická není. */
actual val KeyEvent.isFromHardwareKeyboard: Boolean
    get() = true

@Composable
actual fun ReportDrawnWhen(ready: () -> Boolean) = Unit

/** Snímkům obrazovky desktop zabránit neumí. */
@Composable
actual fun SecureScreen(enabled: Boolean) = Unit

/** Přepínač aplikací na desktopu snímek okna neukazuje, není co krýt. */
@Composable
actual fun PrivacyCover(enabled: Boolean, color: Color) = Unit

/** Volbu „omezit průhlednost" desktop aplikaci nehlásí; Haze rozostřuje přes Skiu vždy. */
@Composable
actual fun rememberBackgroundBlurAllowed(): Boolean = true
