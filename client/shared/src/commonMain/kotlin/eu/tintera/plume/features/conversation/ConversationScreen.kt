package eu.tintera.plume.features.conversation

import eu.tintera.plume.resources.edit_note_24dp
import eu.tintera.plume.resources.conversation_send_rewrite
import eu.tintera.plume.core.ai.ScamKind
import eu.tintera.plume.core.telegram.SpeechTranscript
import eu.tintera.plume.resources.remind_time
import eu.tintera.plume.core.ui.isFromHardwareKeyboard
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.clearAndSetSemantics
import eu.tintera.plume.core.ui.RollingNumber
import eu.tintera.plume.core.ui.readString
import eu.tintera.plume.resources.forum_24dp
import eu.tintera.plume.resources.saved_view_as_chats
import eu.tintera.plume.resources.saved_topic_read_only
import eu.tintera.plume.resources.saved_topic_open_chat
import eu.tintera.plume.resources.service_upgraded_open_new
import eu.tintera.plume.core.ui.savedTopicTitle
import eu.tintera.plume.core.ui.SavedTopicAvatar
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.video.VideoSurface
import eu.tintera.plume.core.telegram.SavedTopicKind
import androidx.compose.runtime.DisposableEffect
import eu.tintera.plume.core.ui.resolveNow
import eu.tintera.plume.core.ui.threshold
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import eu.tintera.plume.core.camera.rememberVideoNoteCamera
import eu.tintera.plume.core.camera.rememberVideoNotePermission
import eu.tintera.plume.core.settings.RecordMode
import eu.tintera.plume.resources.conversation_camera_permission
import eu.tintera.plume.resources.conversation_record_hint_video_note
import eu.tintera.plume.resources.conversation_record_hint_voice
import eu.tintera.plume.resources.conversation_record_video_note
import eu.tintera.plume.resources.conversation_send_video_note
import eu.tintera.plume.resources.videocam_24dp
import eu.tintera.plume.core.telegram.SpeechTrial
import eu.tintera.plume.core.telegram.TranscribeOffer
import eu.tintera.plume.core.telegram.codeBlockAt
import androidx.compose.material3.IconToggleButton
import eu.tintera.plume.resources.format_open
import eu.tintera.plume.resources.text_format_24dp
import eu.tintera.plume.core.telegram.ComposedText
import eu.tintera.plume.core.telegram.linkAt
import eu.tintera.plume.core.ui.SwipeDirection
import eu.tintera.plume.core.ui.detectHorizontalSwipe
import androidx.compose.ui.text.intl.Locale
import eu.tintera.plume.resources.conversation_bot_start
import eu.tintera.plume.resources.conversation_schedule_time
import eu.tintera.plume.resources.conversation_schedule_past
import kotlinx.datetime.TimeZone
import androidx.compose.ui.composed
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.withTimeoutOrNull
import eu.tintera.plume.core.ui.rememberAfterKeyboardHidden
import eu.tintera.plume.core.ui.AutoDeleteBadge
import eu.tintera.plume.core.ui.longPress
import eu.tintera.plume.core.telegram.BotMenuApp
import eu.tintera.plume.core.telegram.VideoChatInfo
import eu.tintera.plume.core.telegram.PendingJoinRequests
import eu.tintera.plume.core.telegram.AppScreen
import eu.tintera.plume.core.telegram.InternalLink
import eu.tintera.plume.core.telegram.SendOptions
import eu.tintera.plume.core.telegram.MessageSchedule
import eu.tintera.plume.core.telegram.GifItem
import eu.tintera.plume.core.telegram.StickerItem
import eu.tintera.plume.resources.conversation_send_silent
import eu.tintera.plume.resources.conversation_send_schedule
import eu.tintera.plume.resources.conversation_send_when_online
import eu.tintera.plume.resources.schedule_send_24dp
import eu.tintera.plume.resources.online_prediction_24dp
import eu.tintera.plume.resources.schedule_24dp
import eu.tintera.plume.resources.conversation_scheduled_open
import eu.tintera.plume.core.ui.VerificationBadge
import eu.tintera.plume.core.ui.EmojiStatusBadge
import eu.tintera.plume.core.telegram.PollComposerMode

import eu.tintera.plume.core.telegram.ReplyKeyboardButtonKind
import eu.tintera.plume.core.telegram.ChatReplyMarkup
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.hazeBlur
import eu.tintera.plume.core.telegram.ChatKind
import eu.tintera.plume.core.ui.avatarShape
import eu.tintera.plume.core.ui.sharedElementClipArea
import eu.tintera.plume.core.ui.rememberSharedElementClip
import eu.tintera.plume.core.ui.LocalSharedElementClip
import eu.tintera.plume.core.ui.LocalSharedElementOcclusion
import eu.tintera.plume.core.ui.occludesSharedElements
import eu.tintera.plume.core.ui.sharedElementOcclusionBand
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import eu.tintera.plume.core.telegram.ContentAccess
import eu.tintera.plume.core.telegram.WriteAccess
import eu.tintera.plume.core.ui.confirm
import eu.tintera.plume.core.ui.reject
import eu.tintera.plume.core.ui.tick
import eu.tintera.plume.core.ui.toggle
import eu.tintera.plume.resources.conversation_not_member
import eu.tintera.plume.resources.conversation_topic_closed
import eu.tintera.plume.resources.conversation_write_restricted
import eu.tintera.plume.resources.notifications_24dp
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.profile_mute
import eu.tintera.plume.resources.profile_unmute
import org.jetbrains.compose.resources.StringResource
import eu.tintera.plume.core.telegram.AttachmentMode
import androidx.compose.ui.draw.clip
import eu.tintera.plume.resources.profile_open
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.ui.unit.Dp
import eu.tintera.plume.resources.conversation_search
import eu.tintera.plume.resources.comments_title
import eu.tintera.plume.resources.search_24dp
import eu.tintera.plume.resources.summarize_24dp
import eu.tintera.plume.resources.summary_action
import eu.tintera.plume.resources.summary_me
import org.koin.compose.viewmodel.koinViewModel
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.NavigationEventInfo
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Button
import eu.tintera.plume.resources.conversation_join
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import eu.tintera.plume.core.ui.chatTitle
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.text.BasicTextField
import eu.tintera.plume.core.ui.ClipboardMediaKind
import eu.tintera.plume.core.ui.ClipboardPasteSuggestion
import eu.tintera.plume.core.ui.KeyboardContent
import eu.tintera.plume.core.ui.KeyboardContentReceiver
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.semantics.contentDescription
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.done_all_24dp
import eu.tintera.plume.resources.alternate_email_24dp
import eu.tintera.plume.resources.favorite_filled_24dp
import eu.tintera.plume.resources.ballot_24dp
import eu.tintera.plume.resources.conversation_unread_mentions
import eu.tintera.plume.resources.conversation_unread_reactions
import eu.tintera.plume.resources.conversation_read_all_mentions
import eu.tintera.plume.resources.conversation_read_all_reactions
import eu.tintera.plume.resources.conversation_read_all_poll_votes
import eu.tintera.plume.resources.conversation_unread_poll_votes
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.VisualTransformation
import eu.tintera.plume.features.conversation.items.ARRIVAL_TOLERANCE_SECONDS
import eu.tintera.plume.features.conversation.items.effectAutoplays
import eu.tintera.plume.features.conversation.items.WavyShape
import eu.tintera.plume.features.conversation.items.SoundingVideoNote
import eu.tintera.plume.features.conversation.items.rememberSoundingVideoNote
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import eu.tintera.plume.core.ui.avatarPolygon
import eu.tintera.plume.core.ui.rememberShownConnection
import eu.tintera.plume.core.ui.rememberConnectionIndicator
import eu.tintera.plume.core.ui.OnlineDot
import eu.tintera.plume.resources.presence_online
import eu.tintera.plume.core.ui.RING_STROKE
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import eu.tintera.plume.core.files.rememberSaveAs
import eu.tintera.plume.core.ui.animateMorph
import eu.tintera.plume.core.ui.morphSpring
import eu.tintera.plume.core.ui.ShapeMorphs
import eu.tintera.plume.core.ui.MorphShape
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.material3.IconButtonShapes
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import eu.tintera.plume.core.telegram.commandSuggestions
import eu.tintera.plume.core.location.liveLocationSharingSupported
import eu.tintera.plume.core.location.rememberDeviceLocation
import eu.tintera.plume.core.telegram.inlineQueryOf
import eu.tintera.plume.core.telegram.mentionInsertion
import eu.tintera.plume.core.telegram.mentionQueryAt
import eu.tintera.plume.resources.smart_toy_24dp
import eu.tintera.plume.resources.conversation_bot_commands
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import org.koin.compose.koinInject
import eu.tintera.plume.core.telegram.SelfDestructTimers
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.audio.VoiceKey
import eu.tintera.plume.core.audio.VoiceSpeed
import eu.tintera.plume.core.audio.VoiceRecording
import eu.tintera.plume.core.audio.rememberRecordPermission
import eu.tintera.plume.core.settings.ExpressionMode
import eu.tintera.plume.core.settings.GestureHint
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.UnreadMarkKind
import eu.tintera.plume.core.telegram.LinkPreview
import androidx.compose.ui.layout.ContentScale
import eu.tintera.plume.core.ui.RemoteImage
import eu.tintera.plume.resources.link_preview_remove
import eu.tintera.plume.resources.link_preview_options
import eu.tintera.plume.resources.tune_24dp
import eu.tintera.plume.resources.link_24dp
import eu.tintera.plume.resources.location_dock_you
import eu.tintera.plume.resources.conversation_voice_message
import eu.tintera.plume.resources.content_video_note
import eu.tintera.plume.core.telegram.LinkPreviewOptions
import eu.tintera.plume.core.ui.describe
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.core.telegram.SponsoredMessage
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.ForumTopicIcon
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.core.ui.strongestAccentOn
import eu.tintera.plume.core.ui.tintedSurface
import eu.tintera.plume.core.ui.asDayLabel
import eu.tintera.plume.core.ui.startOfLocalDay
import eu.tintera.plume.core.ui.chatAvatarKey
import eu.tintera.plume.core.ui.chatTitleKey
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.core.ui.embeddedMediaPickerAvailable
import eu.tintera.plume.core.ui.rememberEmbeddedMediaSelection
import eu.tintera.plume.core.ui.rememberAttachmentPicker
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.sharedFlight
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.core.ui.sharedNavText
import eu.tintera.plume.features.conversation.items.BUBBLE_CORNER
import eu.tintera.plume.features.conversation.items.FloatingDayBadge
import eu.tintera.plume.features.conversation.items.ConversationItemScope
import eu.tintera.plume.features.conversation.items.ConversationItemContent
import eu.tintera.plume.features.conversation.items.DayDividerItem
import eu.tintera.plume.features.conversation.items.MESSAGE_SPACING
import eu.tintera.plume.features.conversation.items.MenuAnchor
import eu.tintera.plume.features.conversation.items.MessageAction
import eu.tintera.plume.features.conversation.items.mapUrl
import kotlin.math.abs
import eu.tintera.plume.features.conversation.items.MessageMenuAction
import eu.tintera.plume.features.conversation.items.MessageItem
import eu.tintera.plume.features.conversation.items.QuotedMessage
import eu.tintera.plume.features.conversation.items.SPONSORED_KEY_PREFIX
import eu.tintera.plume.features.conversation.items.TIME_COLUMN_WIDTH
import eu.tintera.plume.features.conversation.items.UnreadDividerItem
import eu.tintera.plume.features.conversation.items.ConversationItem
import eu.tintera.plume.features.conversation.items.conversationItems
import eu.tintera.plume.features.conversation.items.describe
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_chat_sensitive_show
import eu.tintera.plume.resources.conversation_chat_sensitive_text
import eu.tintera.plume.resources.conversation_chat_sensitive_title
import eu.tintera.plume.resources.visibility_off_24dp
import eu.tintera.plume.resources.conversation_chat_restricted
import eu.tintera.plume.resources.block_24dp
import eu.tintera.plume.core.ui.IntroEmptyState
import eu.tintera.plume.resources.auto_awesome_24dp
import eu.tintera.plume.resources.conversation_send_effect
import eu.tintera.plume.resources.share_preparing
import eu.tintera.plume.resources.arrow_downward_24dp
import eu.tintera.plume.resources.attach_file_24dp
import eu.tintera.plume.resources.check_24dp
import eu.tintera.plume.resources.close_24dp
import eu.tintera.plume.resources.conversation_attach
import eu.tintera.plume.resources.conversation_location_title
import eu.tintera.plume.resources.conversation_attach_file
import eu.tintera.plume.resources.conversation_attach_media
import eu.tintera.plume.resources.conversation_cancel_edit
import eu.tintera.plume.resources.conversation_cancel_reply
import eu.tintera.plume.resources.clipboard_has_gif
import eu.tintera.plume.resources.clipboard_has_image
import eu.tintera.plume.resources.conversation_editing
import eu.tintera.plume.resources.conversation_editing_caption
import eu.tintera.plume.resources.conversation_open_gifs
import eu.tintera.plume.resources.gif_box_24dp
import eu.tintera.plume.resources.conversation_empty
import eu.tintera.plume.resources.conversation_fallback_title
import eu.tintera.plume.resources.conversation_input_caption
import eu.tintera.plume.resources.conversation_input_message
import eu.tintera.plume.resources.conversation_microphone_permission
import eu.tintera.plume.resources.conversation_new_messages
import eu.tintera.plume.resources.conversation_open_emoji
import eu.tintera.plume.resources.conversation_open_stickers
import eu.tintera.plume.resources.conversation_record_voice
import eu.tintera.plume.resources.conversation_save_edit
import eu.tintera.plume.resources.conversation_scroll_to_end
import eu.tintera.plume.resources.conversation_send
import eu.tintera.plume.resources.conversation_send_voice
import eu.tintera.plume.resources.conversation_undo
import eu.tintera.plume.resources.conversation_unpinned
import eu.tintera.plume.resources.spam_bar_contact_added
import eu.tintera.plume.resources.description_24dp
import eu.tintera.plume.resources.edit_24dp
import eu.tintera.plume.resources.mic_24dp
import eu.tintera.plume.resources.mood_24dp
import eu.tintera.plume.resources.perm_media_24dp
import eu.tintera.plume.resources.send_24dp
import eu.tintera.plume.resources.sticky_note_2_24dp
import kotlin.time.Clock
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import eu.tintera.plume.core.telegram.SpamBar
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.ChatBar
import eu.tintera.plume.core.telegram.InviteMembersBar
import eu.tintera.plume.core.telegram.JoinRequestBar
import eu.tintera.plume.core.telegram.SharePhoneBar
import eu.tintera.plume.features.report.ReportHost
import eu.tintera.plume.features.proxy.ProxyLinkHost
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    onBack: () -> Unit,
    onOpenMedia: (messageId: Long) -> Unit,
    /** Video od daného místa — čas v médiu („1:23" v textu). */
    onOpenMediaAt: (messageId: Long, seconds: Int) -> Unit = { messageId, _ -> onOpenMedia(messageId) },
    onForward: (messageIds: List<Long>) -> Unit,
    /** Příloha ankety v prohlížeči; `index` viz [MessageAction.OpenPollMedia]. */
    onOpenPollMedia: (messageId: Long, index: Int) -> Unit = { _, _ -> },
    /** Médium článku v prohlížeči; `index` viz [MessageAction.OpenArticleMedia]. */
    onOpenArticleMedia: (messageId: Long, index: Int) -> Unit = { _, _ -> },
    /** Příběh v prohlížeči — citace odpovědi na příběh, odkaz `t.me/jméno/s/N`. */
    onOpenStory: (StoryTarget) -> Unit = {},
    /**
     * Otevřít chat nad konverzací (zmínka, odkaz, komentáře); kam přesně — zpráva, vlákno,
     * téma, seznam témat —, říká [ChatTarget].
     */
    onOpenChat: (ChatTarget) -> Unit,
    /** Hashtag v textu: hledání zpráv s tagem v seznamu chatů. */
    onSearch: (query: String) -> Unit,
    /** Ťuknutí na hlavičku: profil chatu. */
    onOpenProfile: () -> Unit,
    /** Profil jiného chatu nad konverzací — odesílatel ze skupiny, na jehož tvář se ťuklo. */
    onOpenUserProfile: (chatId: Long) -> Unit,
    /**
     * Chat se z pruhu vrátil z archivu mezi chaty. Jen konverzace otevřená Z ARCHIVU s tím
     * něco dělá (přestěhuje se do hlavního seznamu); jinde se nic měnit nemusí.
     */
    onUnarchived: () -> Unit = {},
    /** Sponka → Anketa (a tlačítko bota „vytvořit anketu"): formulář nové ankety nad konverzací. */
    onCreatePoll: (PollComposerRequest) -> Unit = {},
    /** Mini aplikace bota — obrazovka přes celou plochu nad konverzací. */
    onOpenWebApp: (WebAppRequest) -> Unit = {},
    /** Tlačítko bota „vyber lidi / chat": výběr nad konverzací. */
    onOpenBotShare: (BotShareRequest) -> Unit = {},
    /** Odkaz `t.me/bot?startgroup=…`: výběr skupiny, do které bota přidat. */
    onAddBotToGroup: (InternalLink.BotStartInGroup) -> Unit = {},
    /** Odkaz na obrazovku aplikace (`tg://settings/…`, `tg://newgroup`) — navigaci řeší shell. */
    onOpenScreen: (AppScreen) -> Unit = {},
    /** Tlačítko bota s inline dotazem do jiného chatu: výběr chatu. */
    onSwitchInline: (SwitchInlinePick) -> Unit = {},
    /** Pruh „Pozvat členy" nad čerstvou skupinou: výběr lidí. */
    onAddMembers: () -> Unit = {},
    /** Pruh s čekajícími žádostmi o vstup: otevře jejich seznam. */
    onOpenJoinRequests: () -> Unit = {},
    /**
     * Uložené zprávy jako seznam témat podle zdrojového chatu. `null` = nenabízet (každá jiná
     * konverzace i téma samo).
     */
    onViewAsChats: (() -> Unit)? = null,
    /**
     * Rychlý přepínač (podržení šipky zpět): vybraný chat místo téhle konverzace. `null` =
     * nenabízet (bublina konverzace).
     */
    onSwitchChat: ((ChatSummary) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val stateHolder = viewModel.state.collectAsStateWithLifecycle()
    val state by stateHolder

    /*
     * Chat, jehož obsah se neukazuje: omezený (odkrýt nejde), nebo citlivý a uživatel ještě
     * neřekl „přesto zobrazit". Podle toho se řídí obsah, pole, hlavička i pruh připnutých —
     * jedna podmínka, ať některé z nich obsah neprozradí (tak to udělal pruh připnutých).
     * Potvrzení platí pro tohle otevření chatu (i po otočení), ne napořád: napořád je volba
     * v Nastavení → Soukromí.
     */
    var sensitiveAccepted by rememberSaveable(viewModel.chatId) { mutableStateOf(false) }
    val sensitiveWarning = state.info?.sensitive == true && !sensitiveAccepted
    val chatHidden = state.info?.restriction != null || sensitiveWarning

    // Shrnutí modelem v zařízení (experimentální, viz `core/ai`). Vlastní ViewModel s klíčem
    // podle chatu — stejná pojistka jako u konverzace samotné.
    val summary: ChatSummaryViewModel = koinViewModel(key = "summary-${viewModel.chatId}")
    val summaryAvailable by summary.available.collectAsStateWithLifecycle()
    val summaryState by summary.state.collectAsStateWithLifecycle()
    val summarySourceState by summary.source.collectAsStateWithLifecycle()
    val summaryMe = stringResource(Res.string.summary_me)
    val summaryTitle = state.info?.let { chatTitle(it.id, it.title) }
        ?: stringResource(Res.string.conversation_fallback_title)
    // Návrhy odpovědi. Vlastní ViewModel jako shrnutí a klíčovaný chatem ze stejného důvodu.
    val replies: ReplySuggestionsViewModel = koinViewModel(key = "replies-${viewModel.chatId}")
    val repliesAvailable by replies.available.collectAsStateWithLifecycle()
    val replySuggestions by replies.suggestions.collectAsStateWithLifecycle()
    // Emoji k rozepsanému slovu. Jazyk zařízení a angličtina, jako hledání ve výběru emoji.
    val emojiWords: EmojiSuggestionsViewModel = koinViewModel(key = "emoji-words-${viewModel.chatId}")
    val emojiSuggestions by emojiWords.suggestions.collectAsStateWithLifecycle()
    val emojiLanguages = remember { listOf(Locale.current.language, "en").distinct() }
    val startSummary = {
        // Zdroj se čte až v korutině, PO dotažení historie — a ze stateHolderu, ne z `state`
        // zachyceného při skládání: ten je z doby před dotažením a chyběly by v něm stránky.
        summary.summarize(summaryTitle, summaryMe) {
            viewModel.loadForSummary(SUMMARY_RECENT_MESSAGES)
            val loaded = stateHolder.value
            summarySource(loaded.messages, loaded.unreadBoundaryId)
        }
    }
    // Hledané slovo pro bubliny. Odvozené, aby text bublin překreslila jen změna dotazu, ne každá
    // změna stavu konverzace. Dotaz, ke kterému patří výsledky, ne rozepsaný.
    val searchHighlight = remember {
        derivedStateOf { stateHolder.value.search?.resultsQuery?.takeIf { it.isNotBlank() } }
    }
    // Čte se až v bublině hlasovky, takže změna pozice nepřekresluje celou obrazovku.
    val voicePlayback = viewModel.voicePlayback.collectAsStateWithLifecycle()
    val voiceSpeed = viewModel.voiceSpeed.collectAsStateWithLifecycle()
    val transcribeContext = viewModel.transcribeContext.collectAsStateWithLifecycle()
    val connection by viewModel.connection.collectAsStateWithLifecycle()
    // Nahrávání se mění po 50 ms; obrazovka čte jen jestli běží, čas a úrovně až pruh nahrávání.
    val voiceRecordingState = viewModel.voiceRecording.collectAsStateWithLifecycle()
    val videoRecordingState = viewModel.videoRecording.collectAsStateWithLifecycle()
    val isRecording by remember {
        derivedStateOf { voiceRecordingState.value != null || videoRecordingState.value != null }
    }
    val isVideoRecording by remember { derivedStateOf { videoRecordingState.value != null } }
    val voicePreview by viewModel.voicePreview.collectAsStateWithLifecycle()
    val videoNotePreview by viewModel.videoNotePreview.collectAsStateWithLifecycle()
    // Nový náhled (i hotový po mezistavu) začíná potichu, jako kolečko v bublině.
    val videoNotePlayback = remember(videoNotePreview) { VideoNotePreviewPlayback() }
    val recordGesture = remember { RecordGestureState() }
    val recordPermission = rememberRecordPermission()
    val videoNotePermission = rememberVideoNotePermission()
    val preferredRecordMode by viewModel.recordMode.collectAsStateWithLifecycle()
    // Kamera žije s obrazovkou, ViewModel si ji jen půjčuje (viz `CaptureSlot`).
    val videoNoteCamera = rememberVideoNoteCamera()
    DisposableEffect(videoNoteCamera) {
        videoNoteCamera?.let(viewModel::attachCamera)
        onDispose { videoNoteCamera?.let(viewModel::detachCamera) }
    }
    // Kolečko jen tam, kde ho chat dovolí A kde je čím ho natočit.
    val videoNotesAllowed = state.content.videoNotes && videoNoteCamera != null
    val recordMode = recordModeFor(preferredRecordMode, state.content.voiceNotes, videoNotesAllowed)

    // Výběr cílového chatu je vlastní obrazovka, takže požadavek z nabídky se předá navigaci.
    val forwardRequest = state.forwardRequest
    LaunchedEffect(forwardRequest) {
        if (forwardRequest != null) {
            onForward(forwardRequest)
            viewModel.consumeForwardRequest()
        }
    }
    /*
     * Stav seznamu patří jednomu OKNU zpráv (`window`). Když se seznam vymění celý (skok do
     * historie, návrat k přítomnosti), odejdou všechny položky naráz a `animateItem` je nechá
     * doznít na místě — mizející předěl „Dnes" pak visel přes zprávy z roku 2021 a jezdil
     * se scrollem. Nový stav seznamu s novým oknem nemá co animovat pryč.
     */
    val listState = rememberSaveable(state.window, saver = LazyListState.Saver) { LazyListState() }
    // Přechod na zprávu, ke které se nedá dojet (daleko, nebo nenačtená) — viz `JumpTransition`.
    val jumpTransition = remember { JumpTransition() }
    val snackbarHostState = remember { SnackbarHostState() }

    val chatBar by viewModel.chatBar.collectAsStateWithLifecycle()
    val videoChat by viewModel.videoChat.collectAsStateWithLifecycle()
    val joinRequests by viewModel.joinRequests.collectAsStateWithLifecycle()
    // ✕ schová pruh do té doby, než přibude další žádost — pamatuje se počet, u kterého se
    // schoval. Napořád by správce o nové žádosti nevěděl; při každém otevření chatu by zase
    // otravoval tím, co už viděl.
    var joinRequestsHiddenAt by rememberSaveable(viewModel.chatId) { mutableIntStateOf(0) }
    val spamBar = chatBar as? SpamBar
    var confirmSharePhone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(chatBar) { if (chatBar !is SharePhoneBar) confirmSharePhone = false }
    if (confirmSharePhone) {
        ContactSharePhoneDialog(
            name = state.info?.title.orEmpty(),
            onConfirm = {
                confirmSharePhone = false
                viewModel.sharePhoneNumber()
            },
            onDismiss = { confirmSharePhone = false },
        )
    }
    val chatGone by viewModel.chatGone.collectAsStateWithLifecycle()
    /** Otevřený sheet z pruhu proti spamu; `null` = zavřený. */
    var spamBarAction by rememberSaveable { mutableStateOf<SpamBarAction?>(null) }

    // Chat se z pruhu proti spamu smazal — konverzace nemá co ukazovat.
    LaunchedEffect(chatGone) { if (chatGone) onBack() }
    val unarchived by viewModel.unarchived.collectAsStateWithLifecycle()
    LaunchedEffect(unarchived) { if (unarchived) onUnarchived() }

    ReportHost(viewModel.reportFlow, snackbarHostState)
    ProxyLinkHost(viewModel.proxyLinkFlow, snackbarHostState)

    // Pruh mezitím zmizel (přidán kontakt, zavřeno jinde) — sheet nemá čeho se týkat.
    LaunchedEffect(spamBar) { if (spamBar == null) spamBarAction = null }
    val openSpamBar = spamBar
    val openSpamAction = spamBarAction
    if (openSpamBar != null && openSpamAction != null) {
        SpamBarSheet(
            action = openSpamAction,
            bar = openSpamBar,
            title = state.info?.let { chatTitle(it.id, it.title) }.orEmpty(),
            onConfirm = { cleanup ->
                spamBarAction = null
                viewModel.resolveSpamBar(cleanup)
            },
            onDismiss = { spamBarAction = null },
        )
    }
    var stickerPickerOpen by rememberSaveable { mutableStateOf(false) }
    var previewAttachmentIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var locationPickerOpen by rememberSaveable { mutableStateOf(false) }
    var livePeriodOpen by rememberSaveable { mutableStateOf(false) }

    // Snackbar o odepnutí se skládá v efektu, kde `stringResource` volat nejde — text se proto
    // přečte tady v kompozici.
    val unpinnedMessage = stringResource(Res.string.conversation_unpinned)
    val contactAddedMessage = stringResource(Res.string.spam_bar_contact_added)
    val undoLabel = stringResource(Res.string.conversation_undo)

    // Vybrané soubory jsou už zkopírované v cache aplikace; čekají nad polem na popisek a odeslání.
    val attachmentPicker = rememberAttachmentPicker(
        onPicked = viewModel::addAttachments,
        onError = viewModel::reportError,
    )

    /*
     * Galerie se kreslí rovnou nad vstupním polem, kde ji systém umí vložit dovnitř aplikace
     * (`EmbeddedMediaPicker`); jinde zůstává systémový výběr, tedy odchod na vlastní obrazovku.
     * Rozhoduje se tady, aby o tom `AttachButton` nemusel vědět — nabídka je v obou případech
     * stejná.
     */
    val embeddedMediaPicker = embeddedMediaPickerAvailable()
    var mediaPickerOpen by rememberSaveable { mutableStateOf(false) }
    // Paměť toho, co už je v galerii vybrané. Patří obrazovce: panel se zavřením zahodí, takže
    // by se při dalším otevření dala tatáž fotka vybrat podruhé.
    val mediaSelection = rememberEmbeddedMediaSelection()

    /**
     * Rozepsaný text vstupního pole. Drží se tady, ne v poli: výběr emoji do něj vkládá na pozici
     * kurzoru a maže z něj, zatímco pole samo je pod sheetem.
     *
     * `TextFieldValue`, ne `String`: u vráceného konceptu i u vloženého emoji je potřeba nastavit
     * kurzor. S holým řetězcem by zůstal na začátku textu. Ruční formátování nese [ComposerDraft]
     * vedle textu a posouvá ho s každou změnou, která jde přes `draft = …`.
     */
    val composer = rememberComposerDraft()
    var draft by composer
    // Plánování: napřed den, pak čas. Cíl je rozepsaná zpráva, nebo naplánovaná k přeplánování.
    var scheduleTarget by remember { mutableStateOf<ScheduleTarget?>(null) }
    var effectPickerOpen by remember { mutableStateOf(false) }
    var scheduleDay by remember { mutableStateOf<Long?>(null) }
    // Nápovědy gest napoprvé. Podmínky se čtou až v `snapshotFlow`, obrazovka se kvůli nim neskládá.
    val gestureHints = rememberConversationGestureHints(viewModel, stateHolder) {
        stickerPickerOpen || mediaPickerOpen || locationPickerOpen || livePeriodOpen || effectPickerOpen ||
            scheduleTarget != null || previewAttachmentIndex != null || isRecording ||
            voicePreview != null || videoNotePreview != null ||
            (stateHolder.value.info?.sensitive == true && !sensitiveAccepted)
    }

    // Komu se má odpovědět, rozhoduje čisté pravidlo nad stavem (testy `ReplySuggestionsTest`).
    val replyTarget = replySuggestionTarget(
        messages = state.messages,
        draft = draft.text,
        editing = state.editing,
        canWrite = state.canWrite,
    )
    val summaryTitleForReply = state.info?.let { chatTitle(it.id, it.title) }
    LaunchedEffect(replyTarget, repliesAvailable, summaryTitleForReply) {
        val title = summaryTitleForReply
        if (repliesAvailable && replyTarget != null && title != null) {
            replies.suggest(replyTarget, title, state.messages, summaryMe)
        } else {
            replies.clear()
        }
    }


    /** Poslední otevřená část výběru — uložená v nastavení, takže platí i po restartu aplikace. */
    val expressionMode by viewModel.expressionMode.collectAsStateWithLifecycle()
    /** Sada otevřená ťuknutím na samolepku v konverzaci. */
    var openStickerSetId by rememberSaveable { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    /*
     * Odkrývání časů tahem doleva.
     *
     * Posouvá se CELÁ plocha zpráv, ne jedna bublina: čas u jedné zprávy sám o sobě neřekne,
     * kdy přišly ty okolo, a u jediné bubliny by to navíc vypadalo jako druhá polovina swipu
     * na odpověď. Stav je proto tady, ne v položce, a čte se až při kreslení vrstvy — jinak by
     * každý snímek gesta překreslil celou konverzaci.
     */
    val timeReveal = remember { Animatable(0f) }
    val revealMax = with(LocalDensity.current) { TIME_COLUMN_WIDTH.toPx() }
    val revealSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    // Chyby se dřív zahazovaly: `state.error` se vykreslovalo jen u prázdné konverzace, takže
    // neúspěšná reakce nebo odeslání zmizely bez jediné stopy.
    LaunchedEffect(state.error) {
        val message = state.error ?: return@LaunchedEffect
        // Chyba po akci uživatele je jediné místo, kde odezva hlásí NEúspěch — snackbar se dá
        // přehlédnout, hlavně u odeslání, které mezitím vypadá jako hotové.
        haptics.reject()
        snackbarHostState.showSnackbar(message.resolveNow())
        viewModel.dismissError()
    }

    // Snackbar běží v `snackbarScope`, ne v těle efektu. Vynulování `unpinned` změní klíč efektu,
    // ten se restartuje a zrušil by i snackbar, který se právě ukazuje — takže se neukázal nikdy.
    val snackbarScope = rememberCoroutineScope()

    // Stejná past jako u `unpinned` níž: vynulování mění klíč efektu, takže snackbar běží mimo něj.
    // I text se čte AŽ TAM: `getString` je `suspend`, a dokud se četl v těle efektu, restart po
    // vynulování ho zrušil — hláška se ukázala, jen když byl text náhodou už načtený.
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        viewModel.consumeNotice()
        snackbarScope.launch {
            val text = notice.resolveNow()
            snackbarHostState.showSnackbar(text, duration = SnackbarDuration.Short)
        }
    }

    LaunchedEffect(state.unpinned) {
        val unpinned = state.unpinned ?: return@LaunchedEffect
        // Vynulovat hned, ne až po zavření snackbaru — jinak by se po otočení ukázal znovu.
        viewModel.consumeUnpinned()
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = unpinnedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoUnpin(unpinned)
        }
    }

    // Přidaný kontakt z karty ve zprávě: stejný vzor jako odepnutí — vynulovat hned, snackbar mimo efekt.
    LaunchedEffect(state.addedContact) {
        val userId = state.addedContact ?: return@LaunchedEffect
        viewModel.consumeAddedContact()
        snackbarScope.launch {
            val result = snackbarHostState.showSnackbar(
                message = contactAddedMessage,
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoAddContact(userId)
        }
    }

    // `reverseLayout` obrací i význam indexů: index 0 je nejnovější zpráva dole, vyšší indexy
    // jsou starší nahoře. Stránkování do minulosti se tedy spouští na KONCI seznamu.
    val shouldLoadOlder by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            last >= info.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { shouldLoadOlder }.collect { if (it) viewModel.loadOlder() }
    }

    /*
     * Okno v historii (po skoku na starou zprávu) se dotahuje i k PŘÍTOMNOSTI — na začátku
     * seznamu, tedy u spodní hrany. Jestli je okno v historii, ví ViewModel (`reachedEnd`);
     * obrazovka jen hlásí, že je uživatel blízko.
     */
    val shouldLoadNewer by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex < LOAD_MORE_THRESHOLD }
    }
    LaunchedEffect(listState, state.reachedEnd) {
        if (state.reachedEnd) return@LaunchedEffect
        snapshotFlow { shouldLoadNewer }.collect { if (it) viewModel.loadNewer() }
    }

    // „Jsem dole" = vidím nejnovější zprávu. Při `reverseLayout` je to index 0; kontroluje se
    // i offset, aby se za dole nepovažoval stav, kdy je poslední bublina jen z půlky vidět.
    val atBottom by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }

    // Kolik cizích zpráv přišlo, zatímco uživatel četl historii.
    var pendingNewMessages by rememberSaveable { mutableIntStateOf(0) }
    var lastSeenMessageId by rememberSaveable { mutableStateOf<Long?>(null) }

    /**
     * Změřená výška pruhů nad seznamem (proti spamu a s připnutou zprávou); nula, když žádný
     * není. Pruhy plavou nad seznamem (nejsou v `topBar`), takže jejich výšku Scaffold nezná
     * a měří se ručně.
     */
    var pinnedHeight by remember { mutableStateOf(0.dp) }

    /** Výška pruhů BEZ řádku docku živé polohy — od jejich spodku se měří, kolik ze zprávy zajelo. */
    var barsHeight by remember { mutableStateOf(0.dp) }

    /**
     * Rozbalený dock živé polohy: mapa s ovládáním nad konverzací. Závoj pod ní NENÍ — psát
     * a scrollovat jde dál, kouzlo je právě v tom, že mapa zůstane na očích při psaní.
     */
    var dockExpanded by rememberSaveable { mutableStateOf(false) }

    /** Kde byla zpráva, nad kterou se otevírá nabídka — hlásí ji zpráva při dlouhém stisku. */
    var menuAnchor by remember { mutableStateOf(MenuAnchor(bounds = { null }, maxWidth = { null })) }

    /** Klíč zprávy, kterou právě kreslí nabídka; v seznamu po ní zůstane prázdné místo. */
    val menuKey = remember { mutableStateOf<Long?>(null) }

    /** Horní hrana vstupního pole v okně; nabídka nad zprávou pod ni nesmí. */
    val contentBottom = remember { mutableStateOf<Float?>(null) }
    // Plave-li nad polem napovídání (příkazy, zmínky, emoji, inline bot), schová se FAB slot.
    var suggestionsFloating by remember { mutableStateOf(false) }

    /** Horní hrana seznamu (spodek hlavičky) v okně; nabídka nad zprávou nad ni nesmí. */
    val contentTop = remember { mutableStateOf<Float?>(null) }

    val currentOnOpenMedia by rememberUpdatedState(onOpenMedia)
    val currentOnOpenPollMedia by rememberUpdatedState(onOpenPollMedia)
    val currentOnOpenArticleMedia by rememberUpdatedState(onOpenArticleMedia)
    val currentOnSearch by rememberUpdatedState(onSearch)
    val currentOnOpenChat by rememberUpdatedState(onOpenChat)

    // Chat ze zmínky nebo odkazu: ViewModel ho najde, navigace je věc obrazovky. Událost se
    // nuluje hned, jinak by se po otočení otevřel znovu.
    val currentOnOpenMediaAt by rememberUpdatedState(onOpenMediaAt)
    LaunchedEffect(state.openMediaAt) {
        val target = state.openMediaAt ?: return@LaunchedEffect
        viewModel.consumeOpenMediaAt()
        currentOnOpenMediaAt(target.messageId, target.seconds)
    }
    LaunchedEffect(state.openChat) {
        val target = state.openChat ?: return@LaunchedEffect
        viewModel.consumeOpenChat()
        currentOnOpenChat(target)
    }
    val currentOnOpenScreen by rememberUpdatedState(onOpenScreen)
    LaunchedEffect(state.openScreen) {
        val screen = state.openScreen ?: return@LaunchedEffect
        viewModel.consumeOpenScreen()
        currentOnOpenScreen(screen)
    }
    val currentOnOpenStory by rememberUpdatedState(onOpenStory)
    LaunchedEffect(state.openStory) {
        val story = state.openStory ?: return@LaunchedEffect
        viewModel.consumeOpenStory()
        currentOnOpenStory(story)
    }
    val currentOnAddBotToGroup by rememberUpdatedState(onAddBotToGroup)
    LaunchedEffect(state.addBotToGroup) {
        val link = state.addBotToGroup ?: return@LaunchedEffect
        viewModel.consumeAddBotToGroup()
        currentOnAddBotToGroup(link)
    }
    val currentOnSwitchInline by rememberUpdatedState(onSwitchInline)
    LaunchedEffect(state.switchInline) {
        val pick = state.switchInline ?: return@LaunchedEffect
        viewModel.consumeSwitchInline()
        currentOnSwitchInline(pick)
    }
    val currentOnOpenUserProfile by rememberUpdatedState(onOpenUserProfile)
    LaunchedEffect(state.openProfile) {
        val target = state.openProfile ?: return@LaunchedEffect
        viewModel.consumeOpenProfile()
        currentOnOpenUserProfile(target)
    }

    /*
     * Zpráva, na jejíž tvář se naposledy ťuklo: jen ta nese sdílený prvek do profilu. Přežije
     * i návrat z profilu (a zabití procesu), aby tvář měla kam doletět zpátky.
     */
    val profileFlight = rememberSaveable { mutableStateOf<Long?>(null) }
    val saveAs = rememberSaveAs()
    val currentSaveAs by rememberUpdatedState(saveAs)
    // Příkaz z textu se pošle jen tam, kam se smí psát; jinak zůstane obyčejným textem.
    val canSendCommands = remember(stateHolder) { derivedStateOf { stateHolder.value.canWrite } }
    /*
     * Kolečko se zvukem hraje přehrávač obrazovky, ne položky (viz [SoundingVideoNote]):
     * odscrollované hraje dál v docku. Zpráva se drží i chvíli, kdy v seznamu není (výměna okna).
     */
    val soundingNoteId = state.soundingVideoNote
    val soundingNoteMedia = remember(soundingNoteId) { arrayOfNulls<MessageMedia.VideoNote>(1) }
    (state.messages.firstOrNull { it.id == soundingNoteId }?.media as? MessageMedia.VideoNote)
        ?.let { soundingNoteMedia[0] = it }
    val soundingVideoNote = rememberUpdatedState(
        rememberSoundingVideoNote(
            chatId = viewModel.chatId,
            messageId = soundingNoteId,
            note = soundingNoteMedia[0],
            startMs = { stateHolder.value.soundingVideoNoteFromMs },
            onEnded = { viewModel.onAction(it, MessageAction.VideoNoteSoundEnded) },
        ),
    )
    val itemScope = remember(viewModel) {
        ScreenItemScope(
            viewModel = viewModel,
            state = stateHolder,
            voicePlayback = voicePlayback,
            voiceSpeed = voiceSpeed,
            transcribeContext = transcribeContext,
            contentTop = contentTop,
            onMenuAnchor = { menuAnchor = it },
            menuKey = menuKey,
            timeReveal = { timeReveal.value },
            onOpenMedia = { currentOnOpenMedia(it) },
            onOpenPollMedia = { messageId, index -> currentOnOpenPollMedia(messageId, index) },
            onOpenArticleMedia = { messageId, index -> currentOnOpenArticleMedia(messageId, index) },
            onOpenStickerSet = { openStickerSetId = it },
            onSearch = { currentOnSearch(it) },
            // Výběr umístění je aktivita systému, takže ho má obrazovka; ViewModel jen stáhne.
            onSaveFileAs = { messageId -> currentSaveAs?.let { viewModel.saveDocumentAs(messageId, it) } },
            jumpTransition = jumpTransition,
            searchHighlight = searchHighlight,
            profileFlight = profileFlight,
            canSendCommands = canSendCommands,
            soundingVideoNote = soundingVideoNote,
        )
    }

    /*
     * Bloky seznamu (viz [ConversationItem]). Všechno, co vyžaduje znát sousedy — předěly,
     * seskupení bublin, profilovky, odznak přečtení, citace — spočítá builder jednou na změnu
     * vstupů, ne každá položka zvlášť.
     *
     * Klíče jsou jen ta pole stavu, ze kterých builder čte. Celý `state` by seznam přestavoval
     * i při každém kroku průběhu stahování.
     */
    val items = rememberConversationItems(state)

    // Klíč položky není u odeslaných zpráv jejich id (viz `ConversationState.itemKeys`), takže
    // co se v seznamu hledá podle klíče, musí mít vlastní mapu.
    val byKey = remember(state.messages, state.itemKeys) { state.messages.associateBy(state::keyOf) }

    /*
     * Datum nejvyšší viditelné zprávy, pro plovoucí štítek při scrollu.
     *
     * Při `reverseLayout` je nahoře POSLEDNÍ z viditelných položek. Bere se klíč, ne index —
     * ze stejného důvodu jako u hlášení přečtení. Položky bez číselného klíče (reklama, řádek
     * načítání) se přeskočí, protože k nim žádná zpráva není.
     *
     * **Počítá se jen to, co leží pod horním odsazením**, tedy pod pruhem připnutých zpráv.
     * Položky pod pruhem (a pod hlavičkou) jsou pro seznam „viditelné", ale čtenář je nevidí —
     * a pruh připnutých podle tohohle klíče volí zprávu. Skok z pruhu posadí připnutou těsně
     * pod něj (`ScrollAnchor.Top`); dokud se počítala i předchozí připnutá schovaná nad ní,
     * přeskočil pruh o jednu (3 → 1 místo 3 → 2). Offset roste v obráceném rozvržení odspodu,
     * viz `scrollToAnchor`.
     */
    val topVisibleKey by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val contentTop = info.viewportEndOffset - info.afterContentPadding
            info.visibleItemsInfo.lastOrNull { it.key is Long && it.offset < contentTop }?.key as? Long
        }
    }

    /*
     * Den horní viditelné zprávy pro plovoucí datum. Odvozuje se jen ZAČÁTEK DNE, ne klíč
     * zprávy: `topVisibleKey` se mění s každou zprávou, která nahoře přejede, a čtený přímo
     * tady přeskládal celou obrazovku (hlavičku, pole, pruhy) zhruba každý osmý snímek scrollu
     * (změřeno Perfettem). Začátek dne se změní jen při přechodu na jiný den.
     */
    val visibleDayStart by remember(listState, byKey) {
        derivedStateOf { topVisibleKey?.let { byKey[it]?.timestamp?.startOfLocalDay() } }
    }
    val visibleDay = visibleDayStart?.asDayLabel()

    /*
     * Dock živé polohy (`LiveLocationDockBar`): kolik ze zprávy s každou živou polohou zajelo pod
     * pruhy nebo pod vstupní pole (`dockProgress`). Měří se od spodku pruhů BEZ řádku docku —
     * s ním by si řádek sám ukusoval zprávu a z plynulého přechodu by byl skok.
     *
     * Zpráva, která v rozvržení není (odscrollovaná, mimo načtené okno), je v docku celá. Bez
     * rozvržení (první snímek) se nedokuje nic, jinak by řádek při otevření chatu problikl.
     */
    val dockDensity = LocalDensity.current
    val dockSource by rememberUpdatedState(state)
    val dockTargets by remember(listState) {
        derivedStateOf {
            val s = dockSource
            val info = listState.layoutInfo
            if (s.liveLocations.isEmpty() || info.visibleItemsInfo.isEmpty()) return@derivedStateOf emptyMap<Long, Float>()
            // 8 dp je horní odsazení seznamu nad pruhy (`contentPadding` níž).
            val top = info.viewportEndOffset - with(dockDensity) { (8.dp + barsHeight).toPx() }
            s.liveLocations.associate { message ->
                val key = s.keyOf(message)
                val item = info.visibleItemsInfo.firstOrNull { it.key == key }
                message.id to (item?.let { dockProgress(it.offset, it.size, top, 0f) } ?: 1f)
            }
        }
    }
    // Kompozice čte jen to, KTERÉ polohy jsou v docku; průběh se čte až při rozmístění řádku.
    val dockedIds by remember(listState) {
        derivedStateOf(structuralEqualityPolicy()) { dockTargets.filterValues { it > DOCK_EPSILON }.keys }
    }
    // Při zasouvání řádku (průběh míří k nule) se dál kreslí poslední polohy, ne nic.
    var lastDockedIds by remember { mutableStateOf(emptySet<Long>()) }
    LaunchedEffect(dockedIds) { if (dockedIds.isNotEmpty()) lastDockedIds = dockedIds }
    val shownDocked = (dockedIds.ifEmpty { lastDockedIds }).let { ids -> state.liveLocations.filter { it.id in ids } }
    // Zpráva s polohou zpátky na obrazovce (nebo sdílení skončilo): dock se sbalí i zmizí.
    LaunchedEffect(dockedIds.isEmpty()) { if (dockedIds.isEmpty()) dockExpanded = false }

    val dockReveal = remember { Animatable(0f) }
    val dockSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(dockReveal) {
        snapshotFlow { dockTargets.values.maxOrNull() ?: 0f }.collectLatest { target ->
            // Tah prstem vede řádek přímo; skok (okno v historii, nová zpráva, rozbalení) se dojede.
            if (abs(target - dockReveal.value) > DOCK_JUMP) dockReveal.animateTo(target, dockSpec) else dockReveal.snapTo(target)
        }
    }

    /*
     * Mini přehrávač v docku (`PlayerDockBar`): hrající hlasovka, skladba nebo kolečko se zvukem,
     * jejichž zpráva odjela z obrazovky. Je ve vlastních funkcích ([rememberPlayerDock],
     * [ConversationPlayerDock]), ne tady: s ním uvnitř měla tahle funkce v dexu 275 registrů
     * a otevření každého chatu padalo na `$composer` = null (viz CLAUDE.md, „Gotchas").
     */
    val playerDock = rememberPlayerDock(
        viewModel = viewModel,
        state = state,
        voicePlayback = voicePlayback,
        soundingVideoNote = soundingVideoNote,
        listState = listState,
        barsHeight = { barsHeight },
        spec = dockSpec,
    )

    // Štítek se drží i po zmizení data, aby při odeznívání nezůstala prázdná pilulka.
    var floatingDay by remember { mutableStateOf("") }
    LaunchedEffect(visibleDay) {
        if (visibleDay != null) floatingDay = visibleDay
    }

    /*
     * Skok na zprávu — první nepřečtenou, nebo citovanou.
     *
     * Zprávu hledáme podle id, ne podle indexu — než efekt doběhne, může seznam narůst o novou
     * zprávu a index by ukazoval jinam.
     */
    val jumpSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val leaveMoveSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val leaveFadeSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val gatherSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val restoreSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val leaveDistance = with(LocalDensity.current) { JUMP_LEAVE_DISTANCE.toPx() }
    val gatherDistance = with(LocalDensity.current) { JUMP_GATHER_DISTANCE.toPx() }
    // Seznam se během dojíždění mění (dotahují se stránky), takže se cíl hledá v AKTUÁLNÍM seznamu.
    val currentItems by rememberUpdatedState(items)
    val indexByKey = remember(items) { items.withIndex().associate { (index, item) -> item.key to index } }
    jumpTransition.indexOf = { indexByKey[it] ?: -1 }

    /*
     * Skok na nenačtenou zprávu: seznam začne odjíždět HNED, načítání okna kolem zprávy běží
     * mezitím. Když se zpráva nenajde, seznam se vrátí.
     */
    LaunchedEffect(state.pendingJump) {
        val pending = state.pendingJump
        when {
            state.scrollTarget != null -> Unit
            pending != null -> jumpTransition.leave(
                distance = if (pending.older) leaveDistance else -leaveDistance,
                moveSpec = leaveMoveSpec,
                fadeSpec = leaveFadeSpec,
            )
            else -> jumpTransition.restore(restoreSpec)
        }
    }

    LaunchedEffect(state.scrollTarget) {
        val target = state.scrollTarget ?: return@LaunchedEffect
        // Hledá se BLOK, ne zpráva: fotka z alba je v seznamu součástí jedné položky.
        var index = items.indexOfFirst { it.containsMessage(target.messageId) }
        // Nahoru se posazuje i předěl nad zprávou — u první nepřečtené je to právě to, co má
        // uživatel vidět. Předěl je v obráceném seznamu ZA zprávou.
        if (index >= 0 && target.anchor == ScrollAnchor.Top) {
            while (items.getOrNull(index + 1).let { it is UnreadDividerItem || it is DayDividerItem }) index++
        }
        /*
         * Na citaci a mezi výsledky hledání se DOJÍŽDÍ (`animateToAnchor`), ke vzdálené nebo
         * nenačtené zprávě se PŘEJEDE (`JumpTransition`). První nepřečtená při otevření chatu
         * a návrat dolů skočí hned.
         */
        val findCurrent = { currentItems.indexOfFirst { it.containsMessage(target.messageId) } }
        when {
            index < 0 -> jumpTransition.reset()
            target.anchor != ScrollAnchor.Center -> {
                jumpTransition.reset()
                listState.scrollToAnchor(index, target.anchor)
            }
            target.animate && listState.itemsAwayFrom(index) <= MAX_ANIMATED_JUMP_ITEMS -> {
                jumpTransition.reset()
                listState.animateToAnchor(index, target.anchor, jumpSpec, findCurrent)
            }
            else -> {
                // Starší zprávy jsou nahoře, obsah tedy odjíždí dolů.
                val older = target.older ?: (index > listState.firstVisibleItemIndex)
                jumpTransition.leave(
                    distance = if (older) leaveDistance else -leaveDistance,
                    moveSpec = leaveMoveSpec,
                    fadeSpec = leaveFadeSpec,
                )
                val now = findCurrent().takeIf { it >= 0 } ?: index
                listState.scrollToAnchor(now, target.anchor)
                currentItems.getOrNull(now)?.let {
                    jumpTransition.arrive(it.key, now, gatherDistance, gatherSpec)
                }
            }
        }
        viewModel.consumeScrollTarget()
    }

    /*
     * Tlačítko „dolů" i bez nových zpráv.
     *
     * Kdo odroluje do historie (nebo tam skočí po citaci), neměl jak se vrátit jinak než rolováním.
     * Ukáže se až o kus výš, ne při prvním pixelu — u spodní hrany by jen blikalo pod prstem.
     */
    val farFromBottom by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex >= SCROLL_DOWN_THRESHOLD_ITEMS }
    }
    val measuredSizes = rememberMeasuredSizes(listState)

    val newest = state.messages.firstOrNull()

    /*
     * Nová zpráva dole: seznam se ukotví u spodní hrany UŽ PŘI JEJÍM VLOŽENÍ.
     *
     * `LazyColumn` drží pozici podle klíče první viditelné položky. Nová zpráva na indexu 0 se
     * tedy vložila POD spodní hranu a teprve efekt o snímek později k ní dojížděl — bublina
     * nejdřív nebyla vidět, pak k ní seznam poskočil, a let ze vstupního pole mířil na cíl,
     * který se mu pod rukama posouval. `requestScrollToItem` platí pro nejbližší přeměření,
     * tedy pro tentýž layout, ve kterém se zpráva objeví; proto se volá v kompozici, ne v efektu.
     *
     * Hlídá se klíč, ne id: potvrzení odeslání mění id, ale novou zprávu nepřináší.
     */
    val anchor = remember { NewestItem() }
    if (newest != null) {
        val key = state.keyOf(newest)
        if (key != anchor.key) {
            // První naplnění seznamu není „nová zpráva", jen se zobrazila historie. Smazání
            // nejnovější zprávy klíč taky změní, ale id přitom klesne.
            // Dotažená stránka k přítomnosti (okno v historii) taky posune nejnovější klíč, ale
            // nic nepřišlo — `live` je, jestli předchozí stav končil u přítomnosti.
            val arrived = anchor.key != null && newest.id > anchor.id && anchor.live
            anchor.lastWasArrival = arrived
            // Vlastní zpráva dolů vždycky — jinak by odeslání vypadalo, že se nic nestalo.
            // Cizí jen když je uživatel dole; při čtení historie by ho to vytrhlo ze čtení.
            if (arrived && (newest.isOutgoing || atBottom)) listState.requestScrollToItem(0)
            anchor.key = key
            anchor.id = newest.id
        }
        anchor.live = state.reachedEnd
    }

    /*
     * „Píše…" se vkládá pod nejnovější zprávu, a seznam drží pozici podle první viditelné
     * položky — tou je zpráva, takže by se indikátor vložil POD spodní hranu a nebyl by vidět.
     * Kdo je dole, tomu se seznam ukotví i s ním; kdo čte historii, toho nevytrhne.
     */
    val typingShown = state.typing != null && state.reachedEnd
    val typingAnchor = remember { booleanArrayOf(false) }
    if (typingShown != typingAnchor[0]) {
        if (typingShown && atBottom) listState.requestScrollToItem(0)
        typingAnchor[0] = typingShown
    }

    val newestMessageId = newest?.id

    LaunchedEffect(newestMessageId) {
        val newestId = newestMessageId ?: return@LaunchedEffect
        val previous = lastSeenMessageId
        lastSeenMessageId = newestId

        // První naplnění seznamu není „nová zpráva", jen se zobrazila historie.
        if (previous == null) return@LaunchedEffect
        if (newestId <= previous) return@LaunchedEffect

        // Stránka dotažená k přítomnosti není „nové zprávy" — odznak by lhal.
        if (!anchor.lastWasArrival) return@LaunchedEffect

        // Id zpráv v chatu rostou, takže „nové" jsou ty nad posledním viděným.
        val arrived = state.messages.takeWhile { it.id > previous }

        // Scroll už obstaral `requestScrollToItem` výš; tady se jen počítá odznak.
        when {
            arrived.any { it.isOutgoing } || atBottom -> pendingNewMessages = 0

            // Čtu historii: NEscrollovat, vytrhlo by mě to ze čtení. Místo toho odznak dole.
            else -> pendingNewMessages += arrived.count { !it.isOutgoing }
        }
    }

    // Jakmile se uživatel dostane dolů, odznak nemá co hlásit.
    LaunchedEffect(atBottom) {
        if (atBottom) pendingNewMessages = 0
    }

    // Zhlédnutí reklamy se hlásí, teprve až je karta na obrazovce CELÁ. TDLib to vyžaduje
    // („když je vidět celý text"); hlásit ji při prvním pixelu by znamenalo vykazovat
    // inzerentovi zhlédnutí, která se nestala. Beru přísnější podmínku — celá karta včetně
    // tlačítka — protože rozměr samotného textu odsud nezjistím.
    LaunchedEffect(listState, state.sponsored) {
        if (state.sponsored.isEmpty()) return@LaunchedEffect

        snapshotFlow {
            val info = listState.layoutInfo
            info.visibleItemsInfo
                .filter { item ->
                    item.offset >= info.viewportStartOffset &&
                        item.offset + item.size <= info.viewportEndOffset
                }
                .mapNotNull { it.key as? String }
                .filter { it.startsWith(SPONSORED_KEY_PREFIX) }
                .toSet()
        }
            .debounce(SPONSORED_VIEW_DEBOUNCE_MS)
            .collect { keys ->
                keys.forEach { key ->
                    key.removePrefix(SPONSORED_KEY_PREFIX).toLongOrNull()
                        ?.let(viewModel::reportSponsoredSeen)
                }
            }
    }

    /*
     * Hlášení přečtení.
     *
     * Bere se **klíč položky, ne její index**. Index do `state.messages` nesedí: před zprávami
     * stojí sponzorované položky, takže v kanálu s reklamou byl výběr posunutý o jejich počet
     * a hlásily se za přečtené zprávy, které uživatel neviděl. Klíč zpráv je rovnou jejich id,
     * takže se nemá co rozejít, ani až seznam další položku přibere.
     *
     * Odpočet čeká, až se scrollování uklidní — jinak by při rychlém průletu odešla jedna žádost
     * na každý snímek. Efekt proto **nesmí záviset na `state.messages`**: s ním se restartoval
     * při každé nové zprávě a odpočet začínal znovu od nuly.
     *
     * Hlásí se ZMĚNA, a proto se vedle viditelných položek čtou i zprávy s nepřečtenou reakcí
     * nebo hlasem v anketě (`unreadMarkedIds`). Reakce na zprávu, která už je vidět, viditelné
     * položky nezmění — bez toho by zůstala nepřečtená a tlačítko „❤" by viselo nad zprávou,
     * na kterou se uživatel právě dívá, dokud by nezaroloval.
     */
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? Long } to state.unreadMarkedIds()
        }
            .debounce(READ_DEBOUNCE_MS)
            .collect { (keys, _) -> viewModel.markRead(keys) }
    }

    // Nabídka nad zprávou je vrstva NAD Scaffoldem (viz `MessageMenuLayer`), proto obal.
    // Scaffold je zároveň to, co její závoj rozostří (`hazeSource`).
    val hazeState = rememberHazeState()
    /*
     * Zdroj pro mléčné sklo je JEN seznam zpráv (viz `glassStyle`). Kdyby to byl `hazeState`
     * výš, rozostřovaly by se plochy samy sebou: pruh připnutých by nasál plovoucí datum a pás
     * pod polem sám sebe.
     */
    val listHaze = rememberHazeState()
    // Plocha, do které se v překryvu ořezává sdílený prvek letící sem z prohlížeče médií.
    // Tvary, pod které zajede, hlásí vstupní pole — proto už tady, nad Scaffoldem.
    val listClip = rememberSharedElementClip()
    Box(modifier) {
    // Zpět hledání zavře, z chatu se odchází až dalším. `NavigationBackHandler`, ne
    // `BackHandler` — ten by zabil predictive back.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.search != null,
        onBackCompleted = viewModel::closeSearch,
    )
    // Rozbalený dock živé polohy zavře zpět dřív, než se odejde z chatu.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = dockExpanded,
        onBackCompleted = { dockExpanded = false },
    )
    // Zpět ukončí výběr, teprve další odejde z chatu — jako u hledání.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.selection != null,
        onBackCompleted = viewModel::closeSelection,
    )
    // Otevřený seznam příkazů zavře zpět dřív, než se odejde z chatu.
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = state.commandsOpen,
        onBackCompleted = viewModel::closeCommands,
    )
    NavigationBackHandler(
        state = rememberNavigationEventState(NavigationEventInfo.None),
        isBackEnabled = previewAttachmentIndex != null,
        onBackCompleted = { previewAttachmentIndex = null },
    )

    /*
     * Plocha stránky při hledání USTOUPÍ o stupeň (`surfaceContainerLowest`): je to třetí signál
     * režimu vedle pilulky v hlavičce a plovoucího toolbaru, a jediný, který je vidět i koutkem
     * oka nad samotnými zprávami.
     *
     * **Ustupuje se OD bublin, ne k nim.** Posun opačným směrem (`surfaceContainerLow` a výš) je
     * sice výraznější, ale míří k `surfaceContainerHigh` příchozí bubliny — plocha by se
     * přiblížila tomu, co má nést, a zprávy by při hledání četly hůř než jindy. Takhle se rozdíl
     * naopak o kousek zvětší a nálezy vyskočí. Je to proto jemné vědomě: silnější tónování
     * v tomhle směru role nenabízejí.
     */
    val pageColor by animateColorAsState(
        targetValue = if (state.search != null) {
            MaterialTheme.colorScheme.surfaceContainerLowest
        } else {
            MaterialTheme.colorScheme.background
        },
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec<Color>(),
        label = "conversation-page",
    )

    Scaffold(
        // Barva popředí se z plochy neodvozuje: `surfaceContainerLowest` mapování nemá a fallback
        // by závisel na tom, co je zrovna v `LocalContentColor`.
        containerColor = pageColor,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxSize().hazeSource(hazeState),
        /*
         * Spodní inset si řeší vstupní pole samo (`windowInsetsPadding` se `safeDrawing`).
         *
         * Kdyby ho dal i Scaffold do `padding`, započítal by se dvakrát a pole by viselo o výšku
         * systémové lišty výš — `padding` se v obsahu jen aplikuje, inset se tím nekonzumuje.
         * Takhle navíc sahá obsah i závoj pole až pod lištu, stejně jako pod pole samotné.
         */
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = topBar@{
            state.selection?.let { selection ->
                SelectionTopBar(selection = selection, onClose = viewModel::closeSelection)
                return@topBar
            }
            state.search?.let { search ->
                InChatSearchTopBar(
                    initialQuery = search.query,
                    onQueryChange = viewModel::onSearchQuery,
                    onClose = viewModel::closeSearch,
                    sender = search.sender,
                    // Podle člena jen ve skupině: v soukromém chatu jsou odesílatelé dva a v kanálu
                    // píše jen kanál.
                    onPickSender = viewModel::openMemberPicker.takeIf { state.info?.let { it.isGroup && !it.isChannel } == true },
                    onRemoveSender = { viewModel.setSearchSender(null) },
                )
                return@topBar
            }
            TopAppBar(
                title = {
                    // Hlavička vede do profilu chatu — tak to znají uživatelé ze všech klientů.
                    // Uložené zprávy profil nemají (je to vlastní účet, nic k ukázání).
                    val profileLabel = stringResource(Res.string.profile_open)
                    /*
                     * Stav spojení nese avatar (zvlní se a točí), stejně jako fotka účtu
                     * v seznamu chatů — ne text pod názvem. Čtečce obrazovky ho řekne popis
                     * stavu hlavičky.
                     */
                    val shownConnection = rememberShownConnection(connection)
                    val connectionLabel = shownConnection?.label()?.let { stringResource(it) }
                    // „Online" už v podtitulu není, nese ho jen tečka — čtečce ho proto řekne
                    // popis stavu hlavičky, stejně jako výpadek spojení (ten má přednost: bez
                    // spojení je dostupnost stará hodnota).
                    val onlineLabel = stringResource(Res.string.presence_online)
                        .takeIf { state.presence is Presence.Online && state.info?.isSavedMessages == false }
                    val headerState = connectionLabel ?: onlineLabel
                    val kind = when {
                        state.info?.isChannel == true -> ChatKind.Channel
                        state.info?.isGroup == true -> ChatKind.Group
                        else -> ChatKind.Private
                    }
                    val indicator = rememberConnectionIndicator(
                        connection = shownConnection,
                        avatarSize = HEADER_AVATAR,
                        grownSize = HEADER_AVATAR_GROWN,
                        base = kind.avatarPolygon(),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(
                                // Vlákno komentářů vlastní profil nemá; profil skupiny by vedl pryč.
                                enabled = state.info?.isSavedMessages == false && state.threadRootId == null,
                                onClickLabel = profileLabel,
                                onClick = onOpenProfile,
                            )
                            .semantics { headerState?.let { stateDescription = it } }
                            // Místo pro zvětšený avatar s prstencem: řádek ořezává (tvar vlnky
                            // stisku), takže bez rezervy by prstenec usekl vlevo i nahoře a dole.
                            .padding(horizontal = HEADER_RING_ROOM, vertical = HEADER_RING_ROOM),
                    ) {
                        // Nad profilem bota je jeho tvář velká uprostřed obrazovky a nese i tečku
                        // a výpadek spojení (`BotIntroPage`); v hlavičce by byla podruhé.
                        val headerAvatar = !showsBotIntro(state)
                        // Téma: jeho ikona místo fotky skupiny. Nic neletí — seznam témat je
                        // jiná obrazovka a fotka skupiny v ní v řádku není.
                        state.info?.topic?.let { topic ->
                            ForumTopicIcon(
                                icon = topic.icon,
                                name = topic.name,
                                isGeneral = topic.isGeneral,
                                size = HEADER_AVATAR,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        }
                        // Téma Uložených zpráv: fotka chatu, ze kterého se ukládalo (Moje poznámky
                        // záložka). Taky nic neletí, řádek v seznamu témat je jiná obrazovka.
                        state.info?.savedTopic?.let { topic ->
                            SavedTopicAvatar(topic = topic, size = HEADER_AVATAR, modifier = Modifier.padding(end = 12.dp))
                        }
                        state.info?.takeIf { it.topic == null && it.savedTopic == null }?.let { info ->
                          AnimatedVisibility(
                              visible = headerAvatar,
                              enter = fadeIn() + expandHorizontally(),
                              exit = fadeOut() + shrinkHorizontally(),
                          ) {
                            // Mezera ke jménu je součást avataru, ne řádku: schovaný avatar
                            // by jinak nechal před jménem díru.
                            Box(Modifier.padding(end = 12.dp)) {
                                Avatar(
                                    chatId = info.id,
                                    title = info.title,
                                    avatarFileId = info.avatarFileId,
                                    size = HEADER_AVATAR,
                                    isSavedMessages = info.isSavedMessages,
                                    // Stejný tvar jako v seznamu, odkud avatar přiletěl. Při výpadku
                                    // ořezává indikátor — vlastní tvar by vlnky u skupiny schoval.
                                    shape = if (indicator.isShaping) RectangleShape else kind.avatarShape(),
                                    // Vlákno komentářů je jiná obrazovka než chat skupiny; sdílený
                                    // klíč by se přetahoval s její hlavičkou i s položkou v seznamu.
                                    modifier = Modifier
                                        .then(
                                            if (state.threadRootId == null) {
                                                Modifier.sharedNavElement(chatAvatarKey(info.id))
                                            } else {
                                                Modifier
                                            },
                                        )
                                        .then(with(indicator) { Modifier.avatar() }),
                                )
                                with(indicator) { Spacer(Modifier.matchParentSize().ring()) }
                                // Jako v seznamu chatů. Bez spojení je dostupnost stará hodnota
                                // a tečka by přeškrtla prstenec.
                                if (shownConnection == null && state.presence is Presence.Online && !info.isSavedMessages) {
                                    OnlineDot(
                                        rim = MaterialTheme.colorScheme.surface,
                                        size = HEADER_ONLINE_DOT,
                                        modifier = Modifier.align(Alignment.BottomEnd),
                                        // Letí s avatarem ze seznamu chatů; ve vlákně ne, stejně
                                        // jako avatar.
                                        chatId = info.id.takeIf { state.threadRootId == null },
                                    )
                                }
                            }
                          }
                        }
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = when {
                                        state.threadRootId != null -> stringResource(Res.string.comments_title)
                                        state.info?.topic != null -> state.info?.topic?.name.orEmpty()
                                        state.info?.savedTopic != null -> savedTopicTitle(state.info!!.savedTopic!!)
                                        else -> state.info?.let { chatTitle(it.id, it.title) }
                                            ?: stringResource(Res.string.conversation_fallback_title)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    // Klíč se dá jen když chat známe; než dorazí `info`, není s čím
                                    // párovat. Váha bez vyplnění: dlouhý název se zkrátí, štítek
                                    // za ním zůstane vidět.
                                    modifier = Modifier.weight(1f, fill = false).then(
                                        state.info
                                            ?.takeIf { !state.narrowed }
                                            ?.let { Modifier.sharedNavText(chatTitleKey(it.id)) }
                                            ?: Modifier,
                                    ),
                                )
                                // Podvod a falešný účet musí být vidět tam, kde se píše — ve
                                // vlákně komentářů je hlavička o skupině, ne o účtu.
                                state.info?.verification
                                    ?.takeIf { !state.narrowed }
                                    ?.let { VerificationBadge(it) }
                                state.info?.emojiStatus
                                    ?.takeIf { !state.narrowed }
                                    ?.let { EmojiStatusBadge(it) }
                                // Automatické mazání: co se tu napíše, samo zmizí. Ve vlákně
                                // komentářů by patřilo celé skupině, ne vláknu.
                                state.autoDeleteTime
                                    .takeIf { it > 0 && !state.narrowed }
                                    ?.let { AutoDeleteBadge(it) }
                            }

                            /*
                             * Podtitul: kdy byl protějšek naposledy vidět. Stav spojení tu NENÍ, ten
                             * nese avatar — a „online" taky ne, to říká tečka na avataru. Dvakrát
                             * totéž hned vedle sebe byl jen šum.
                             *
                             * Bez spojení se ale dostupnost schová: dokud spojení nestojí, je
                             * „naposledy viděn" jen stará hodnota, kterou nemá co obnovit.
                             *
                             * „Píše…" tu NENÍ: kreslí se v seznamu zpráv tam, kam zpráva naskočí
                             * (`TypingItem`), jako v Google Zprávách. Výšku hlavičky animuje
                             * `AnimatedContent`, jinak by název při objevení podtitulu poskočil.
                             */
                            // Ve vlákně komentářů a v tématu říká podtitul, ve které skupině se diskutuje.
                            val threadSubtitle = state.info
                                ?.takeIf { state.narrowed }
                                ?.let { HeaderSubtitle(chatTitle(it.id, it.title)) }
                            val subtitle = threadSubtitle ?: state.presence
                                ?.takeIf { shownConnection == null }
                                // Vlastní dostupnost v Uložených zprávách nic neříká — byl bys
                                // pořád „online", protože se na ně právě díváš.
                                ?.takeUnless { state.info?.isSavedMessages == true }
                                ?.takeUnless { it is Presence.Online }
                                ?.let { HeaderSubtitle(it.describe()) }
                            val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
                            val fastEffects = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

                            AnimatedContent(
                                targetState = subtitle,
                                transitionSpec = { fadeIn(effects) togetherWith fadeOut(fastEffects) },
                                contentAlignment = Alignment.CenterStart,
                                label = "header-subtitle",
                            ) { current ->
                                current ?: return@AnimatedContent
                                Text(
                                    text = current.text,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    // Podržení nabídne rychlý přepínač chatů; ťuknutí je dál „zpět".
                    ChatSwitcherBackButton(chatId = viewModel.chatId, onBack = onBack, onSwitch = onSwitchChat)
                },
                actions = {
                    // Prázdný chat s botem je jeho profil — není co shrnout ani hledat.
                    // Omezený chat nemá co ukázat, tedy ani co shrnout nebo hledat.
                    val botIntro = showsBotIntro(state) || chatHidden
                    if (summaryAvailable && state.threadRootId == null && !botIntro) {
                        IconButton(onClick = startSummary, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                painterResource(Res.drawable.summarize_24dp),
                                contentDescription = stringResource(Res.string.summary_action),
                            )
                        }
                    }
                    // Uložené zprávy podle zdrojového chatu: jen u celých Uložených zpráv, ne v tématu.
                    if (onViewAsChats != null && state.info?.isSavedMessages == true && !state.narrowed) {
                        IconButton(onClick = onViewAsChats, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                painterResource(Res.drawable.forum_24dp),
                                contentDescription = stringResource(Res.string.saved_view_as_chats),
                            )
                        }
                    }
                    if (!botIntro) {
                        IconButton(onClick = viewModel::openSearch, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                painterResource(Res.drawable.search_24dp),
                                contentDescription = stringResource(Res.string.conversation_search),
                            )
                        }
                    }
                },
            )
        },
        /*
         * Vstupní pole JE `bottomBar`. Scaffold ho změří (i s citací, úpravou, víc řádky
         * a klávesnicí) a podle toho sám postaví snackbar a tlačítko dolů nad něj.
         *
         * Obsah Scaffoldu je přesto přes celou plochu: spodní padding se nedává na kontejner,
         * ale do `contentPadding` seznamu. Zprávy tak pod pole zajíždějí a v klidu nad ním
         * končí. Kdyby šel padding na kontejner, seznam by před polem natvrdo skončil a přes
         * obrazovku by vedl předěl, o který se zprávy při scrollu „zasekávají".
         */
        bottomBar = {
            // Horní hrana pole je spodní mez pro nabídku nad zprávou (viz `contentBottom`).
            Box(Modifier.onGloballyPositioned { contentBottom.value = it.boundsInWindow().top }) {
            /*
             * Lišty místo pole (Přidat se, Start, Ztlumit, věta „nejde psát") mají pod sebou
             * TENTÝŽ pás skla jako pole. Seznam zpráv pod spodní lištu zajíždí (spodek je
             * `contentPadding`, ne konec plochy), takže bez pásu prosvítaly bubliny zpod tlačítka.
             */
            val barGlass = Modifier.hazeBlur(
                input = HazeInput.Sources(listHaze),
                style = glassStyle(
                    color = MaterialTheme.colorScheme.surface,
                    alpha = GLASS_TINT_ALPHA,
                    fallbackAlpha = INPUT_SCRIM_ALPHA,
                ),
            )
            // Při výběru patří spodek akcím nad ním; rozepsaný text zůstává v konceptu.
            state.selection?.let { selection ->
                SelectionToolbar(
                    selection = selection,
                    onCopy = viewModel::copySelection,
                    onCopyWithNames = viewModel::copySelectionWithNames,
                    onCopyMarkdown = viewModel::copySelectionAsMarkdown,
                    onForward = viewModel::forwardSelection,
                    onReport = viewModel::reportSelection,
                    onDelete = viewModel::deleteSelection,
                )
                return@Box
            }
            // Při hledání patří spodek šipkám mezi výsledky; rozepsaný text zůstává v konceptu.
            state.search?.let { search ->
                InChatSearchNavigator(
                    search = search,
                    // Procházení po dílcích — tik, ne potvrzení: výsledků bývají stovky.
                    onOlder = {
                        haptics.tick()
                        viewModel.searchOlder()
                    },
                    onNewer = {
                        haptics.tick()
                        viewModel.searchNewer()
                    },
                )
                return@Box
            }
            // Omezený chat: do chatu, který se nedá ukázat, se ani psát nedá.
            if (chatHidden) return@Box
            if (state.info?.canJoin == true) {
                JoinBar(joining = state.joining, onJoin = viewModel::join, modifier = barGlass)
                return@Box
            }
            // Převedená základní skupina: psát se dá jen do té nové, tak tam vede.
            state.info?.upgradedToChatId?.let { upgraded ->
                JoinBar(
                    joining = false,
                    onJoin = { onOpenChat(ChatTarget(upgraded)) },
                    label = Res.string.service_upgraded_open_new,
                    modifier = barGlass,
                )
                return@Box
            }
            // Nespuštěný bot (nebo čekající parametr z odkazu): Start místo pole.
            if (showsBotStart(state)) {
                JoinBar(
                    joining = state.startingBot,
                    onJoin = {
                        haptics.confirm()
                        viewModel.startBot()
                    },
                    label = Res.string.conversation_bot_start,
                    modifier = barGlass,
                )
                return@Box
            }
            // Kde se psát nesmí, pole by skončilo chybou až po odeslání. Kanál aspoň nabídne ztlumení.
            when (state.info?.writeAccess) {
                WriteAccess.ChannelReadOnly -> {
                    MuteBar(muted = state.muted, onToggle = viewModel::toggleMute, modifier = barGlass)
                    return@Box
                }
                WriteAccess.Restricted -> {
                    ReadOnlyNote(Res.string.conversation_write_restricted, barGlass)
                    return@Box
                }
                WriteAccess.NotMember -> {
                    ReadOnlyNote(Res.string.conversation_not_member, barGlass)
                    return@Box
                }
                WriteAccess.TopicClosed -> {
                    ReadOnlyNote(Res.string.conversation_topic_closed, barGlass)
                    return@Box
                }
                // Téma Uložených zpráv uložené odjinud: odeslat do něj TDLib nedovolí. Zdrojový
                // chat aspoň jde otevřít — tam se odpovídá; bez známého chatu jen proč.
                WriteAccess.SavedTopic -> {
                    val source = (state.info?.savedTopic?.kind as? SavedTopicKind.FromChat)?.chatId
                    if (source != null) {
                        JoinBar(
                            joining = false,
                            onJoin = { onOpenChat(ChatTarget(source)) },
                            label = Res.string.saved_topic_open_chat,
                            modifier = barGlass,
                        )
                    } else {
                        ReadOnlyNote(Res.string.saved_topic_read_only, barGlass)
                    }
                    return@Box
                }
                WriteAccess.Allowed, null -> Unit
            }
            Column {
            ReplySuggestionChips(
                suggestions = replySuggestions,
                // Návrh se jen VLOŽÍ do pole; odeslání zůstává na uživateli. `onDraftChanged`
                // musí jít taky — uloží koncept a ohlásí „píše…", jako by to napsal sám.
                onPick = { suggestion ->
                    draft = TextFieldValue(suggestion, selection = TextRange(suggestion.length))
                    viewModel.onDraftChanged(composer.composed)
                    replies.consume()
                },
            )
            // Vlastní klávesnice bota stojí NAD polem (viz `ReplyKeyboardPanel`); při úpravě
            // zprávy ne — tlačítko by poslalo novou zprávu uprostřed opravy staré.
            (state.replyMarkup as? ChatReplyMarkup.Keyboard)?.takeIf { state.editing == null }?.let { keyboard ->
                // Použitá jednorázová klávesnice se otevírá sbalená i po návratu do chatu — TDLib
                // si použití nepamatuje a klávesnice v chatu zůstává (viz `keyboardUsed`).
                val usedKeyboard by viewModel.usedKeyboardMessageId.collectAsStateWithLifecycle()
                var collapsed by rememberSaveable(keyboard.messageId) {
                    mutableStateOf(keyboard.oneTime && usedKeyboard == keyboard.messageId)
                }
                ReplyKeyboardPanel(
                    keyboard = keyboard,
                    collapsed = collapsed,
                    onToggle = { collapsed = !collapsed },
                    onPress = { row, column ->
                        haptics.confirm()
                        // Jednorázová klávesnice se po použití SCHOVÁ, nezmizí (Bot API
                        // `one_time_keyboard`) — šipkou jde vrátit. Vysvětlení u tlačítka, které
                        // Plume neumí, použití není.
                        val kind = keyboard.rows.getOrNull(row)?.getOrNull(column)?.kind
                        if (keyboard.oneTime && kind !is ReplyKeyboardButtonKind.Unsupported) collapsed = true
                        viewModel.pressReplyKeyboardButton(row, column)
                    },
                )
            }
            // Příkazy botů: napovídání podle rozepsaného „/…", nebo celý seznam z tlačítka u pole.
            val typedCommands = commandSuggestions(draft.text, state.botCommands)
            // Inline bot (`@gif kočka`): výsledky nad polem. S přílohami je text popisek a při úpravě
            // text staré zprávy — ani jedno není dotaz na bota.
            val inlineDraft = remember(draft.text, state.editing, state.attachments.isEmpty()) {
                inlineQueryOf(draft.text).takeIf { state.editing == null && state.attachments.isEmpty() }
            }
            // Zmínka (`@ja` u kurzoru): členové skupiny nad polem. Kurzor, ne celý text — zmínka se
            // píše kdekoli ve větě. S rozepsaným dotazem na inline bota se nenapovídá, to je jiné „@".
            val mentionQuery = remember(draft, inlineDraft) {
                draft.takeIf { inlineDraft == null && it.selection.collapsed }
                    ?.let { mentionQueryAt(it.text, it.selection.start) }
            }
            LaunchedEffect(mentionQuery?.query) { viewModel.mentionQuery(mentionQuery?.query) }
            // Emoji ke slovu u kurzoru. Ne vedle jiného napovídání — dotaz na inline bota a zmínka
            // mají nad polem přednost a emoji plovoucí přes jejich panel by ho zakryla.
            val emojiWord = remember(draft, inlineDraft, mentionQuery, typedCommands) {
                draft.takeIf {
                    inlineDraft == null && mentionQuery == null && typedCommands == null && it.selection.collapsed
                }?.let { emojiWordAt(it.text, it.selection.start) }
            }
            LaunchedEffect(emojiWord?.word) { emojiWords.lookup(emojiWord?.word, emojiLanguages) }
            val shownEmojis = emojiSuggestions?.takeIf { it.word == emojiWord?.word }?.emojis.orEmpty()
            LaunchedEffect(inlineDraft) { viewModel.inlineQuery(inlineDraft) }
            // Bot, který chce polohu, ji dostane přibližnou — dotaz na oprávnění přijde až u něj.
            val inlineLocation = rememberDeviceLocation()
            val locationBot = state.inline?.bot?.takeIf { it.needLocation }?.userId
            LaunchedEffect(locationBot) {
                if (locationBot != null) inlineLocation.request(viewModel::setInlineLocation)
            }
            /*
             * Napovídání při psaní PLAVE nad polem a místo v rozvržení nezabírá (viz
             * `FloatingSuggestion`): mění se s každým písmenem a konverzace pod ním nesmí
             * poskakovat. Stojí těsně nad polem, u textu, ke kterému patří. Návrhy odpovědi
             * a klávesnice bota výš zůstávají v rozvržení — viz `ReplySuggestionChips`.
             */
            val suggestionGlass = Modifier.hazeBlur(
                input = HazeInput.Sources(listHaze),
                // Inverzní plocha, jako snackbar (viz `FloatingSuggestion`).
                style = glassStyle(
                    color = MaterialTheme.colorScheme.inverseSurface,
                    alpha = FLOATING_SUGGESTION_TINT_ALPHA,
                    fallbackAlpha = GLASS_FALLBACK_ALPHA,
                ),
            )
            val commandsShown = state.editing == null && (typedCommands != null || state.commandsOpen)
            // Plovoucí napovídání leží tam, kde jinak stojí tlačítko dolů a zmínky — ty se
            // mezitím schovají, jinak by visely přes seznam příkazů nebo přes emoji.
            val floatingShown = (commandsShown && state.botCommands.isNotEmpty()) ||
                (mentionQuery != null && state.mentionSuggestions?.candidates.orEmpty().isNotEmpty()) ||
                (emojiWord != null && shownEmojis.isNotEmpty()) ||
                inlineDraft != null
            SideEffect { suggestionsFloating = floatingShown }
            Column(Modifier.floatAbove()) {
                BotCommandsPanel(
                    commands = typedCommands ?: state.botCommands.takeIf { state.commandsOpen }.orEmpty(),
                    visible = commandsShown,
                    onPick = { command ->
                        haptics.confirm()
                        // Rozepsané „/st" byl ten příkaz — pole se vyprázdní a koncept s ním.
                        if (typedCommands != null) {
                            draft = TextFieldValue()
                            viewModel.onDraftChanged(composer.composed)
                        }
                        viewModel.closeCommands()
                        viewModel.sendCommand(command.text)
                    },
                    glass = suggestionGlass,
                )
                MentionSuggestionsPanel(
                    candidates = state.mentionSuggestions?.candidates.orEmpty(),
                    visible = mentionQuery != null,
                    onPick = { candidate ->
                        val query = mentionQuery ?: return@MentionSuggestionsPanel
                        val (text, cursor) = mentionInsertion(draft.text, draft.selection.start, query, candidate)
                        draft = TextFieldValue(text, selection = TextRange(cursor))
                        viewModel.mentionPicked(candidate)
                        viewModel.onDraftChanged(composer.composed)
                    },
                    glass = suggestionGlass,
                )
                EmojiSuggestionsPanel(
                    emojis = shownEmojis,
                    visible = emojiWord != null,
                    onPick = { emoji ->
                        val word = emojiWord ?: return@EmojiSuggestionsPanel
                        haptics.confirm()
                        val (text, cursor) = emojiReplacement(draft.text, word, emoji)
                        draft = TextFieldValue(text, selection = TextRange(cursor))
                        emojiWords.picked(emoji)
                        viewModel.onDraftChanged(composer.composed)
                    },
                    glass = suggestionGlass,
                )
                InlineResultsPanel(
                    inline = state.inline,
                    visible = inlineDraft != null,
                    onPick = { result ->
                        haptics.confirm()
                        viewModel.sendInlineResult(result)
                        // Dotaz v poli byl ten výsledek — pole se vyprázdní a koncept s ním.
                        draft = TextFieldValue()
                        viewModel.onDraftChanged(composer.composed)
                    },
                    onButton = viewModel::pressInlineButton,
                    onLoadMore = viewModel::loadMoreInline,
                    glass = suggestionGlass,
                )
            }
            // Fotka letící z prohlížeče zajede pod kartu pole a tlačítko, ne jen za čáru nad nimi.
            CompositionLocalProvider(LocalSharedElementOcclusion provides listClip) {
            MessageInput(
                gestureHints = gestureHints,
                glass = Modifier.sharedElementOcclusionBand().hazeBlur(
                    input = HazeInput.Sources(listHaze),
                    style = glassStyle(
                        color = MaterialTheme.colorScheme.surface,
                        alpha = GLASS_TINT_ALPHA,
                        // Bez rozostření zůstane dřívější krycí pás; jinak by pod ním prosvítaly
                        // zprávy a psaní by se v nich ztratilo.
                        fallbackAlpha = INPUT_SCRIM_ALPHA,
                    ),
                ),
                replyingTo = state.replyingTo,
                replyQuote = state.replyQuote?.text,
                partnerName = state.info?.title,
                flight = state.flight,
                onCancelReply = viewModel::clearReply,
                // Odeslání je potvrzení akce; dosud ho doprovázel jen let bubliny. Hlásí se
                // v okamžiku ťuknutí, ne po odpovědi serveru — odezva patří ke gestu.
                onSend = { text, options ->
                    haptics.confirm()
                    viewModel.send(text, options)
                },
                sendMenu = sendMenuOffer(state),
                onSchedule = { scheduleTarget = ScheduleTarget.Draft },
                onPickEffect = {
                    viewModel.loadEffects()
                    effectPickerOpen = true
                },
                onOpenScheduled = viewModel::openScheduled.takeIf { state.hasScheduledMessages },
                onDraftChanged = viewModel::onDraftChanged,
                editing = state.editing,
                onCancelEdit = viewModel::cancelEdit,
                placeholder = state.inputPlaceholder,
                linkPreview = composerLinkPreview(
                    preview = state.linkPreview,
                    options = state.linkPreviewOptions,
                    editingCaption = state.editing?.composerEdit() == ComposerEdit.Caption,
                    hasAttachments = state.attachments.isNotEmpty(),
                ),
                linkPreviewLinks = state.linkPreviewLinks,
                linkPreviewOptions = state.linkPreviewOptions,
                onLinkPreviewOptions = viewModel::setLinkPreviewOptions,
                inputFill = state.inputFill,
                onInputFilled = viewModel::consumeInputFill,
                onOpenStickers = { stickerPickerOpen = true },
                onPickMedia = { if (embeddedMediaPicker) mediaPickerOpen = true else attachmentPicker.pickMedia() },
                isRecording = isRecording,
                recording = { voiceRecordingState.value ?: videoRecordingState.value },
                recordMode = recordMode,
                canSwitchRecordMode = canSwitchRecordMode(state.content.voiceNotes, videoNotesAllowed),
                onSwitchRecordMode = {
                    viewModel.setRecordMode(if (recordMode == RecordMode.VideoNote) RecordMode.Voice else RecordMode.VideoNote)
                },
                // Zastavené kolečko je pořád kolečko: odeslání se tak jmenuje i v náhledu.
                recordingKind = if (isVideoRecording || videoNotePreview != null) RecordMode.VideoNote else RecordMode.Voice,
                recordGesture = recordGesture,
                onStartRecording = { held ->
                    val video = recordMode == RecordMode.VideoNote
                    // Odpověď bez dialogu přijde hned, uvnitř `request`; z dialogu až potom.
                    var immediate = true
                    (if (video) videoNotePermission else recordPermission).request { granted ->
                        when {
                            !granted -> {
                                haptics.reject()
                                viewModel.reportError(
                                    UiText(if (video) Res.string.conversation_camera_permission else Res.string.conversation_microphone_permission),
                                )
                            }
                            // Oprávnění přišlo až z dialogu: prst mezitím tlačítko pustil, a nahrávat
                            // bez něj by bylo nahrávání, o kterém uživatel neví. Podrží znovu.
                            held && !immediate -> Unit
                            else -> {
                                // Nahrávání běží, dokud ho uživatel neukončí — přepnutý stav, ne
                                // hotová akce. Odepřené oprávnění je naopak odmítnutí.
                                haptics.toggle(on = true)
                                if (video) viewModel.startVideoRecording() else viewModel.startRecording()
                            }
                        }
                    }
                    immediate = false
                },
                onCancelRecording = {
                    haptics.reject()
                    viewModel.cancelRecording()
                },
                voicePreview = voicePreview,
                voicePreviewPlayback = { voicePlayback.value?.takeIf { it.key.isVoicePreview } },
                onStopRecording = {
                    // Mikrofon se zavírá — přepnutý stav, jako při spuštění.
                    haptics.toggle(on = false)
                    viewModel.stopRecordingForPreview()
                },
                onToggleVoicePreview = viewModel::toggleVoicePreview,
                onSeekVoicePreview = viewModel::seekVoicePreview,
                onContinueVoicePreview = {
                    haptics.toggle(on = true)
                    viewModel.continueVoicePreview()
                },
                onDiscardVoicePreview = {
                    haptics.reject()
                    viewModel.discardVoicePreview()
                },
                videoNotePreview = videoNotePreview,
                videoNotePlayback = videoNotePlayback,
                onDiscardVideoNotePreview = {
                    haptics.reject()
                    viewModel.discardVideoNotePreview()
                },
                onContinueVideoNotePreview = {
                    haptics.toggle(on = true)
                    viewModel.continueVideoNotePreview()
                },
                onSendRecording = { options ->
                    haptics.confirm()
                    viewModel.sendRecording(options)
                },
                // Nabídka nad hlasovkou nahrávání pozastaví, zavření bez volby ho obnoví.
                onVoiceMenu = { open -> if (open) viewModel.pauseRecording() else viewModel.resumeRecording() },
                onScheduleRecording = { scheduleTarget = ScheduleTarget.Voice },
                onPickDocument = attachmentPicker::pickDocument,
                // Anketa jde jen tam, kde to dovolí právo i druh chatu (`ContentAccess.polls`).
                onCreatePoll = {
                    onCreatePoll(PollComposerRequest(PollComposerMode.Any, state.replyingTo?.id, state.replyQuote))
                }.takeIf { state.content.polls },
                // Poloha je „základní zpráva" jako text: smí se všude, kde je vstupní pole.
                onSendLocation = { locationPickerOpen = true },
                // Kontakt je taky „základní zpráva" (TDLib ho řadí k textu), jako poloha.
                onShareContact = viewModel::openContactPicker,
                onToggleCommands = viewModel::toggleCommands.takeIf { state.botCommands.isNotEmpty() },
                menuApp = state.botMenuApp,
                onOpenMenuApp = viewModel::openBotMenuApp,
                attachments = state.attachments,
                // Vykreslovaná úprava fotky blokuje odeslání stejně jako kopírování z galerie.
                attachmentsPreparing = mediaSelection.preparing || state.markupBusy,
                onRemoveAttachment = viewModel::removeAttachment,
                onOpenAttachment = { index -> previewAttachmentIndex = index },
                onSetAttachmentMode = viewModel::setAttachmentMode,
                content = state.content,
                selfDestructAllowed = state.canSelfDestruct(attachmentCount = 1),
                previewAttachmentIndex = previewAttachmentIndex,
                expressionMode = expressionMode,
                composer = composer,
                onKeyboardContent = { received ->
                    when (received) {
                        // GIF odchází hned, jako samolepka; obrázek čeká nad polem jako z výběru.
                        is KeyboardContent.Animation -> viewModel.sendAnimation(received.animation)
                        is KeyboardContent.Image -> viewModel.addAttachments(listOf(received.photo))
                        is KeyboardContent.File -> viewModel.addAttachments(listOf(received.document))
                    }
                },
                onKeyboardContentFailed = viewModel::reportError,
            )
            }
            }
            }
        },
        // Tlačítko dolů ve FAB slotu: Scaffold ho drží nad polem a snackbar pak nad ním.
        // Uprostřed, ne vpravo: vpravo by viselo přímo nad tlačítkem odeslání a mikrofonu
        // a ťuknutí o kousek vedle by místo skoku dolů spustilo nahrávání.
        floatingActionButtonPosition = FabPosition.Center,
        floatingActionButton = {
            // Nad polem zrovna plave napovídání (viz `FloatingSuggestion`) — tlačítka by visela
            // přes něj, takže na tu chvíli ustoupí.
            AnimatedVisibility(
                visible = !suggestionsFloating,
                enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
            ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Zmínky a reakce nad tlačítkem dolů: jsou vidět, i když je uživatel dole — zmínka
            // bývá hluboko v nepřečtené historii a tlačítko dolů o ní nic neřekne.
            UnreadMarkButton(
                kind = UnreadMarkKind.Mention,
                count = state.unreadMarks.mentions,
                onClick = viewModel::jumpToUnreadMark,
                onReadAll = viewModel::readAllMarks,
            )
            UnreadMarkButton(
                kind = UnreadMarkKind.Reaction,
                count = state.unreadMarks.reactions,
                onClick = viewModel::jumpToUnreadMark,
                onReadAll = viewModel::readAllMarks,
            )
            UnreadMarkButton(
                kind = UnreadMarkKind.PollVote,
                count = state.unreadMarks.pollVotes,
                onClick = viewModel::jumpToUnreadMark,
                onReadAll = viewModel::readAllMarks,
            )
            ScrollToBottomButton(
                // V okně v historii vede dolů i od spodní hrany — nejnovější zprávy nejsou načtené.
                visible = farFromBottom || pendingNewMessages > 0 || !state.reachedEnd,
                count = pendingNewMessages,
                onClick = {
                    if (state.reachedEnd) {
                        scope.launch {
                            listState.animateScrollToBottom { index ->
                                items.getOrNull(index)?.let { measuredSizes[it.key] }
                            }
                        }
                    } else {
                        viewModel.jumpToLatest()
                    }
                },
            )
            }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // Nabídka nad zprávou se kreslí až v bublině — viz `MessageMenuOverlay`.

        if (mediaPickerOpen) {
            MediaPickerSheet(
                selection = mediaSelection,
                pickedPaths = state.attachments.map { state.galleryPath(it.path) },
                allowPhotos = state.content.photos,
                allowVideos = state.content.videos,
                onPicked = viewModel::addAttachments,
                onUnpicked = viewModel::removeAttachments,
                onError = viewModel::reportError,
                onDismiss = { mediaPickerOpen = false },
            )
        }

        state.contactPicker?.let { picker ->
            ContactPickerSheet(
                picker = picker,
                onQueryChange = viewModel::searchContacts,
                onPick = viewModel::pickContact,
                onDismiss = viewModel::dismissContactPicker,
            )
        }

        state.pendingContact?.let { contact ->
            ContactConfirmDialog(
                contact = contact,
                onConfirm = {
                    haptics.confirm()
                    viewModel.confirmContact()
                },
                onDismiss = viewModel::cancelContact,
            )
        }

        if (locationPickerOpen) {
            PlacePickerHost(
                searcher = viewModel.places,
                title = stringResource(Res.string.conversation_location_title),
                onPick = { place ->
                    locationPickerOpen = false
                    haptics.confirm()
                    viewModel.sendLocation(place)
                },
                onDismiss = { locationPickerOpen = false },
                autoFocusSearch = false,
                // Živou polohu jen tam, kde ji platforma umí posouvat, a ne v kanálu — příspěvek
                // kanálu s polohou, která se hýbe, oficiální klienti nenabízejí.
                onShareLive = {
                    locationPickerOpen = false
                    livePeriodOpen = true
                }.takeIf { liveLocationSharingSupported && state.info?.isChannel != true },
            )
        }

        // Přesná: živá poloha se sleduje na mapě, kilometrová přesnost by z ní udělala šum.
        val liveLocation = rememberDeviceLocation(precise = true)
        state.proximityAlert?.let { prompt ->
            ProximityAlertDialog(
                currentRadius = prompt.currentRadius,
                onPick = viewModel::setProximityAlert,
                onDismiss = viewModel::dismissProximityAlert,
            )
        }
        if (livePeriodOpen) {
            LiveLocationPeriodDialog(
                onPick = { period ->
                    livePeriodOpen = false
                    haptics.confirm()
                    liveLocation.request { point -> viewModel.shareLiveLocation(point, period) }
                },
                onDismiss = { livePeriodOpen = false },
            )
        }

        if (stickerPickerOpen) {
            StickerPickerSheet(
                mode = expressionMode,
                onModeChange = viewModel::setExpressionMode,
                // Při úpravě zprávy jen emoji: samolepku do upravované zprávy vložit nejde.
                stickersAvailable = state.editing == null && state.content.stickers,
                draft = draft,
                // Úprava z výběru emoji je psaní jako každé jiné: ukládá koncept i hlásí „píše…".
                onDraftEdit = {
                    draft = it
                    viewModel.onDraftChanged(composer.composed)
                },
                onPick = { sticker ->
                    stickerPickerOpen = false
                    haptics.confirm()
                    viewModel.sendSticker(sticker)
                },
                sendMenu = sendMenuOffer(state).copy(effects = false),
                onPickWith = { sticker, choice ->
                    stickerPickerOpen = false
                    when (choice) {
                        SendMenuChoice.Silent -> {
                            haptics.confirm()
                            viewModel.sendSticker(sticker, SendOptions(silent = true))
                        }
                        SendMenuChoice.WhenOnline -> {
                            haptics.confirm()
                            viewModel.sendSticker(sticker, SendOptions(schedule = MessageSchedule.WhenOnline))
                        }
                        SendMenuChoice.Schedule -> scheduleTarget = ScheduleTarget.Sticker(sticker)
                    }
                },
                chatId = viewModel.chatId,
                onPickGif = { gif ->
                    stickerPickerOpen = false
                    haptics.confirm()
                    viewModel.sendGif(gif)
                },
                onPickGifWith = { gif, choice ->
                    stickerPickerOpen = false
                    when (choice) {
                        SendMenuChoice.Silent -> {
                            haptics.confirm()
                            viewModel.sendGif(gif, SendOptions(silent = true))
                        }
                        SendMenuChoice.WhenOnline -> {
                            haptics.confirm()
                            viewModel.sendGif(gif, SendOptions(schedule = MessageSchedule.WhenOnline))
                        }
                        SendMenuChoice.Schedule -> scheduleTarget = ScheduleTarget.Gif(gif)
                    }
                },
                onDismiss = { stickerPickerOpen = false },
            )
        }

        // Sada z odkazu `t.me/addstickers/…` otevře týž sheet jako ťuknutí na samolepku.
        LaunchedEffect(state.openStickerSet) {
            val setId = state.openStickerSet ?: return@LaunchedEffect
            viewModel.consumeOpenStickerSet()
            openStickerSetId = setId
        }

        state.memberPicker?.let { picker ->
            MemberPickerSheet(
                picker = picker,
                onQueryChange = viewModel::onMemberQuery,
                onPick = viewModel::setSearchSender,
                onDismiss = viewModel::closeMemberPicker,
            )
        }

        state.invite?.let { invite ->
            InviteSheet(invite = invite, onJoin = viewModel::joinInvite, onDismiss = viewModel::dismissInvite)
        }

        state.folderInvite?.let { invite ->
            FolderInviteSheet(
                invite = invite,
                onToggle = viewModel::toggleFolderInviteChat,
                onAdd = viewModel::addFolderInvite,
                onDismiss = viewModel::dismissFolderInvite,
            )
        }

        state.loginRequest?.let { request ->
            LoginUrlDialog(prompt = request.prompt, onConfirm = viewModel::confirmLogin, onDismiss = viewModel::dismissLogin)
        }
        state.confirmLink?.let { url ->
            OpenLinkDialog(url = url, onOpen = viewModel::openConfirmedLink, onDismiss = viewModel::dismissLink)
        }

        state.loginWarning?.let { kind ->
            LoginLinkWarningDialog(kind = kind, onDismiss = viewModel::dismissLoginWarning)
        }

        state.botAlert?.let { text ->
            BotAlertDialog(text = text, onDismiss = viewModel::dismissBotAlert)
        }

        if (state.confirmSharePhone) {
            SharePhoneDialog(onShare = viewModel::sharePhone, onDismiss = viewModel::dismissSharePhone)
        }
        // Přesná: bot polohou volá taxi nebo kurýra, kilometrová přesnost tam nic neřekne.
        val keyboardLocation = rememberDeviceLocation(precise = true)
        if (state.confirmShareLocation) {
            ShareLocationDialog(
                onShare = {
                    viewModel.dismissShareLocation()
                    keyboardLocation.request(viewModel::sendKeyboardLocation)
                },
                onDismiss = viewModel::dismissShareLocation,
            )
        }

        openStickerSetId?.let { setId ->
            StickerSetSheet(
                setId = setId,
                onPick = { sticker ->
                    openStickerSetId = null
                    haptics.confirm()
                    viewModel.sendSticker(sticker)
                },
                onDismiss = { openStickerSetId = null },
            )
        }

        if (summaryState != ChatSummaryState.Idle) {
            ChatSummarySheet(
                state = summaryState,
                source = summarySourceState,
                onRetry = startSummary,
                onDismiss = summary::dismiss,
            )
        }

        state.messageInfo?.let { sheet ->
            MessageInfoSheet(
                sheet = sheet,
                onCopyLink = viewModel::copyMessageLink,
                onDismiss = viewModel::dismissMessageInfo,
            )
        }

        state.translation?.let { sheet ->
            TranslationSheet(
                sheet = sheet,
                onCopy = viewModel::copyTranslation,
                onLanguageChange = viewModel::changeTranslationLanguage,
                onDismiss = viewModel::dismissTranslation,
                onOnDeviceChange = viewModel::setTranslateOnDevice,
                onDownload = viewModel::downloadTranslationModels,
                onServer = viewModel::translateOnServer,
            )
        }

        state.selectingText?.let { message ->
            SelectTextSheet(message = message, onDismiss = viewModel::dismissTextSelection)
        }

        state.reactionDetails?.let { details ->
            ReactionDetailsSheet(
                details = details,
                onFilter = viewModel::filterReactionDetails,
                onDismiss = viewModel::dismissReactionDetails,
            )
        }

        if (state.addingPollOption != null) {
            AddPollOptionDialog(
                chatId = viewModel.chatId,
                places = viewModel.places,
                onConfirm = viewModel::addPollOption,
                onError = viewModel::reportError,
                onDismiss = viewModel::dismissAddPollOption,
            )
        }

        state.giveaway?.let { sheet ->
            GiveawayStatusSheet(
                sheet = sheet,
                onCopyCode = viewModel::copyGiftCode,
                onDismiss = viewModel::dismissGiveaway,
            )
        }

        state.pollVoters?.let { messageId ->
            state.messages.firstOrNull { it.id == messageId }?.poll?.let { poll ->
                PollVotersSheet(
                    chatId = viewModel.chatId,
                    messageId = messageId,
                    poll = poll,
                    onOpenVoter = viewModel::openPollVoter,
                    onDismiss = viewModel::dismissPollVoters,
                )
            }
        }

        if (state.pendingStopPoll != null) {
            StopPollDialog(onConfirm = viewModel::confirmStopPoll, onDismiss = viewModel::cancelStopPoll)
        }

        // Jednorázová událost od tlačítka bota. Navigace je synchronní, takže vynulování potom
        // efekt nezruší uprostřed (past „Vrátit" v docs/poznatky/konverzace-pohyb.md se týká práce, která teprve běží).
        state.openPollComposer?.let { mode ->
            LaunchedEffect(mode) {
                onCreatePoll(PollComposerRequest(mode, state.replyingTo?.id, state.replyQuote))
                viewModel.consumeOpenPollComposer()
            }
        }
        state.openBotShare?.let { request ->
            LaunchedEffect(request) {
                viewModel.consumeOpenBotShare()
                onOpenBotShare(request)
            }
        }
        state.openWebApp?.let { request ->
            LaunchedEffect(request) {
                viewModel.consumeOpenWebApp()
                onOpenWebApp(request)
            }
        }

        state.pendingPin?.let { pending ->
            PinMessageDialog(
                pending = pending,
                partnerName = state.info?.title,
                onConfirm = viewModel::confirmPin,
                onDismiss = viewModel::cancelPin,
            )
        }

        state.pendingDelete?.let { pending ->
            MessageDeleteDialog(
                pending = pending,
                partnerName = state.info?.title,
                onConfirm = viewModel::confirmDelete,
                onDismiss = viewModel::cancelDelete,
            )
        }

        val layoutDirection = LocalLayoutDirection.current
        // Pruh připnutých a vstupní pole leží PŘES seznam — fotka letící pod ně se o ně ořízne.
        val clipDensity = LocalDensity.current
        val obscuredBottom = padding.calculateBottomPadding()
        SideEffect {
            with(clipDensity) {
                listClip.obscuredTop = pinnedHeight.toPx()
                listClip.obscuredBottom = obscuredBottom.toPx()
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(
                    top = padding.calculateTopPadding(),
                    start = padding.calculateStartPadding(layoutDirection),
                    end = padding.calculateEndPadding(layoutDirection),
                )
                .onGloballyPositioned { contentTop.value = it.boundsInWindow().top },
        ) {
            when {
                /*
                 * Omezený celý chat: místo zpráv jen důvod od serveru. Stojí PŘED načítáním —
                 * historie se sice načte, ale ukazovat se nesmí, a točící se indikátor by
                 * sliboval, že přijde.
                 */
                state.info?.restriction != null -> IntroEmptyState(
                    icon = Res.drawable.block_24dp,
                    title = stringResource(Res.string.conversation_chat_restricted),
                    text = state.info?.restriction.orEmpty(),
                    modifier = Modifier.fillMaxSize(),
                )

                // Citlivý chat: napřed varování, obsah až po výslovném „přesto zobrazit".
                sensitiveWarning -> IntroEmptyState(
                    icon = Res.drawable.visibility_off_24dp,
                    title = stringResource(Res.string.conversation_chat_sensitive_title),
                    text = stringResource(Res.string.conversation_chat_sensitive_text),
                    modifier = Modifier.fillMaxSize(),
                    action = {
                        FilledTonalButton(onClick = { sensitiveAccepted = true }) {
                            Text(stringResource(Res.string.conversation_chat_sensitive_show))
                        }
                    },
                )

                state.messages.isEmpty() && state.loading ->
                    LoadingIndicator(Modifier.align(Alignment.Center))

                showsBotIntro(state) -> {
                    val shownConnection = rememberShownConnection(connection)
                    BotIntroPage(
                        intro = state.botIntro ?: return@Box,
                        chatId = viewModel.chatId,
                        title = state.info?.title.orEmpty(),
                        avatarFileId = state.info?.avatarFileId,
                        connection = shownConnection,
                        connectionLabel = shownConnection?.label()?.let { stringResource(it) },
                        online = state.presence is Presence.Online,
                        onAction = { itemScope.onAction(0L, it) },
                    )
                }

                state.messages.isEmpty() -> Text(
                    text = state.error?.resolve() ?: stringResource(Res.string.conversation_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )

                /*
                 * Gesto patří SEZNAMU, ne položce.
                 *
                 * Bublina si bere jen tah doprava (odpověď) a doleva nic nespotřebuje, takže
                 * gesto propadne sem. Svislý scroll má přednost: seznam je potomek, dostane
                 * události dřív a jeho spotřebování tenhle detektor zruší — a detektor se
                 * rozhoduje až po delším a výrazně vodorovném pohybu (`detectHorizontalSwipe`).
                 */
                else -> Box(
                    Modifier
                        .fillMaxSize()
                        /*
                         * `LazyColumn` ořezává jen ve směru scrollu, tedy svisle. Sloupec časů
                         * čekající za pravým okrajem byl schovaný jen proto, že seznam končil
                         * u okraje obrazovky; s bočním insetem (výřez vpravo) prosvítal. Ořez
                         * drží i odtaženou bublinu v jejím panelu.
                         */
                        .clipToBounds()
                        // Kam ořezává seznam, tam se ořezává i fotka letící z prohlížeče zpátky
                        // (viz `SharedElementClip`) — jinak by na konci letu ležela přes hlavičku.
                        .sharedElementClipArea(listClip)
                        .hazeSource(listHaze)
                        .pointerInput(revealMax) {
                            detectHorizontalSwipe(
                                direction = SwipeDirection.Left,
                                onEnd = {
                                    gestureHints.used(GestureHint.RevealTimes)
                                    scope.launch { timeReveal.animateTo(0f, revealSpec) }
                                },
                            ) { delta ->
                                scope.launch {
                                    // Jen doleva a jen po šířku sloupce s časy; dál už není co
                                    // odkrývat a gumové tahání by jen mátlo.
                                    timeReveal.snapTo((timeReveal.value - delta).coerceIn(0f, revealMax))
                                }
                            }
                        },
                ) {
                  CompositionLocalProvider(LocalSharedElementClip provides listClip) {
                  LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    // Odjezd při přechodu na vzdálenou zprávu; čte se až ve vrstvě.
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        alpha = jumpTransition.listAlpha
                        translationY = jumpTransition.listShift
                    },
                    // Dole se odsazuje o výšku `bottomBar` (vstupního pole), aby nejnovější
                    // zpráva v klidu nekončila pod ním. Při scrollu obsah pod pole zajede.
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp + pinnedHeight,
                        bottom = 8.dp + padding.calculateBottomPadding(),
                    ),
                    // Rozestup UVNITŘ skupiny zpráv od jednoho odesílatele. Mezera mezi skupinami
                    // se přidává až v bublině — tady na to není informace o sousedech.
                    verticalArrangement = Arrangement.spacedBy(MESSAGE_SPACING),
                ) {
                    items(items, key = { it.key }, contentType = { it.contentType }) { item ->
                        ConversationItemContent(item, itemScope)
                    }
                }
                  }
                  // Nápověda k odkrývání časů ukazuje k pravému okraji, odkud časy přijedou.
                  RevealTimesHintAnchor(gestureHints, Modifier.align(Alignment.CenterEnd))
                }
            }

            /*
             * Plovoucí datum.
             *
             * Ukáže se jen při scrollu a zase zmizí. V dlouhé historii je předěl často mimo
             * obrazovku a bez tohohle by se při hledání starší zprávy nedalo poznat, kde zrovna
             * jsem. Je to overlay, ne `stickyHeader`: ten se v obráceném seznamu lepí na jeho
             * začátek, tedy dolů, což je přesně naopak.
             */
            AnimatedVisibility(
                visible = listState.isScrollInProgress && floatingDay.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                FloatingDayBadge(
                    text = floatingDay,
                    glass = Modifier.hazeBlur(
                        input = HazeInput.Sources(listHaze),
                        style = glassStyle(
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            alpha = GLASS_TEXT_TINT_ALPHA,
                            fallbackAlpha = GLASS_FALLBACK_ALPHA,
                        ),
                    ),
                    modifier = Modifier.padding(top = 8.dp + pinnedHeight),
                )
            }

            /*
             * Připnuté zprávy.
             *
             * Plavou nad seznamem jako vstupní pole, ne v hlavičce Scaffoldu: seznam pod nimi zajíždí
             * a odsazuje se o jejich ZMĚŘENOU výšku. Kdyby byly součástí `topBar`, ukusovaly by místo
             * i ve chvíli, kdy odjíždějí, a obsah by pod nimi na konci animace poskočil.
             *
             * Ukazuje se jedna zpráva (viz `PinnedMessagesBar`), a **která, rozhoduje scroll**
             * (`pinnedForScroll`): ta, kterou uživatel právě míjí. Pruh tím slouží i jako
             * orientace v historii. Nedrží se proto žádný stav — odvozuje se z klíče nejvyšší
             * viditelné položky, takže nová připnutá zpráva výběr nerozhodí.
             */
            var allPinnedOpen by rememberSaveable { mutableStateOf(false) }

            val density = LocalDensity.current
            /*
             * Pruh proti spamu a připnuté zprávy plavou nad seznamem spolu a měří se spolu: seznam
             * se odsazuje o výšku obou. Spam stojí nahoře — je důležitější než připnutá zpráva.
             */
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .onSizeChanged { pinnedHeight = with(density) { it.height.toDp() } },
            ) {
                Column(Modifier.onSizeChanged { barsHeight = with(density) { it.height.toDp() } }) {
                    // Při odjezdu se musí dál kreslit poslední pruh, ne nic (stejně jako u připnutých).
                    // Při odjezdu se kreslí poslední pruh — stav je v tu chvíli už prázdný.
                    val lastChatBar = remember { arrayOf<ChatBar?>(null) }
                    if (chatBar != null) lastChatBar[0] = chatBar
                    AnimatedVisibility(
                        visible = chatBar != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        when (val bar = lastChatBar[0]) {
                            is SpamBar -> SpamBarCard(
                                bar = bar,
                                onBlock = { spamBarAction = SpamBarAction.Block },
                                onReport = { spamBarAction = SpamBarAction.Report },
                                onAddContact = viewModel::addSpamBarContact,
                                onUnarchive = viewModel::unarchiveFromSpamBar,
                                onDismiss = viewModel::dismissChatBar,
                            )
                            SharePhoneBar, InviteMembersBar, is JoinRequestBar -> ChatNoticeBarCard(
                                bar = bar,
                                onSharePhone = { confirmSharePhone = true },
                                onInviteMembers = onAddMembers,
                                onDismiss = viewModel::dismissChatBar,
                            )
                            null -> Unit
                        }
                    }
                    // Žádosti o vstup hned pod spamem: čekají na rozhodnutí, jako on.
                    val shownJoinRequests = joinRequests?.takeIf { it.count > joinRequestsHiddenAt }
                    val lastJoinRequests = remember { arrayOf<PendingJoinRequests?>(null) }
                    if (shownJoinRequests != null) lastJoinRequests[0] = shownJoinRequests
                    AnimatedVisibility(
                        visible = shownJoinRequests != null && !chatHidden,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        lastJoinRequests[0]?.let { requests ->
                            JoinRequestsBar(
                                requests = requests,
                                onClick = onOpenJoinRequests,
                                onHide = { joinRequestsHiddenAt = requests.count },
                                glass = Modifier.hazeBlur(
                                    input = HazeInput.Sources(listHaze),
                                    style = glassStyle(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        alpha = GLASS_TEXT_TINT_ALPHA,
                                        fallbackAlpha = GLASS_FALLBACK_ALPHA,
                                    ),
                                ),
                            )
                        }
                    }
                    // Videochat mezi spamem a připnutými: o dění v chatu, ne o obsahu.
                    val lastVideoChat = remember { arrayOf<VideoChatInfo?>(null) }
                    if (videoChat != null) lastVideoChat[0] = videoChat
                    AnimatedVisibility(
                        visible = videoChat != null && !chatHidden,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        lastVideoChat[0]?.let { info ->
                            VideoChatBar(
                                info = info,
                                onClick = viewModel::explainVideoChat,
                                glass = Modifier.hazeBlur(
                                    input = HazeInput.Sources(listHaze),
                                    style = glassStyle(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        alpha = GLASS_TEXT_TINT_ALPHA,
                                        fallbackAlpha = GLASS_FALLBACK_ALPHA,
                                    ),
                                ),
                            )
                        }
                    }
                    AnimatedVisibility(
                        // Omezený chat: připnutá zpráva by ukázala obsah, který se ukázat nesmí.
                        visible = state.pinned.isNotEmpty() && !chatHidden,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        // Při odjezdu se musí dál kreslit poslední zpráva, ne prázdný pruh.
                        val last = remember { arrayOf(emptyList<ChatMessage>()) }
                        if (state.pinned.isNotEmpty()) last[0] = state.pinned
                        val pinned = last[0]

                        PinnedMessagesBar(
                            messages = pinned,
                            index = pinnedIndex(pinned, pinnedForScroll(pinned, topVisibleKey)),
                            canUnpin = state.canUnpin,
                            // Ťuknutí skočí na ukázanou zprávu, a to KOTVOU NAHORU: zpráva pak stojí nad
                            // viditelnou částí, takže pruh přejde na předchozí připnutou a další ťuknutí
                            // vede o krok zpět. Se středem se nad cílem odkryje kus historie a pruh
                            // přeskočí rovnou k nejstarší.
                            onOpen = { message -> viewModel.jumpToMessage(message.id, anchor = ScrollAnchor.Top) },
                            onUnpin = viewModel::unpin,
                            onShowAll = { allPinnedOpen = true },
                            glass = Modifier.hazeBlur(
                                input = HazeInput.Sources(listHaze),
                                style = glassStyle(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    alpha = GLASS_TEXT_TINT_ALPHA,
                                    fallbackAlpha = GLASS_FALLBACK_ALPHA,
                                ),
                            ),
                        )
                    }
                }

                /*
                 * Dock živé polohy: poslední řádek pod pruhy, vysouvá se tím, jak zpráva s polohou
                 * odjíždí z obrazovky. Ve vlákně komentářů polohy nejsou (`liveLocations` prázdné).
                 */
                if (!chatHidden && shownDocked.isNotEmpty()) {
                    LiveLocationDockBar(
                        locations = shownDocked,
                        reveal = { dockReveal.value },
                        expanded = dockExpanded,
                        onToggle = { dockExpanded = !dockExpanded },
                        chatTitle = state.info?.title.orEmpty(),
                        onOpenMap = { message ->
                            (message.media as? MessageMedia.Location)?.let {
                                viewModel.onAction(message.id, MessageAction.OpenLink(it.mapUrl))
                            }
                        },
                        onShowMessage = { message ->
                            dockExpanded = false
                            viewModel.jumpToMessage(message.id)
                        },
                        onStop = { message -> viewModel.onAction(message.id, MessageAction.StopLiveLocation) },
                        onProximityAlert = { message -> viewModel.onAction(message.id, MessageAction.SetProximityAlert) },
                        glass = Modifier.hazeBlur(
                            input = HazeInput.Sources(listHaze),
                            style = glassStyle(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                alpha = GLASS_TEXT_TINT_ALPHA,
                                fallbackAlpha = GLASS_FALLBACK_ALPHA,
                            ),
                        ),
                    )
                }

                // Skladbu z jiného chatu ukazuje i omezený chat — neprozradí z něj nic a zastavit ji jinde nejde.
                ConversationPlayerDock(
                    dock = playerDock,
                    chatHidden = chatHidden,
                    viewModel = viewModel,
                    voicePlayback = voicePlayback,
                    soundingVideoNote = soundingVideoNote,
                    onOpenChat = onOpenChat,
                    glass = Modifier.hazeBlur(
                        input = HazeInput.Sources(listHaze),
                        style = glassStyle(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            alpha = GLASS_TEXT_TINT_ALPHA,
                            fallbackAlpha = GLASS_FALLBACK_ALPHA,
                        ),
                    ),
                )
            }

            state.scheduledMessages?.let { scheduled ->
                ScheduledMessagesSheet(
                    messages = scheduled,
                    onSendNow = { message -> viewModel.reschedule(message.id, null) },
                    onReschedule = { message -> scheduleTarget = ScheduleTarget.Existing(message.id) },
                    onDelete = { message -> viewModel.deleteScheduled(message.id) },
                    onDismiss = viewModel::closeScheduled,
                )
            }

            state.remindRequest?.let { request ->
                val messageId = request.messageId
                RemindSheet(
                    suggested = request.suggestedAt,
                    suggesting = request.suggesting,
                    fromModel = request.fromModel,
                    onRemind = { at ->
                        haptics.confirm()
                        viewModel.remind(messageId, at)
                    },
                    // Přesný čas jde týmiž dialogy jako plánování zprávy.
                    onPickTime = {
                        viewModel.closeRemind()
                        scheduleTarget = ScheduleTarget.Reminder(messageId)
                    },
                    onDismiss = viewModel::closeRemind,
                )
            }

            if (scheduleTarget != null && scheduleDay == null) {
                FutureDayDialog(
                    now = Clock.System.now().epochSeconds,
                    maxAheadSeconds = MAX_SCHEDULE_AHEAD_SECONDS,
                    onPick = { day -> scheduleDay = day },
                    onDismiss = {
                        // Hlasovka čekala pozastavená; bez času se nahrává dál.
                        if (scheduleTarget == ScheduleTarget.Voice) viewModel.resumeRecording()
                        scheduleTarget = null
                    },
                )
            }
            val target = scheduleTarget
            val day = scheduleDay
            if (target != null && day != null) {
                TimeOfDayDialog(
                    title = stringResource(
                        if (target is ScheduleTarget.Reminder) Res.string.remind_time else Res.string.conversation_schedule_time,
                    ),
                    // Nabídne se za hodinu od teď — nejčastěji se plánuje „na později dnes".
                    initial = Clock.System.now().epochSeconds + SCHEDULE_DEFAULT_AHEAD_SECONDS,
                    onPick = { hour, minute ->
                        scheduleTarget = null
                        scheduleDay = null
                        val at = closeDateOf(day, hour, minute, TimeZone.currentSystemDefault())
                        if (!scheduleValid(at, Clock.System.now().epochSeconds)) {
                            haptics.reject()
                            viewModel.reportError(UiText(Res.string.conversation_schedule_past))
                            if (target == ScheduleTarget.Voice) viewModel.resumeRecording()
                            return@TimeOfDayDialog
                        }
                        val schedule = MessageSchedule.At(at)
                        when (target) {
                            ScheduleTarget.Draft -> {
                                haptics.confirm()
                                viewModel.send(composer.composed, SendOptions(schedule = schedule))
                                draft = TextFieldValue()
                            }
                            ScheduleTarget.Voice -> {
                                haptics.confirm()
                                viewModel.sendRecording(SendOptions(schedule = schedule))
                            }
                            is ScheduleTarget.Existing -> viewModel.reschedule(target.messageId, schedule)
                            is ScheduleTarget.Reminder -> {
                                haptics.confirm()
                                viewModel.remind(target.messageId, at)
                            }
                            is ScheduleTarget.Sticker -> {
                                haptics.confirm()
                                viewModel.sendSticker(target.sticker, SendOptions(schedule = schedule))
                            }
                            is ScheduleTarget.Gif -> {
                                haptics.confirm()
                                viewModel.sendGif(target.gif, SendOptions(schedule = schedule))
                            }
                        }
                    },
                    onDismiss = {
                        if (scheduleTarget == ScheduleTarget.Voice) viewModel.resumeRecording()
                        scheduleTarget = null
                        scheduleDay = null
                    },
                )
            }

            if (effectPickerOpen) {
                val effects by viewModel.effectChoices.collectAsStateWithLifecycle()
                EffectPickerSheet(
                    effects = effects,
                    onPick = { effect ->
                        effectPickerOpen = false
                        haptics.confirm()
                        viewModel.send(composer.composed, SendOptions(effectId = effect.id))
                        draft = TextFieldValue()
                    },
                    onDismiss = { effectPickerOpen = false },
                )
            }

            if (allPinnedOpen && state.pinned.isNotEmpty()) {
                PinnedMessagesSheet(
                    messages = state.pinned,
                    canUnpin = state.canUnpin,
                    onOpen = { message -> viewModel.jumpToMessage(message.id, anchor = ScrollAnchor.Top) },
                    onUnpin = viewModel::unpin,
                    onDismiss = { allPinnedOpen = false },
                )
            }

            // Nad seznamem, pod vstupním polem: odeslání i koš zůstávají ovladatelné.
            VideoNoteRecordingOverlay(
                visible = isVideoRecording,
                camera = videoNoteCamera,
                recording = { videoRecordingState.value },
                modifier = Modifier.fillMaxSize().padding(bottom = obscuredBottom),
                preview = videoNotePreview,
                playback = videoNotePlayback,
            )
            // Zámek nad tlačítkem je tady, ne v liště — sklo pod polem by ho ořízlo.
            RecordLockHost(
                visible = isRecording && recordGesture.held && !recordGesture.locked,
                gesture = recordGesture,
                modifier = Modifier.fillMaxSize().padding(bottom = obscuredBottom),
            )

        }
    }

    MessageMenuLayer(
        menu = state.messageMenu,
        item = state.messageMenu?.let { menu ->
            items.firstOrNull { it is MessageItem && it.frame.message.id == menu.messageId } as? MessageItem
        },
        anchor = menuAnchor,
        contentTop = contentTop.value,
        contentBottom = contentBottom.value,
        itemScope = itemScope,
        hazeState = hazeState,
        onShownChange = { menuKey.value = it },
    )

    StagedAttachmentPreviewHost(
        state = state,
        viewModel = viewModel,
        index = previewAttachmentIndex,
        onIndexChange = { previewAttachmentIndex = it },
    )
    }
}

/**
 * Tlačítko „dolů", s počtem nových zpráv, když nějaké čekají.
 *
 * Objeví se, když je uživatel v historii — dole se totiž nescrolluje automaticky, aby ho nová
 * zpráva nevytrhla ze čtení. Bez nových zpráv je to jen kulatá šipka; s nimi se roztáhne
 * o počet, protože bez čísla by uživatel nevěděl, jestli se vyplatí skočit dolů.
 */
@Composable
private fun ScrollToBottomButton(
    visible: Boolean,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        // Vjezd zespoda: tlačítko přiletí odtamtud, kam po ťuknutí odskočí.
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = 4.dp,
        ) {
            Row(
                // Bez počtu je z toho kruh: šířka i výška jsou dané touhle minimální plochou.
                Modifier
                    .heightIn(min = SCROLL_DOWN_BUTTON_SIZE)
                    .widthIn(min = SCROLL_DOWN_BUTTON_SIZE)
                    .padding(horizontal = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    painterResource(Res.drawable.arrow_downward_24dp),
                    contentDescription = if (count > 0) null else stringResource(Res.string.conversation_scroll_to_end),
                    modifier = Modifier.size(18.dp),
                )

                // Mezera je v obsahu, ne ve `spacedBy`: skrytý AnimatedVisibility je pořád potomek
                // a rozestup by kruh bez počtu nafoukl do šišky.
                AnimatedVisibility(
                    visible = count > 0,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut(),
                ) {
                    // Při zmizení se musí dál kreslit poslední nenulový počet, ne „0 zpráv".
                    // Obyčejné pole, ne stav: zápis do stavu v kompozici by jen vynutil další.
                    val shown = remember { intArrayOf(count) }
                    if (count > 0) shown[0] = count

                    NewMessagesLabel(shown[0], Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/**
 * Tlačítko „@" (nepřečtené zmínky) nebo „❤" (nepřečtené reakce na vlastní zprávy) s počtem.
 *
 * Ťuknutí skočí na nejstarší, podržení nabídne „Označit vše jako přečtené" — tak to mají
 * i oficiální klienti. Vzhledem je to sourozenec tlačítka dolů: stejný kruh, stejná role,
 * počet nese odznak v rohu, aby se kruh netáhl do šířky.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UnreadMarkButton(
    kind: UnreadMarkKind,
    count: Int,
    onClick: (UnreadMarkKind) -> Unit,
    onReadAll: (UnreadMarkKind) -> Unit,
) {
    // Při zmizení se musí dál kreslit poslední nenulový počet, ne nula (viz tlačítko dolů).
    val shown = remember { intArrayOf(count) }
    if (count > 0) shown[0] = count

    AnimatedVisibility(
        visible = count > 0,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
    ) {
        var menuOpen by remember { mutableStateOf(false) }
        val label = when (kind) {
            UnreadMarkKind.Mention -> pluralStringResource(Res.plurals.conversation_unread_mentions, shown[0], shown[0])
            UnreadMarkKind.Reaction -> pluralStringResource(Res.plurals.conversation_unread_reactions, shown[0], shown[0])
            UnreadMarkKind.PollVote -> pluralStringResource(Res.plurals.conversation_unread_poll_votes, shown[0], shown[0])
        }
        val readAllLabel = stringResource(
            when (kind) {
                UnreadMarkKind.Mention -> Res.string.conversation_read_all_mentions
                UnreadMarkKind.Reaction -> Res.string.conversation_read_all_reactions
                UnreadMarkKind.PollVote -> Res.string.conversation_read_all_poll_votes
            },
        )

        // Mezera pod tlačítkem je v obsahu, ne ve `spacedBy` sloupce: skryté tlačítko by jinak
        // nechalo nad tlačítkem dolů prázdné místo.
        Box(Modifier.padding(bottom = 10.dp)) {
            BadgedBox(
                badge = {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) {
                        RollingNumber(shown[0], style = LocalTextStyle.current.tabularNumbers())
                    }
                },
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 4.dp,
                ) {
                    // Klikání uvnitř `Surface`: ten ořezává obsah tvarem, takže vlnka zůstane
                    // v kruhu, a stín, který by `clip` zvenku uřízl, zůstane celý.
                    Box(
                        Modifier
                            .size(SCROLL_DOWN_BUTTON_SIZE)
                            .combinedClickable(
                                onClickLabel = label,
                                onLongClickLabel = readAllLabel,
                                onLongClick = { menuOpen = true },
                                onClick = { onClick(kind) },
                            )
                            .semantics { contentDescription = label },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painterResource(
                                when (kind) {
                                    UnreadMarkKind.Mention -> Res.drawable.alternate_email_24dp
                                    UnreadMarkKind.Reaction -> Res.drawable.favorite_filled_24dp
                                    UnreadMarkKind.PollVote -> Res.drawable.ballot_24dp
                                },
                            ),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            ExpressiveMenu(
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                groups = listOf(
                    listOf(MenuAction(label = readAllLabel, icon = Res.drawable.done_all_24dp) { onReadAll(kind) }),
                ),
            )
        }
    }
}

/**
 * „N nových zpráv" s přetáčeným číslem ([RollingNumber]). Skloňování řeší `plurals`
 * v prostředcích (čeština má tři tvary, angličtina dva), takže se věta nesestavuje v kódu:
 * číslo se do ní dosadí jako značka a věta se kolem ní rozdělí. Compose Resources dosazuje
 * argumenty jako text (`replaceWithArgs`), takže značka projde beze změny.
 */
@Composable
private fun NewMessagesLabel(count: Int, modifier: Modifier = Modifier) {
    val template = pluralStringResource(Res.plurals.conversation_new_messages, count, NUMBER_MARK)
    val before = template.substringBefore(NUMBER_MARK)
    val after = template.substringAfter(NUMBER_MARK, missingDelimiterValue = "")
    val style = MaterialTheme.typography.labelLarge
    val label = pluralStringResource(Res.plurals.conversation_new_messages, count, count)
    Row(modifier.clearAndSetSemantics { contentDescription = label }) {
        if (before.isNotEmpty()) Text(before, style = style)
        RollingNumber(count, style = style.tabularNumbers())
        if (after.isNotEmpty()) Text(after, style = style)
    }
}

/** Značka místa čísla ve větě z prostředků; znak ze soukromé oblasti Unicode se v textu nevyskytne. */
private const val NUMBER_MARK = "\uE000"

@Composable
private fun MessageInput(
    /** Mléčné sklo nad seznamem: pás je průhledný, barvu i rozostření nese sklo. */
    glass: Modifier,
    replyingTo: ChatMessage?,
    /** Citovaný úryvek [replyingTo], nebo `null` (odpověď na celou zprávu). */
    replyQuote: String?,
    partnerName: String?,
    flight: MessageFlight?,
    onCancelReply: () -> Unit,
    onSend: (ComposedText, SendOptions) -> Unit,
    /** Co nabídne podržení odesílacího tlačítka ([sendMenuOffer]). */
    sendMenu: SendMenuOffer = SendMenuOffer(silent = false, schedule = false, whenOnline = false),
    /** „Naplánovat…" z nabídky; výběr času i odeslání řeší obrazovka, která drží text. */
    onSchedule: () -> Unit = {},
    /** Podržení → „Odeslat s efektem…": otevře výběr efektu. */
    onPickEffect: () -> Unit = {},
    /** Otevře seznam naplánovaných zpráv; `null`, když chat žádné nemá. */
    onOpenScheduled: (() -> Unit)? = null,
    /**
     * Jen ruční úpravy textu a formátování — vyprázdnění po odeslání se nehlásí, o to se stará
     * `onSend`.
     */
    onDraftChanged: (ComposedText) -> Unit,
    /** Upravovaná zpráva; karta pak místo citace ukazuje, co se upravuje. */
    editing: ChatMessage?,
    onCancelEdit: () -> Unit,
    /** Nápověda do prázdného pole, kterou si přeje bot; `null` = výchozí. */
    placeholder: String? = null,
    /** Náhled odkazu v rozepsaném textu, pokud se má ukázat ([composerLinkPreview]). */
    linkPreview: LinkPreview? = null,
    /** Odkazy v rozepsaném textu; s víc než jedním nabídne sheet, ke kterému náhled patří. */
    linkPreviewLinks: List<String> = emptyList(),
    linkPreviewOptions: LinkPreviewOptions = LinkPreviewOptions(),
    onLinkPreviewOptions: (LinkPreviewOptions) -> Unit = {},
    /** Text, který se má jednou vložit do pole (koncept, úprava). */
    inputFill: InputFill?,
    onInputFilled: (InputFill) -> Unit,
    onOpenStickers: () -> Unit,
    onPickMedia: () -> Unit,
    onPickDocument: () -> Unit,
    /** Založit anketu; `null`, kde anketa nejde. */
    onCreatePoll: (() -> Unit)?,
    /** Poslat polohu; `null`, kde nejde. */
    onSendLocation: (() -> Unit)?,
    onShareContact: (() -> Unit)?,
    /** Seznam příkazů botů; `null` v chatu bez botů s příkazy. */
    onToggleCommands: (() -> Unit)?,
    /** Tlačítko nabídky bota s mini aplikací; když je, stojí místo tlačítka příkazů. */
    menuApp: BotMenuApp? = null,
    onOpenMenuApp: () -> Unit = {},
    isRecording: Boolean,
    /** Čte se až v pruhu nahrávání — čas a úrovně se mění dvacetkrát za vteřinu. */
    recording: () -> VoiceRecording?,
    /**
     * Co nahraje podržení tlačítka v prázdném poli ([recordModeFor]); `null` = nic, tlačítko
     * je pak odeslání.
     */
    recordMode: RecordMode?,
    /** Ťuknutí na tlačítko přepne hlasovku ↔ kolečko ([canSwitchRecordMode]). */
    canSwitchRecordMode: Boolean,
    onSwitchRecordMode: () -> Unit,
    /** Co se právě nahrává — podle toho popis tlačítka odeslání. */
    recordingKind: RecordMode,
    /** Gesto nahrávání; drží ho obrazovka, protože zámek nad tlačítkem kreslí ona. */
    recordGesture: RecordGestureState,
    /**
     * Začne nahrávat. [held] = prst drží tlačítko; když se oprávnění teprve žádá, nahrávat se
     * potom nesmí (prst mezitím tlačítko pustil). Bez prstu (čtečka obrazovky) se začne zamčené.
     */
    onStartRecording: (held: Boolean) -> Unit,
    onCancelRecording: () -> Unit,
    /**
     * Zastavená hlasovka k poslechu před odesláním; `null` = žádná. Karta s ní vypadá jako při
     * nahrávání (vlnka, barva, odeslání hlasu), jen místo živých úrovní ukazuje celý waveform.
     */
    voicePreview: VoicePreview? = null,
    /** Přehrávání náhledu; čte se až v pruhu náhledu. */
    voicePreviewPlayback: () -> VoicePlayback? = { null },
    /** ■ u zamčené hlasovky: ukončí nahrávání a místo odeslání ukáže náhled. */
    onStopRecording: () -> Unit = {},
    onToggleVoicePreview: () -> Unit = {},
    onSeekVoicePreview: (Float) -> Unit = {},
    onDiscardVoicePreview: () -> Unit = {},
    onContinueVoicePreview: () -> Unit = {},
    /** Zastavené kolečko; samo hraje v kruhu nad konverzací, karta ukazuje jen ovládání. */
    videoNotePreview: VideoNotePreview? = null,
    videoNotePlayback: VideoNotePreviewPlayback? = null,
    onDiscardVideoNotePreview: () -> Unit = {},
    onContinueVideoNotePreview: () -> Unit = {},
    onSendRecording: (SendOptions) -> Unit,
    /** Nabídka odeslání nad hlasovkou se otevřela (`true`) nebo zavřela bez volby (`false`). */
    onVoiceMenu: (open: Boolean) -> Unit = {},
    /** „Naplánovat…" nad hlasovkou; výběr času řeší obrazovka, nahrávání je mezitím pozastavené. */
    onScheduleRecording: () -> Unit = {},
    /** Přílohy čekající na odeslání; text pole je pak jejich popisek. */
    attachments: List<OutgoingAttachment>,
    /**
     * Vybrané přílohy se ještě kopírují (velké video trvá vteřiny). Bez ukazatele by se po
     * zavření galerie chvíli nedělo nic a vypadalo by to, že výběr nezabral.
     */
    attachmentsPreparing: Boolean = false,
    onRemoveAttachment: (Int) -> Unit,
    onOpenAttachment: (Int) -> Unit = {},
    onSetAttachmentMode: (Int, AttachmentMode) -> Unit = { _, _ -> },
    /** Soukromý chat, kde se u jedné přílohy smí nabídnout mizení (`canSelfDestruct`). */
    selfDestructAllowed: Boolean = false,
    previewAttachmentIndex: Int? = null,
    /** Ikona tlačítka výběru ukazuje, čím se výběr otevře — emoji, nebo samolepkami. */
    expressionMode: ExpressionMode,
    /** Rozepsaný text i s formátováním; drží ho obrazovka, protože do něj vkládá i výběr emoji. */
    composer: ComposerDraft,
    /**
     * Které druhy obsahu chat přijme. Co se poslat nesmí, se nenabízí: sponka schová položku
     * (a bez jediné i sama sebe), mikrofon ustoupí odeslání, samolepky zůstanou na emoji.
     */
    content: ContentAccess = ContentAccess.All,
    /** GIF nebo obrázek vložený z klávesnice (Gboard), už zkopírovaný do cache. */
    onKeyboardContent: (KeyboardContent) -> Unit = {},
    onKeyboardContentFailed: (UiText) -> Unit = {},
    /** Nápovědy gest napoprvé; pole do nich hlásí, co právě dělá tlačítko odeslání. */
    gestureHints: GestureHints? = null,
    modifier: Modifier = Modifier,
) {
    var draft by composer
    /*
     * Přepis konceptu modelem v zařízení („Přepsat…" v nabídce odeslání). Žije tady, ne
     * v `ConversationScreen`, který je v dexu na hraně (viz „Gotchas" v CLAUDE.md). Přepsaný
     * text se jen VLOŽÍ do pole, jako návrh odpovědi; formátování konceptu se zahodí — model
     * vrací holý text a rozsahy by na něm ujely.
     */
    val rewrite = rememberDraftRewrite()
    DraftRewriteSheet(rewrite) { text ->
        composer.fill(text, emptyList())
        onDraftChanged(composer.composed)
    }
    // Gesto nahrávání (viz `holdToRecord` a `RecordGestureState`).
    var recordHeld by recordGesture::held
    var recordLocked by recordGesture::locked
    val recordDrag = recordGesture.drag
    LaunchedEffect(isRecording) { if (!isRecording) recordLocked = false }
    val recordHolding = isRecording && recordHeld && !recordLocked
    // Hlas ve vstupní kartě: nahrává se, nebo je nahrávka zastavená k poslechu. Karta i tlačítko
    // se v obou stavech chovají stejně; liší se jen pruh a to, že náhled nedýchá (nic neběží).
    val voiceActive = isRecording || voicePreview != null || videoNotePreview != null
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    /*
     * Úsek, ke kterému se právě zadává adresa odkazu. Zachycený při ťuknutí na „Odkaz": dialog
     * vezme poli fokus a výběr by se na něj nedal spolehnout.
     */
    // Blok kódu, kterému se nastavuje jazyk — úsek z doby ťuknutí, jako u odkazu.
    var languageTarget by rememberSaveable { mutableStateOf<List<Int>?>(null) }
    languageTarget?.let { (start, end) ->
        CodeLanguageDialog(
            current = composer.formats.codeBlockAt(start, end)?.language,
            onConfirm = { language ->
                composer.setCodeLanguage(start, end, language)
                onDraftChanged(composer.composed)
                languageTarget = null
            },
            onDismiss = { languageTarget = null },
        )
    }
    var linkTarget by rememberSaveable { mutableStateOf<List<Int>?>(null) }
    linkTarget?.let { (start, end) ->
        LinkDialog(
            current = composer.formats.linkAt(start, end),
            onConfirm = { url ->
                composer.link(start, end, url)
                onDraftChanged(composer.composed)
                linkTarget = null
            },
            onDismiss = { linkTarget = null },
        )
    }
    val formatting = FormattingTransformation(
        formats = composer.formats,
        contentColor = MaterialTheme.colorScheme.onSurface,
        accentColor = MaterialTheme.colorScheme.primary,
    )

    /*
     * Vložení textu zvenku. Koncept z minula jde JEN do prázdného pole — než ho TDLib vydá, mohl
     * uživatel začít psát. Úprava zprávy pole přepisuje vždycky. Kurzor na konec, ať se píše dál.
     */
    LaunchedEffect(inputFill) {
        val fill = inputFill ?: return@LaunchedEffect
        if (!fill.onlyIfEmpty || draft.text.isEmpty()) composer.fill(fill.text, fill.formats)
        onInputFilled(fill)
    }

    // Úprava zprávy přílohy schová — odeslání by je stejně neposlalo.
    val withAttachments = attachments.isNotEmpty() && editing == null
    val keyboard = LocalSoftwareKeyboardController.current
    val afterKeyboardHidden = rememberAfterKeyboardHidden()
    // Nahoře, ne u pole: podle fokusu se ukazuje i návrh vložení ze schránky nad polem.
    val fieldInteraction = remember { MutableInteractionSource() }
    val fieldFocused by fieldInteraction.collectIsFocusedAsState()
    // Píše se: pole má fokus a klávesnice je vidět. Fokus sám nestačí — po schování klávesnice
    // ho pole drží dál. Výška klávesnice funguje i na iOS (`isImeVisible` je jen na Androidu).
    val ime = WindowInsets.ime
    val keyboardShown by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
    val typing = fieldFocused && keyboardShown && !voiceActive
    // Lišta formátování bez výběru (ikona „Aa"). Se schováním klávesnice se zavře — bez ní
    // není co formátovat a ikona může být mezitím zpátky sponkou.
    var formattingOpen by rememberSaveable { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    var fieldHeight by remember { mutableStateOf(0.dp) }
    LaunchedEffect(typing) { if (!typing) formattingOpen = false }

    var linkOptionsOpen by rememberSaveable { mutableStateOf(false) }
    // Náhled, který zmizel (smazaný odkaz, odeslaná zpráva), si sheet nenechá otevřený.
    if (linkOptionsOpen && linkPreview != null) {
        LinkPreviewOptionsSheet(
            preview = linkPreview,
            links = linkPreviewLinks,
            options = linkPreviewOptions,
            onChange = onLinkPreviewOptions,
            onDismiss = { linkOptionsOpen = false },
        )
    }

    /*
     * Pruh má barvu POZADÍ, jen průsvitnou.
     *
     * Zprávy pod něj zajíždějí (padding `bottomBar` jde do `contentPadding` seznamu, viz výše)
     * a bez pruhu by se pod kartou
     * míchaly s textem. Plná barva by zase udělala ostrý předěl přes celou obrazovku. Průsvit
     * dělá závoj: je vidět, že tam obsah je, ale nepřebíjí psaní.
     */
    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.then(glass),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                /*
                 * Spodek (lišta, klávesnice) i boky. Naležato leží na boku výřez kamery nebo
                 * navigační lišta se třemi tlačítky a tlačítko odeslání by jinak zajelo pod ně.
                 * Levý inset vedle railu už zkonzumoval shell.
                 */
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            /*
             * JEDNA karta na citaci i na psaní.
             *
             * Dřív to byly dva samostatné kusy nad sebou a odpověď působila jako cizí prvek,
             * co se vsunul mezi konverzaci a vstup. Takhle je vidět, že citace patří k tomu,
             * co se právě píše.
             *
             * Pole uvnitř je průhledné — kontejner nese karta, ne ono; dvě zaoblené plochy
             * v sobě by vypadaly jako chyba.
             */
            /*
             * Při nahrávání se karta ROZVLNÍ a přebarví. Pruh nahrávání vypadá jinak než pole,
             * ale na místě zůstává — bez změny karty se dal přehlédnout otevřený mikrofon.
             * Vlnka je v aplikaci „hlas": stejně (jen mělčeji a bez pohybu) jsou zvlněné
             * hlasovky v konverzaci. Stojí, neobíhá — živost nese dýchající tlačítko vedle.
             */
            val recordingWave by animateFloatAsState(
                targetValue = if (voiceActive) 1f else 0f,
                animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                label = "composer-recording-wave",
            )
            val cardColor by animateColorAsState(
                targetValue = if (voiceActive) {
                    // Hlas je v konverzaci terciární (viz tlačítko vedle), takže i karta při nahrávání.
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    composerCardColor()
                },
                animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
                label = "composer-recording-color",
            )
            val cardShape = if (recordingWave > 0f) {
                WavyShape(ComposerShape, progress = recordingWave, phase = 0f)
            } else {
                ComposerShape
            }
            Surface(
                shape = cardShape,
                color = cardColor,
                modifier = Modifier.weight(1f).occludesSharedElements(cardShape),
            ) {
              Column {
                // Úprava má přednost před citací: odpověď se při ní neruší, jen se na chvíli schová.
                if (editing != null) {
                    EditingHeader(message = editing, onCancel = onCancelEdit)
                } else {
                  AnimatedVisibility(
                      visible = attachmentsPreparing,
                      enter = fadeIn() + expandVertically(),
                      exit = fadeOut() + shrinkVertically(),
                  ) {
                      AttachmentsPreparing()
                  }
                  StagedAttachmentsStrip(
                      attachments = attachments,
                      onRemove = onRemoveAttachment,
                      onOpen = onOpenAttachment,
                      onSetMode = onSetAttachmentMode,
                      selfDestructAllowed = selfDestructAllowed,
                      previewAttachmentIndex = previewAttachmentIndex,
                  )
                  replyingTo?.let { target ->
                    // Citace je horní pruh karty, barevně oddělený — stejně jako na kartě zprávy,
                    // ne subkartička v kartě.
                    ComposerHeader {
                        QuotedMessage(
                            message = target,
                            partnerName = partnerName,
                            accent = composerHeaderAccent(),
                            contained = false,
                            quoteText = replyQuote,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = onCancelReply, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                painterResource(Res.drawable.close_24dp),
                                contentDescription = stringResource(Res.string.conversation_cancel_reply),
                            )
                        }
                    }
                  }
                }
                  /*
                   * Náhled odkazu je poslední pruh nad polem, nejblíž textu, ke kterému patří.
                   * Mizí a objevuje se se svým místem — naskočí po pauze v psaní a skok by pole
                   * pod prstem posunul. Patří i k úpravě textu: upravená zpráva odejde s ním.
                   */
                  AnimatedVisibility(
                      visible = linkPreview != null,
                      enter = fadeIn() + expandVertically(),
                      exit = fadeOut() + shrinkVertically(),
                  ) {
                      // Při odjezdu je náhled už `null`; dobíhá se s posledním.
                      var shown by remember { mutableStateOf(linkPreview) }
                      if (linkPreview != null) shown = linkPreview
                      shown?.let { preview ->
                          LinkPreviewHeader(
                              preview = preview,
                              onOpenOptions = { linkOptionsOpen = true },
                              onDismiss = { onLinkPreviewOptions(linkPreviewOptions.copy(disabled = true)) },
                          )
                      }
                  }
                if (editing == null) {
                  /*
                   * GIF nebo obrázek ve schránce: na iOS nabídne vložení systémovým tlačítkem,
                   * které se na povolení neptá (viz `ClipboardPasteSuggestion`). Jen s fokusem
                   * v poli — návrh patří k psaní, jako návrhy klávesnice.
                   */
                  ClipboardPasteSuggestion(
                      active = fieldFocused && !voiceActive,
                      acceptAnimations = content.stickers,
                      acceptImages = content.photos,
                      onContent = onKeyboardContent,
                      onError = onKeyboardContentFailed,
                  ) { kind, pasteButton ->
                      ComposerHeader {
                          Text(
                              text = stringResource(
                                  when (kind) {
                                      ClipboardMediaKind.Animation -> Res.string.clipboard_has_gif
                                      ClipboardMediaKind.Image -> Res.string.clipboard_has_image
                                  },
                              ),
                              style = MaterialTheme.typography.labelLarge,
                              modifier = Modifier.weight(1f).padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                          )
                          pasteButton()
                      }
                  }
                }

                // Při nahrávání místo pole pruh s časem; citace nad ním zůstává, hlasovka na ni odpoví.
                if (videoNotePreview != null && videoNotePlayback != null) {
                    VideoNotePreviewBar(
                        preview = videoNotePreview,
                        playback = videoNotePlayback,
                        onDiscard = onDiscardVideoNotePreview,
                        // Jako u hlasovky: ťuknutím, takže nahrávání je rovnou zamčené.
                        onContinue = {
                            recordLocked = true
                            onContinueVideoNotePreview()
                        },
                    )
                } else if (voicePreview != null) {
                    VoicePreviewBar(
                        preview = voicePreview,
                        playback = voicePreviewPlayback,
                        onToggle = onToggleVoicePreview,
                        onSeek = onSeekVoicePreview,
                        onDiscard = onDiscardVoicePreview,
                        // Spouští se ťuknutím, ne podržením: nahrávání je rovnou zamčené.
                        onContinue = {
                            recordLocked = true
                            onContinueVoicePreview()
                        },
                    )
                } else if (isRecording) {
                    RecordingBar(
                        recording = recording,
                        onCancel = onCancelRecording,
                        holding = recordHolding,
                        cancelProgress = { cancelProgress(recordDrag.value.x, with(density) { RECORD_CANCEL_DISTANCE.toPx() }) },
                        onStop = onStopRecording,
                        video = recordingKind == RecordMode.VideoNote,
                    )
                } else Row(verticalAlignment = Alignment.Bottom) {
            /*
             * Ikony stojí VEDLE pole, ne v jeho `leadingIcon` / `trailingIcon`. `TextField`
             * sloty centruje na výšku pole, takže s víceřádkovou zprávou ikony ujížděly doprostřed;
             * tady se drží dole u posledního řádku, stejně jako odesílací tlačítko vedle karty.
             * Odsazení 4 dp je (56 − 48) / 2: tlačítko pak sedí na střed jednořádkového pole.
             */
            // Přílohy jen při psaní — k úpravě textové zprávy příloha nepatří.
            // Poloha jde všude, kde se smí psát, takže sponka zůstává i ve skupině bez příloh.
            // Příkazy bota: v chatu s botem je to hlavní ovládání, jako „Menu" u oficiálních klientů.
            // Bot s mini aplikací v nabídce ji má na tom místě místo příkazů (ty dál napovídá „/").
            val botButton = editing == null && (menuApp != null || onToggleCommands != null)
            if (editing == null && menuApp != null) {
                BotMenuAppButton(
                    text = menuApp.text,
                    compact = draft.text.isNotBlank() || attachments.isNotEmpty(),
                    iconColor = inputIconColor(),
                    onClick = onOpenMenuApp,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            } else if (editing == null && onToggleCommands != null) {
                IconButton(
                    onClick = onToggleCommands,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = inputIconColor()),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        painterResource(Res.drawable.smart_toy_24dp),
                        contentDescription = stringResource(Res.string.conversation_bot_commands),
                    )
                }
            }
            // Sponka, nebo „Aa" — viz ComposerStartSlot. Smajlík má místo vpravo vždycky.
            val attachColor = inputIconColor()
            ComposerStartSlot(
                attach = if (editing == null && (content.anyAttachment || onSendLocation != null)) {
                    {
                        AttachButton(
                            onPickMedia = onPickMedia,
                            onPickDocument = onPickDocument,
                            // Skupina může povolit fotky a zakázat soubory; nabídne se jen dovolené.
                            canPickMedia = content.photos || content.videos,
                            canPickDocument = content.documents,
                            onCreatePoll = onCreatePoll,
                            onSendLocation = onSendLocation,
                            onShareContact = onShareContact,
                            contentColor = attachColor,
                        )
                    }
                } else {
                    null
                },
                formatAuto = typing && draft.text.isNotEmpty(),
                typing = typing,
                formattingShown = (typing && formattingOpen) || !draft.selection.collapsed,
                onToggleFormatting = { formattingOpen = !formattingOpen },
                onFormatWithoutKeyboard = {
                    // Lišta se ukáže, až klávesnice vyjede (kreslí se jen při psaní).
                    formattingOpen = true
                    fieldFocus.requestFocus()
                    keyboard?.show()
                },
                fieldHeight = fieldHeight,
                contentColor = attachColor,
                // Za tlačítkem příkazů bez odsazení: glyfy mají kolem sebe vzduch samy.
                modifier = Modifier.padding(start = if (botButton) 0.dp else 4.dp, bottom = 4.dp),
            )
            // Výška pole říká levému místu, jestli se ikony vejdou pod sebe.
            Box(Modifier.weight(1f).onSizeChanged { fieldHeight = with(density) { it.height.toDp() } }) {
            /*
             * `BasicTextField` s dekorací, ne `TextField`: ten má vnitřní odsazení 16 dp napevno
             * a vedle sponky (48dp tlačítko, glyf má kolem sebe dalších 12 dp) z toho byla
             * mezera skoro 30 dp, než začal text. U ikony stačí 4 dp — glyf má svůj vzduch.
             * Vlevo je ikona vždycky — sponka, nebo „Aa" (i při úpravě zprávy).
             */
            val fieldColors = TextFieldDefaults.colors(
                // Kontejner i podtržení nese karta kolem; pole samo nekreslí nic.
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            )
            // Klávesnice nabízí GIFy a samolepky jen poli, které řekne, že je přijme.
            // Při úpravě zprávy ne — k upravovanému textu příloha nepatří, stejně jako sponka.
            KeyboardContentReceiver(
                acceptAnimations = editing == null && content.stickers,
                acceptImages = editing == null && content.photos,
                acceptFiles = editing == null && content.documents,
                onContent = onKeyboardContent,
                onError = onKeyboardContentFailed,
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = {
                        val changed = it.text != draft.text
                        draft = it
                        // Pohyb kurzoru nebo výběr taky volá `onValueChange`; psaní to není.
                        if (changed) onDraftChanged(composer.composed)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = TextFieldDefaults.MinHeight)
                        .focusRequester(fieldFocus)
                        /*
                         * Enter z fyzické klávesnice (Fold, tablet) odešle, Shift+Enter udělá nový
                         * řádek — jako na počítači. Enter ze softwarové klávesnice dál zalomí řádek,
                         * odeslání je tam tlačítko. Prázdnou zprávu Enter nepošle, ale ani nezalomí:
                         * prázdný řádek navíc by nikdo nechtěl. Podmínky jsou tytéž jako u tlačítka.
                         */
                        .onPreviewKeyEvent { event ->
                            val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                            if (!enter || event.isShiftPressed || !event.isFromHardwareKeyboard) {
                                return@onPreviewKeyEvent false
                            }
                            val sendable = !voiceActive && !attachmentsPreparing &&
                                (draft.text.isNotBlank() || withAttachments || editing?.composerEdit() == ComposerEdit.Caption)
                            if (event.type == KeyEventType.KeyDown && sendable) {
                                onSend(composer.composed, SendOptions())
                                draft = TextFieldValue()
                            }
                            true
                        },
                    visualTransformation = formatting,
                    textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 6,
                    interactionSource = fieldInteraction,
                    keyboardOptions = KeyboardOptions(
                        // Zpráva jsou věty, ne jedno slovo: klávesnice má po tečce nabídnout velké
                        // písmeno sama. Výchozí `None` nutí uživatele přepínat shift ručně.
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            onSend(composer.composed, SendOptions())
                            draft = TextFieldValue()
                        },
                    ),
                    decorationBox = { innerTextField ->
                        TextFieldDefaults.DecorationBox(
                            value = draft.text,
                            innerTextField = innerTextField,
                            enabled = true,
                            singleLine = false,
                            visualTransformation = VisualTransformation.None,
                            interactionSource = fieldInteraction,
                            // S přílohami je text jejich popisek.
                            // Nápověda od bota platí jen pro zprávu; popisek příloh je jiná věc.
                            placeholder = {
                                Text(
                                    when {
                                        withAttachments -> stringResource(Res.string.conversation_input_caption)
                                        placeholder != null && editing == null -> placeholder
                                        else -> stringResource(Res.string.conversation_input_message)
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            colors = fieldColors,
                            contentPadding = TextFieldDefaults.contentPaddingWithoutLabel(
                                // Vlevo je vždycky ikona (sponka, nebo „Aa").
                                start = FIELD_ICON_GAP,
                                end = FIELD_ICON_GAP,
                            ),
                            container = {},
                        )
                    },
                )
            }

            // Startovní strana letu: **už vypadá jako bublina**, ne jen jako text. Sdílí se celý
            // kontejner, takže po cestě roste i pozadí — kdyby se sdílel jen text, doletěl by
            // sám a bublina by pod ním na konci skokem naskočila.
            //
            // Kreslí se přes vyprázdněné pole, takže odeslání vypadá, jako by se napsaný text
            // proměnil v bublinu a odletěl.
            flight?.let { current ->
                Surface(
                    // MUSÍ sedět s barvou cílové bubliny, jinak zpráva po cestě změní barvu.
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    // Odesílaná zpráva je vždycky nejnovější, takže pod ní nic nenavazuje
                    // a všechna nároží jsou zaoblená. Dřív tu bylo spodní stažené, protože takové
                    // bylo i v bublině; se seskupením podle obou sousedů to platit přestalo.
                    shape = RoundedCornerShape(BUBBLE_CORNER),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .sharedFlight(current.sharedKey, visible = current.itemKey == null),
                ) {
                    Text(
                        text = current.text,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
            }
            // Naplánované zprávy: hodiny u pole, dokud nějaké čekají — do historie nepatří,
            // takže jinak by o nich nebylo vidět nic.
            if (editing == null && onOpenScheduled != null) {
                IconButton(
                    onClick = onOpenScheduled,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = inputIconColor()),
                    modifier = Modifier.padding(bottom = 4.dp),
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(
                        painterResource(Res.drawable.schedule_24dp),
                        contentDescription = stringResource(Res.string.conversation_scheduled_open),
                    )
                }
            }
            // Výběr samolepek, emoji a GIFů je tu VŽDYCKY, i s otevřenou klávesnicí — potřebuje
            // se hlavně uprostřed psaní. „Aa" si místo vyměňuje se sponkou (ComposerStartSlot).
            IconButton(
                onClick = {
                    // Sheet a klávesnice by se jinak přetahovaly o spodek obrazovky; sheet je
                    // další okno, takže otevřený hned by klávesnici utrhl skokem.
                    afterKeyboardHidden(onOpenStickers)
                },
                colors = IconButtonDefaults.iconButtonColors(contentColor = inputIconColor()),
                modifier = Modifier.padding(end = 4.dp, bottom = 4.dp),
                shapes = IconButtonDefaults.shapes(),
            ) {
                // Ikona slibuje, co se otevře: poslední použitou část výběru. Při úpravě
                // zprávy jde otevřít jen emoji — samolepka do textu upravované zprávy nepatří.
                when {
                    editing != null || !content.stickers || expressionMode == ExpressionMode.Emoji ->
                        Icon(painterResource(Res.drawable.mood_24dp), contentDescription = stringResource(Res.string.conversation_open_emoji))
                    expressionMode == ExpressionMode.Gifs ->
                        Icon(painterResource(Res.drawable.gif_box_24dp), contentDescription = stringResource(Res.string.conversation_open_gifs))
                    else ->
                        Icon(painterResource(Res.drawable.sticky_note_2_24dp), contentDescription = stringResource(Res.string.conversation_open_stickers))
                }
            }
                }
                /*
                 * Lišta formátování, dokud je v poli označený text, nebo ji otevřela ikona „Aa"
                 * (viz `FormattingBar`). POD polem, ne nad ním: systémová lišta výběru (Vyjmout,
                 * Kopírovat…) se kreslí nad výběrem a lištu nad polem by přikryla. Vyjíždí se
                 * svým místem, jako náhled odkazu.
                 */
                AnimatedVisibility(
                    visible = (typing && formattingOpen) || (fieldFocused && !voiceActive && !draft.selection.collapsed),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    FormattingBar(
                        composer = composer,
                        onChanged = { onDraftChanged(composer.composed) },
                        onLink = { draft.selection.let { linkTarget = listOf(it.min, it.max) } },
                        onCodeLanguage = { draft.selection.let { languageTarget = listOf(it.min, it.max) } },
                    )
                }
              }
            }

            /*
             * Odeslání je velikost MEDIUM (56 dp), ne výchozí small (40 dp).
             *
             * 56 dp je přesně minimální výška jednořádkového `TextField`, takže tlačítko se
             * spodním zarovnáním řádku sedí na střed pole bez jakéhokoli dopočítávaného odsazení.
             * Když se pole zalomí na víc řádků nebo nad ním visí citace, zůstane u posledního
             * řádku — tam, kde se píše. Small tlačítko sedělo u spodní hrany o 8 dp níž než střed.
             *
             * Je to hlavní akce obrazovky, takže větší plocha dává smysl i sama o sobě. Large
             * (96 dp) by už bylo vyšší než pole a přebíjelo by psaní.
             *
             * Tvar nese stav: mikrofon je „sušenka", odeslání kruh (`MorphShape`, `ShapeMorphs.Rest`);
             * kruh se při stisku mění podle M3 Expressive na zaoblený čtverec.
             */
            /*
             * Prázdné pole bez příloh = mikrofon, nebo kamera (jako v Telegramu): tlačítko, které
             * by jinak bylo neaktivní, dostane smysl. Podržení nahrává, ťuknutí přepíná hlasovku
             * a kolečko (`holdToRecord`). Při úpravě zprávy není — nahrávka úprava není.
             */
            // Bez práva na hlasovky i kolečka tlačítko zůstane odesláním, jen neaktivním.
            val showMic = !voiceActive && editing == null && draft.text.isBlank() && !withAttachments &&
                !attachmentsPreparing && recordMode != null

            /*
             * Prázdný POPISEK je platná úprava — tím se popisek odebere —, takže tlačítko musí jít
             * zmáčknout i s prázdným polem. U textu zprávy ne: prázdnou zprávu server odmítne.
             */
            val removableCaption = editing?.composerEdit() == ComposerEdit.Caption
            val kind = when {
                editing != null -> SendButtonKind.Save
                voiceActive -> SendButtonKind.SendVoice
                showMic && recordMode == RecordMode.VideoNote -> SendButtonKind.Camera
                showMic -> SendButtonKind.Mic
                else -> SendButtonKind.Send
            }

            var sendMenuOpen by remember { mutableStateOf(false) }
            // Nabídka otevřená nad hlasovkou: volby jdou na nahrávku, ne na text pole.
            var voiceMenu by remember { mutableStateOf(false) }
            var voiceChosen by remember { mutableStateOf(false) }
            // Položka nabídky volá NEJDŘÍV zavření a teprve pak svoji akci, takže o obnovení
            // nahrávání se rozhoduje až po zavření: bez volby se nahrává dál, s volbou ne.
            LaunchedEffect(sendMenuOpen) {
                if (!sendMenuOpen && voiceMenu) {
                    if (!voiceChosen) onVoiceMenu(false)
                    voiceMenu = false
                    voiceChosen = false
                }
            }
            // Efekt jde vybrat jen k textu — výběr efektu posílá obsah pole.
            val voiceSendMenu = sendMenu.copy(effects = false)
            val recordActionLabel = stringResource(
                if (recordMode == RecordMode.VideoNote) Res.string.conversation_record_video_note else Res.string.conversation_record_voice,
            )
            // Ťuknutí na mikrofon či kameru nahrávání nespouští, jen přepíná — a řekne jak dál.
            val recordHint = rememberTooltipState(isPersistent = false)
            val hintScope = rememberCoroutineScope()
            // „Přepsat…" nese nabídka odeslání, ale do `sendMenuOffer` nepatří: rozhoduje o něm
            // text pole, a ten `ConversationScreen` číst nemá (skládal by se při každém úhozu).
            val rewriteOffered = rewrite.offered && editing == null && rewritable(draft.text)
            val sendOptionsOffered = (kind == SendButtonKind.Send && (sendMenu.any || rewriteOffered) && !attachmentsPreparing) ||
                (kind == SendButtonKind.SendVoice && voiceSendMenu.any)
            ButtonGestureHint(
                hints = gestureHints,
                sendOptions = kind == SendButtonKind.Send && sendOptionsOffered,
                recording = showMic,
                recordMode = recordMode ?: RecordMode.Voice,
                effects = sendMenu.effects,
            ) {
            Box(Modifier.onGloballyPositioned { recordGesture.buttonCenterX = it.boundsInWindow().center.x }) {
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
                    tooltip = {
                        PlainTooltip {
                            Text(
                                stringResource(
                                    if (recordMode == RecordMode.VideoNote) {
                                        Res.string.conversation_record_hint_video_note
                                    } else {
                                        Res.string.conversation_record_hint_voice
                                    },
                                ),
                            )
                        }
                    },
                    state = recordHint,
                    // Podržení tu nahrává; nápověda na dlouhý stisk by se s ním prala.
                    enableUserInput = false,
                ) {
                /*
                 * Morf, dýchání a barvy se čtou AŽ TADY, v obsahu `TooltipBox` (vlastní rozsah
                 * rekompozice). Čtené v kartě pole by s každým snímkem animace přeskládaly celou
                 * kartu i s polem — při nahrávání po celou dobu nahrávky (změřeno Perfettem).
                 */
                /*
                 * Tvar nese stav: mikrofon (klidový stav prázdného pole) je „sušenka" z M3 Expressive,
                 * odeslání a uložení úpravy kruh. Změna se animuje morfem mezi nimi a ikona se vymění
                 * zvětšením. Tlačítko se mění samo od sebe (začne se psát, pole se vyprázdní) a skoková
                 * výměna ikony se dala přehlédnout — a ťuknout pak na mikrofon místo odeslání.
                 * Stav se tak pozná i bez ikony a barvy, třeba koutkem oka nebo na slunci.
                 */
                // Pružina přestřelí za oba konce; mimo 0..1 by se tvar zdeformoval.
                val morphProgress = animateMorph(
                    kind == SendButtonKind.Mic || kind == SendButtonKind.Camera,
                    "send-button-shape",
                ).coerceIn(0f, 1f)

                /*
                 * Při nahrávání tlačítko DÝCHÁ: pomalý morf kruh ↔ SoftBurst. Tečka v pruhu nahrávání
                 * už nebliká — dva blikající prvky vedle sebe by byly šum — a živost nese tlačítko,
                 * kterým se nahrávání i odesílá.
                 */
                val breathing = if (isRecording) {
                    rememberInfiniteTransition(label = "recording-breath").animateFloat(
                        initialValue = 0f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(tween(RECORDING_BREATH_MS), RepeatMode.Reverse),
                        label = "recording-breath-progress",
                    ).value
                } else {
                    0f
                }

                /*
                 * Barva říká, ČEHO se akce týká: text jde `primary` (jako odchozí bublina), hlas
                 * `tertiary`. Hlasovka je doplňková cesta vedle psaní, a terciární role je v M3
                 * přesně na to — tlačítko je pak stejně výrazné jako odeslání textu (obojí je plná
                 * role, ne tonální), jen se pozná, že udělá něco jiného.
                 *
                 * Dřív byl mikrofon tonální `secondaryContainer`, tedy „klidový stav prázdného pole".
                 * Jenže tím bylo tlačítko v prázdném poli to nejméně nápadné na obrazovce, přestože
                 * je to jediná akce, která se z prázdného pole dá udělat.
                 *
                 * Nahrávání a mikrofon mají barvu STEJNOU: mezi „můžu nahrát" a „nahrávám" rozhoduje
                 * tvar (sušenka ↔ dýchající SoftBurst), vlnka karty a pruh s časem. Přebarvit k tomu
                 * ještě tlačítko by byl čtvrtý signál pro jednu změnu.
                 */
                // Kolečko je taky nahrávka, takže nese tutéž terciární roli jako hlas.
                val voiceButton = kind == SendButtonKind.Mic || kind == SendButtonKind.Camera ||
                    kind == SendButtonKind.SendVoice
                val colorSpec = MaterialTheme.motionScheme.slowEffectsSpec<Color>()
                val containerColor by animateColorAsState(
                    targetValue = if (voiceButton) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    animationSpec = colorSpec,
                    label = "send-button-container",
                )
                val contentColor by animateColorAsState(
                    targetValue = if (voiceButton) {
                        MaterialTheme.colorScheme.onTertiary
                    } else {
                        MaterialTheme.colorScheme.onPrimary
                    },
                    animationSpec = colorSpec,
                    label = "send-button-content",
                )

                // Kruh (odeslání) se při stisku mění podle M3 Expressive na zaoblený čtverec.
                // Sušenka a tvary během přechodu stisk nemění: mezi obecnými tvary IconButton
                // neanimuje a skok by vypadal jako chyba.
                val buttonShapes = if (breathing > 0f) {
                    val shape = MorphShape(ShapeMorphs.Live, breathing, rotation = RECORDING_BREATH_ROTATION)
                    IconButtonShapes(shape = shape, pressedShape = shape)
                } else if (morphProgress > 0f) {
                    val shape = MorphShape(ShapeMorphs.Rest, morphProgress, rotation = ShapeMorphs.REST_ROTATION)
                    IconButtonShapes(shape = shape, pressedShape = shape)
                } else {
                    IconButtonDefaults.shapes(
                        shape = IconButtonDefaults.mediumRoundShape,
                        pressedShape = IconButtonDefaults.mediumPressedShape,
                    )
                }
                FilledIconButton(
                    onClick = {
                        when {
                            voiceActive -> onSendRecording(SendOptions())
                            showMic -> {
                                if (canSwitchRecordMode) {
                                    haptics.toggle(on = recordMode == RecordMode.Voice)
                                    onSwitchRecordMode()
                                }
                                hintScope.launch { recordHint.show() }
                            }
                            else -> {
                                onSend(composer.composed, SendOptions())
                                draft = TextFieldValue()
                            }
                        }
                    },
                    // Přílohy jdou poslat i bez popisku a popisek jde i odebrat.
                    // Náhled, který se ještě dokončuje, poslat nejde — soubor není hotový.
                    enabled = (isRecording || voicePreview?.voice != null || videoNotePreview?.note != null || showMic || draft.text.isNotBlank() || withAttachments || removableCaption) &&
                        // Odeslání teď by poslalo zprávu bez videa, které se ještě kopíruje.
                        !(kind == SendButtonKind.Send && attachmentsPreparing),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = containerColor,
                        contentColor = contentColor,
                    ),
                    shapes = buttonShapes,
                    modifier = Modifier
                        // PRVNÍ v řetězu: tlačítko během gesta změní druh i modifikátory za tímhle.
                        .holdToRecord(
                            enabled = showMic,
                            cancelDistance = RECORD_CANCEL_DISTANCE,
                            lockDistance = RECORD_LOCK_DISTANCE,
                            onHoldStart = {
                                gestureHints?.used(GestureHint.HoldToRecord)
                                recordHeld = true
                                // Zamčení z minula (akce čtečky, která nakonec nenahrávala) neplatí.
                                recordLocked = false
                                onStartRecording(true)
                            },
                            onDrag = { recordDrag.value = it },
                            onRelease = {
                                recordHeld = false
                                if (isRecording) onSendRecording(SendOptions())
                            },
                            onCancel = {
                                recordHeld = false
                                if (isRecording) onCancelRecording()
                            },
                            onLock = {
                                recordHeld = false
                                if (isRecording) {
                                    haptics.threshold()
                                    recordLocked = true
                                }
                            },
                        )
                        // Čtečka obrazovky podržet neumí: nahrávání spustí akcí, rovnou zamčené.
                        .semantics {
                            if (showMic) {
                                customActions = listOf(
                                    CustomAccessibilityAction(recordActionLabel) {
                                        recordLocked = true
                                        onStartRecording(false)
                                        true
                                    },
                                )
                            }
                        }
                        .size(IconButtonDefaults.mediumContainerSize())
                        // Klidový tvar; stisk ho na chvíli zhranatí, a to při letu fotky nehraje roli.
                        .occludesSharedElements(buttonShapes.shape)
                        // Podržení nabídne odeslat bez zvuku a naplánovat — u hotové zprávy
                        // i u nahrávané hlasovky.
                        .longPressInstead(enabled = sendOptionsOffered) {
                            gestureHints?.used(GestureHint.SendOptions)
                            keyboard?.hide()
                            voiceMenu = kind == SendButtonKind.SendVoice
                            if (voiceMenu) onVoiceMenu(true)
                            sendMenuOpen = true
                        },
                ) {
                    // Ikona jede stejnou pružinou jako tvar — rychlejší by se vyměnila dřív, než je
                    // morf vůbec vidět.
                    val iconScale = morphSpring<Float>()
                    val iconFade = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
                    AnimatedContent(
                        targetState = kind,
                        transitionSpec = {
                            (scaleIn(iconScale, initialScale = 0.4f) + fadeIn(iconFade))
                                .togetherWith(scaleOut(iconScale, targetScale = 0.4f) + fadeOut(iconFade))
                        },
                        label = "send-button-icon",
                    ) { shown ->
                        // Při úpravě fajfka: nic se neposílá, jen potvrzuje změna.
                        Icon(
                            painterResource(
                                when (shown) {
                                    SendButtonKind.Save -> Res.drawable.check_24dp
                                    SendButtonKind.Mic -> Res.drawable.mic_24dp
                                    SendButtonKind.Camera -> Res.drawable.videocam_24dp
                                    SendButtonKind.Send, SendButtonKind.SendVoice -> Res.drawable.send_24dp
                                },
                            ),
                            contentDescription = stringResource(
                                when (shown) {
                                    SendButtonKind.Save -> Res.string.conversation_save_edit
                                    SendButtonKind.Mic -> Res.string.conversation_record_voice
                                    SendButtonKind.Camera -> Res.string.conversation_record_video_note
                                    SendButtonKind.SendVoice -> if (recordingKind == RecordMode.VideoNote) {
                                        Res.string.conversation_send_video_note
                                    } else {
                                        Res.string.conversation_send_voice
                                    }
                                    SendButtonKind.Send -> Res.string.conversation_send
                                },
                            ),
                            modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                        )
                    }
                }
                }
                val offer = if (voiceMenu) voiceSendMenu else sendMenu
                ExpressiveMenu(
                    expanded = sendMenuOpen,
                    onDismiss = { sendMenuOpen = false },
                    popupPositionProvider = remember { MenuAbovePositionProvider(alignEnd = true) },
                    groups = listOf(
                        if (voiceMenu) {
                            listOfNotNull(
                                MenuAction(stringResource(Res.string.conversation_send_silent), Res.drawable.notifications_off_24dp) {
                                    voiceChosen = true
                                    sendMenuOpen = false
                                    onSendRecording(SendOptions(silent = true))
                                }.takeIf { offer.silent },
                                MenuAction(stringResource(Res.string.conversation_send_schedule), Res.drawable.schedule_send_24dp) {
                                    voiceChosen = true
                                    sendMenuOpen = false
                                    onScheduleRecording()
                                }.takeIf { offer.schedule },
                                MenuAction(stringResource(Res.string.conversation_send_when_online), Res.drawable.online_prediction_24dp) {
                                    voiceChosen = true
                                    sendMenuOpen = false
                                    onSendRecording(SendOptions(schedule = MessageSchedule.WhenOnline))
                                }.takeIf { offer.whenOnline },
                            )
                        } else listOfNotNull(
                            MenuAction(stringResource(Res.string.conversation_send_silent), Res.drawable.notifications_off_24dp) {
                                sendMenuOpen = false
                                onSend(composer.composed, SendOptions(silent = true))
                                draft = TextFieldValue()
                            }.takeIf { sendMenu.silent },
                            MenuAction(stringResource(Res.string.conversation_send_schedule), Res.drawable.schedule_send_24dp) {
                                sendMenuOpen = false
                                onSchedule()
                            }.takeIf { sendMenu.schedule },
                            MenuAction(stringResource(Res.string.conversation_send_when_online), Res.drawable.online_prediction_24dp) {
                                sendMenuOpen = false
                                onSend(composer.composed, SendOptions(schedule = MessageSchedule.WhenOnline))
                                draft = TextFieldValue()
                            }.takeIf { sendMenu.whenOnline },
                            MenuAction(stringResource(Res.string.conversation_send_effect), Res.drawable.auto_awesome_24dp) {
                                sendMenuOpen = false
                                onPickEffect()
                            }.takeIf { sendMenu.effects },
                            MenuAction(stringResource(Res.string.conversation_send_rewrite), Res.drawable.edit_note_24dp) {
                                sendMenuOpen = false
                                rewrite.open(draft.text)
                            }.takeIf { rewriteOffered },
                        ),
                    ),
                )
            }
            }
        }
    }
}

/** Tvar karty vstupního pole; při nahrávání se z něj rozvlní [WavyShape]. */
/** Avatar v hlavičce. */
private val HEADER_AVATAR = 36.dp

/** Avatar v hlavičce při výpadku spojení (viz `ConnectionIndicator`). */
private val HEADER_AVATAR_GROWN = 42.dp

/** Rezerva řádku hlavičky kolem avataru pro jeho zvětšení a půl tahu prstence. */
private val HEADER_RING_ROOM = (HEADER_AVATAR_GROWN + RING_STROKE - HEADER_AVATAR) / 2

/** Tečka dostupnosti na 36dp avataru — v seznamu chatů je 14 dp na 52dp. */
private val HEADER_ONLINE_DOT = 11.dp

private val ComposerShape = RoundedCornerShape(24.dp)

/** Odsazení textu od ikony vedle pole — glyf má v 48dp tlačítku kolem sebe svých 12 dp. */
private val FIELD_ICON_GAP = 4.dp

/** Půlperioda dýchání tlačítka při nahrávání. */
private const val RECORDING_BREATH_MS = 900

/** O kolik se tlačítko při nádechu pootočí. */
private const val RECORDING_BREATH_ROTATION = 30f

/**
 * Barva ikon v kartě vstupního pole (přílohy, emoji/samolepky).
 *
 * Výchozí `onSurfaceVariant` byla na `surfaceContainerHigh` karty zašedlá a špatně čitelná.
 * Akcent je to, čím M3 odlišuje ovládání od textu (placeholder zůstává šedý) — jen se nebere
 * `primary` natvrdo, ale ten z akcentních rolí, který je na kartě nejlíp vidět
 * ([strongestAccentOn]).
 *
 * Samotná `primary` totiž byla na kartě NEJSLABŠÍ prvek: v Material You z tapety vyšla olivová
 * s kontrastem 5,3 : 1, zatímco placeholder vedle ní měl 7,6 a ostatní ikony v aplikaci přes 10.
 * Obrysová ikona s tahem 2 dp se pak čte jako rozmazaná — viz [strongestAccentOn]. Srovnání
 * s Google Zprávami sedí: tamní ikony u pole jsou taky akcent, ale o poznání tmavší.
 *
 * Plocha je cílová barva karty, ne ta právě animovaná: při nahrávání karta přebarvuje do
 * `primaryContainer`, jenže tehdy je místo pole pruh nahrávání a ikony se nekreslí vůbec.
 */
@Composable
private fun inputIconColor() = strongestAccentOn(
    background = composerCardColor(),
    MaterialTheme.colorScheme.primary,
    MaterialTheme.colorScheme.onPrimaryContainer,
)

/**
 * Klidová barva karty vstupního pole: `surfaceContainerHigh` přitónovaná do `primaryContainer`.
 *
 * V neutrálních surface rolích byla karta jediná velká šedá plocha na obrazovce a od pozadí
 * konverzace ji dělila jen světlost. S přitónováním nese barvu palety (a na Androidu tedy
 * i tapety), ale zůstane pozadím — plný `primaryContainer` by ji posunul na světlost odchozí
 * bubliny a pole by vypadalo jako rozepsaná zpráva. Viz [tintedSurface].
 */
@Composable
private fun composerCardColor(): Color = tintedSurface(
    surface = MaterialTheme.colorScheme.surfaceContainerHigh,
    tint = MaterialTheme.colorScheme.primaryContainer,
)

/**
 * Co tlačítko u vstupního pole právě dělá. Změna mezi nimi se animuje.
 *
 * [SendVoice] je vlastní hodnota, i když kreslí tutéž ikonu jako [Send]: odeslání hlasovky patří
 * barevně k [Mic] (hlas = terciární), zatímco odeslání textu k `primary`. S jediným „Send" by se
 * tlačítko při spuštění nahrávání přebarvilo z terciární na primary a zase zpátky.
 */
private enum class SendButtonKind { Mic, Camera, SendVoice, Send, Save }

/** Co se plánuje: rozepsaná zpráva z pole, nebo už naplánovaná, které se mění čas. */
private sealed interface ScheduleTarget {
    data object Draft : ScheduleTarget

    /** Nahrávaná hlasovka, po dobu výběru času pozastavená. */
    data object Voice : ScheduleTarget
    data class Existing(val messageId: Long) : ScheduleTarget

    /** Připomínka zprávy (`RemindSheet` → „Vybrat datum a čas"). */
    data class Reminder(val messageId: Long) : ScheduleTarget

    /** Samolepka z výběru, naplánovaná z nabídky u podržené samolepky. */
    data class Sticker(val sticker: StickerItem) : ScheduleTarget

    /** GIF z výběru, naplánovaný z nabídky u podrženého GIFu. */
    data class Gif(val gif: GifItem) : ScheduleTarget
}

/** Výchozí čas ve výběru hodiny při plánování: za hodinu od teď. */
private const val SCHEDULE_DEFAULT_AHEAD_SECONDS = 60L * 60

/**
 * Dlouhý stisk místo ťuknutí. `IconButton` `onLongClick` nemá, takže se gesto pozoruje
 * v `Initial` (dřív než tlačítko): po `longPressTimeout` s prstem na místě zavolá
 * [onLongPress] a **zbytek gesta spotřebuje** — puštění prstu pak tlačítko neodešle.
 * Krátké ťuknutí projde beze změny.
 */
internal fun Modifier.longPressInstead(enabled: Boolean, onLongPress: () -> Unit): Modifier = composed {
    val haptics = LocalHapticFeedback.current
    val action by rememberUpdatedState(onLongPress)
    if (!enabled) {
        this
    } else {
        pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                // `null` = vypršel čas a prst je pořád dole na místě.
                val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Initial)
                            .changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                    }
                }
                if (released != null) return@awaitEachGesture
                haptics.longPress()
                action()
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                    if (event.changes.none { it.pressed }) break
                }
            }
        }
    }
}

/**
 * Horní pruh karty nad vstupním polem (citace, úprava): tónovaná plocha přes celou šířku karty,
 * stejně jako citace na kartě zprávy. Zaoblení nahoře nese karta, pruh je rovný.
 */
/** Řádek nad polem, dokud se vybrané přílohy kopírují do cache. */
@Composable
private fun AttachmentsPreparing() {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LoadingIndicator(Modifier.size(32.dp))
        Text(
            text = stringResource(Res.string.share_preparing),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ComposerHeader(content: @Composable RowScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * Akcent uvnitř [ComposerHeader] — `tertiary`, dokud je na jeho ploše vidět.
 *
 * `tertiary` na `tertiaryContainer` je zkratka, kterou M3 negarantuje: v paletě se zvýšeným
 * kontrastem se v tmavém režimu obě potkají na téže světlosti a popisek zmizí. Tatáž past jako
 * u citace na kartě zprávy — viz [accentOn].
 */
@Composable
private fun composerHeaderAccent(): Color = accentOn(
    background = MaterialTheme.colorScheme.tertiaryContainer,
    accent = MaterialTheme.colorScheme.tertiary,
    fallback = MaterialTheme.colorScheme.onTertiaryContainer,
)

/**
 * Náhled odkazu v rozepsané zprávě: web, titulek a malý obrázek, jak ho připojí server.
 *
 * Tentýž pruh jako citace a úprava ([ComposerHeader]) — říká, co odejde spolu s textem. Ťuknutí
 * (i ikona voleb) otevře [LinkPreviewOptionsSheet], ✕ náhled vypne. Obrázek je vždycky malý
 * čtvereček: jde o to poznat, JAKÝ náhled to je; velikost v odeslané zprávě ukazuje sheet.
 */
@Composable
private fun LinkPreviewHeader(
    preview: LinkPreview,
    onOpenOptions: () -> Unit,
    onDismiss: () -> Unit,
) = ComposerHeader {
    val accent = composerHeaderAccent()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onOpenOptions)
            .padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(Res.drawable.link_24dp),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(end = 4.dp).size(16.dp),
                )
                Text(
                    text = preview.siteName ?: preview.title ?: preview.url,
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Druhý řádek jen tehdy, když řekne něco jiného než první.
            (preview.title?.takeIf { preview.siteName != null } ?: preview.description)?.let { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        preview.photo?.let { photo ->
            RemoteImage(
                fileId = photo.fileId,
                thumbnailFileId = photo.thumbnailFileId,
                placeholder = photo.minithumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.padding(start = 8.dp).size(LINK_HEADER_THUMBNAIL).clip(RoundedCornerShape(8.dp)),
            )
        }
    }
    IconButton(onClick = onOpenOptions, shapes = IconButtonDefaults.shapes()) {
        Icon(
            painterResource(Res.drawable.tune_24dp),
            contentDescription = stringResource(Res.string.link_preview_options),
        )
    }
    IconButton(onClick = onDismiss, shapes = IconButtonDefaults.shapes()) {
        Icon(
            painterResource(Res.drawable.close_24dp),
            contentDescription = stringResource(Res.string.link_preview_remove),
        )
    }
}

/** Obrázek v pruhu náhledu odkazu — vejde se do výšky dvou řádků textu. */
private val LINK_HEADER_THUMBNAIL = 36.dp

/**
 * Hlavička karty při úpravě zprávy.
 *
 * Vypadá jako citace (ikona, popisek, původní text), protože plní stejnou roli: říká, k čemu
 * se vztahuje to, co se píše. Původní text zůstává vidět, i když ho uživatel v poli přepíše.
 *
 * **Popisek přílohy to musí říct**: fotka v konverzaci zůstane, ať se v poli píše cokoli, takže
 * bez toho by nebylo poznat, že se upravuje popisek, a ne zpráva. U přílohy bez popisku se
 * původní text nekreslí vůbec — prázdný řádek by vypadal jako chyba vykreslení.
 */
@Composable
private fun EditingHeader(message: ChatMessage, onCancel: () -> Unit) = ComposerHeader {
    val accent = composerHeaderAccent()
    val caption = message.composerEdit() == ComposerEdit.Caption
    Column(Modifier.weight(1f).padding(start = 14.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painterResource(Res.drawable.edit_24dp),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.padding(end = 4.dp).size(16.dp),
            )
            Text(
                text = stringResource(
                    if (caption) Res.string.conversation_editing_caption else Res.string.conversation_editing,
                ),
                style = MaterialTheme.typography.labelLarge,
                color = accent,
            )
        }
        if (message.text.isNotBlank()) {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    IconButton(onClick = onCancel, shapes = IconButtonDefaults.shapes()) {
        Icon(painterResource(Res.drawable.close_24dp), contentDescription = stringResource(Res.string.conversation_cancel_edit))
    }
}

/**
 * Odroluje tak, aby položka seděla u horní hrany, nebo na středu.
 *
 * `scrollToItem` v obráceném seznamu posadí položku ke SPODNÍ hraně, protože tam je jeho začátek.
 * U skoku na nepřečtené je to přesně naopak, než je potřeba: první nepřečtená má být nahoře
 * a nepřečtené pod ní. U citace zase patří na střed, aby bylo vidět, na co navazuje. Dorovnání se
 * počítá až ze **změřeného** layoutu, protože výška bubliny se zprávu od zprávy liší.
 *
 * Počítá se s **odsazením obsahu**. Dole je pod ním vstupní pole, takže viditelná plocha začíná
 * nad ním; dřív se horní hrana brala z celé výšky viewportu a položka zajela o výšku pole pod
 * horní okraj.
 */
private suspend fun LazyListState.scrollToAnchor(index: Int, anchor: ScrollAnchor) {
    if (anchor == ScrollAnchor.Bottom) {
        scrollToItem(0)
        return
    }
    scrollToItem(index)

    val info = layoutInfo
    val item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    // Offset roste od začátku seznamu, tedy v obráceném rozvržení odspodu nahoru. Nula je horní
    // hrana spodního odsazení, `visibleHeight` spodní hrana horního.
    val visibleHeight = info.viewportEndOffset - info.afterContentPadding
    val desired = when (anchor) {
        ScrollAnchor.Top -> visibleHeight - item.size
        ScrollAnchor.Center -> (visibleHeight - item.size) / 2
        ScrollAnchor.Bottom -> 0
    }.coerceAtLeast(0)
    scrollBy((item.offset - desired).toFloat())
}

/**
 * Jako [scrollToAnchor], ale seznam k položce DOJEDE — u skoku na citaci a mezi výsledky
 * hledání je jinak vidět jen okamžité přeskočení a ztratí se, kterým směrem a jak daleko zpráva je.
 *
 * Položka mimo obrazovku nemá změřenou polohu, takže se napřed dojede na odhad z průměrné výšky
 * viditelných položek a teprve pak se poloha doladí podle skutečného layoutu. Daleko
 * ([MAX_ANIMATED_JUMP_ITEMS]) se nedojíždí: rolování přes desítky zpráv by trvalo a nic by
 * neukázalo, a navíc by skládalo všechno mezi.
 */
/** O kolik položek je [index] mimo viditelnou část seznamu; 0 = je vidět. */
private fun LazyListState.itemsAwayFrom(index: Int): Int {
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return Int.MAX_VALUE
    return when {
        index < visible.first().index -> visible.first().index - index
        index > visible.last().index -> index - visible.last().index
        else -> 0
    }
}

/** Jak daleko seznam při přechodu odjede, než zmizí. */
private val JUMP_LEAVE_DISTANCE = 96.dp

/** Z jaké vzdálenosti k nalezené zprávě sjedou sousedé (nejbližší; vzdálenější z dál). */
private val JUMP_GATHER_DISTANCE = 56.dp

private suspend fun LazyListState.animateToAnchor(
    index: Int,
    anchor: ScrollAnchor,
    spec: AnimationSpec<Float>,
    /** Index cíle v AKTUÁLNÍM seznamu. Během jízdy se mohou dotáhnout stránky a index se posune. */
    currentIndex: () -> Int,
) {
    val info = layoutInfo
    val visible = info.visibleItemsInfo
    if (visible.isEmpty()) return scrollToAnchor(index, anchor)
    val first = visible.first()
    val last = visible.last()
    val distance = when {
        index < first.index -> first.index - index
        index > last.index -> index - last.index
        else -> 0
    }
    if (distance > MAX_ANIMATED_JUMP_ITEMS) return scrollToAnchor(index, anchor)

    val visibleHeight = info.viewportEndOffset - info.afterContentPadding
    fun desired(size: Int) = when (anchor) {
        ScrollAnchor.Top -> visibleHeight - size
        ScrollAnchor.Center -> (visibleHeight - size) / 2
        ScrollAnchor.Bottom -> 0
    }.coerceAtLeast(0)

    val item = visible.firstOrNull { it.index == index }
    if (item == null) {
        // Odhad polohy: průměrná položka krát počet položek k cíli. Offset roste od spodní hrany
        // nahoru (obrácený seznam), vyšší index je výš.
        val average = visible.sumOf { it.size } / visible.size
        val estimatedOffset = if (index > last.index) {
            last.offset + last.size + (distance - 1) * average
        } else {
            first.offset - distance * average
        }
        animateScrollBy((estimatedOffset - desired(average)).toFloat(), spec)
    }
    // Doladění podle skutečného layoutu; odhad se mohl minout, nebo cíl vůbec není vidět.
    val now = currentIndex().takeIf { it >= 0 } ?: return
    val landed = layoutInfo.visibleItemsInfo.firstOrNull { it.index == now }
        ?: return scrollToAnchor(now, anchor)
    val correction = (landed.offset - desired(landed.size)).toFloat()
    if (correction != 0f) animateScrollBy(correction, spec)
}

/** Nejvýš o tolik položek se na citaci / výsledek hledání dojíždí; dál se skočí. */
private const val MAX_ANIMATED_JUMP_ITEMS = 40

/**
 * Výšky položek, které seznam už někdy rozvrhl, podle klíče.
 *
 * Položka pod spodní hranou z `layoutInfo` vypadne, i když přes ni uživatel před chvílí
 * vyroloval nahoru. Návrat dolů ([animateScrollToBottom]) z nich spočítá přesnou vzdálenost.
 */
@Composable
private fun rememberMeasuredSizes(listState: LazyListState): Map<Any, Int> {
    val sizes = remember(listState) { HashMap<Any, Int>() }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }
            .collect { visible -> visible.forEach { sizes[it.key] = it.size } }
    }
    return sizes
}

/**
 * Tlačítko „dolů": dojede k nejnovější zprávě obyčejným rolováním o spočítanou vzdálenost,
 * NE přes `animateScrollToItem`.
 *
 * `animateScrollToItem` o pár zpráv nad spodkem skončil poskočením: seznam dojel dolů, chvíli
 * stál, pak všechny bubliny kromě nejnovější vyskočily o ~330 px nahoru a sjely zpátky
 * (`animateItem`). Spouští to jeho závěrečné `snapToItem` — na pozici, kde už seznam stojí,
 * podle zdroje Compose animátor položek záměrně NEresetuje, a seznam je v lookahead scope
 * `SharedTransitionLayout`. Že přichází až po doběhnutí rolování, ukázal záznam obrazovky
 * rozložený po snímcích; poslední krok mechanismu ověřený není. Rolování o vzdálenost
 * (`animateScrollBy`) je pro seznam totéž co tah prstem a nic takového nespouští.
 *
 * Vzdálenost se skládá z výšek položek pod obrazovkou ([sizeAt], viz [rememberMeasuredSizes]).
 * Zdaleka ([MAX_ANIMATED_JUMP_ITEMS]) a tam, kde některou výšku neznáme (seznam se po otočení
 * obrazovky obnovil uprostřed historie), zůstává `animateScrollToItem` — rolovat přes desítky
 * zpráv by je všechny skládalo, a zdaleka se poskočení neukazovalo.
 */
private suspend fun LazyListState.animateScrollToBottom(sizeAt: (index: Int) -> Int?) {
    if (firstVisibleItemIndex > MAX_ANIMATED_JUMP_ITEMS) return animateScrollToItem(0)
    val spacing = layoutInfo.mainAxisItemSpacing
    var distance = firstVisibleItemScrollOffset
    for (index in 0 until firstVisibleItemIndex) {
        distance += (sizeAt(index) ?: return animateScrollToItem(0)) + spacing
    }
    // Bez odskoku: za spodní hranou seznam nepokračuje, překmit by se o ni zarazil a pružina
    // by se pak vracela nahoru.
    val spec = spring<Float>(stiffness = Spring.StiffnessMediumLow)
    animateScrollBy(-distance.toFloat(), spec)
    // Výška se mohla mezitím změnit (dotažený náhled, obrázek), nebo dole přibyla zpráva.
    val rest = layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.offset
    if (rest == null) scrollToItem(0) else if (rest != 0) animateScrollBy(rest.toFloat(), spec)
}

/**
 * [ConversationItemScope] nad ViewModelem obrazovky.
 *
 * Stav se čte přes [State] až uvnitř volání, takže se k jeho změnám přihlásí jen to UI, které
 * hodnotu opravdu kreslí (průběh stahování, pozice hlasovky), ne celá položka.
 */
private class ScreenItemScope(
    private val viewModel: ConversationViewModel,
    private val state: State<ConversationState>,
    private val voicePlayback: State<VoicePlayback?>,
    private val voiceSpeed: State<VoiceSpeed>,
    private val transcribeContext: State<TranscribeContext>,
    private val contentTop: State<Float?>,
    private val onMenuAnchor: (MenuAnchor) -> Unit,
    private val menuKey: State<Long?>,
    override val timeReveal: () -> Float,
    private val onOpenMedia: (messageId: Long) -> Unit,
    private val onOpenPollMedia: (messageId: Long, index: Int) -> Unit,
    private val onOpenArticleMedia: (messageId: Long, index: Int) -> Unit,
    private val onOpenStickerSet: (setId: Long) -> Unit,
    private val onSearch: (query: String) -> Unit,
    private val onSaveFileAs: (messageId: Long) -> Unit,
    private val jumpTransition: JumpTransition,
    private val searchHighlight: State<String?>,
    private val profileFlight: MutableState<Long?>,
    private val canSendCommands: State<Boolean>,
    private val soundingVideoNote: State<SoundingVideoNote?>,
) : ConversationItemScope {
    override fun isProfileFlight(messageId: Long): Boolean = profileFlight.value == messageId

    override fun pressingBotButton(messageId: Long): Pair<Int, Int>? =
        state.value.pressingBotButton?.takeIf { it.messageId == messageId }?.let { it.row to it.column }

    override fun searchHighlight(): String? = searchHighlight.value

    override fun canSendCommands(): Boolean = canSendCommands.value

    override fun doubleTapReaction(): Boolean = state.value.doubleTapReaction

    override fun gatherShift(key: Any): Float = jumpTransition.shiftOf(key)

    override fun gatherAlpha(key: Any): Float = jumpTransition.alphaOf(key)

    override fun voicePlayback(messageId: Long, part: Int): VoicePlayback? =
        voicePlayback.value?.takeIf { it.key.messageId == messageId && it.key.part == part }

    override fun voiceSpeed(): VoiceSpeed = voiceSpeed.value

    override fun transcribeOffer(durationSeconds: Int): TranscribeOffer = transcribeContext.value.let {
        eu.tintera.plume.core.telegram.transcribeOffer(it.isPremium, it.trial, durationSeconds, it.onDevice)
    }

    override fun localTranscript(messageId: Long): SpeechTranscript? = transcribeContext.value.local[messageId]

    @Composable
    override fun scamWarning(messageId: Long): ScamKind? {
        val verdicts by viewModel.scamVerdicts.collectAsStateWithLifecycle()
        return verdicts[messageId]
    }

    override fun dismissScamWarning(messageId: Long) = viewModel.dismissScamWarning(messageId)

    override fun videoNoteSounding(messageId: Long): Boolean = state.value.soundingVideoNote == messageId

    override fun soundingVideoNote(messageId: Long): SoundingVideoNote? =
        soundingVideoNote.value?.takeIf { it.key.messageId == messageId }

    override fun documentProgress(fileId: Int): Float? = state.value.documentDownloads[fileId]

    override fun articleLoading(messageId: Long): Boolean = messageId in state.value.loadingArticles

    override fun contentTop(): Float? = contentTop.value

    override fun onMenuAnchor(anchor: MenuAnchor) = onMenuAnchor.invoke(anchor)

    override fun isInMenu(key: Long): Boolean = menuKey.value == key

    /** Kdy se konverzace otevřela; starší zprávy jsou historie, ne příchod. */
    private val openedAt = Clock.System.now().epochSeconds
    private val arrived = HashSet<Long>()

    override fun claimArrival(key: Long, timestamp: Int): Boolean =
        timestamp >= openedAt - ARRIVAL_TOLERANCE_SECONDS && arrived.add(key)

    private val diceThrown = HashSet<Long>()

    override fun claimDiceThrow(key: Long, timestamp: Int): Boolean =
        timestamp >= openedAt - ARRIVAL_TOLERANCE_SECONDS && diceThrown.add(key)

    private val effectsPlayed = HashSet<Long>()

    override fun claimEffect(key: Long, message: ChatMessage, unreadBoundary: Long?): Boolean =
        effectAutoplays(message.timestamp, openedAt, message.isOutgoing, message.id, unreadBoundary) &&
            effectsPlayed.add(key)

    private val unreadBoundaryState = derivedStateOf { state.value.unreadBoundaryId }

    override fun unreadBoundary(): Long? = unreadBoundaryState.value

    /**
     * Navigace, sheet nad obrazovkou a systémový výběr umístění jsou věc obrazovky; všechno
     * ostatní jde do ViewModelu.
     */
    override fun onAction(messageId: Long, action: MessageAction) = when (action) {
        MessageAction.OpenMedia -> onOpenMedia(messageId)
        is MessageAction.OpenPollMedia -> onOpenPollMedia(messageId, action.index)
        is MessageAction.OpenArticleMedia -> onOpenArticleMedia(messageId, action.index)
        is MessageAction.OpenStickerSet -> onOpenStickerSet(action.setId)
        is MessageAction.SearchHashtag -> onSearch(action.tag)
        MessageMenuAction.SaveFileAs -> onSaveFileAs(messageId)
        is MessageAction.OpenSenderProfile -> {
            // Klíč letu musí stát dřív, než se profil otevře; chat se hledá asynchronně.
            profileFlight.value = messageId
            viewModel.onAction(messageId, action)
        }
        else -> viewModel.onAction(messageId, action)
    }

    override fun openSponsored(sponsored: SponsoredMessage) = viewModel.onSponsoredClick(sponsored)
}

/**
 * Naposledy zpracovaná nejnovější zpráva, pro ukotvení seznamu dole.
 *
 * Obyčejná třída, ne `mutableStateOf`: zapisuje se v kompozici a zápis do snapshot stavu by
 * obrazovku jen znovu rekomponoval, aniž by se tím cokoli změnilo.
 */
private class NewestItem {
    var key: Long? = null
    var id: Long = 0L

    /** Předchozí stav končil u přítomnosti — jen pak je novější nejnovější zpráva příchozí. */
    var live: Boolean = true

    /** Poslední změna nejnovější zprávy byla příchod, ne dotažená stránka. Čte ho odznak dole. */
    var lastWasArrival: Boolean = false
}

private const val LOAD_MORE_THRESHOLD = 5

/** O kolik položek výš od spodku se objeví tlačítko „dolů". */
private const val SCROLL_DOWN_THRESHOLD_ITEMS = 3

/** Tlačítko „dolů" bez počtu je kruh téhle velikosti — zároveň minimální dotyková plocha. */
private val SCROLL_DOWN_BUTTON_SIZE = 48.dp

private const val READ_DEBOUNCE_MS = 400L

/** Prefix klíče reklamní položky; podle něj se v seznamu pozná pro hlášení zhlédnutí. */

/** Jak dlouho musí být reklama v klidu na obrazovce, než se zhlédnutí nahlásí. */
private const val SPONSORED_VIEW_DEBOUNCE_MS = 1000L

/**
 * Průsvit pruhu pod vstupním polem.
 *
 * Dost na to, aby text pod ním nerušil, ale ne plná barva — ta by udělala ostrý předěl přes
 * celou obrazovku, což je právě to, co se odstraňovalo.
 */
private const val INPUT_SCRIM_ALPHA = 0.93f

/** Řádek pod názvem chatu: naposledy viděn, nebo ve vlákně název skupiny. */
private data class HeaderSubtitle(val text: String)

/**
 * „Naposledy viděn" do hlavičky.
 *
 * Telegram dostupnost **záměrně rozmazává**: kdo si to v soukromí vypne, je vidět jen jako
 * „naposledy nedávno" a přesný čas nedostaneme. Proto se nikde nedopočítává nic přesnějšího,
 * než co server dal — vymyslet minutu, kterou neznáme, by bylo horší než neurčitost.
 */

/**
 * Místo vstupního pole ve skupině nebo kanálu, kde uživatel není (otevřeno z globálního hledání),
 * a u nespuštěného bota se Start ([label]). Plné tlačítko přes šířku — je to jediná akce,
 * kterou tu jde udělat.
 */
@Composable
private fun JoinBar(
    joining: Boolean,
    onJoin: () -> Unit,
    label: StringResource = Res.string.conversation_join,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Button(
            onClick = onJoin,
            enabled = !joining,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Text(stringResource(label))
        }
    }
}

/**
 * Místo vstupního pole v kanálu, kde uživatel nepublikuje: ztlumit / zapnout zvuk, jako
 * v oficiálním klientu. Popisek říká, co se stane, ne stav (stejně jako v profilu chatu).
 */
@Composable
private fun MuteBar(muted: Boolean?, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        FilledTonalButton(
            onClick = onToggle,
            enabled = muted != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        ) {
            Icon(
                painterResource(if (muted == true) Res.drawable.notifications_24dp else Res.drawable.notifications_off_24dp),
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (muted == true) Res.string.profile_unmute else Res.string.profile_mute))
        }
    }
}

/** Místo vstupního pole tam, kde psát nejde a nic se s tím udělat nedá — aspoň proč. */
@Composable
private fun ReadOnlyNote(text: StringResource, modifier: Modifier = Modifier) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    )
}

/** Pod tímhle podílem schované zprávy se poloha do docku nepočítá — řádek by se kreslil naprázdno. */
private const val DOCK_EPSILON = 0.001f

/** Rozdíl průběhu, nad kterým se dock dojíždí animací místo vedení prstem (skok v seznamu). */
private const val DOCK_JUMP = 0.35f

/**
 * Od jakého vysunutí docku kreslí obraz kolečka se zvukem dock, a ne bublina. Polovina: dokud je
 * víc kolečka vidět v seznamu, patří obraz jemu.
 */
private const val VIDEO_NOTE_HANDOFF = 0.5f

/** Mini přehrávač v docku: co se v něm kreslí a jak daleko je vysunutý ([rememberPlayerDock]). */
private class PlayerDock(val player: DockedPlayer?, val shown: Boolean, val reveal: () -> Float)

/**
 * Hrající hlasovka, skladba nebo kolečko se zvukem, jejichž zpráva odjela z obrazovky. Měří se
 * stejně jako dock polohy; skladba z jiného chatu svou zprávu v seznamu nemá, a je proto v docku
 * celá. Kompozice čte jen KLÍČ hrajícího zvuku, průběh až řádek. Zvuk je v konverzaci nejvýš
 * jeden, takže kolečko a hlasovka se nepotkají.
 */
@Composable
private fun rememberPlayerDock(
    viewModel: ConversationViewModel,
    state: ConversationState,
    voicePlayback: State<VoicePlayback?>,
    soundingVideoNote: State<SoundingVideoNote?>,
    listState: LazyListState,
    barsHeight: () -> Dp,
    spec: FiniteAnimationSpec<Float>,
): PlayerDock {
    val density = LocalDensity.current
    val source by rememberUpdatedState(state)
    val nowPlaying by viewModel.nowPlaying.collectAsStateWithLifecycle()
    val docked by remember(viewModel.chatId) {
        derivedStateOf(structuralEqualityPolicy()) {
            soundingVideoNote.value?.let { it.key to false }
                ?: voicePlayback.value?.takeIf { it.docksIn(viewModel.chatId) }?.let { it.key to it.music }
        }
    }
    val target by remember(listState) {
        derivedStateOf {
            val key = docked?.first ?: return@derivedStateOf 0f
            if (key.chatId != viewModel.chatId) return@derivedStateOf 1f
            val info = listState.layoutInfo
            if (info.visibleItemsInfo.isEmpty()) return@derivedStateOf 0f
            // 8 dp je horní odsazení seznamu nad pruhy, jako u docku polohy.
            val top = info.viewportEndOffset - with(density) { (8.dp + barsHeight()).toPx() }
            // Blok, ne zpráva: skladba z alba je položka s klíčem nejstarší zprávy alba.
            val block = source.blockKeyOf(key.messageId)
            val item = block?.let { b -> info.visibleItemsInfo.firstOrNull { it.key == b } }
            item?.let { dockProgress(it.offset, it.size, top, 0f) } ?: 1f
        }
    }
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(reveal) {
        snapshotFlow { target }.collectLatest { to ->
            if (abs(to - reveal.value) > DOCK_JUMP) reveal.animateTo(to, spec) else reveal.snapTo(to)
        }
    }
    val shown by remember { derivedStateOf { target > DOCK_EPSILON || reveal.value > DOCK_EPSILON } }
    // Obraz kolečka si dock bere, až je víc v docku než v seznamu; kreslí ho vždycky jen jedna plocha.
    LaunchedEffect(soundingVideoNote.value) {
        val note = soundingVideoNote.value ?: return@LaunchedEffect
        snapshotFlow { shown && reveal.value >= VIDEO_NOTE_HANDOFF }.collect { note.docked = it }
    }
    val you = stringResource(Res.string.location_dock_you)
    val voiceLabel = stringResource(Res.string.conversation_voice_message)
    val videoNoteLabel = stringResource(Res.string.content_video_note)
    // Při zasouvání (zvuk dohrál, zpráva zpátky v pohledu) se kreslí poslední řádek, ne nic.
    val last = remember { arrayOf<DockedPlayer?>(null) }
    docked?.let { (key, music) ->
        val message = state.messages.firstOrNull { it.id == key.messageId }?.takeIf { key.chatId == viewModel.chatId }
        val videoNote = (message?.media as? MessageMedia.VideoNote)?.takeIf { soundingVideoNote.value?.key == key }
        last[0] = if (videoNote != null) {
            DockedPlayer(
                key = key,
                music = false,
                title = message.let { if (it.isOutgoing) you else it.senderName ?: state.info?.title } ?: videoNoteLabel,
                label = videoNoteLabel,
                coverFileId = videoNote.thumbnailFileId,
                video = true,
            )
        } else if (music) {
            val audio = (message?.media as? MessageMedia.Audio)?.takeIf { key.part == VoiceKey.NO_PART }
            val track = nowPlaying?.takeIf { it.key == key }?.track
            DockedPlayer(
                key = key,
                music = true,
                title = (track?.title ?: audio?.title)?.ifBlank { null } ?: audio?.fileName.orEmpty(),
                label = track?.performer ?: audio?.performer?.ifBlank { null },
                coverFileId = track?.coverFileId ?: audio?.coverFileId,
                hasNext = nowPlaying?.takeIf { it.key == key }?.next != null,
            )
        } else {
            DockedPlayer(
                key = key,
                music = false,
                title = message?.let { if (it.isOutgoing) you else it.senderName ?: state.info?.title } ?: voiceLabel,
                label = voiceLabel.takeIf { message?.media is MessageMedia.VoiceNote },
            )
        }
    }
    return PlayerDock(player = last[0], shown = shown, reveal = { reveal.value })
}

/** Řádek mini přehrávače pod dockem polohy ([PlayerDockBar] nad stavem z [rememberPlayerDock]). */
@Composable
private fun ConversationPlayerDock(
    dock: PlayerDock,
    chatHidden: Boolean,
    viewModel: ConversationViewModel,
    voicePlayback: State<VoicePlayback?>,
    soundingVideoNote: State<SoundingVideoNote?>,
    onOpenChat: (ChatTarget) -> Unit,
    glass: Modifier,
) {
    val player = dock.player ?: return
    if (!dock.shown || (chatHidden && player.key.chatId == viewModel.chatId)) return
    val note = { soundingVideoNote.value?.takeIf { player.video && it.key == player.key } }
    PlayerDockBar(
        player = player,
        playback = {
            if (player.video) note()?.playback else voicePlayback.value?.takeIf { it.key == player.key }
        },
        reveal = dock.reveal,
        onToggle = { if (player.video) note()?.toggle() else viewModel.togglePlaying() },
        onNext = viewModel::skipToNextTrack,
        onStop = {
            if (player.video) {
                viewModel.onAction(player.key.messageId, MessageAction.VideoNoteSoundEnded)
            } else {
                viewModel.stopPlaying()
            }
        },
        video = if (player.video) {
            { note()?.takeIf { it.docked }?.let { VideoSurface(it.player, Modifier.fillMaxSize()) } }
        } else {
            null
        },
        onShow = {
            if (player.key.chatId == viewModel.chatId) {
                viewModel.jumpToMessage(player.key.messageId)
            } else {
                onOpenChat(ChatTarget(player.key.chatId, player.key.messageId))
            }
        },
        glass = glass,
    )
}

/**
 * Bloky seznamu konverzace (viz [conversationItems]). Vlastní funkce, ne kus [ConversationScreen]:
 * ta je v dexu na hraně registrů (viz CLAUDE.md, „ConversationScreen je v dexu na hraně").
 */
@Composable
private fun rememberConversationItems(state: ConversationState): List<ConversationItem> {
    // Spotřebovaná mizející média: v seznamu jsou oznámením o vypršení hned, ne až po TDLib.
    val expiredMedia by koinInject<SelfDestructTimers>().consumedIds.collectAsState()
    return remember(
        state.messages,
        state.itemKeys,
        state.sponsored,
        state.reachedEnd,
        state.loading,
        state.info,
        state.unreadBoundaryId,
        state.readReceiptFor,
        state.lastReadOutboxMessageId,
        state.quotes,
        state.highlightedMessageId,
        state.search?.current,
        state.flight,
        state.messageMenu,
        state.noticeMenu,
        state.typing,
        // Jen KLÍČE výběru: práva k němu chodí po jednom a seznam bloků se jimi nemění.
        state.selection?.keys,
        expiredMedia,
    ) { conversationItems(state, expiredMedia) }
}
