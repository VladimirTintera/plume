package eu.tintera.plume.core.files

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/**
 * Jméno, pod kterým odejde vybrané nebo sdílené video. TDLib ho bere z cesty nahrávaného souboru
 * a příjemce ho uvidí při uložení a sdílení (`savedFileName`).
 *
 * Jméno od poskytovatele ([providedName], `OpenableColumns.DISPLAY_NAME`) zůstává, pokud něco
 * říká. **Google Fotky ale původní jméno neprozradí**: místo `PXL_20261125_192900.mp4` hlásí
 * číslo položky v `MediaStore` (`2143.mp4`). Číselné jméno (a žádné) se proto nahradí datem
 * natočení z hlavičky videa ([recordedAt], `METADATA_KEY_DATE`), stejně jako u videa
 * z vlastní kamery (`VID_20261125_192900.mp4`, místní čas). Bez data zůstane, co přišlo.
 */
fun outgoingVideoName(providedName: String?, recordedAt: String?, zone: TimeZone): String {
    val name = providedName?.trim()?.takeIf { it.isNotEmpty() }
    val stem = name?.substringBeforeLast('.')
    val opaque = stem == null || stem.all { it.isDigit() }
    if (!opaque && name != null) return name
    val recorded = parseRecordingDate(recordedAt, zone) ?: return name ?: FALLBACK_VIDEO_NAME
    val extension = name?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() } ?: "mp4"
    fun Int.pad() = toString().padStart(2, '0')
    val stamp = "${recorded.year}${recorded.month.number.pad()}${recorded.day.pad()}_" +
        "${recorded.hour.pad()}${recorded.minute.pad()}${recorded.second.pad()}"
    return "VID_$stamp.$extension"
}

/**
 * Datum natočení z `MediaMetadataRetriever.METADATA_KEY_DATE` (`20261125T182900.000Z`, UTC)
 * v místním čase. Video bez data nese počátek epochy QuickTime (`19040101T000000.000Z`),
 * jiné nulu nebo nic; vše před rokem 1971 se bere jako „datum neznámé".
 */
internal fun parseRecordingDate(recordedAt: String?, zone: TimeZone): LocalDateTime? {
    val match = recordedAt?.let(RECORDING_DATE::find) ?: return null
    val (year, month, day, hour, minute, second) = match.destructured
    val utc = runCatching {
        LocalDateTime(year.toInt(), month.toInt(), day.toInt(), hour.toInt(), minute.toInt(), second.toInt())
    }.getOrNull() ?: return null
    if (utc.year < MIN_RECORDING_YEAR) return null
    return utc.toInstant(TimeZone.UTC).toLocalDateTime(zone)
}

private val RECORDING_DATE = Regex("""^(\d{4})(\d{2})(\d{2})T(\d{2})(\d{2})(\d{2})""")
private const val MIN_RECORDING_YEAR = 1971
private const val FALLBACK_VIDEO_NAME = "video.mp4"
