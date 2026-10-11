package eu.tintera.plume.core.share

import androidx.compose.runtime.Composable

/** Sdílení z jiné aplikace na desktop nechodí, není kam se vracet. */
@Composable
actual fun rememberReturnToCaller(): () -> Unit = {}
