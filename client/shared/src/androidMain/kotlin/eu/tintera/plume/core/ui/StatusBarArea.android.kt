package eu.tintera.plume.core.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.runtime.Composable

@OptIn(ExperimentalLayoutApi::class)
@Composable
actual fun statusBarArea(): WindowInsets = WindowInsets.statusBarsIgnoringVisibility
