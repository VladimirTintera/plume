package eu.tintera.plume.features.stories

import androidx.compose.runtime.collectAsState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import eu.tintera.plume.resources.story_cover_menu
import eu.tintera.plume.resources.image_24dp
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.koin.compose.koinInject
import eu.tintera.plume.core.ui.StoryVideo
import eu.tintera.plume.core.ui.rememberAttachmentPicker
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.StoryAudience
import eu.tintera.plume.resources.story_select_people
import eu.tintera.plume.resources.story_replace_media
import eu.tintera.plume.resources.add_photo_alternate_24dp
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.telegram.ActiveStories
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.LiveMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.ConfirmDialog
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.resources.invite_links_share_failed
import eu.tintera.plume.resources.story_link_copied
import eu.tintera.plume.resources.story_share_link
import eu.tintera.plume.resources.story_copy_link
import eu.tintera.plume.resources.story_forward
import eu.tintera.plume.resources.share_24dp
import eu.tintera.plume.resources.link_24dp
import eu.tintera.plume.resources.forward_24dp
import eu.tintera.plume.core.files.MediaSharer
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.TextClipboard
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.features.report.ReportSheet
import eu.tintera.plume.core.ui.DarkSystemBars
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.core.ui.asRelativeTime
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.rememberFileState
import eu.tintera.plume.core.video.VideoSurface
import eu.tintera.plume.core.video.rememberVideoPlayer
import eu.tintera.plume.features.media.ON_SCRIM
import eu.tintera.plume.resources.push_pin_24dp
import eu.tintera.plume.resources.keep_off_24dp
import eu.tintera.plume.resources.history_toggle_off_24dp
import eu.tintera.plume.resources.visibility_24dp
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.story_pin
import eu.tintera.plume.resources.story_unpin
import eu.tintera.plume.resources.story_profile_add
import eu.tintera.plume.resources.story_profile_remove
import eu.tintera.plume.resources.story_audience_menu
import eu.tintera.plume.resources.story_edit_caption
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.archive_24dp
import eu.tintera.plume.resources.flag_24dp
import eu.tintera.plume.resources.folder_24dp
import eu.tintera.plume.resources.story_albums_menu
import eu.tintera.plume.resources.story_report
import eu.tintera.plume.resources.profile_more
import eu.tintera.plume.resources.more_vert_24dp
import eu.tintera.plume.resources.story_hide
import eu.tintera.plume.resources.story_unhide
import eu.tintera.plume.resources.unarchive_24dp
import eu.tintera.plume.resources.story_delete_confirm
import eu.tintera.plume.resources.story_delete_text
import eu.tintera.plume.resources.story_delete_title
import eu.tintera.plume.resources.common_close
import eu.tintera.plume.resources.stories_next
import eu.tintera.plume.resources.stories_previous
import eu.tintera.plume.resources.story_live_unsupported
import eu.tintera.plume.resources.story_unavailable
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

/**
 * Prohlížeč příběhů přes celou obrazovku.
 *
 * Autoři jsou stránky pageru (tah do strany), uvnitř autora se jde ťuknutím: levá třetina zpět,
 * zbytek dál. Podržení pozastaví, tah dolů zavře. Na široké obrazovce je příběh uprostřed
 * v poměru 9 : 16 — tak ho autor natočil.
 */
@Composable
fun StoriesScreen(
    viewModel: StoriesViewModel,
    onBack: () -> Unit,
    /** Poslat příběh do chatu: výběr cílů nad prohlížečem (tentýž jako u přeposlání zpráv). */
    onForward: (posterChatId: Long, storyId: Int) -> Unit = { _, _ -> },
    /** Vyměnit médium vlastního příběhu: editor nad prohlížečem s vybranou fotkou nebo videem. */
    onReplaceMedia: (target: StoryEditTarget, path: String, video: Boolean) -> Unit = { _, _, _ -> },
) {
    // Podklad je tmavý v každém motivu, ikony lišt proto světlé (jako prohlížeč médií).
    DarkSystemBars()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val reactionChoices by viewModel.reactionChoices.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val viewers by viewModel.viewers.collectAsStateWithLifecycle()
    val reportWizard by viewModel.report.wizard.collectAsStateWithLifecycle()
    val albumsSheet by viewModel.albums.collectAsStateWithLifecycle()
    val audienceSheet by viewModel.audience.collectAsStateWithLifecycle()
    val captionEdit by viewModel.captionEdit.collectAsStateWithLifecycle()
    val pickingPeople by viewModel.pickingPeople.collectAsStateWithLifecycle()
    val pinned by viewModel.pinned.collectAsStateWithLifecycle()
    val storyVideoSupported = koinInject<StoryVideo>().supported
    val clipboard = koinInject<TextClipboard>()
    val sharer = koinInject<MediaSharer>()
    // Nad prohlížečem je jiná obrazovka (editor příběhu): příběh pod ní stojí. Jinak dojel,
    // prohlížeč se zavřel a po uložení nebylo kam se vrátit (ověřeno na emulátoru).
    // collectAsState, ne …WithLifecycle: to by sběr při zastavení vypnulo a změnu nepoznalo.
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val covered = !lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    // Příběh, kterému se vybírá nové médium. **Po dobu výběru příběh stojí**: výběr fotek je
    // panel přes aplikaci, ne celá obrazovka, takže prohlížeč pod ním dál běží — příběh dojel,
    // prohlížeč se zavřel a vybraná fotka pak neměla kam dojít (ověřeno na emulátoru).
    var replaceKey by remember { mutableStateOf<StoryKey?>(null) }
    // Návrat z editoru po výměně média: příběh se dočte znovu (nové soubory). Zrušený výběr
    // nic nevrátí — pauza se po návratu uvolní sama, až by výsledek přišel (příprava fotky trvá).
    LifecycleResumeEffect(viewModel) {
        viewModel.reloadReplaced()
        onPauseOrDispose { }
    }
    val replaceScope = rememberCoroutineScope()
    LifecycleResumeEffect(replaceKey) {
        val pending = replaceKey
        val release = if (pending != null) {
            replaceScope.launch {
                delay(REPLACE_PICK_GRACE_MS)
                if (replaceKey == pending) replaceKey = null
            }
        } else {
            null
        }
        onPauseOrDispose { release?.cancel() }
    }
    val replacePicker = rememberAttachmentPicker(
        onPicked = { picked ->
            val key = replaceKey ?: return@rememberAttachmentPicker
            replaceKey = null
            val target = StoryEditTarget(key.posterChatId, key.storyId, state.pages[key]?.caption.orEmpty())
            when (val first = picked.firstOrNull()) {
                is OutgoingAttachment.Photo -> {
                    viewModel.replacingMedia(key)
                    onReplaceMedia(target, first.path, false)
                }
                // Video převede jen platforma s kodérem (desktop ho nemá).
                is OutgoingAttachment.Video -> if (storyVideoSupported) {
                    viewModel.replacingMedia(key)
                    onReplaceMedia(target, first.path, true)
                }
                else -> Unit
            }
        },
        onError = {},
    )
    pickingPeople?.let { sheet ->
        StoryAudiencePicker(
            title = stringResource(Res.string.story_select_people),
            initial = (sheet.audience as? StoryAudience.Selected)?.userIds.orEmpty().toSet(),
            contactsOnly = false,
            onDone = viewModel::confirmPeople,
            onBack = viewModel::closePeoplePicker,
        )
        return
    }
    val live by viewModel.live.collectAsStateWithLifecycle()
    val currentOnBack by rememberUpdatedState(onBack)
    LaunchedEffect(state.closed) { if (state.closed) currentOnBack() }
    if (state.posters.isEmpty()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim))
        return
    }

    val pager = rememberPagerState(initialPage = state.poster) { state.posters.size }
    // Tahem vybraný autor do stavu, ťuknutím vybraný (konec příběhů autora) do pageru.
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { viewModel.showPoster(it) }
    }
    LaunchedEffect(state.poster) {
        if (pager.currentPage != state.poster) pager.animateScrollToPage(state.poster)
    }

    val scope = rememberCoroutineScope()
    val dismiss = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var height by remember { mutableFloatStateOf(1f) }
    val dragState = rememberDraggableState { delta ->
        scope.launch { dismiss.snapTo((dismiss.value + delta).coerceAtLeast(0f)) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { height = it.height.toFloat().coerceAtLeast(1f) }
            .graphicsLayer { alpha = 1f - (dismiss.value / height * DISMISS_FADE).coerceIn(0f, 0.6f) }
            .background(MaterialTheme.colorScheme.scrim)
            // Lišty a výřez, ne klávesnice: s ní by se příběh zmenšil. Nad klávesnici jde jen
            // lišta s odpovědí (`imePadding` v ní; lišty tu už spotřebované odečte sama).
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStarted = { dragging = true },
                onDragStopped = {
                    dragging = false
                    if (dismiss.value > height * DISMISS_THRESHOLD) currentOnBack() else dismiss.animateTo(0f)
                },
            ),
    ) {
        HorizontalPager(
            state = pager,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize().graphicsLayer { translationY = dismiss.value },
        ) { page ->
            val author = state.posters[page]
            val index = state.storyIndex(page)
            val key = state.keyAt(page)
            val active = page == state.poster && pager.settledPage == page && !pager.isScrollInProgress
            val story = key?.let { state.pages[it] }
            // Správa vlastního příběhu v ⋮: jen co TDLib u příběhu dovolí.
            val own = if (key != null && story != null && story.isOwn && story.isAvailable) {
                val isPinned = key.storyId in pinned[key.posterChatId].orEmpty()
                // Obálka jen u videa; editor potřebuje video stažené — dokud není, položka chybí.
                val coverVideo = (story.content as? MessageMedia.Video)?.takeIf { story.canBeEdited }
                val coverPath = coverVideo?.let { (rememberFileState(it.fileId) as? FileState.Ready)?.path }
                OwnStoryMenu(
                    onEditCaption = { viewModel.openCaptionEdit(key) }.takeIf { story.canBeEdited },
                    onEditCover = coverPath?.let { path ->
                        {
                            viewModel.replacingMedia(key)
                            onReplaceMedia(StoryEditTarget(key.posterChatId, key.storyId, story.caption, coverOnly = true), path, true)
                        }
                    },
                    onReplaceMedia = {
                        replaceKey = key
                        replacePicker.pickMedia()
                    }.takeIf { story.canBeEdited },
                    onAudience = { viewModel.openAudience(key) }.takeIf { story.audience != null },
                    onToggleProfile = { viewModel.setOnProfile(key, !story.isOnProfile) }.takeIf { story.canToggleOnProfile },
                    onProfile = story.isOnProfile,
                    // Připnout jde jen příběh, který v profilu je.
                    onTogglePin = { viewModel.setPinned(key, !isPinned) }.takeIf { story.canToggleOnProfile && story.isOnProfile },
                    pinned = isPinned,
                )
            } else {
                null
            }
            // Poslat dál a odkaz: jen co TDLib u příběhu dovolí (`canBeForwarded`, veřejné jméno autora).
            val share = if (key != null && story != null && story.isAvailable && story.canBeForwarded) {
                val link = story.link
                StoryShare(
                    onForward = { onForward(key.posterChatId, key.storyId) },
                    onCopyLink = link?.let {
                        {
                            scope.launch {
                                clipboard.copy(it)
                                if (!clipboard.confirmsCopy) viewModel.showNotice(UiText(Res.string.story_link_copied))
                            }
                        }
                    },
                    onShareLink = link?.let {
                        { if (!sharer.shareText(it)) viewModel.showNotice(UiText(Res.string.invite_links_share_failed)) }
                    },
                )
            } else {
                null
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Sousední autor se při tahu lehce zmenší — stránky nejsou jedna plocha.
                        val offset = abs(pager.getOffsetDistanceInPages(page)).coerceIn(0f, 1f)
                        val scale = 1f - offset * PAGE_SCALE
                        scaleX = scale
                        scaleY = scale
                    },
                contentAlignment = Alignment.Center,
            ) {
                StoryPage(
                    author = author,
                    index = index,
                    story = story,
                    live = live?.takeIf { it.key == key },
                    liveSupported = viewModel.liveSupported,
                    playback = StoryPlayback(
                        active = active,
                        // Seznam diváků přes příběh — příběh pod ním stojí.
                        paused = dragging || viewers != null || reportWizard != null || albumsSheet != null ||
                            audienceSheet != null || captionEdit != null || replaceKey != null || covered,
                        restart = state.restart,
                    ),
                    reactionChoices = reactionChoices,
                    navigation = StoryNavigation(
                        onNext = viewModel::next,
                        onPrevious = viewModel::previous,
                        onClose = onBack,
                    ),
                    barActions = StoryBarActions(
                        onReact = { reaction -> key?.let { viewModel.react(it, reaction) } },
                        onReply = { text, onResult -> key?.let { viewModel.reply(it, text, onResult) } },
                        onLiveSend = viewModel::sendLiveMessage,
                        onLiveDelete = { message, spam -> viewModel.deleteLiveMessage(message.id, spam) },
                        onDelete = { key?.let(viewModel::delete) },
                        onViewers = { key?.let(viewModel::openViewers) },
                    ),
                    menu = StoryMenu(
                        onToggleHidden = { viewModel.toggleHidden(author.chatId) },
                        onReport = { key?.let(viewModel::reportStory) },
                        onAlbums = { key?.let(viewModel::openAlbums) },
                        own = own,
                        share = share,
                    ),
                )
            }
        }

        audienceSheet?.let { sheet ->
            StoryAudiencePickerSheet(
                sheet = sheet,
                onChoose = viewModel::setAudience,
                onDismiss = viewModel::closeAudience,
                onPickPeople = viewModel::pickPeople,
            )
        }

        captionEdit?.let { key ->
            StoryCaptionDialog(
                initial = state.pages[key]?.caption.orEmpty(),
                onConfirm = viewModel::editCaption,
                onDismiss = viewModel::closeCaptionEdit,
            )
        }

        albumsSheet?.let { albums ->
            StoryAlbumsSheet(
                state = albums,
                onToggle = viewModel::toggleAlbum,
                onCreate = viewModel::createAlbum,
                onRename = viewModel::renameAlbum,
                onDelete = viewModel::deleteAlbum,
                onReorder = viewModel::reorderAlbums,
                onOpenAlbumStories = viewModel::openAlbumStories,
                onReorderAlbumStories = viewModel::reorderAlbumStories,
                onCloseAlbumStories = viewModel::closeAlbumStories,
                onDismiss = viewModel::closeAlbums,
            )
        }

        reportWizard?.let { wizard ->
            ReportSheet(
                wizard = wizard,
                onChoose = viewModel.report::choose,
                onSubmitText = viewModel.report::submitText,
                onDismiss = viewModel.report::cancel,
            )
        }

        viewers?.let { opened ->
            StoryViewersSheet(
                state = opened,
                viewCount = state.pages[state.current]?.viewCount,
                onLoadMore = viewModel::loadMoreViewers,
                onDismiss = viewModel::closeViewers,
            )
        }

        StoryNotice(
            notice = notice,
            onShown = viewModel::noticeShown,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = STORY_BAR_HEIGHT + 8.dp),
        )
    }
}

@Composable
private fun StoryPage(
    author: ActiveStories,
    index: Int,
    story: MessageMedia.Story?,
    live: LiveStoryState?,
    liveSupported: Boolean,
    playback: StoryPlayback,
    reactionChoices: List<String>,
    navigation: StoryNavigation,
    barActions: StoryBarActions,
    menu: StoryMenu,
) {
    var held by remember { mutableStateOf(false) }
    // Psaní odpovědi a výběr reakce příběh zastaví — jinak by uživateli odjel pod rukama.
    var composing by remember(author.chatId, index) { mutableStateOf(false) }
    // Velké emoji přes příběh po dané reakci; klíč roste, ať se táž reakce ukáže znovu.
    var burst by remember(author.chatId, index) { mutableStateOf<Pair<Int, String>?>(null) }
    val bar = story != null && story.isAvailable && !story.isLive && !story.isOwn
    val ownBar = story != null && story.isAvailable && story.isOwn
    // Chat vysílání: jen u cizího živého příběhu, který tu jde sledovat.
    val liveBar = story != null && story.live != null && liveSupported && !story.isOwn
    val liveRunning = live != null && (live.phase == LivePhase.Connecting || live.phase == LivePhase.Live)
    var confirmDelete by remember(author.chatId, index) { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                StoryCard(
                    author = author,
                    index = index,
                    story = story,
                    live = live,
                    liveSupported = liveSupported,
                    playback = playback.copy(paused = playback.paused || held || composing || confirmDelete),
                    onHeld = { held = it },
                    navigation = navigation,
                    menu = menu,
                )
                ReactionBurst(burst, Modifier.align(Alignment.Center))
                if (liveBar && liveRunning && live != null) {
                    LiveChatMessages(
                        messages = live.messages,
                        canModerate = live.canModerate,
                        onDelete = barActions.onLiveDelete,
                        // S klávesnicí jede lišta s polem nahoru a chat nad ni. Odsazení před
                        // výškou, ať se chat posune, a ne zmenší; místo pro lištu pod obrazem už je.
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .imePadding()
                            .fillMaxWidth()
                            .fillMaxHeight(LIVE_CHAT_HEIGHT),
                    )
                    LiveReactionBurst(live.reaction, Modifier.align(Alignment.Center))
                }
            }
            if (bar || ownBar || liveBar) Spacer(Modifier.height(STORY_BAR_HEIGHT))
        }
        if (ownBar && story != null) {
            OwnStoryBar(
                viewCount = story.viewCount,
                onViewers = barActions.onViewers,
                onDelete = { confirmDelete = true },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (confirmDelete) {
            ConfirmDialog(
                title = stringResource(Res.string.story_delete_title),
                text = stringResource(Res.string.story_delete_text),
                confirmLabel = stringResource(Res.string.story_delete_confirm),
                icon = Res.drawable.delete_24dp,
                destructive = true,
                onConfirm = {
                    confirmDelete = false
                    barActions.onDelete()
                },
                onDismiss = { confirmDelete = false },
            )
        }
        if (liveBar && liveRunning && live != null) {
            LiveMessageBar(live, barActions.onLiveSend, Modifier.align(Alignment.BottomCenter))
        }
        if (bar && story != null) {
            StoryReplyBar(
                story = story,
                reactionChoices = reactionChoices,
                onComposingChange = { composing = it },
                onReact = { reaction ->
                    if (reaction != story.chosenReaction) burst = ((burst?.first ?: 0) + 1) to reaction
                    barActions.onReact(reaction)
                },
                onReply = barActions.onReply,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Příběh sám: obsah, průběh a hlavička, ťuknutí a podržení. */
@Composable
private fun StoryCard(
    author: ActiveStories,
    index: Int,
    story: MessageMedia.Story?,
    live: LiveStoryState?,
    liveSupported: Boolean,
    playback: StoryPlayback,
    onHeld: (Boolean) -> Unit,
    navigation: StoryNavigation,
    menu: StoryMenu,
) {
    val onNext = navigation.onNext
    val onPrevious = navigation.onPrevious
    var progress by remember(author.chatId, index) { mutableFloatStateOf(0f) }
    // Otevřená nabídka autora příběh zastaví, jako podržení.
    var menuOpen by remember(author.chatId) { mutableStateOf(false) }
    val next = stringResource(Res.string.stories_next)
    val previous = stringResource(Res.string.stories_previous)

    Box(
        Modifier
            .aspectRatio(STORY_ASPECT, matchHeightConstraintsFirst = true)
            .clip(ShapeDefaults.Large)
            .background(Color.Black)
            .pointerInput(onNext, onPrevious) {
                detectTapGestures(
                    onPress = {
                        onHeld(true)
                        tryAwaitRelease()
                        onHeld(false)
                    },
                    // Prázdné podržení: bez něj by puštění po pauze počítalo jako ťuknutí a přeskočilo.
                    onLongPress = {},
                    onTap = { offset -> if (offset.x < size.width * BACK_ZONE) onPrevious() else onNext() },
                )
            }
            .semantics {
                customActions = listOf(
                    CustomAccessibilityAction(next) { onNext(); true },
                    CustomAccessibilityAction(previous) { onPrevious(); true },
                )
            },
    ) {
        StoryContent(
            story = story,
            live = live,
            liveSupported = liveSupported,
            active = playback.active,
            paused = playback.paused || menuOpen,
            restart = playback.restart,
            onProgress = { progress = it },
            onFinished = onNext,
        )

        StoryHeader(
            author = author,
            index = index,
            progress = progress,
            postedAt = story?.postedAt?.takeIf { it > 0 } ?: author.stories.getOrNull(index)?.date ?: 0,
            liveViewers = if (story?.live != null && liveSupported) live?.viewers ?: 0 else null,
            onClose = navigation.onClose,
            menuOpen = menuOpen,
            onMenuOpenChange = { menuOpen = it },
            onToggleHidden = menu.onToggleHidden,
            onReport = menu.onReport.takeIf { story != null && story.isAvailable && !story.isOwn },
            onAlbums = menu.onAlbums.takeIf { story != null && story.isOwn && story.canBeAddedToAlbum },
            own = menu.own,
            share = menu.share,
        )

        val caption = story?.caption.orEmpty()
        if (caption.isNotBlank()) {
            Text(
                caption,
                color = ON_SCRIM,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = CAPTION_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = SHADE_ALPHA))))
                    .padding(horizontal = 16.dp, vertical = 20.dp),
            )
        }
    }
}

/**
 * Obsah příběhu a jeho průběh. Fotka běží [PHOTO_DURATION_MS] od chvíle, kdy je stažená; video
 * tak dlouho, jak hraje. Kde video přehrát nejde (desktop), stojí náhled po délku videa.
 */
@Composable
private fun BoxScope.StoryContent(
    story: MessageMedia.Story?,
    live: LiveStoryState?,
    liveSupported: Boolean,
    active: Boolean,
    paused: Boolean,
    restart: Int,
    onProgress: (Float) -> Unit,
    onFinished: () -> Unit,
) {
    val currentPaused by rememberUpdatedState(paused)
    val finish by rememberUpdatedState(onFinished)
    val media = story?.content
    when {
        story == null -> LoadingIndicator(color = ON_SCRIM, modifier = Modifier.align(Alignment.Center))

        story.live != null && liveSupported -> LiveStoryContent(live, active, paused, onProgress, onFinished)

        media is MessageMedia.Video -> {
            val file = if (active) rememberFileState(media.fileId) else FileState.Pending
            val path = (file as? FileState.Ready)?.path
            val player = if (active) rememberVideoPlayer() else null
            var playing by remember(media.fileId) { mutableStateOf(false) }
            if (!playing) {
                RemoteImage(
                    fileId = media.thumbnailFileId ?: media.fileId,
                    placeholder = media.minithumbnail,
                    contentScale = ContentScale.Crop,
                    background = Color.Black,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (player != null && path != null) {
                VideoSurface(player, Modifier.fillMaxSize())
                LaunchedEffect(player, path, restart) {
                    player.load(path)
                    player.play()
                    var fallbackMs = 0L
                    var last = withFrameMillis { it }
                    while (true) {
                        val now = withFrameMillis { it }
                        val delta = now - last
                        last = now
                        if (player.isFailed) {
                            // Desktop přehrávač nemá: náhled stojí po délku videa.
                            if (!currentPaused) fallbackMs += delta
                            val total = media.durationSeconds.coerceAtLeast(1) * 1000L
                            onProgress((fallbackMs.toFloat() / total).coerceIn(0f, 1f))
                            if (fallbackMs >= total) break
                            continue
                        }
                        if (currentPaused && player.isPlaying) player.pause()
                        if (!currentPaused && !player.isPlaying && !player.isEnded) player.play()
                        if (player.isPlaying) playing = true
                        val duration = player.durationMs
                        if (duration > 0) onProgress((player.positionMs.toFloat() / duration).coerceIn(0f, 1f))
                        if (player.isEnded) break
                    }
                    finish()
                }
            }
        }

        else -> {
            val photo = media as? MessageMedia.Photo
            when {
                photo != null -> RemoteImage(
                    fileId = photo.fullFileId,
                    thumbnailFileId = photo.fileId,
                    placeholder = photo.minithumbnail,
                    contentScale = ContentScale.Crop,
                    background = Color.Black,
                    modifier = Modifier.fillMaxSize(),
                )

                else -> Text(
                    stringResource(if (story.isLive) Res.string.story_live_unsupported else Res.string.story_unavailable),
                    color = ON_SCRIM,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(32.dp),
                )
            }
            if (active) {
                // Fotka se počítá od chvíle, kdy je vidět; text bez obsahu hned.
                val ready = photo == null || rememberFileState(photo.fullFileId) is FileState.Ready
                LaunchedEffect(story.storyId, ready, restart) {
                    if (!ready) return@LaunchedEffect
                    var elapsed = 0L
                    var last = withFrameMillis { it }
                    while (elapsed < PHOTO_DURATION_MS) {
                        val now = withFrameMillis { it }
                        if (!currentPaused) elapsed += now - last
                        last = now
                        onProgress(elapsed.toFloat() / PHOTO_DURATION_MS)
                    }
                    finish()
                }
            }
        }
    }
}

/** Dílky průběhu, autor, čas a zavření. */
@Composable
private fun BoxScope.StoryHeader(
    author: ActiveStories,
    index: Int,
    progress: Float,
    postedAt: Int,
    /** Kolik lidí sleduje živý příběh; `null` = není živý (ukáže se čas zveřejnění). */
    liveViewers: Int?,
    onClose: () -> Unit,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onToggleHidden: () -> Unit,
    /** Nahlásit tenhle příběh; `null` = nejde (vlastní, nenačtený). */
    onReport: (() -> Unit)?,
    /** Alba vlastního příběhu; `null` = nejde (cizí, nesmí do alba). */
    onAlbums: (() -> Unit)?,
    /** Správa vlastního příběhu; `null` u cizího. */
    own: OwnStoryMenu?,
    /** Poslat dál a odkaz; `null`, když příběh dál poslat nejde. */
    share: StoryShare?,
) {
    Column(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = SHADE_ALPHA), Color.Transparent)))
            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 16.dp),
    ) {
        Row(Modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            author.stories.forEachIndexed { i, _ ->
                val fill = when {
                    i < index -> 1f
                    i == index -> progress
                    else -> 0f
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(ON_SCRIM.copy(alpha = TRACK_ALPHA)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fill)
                            .height(3.dp)
                            .background(ON_SCRIM),
                    )
                }
            }
        }
        Row(
            Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Avatar(
                chatId = author.avatarColorId,
                title = author.title,
                avatarFileId = author.avatarFileId,
                size = 36.dp,
                shape = author.kind.avatarShape(),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    author.title,
                    color = ON_SCRIM,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (liveViewers != null) {
                    LiveBadge(liveViewers)
                } else if (postedAt > 0) {
                    Text(
                        postedAt.asRelativeTime(),
                        color = ON_SCRIM.copy(alpha = SECONDARY_ALPHA),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            // Skrýt do archivu / vrátit z něj. `canBeArchived` platí jen v hlavním seznamu —
            // u skrytého autora je `false`, a vrátit ho jít musí. Vlastní příběhy nejdou ani jedno.
            val archive = if (author.canBeArchived || author.isHidden) {
                if (author.isHidden) {
                    MenuAction(stringResource(Res.string.story_unhide), Res.drawable.unarchive_24dp) {
                        onMenuOpenChange(false)
                        onToggleHidden()
                    }
                } else {
                    MenuAction(stringResource(Res.string.story_hide), Res.drawable.archive_24dp) {
                        onMenuOpenChange(false)
                        onToggleHidden()
                    }
                }
            } else {
                null
            }
            // Alba jen u vlastního příběhu, který do nich jít smí (`canBeAddedToAlbum`).
            val albums = onAlbums?.let { open ->
                MenuAction(stringResource(Res.string.story_albums_menu), Res.drawable.folder_24dp) {
                    onMenuOpenChange(false)
                    open()
                }
            }
            fun action(label: String, icon: DrawableResource, run: () -> Unit) = MenuAction(label, icon) {
                onMenuOpenChange(false)
                run()
            }
            // Poslat dál a odkaz nahoře: u cizího příběhu je to nejčastější volba v nabídce.
            val shareGroup = listOfNotNull(
                share?.onForward?.let { action(stringResource(Res.string.story_forward), Res.drawable.forward_24dp, it) },
                share?.onCopyLink?.let { action(stringResource(Res.string.story_copy_link), Res.drawable.link_24dp, it) },
                share?.onShareLink?.let { action(stringResource(Res.string.story_share_link), Res.drawable.share_24dp, it) },
            )
            val ownEdit = listOfNotNull(
                own?.onEditCaption?.let { action(stringResource(Res.string.story_edit_caption), Res.drawable.edit_24dp, it) },
                own?.onReplaceMedia?.let { action(stringResource(Res.string.story_replace_media), Res.drawable.add_photo_alternate_24dp, it) },
                own?.onEditCover?.let { action(stringResource(Res.string.story_cover_menu), Res.drawable.image_24dp, it) },
                own?.onAudience?.let { action(stringResource(Res.string.story_audience_menu), Res.drawable.visibility_24dp, it) },
            )
            val ownProfile = listOfNotNull(
                own?.onToggleProfile?.let {
                    if (own.onProfile) {
                        action(stringResource(Res.string.story_profile_remove), Res.drawable.history_toggle_off_24dp, it)
                    } else {
                        action(stringResource(Res.string.story_profile_add), Res.drawable.history_toggle_off_24dp, it)
                    }
                },
                own?.onTogglePin?.let {
                    if (own.pinned) {
                        action(stringResource(Res.string.story_unpin), Res.drawable.keep_off_24dp, it)
                    } else {
                        action(stringResource(Res.string.story_pin), Res.drawable.push_pin_24dp, it)
                    }
                },
            )
            // Nahlásit jde cizí příběh, který je vidět; vlastní ne. Vlastní skupina a chybová
            // barva, jako u každé nevratné akce v nabídce.
            val report = onReport?.let { report ->
                MenuAction(stringResource(Res.string.story_report), Res.drawable.flag_24dp, destructive = true) {
                    onMenuOpenChange(false)
                    report()
                }
            }
            if (archive != null || report != null || albums != null || shareGroup.isNotEmpty() || ownEdit.isNotEmpty() || ownProfile.isNotEmpty()) {
                Box {
                    IconButton(onClick = { onMenuOpenChange(true) }, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(Res.drawable.more_vert_24dp), stringResource(Res.string.profile_more), tint = ON_SCRIM)
                    }
                    ExpressiveMenu(
                        expanded = menuOpen,
                        onDismiss = { onMenuOpenChange(false) },
                        groups = listOf(shareGroup, ownEdit, ownProfile + listOfNotNull(albums), listOfNotNull(archive), listOfNotNull(report))
                            .filter { it.isNotEmpty() },
                    )
                }
            }
            IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(Res.drawable.close_24dp), stringResource(Res.string.common_close), tint = ON_SCRIM)
            }
        }
    }
}

/** Poměr stran příběhu: na výšku, jak ho natáčí telefon. */
private const val STORY_ASPECT = 9f / 16f

/** Jak dlouho stojí fotka, než se jde dál. */
private const val PHOTO_DURATION_MS = 6_000L

/** Levá část příběhu, kde ťuknutí vrací zpět; zbytek jde dál (dál se ťuká častěji). */
private const val BACK_ZONE = 0.3f

private const val PAGE_SCALE = 0.08f
private const val SHADE_ALPHA = 0.5f
private const val TRACK_ALPHA = 0.35f
private const val SECONDARY_ALPHA = 0.75f
private const val CAPTION_LINES = 6

/** Kolik výšky obrazu smí zabrat chat živého vysílání. */
private const val LIVE_CHAT_HEIGHT = 0.4f

/** Jak daleko dolů (v podílu výšky) musí tah zajít, aby se prohlížeč zavřel. */
private const val DISMISS_THRESHOLD = 0.18f

/** Jak rychle při zavíracím tahu prosvítá obsah pod prohlížečem. */
private const val DISMISS_FADE = 2.5f

/** Jestli příběh běží: průběh se počítá a video hraje jen u aktivního, nezastaveného. */
internal data class StoryPlayback(
    /** Autor je vybraný a pager dosedl. */
    val active: Boolean,
    /** Zastaveno: podržení, psaní odpovědi, otevřený sheet nad příběhem. */
    val paused: Boolean,
    /** Roste s každým „pusť znovu od začátku" ([StoriesState.restart]). */
    val restart: Int,
)

/** Pohyb mezi příběhy (ťuknutí do stran, dohrání) a zavření prohlížeče. */
internal data class StoryNavigation(
    val onNext: () -> Unit,
    val onPrevious: () -> Unit,
    val onClose: () -> Unit,
)

/**
 * Akce z lišty pod příběhem: reakce a odpověď u cizího, chat živého vysílání, diváci
 * a smazání u vlastního.
 */
internal data class StoryBarActions(
    val onReact: (String) -> Unit,
    val onReply: (String, (Boolean) -> Unit) -> Unit,
    val onLiveSend: (String, (Boolean) -> Unit) -> Unit,
    val onLiveDelete: (LiveMessage, Boolean) -> Unit,
    val onDelete: () -> Unit,
    val onViewers: () -> Unit,
)

/** Nabídka ⋮ v hlavičce příběhu; co u kterého příběhu nejde, vyřadí `StoryCard`. */
internal data class StoryMenu(
    val onToggleHidden: () -> Unit,
    val onReport: () -> Unit,
    val onAlbums: () -> Unit,
    /** Správa vlastního příběhu; `null` u cizího. */
    val own: OwnStoryMenu?,
    /** Poslat dál a odkaz; `null`, když příběh dál poslat nejde. */
    val share: StoryShare? = null,
)

/**
 * Poslat příběh dál do chatu a jeho odkaz. Odkaz (`null`) má jen příběh autora s veřejným
 * jménem — `t.me/jméno/s/N` bez jména neexistuje.
 */
internal class StoryShare(
    val onForward: () -> Unit,
    val onCopyLink: (() -> Unit)?,
    val onShareLink: (() -> Unit)?,
)

/**
 * Správa vlastního příběhu v nabídce ⋮. `null` u akce = TDLib ji u příběhu nedovolí
 * (`canBeEdited`, `canSetPrivacySettings`, `canToggleIsPostedToChatPage`).
 */
internal class OwnStoryMenu(
    val onEditCaption: (() -> Unit)?,
    val onEditCover: (() -> Unit)?,
    val onReplaceMedia: (() -> Unit)?,
    val onAudience: (() -> Unit)?,
    val onToggleProfile: (() -> Unit)?,
    val onProfile: Boolean,
    val onTogglePin: (() -> Unit)?,
    val pinned: Boolean,
)

/** Jak dlouho po návratu z výběru fotek čekat na výsledek, než se pauza uvolní (zrušený výběr nic nevrátí). */
private const val REPLACE_PICK_GRACE_MS = 3_000L
