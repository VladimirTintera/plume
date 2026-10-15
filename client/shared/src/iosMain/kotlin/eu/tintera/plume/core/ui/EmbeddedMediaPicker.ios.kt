package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.tintera.plume.core.telegram.OutgoingAttachment

/**
 * iOS vestavěný výběr fotek nemá.
 *
 * `PHPickerViewController` se z principu ukazuje jako samostatná obrazovka a obdoba vloženého
 * pickeru se soukromím systémového výběru tu není: vlastní galerie by znamenala oprávnění
 * k celé knihovně fotek, tedy přesně to, čemu se `PHPicker` vyhýbá. Zůstává systémový výběr
 * ([rememberAttachmentPicker]).
 */
@Composable
actual fun embeddedMediaPickerAvailable(): Boolean = false

/** Paměť výběru bez výběru: obsah nemá odkud vzniknout. */
private object NoSelection : EmbeddedMediaSelection {
    override val preparing = false
}

@Composable
actual fun rememberEmbeddedMediaSelection(): EmbeddedMediaSelection = NoSelection

@Composable
actual fun EmbeddedMediaPicker(
    selection: EmbeddedMediaSelection,
    pickedPaths: List<String>,
    sources: Map<String, String>,
    allowPhotos: Boolean,
    allowVideos: Boolean,
    expanded: Boolean,
    single: Boolean,
    onPicked: (List<OutgoingAttachment>) -> Unit,
    onUnpicked: (List<String>) -> Unit,
    onError: (UiText) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier,
) = Unit
