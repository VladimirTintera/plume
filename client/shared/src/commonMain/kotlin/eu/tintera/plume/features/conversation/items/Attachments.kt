package eu.tintera.plume.features.conversation.items

import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.features.conversation.loadsOriginalPreview
import eu.tintera.plume.features.conversation.showsLargePreview
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
import androidx.compose.runtime.key
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import eu.tintera.plume.features.conversation.MAX_PHOTO_HEIGHT
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.audio.VoiceSpeed
import eu.tintera.plume.core.files.DocumentKind
import eu.tintera.plume.core.files.documentKind
import eu.tintera.plume.core.files.formatFileSize
import eu.tintera.plume.core.files.opensInViewer
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.AnimatedSticker
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.core.ui.photoKey
import eu.tintera.plume.core.ui.rememberUploadProgress
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.features.conversation.AudioContent
import eu.tintera.plume.features.conversation.VoiceNoteContent
import eu.tintera.plume.features.conversation.formatDuration
import eu.tintera.plume.features.conversation.photoFrame
import eu.tintera.plume.resources.Res
import eu.tintera.plume.core.ui.BlurredThumbnail
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.resources.star_filled_24dp
import eu.tintera.plume.resources.paid_media_items
import eu.tintera.plume.resources.paid_media_price
import org.jetbrains.compose.resources.pluralStringResource
import androidx.compose.foundation.layout.Spacer
import eu.tintera.plume.core.telegram.SelfDestructTimers
import eu.tintera.plume.core.telegram.SelfDestructType
import eu.tintera.plume.core.ui.fileSizeText
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.content_expired_photo
import eu.tintera.plume.resources.content_expired_video
import eu.tintera.plume.resources.conversation_cancel_sending
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.image_24dp
import eu.tintera.plume.resources.document_open
import eu.tintera.plume.resources.media_photo
import eu.tintera.plume.resources.media_self_destruct_seconds
import eu.tintera.plume.resources.media_sticker
import eu.tintera.plume.resources.media_video
import eu.tintera.plume.resources.media_view_once
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.schedule_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import eu.tintera.plume.core.ui.StickerPlayback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

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
    videoNote: VideoNoteHandlers = VideoNoteHandlers.None,
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
    /** Hod kostkou proběhne před očima; jinak je vidět rovnou výsledek (viz [DiceImage]). */
    diceThrow: Boolean = false,
    /**
     * Výhru hodu oslaví konfety — jen u VLASTNÍHO hodu, který proběhl před očima; server snímek
     * výhry posílá právě kvůli odeslanému hodu (viz [DiceImage]).
     */
    diceCelebrate: Boolean = false,
    /** Přepis hlasovky (tlačítko a text pod ní); `null` = bez přepisu (kopie v nabídce, náhledy). */
    transcript: TranscriptState? = null,
    /** Akce z karty s vlastními prvky (soutěž: kanály, výherci, podrobnosti). */
    onAction: (MessageAction) -> Unit = {},
) {
    val shape = RoundedCornerShape(12.dp)
    // Náhled souboru zůstává malý čtvereček bez ohledu na tvar bubliny — velké zaoblení bubliny
    // by z 48dp náhledu udělalo kolečko.
    val pictureShape = mediaShape ?: shape

    when (media) {
        is MessageMedia.Photo -> {
            val timers = koinInject<SelfDestructTimers>()
            val consumedIds by timers.consumedIds.collectAsState()
            val isConsumed = messageId in consumedIds || timers.isConsumed(messageId)
            val isSelfDestruct = media.isSecret || media.selfDestructType != null

            if (isSelfDestruct && isConsumed) {
                ExpiredMediaRow(
                    isVideo = false,
                    accent = accent,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = onRequestMenu,
                    ),
                )
            } else {
                // Rámeček i jeho pravidla jsou v `photoFrame` (s testy): fotka se zužuje, neořezává.
                BoxWithConstraints {
                    val frame = photoFrame(maxWidth, media.width.toFloat() / media.height.toFloat(), fillWidth)
                    val revealedSpoilers = koinInject<RevealedSpoilers>()
                    val revealedIds by revealedSpoilers.revealed.collectAsState()
                    val revealed = !media.hasSpoiler || messageId in revealedIds
                    val spoilerStyle = rememberSpoilerBlurStyle()
                    val shouldBlur = isSelfDestruct || (media.hasSpoiler && !revealed)

                    Box(
                        modifier = Modifier
                            .size(frame)
                            .clip(pictureShape)
                            .sharedNavElement(photoKey(messageId), shape = pictureShape, inTwoPane = true)
                            /*
                             * Gesta patří FOTCE, ne celé bublině.
                             *
                             * Kdyby ťuknutí odchytávala bublina, sebralo by ho i odkazům v popisku.
                             * Dlouhý stisk se musí zopakovat tady: potomek s vlastním klikáním ho
                             * nepustí k rodiči, takže by nad fotkou nešla otevřít nabídka.
                             */
                            .combinedClickable(
                                onClick = {
                                    if (isSelfDestruct) {
                                        onOpen()
                                    } else if (media.hasSpoiler && !revealed) {
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
                            contentDescription = stringResource(Res.string.media_photo),
                            // `Crop` dořeší podlahu šířky (extrémně vysoká fotka, fotka s popiskem):
                            // poměr stran zůstává a vidět je prostředek. Jinak není co ořezávat.
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .spoilerBlur(shouldBlur, revealed = false, spoilerStyle),
                        )

                        if (isSelfDestruct) {
                            SelfDestructBadge(
                                selfDestructType = media.selfDestructType,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        } else if (media.hasSpoiler) {
                            SpoilerOverlay(visible = !revealed, sensitive = media.isSensitive)
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
        }

        is MessageMedia.PaidMedia -> PaidMediaPreview(
            media = media,
            shape = pictureShape,
            fillWidth = fillWidth,
            modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = onRequestMenu),
        )

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
                text = media.label.resolve(),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        is MessageMedia.Product -> ProductCard(media, accent)
        is MessageMedia.Giveaway -> GiveawayCard(media, accent, onAction)
        // Dárek kreslí servisní položka (ServiceMessageItem); sem se dostane jen kdyby přestal být oznámením.
        is MessageMedia.Gift -> GiftCard(media, onAction)
        // Příběh je fotka nebo video — kreslí se toutéž cestou, jen se značkou v rohu.
        is MessageMedia.Story -> StoryPreview(
            story = media,
            shape = pictureShape,
            onOpen = onOpen,
            onRequestMenu = onRequestMenu,
        ) { inner ->
            MessageAttachment(
                media = inner,
                messageId = messageId,
                onOpen = onOpen,
                onRequestMenu = onRequestMenu,
                accent = accent,
                mediaShape = mediaShape,
                fillWidth = fillWidth,
            )
        }

        is MessageMedia.Sticker -> StickerImage(
            sticker = media,
            messageId = messageId,
            onRequestMenu = onRequestMenu,
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
        is MessageMedia.Dice -> DiceImage(media, throwNow = diceThrow, celebrate = diceCelebrate, seed = messageId)
        is MessageMedia.Location -> LocationMap(
            location = media,
            shape = pictureShape,
            onOpen = onOpen,
            onRequestMenu = onRequestMenu,
            fillWidth = fillWidth,
        )
        is MessageMedia.Animation -> BoxWithConstraints {
            // Stejný rámeček jako fotka a video: GIF na výšku se zúží, místo aby přišel o kraje.
            val frame = photoFrame(maxWidth, media.width.toFloat() / media.height.toFloat(), fillWidth)
            val revealedSpoilers = koinInject<RevealedSpoilers>()
            val revealedIds by revealedSpoilers.revealed.collectAsState()
            AnimationBubble(
                animation = media,
                shape = pictureShape,
                onRequestMenu = onRequestMenu,
                modifier = Modifier.size(frame),
                revealed = !media.hasSpoiler || messageId in revealedIds,
                onReveal = { revealedSpoilers.reveal(messageId) },
            )
        }

        is MessageMedia.Video -> {
            val timers = koinInject<SelfDestructTimers>()
            val consumedIds by timers.consumedIds.collectAsState()
            val isConsumed = messageId in consumedIds || timers.isConsumed(messageId)
            val isSelfDestruct = media.isSecret || media.selfDestructType != null

            if (isSelfDestruct && isConsumed) {
                ExpiredMediaRow(
                    isVideo = true,
                    accent = accent,
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = onRequestMenu,
                    ),
                )
            } else {
                BoxWithConstraints {
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
                        isSelfDestruct = isSelfDestruct,
                        shape = pictureShape,
                        modifier = Modifier
                            .size(frame)
                            .clip(pictureShape)
                            // Dlouhý stisk se opakuje — potomek s vlastním klikáním ho k bublině nepustí.
                            .combinedClickable(
                                onClick = {
                                    if (isSelfDestruct) {
                                        onOpen()
                                    } else if (media.hasSpoiler && !revealed) {
                                        revealedSpoilers.reveal(messageId)
                                    } else {
                                        onOpen()
                                    }
                                },
                                onLongClick = onRequestMenu,
                            ),
                    )
                }
            }
        }

        // Obrázek nebo video jako soubor, s náhledem, na ploše bez bubliny: velký snímek jako fotka.
        is MessageMedia.Document -> if (mediaShape != null && media.showsLargePreview) {
            LargeDocumentPreview(
                document = media,
                messageId = messageId,
                progress = documentProgress(media.fileId),
                uploading = uploading,
                onCancelUpload = onCancelUpload,
                onOpen = if (opensInViewer(media.fileName, media.mimeType)) onOpen else onOpenDocument,
                onRequestMenu = onRequestMenu,
                shape = pictureShape,
                fillWidth = fillWidth,
            )
        } else DocumentContent(
            messageId = messageId,
            document = media,
            progress = documentProgress(media.fileId),
            uploading = uploading,
            onCancelUpload = onCancelUpload,
            // Obrázek a video, které prohlížeč umí, se otevřou v něm; ostatní dostane systém.
            onOpen = if (opensInViewer(media.fileName, media.mimeType)) onOpen else onOpenDocument,
            onRequestMenu = onRequestMenu,
            accent = accent,
        )

        is MessageMedia.Call -> CallRow(call = media, accent = accent)

        is MessageMedia.VideoNote -> VideoNoteBubble(note = media, handlers = videoNote, onRequestMenu = onRequestMenu)

        is MessageMedia.Contact -> ContactCard(
            contact = media,
            accent = accent,
            onWrite = onOpen,
            onAddContact = { onAction(MessageAction.AddContact) },
        )

        is MessageMedia.Audio -> AudioContent(
            audio = media,
            playback = voice.playback,
            speed = voice.speed,
            onToggle = voice.onToggle,
            onSeek = voice.onSeek,
            onCycleSpeed = voice.onCycleSpeed,
            accent = voice.accent,
            onAccent = voice.onAccent,
        )

        is MessageMedia.VoiceNote -> VoiceNoteContent(
            voice = media,
            playback = voice.playback,
            speed = voice.speed,
            onToggle = voice.onToggle,
            onSeek = voice.onSeek,
            onCycleSpeed = voice.onCycleSpeed,
            accent = voice.accent,
            onAccent = voice.onAccent,
            transcript = transcript,
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
private fun StickerImage(
    sticker: MessageMedia.Sticker,
    modifier: Modifier = Modifier,
    messageId: Long = 0L,
    onRequestMenu: (() -> Unit)? = null,
) {
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
    // Citlivá samolepka: rozostřená se štítkem, první ťuknutí ji odkryje, další otevře sadu.
    SensitiveBox(
        messageId = messageId,
        hidden = sticker.isSensitive,
        onLongClick = onRequestMenu,
        coveredShape = RoundedCornerShape(STICKER_COVER_CORNER),
    ) { blur ->
        if (animation != null) {
            AnimatedSticker(
                fileId = animation,
                contentDescription = description,
                modifier = size.then(blur),
                placeholder = still,
            )
        } else {
            Box(size.then(blur)) { still() }
        }
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

/**
 * Hod kostkou, šipkami, míčem nebo automatem.
 *
 * Vrstvy animace se kreslí přes sebe (automat jich má pět) a výsledek je v nich nakreslený:
 * poslední snímek ukazuje, co padlo. Proto se hod z historie ukazuje rovnou posledním snímkem
 * a přehraje se jen ten, který přišel před očima ([throwNow]) — a jen jednou; kdyby hrál ve
 * smyčce jako samolepka, házelo by se pořád dokola a výsledek by byl vidět jen na okamžik.
 *
 * Dokud výsledek nedorazil (vlastní hod čeká na server), točí se úvodní animace ve smyčce.
 * Hotovo je, až doběhnou VŠECHNY vrstvy — válce automatu nekončí naráz.
 */
@Composable
private fun DiceImage(dice: MessageMedia.Dice, throwNow: Boolean, celebrate: Boolean, seed: Long) {
    var thrown by remember { mutableStateOf(!throwNow) }
    val finishedLayers = remember(dice.layers) { mutableSetOf<Int>() }
    val playback = when {
        dice.rolling -> StickerPlayback.Loop
        thrown -> StickerPlayback.LastFrame
        else -> StickerPlayback.Once
    }
    // Oslava výhry na snímku, který poslal server (míč propadne košem, šipka zasáhne střed).
    // U automatu se hlásí poslední vrstva — pravý válec, ten dotočí poslední.
    var celebrating by remember { mutableStateOf(false) }
    val raise = LocalRaiseItem.current
    val winFrame = dice.successFrame.takeIf { celebrate && it > 0 } ?: 0
    Box(Modifier.size(DICE_SIZE).semantics { contentDescription = dice.label }) {
        dice.layers.forEachIndexed { index, fileId ->
            key(fileId) {
                AnimatedSticker(
                    fileId = fileId,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    playback = playback,
                    onFinished = {
                        finishedLayers += fileId
                        if (finishedLayers.size == dice.layers.size) thrown = true
                    },
                    markerFrame = if (index == dice.layers.lastIndex) winFrame else 0,
                    onMarker = {
                        celebrating = true
                        raise(true)
                    },
                    placeholder = {
                        // Znak drží místo jen pod spodní vrstvou; nad ní by se zdvojil.
                        if (index == 0) Text(text = dice.emoji, style = MaterialTheme.typography.displayLarge)
                    },
                )
            }
        }
        if (celebrating) {
            DiceConfetti(
                seed = seed,
                onFinished = {
                    celebrating = false
                    raise(false)
                },
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** Co bublina potřebuje k hlasovce; u ostatních příloh se nepoužije. */
internal class VoiceHandlers(
    val playback: () -> VoicePlayback?,
    val speed: () -> VoiceSpeed,
    val onToggle: () -> Unit,
    val onSeek: (Float) -> Unit,
    val onCycleSpeed: () -> Unit,
    val accent: Color,
    val onAccent: Color,
) {
    companion object {
        val None = VoiceHandlers({ null }, { VoiceSpeed.Normal }, {}, {}, {}, Color.Unspecified, Color.Unspecified)
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
    isSelfDestruct: Boolean = false,
    /** Tvar, do kterého náhled ořezává volající — letí s ním do prohlížeče a zpátky. */
    shape: Shape? = null,
) {
    val spoilerStyle = rememberSpoilerBlurStyle()
    val shouldBlur = isSelfDestruct || (video.hasSpoiler && !revealed)
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        video.thumbnailFileId?.let { thumbnail ->
            RemoteImage(
                fileId = thumbnail,
                placeholder = video.minithumbnail,
                contentDescription = stringResource(Res.string.media_video),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .sharedNavElement(photoKey(messageId), shape = shape, inTwoPane = true)
                    .spoilerBlur(shouldBlur, revealed = false, spoilerStyle),
            )
        }
        if (uploading) {
            UploadIndicator(fileId = video.fileId, onCancel = onCancelUpload, modifier = Modifier.align(Alignment.Center))
        } else if (isSelfDestruct) {
            SelfDestructBadge(
                selfDestructType = video.selfDestructType,
                modifier = Modifier.align(Alignment.Center),
            )
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
        if (!isSelfDestruct && video.hasSpoiler) {
            SpoilerOverlay(visible = !revealed, sensitive = video.isSensitive)
        }
        if (!isSelfDestruct && revealed) {
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
    messageId: Long,
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
                // Citlivý náhled je rozostřený, bez štítku (na 48 dp se nevejde); ťuknutí na něj
                // ho odkryje, ťuknutí na název soubor otevře.
                document.thumbnailFileId != null -> SensitiveBox(
                    messageId = messageId,
                    hidden = document.isSensitive,
                    label = false,
                    onLongClick = onRequestMenu,
                ) { blur ->
                    DocumentThumbnail(
                        thumbnailFileId = document.thumbnailFileId,
                        kind = kind,
                        progress = progress,
                        modifier = blur,
                    )
                }
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
                text = fileSizeText(size),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/**
 * Obrázek nebo video poslané jako soubor, VELKÉ — jako fotka, rámečkem podle poměru stran náhledu
 * ([photoFrame]). Název a velikost jsou pod snímkem v popisku (`MediaMessageItem`).
 *
 * Kreslí se originál, když se smí stáhnout sám ([loadsOriginalPreview]), jinak náhled od serveru;
 * ten je malý, ale rozmazaný snímek je pořád víc než ikona v řádku. U videa je uprostřed symbol
 * přehrání, jako u videa poslaného jako video. Ťuknutí otevře prohlížeč, a co ten neumí, systém —
 * pak se nad snímkem točí průběh stahování, bíle na závoji jako u nahrávání.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LargeDocumentPreview(
    document: MessageMedia.Document,
    messageId: Long,
    progress: Float?,
    uploading: Boolean,
    onCancelUpload: () -> Unit,
    onOpen: () -> Unit,
    onRequestMenu: () -> Unit,
    shape: Shape,
    fillWidth: Boolean,
) {
    val thumbnail = document.thumbnailFileId ?: return
    val kind = remember(document.fileName, document.mimeType) { documentKind(document.fileName, document.mimeType) }
    val inViewer = remember(document.fileName, document.mimeType) { opensInViewer(document.fileName, document.mimeType) }
    BoxWithConstraints {
        val frame = photoFrame(maxWidth, document.thumbnailWidth.toFloat() / document.thumbnailHeight.toFloat(), fillWidth)
        Box(
            Modifier
                .size(frame)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                // Z náhledu vyroste prohlížeč, stejně jako z fotky; co jde systému, neletí nikam.
                .then(if (inViewer) Modifier.sharedNavElement(photoKey(messageId), shape = shape, inTwoPane = true) else Modifier)
                .combinedClickable(
                    enabled = !uploading,
                    onClick = onOpen,
                    onLongClick = onRequestMenu,
                    onClickLabel = stringResource(Res.string.document_open),
                ),
            contentAlignment = Alignment.Center,
        ) {
            RemoteImage(
                fileId = if (document.loadsOriginalPreview) document.fileId else thumbnail,
                thumbnailFileId = thumbnail.takeIf { document.loadsOriginalPreview },
                contentDescription = document.fileName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            when {
                uploading -> UploadIndicator(fileId = document.fileId, onCancel = onCancelUpload)
                progress != null -> Box(
                    Modifier.size(UPLOAD_INDICATOR_SIZE).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    DownloadProgress(progress = progress, color = Color.White)
                }
                kind == DocumentKind.Video -> Box(
                    Modifier.size(UPLOAD_INDICATOR_SIZE).clip(CircleShape)
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
        }
    }
}

/** Název a velikost souboru pod velkým náhledem ([LargeDocumentPreview]), v barvě [color]. */
@Composable
internal fun DocumentCaption(document: MessageMedia.Document, color: Color) {
    val size = remember(document.sizeBytes) { formatFileSize(document.sizeBytes) }
    Column {
        Text(
            text = document.fileName,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
        Text(text = fileSizeText(size), style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f))
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
private fun DocumentThumbnail(thumbnailFileId: Int, kind: DocumentKind, progress: Float?, modifier: Modifier = Modifier) {
    Box(
        modifier.size(UPLOAD_INDICATOR_SIZE).clip(RoundedCornerShape(DOCUMENT_THUMBNAIL_CORNER)),
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

/** Kolečko s průběhem nahrávání přes přílohu. */
private val UPLOAD_INDICATOR_SIZE = 48.dp

/**
 * Animované emoji. Menší než samolepka — je to pořád „jedno emoji", ne obrázek — ale větší než
 * znak v `displayLarge`, protože animace má kolem kresby vlastní vzduch.
 */
private val ANIMATED_EMOJI_SIZE = 112.dp

/** Hod je samolepka od Telegramu a kreslí se stejně velký jako ona. */
private val DICE_SIZE = 160.dp

/** Delší hrana samolepky. Telegram je kreslí zhruba takhle velké. */
private val STICKER_SIZE = 160.dp

/**
 * Odznak samozničujícího média (view-once nebo odpočet času).
 */
@Composable
internal fun SelfDestructBadge(
    selfDestructType: SelfDestructType?,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
        contentColor = Color.White,
        modifier = modifier.padding(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.schedule_24dp),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            val text = when (selfDestructType) {
                is SelfDestructType.Timer -> stringResource(Res.string.media_self_destruct_seconds, selfDestructType.seconds)
                else -> stringResource(Res.string.media_view_once)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

/**
 * Řádek pro vypršelé samozničující médium.
 */
@Composable
internal fun ExpiredMediaRow(
    isVideo: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.surfaceContainerHigh
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = background,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(
                painter = painterResource(Res.drawable.schedule_24dp),
                contentDescription = null,
                // Akcent patří bublině: u odchozí zprávy je to `onPrimary`, který na vlastní
                // ploše řádku (`surfaceContainerHigh`) zmizí. Proto se ověřuje proti ní.
                tint = accentOn(
                    background = background,
                    accent = accent.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.primary,
                    fallback = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(if (isVideo) Res.string.content_expired_video else Res.string.content_expired_photo),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Zaoblení rozostřené citlivé samolepky — viz `SensitiveBox.coveredShape`. */
private val STICKER_COVER_CORNER = 24.dp

/**
 * Nezaplacené placené médium: rozmazaný náhled od serveru, uprostřed cena v hvězdách, v rohu
 * délka videa. Jedna položka má rámeček podle poměru stran jako fotka ([photoFrame]); víc
 * položek je mřížka jako album ([albumRowSizes]), s JEDNOU cenou přes celou mřížku — kupuje se
 * všechno naráz, cena u každé dlaždice by tvrdila, že jde koupit kus.
 *
 * **Rozmazání je jediné, co server dá** — soubor se vydá až po zaplacení, takže tu není co
 * stahovat ani co odkrývat. Ťuknutí vysvětlí, že odemknout to Plume zatím neumí.
 */
@Composable
private fun PaidMediaPreview(
    media: MessageMedia.PaidMedia,
    shape: Shape,
    fillWidth: Boolean,
    modifier: Modifier = Modifier,
) {
    val description = pluralStringResource(
        Res.plurals.paid_media_price,
        media.starCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        media.starCount.toInt().grouped(),
    )
    val items = if (media.previews.size > 1) {
        pluralStringResource(Res.plurals.paid_media_items, media.previews.size, media.previews.size)
    } else {
        null
    }
    BoxWithConstraints {
        val single = media.previews.singleOrNull()
        val width = maxWidth
        val gridModifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics { contentDescription = listOfNotNull(items, description).joinToString(", ") }
        Box(
            if (single != null) {
                gridModifier.size(photoFrame(width, single.ratio(default = 1f), fillWidth))
            } else {
                gridModifier.width(width)
            },
            contentAlignment = Alignment.Center,
        ) {
            if (single != null) {
                PaidPreviewTile(single, Modifier.fillMaxSize())
            } else {
                PaidPreviewGrid(media.previews, width)
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                contentColor = Color.White,
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        painterResource(Res.drawable.star_filled_24dp),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = media.starCount.toInt().grouped(),
                        style = MaterialTheme.typography.labelLarge.tabularNumbers(),
                    )
                }
            }
        }
    }
}

/** Mřížka náhledů placeného média; řádky a výšky jako u alba (viz `AlbumContent`). */
@Composable
private fun PaidPreviewGrid(previews: List<MessageMedia.PaidPreview>, width: Dp) {
    Column(verticalArrangement = Arrangement.spacedBy(ALBUM_GAP)) {
        var start = 0
        albumRowSizes(previews.size).forEach { size ->
            val row = previews.subList(start, start + size)
            start += size
            val alone = row.singleOrNull()
            val height = if (alone != null) {
                (width / alone.ratio(default = 16f / 9f)).coerceAtMost(MAX_PHOTO_HEIGHT)
            } else {
                ((width - ALBUM_GAP * (size - 1)) / size).coerceAtMost(ALBUM_TILE_MAX_HEIGHT)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(ALBUM_GAP)) {
                row.forEach { preview ->
                    PaidPreviewTile(preview, Modifier.weight(1f).height(height))
                }
            }
        }
    }
}

/** Jeden rozmazaný náhled; u videa délka v rohu. */
@Composable
private fun PaidPreviewTile(preview: MessageMedia.PaidPreview, modifier: Modifier) {
    Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        preview.minithumbnail?.let { BlurredThumbnail(it, Modifier.fillMaxSize()) }
        if (preview.durationSeconds > 0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f),
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
            ) {
                Text(
                    text = formatDuration(preview.durationSeconds),
                    style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

/** Poměr stran náhledu; bez rozměrů [default]. */
private fun MessageMedia.PaidPreview.ratio(default: Float): Float =
    if (width > 0 && height > 0) width.toFloat() / height else default
