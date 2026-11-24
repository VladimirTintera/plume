package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.window.PopupProperties

// Drobné platformní kousky UI, které na desktopu nemají co dělat nebo platí automaticky.

/**
 * Okno nabídky klávesnici brát nesmí, stejně jako na ostatních platformách (viz [MenuPopupProperties]).
 *
 * **Bez insetů okna** (`usePlatformInsets = false`): s vlastním titulkem je titulek horní inset
 * (`DesktopTitleBar`) a popup s insety bere za horní kraj okna až spodek titulku. M3 k tomu drží
 * nabídku 48 dp od kraje (`verticalMargin` v `DropdownMenuPositionProvider`), takže nabídka
 * od kotvy v řádku titulku (účet, ⋮ v hlavičce konverzace) nezačínala pod kotvou, ale až
 * 64 + 48 dp od horního kraje okna, o šedesát bodů níž. Titulek nabídce nepřekáží: semafory
 * končí v půlce jeho výšky a 48 dp od kraje okna nabídku drží i bez insetů.
 */
actual val MenuPopupProperties: PopupProperties = PopupProperties(focusable = true, usePlatformInsets = false)

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
