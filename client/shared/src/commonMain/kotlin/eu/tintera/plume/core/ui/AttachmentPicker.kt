package eu.tintera.plume.core.ui

import androidx.compose.runtime.Composable
import eu.tintera.plume.core.telegram.OutgoingAttachment

/**
 * Výběr fotky nebo souboru k odeslání.
 *
 * Platforma vybere soubor systémovým výběrem (Android Photo Picker / výběr dokumentu, iOS PHPicker /
 * UIDocumentPicker) a **zkopíruje ho do cache aplikace** — TDLib posílá podle cesty a systémové URI
 * neotevře. Fotka se přitom zmenší a uloží jako JPEG (viz [PHOTO_MAX_EDGE]); soubor zůstane beze
 * změny. Žádné oprávnění to nepotřebuje: oba systémové výběry dávají přístup jen k tomu, co
 * uživatel vybral.
 */
interface AttachmentPicker {
    fun pickPhoto()
    fun pickDocument()
}

/**
 * [onPicked] dostane přílohu připravenou k odeslání, [onError] hlášku, když se soubor nepodařilo
 * připravit. Zrušení výběru nevolá nic.
 */
@Composable
expect fun rememberAttachmentPicker(
    onPicked: (OutgoingAttachment) -> Unit,
    onError: (String) -> Unit,
): AttachmentPicker

/**
 * Delší strana odesílané fotky.
 *
 * TDLib přijme fotku nejvýš 10 MB a do 10 000 px; fotka z foťáku bývá větší. Telegram ji stejně
 * zmenšuje na 2560 px, takže větší rozlišení by jen prodloužilo nahrávání.
 */
const val PHOTO_MAX_EDGE = 2560

/** Kvalita JPEG odesílané fotky. Pod ní jsou vidět artefakty, nad ní roste velikost bez přínosu. */
const val PHOTO_JPEG_QUALITY = 87

/** Podadresář cache pro připravené přílohy. */
const val OUTGOING_DIRECTORY = "outgoing"
