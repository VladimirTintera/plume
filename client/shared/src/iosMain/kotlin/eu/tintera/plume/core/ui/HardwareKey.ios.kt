package eu.tintera.plume.core.ui

import androidx.compose.ui.input.key.KeyEvent

/** Na iOS chodí jako klávesy jen fyzická klávesnice; softwarová píše přes vstup textu. */
actual val KeyEvent.isFromHardwareKeyboard: Boolean
    get() = true
