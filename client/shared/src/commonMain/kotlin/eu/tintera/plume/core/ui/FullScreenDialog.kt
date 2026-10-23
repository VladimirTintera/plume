package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable

/**
 * Dialog přes celé okno i pod systémové lišty, na tmavém pozadí (prohlížeč ukázek mini
 * aplikace). Obsah si insety bere sám (`safeDrawing`). Společné `DialogProperties` to neumí:
 * Android na to má `decorFitsSystemWindows`, iOS `usePlatformInsets` — jako u zámku aplikace.
 */
@Composable
expect fun FullScreenDialog(onDismiss: () -> Unit, content: @Composable () -> Unit)
