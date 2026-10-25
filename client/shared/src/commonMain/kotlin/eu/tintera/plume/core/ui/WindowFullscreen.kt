package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable

/**
 * Okno aplikace přes celou obrazovku (video v prohlížeči). Jen desktop: na telefonu je aplikace
 * přes celou obrazovku vždycky a místo toho se schovávají systémové lišty ([HideSystemBars]).
 */
interface WindowFullscreen {
    /** Okno je teď přes celou obrazovku — i když ho tam dal uživatel tlačítkem okna. */
    val active: Boolean

    /**
     * Přepne okno; po vypnutí se vrátí, jak bylo před zapnutím (maximalizované zůstane
     * maximalizované). Přechod trvá (na macOS animace do vlastní plochy), [active] se změní až po něm.
     */
    fun set(on: Boolean)
}

/** Celá obrazovka okna, nebo `null`, kde ji platforma nemá. */
@Composable
expect fun rememberWindowFullscreen(): WindowFullscreen?
