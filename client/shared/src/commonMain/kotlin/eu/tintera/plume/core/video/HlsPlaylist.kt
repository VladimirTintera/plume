package eu.tintera.plume.core.video

/**
 * Přepíše HLS seznam přepočítané kvality (`alternativeVideo.hlsFile`) tak, aby segmenty vedly na
 * [videoUri] — místní adresu, ze které se čte rozestavěný soubor té kvality.
 *
 * Server do seznamu píše místo adresy souboru `mtproto:<id>` (id dokumentu na serveru, ne
 * `fileId` z TDLib) a segmenty jsou rozsahy bajtů v něm (`#EXT-X-BYTERANGE`). Tak to čte
 * i oficiální klient pro iOS (`HLSServerSource.playlistData`). Seznam jedné kvality odkazuje na
 * jediný soubor; kdyby jich bylo víc, místní adresa by je nerozlišila — pak `null` a video se
 * nestreamuje. `null` taky, když seznam žádný segment nemá.
 */
fun rewriteHlsPlaylist(playlist: String, videoUri: String): String? {
    val ids = MTPROTO_URI.findAll(playlist).map { it.groupValues[1] }.toSet()
    if (ids.size != 1 || !playlist.trimStart().startsWith("#EXTM3U")) return null
    return MTPROTO_URI.replace(playlist, videoUri)
}

/**
 * `Range: bytes=a-b` (i `a-` a `-n`) jako rozsah v souboru o [total] bajtech; `null`, když
 * nejde splnit. Víc rozsahů v jednom požadavku AVPlayer neposílá.
 */
fun parseByteRange(header: String, total: Long): LongRange? {
    val spec = header.trim().removePrefix("bytes=").takeIf { it != header.trim() } ?: return null
    val start = spec.substringBefore('-').trim()
    val end = spec.substringAfter('-', "").trim()
    val range = when {
        start.isEmpty() -> {
            val suffix = end.toLongOrNull() ?: return null
            (total - suffix).coerceAtLeast(0) until total
        }
        else -> {
            val from = start.toLongOrNull() ?: return null
            val to = if (end.isEmpty()) total - 1 else end.toLongOrNull()?.coerceAtMost(total - 1) ?: return null
            from..to
        }
    }
    return range.takeIf { !it.isEmpty() && it.first < total }
}

private val MTPROTO_URI = Regex("mtproto:(\\d+)")
