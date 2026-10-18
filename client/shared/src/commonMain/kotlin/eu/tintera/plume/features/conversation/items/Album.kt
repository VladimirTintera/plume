package eu.tintera.plume.features.conversation.items

import eu.tintera.plume.features.media.LocalPhotoDescriptions
import eu.tintera.plume.core.ui.LocalAutoDownload
import androidx.compose.runtime.CompositionLocalProvider
import eu.tintera.plume.core.telegram.NO_ARTICLE_MEDIA
import eu.tintera.plume.core.ui.LocalArticleMediaFlight
import eu.tintera.plume.core.ui.resolve
import androidx.compose.foundation.ExperimentalFoundationApi
import eu.tintera.plume.core.ui.contextClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.RevealedSpoilers
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.core.ui.photoKey
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.features.conversation.MAX_PHOTO_HEIGHT
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.media_photo
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

/**
 * Obsah alba v bublině.
 *
 * Fotky (a videa s náhledem) jako **mřížka** — celé album je vidět najednou, jako v ostatních
 * klientech. Carousel by část alba schoval a jeho vodorovný tah by se pral se swipem na odpověď.
 * Album dokumentů nebo audia (ta se nemíchají s fotkami) je seznam příloh pod sebou.
 */
@Composable
internal fun AlbumContent(
    members: List<ChatMessage>,
    onOpen: (messageId: Long) -> Unit,
    /** Dlouhý stisk na položce alba; nese její id, odpověď z nabídky míří na ni. */
    onRequestMenu: (messageId: Long) -> Unit,
    onCancelUpload: (messageId: Long) -> Unit,
    documentProgress: (fileId: Int) -> Float?,
    onOpenDocument: (messageId: Long) -> Unit,
    accent: Color,
    /** Tvar mřížky, když se album kreslí bez bubliny; `null` znamená album uvnitř bubliny. */
    shape: Shape? = null,
) {
    val visual = members.all { it.media.isAlbumTile() }
    if (!visual) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            members.forEach { member ->
                member.media?.let { media ->
                    MessageAttachment(
                        media = media,
                        messageId = member.id,
                        onOpen = { onOpen(member.id) },
                        onRequestMenu = { onRequestMenu(member.id) },
                        uploading = member.sendState == SendState.Sending,
                        onCancelUpload = { onCancelUpload(member.id) },
                        documentProgress = documentProgress,
                        onOpenDocument = { onOpenDocument(member.id) },
                        accent = accent,
                    )
                }
            }
        }
        return
    }

    AlbumGrid(
        cells = members.map { AlbumCell(it.id, it.media, uploading = it.sendState == SendState.Sending) },
        onOpen = { onOpen(it.messageId) },
        onRequestMenu = { onRequestMenu(it.messageId) },
        onCancelUpload = { onCancelUpload(it.messageId) },
        shape = shape,
    )
}

/**
 * Dlaždice mřížky. Obvykle jedna zpráva alba; u koláže v článku je to médium téže zprávy
 * a [articleIndex] ho odliší v klíči letu do prohlížeče (viz `photoKey`).
 */
internal data class AlbumCell(
    val messageId: Long,
    val media: MessageMedia?,
    val uploading: Boolean = false,
    val articleIndex: Int = NO_ARTICLE_MEDIA,
)

/** Patří všechny dlaždice do mřížky? Jinak se kreslí pod sebou (soubory, audio). */
internal fun List<AlbumCell>.allTiles(): Boolean = all { it.media.isAlbumTile() }

/**
 * Mřížka fotek a videí: řádky po 1–3 ([albumRowSizes]), zaoblení nese celá mřížka. Sdílí ji album
 * (víc zpráv) i koláž v článku (víc médií jedné zprávy).
 */
@Composable
internal fun AlbumGrid(
    cells: List<AlbumCell>,
    onOpen: (AlbumCell) -> Unit,
    onRequestMenu: (AlbumCell) -> Unit,
    onCancelUpload: (AlbumCell) -> Unit,
    /** Tvar mřížky, když se kreslí bez bubliny; `null` znamená mřížku uvnitř bubliny. */
    shape: Shape? = null,
) {
    // Zaoblení nese celá mřížka, ne jednotlivé dlaždice: uvnitř jsou jen tenké spáry.
    val gridShape = shape ?: RoundedCornerShape(12.dp)
    val rowSizes = albumRowSizes(cells.size)
    Column(
        Modifier.clip(gridShape),
        verticalArrangement = Arrangement.spacedBy(ALBUM_GAP),
    ) {
        var start = 0
        rowSizes.forEachIndexed { rowIndex, size ->
            val rowCells = cells.subList(start, start + size)
            start += size

            BoxWithConstraints {
                // Jedna fotka na řádek podle svého poměru stran (se stropem), víc fotek čtverce —
                // řádek se tak nikdy nerozjede do nekonečné výšky u svislých snímků.
                val single = rowCells.singleOrNull()
                val height = if (single != null) {
                    (maxWidth / single.media.albumRatio()).coerceAtMost(MAX_PHOTO_HEIGHT)
                } else {
                    ((maxWidth - ALBUM_GAP * (size - 1)) / size).coerceAtMost(ALBUM_TILE_MAX_HEIGHT)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(ALBUM_GAP)) {
                    rowCells.forEachIndexed { index, cell ->
                        CompositionLocalProvider(LocalArticleMediaFlight provides cell.articleIndex) {
                            AlbumTile(
                                cell = cell,
                                // Tvar dlaždice letí s fotkou do prohlížeče a zpátky; ořez mřížky
                                // se v překryvu neuplatní (viz `sharedNavElement`).
                                shape = albumTileShape(
                                    grid = gridShape,
                                    top = rowIndex == 0,
                                    bottom = rowIndex == rowSizes.lastIndex,
                                    start = index == 0,
                                    end = index == rowCells.lastIndex,
                                ),
                                onOpen = { onOpen(cell) },
                                onRequestMenu = { onRequestMenu(cell) },
                                onCancelUpload = { onCancelUpload(cell) },
                                modifier = Modifier.weight(1f).height(height),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dlaždice alba. Fotka jde do prohlížeče se sdíleným prvkem, stejně jako samostatná fotka; video
 * zatím jen ukazuje náhled a popisek, přehrávat neumíme.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun AlbumTile(
    cell: AlbumCell,
    shape: Shape,
    onOpen: () -> Unit,
    onRequestMenu: () -> Unit,
    onCancelUpload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uploading = cell.uploading
    val messageId = cell.messageId

    when (val media = cell.media) {
        is MessageMedia.Photo -> {
            val revealedSpoilers = koinInject<RevealedSpoilers>()
            val revealedIds by revealedSpoilers.revealed.collectAsState()
            val revealed = !media.hasSpoiler || messageId in revealedIds
            val spoilerStyle = rememberSpoilerBlurStyle()
            Box(
                modifier = modifier.contextClickable(
                    onClick = {
                        if (media.hasSpoiler && !revealed) {
                            revealedSpoilers.reveal(messageId)
                        } else {
                            onOpen()
                        }
                    },
                    onLongClick = onRequestMenu,
                ),
            ) {
                RemoteImage(
                    fileId = media.fileId,
                    thumbnailFileId = media.thumbnailFileId,
                    placeholder = media.minithumbnail,
                    autoDownload = LocalAutoDownload.current.photos,
                    contentDescription = LocalPhotoDescriptions.current[messageId] ?: stringResource(Res.string.media_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .sharedNavElement(photoKey(messageId), shape = shape, besidePanes = true)
                        .spoilerBlur(media.hasSpoiler, revealed, spoilerStyle),
                )
                if (media.hasSpoiler) {
                    SpoilerOverlay(visible = !revealed, sensitive = media.isSensitive)
                }
                // Každá dlaždice nahrává sama za sebe, takže má i vlastní zrušení.
                if (uploading) {
                    UploadIndicator(
                        fileId = media.fileId,
                        onCancel = onCancelUpload,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }

        is MessageMedia.Video -> {
            val revealedSpoilers = koinInject<RevealedSpoilers>()
            val revealedIds by revealedSpoilers.revealed.collectAsState()
            val revealed = !media.hasSpoiler || messageId in revealedIds
            VideoThumbnail(
                video = media,
                messageId = messageId,
                uploading = uploading,
                onCancelUpload = onCancelUpload,
                revealed = revealed,
                shape = shape,
                modifier = modifier.contextClickable(
                    onClick = {
                        if (media.hasSpoiler && !revealed) {
                            revealedSpoilers.reveal(messageId)
                        } else {
                            onOpen()
                        }
                    },
                    onLongClick = onRequestMenu,
                ),
            )
        }

        is MessageMedia.Attachment -> Box(modifier.contextClickable(onClick = {}, onLongClick = onRequestMenu)) {
            val uploadingFile = media.fileId?.takeIf { uploading }
            media.thumbnailFileId?.let { thumbnail ->
                RemoteImage(
                    fileId = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            ) {
                Text(
                    text = media.label.resolve(),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            if (uploadingFile != null) {
                UploadIndicator(
                    fileId = uploadingFile,
                    onCancel = onCancelUpload,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        else -> Box(modifier)
    }
}

/** Patří příloha do mřížky? Fotka, nebo video či GIF s náhledem; dokumenty a audio ne. */
private fun MessageMedia?.isAlbumTile(): Boolean = when (this) {
    is MessageMedia.Photo -> true
    is MessageMedia.Video -> true
    is MessageMedia.Attachment -> thumbnailFileId != null
    else -> false
}

/** Poměr stran dlaždice samotné na řádku. Video bez rozměrů je 16:9, neznámá fotka čtverec. */
internal fun MessageMedia?.albumRatio(): Float = when (this) {
    is MessageMedia.Photo -> if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 1f
    is MessageMedia.Video -> if (width > 0 && height > 0) width.toFloat() / height.toFloat() else 16f / 9f
    else -> 16f / 9f
}

/**
 * Kolik fotek jde do kterého řádku mřížky, shora dolů.
 *
 * Pravidla místo výpočtu mozaiky podle poměrů stran: řádky po 2–3 vypadají vyrovnaně bez ohledu na
 * to, co je na fotkách, a jedna velká fotka nahoře jen u lichých malých alb, kde by jinak jedna
 * zbyla sama dole. Telegram dovolí nejvýš 10 zpráv v albu.
 */
internal fun albumRowSizes(count: Int): List<Int> = when (count) {
    0 -> emptyList()
    1 -> listOf(1)
    2 -> listOf(2)
    3 -> listOf(1, 2)
    4 -> listOf(2, 2)
    5 -> listOf(2, 3)
    6 -> listOf(3, 3)
    7 -> listOf(2, 2, 3)
    8 -> listOf(2, 3, 3)
    9 -> listOf(3, 3, 3)
    else -> {
        // Nad deset (ze serveru nepřijde, ale nerozbije se to): řádky po třech, zbytek do prvního.
        val rest = count % 3
        (if (rest == 0) emptyList() else listOf(rest)) + List(count / 3) { 3 }
    }
}

/** Spára mezi fotkami alba. */
internal val ALBUM_GAP = 2.dp

/** Strop výšky řádku alba s víc fotkami — dvě čtvercové fotky na širokém displeji by byly obří. */
internal val ALBUM_TILE_MAX_HEIGHT = 200.dp

/**
 * Tvar dlaždice alba: rohy mřížky tam, kde dlaždice sahá do rohu, jinak rovné hrany.
 *
 * V albu ořezává mřížka, ne dlaždice — jenže sdílený prvek se během letu kreslí mimo rodiče
 * a bez vlastního tvaru se fotka z rohu mřížky vracela z prohlížeče hranatá.
 */
internal fun albumTileShape(grid: Shape, top: Boolean, bottom: Boolean, start: Boolean, end: Boolean): Shape {
    if (grid !is CornerBasedShape) return RectangleShape
    val none = CornerSize(0)
    return grid.copy(
        topStart = if (top && start) grid.topStart else none,
        topEnd = if (top && end) grid.topEnd else none,
        bottomEnd = if (bottom && end) grid.bottomEnd else none,
        bottomStart = if (bottom && start) grid.bottomStart else none,
    )
}
