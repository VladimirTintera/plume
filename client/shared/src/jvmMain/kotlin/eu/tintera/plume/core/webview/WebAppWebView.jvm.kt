package eu.tintera.plume.core.webview

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Vestavěný prohlížeč pro mini aplikace desktop zatím nemá (JCEF by přidal ~100 MB). */
@Composable
actual fun WebAppWebView(
    url: String,
    profile: String,
    controller: WebAppViewController,
    backgroundColor: Int,
    onEvent: (type: String, data: String?) -> Unit,
    onPageLoaded: () -> Unit,
    onOpenExternal: (url: String) -> Unit,
    modifier: Modifier,
) = Unit
