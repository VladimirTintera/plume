package eu.tintera.plume.core.files

/** Co je v souboru poslaném „jako soubor" — podle toho se v bublině kreslí ikona a náhled. */
enum class DocumentKind { Image, Video, Other }

/**
 * Pozná obrázek a video mezi soubory.
 *
 * `mimeType` posílá odesílatel a bývá prázdný nebo obecný (`application/octet-stream`), takže
 * přednost má přípona z názvu; typ je záloha pro soubor bez přípony. Obojí se porovnává se
 * seznamem formátů, ne s prefixem `image/` — TIFF, RAW nebo PSD jsou taky „obrázek", ale
 * otevřít je neumíme, a náhled s ikonou fotky by sliboval něco, co ťuknutí nesplní.
 */
fun documentKind(fileName: String, mimeType: String): DocumentKind {
    val extension = fileName.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    if (extension.isNotEmpty()) {
        when (extension) {
            in IMAGE_EXTENSIONS -> return DocumentKind.Image
            in VIDEO_EXTENSIONS -> return DocumentKind.Video
        }
    }
    return when (mimeType.lowercase().substringBefore(';').trim()) {
        in IMAGE_MIME_TYPES -> DocumentKind.Image
        in VIDEO_MIME_TYPES -> DocumentKind.Video
        else -> DocumentKind.Other
    }
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp")
private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mov", "webm", "mkv", "3gp")

private val IMAGE_MIME_TYPES = setOf(
    "image/jpeg", "image/png", "image/webp", "image/gif", "image/heic", "image/heif", "image/bmp",
)
private val VIDEO_MIME_TYPES = setOf(
    "video/mp4", "video/quicktime", "video/webm", "video/x-matroska", "video/3gpp",
)
