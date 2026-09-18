package eu.tintera.plume.core.files

/**
 * Coil kreslí mimo Android přes Skiu, která HEIC nedekóduje (to umí jen systémové kodeky), a
 * AVPlayer nepřehraje WebM ani Matroska. Takový soubor jde do `UIDocumentInteractionController`,
 * který si s HEIC poradí.
 */
internal actual fun viewerUnsupportedFormats(): Set<DocumentFormat> =
    setOf(DocumentFormat.Heic, DocumentFormat.Webm, DocumentFormat.Matroska)
