package eu.tintera.plume.core.telegram

/**
 * Náhledy snímků videa pro posuvník (`VideoStoryboard` z TDLib).
 *
 * [imageFileId] je JPEG s dlaždicemi [tileWidth] × [tileHeight] px, [mapFileId] textový soubor,
 * který říká, od kterého času platí která dlaždice (`parseStoryboardMap` v `core/video`). Server
 * je vyrábí sám, jen u delších videí.
 */
data class VideoStoryboard(
    val imageFileId: Int,
    val mapFileId: Int,
    val tileWidth: Int,
    val tileHeight: Int,
)
