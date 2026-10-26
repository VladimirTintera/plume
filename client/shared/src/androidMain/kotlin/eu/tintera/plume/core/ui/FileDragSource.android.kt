package eu.tintera.plume.core.ui

import androidx.compose.ui.Modifier

actual val fileDragSupported: Boolean = false

actual fun Modifier.fileDragSource(file: () -> DraggedFile?): Modifier = this
