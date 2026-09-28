package eu.tintera.plume.core.ui

import androidx.compose.foundation.text.contextmenu.builder.TextContextMenuBuilderScope
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession

/**
 * Položka kontextové nabídky textu. `item` z Compose NENÍ ve společném kódu, jen v platformních
 * sadách (Android bere navíc ikonu jako drawable), takže tenhle obal nabídku sestavenou ve
 * společném kódu propojí s platformou.
 */
expect fun TextContextMenuBuilderScope.menuItem(
    key: Any,
    label: String,
    onClick: TextContextMenuSession.() -> Unit,
)
