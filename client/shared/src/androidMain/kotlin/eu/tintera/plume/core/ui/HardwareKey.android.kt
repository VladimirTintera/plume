package eu.tintera.plume.core.ui

import android.view.KeyCharacterMap
import androidx.compose.ui.input.key.KeyEvent

/**
 * Softwarová klávesnice značí své klávesy `FLAG_SOFT_KEYBOARD` a posílá je z virtuálního
 * zařízení (`VIRTUAL_KEYBOARD`, totéž dělá `adb shell input`). Skutečnou klávesnici na
 * emulátoru dodá `adb emu event send EV_KEY:KEY_ENTER:1`.
 */
actual val KeyEvent.isFromHardwareKeyboard: Boolean
    get() = (nativeKeyEvent.flags and android.view.KeyEvent.FLAG_SOFT_KEYBOARD) == 0 &&
        nativeKeyEvent.deviceId != KeyCharacterMap.VIRTUAL_KEYBOARD
