package eu.tintera.plume.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import eu.tintera.plume.core.settings.DEFAULT_CHAT_PREVIEW_LINES
import eu.tintera.plume.core.settings.DEFAULT_STICKER_SIZE

/**
 * Řádky náhledu zprávy v seznamu chatů z Nastavení → Vzhled. Dodává je kořen aplikace.
 *
 * Statický jako [LocalMessageTextSize]: mění se jen v Nastavení, a pak má překreslit všechny řádky.
 */
val LocalChatPreviewLines = staticCompositionLocalOf { DEFAULT_CHAT_PREVIEW_LINES }

/** Mezery mezi řádky seznamu chatů z Nastavení → Vzhled. Dodává je kořen aplikace. */
val LocalChatRowGaps = staticCompositionLocalOf { true }

/**
 * Delší hrana samolepky ve zprávě v dp z Nastavení → Vzhled. Dodává ji kořen aplikace.
 *
 * Statický jako [LocalMessageTextSize]: samolepka je v bloku seznamu bez přístupu k DI.
 */
val LocalStickerSize = staticCompositionLocalOf { DEFAULT_STICKER_SIZE }
