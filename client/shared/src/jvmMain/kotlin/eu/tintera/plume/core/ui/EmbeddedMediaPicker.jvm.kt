package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import eu.tintera.plume.core.telegram.OutgoingAttachment

/** Vestavěná galerie je jen na Androidu; desktop vybírá přes dialog souborů ([rememberAttachmentPicker]). */
@Composable
actual fun embeddedMediaPickerAvailable(): Boolean = false

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
    onPicked: (List<OutgoingAttachment>) -> Unit,
    onUnpicked: (List<String>) -> Unit,
    onError: (UiText) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier,
) = Unit
