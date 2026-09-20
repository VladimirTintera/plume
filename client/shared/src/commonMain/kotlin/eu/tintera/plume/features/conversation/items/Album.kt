package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.core.ui.photoKey
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.features.conversation.MAX_PHOTO_HEIGHT
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.media_photo
import org.jetbrains.compose.resources.stringResource

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
    onRequestMenu: () -> Unit,
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
                        onRequestMenu = onRequestMenu,
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

    Column(
        // Zaoblení nese celá mřížka, ne jednotlivé dlaždice: uvnitř jsou jen tenké spáry.
        Modifier.clip(shape ?: RoundedCornerShape(12.dp)),
        verticalArrangement = Arrangement.spacedBy(ALBUM_GAP),
    ) {
        var start = 0
        albumRowSizes(members.size).forEach { size ->
            val rowMembers = members.subList(start, start + size)
            start += size

            BoxWithConstraints {
                // Jedna fotka na řádek podle svého poměru stran (se stropem), víc fotek čtverce —
                // řádek se tak nikdy nerozjede do nekonečné výšky u svislých snímků.
                val single = rowMembers.singleOrNull()
                val height = if (single != null) {
                    (maxWidth / single.media.albumRatio()).coerceAtMost(MAX_PHOTO_HEIGHT)
                } else {
                    ((maxWidth - ALBUM_GAP * (size - 1)) / size).coerceAtMost(ALBUM_TILE_MAX_HEIGHT)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(ALBUM_GAP)) {
                    rowMembers.forEach { member ->
                        AlbumTile(
                            member = member,
                            onOpen = { onOpen(member.id) },
                            onRequestMenu = onRequestMenu,
                            onCancelUpload = { onCancelUpload(member.id) },
                            modifier = Modifier.weight(1f).height(height),
                        )
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
    member: ChatMessage,
    onOpen: () -> Unit,
    onRequestMenu: () -> Unit,
    onCancelUpload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uploading = member.sendState == SendState.Sending

    when (val media = member.media) {
        is MessageMedia.Photo -> {
            var revealed by rememberSaveable(member.id) { mutableStateOf(!media.hasSpoiler) }
            val blurRadius by animateDpAsState(if (revealed) 0.dp else 24.dp)
            Box(
                modifier = modifier.combinedClickable(
                    onClick = {
                        if (media.hasSpoiler && !revealed) {
                            revealed = true
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
                    contentDescription = stringResource(Res.string.media_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .sharedNavElement(photoKey(member.id))
                        .blur(blurRadius),
                )
                if (media.hasSpoiler) {
                    SpoilerOverlay(visible = !revealed)
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
            var revealed by rememberSaveable(member.id) { mutableStateOf(!media.hasSpoiler) }
            VideoThumbnail(
                video = media,
                messageId = member.id,
                uploading = uploading,
                onCancelUpload = onCancelUpload,
                revealed = revealed,
                modifier = modifier.combinedClickable(
                    onClick = {
                        if (media.hasSpoiler && !revealed) {
                            revealed = true
                        } else {
                            onOpen()
                        }
                    },
                    onLongClick = onRequestMenu,
                ),
            )
        }

        is MessageMedia.Attachment -> Box(modifier.combinedClickable(onClick = {}, onLongClick = onRequestMenu)) {
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
                    text = media.label,
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
private val ALBUM_GAP = 2.dp

/** Strop výšky řádku alba s víc fotkami — dvě čtvercové fotky na širokém displeji by byly obří. */
private val ALBUM_TILE_MAX_HEIGHT = 200.dp
