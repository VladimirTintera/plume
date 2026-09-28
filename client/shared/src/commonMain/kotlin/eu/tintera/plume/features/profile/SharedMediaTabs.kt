package eu.tintera.plume.features.profile

import androidx.compose.material3.LocalTextStyle
import eu.tintera.plume.resources.profile_shared_music_play
import eu.tintera.plume.resources.profile_shared_music_pause
import eu.tintera.plume.resources.music_note_24dp
import eu.tintera.plume.resources.pause_24dp
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.features.conversation.formatDuration
import eu.tintera.plume.core.audio.VoiceKey
import eu.tintera.plume.core.audio.VoicePlayback
import org.koin.compose.koinInject
import eu.tintera.plume.features.conversation.items.spoilerBlur
import eu.tintera.plume.features.conversation.items.rememberSpoilerBlurStyle
import eu.tintera.plume.core.telegram.RevealedSpoilers
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.ui.platform.LocalLayoutDirection
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.core.ui.SearchPill
import eu.tintera.plume.resources.media_spoiler
import eu.tintera.plume.resources.media_sensitive
import eu.tintera.plume.resources.visibility_off_24dp
import eu.tintera.plume.resources.chats_search_clear
import eu.tintera.plume.resources.chats_search_empty
import eu.tintera.plume.resources.chats_search_empty_hint
import eu.tintera.plume.resources.profile_members_hidden
import eu.tintera.plume.resources.profile_members_search
import eu.tintera.plume.resources.profile_members_search_empty
import eu.tintera.plume.resources.search_off_24dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.files.DocumentKind
import eu.tintera.plume.core.files.documentKind
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MemberRole
import eu.tintera.plume.core.telegram.MessageEntityType
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.ProfileMember
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.GROUP_CORNER
import eu.tintera.plume.core.ui.GROUP_ITEM_SPACING
import eu.tintera.plume.core.ui.INNER_CORNER
import eu.tintera.plume.core.ui.groupItemShape
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.core.ui.photoKey
import eu.tintera.plume.core.ui.LocalSharedElementClip
import eu.tintera.plume.core.ui.LocalSharedMediaFlight
import eu.tintera.plume.core.ui.rememberSharedElementClip
import eu.tintera.plume.core.ui.sharedElementClipArea
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.core.ui.sharedNavText
import eu.tintera.plume.core.ui.LocalSavedMessagesChatId
import eu.tintera.plume.core.ui.chatAvatarKey
import eu.tintera.plume.core.ui.chatTitleKey
import eu.tintera.plume.core.ui.fileSizeText
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.image_24dp
import eu.tintera.plume.resources.language_24dp
import eu.tintera.plume.resources.play_arrow_24dp
import eu.tintera.plume.resources.profile_bot
import eu.tintera.plume.resources.smart_toy_24dp
import eu.tintera.plume.resources.shield_person_filled_24dp
import eu.tintera.plume.resources.star_filled_24dp
import eu.tintera.plume.resources.profile_role_admin
import eu.tintera.plume.resources.profile_role_owner
import eu.tintera.plume.resources.profile_shared_file_meta
import eu.tintera.plume.resources.profile_shared_link
import eu.tintera.plume.resources.profile_shared_media_video
import eu.tintera.plume.resources.profile_shared_photo
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Obsah záložek profilu.
 *
 * Každá záložka je **vlastní scrollovatelný seznam** s pevnou výškou stránky pageru — profil
 * kolem nich není `LazyColumn`, ale `verticalScroll` sloupec (viz `ChatProfileScreen`), takže
 * vnitřní seznam nikdy nedostane nekonečnou výšku.
 *
 * Další stránka se dotahuje, až se doroluje na konec ([onLoadMore]) — ne dopředu: sdíleného
 * obsahu bývají v dlouhém chatu tisíce položek.
 */

/** Dlaždic mřížky médií na šířku. Tři jsou kompromis mezi přehledem a velikostí náhledu. */
private const val MEDIA_COLUMNS = 3

/** Kolik položek před koncem spustí dotažení další stránky. */
private const val LOAD_MORE_THRESHOLD = 6

@Composable
internal fun SharedMediaGrid(
    messages: List<ChatMessage>,
    loading: Boolean,
    contentPadding: PaddingValues,
    onOpen: (messageId: Long) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    /*
     * Mřížka je zároveň hranicí, do které se ořezává fotka letící z prohlížeče zpátky (viz
     * [SharedElementClip]) — bez toho by se dlaždice u horního okraje na konci letu nakreslila
     * přes záložky a hlavičku profilu. A `LocalSharedMediaFlight` odlišuje klíč dlaždice od
     * klíče bubliny v konverzaci, aby se ty dvě nespárovaly navzájem (viz `photoKey`).
     */
    val clip = rememberSharedElementClip()
    CompositionLocalProvider(
        LocalSharedMediaFlight provides true,
        LocalSharedElementClip provides clip,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(MEDIA_COLUMNS),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = modifier.fillMaxSize().sharedElementClipArea(clip),
        ) {
            itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
                LoadMoreAtEnd(index, messages.size, onLoadMore)
                MediaTile(message, shape = mediaTileShape(index, messages.size), onOpen = { onOpen(message.id) })
            }
            if (loading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TabLoading()
                }
            }
        }
    }
}

/**
 * Dlaždice mřížky: čtverec s náhledem, u videa symbol přehrání.
 *
 * **Skryté médium (spoiler, citlivý obsah) je rozostřené i tady**, dokud ho uživatel neodkryl
 * — dřív tu byl ostrý každý spoiler. Odkrývá se jako v konverzaci: první ťuknutí odkryje,
 * druhé otevře. Prohlížeč totiž médium, na které se ťuklo, odkrývá sám (z konverzace je to už
 * druhé ťuknutí), takže otevření rovnou by ho ukázalo bez varování. Štítek se do dlaždice
 * nevejde, skrytí nese přeškrtnuté oko.
 */
@Composable
private fun MediaTile(message: ChatMessage, shape: Shape, onOpen: () -> Unit) {
    val photo = message.media as? MessageMedia.Photo
    val video = message.media as? MessageMedia.Video
    val fileId = photo?.fileId ?: video?.thumbnailFileId
    val thumbnailFileId = photo?.thumbnailFileId ?: video?.thumbnailFileId
    val hasSpoiler = photo?.hasSpoiler == true || video?.hasSpoiler == true
    val revealedSpoilers = koinInject<RevealedSpoilers>()
    val revealedIds by revealedSpoilers.revealed.collectAsState()
    val hidden = hasSpoiler && message.id !in revealedIds
    val spoilerStyle = rememberSpoilerBlurStyle()

    Box(
        Modifier
            .aspectRatio(1f)
            .clip(shape)
            // Prohlížeč z dlaždice vyroste a při zavření se do ní vrátí. Klíč je JINÝ než
            // u bubliny v konverzaci (`LocalSharedMediaFlight`): při otevření profilu a návratu
            // z něj jsou obě strany složené naráz a se společným klíčem se spárovaly mezi sebou.
            .sharedNavElement(photoKey(message.id), shape = shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(onClick = { if (hidden) revealedSpoilers.reveal(message.id) else onOpen() }),
    ) {
        if (fileId != null) {
            RemoteImage(
                fileId = fileId,
                thumbnailFileId = thumbnailFileId,
                placeholder = photo?.minithumbnail ?: video?.minithumbnail,
                contentDescription = stringResource(
                    if (video != null) Res.string.profile_shared_media_video else Res.string.profile_shared_photo,
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .spoilerBlur(hidden, revealed = false, spoilerStyle),
            )
        } else {
            // Video bez náhledu (server ho nedal) — prázdná plocha by vypadala jako chyba.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(painterResource(Res.drawable.image_24dp), contentDescription = null)
            }
        }
        if (hidden) {
            Icon(
                painterResource(Res.drawable.visibility_off_24dp),
                contentDescription = stringResource(
                    if (photo?.isSensitive == true || video?.isSensitive == true) {
                        Res.string.media_sensitive
                    } else {
                        Res.string.media_spoiler
                    },
                ),
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (video != null) {
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = Color.Black.copy(alpha = VIDEO_BADGE_ALPHA),
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            ) {
                Icon(
                    painterResource(Res.drawable.play_arrow_24dp),
                    contentDescription = null,
                    modifier = Modifier.padding(2.dp).size(16.dp),
                )
            }
        }
    }
}

@Composable
internal fun SharedFilesList(
    messages: List<ChatMessage>,
    loading: Boolean,
    downloads: Map<Int, Float>,
    contentPadding: PaddingValues,
    onOpen: (messageId: Long) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
            LoadMoreAtEnd(index, messages.size, onLoadMore)
            val document = message.media as? MessageMedia.Document ?: return@itemsIndexed
            FileRow(
                message = message,
                document = document,
                progress = downloads[document.fileId],
                onOpen = { onOpen(message.id) },
                modifier = Modifier.animateItem().clip(groupItemShape(index, messages.size)),
            )
        }
        if (loading) item { TabLoading() }
    }
}

/**
 * Záložka Hudba: skladby chatu pod sebou. Ťuknutí skladbu pustí přímo tady, bez odchodu do
 * konverzace — hraje týž přehrávač jako bublina, takže konverzace pod profilem ukazuje průběh
 * a po dohrání naváže další skladba. U hrající je místo délky uplynulý čas.
 */
@Composable
internal fun SharedMusicList(
    messages: List<ChatMessage>,
    loading: Boolean,
    playback: VoicePlayback?,
    chatId: Long,
    contentPadding: PaddingValues,
    onToggle: (messageId: Long) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
            LoadMoreAtEnd(index, messages.size, onLoadMore)
            val audio = message.media as? MessageMedia.Audio ?: return@itemsIndexed
            val current = playback?.takeIf { it.key == VoiceKey(chatId, message.id) }
            MusicRow(
                message = message,
                audio = audio,
                playback = current,
                onToggle = { onToggle(message.id) },
                modifier = Modifier.animateItem().clip(groupItemShape(index, messages.size)),
            )
        }
        if (loading) item { TabLoading() }
    }
}

/** Řádek skladby: obal (nebo nota) s tlačítkem, název a pod ním interpret s délkou. */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun MusicRow(
    message: ChatMessage,
    audio: MessageMedia.Audio,
    playback: VoicePlayback?,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playing = playback?.playing == true
    val time = when {
        playback != null && playback.durationMs > 0 ->
            "${formatDuration((playback.positionMs / 1000).toInt())} / ${formatDuration((playback.durationMs / 1000).toInt())}"
        // Délku server znát nemusí (Bot API ji u souboru bez metadat pošle jako nulu).
        audio.durationSeconds > 0 -> formatDuration(audio.durationSeconds)
        else -> null
    }
    val meta = listOfNotNull(audio.performer?.ifBlank { null }, time, message.timestamp.asDayLabel())
        .joinToString(" · ")
    val actionLabel = stringResource(if (playing) Res.string.profile_shared_music_pause else Res.string.profile_shared_music_play)
    ListItem(
        headlineContent = {
            Text(audio.title.ifBlank { audio.fileName }, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(meta, style = LocalTextStyle.current.tabularNumbers(), maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(FILE_ICON_SIZE)
                    .clip(RoundedCornerShape(FILE_ICON_CORNER))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                audio.coverFileId?.let { cover ->
                    RemoteImage(fileId = cover, modifier = Modifier.fillMaxSize())
                    // Tlačítko musí být vidět i na světlém obalu.
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = COVER_SCRIM_ALPHA)))
                }
                when {
                    playback?.loading == true && !playing -> CircularWavyProgressIndicator(Modifier.size(24.dp))
                    else -> Icon(
                        painterResource(
                            when {
                                playing -> Res.drawable.pause_24dp
                                playback != null || audio.coverFileId != null -> Res.drawable.play_arrow_24dp
                                else -> Res.drawable.music_note_24dp
                            },
                        ),
                        contentDescription = null,
                        tint = if (audio.coverFileId != null) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        },
        colors = rowColors(),
        modifier = modifier.clickable(onClickLabel = actionLabel, onClick = onToggle),
    )
}

/** Řádek souboru: náhled nebo ikona podle druhu, název a pod ním velikost s datem. */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun FileRow(
    message: ChatMessage,
    document: MessageMedia.Document,
    progress: Float?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unit = fileSizeText(document.sizeBytes)
    ListItem(
        headlineContent = { Text(document.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        // Oddělovač i pořadí patří překladu, ne slepenci v kódu.
        supportingContent = {
            Text(stringResource(Res.string.profile_shared_file_meta, unit, message.timestamp.asDayLabel()))
        },
        leadingContent = {
            Box(Modifier.size(FILE_ICON_SIZE), contentAlignment = Alignment.Center) {
                when {
                    // Průběh nahrazuje náhled, aby bylo hned vidět, že se něco děje.
                    progress != null -> CircularWavyProgressIndicator()
                    document.thumbnailFileId != null -> RemoteImage(
                        fileId = document.thumbnailFileId,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(FILE_ICON_CORNER)),
                    )
                    else -> Icon(painterResource(document.icon()), contentDescription = null)
                }
            }
        },
        colors = rowColors(),
        modifier = modifier.clickable(onClick = onOpen),
    )
}

/** Ikona podle druhu souboru; poznává se podle přípony, ne podle `mimeType` od odesílatele. */
private fun MessageMedia.Document.icon(): DrawableResource = when (documentKind(fileName, mimeType)) {
    DocumentKind.Image -> Res.drawable.image_24dp
    DocumentKind.Video -> Res.drawable.play_arrow_24dp
    DocumentKind.Other -> Res.drawable.description_24dp
}

@Composable
internal fun SharedLinksList(
    messages: List<ChatMessage>,
    loading: Boolean,
    contentPadding: PaddingValues,
    onOpen: (url: String) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
            LoadMoreAtEnd(index, messages.size, onLoadMore)
            val url = message.sharedUrl() ?: return@itemsIndexed
            LinkRow(
                message = message,
                url = url,
                onOpen = { onOpen(url) },
                modifier = Modifier.animateItem().clip(groupItemShape(index, messages.size)),
            )
        }
        if (loading) item { TabLoading() }
    }
}

/**
 * Řádek odkazu: titulek webu (nebo text zprávy) a pod ním adresa.
 *
 * Náhled skládá server, takže se bere, co poslal; bez něj zbyde text zprávy, ve kterém odkaz je.
 */
@Composable
private fun LinkRow(message: ChatMessage, url: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val preview = message.linkPreview
    val title = preview?.title?.takeIf { it.isNotBlank() }
        ?: preview?.siteName?.takeIf { it.isNotBlank() }
        ?: message.text.takeIf { it.isNotBlank() }
        ?: url
    ListItem(
        headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column {
                Text(
                    displayUrl(url),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(message.timestamp.asDayLabel(), style = MaterialTheme.typography.bodySmall)
            }
        },
        leadingContent = {
            Box(Modifier.size(FILE_ICON_SIZE), contentAlignment = Alignment.Center) {
                val photo = preview?.photo
                if (photo != null) {
                    RemoteImage(
                        fileId = photo.fileId,
                        thumbnailFileId = photo.thumbnailFileId,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(FILE_ICON_CORNER)),
                    )
                } else {
                    Icon(
                        painterResource(Res.drawable.language_24dp),
                        contentDescription = stringResource(Res.string.profile_shared_link),
                    )
                }
            }
        },
        colors = rowColors(),
        modifier = modifier.clickable(onClick = onOpen),
    )
}

/**
 * Adresa, kterou zpráva sdílí.
 *
 * Náhled od serveru má přednost — je to ten odkaz, který Telegram ze zprávy vybral. Bez náhledu
 * se vezme první odkaz z entit; zpráva se do záložky dostala proto, že v ní nějaký je.
 */
private fun ChatMessage.sharedUrl(): String? =
    linkPreview?.url ?: entities.firstNotNullOfOrNull { (it.type as? MessageEntityType.Link)?.url }

/** Pole hledání nad záložkou sdíleného obsahu; [query] je jen výchozí text, dál ho drží pole samo. */
internal class SharedSearch(val query: String, val placeholder: String, val onQueryChange: (String) -> Unit)

/**
 * Záložka sdíleného obsahu s hledáním nad seznamem.
 *
 * **Pole hledání NEODJÍŽDÍ se seznamem**, stejně jako u členů: kdo v záložce hledá, hledá proto,
 * že listování nestačí. [nothingFound] ukáže prázdný stav místo seznamu — jen k dotazu, na který
 * server odpověděl, jinak by při psaní dalšího znaku problikával.
 */
@Composable
internal fun SharedTabFrame(
    search: SharedSearch?,
    nothingFound: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    Column(modifier.fillMaxSize()) {
        if (search != null) {
            SearchPill(
                initialQuery = search.query,
                onQueryChange = search.onQueryChange,
                placeholder = search.placeholder,
                clearDescription = stringResource(Res.string.chats_search_clear),
                modifier = Modifier.padding(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection),
                    top = contentPadding.calculateTopPadding(),
                ),
            )
        }
        if (nothingFound) {
            IntroEmptyState(
                icon = Res.drawable.search_off_24dp,
                title = stringResource(Res.string.chats_search_empty),
                text = stringResource(Res.string.chats_search_empty_hint),
                containerColor = Color.Transparent,
                modifier = Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding()),
            )
        } else {
            content()
        }
    }
}

/** Pole hledání nad členy; [query] je jen výchozí text, dál ho drží pole samo. */
internal class MemberSearch(val query: String, val onQueryChange: (String) -> Unit)

/**
 * Členové skupiny: bloky zaoblených řádků, stránkované a — u velké skupiny — s hledáním.
 *
 * **Pole hledání NEODJÍŽDÍ se seznamem**, stojí nad ním. Stejná úvaha jako u `ChatPicker`:
 * u skupiny s tisíci členy je hledání to hlavní, a zmizet by mělo přesně ve chvíli, kdy ho
 * uživatel po chvíli marného listování potřebuje.
 */
@Composable
internal fun ProfileMembersList(
    list: MemberList,
    contentPadding: PaddingValues,
    onOpen: (userId: Long) -> Unit,
    /** Dlouhý stisk: nabídka správy člena. Otevře se, jen když je co nabídnout. */
    onManage: (userId: Long) -> Unit,
    onLoadMore: () -> Unit,
    /** `null` = hledání se nenabízí (skupina se vejde na jednu stránku). */
    search: MemberSearch?,
    /** Skupina skrývá členy: server vydal jen správce a musí se to říct. */
    hidden: Boolean,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    Column(modifier.fillMaxSize()) {
        if (hidden) {
            /*
             * Bez vysvětlení vypadalo šestnáct lidí u skupiny s jedenácti tisíci členy jako
             * nedotažený seznam. Tlačítko „zobrazit všechny" tu není čím nahradit — rozhodla
             * o tom skupina, ne aplikace.
             */
            Text(
                stringResource(Res.string.profile_members_hidden),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = contentPadding.calculateStartPadding(layoutDirection) + 4.dp,
                    end = contentPadding.calculateEndPadding(layoutDirection) + 4.dp,
                    top = contentPadding.calculateTopPadding(),
                ),
            )
        }
        if (search != null) {
            SearchPill(
                initialQuery = search.query,
                onQueryChange = search.onQueryChange,
                placeholder = stringResource(Res.string.profile_members_search),
                clearDescription = stringResource(Res.string.chats_search_clear),
                modifier = Modifier.padding(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection),
                    top = contentPadding.calculateTopPadding(),
                ),
            )
        }
        val members = list.members
        when {
            members == null -> Box(
                Modifier.fillMaxSize().padding(contentPadding),
                contentAlignment = Alignment.Center,
            ) { TabLoading() }

            // Prázdno bez dotazu je skupina, jejíž členy server nevydal; tam není co radit.
            members.isEmpty() && list.query.isNotEmpty() -> IntroEmptyState(
                icon = Res.drawable.search_off_24dp,
                title = stringResource(Res.string.profile_members_search_empty),
                text = stringResource(Res.string.chats_search_empty_hint),
                containerColor = Color.Transparent,
                modifier = Modifier.fillMaxSize().padding(bottom = contentPadding.calculateBottomPadding()),
            )

            else -> MemberRows(list, members, contentPadding, onOpen, onManage, onLoadMore)
        }
    }
}

@Composable
private fun MemberRows(
    list: MemberList,
    members: List<ProfileMember>,
    contentPadding: PaddingValues,
    onOpen: (userId: Long) -> Unit,
    onManage: (userId: Long) -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(GROUP_ITEM_SPACING),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(members, key = { _, it -> it.sender.id }) { index, member ->
            if (list.nextOffset != null) LoadMoreAtEnd(index, members.size, onLoadMore)
            /*
             * Ťuknutí otevře soukromý chat s členem (id chatu = id uživatele), takže avatar
             * a jméno letí do hlavičky konverzace týmiž klíči jako ze seznamu chatů. Kromě
             * vlastního účtu: ten vede do Uložených zpráv, které mají místo fotky záložku.
             */
            val flies = member.sender.id != LocalSavedMessagesChatId.current
            ListItem(
                headlineContent = {
                    Text(
                        member.sender.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (flies) Modifier.sharedNavText(chatTitleKey(member.sender.id)) else Modifier,
                    )
                },
                leadingContent = {
                    Avatar(
                        chatId = member.sender.id,
                        title = member.sender.title,
                        avatarFileId = member.sender.avatarFileId,
                        size = MEMBER_AVATAR_SIZE,
                        modifier = if (flies) Modifier.sharedNavElement(chatAvatarKey(member.sender.id)) else Modifier,
                    )
                },
                // Bot je pod jménem, ne ve štítku: štítek patří roli a bot bývá i správce.
                supportingContent = if (member.isBot) {
                    { BotLabel() }
                } else {
                    null
                },
                trailingContent = if (member.hasLabel) {
                    { MemberRoleLabel(member) }
                } else {
                    null
                },
                colors = rowColors(),
                modifier = Modifier
                    .animateItem()
                    .clip(groupItemShape(index, members.size))
                    /*
                     * Ťuknutí otevírá konverzaci jako dřív; správa je dlouhý stisk, stejně jako
                     * akce nad chatem v seznamu. Kdyby ťuknutí otevíralo nabídku, zmizela by
                     * nejčastější cesta „napsat tomuhle člověku".
                     */
                    .combinedClickable(
                        onClick = { onOpen(member.sender.id) },
                        onLongClick = { onManage(member.sender.id) },
                    ),
            )
        }
        if (list.loadingMore) {
            item(key = MEMBERS_LOADING_KEY) { TabLoading() }
        }
    }
}

/** Štítek na konci řádku má vlastník, správce a každý, komu skupina dala vlastní štítek. */
internal val ProfileMember.hasLabel: Boolean
    get() = role != MemberRole.Member || tag != null

/**
 * Role jako tonální štítek — holé barevné slovo na konci řádku vypadalo jako odkaz.
 *
 * - **Vlastník a správce se liší barvou I ikonou.** Vlastník je terciární s hvězdou, správce
 *   sekundární se štítem: se stejnou barvou se vlastník mezi správci ztrácel, a barva sama
 *   nestačí (barvoslepost, paleta se zvýšeným kontrastem) — proto i ikona.
 * - **Vlastní štítek člena ([ProfileMember.tag]) nahrazuje slovo role**, ikona a barva role
 *   zůstávají — „Moderátor" se hvězdou pořád říká, že jde o vlastníka. Tak to ukazují i oficiální
 *   klienti. Běžný člen se štítkem má neutrální štítek bez ikony: nic neřídí.
 */
@Composable
private fun MemberRoleLabel(member: ProfileMember) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (member.role) {
        MemberRole.Owner -> colors.tertiaryContainer to colors.onTertiaryContainer
        MemberRole.Admin -> colors.secondaryContainer to colors.onSecondaryContainer
        MemberRole.Member -> colors.surfaceContainerHigh to colors.onSurfaceVariant
    }
    val icon = when (member.role) {
        MemberRole.Owner -> Res.drawable.star_filled_24dp
        MemberRole.Admin -> Res.drawable.shield_person_filled_24dp
        MemberRole.Member -> null
    }
    val text = member.tag ?: when (member.role) {
        MemberRole.Owner -> stringResource(Res.string.profile_role_owner)
        MemberRole.Admin -> stringResource(Res.string.profile_role_admin)
        MemberRole.Member -> return
    }
    Surface(shape = RoundedCornerShape(percent = 50), color = container, contentColor = content) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .padding(start = if (icon != null) 8.dp else 10.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        ) {
            if (icon != null) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ROLE_ICON_SIZE))
            }
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Štítek píše skupina a může být dlouhý; jméno člena má přednost.
                modifier = Modifier.widthIn(max = MAX_TAG_WIDTH),
            )
        }
    }
}

@Composable
private fun BotLabel() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(painterResource(Res.drawable.smart_toy_24dp), contentDescription = null, modifier = Modifier.size(ROLE_ICON_SIZE))
        Text(stringResource(Res.string.profile_bot))
    }
}

/** Řádky záložek jsou světlé bloky na tónované stránce, jako chaty v seznamu. */
@Composable
private fun rowColors() = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)

/**
 * Tvar dlaždice mřížky: velké zaoblení jen v rozích celého bloku.
 *
 * Neúplný poslední řádek dělá v bloku schod; roh nad ním (poslední dlaždice předposledního
 * řádku) je taky vnější, takže se zaoblí.
 */
internal fun mediaTileShape(index: Int, count: Int, columns: Int = MEDIA_COLUMNS): Shape {
    val row = index / columns
    val column = index % columns
    val lastRow = (count - 1) / columns
    val incomplete = count % columns != 0
    fun corner(outer: Boolean) = if (outer) GROUP_CORNER else INNER_CORNER
    return RoundedCornerShape(
        topStart = corner(index == 0),
        topEnd = corner(index == minOf(columns, count) - 1),
        bottomStart = corner(row == lastRow && column == 0),
        bottomEnd = corner(
            index == count - 1 || (incomplete && row == lastRow - 1 && column == columns - 1),
        ),
    )
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun TabLoading() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        LoadingIndicator()
    }
}

/**
 * Spustí [onLoadMore], jakmile se doroluje blízko konce.
 *
 * Čte se přes `snapshotFlow`, ne v kompozici: index poslední viditelné položky se při scrollu
 * mění každý snímek a rekomponovat kvůli němu celý seznam by bylo drahé.
 */
/**
 * Dotáhne další stránku, jakmile se složí jedna z posledních položek.
 *
 * Skládá se, teprve když je položka blízko viewportu, takže „doroloval se ke konci" pozná sama
 * kompozice. **Přes `layoutInfo` to nefungovalo**: u mřížky se porovnání indexu viditelné položky
 * s počtem minulo a další stránka nedorazila nikdy (u seznamu s jedinou položkou naopak prošlo
 * hned, takže vada byla vidět jen na mřížce). Klíč `count` pustí efekt jednou za stránku.
 */
@Composable
private fun LoadMoreAtEnd(index: Int, count: Int, onLoadMore: () -> Unit) {
    if (index < count - LOAD_MORE_THRESHOLD) return
    val current by rememberUpdatedState(onLoadMore)
    LaunchedEffect(count) { current() }
}

private val FILE_ICON_SIZE = 44.dp
private val FILE_ICON_CORNER = 8.dp

/** Ztmavení obalu pod tlačítkem přehrání. */
private const val COVER_SCRIM_ALPHA = 0.35f
private val MEMBER_AVATAR_SIZE = 40.dp
private const val MEMBERS_LOADING_KEY = "members-loading"
private val ROLE_ICON_SIZE = 14.dp
private val MAX_TAG_WIDTH = 120.dp

/** Podklad symbolu přehrání na dlaždici videa — na světlé fotce by bílá ikona zanikla. */
private const val VIDEO_BADGE_ALPHA = 0.55f

/**
 * Adresa, jak ji uvidí uživatel.
 *
 * `mailto:` se zahazuje — filtr odkazů od TDLib bere i e-maily a schéma u nich nic neříká;
 * ostatní schémata se nechávají, protože `https` od `tg` odlišit potřeba je.
 */
internal fun displayUrl(url: String): String = url.removePrefix("mailto:")
