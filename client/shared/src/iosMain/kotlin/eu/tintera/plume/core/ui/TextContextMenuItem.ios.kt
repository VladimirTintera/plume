package eu.tintera.plume.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.contextmenu.builder.TextContextMenuBuilderScope
import androidx.compose.foundation.text.contextmenu.builder.item
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession

// Na iOS je `item` zatím experimentální (Android ho má stabilní).
@OptIn(ExperimentalFoundationApi::class)
actual fun TextContextMenuBuilderScope.menuItem(
    key: Any,
    label: String,
    onClick: TextContextMenuSession.() -> Unit,
) = item(key = key, label = label, onClick = onClick)
