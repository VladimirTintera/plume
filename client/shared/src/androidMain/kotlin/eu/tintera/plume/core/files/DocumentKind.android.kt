package eu.tintera.plume.core.files

import android.os.Build

/**
 * HEIC dekóduje až `ImageDecoder` (Android 9); na starších ho Coil nepřečte. Video přehrává
 * ExoPlayer, který zvládne všechny kontejnery ze seznamu včetně WebM a Matroska.
 */
internal actual fun viewerUnsupportedFormats(): Set<DocumentFormat> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) emptySet() else setOf(DocumentFormat.Heic)
