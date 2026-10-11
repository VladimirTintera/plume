package eu.tintera.plume.core.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Platforma umí videosamolepky (`.webm`, VP9) přehrát. Bez toho se soubor ani nestahuje
 * a samolepka zůstane statickým náhledem.
 *
 * Android: dva VP9 dekodéry `MediaCodec` a průhlednost složená z druhého streamu (viz
 * [WebmVideo]). iOS WebM neumí vůbec (AVFoundation ani VideoToolbox kontejner nepřečte) a desktop
 * přehrávač nemá — tam `false`.
 */
expect val webmStickersSupported: Boolean

/**
 * Videosamolepka ze souboru [path] ve smyčce. Dokud se první snímek nenakreslí (a když se
 * soubor přečíst nebo dekódovat nepodaří, nebo hraje příliš mnoho videosamolepek naráz), kreslí
 * se [placeholder] — statický náhled, který odpovídá prvnímu snímku.
 *
 * S [playing] `false` stojí na prvním snímku. Dekóduje se jen v rozlišení, ve kterém se
 * samolepka kreslí, ne v celých 512 × 512.
 */
@Composable
expect fun WebmSticker(
    path: String,
    playing: Boolean,
    contentDescription: String?,
    modifier: Modifier,
    placeholder: @Composable () -> Unit,
)
