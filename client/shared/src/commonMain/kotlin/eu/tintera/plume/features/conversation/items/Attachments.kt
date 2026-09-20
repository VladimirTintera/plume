package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.koin.compose.koinInject
import eu.tintera.plume.core.telegram.RevealedSpoilers
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.files.DocumentKind
import eu.tintera.plume.core.files.FileSizeUnit
import eu.tintera.plume.core.files.documentKind
import eu.tintera.plume.core.files.formatFileSize
import eu.tintera.plume.core.files.opensInViewer
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.AnimatedSticker
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.core.ui.photoKey
import eu.tintera.plume.core.ui.rememberUploadProgress
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.features.conversation.VoiceNoteContent
import eu.tintera.plume.features.conversation.formatDuration
import eu.tintera.plume.features.conversation.photoFrame
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.conversation_cancel_sending
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.image_24dp
import eu.tintera.plume.resources.document_open
import eu.tintera.plume.resources.file_size_b
import eu.tintera.plume.resources.file_size_gb
import eu.tintera.plume.resources.file_size_kb
import eu.tintera.plume.resources.file_size_mb
import eu.tintera.plume.resources.media_photo
import eu.tintera.plume.resources.media_sticker
import eu.tintera.plume.resources.media_video
import eu.tintera.plume.resources.play_arrow_24dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Příloha uvnitř bubliny.
 *
 * Fotka si drží **poměr stran z metadat zprávy**, ne z načteného obrázku — díky tomu má bublina
 * správnou výšku od začátku a seznam nepodskočí, až se obrázek dotáhne.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun MessageAttachment(
    media: MessageMedia,
    messageId: Long,
    onOpen: () -> Unit,
    onRequestMenu: () -> Unit,
    /** Odesílaná zpráva: přes přílohu se kreslí průběh nahrávání. */
    uploading: Boolean = false,
    onCancelUpload: () -> Unit = {},
    voice: VoiceHandlers = VoiceHandlers.None,
    /** Průběh stahování souboru, který uživatel otevírá; `null`, když se nestahuje. */
    documentProgress: (fileId: Int) -> Float? = { null },
    onOpenDocument: () -> Unit = {},
    /** Barva popředí bubliny — v odchozí `primary` bublině nesmí být `primary`. */
    accent: Color = Color.Unspecified,
    /**
     * Tvar fotky a videa, když se kreslí bez bubliny — přebírá zaoblení bubliny, aby snímek
     * navázal na sousedy i na popisek pod sebou. `null` znamená obrázek uvnitř bubliny.
     */
    mediaShape: Shape? = null,
    /** Fotka a video využijí celou nabízenou šířku (u fotky s popiskem, aby se popisek nelámal). */
    fillWidth: Boolean = false,
) {
    val shape = RoundedCornerShape(12.dp)
    // Náhled souboru zůstává malý čtvereček bez ohledu na tvar bubliny — velké zaoblení bubliny
    // by z 48dp náhledu udělalo kolečko.
    val pictureShape = mediaShape ?: shape

    when (media) {
        is MessageMedia.Photo -> {
            // Rámeček i jeho pravidla jsou v `photoFrame` (s testy): fotka se zužuje, neořezává.
            BoxWithConstraints {
                val frame = photoFrame(maxWidth, media.width.toFloat() / media.height.toFloat(), fillWidth)
                val revealedSpoilers = koinInject<RevealedSpoilers>()
                val revealedIds by revealedSpoilers.revealed.collectAsState()
                val revealed = !media.hasSpoiler || messageId in revealedIds
                val spoilerStyle = rememberSpoilerBlurStyle()

                Box(
                    modifier = Modifier
                        .size(frame)
                        .clip(pictureShape)
                        .sharedNavElement(photoKey(messageId))
                        /*
                         * Gesta patří FOTCE, ne celé bublině.
                         *
                         * Kdyby ťuknutí odchytávala bublina, sebralo by ho i odkazům v popisku.
                         * Dlouhý stisk se musí zopakovat tady: potomek s vlastním klikáním ho
                         * nepustí k rodiči, takže by nad fotkou nešla otevřít nabídka.
                         */
                        .combinedClickable(
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
                        contentDescription = stringResource(Res.string.media_photo),
                        // `Crop` dořeší podlahu šířky (extrémně vysoká fotka, fotka s popiskem):
                        // poměr stran zůstává a vidět je prostředek. Jinak není co ořezávat.
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .spoilerBlur(media.hasSpoiler, revealed, spoilerStyle),
                    )

                    if (media.hasSpoiler) {
                        SpoilerOverlay(visible = !revealed)
                    }

                    if (uploading) {
                        UploadIndicator(
                            fileId = media.fileId,
                            onCancel = onCancelUpload,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }
        }

        is MessageMedia.Attachment -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val uploadingFile = media.fileId?.takeIf { uploading }
            if (uploadingFile != null) {
                // Místo náhledu průběh: u odesílaného souboru je to jediná informace, která se mění.
                UploadIndicator(fileId = uploadingFile, onCancel = onCancelUpload)
            } else {
                media.thumbnailFileId?.let { thumbnail ->
                    RemoteImage(
                        fileId = thumbnail,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(shape),
                    )
                }
            }
            Text(
                text = media.label,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        is MessageMedia.Sticker -> StickerImage(
            sticker = media,
            /*
             * Ťuknutí otevře sadu. Bez vlnky: samolepka je průhledný tvar bez bubliny a obdélníková
             * vlnka přes něj by vypadala jako chyba. Dlouhý stisk se musí zopakovat — potomek
             * s vlastním klikáním ho k bublině nepustí (stejná past jako u fotky).
             */
            modifier = Modifier.combinedClickable(
                interactionSource = null,
                indication = null,
                onClick = onOpen,
                onLongClick = onRequestMenu,
            ),
        )
        is MessageMedia.AnimatedEmoji -> AnimatedEmojiImage(media)
        is MessageMedia.Video -> BoxWithConstraints {
            // Stejný rámeček jako fotka: video na výšku se zúží, místo aby přišlo o kraje.
            val frame = photoFrame(maxWidth, media.albumRatio(), fillWidth)
            val revealedSpoilers = koinInject<RevealedSpoilers>()
            val revealedIds by revealedSpoilers.revealed.collectAsState()
            val revealed = !media.hasSpoiler || messageId in revealedIds
            VideoThumbnail(
                video = media,
                messageId = messageId,
                uploading = uploading,
                onCancelUpload = onCancelUpload,
                revealed = revealed,
                modifier = Modifier
                    .size(frame)
                    .clip(pictureShape)
                    // Dlouhý stisk se opakuje — potomek s vlastním klikáním ho k bublině nepustí.
                    .combinedClickable(
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

        is MessageMedia.Document -> DocumentContent(
            document = media,
            progress = documentProgress(media.fileId),
            uploading = uploading,
            onCancelUpload = onCancelUpload,
            // Obrázek a video, které prohlížeč umí, se otevřou v něm; ostatní dostane systém.
            onOpen = if (opensInViewer(media.fileName, media.mimeType)) onOpen else onOpenDocument,
            onRequestMenu = onRequestMenu,
            accent = accent,
        )

        is MessageMedia.VoiceNote -> VoiceNoteContent(
            voice = media,
            playback = voice.playback,
            onToggle = voice.onToggle,
            onSeek = voice.onSeek,
            accent = voice.accent,
            onAccent = voice.onAccent,
        )
    }
}

/**
 * Průběh nahrávání odesílané přílohy, s křížkem, který odesílání zruší.
 *
 * Vlnitý kruh z M3 Expressive na tmavém kolečku — musí být čitelný přes jakoukoli fotku, proto
 * závoj `scrim` a bílá, stejně jako ovládání v prohlížeči médií. Dokud TDLib nahrávání neohlásí
 * (připravuje soubor), točí se neurčitě; pak ukazuje skutečný podíl.
 *
 * Křížek uprostřed kruhu je zvyk z Telegramu i jiných klientů: celé kolečko je tlačítko. Klikání
 * je `clickable`, ne `combinedClickable` — dlouhý stisk nad průběhem nabídku otevírat nemusí.
 */
@Composable
internal fun UploadIndicator(fileId: Int, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val progress = rememberUploadProgress(fileId)

    Box(
        modifier
            .size(UPLOAD_INDICATOR_SIZE)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f))
            .clickable(onClickLabel = stringResource(Res.string.conversation_cancel_sending), onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(Res.drawable.close_24dp),
            contentDescription = stringResource(Res.string.conversation_cancel_sending),
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
        if (progress == null) {
            CircularWavyProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
        } else {
            CircularWavyProgressIndicator(
                progress = { progress },
                color = Color.White,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

/**
 * Samolepka.
 *
 * Kreslí se **bez pozadí i bez zaoblení** — samolepka má vlastní tvar s průhledností a rámeček
 * kolem ní by z ní udělal obrázek. Když se nedá vykreslit ani ona, ani její náhled, zbývá emoji,
 * které k ní Telegram vede; velké, ať je poznat, že to je celý obsah zprávy, a ne popisek.
 *
 * Animovaná `.tgs` hraje přes [AnimatedSticker] a do té doby (i když se načíst nepodaří) ukazuje
 * statický náhled. `.webm` zatím zůstává jen náhledem.
 */
@Composable
private fun StickerImage(sticker: MessageMedia.Sticker, modifier: Modifier = Modifier) {
    val description = sticker.emoji.ifBlank { stringResource(Res.string.media_sticker) }
    val staticFileId = sticker.fileId ?: sticker.thumbnailFileId

    if (staticFileId == null && sticker.animationFileId == null) {
        Text(text = description, style = MaterialTheme.typography.displayMedium)
        return
    }

    // Poměr stran z metadat zprávy, ne z načteného souboru — jinak by seznam poskočil,
    // až se obrázek dotáhne. Nulové rozměry samolepka mít nemá, ale dělit se jimi nedá,
    // takže z ní v tom případě je čtverec.
    val ratio = if (sticker.width > 0 && sticker.height > 0) {
        sticker.width.toFloat() / sticker.height.toFloat()
    } else {
        1f
    }

    val width = if (ratio >= 1f) STICKER_SIZE else STICKER_SIZE * ratio
    val height = if (ratio >= 1f) STICKER_SIZE / ratio else STICKER_SIZE
    val size = modifier.size(width = width, height = height)

    val still: @Composable () -> Unit = {
        if (staticFileId != null) {
            RemoteImage(
                fileId = staticFileId,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                // Samolepka je průhledná; podložka pod ní by z ní udělala obrázek v rámečku.
                background = Color.Transparent,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Animace bez náhledu: než se načte, drží místo aspoň emoji.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = description, style = MaterialTheme.typography.displayMedium)
            }
        }
    }

    val animation = sticker.animationFileId
    if (animation != null) {
        AnimatedSticker(
            fileId = animation,
            contentDescription = description,
            modifier = size,
            placeholder = still,
        )
    } else {
        Box(size) { still() }
    }
}

/**
 * Animované emoji.
 *
 * Než se animace načte (a když se načíst nepodaří), stojí na jejím místě tentýž znak velkým písmem,
 * jak se emoji kreslilo dřív — přechod na animaci je tak výměna obrázku za obrázek, ne naskočení
 * z prázdna. Velikost místa je pevná, aby se bublina při výměně nepřeskládala.
 */
@Composable
private fun AnimatedEmojiImage(emoji: MessageMedia.AnimatedEmoji) {
    AnimatedSticker(
        fileId = emoji.animationFileId,
        contentDescription = emoji.emoji,
        modifier = Modifier.size(ANIMATED_EMOJI_SIZE),
        placeholder = {
            Text(text = emoji.emoji, style = MaterialTheme.typography.displayLarge)
        },
    )
}

/** Co bublina potřebuje k hlasovce; u ostatních příloh se nepoužije. */
internal class VoiceHandlers(
    val playback: () -> VoicePlayback?,
    val onToggle: () -> Unit,
    val onSeek: (Float) -> Unit,
    val accent: Color,
    val onAccent: Color,
) {
    companion object {
        val None = VoiceHandlers({ null }, {}, {}, Color.Unspecified, Color.Unspecified)
    }
}

/**
 * Náhled videa v bublině nebo dlaždici alba: obrázek, uprostřed tlačítko přehrávání (u odesílaného
 * průběh nahrávání se zrušením) a vlevo dole délka. Přehrává se až v prohlížeči — v bublině by
 * se video pralo se scrollem a swipem na odpověď.
 *
 * Náhled nese sdílený prvek `photoKey`, stejně jako fotka: v prohlížeči z něj video vyrůstá.
 */
@Composable
internal fun VideoThumbnail(
    video: MessageMedia.Video,
    messageId: Long,
    uploading: Boolean,
    onCancelUpload: () -> Unit,
    modifier: Modifier = Modifier,
    revealed: Boolean = true,
) {
    val spoilerStyle = rememberSpoilerBlurStyle()
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        video.thumbnailFileId?.let { thumbnail ->
            RemoteImage(
                fileId = thumbnail,
                contentDescription = stringResource(Res.string.media_video),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .sharedNavElement(photoKey(messageId))
                    .spoilerBlur(video.hasSpoiler, revealed, spoilerStyle),
            )
        }
        if (uploading) {
            UploadIndicator(fileId = video.fileId, onCancel = onCancelUpload, modifier = Modifier.align(Alignment.Center))
        } else if (revealed) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(UPLOAD_INDICATOR_SIZE)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(Res.drawable.play_arrow_24dp),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        if (video.hasSpoiler) {
            SpoilerOverlay(visible = !revealed)
        }
        if (revealed) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            ) {
                Text(
                    text = formatDuration(video.durationSeconds),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/**
 * Soubor v bublině: ikona (nebo náhled), název a velikost. Ťuknutí ho stáhne a otevře systémem.
 *
 * Během stahování je nad ikonou vlnitý kruh s průběhem — u velkého souboru je to jediná
 * informace, která se mění, a bez ní by ťuknutí vypadalo, že nic neudělalo. Odesílaný soubor
 * ukazuje průběh nahrávání a otevřít se nedá; ještě není kde.
 *
 * Ťuknutí i dlouhý stisk má řádek vlastní, takže nad souborem jde otevřít nabídka — stejná past
 * jako u fotky.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentContent(
    document: MessageMedia.Document,
    progress: Float?,
    uploading: Boolean,
    onCancelUpload: () -> Unit,
    onOpen: () -> Unit,
    onRequestMenu: () -> Unit,
    accent: Color,
) {
    val size = remember(document.sizeBytes) { formatFileSize(document.sizeBytes) }
    val kind = remember(document.fileName, document.mimeType) {
        documentKind(document.fileName, document.mimeType)
    }

    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                enabled = !uploading,
                onClick = onOpen,
                onLongClick = onRequestMenu,
                onClickLabel = stringResource(Res.string.document_open),
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            when {
                uploading -> UploadIndicator(fileId = document.fileId, onCancel = onCancelUpload)
                document.thumbnailFileId != null -> DocumentThumbnail(
                    thumbnailFileId = document.thumbnailFileId,
                    kind = kind,
                    progress = progress,
                )
                else -> DocumentIcon(kind = kind, progress = progress, accent = accent)
            }
        }

        Column {
            Text(
                text = document.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Text(
                text = stringResource(size.unit.label(), size.value),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/**
 * Náhled souboru místo ikony: čtvereček s malým zaoblením, u videa se symbolem přehrání.
 *
 * Náhled posílá server (u obrázku) nebo odesílatel, takže bývá jen u obrázků a videí — tedy
 * přesně tam, kde ikona obecného souboru o obsahu nic neřekne. Průběh stahování jde přes
 * náhled stejně jako u nahrávání: bílá na závoji, protože akcent bubliny by na fotce zanikl.
 */
@Composable
private fun DocumentThumbnail(thumbnailFileId: Int, kind: DocumentKind, progress: Float?) {
    Box(
        Modifier.size(UPLOAD_INDICATOR_SIZE).clip(RoundedCornerShape(DOCUMENT_THUMBNAIL_CORNER)),
        contentAlignment = Alignment.Center,
    ) {
        RemoteImage(
            fileId = thumbnailFileId,
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
        )
        when {
            progress != null -> {
                Box(Modifier.matchParentSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)))
                DownloadProgress(progress = progress, color = Color.White)
            }
            kind == DocumentKind.Video -> Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(Res.drawable.play_arrow_24dp),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Ikona souboru bez náhledu — podle druhu, ať je aspoň poznat, že jde o fotku nebo video. */
@Composable
private fun DocumentIcon(kind: DocumentKind, progress: Float?, accent: Color) {
    Box(
        Modifier
            .size(UPLOAD_INDICATOR_SIZE)
            .clip(CircleShape)
            .background(accent.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(
                when (kind) {
                    DocumentKind.Image -> Res.drawable.image_24dp
                    DocumentKind.Video -> Res.drawable.play_arrow_24dp
                    DocumentKind.Other -> Res.drawable.description_24dp
                },
            ),
            contentDescription = null,
            tint = accent,
        )
    }
    if (progress != null) DownloadProgress(progress = progress, color = accent)
}

/** Dokud TDLib nehlásí podíl, točí se neurčitě — stejně jako u nahrávání. */
@Composable
private fun DownloadProgress(progress: Float, color: Color) {
    if (progress <= 0f) {
        CircularWavyProgressIndicator(color = color, modifier = Modifier.size(UPLOAD_INDICATOR_SIZE))
    } else {
        CircularWavyProgressIndicator(
            progress = { progress },
            color = color,
            modifier = Modifier.size(UPLOAD_INDICATOR_SIZE),
        )
    }
}

/** Náhled souboru je řádek, ne snímek — zaoblení bubliny by z něj udělalo kolečko. */
private val DOCUMENT_THUMBNAIL_CORNER = 12.dp

/** Jednotku velikosti pojmenuje až UI — v jiném jazyce se může lišit i pořadí čísla a zkratky. */
internal fun FileSizeUnit.label(): StringResource = when (this) {
    FileSizeUnit.Bytes -> Res.string.file_size_b
    FileSizeUnit.Kilobytes -> Res.string.file_size_kb
    FileSizeUnit.Megabytes -> Res.string.file_size_mb
    FileSizeUnit.Gigabytes -> Res.string.file_size_gb
}

/** Kolečko s průběhem nahrávání přes přílohu. */
private val UPLOAD_INDICATOR_SIZE = 48.dp

/**
 * Animované emoji. Menší než samolepka — je to pořád „jedno emoji", ne obrázek — ale větší než
 * znak v `displayLarge`, protože animace má kolem kresby vlastní vzduch.
 */
private val ANIMATED_EMOJI_SIZE = 112.dp

/** Delší hrana samolepky. Telegram je kreslí zhruba takhle velké. */
private val STICKER_SIZE = 160.dp
