package eu.tintera.plume.core.webapp

import androidx.compose.runtime.Composable

/**
 * Zástupce mini aplikace na ploše (`addToHomeScreen`). Umí ho jen Android (připnutý zástupce
 * přes launcher); jinde je [supported] `false` a stránka dostane „nepodporováno".
 */
interface HomeScreenShortcuts {
    val supported: Boolean

    /** `added`, `missing`, nebo `unsupported` — tvar, jaký čeká `home_screen_checked`. */
    fun status(id: String): String

    /**
     * Požádá launcher o připnutí zástupce, který otevře [url] v Plume. Výsledek je jen
     * „žádost odešla" — jestli ji uživatel v launcheru potvrdil, systém neřekne.
     */
    fun add(id: String, label: String, url: String): Boolean
}

@Composable
expect fun rememberHomeScreenShortcuts(): HomeScreenShortcuts

internal object NoHomeScreenShortcuts : HomeScreenShortcuts {
    override val supported = false
    override fun status(id: String) = "unsupported"
    override fun add(id: String, label: String, url: String) = false
}
