package eu.tintera.plume.core.files

/** Co je v souboru poslaném „jako soubor" — podle toho se v bublině kreslí ikona a náhled. */
enum class DocumentKind { Image, Video, Other }

/**
 * Formát obrázku nebo videa, který umíme poznat.
 *
 * Seznam, ne prefix `image/` — TIFF, RAW nebo PSD jsou taky „obrázek", ale otevřít je neumíme,
 * a náhled s ikonou fotky by sliboval něco, co ťuknutí nesplní.
 */
enum class DocumentFormat(val kind: DocumentKind, val extensions: Set<String>, val mimeTypes: Set<String>) {
    Jpeg(DocumentKind.Image, setOf("jpg", "jpeg"), setOf("image/jpeg")),
    Png(DocumentKind.Image, setOf("png"), setOf("image/png")),
    Webp(DocumentKind.Image, setOf("webp"), setOf("image/webp")),
    Gif(DocumentKind.Image, setOf("gif"), setOf("image/gif")),
    Heic(DocumentKind.Image, setOf("heic", "heif"), setOf("image/heic", "image/heif")),
    Bmp(DocumentKind.Image, setOf("bmp"), setOf("image/bmp")),
    Mp4(DocumentKind.Video, setOf("mp4", "m4v"), setOf("video/mp4")),
    QuickTime(DocumentKind.Video, setOf("mov"), setOf("video/quicktime")),
    Webm(DocumentKind.Video, setOf("webm"), setOf("video/webm")),
    Matroska(DocumentKind.Video, setOf("mkv"), setOf("video/x-matroska")),
    ThreeGp(DocumentKind.Video, setOf("3gp"), setOf("video/3gpp")),
}

/**
 * Pozná formát souboru.
 *
 * `mimeType` posílá odesílatel a bývá prázdný nebo obecný (`application/octet-stream`), takže
 * přednost má přípona z názvu; typ je záloha pro soubor bez přípony nebo s příponou, kterou
 * neznáme.
 */
fun documentFormat(fileName: String, mimeType: String): DocumentFormat? {
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    if (extension.isNotEmpty()) {
        DocumentFormat.entries.firstOrNull { extension in it.extensions }?.let { return it }
    }
    val mime = mimeType.lowercase().substringBefore(';').trim()
    return DocumentFormat.entries.firstOrNull { mime in it.mimeTypes }
}

fun documentKind(fileName: String, mimeType: String): DocumentKind =
    documentFormat(fileName, mimeType)?.kind ?: DocumentKind.Other

/**
 * Otevře se soubor v prohlížeči médií, nebo ho předáme systému?
 *
 * Tady už rozhoduje platforma, ne jen formát: prohlížeč kreslí přes Coil a přehrává přes
 * ExoPlayer / AVPlayer a každá strana umí něco jiného (viz [viewerUnsupportedFormats]). Co
 * prohlížeč neotevře, jde systému — stejně jako soubor, který obrázkem ani videem není.
 */
fun opensInViewer(fileName: String, mimeType: String): Boolean {
    val format = documentFormat(fileName, mimeType) ?: return false
    return format !in viewerUnsupportedFormats()
}

/** Formáty, které prohlížeč médií na téhle platformě neotevře. */
internal expect fun viewerUnsupportedFormats(): Set<DocumentFormat>
