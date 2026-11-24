package eu.tintera.plume.core.ui

import android.view.WindowManager
import androidx.compose.ui.window.PopupProperties

// Základ jako `PopupProperties(focusable = true)` (jen FLAG_WATCH_OUTSIDE_TOUCH), navíc
// FLAG_ALT_FOCUSABLE_IM.
actual val MenuPopupProperties: PopupProperties = PopupProperties(
    flags = WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
        WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
)

actual fun PopupProperties.overTitleBar(): PopupProperties = this
