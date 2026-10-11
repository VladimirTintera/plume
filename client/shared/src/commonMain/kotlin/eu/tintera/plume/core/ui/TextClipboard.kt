package eu.tintera.plume.core.ui

/**
 * Kopírování textu do systémové schránky.
 *
 * Vlastní `expect`, ne Compose API: `LocalClipboardManager` je zastaralý a jeho náhrada
 * `LocalClipboard` pracuje s `ClipEntry`, což je `expect class` **bez společné továrny na text** —
 * v commonMain se prostě nedá vyrobit. Tak je to stejně jako `UrlOpener` platformní třída v DI.
 */
expect class TextClipboard {
    /** `suspend`, protože popisek schránky se čte z prostředků. */
    suspend fun copy(text: String)

    /**
     * Systém zkopírování potvrdí sám (Android 13+: okénko s obsahem schránky vlevo dole).
     * Vlastní hláška „Zkopírováno" by se s ním pak zdvojila a překryla — ověřeno na emulátoru.
     */
    val confirmsCopy: Boolean
}
