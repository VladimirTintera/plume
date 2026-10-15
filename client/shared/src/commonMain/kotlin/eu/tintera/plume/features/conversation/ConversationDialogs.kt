package eu.tintera.plume.features.conversation

import eu.tintera.plume.resources.menu_select
import eu.tintera.plume.core.settings.ExpressionMode
import eu.tintera.plume.core.telegram.MediaReplacement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.tintera.plume.core.location.liveLocationSharingSupported
import eu.tintera.plume.core.location.rememberDeviceLocation
import eu.tintera.plume.core.telegram.MessageSchedule
import eu.tintera.plume.core.telegram.SendOptions
import eu.tintera.plume.core.ui.EmbeddedMediaSelection
import eu.tintera.plume.core.ui.rememberEmbeddedMediaSelection
import eu.tintera.plume.core.telegram.GifItem
import eu.tintera.plume.core.telegram.StickerItem
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.confirm
import eu.tintera.plume.core.ui.reject
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_location_title
import eu.tintera.plume.resources.conversation_schedule_past
import eu.tintera.plume.resources.conversation_schedule_time
import eu.tintera.plume.resources.remind_time
import kotlin.time.Clock
import kotlinx.datetime.TimeZone
import org.jetbrains.compose.resources.stringResource

/**
 * Co je nad konverzací otevřené po ťuknutí v obrazovce: výběry samolepek, galerie a místa,
 * plánování, efekty, náhled přílohy, seznam připnutých. Dialogy, které otevírá stav ViewModelu
 * (`state.translation`, `state.pendingDelete`…), sem nepatří — ty si řídí ViewModel sám.
 *
 * Jeden držák místo deseti proměnných v [ConversationScreen]: ta je v dexu na hraně registrů
 * a každá `var … by rememberSaveable` v ní stála registry navíc.
 *
 * **Otočení přežijí jen příznaky, které ho přežívaly dřív** (ukládá je [Saver]). Plánování
 * a výběr efektu se zapomenou: [ScheduleTarget] nese samolepku nebo GIF, které uložit nejde,
 * a den bez cíle nedává smysl.
 */
@Stable
internal class ConversationSheets {
    var stickerPicker by mutableStateOf(false)
    var mediaPicker by mutableStateOf(false)

    /** Galerie pro výměnu fotky nebo videa při úpravě zprávy (jen kde je vestavěná). */
    var replacementPicker by mutableStateOf(false)

    /** Výběr GIFů pro výměnu GIFu při úpravě zprávy. */
    var gifReplacement by mutableStateOf(false)
    var locationPicker by mutableStateOf(false)
    var livePeriod by mutableStateOf(false)

    /** Příloha nad polem otevřená v náhledu; `null` = zavřený. */
    var previewAttachmentIndex by mutableStateOf<Int?>(null)

    /** Sada otevřená ťuknutím na samolepku v konverzaci. */
    var openStickerSetId by mutableStateOf<Long?>(null)

    /** Všechny připnuté zprávy (ťuknutí na seznam v pruhu připnutých). */
    var allPinned by mutableStateOf(false)

    /** Plánování: napřed den, pak čas. Cíl je rozepsaná zpráva, nebo naplánovaná k přeplánování. */
    var scheduleTarget by mutableStateOf<ScheduleTarget?>(null)
    var scheduleDay by mutableStateOf<Long?>(null)
    var effectPicker by mutableStateOf(false)

    companion object {
        val Saver: Saver<ConversationSheets, Any> = listSaver(
            save = {
                listOf(
                    it.stickerPicker, it.mediaPicker, it.locationPicker, it.livePeriod,
                    it.previewAttachmentIndex, it.openStickerSetId, it.allPinned,
                )
            },
            restore = { saved ->
                ConversationSheets().apply {
                    stickerPicker = saved[0] as Boolean
                    mediaPicker = saved[1] as Boolean
                    locationPicker = saved[2] as Boolean
                    livePeriod = saved[3] as Boolean
                    previewAttachmentIndex = saved[4] as Int?
                    openStickerSetId = saved[5] as Long?
                    allPinned = saved[6] as Boolean
                }
            },
        )
    }
}

@Composable
internal fun rememberConversationSheets(): ConversationSheets =
    rememberSaveable(saver = ConversationSheets.Saver) { ConversationSheets() }

/**
 * Dialogy a sheety nad konverzací — ty z ťuknutí v obrazovce ([sheets]) i ty, které otevírá
 * stav ViewModelu. Sheety jsou vlastní okna, takže na místě v kompozici nezáleží.
 */
@Composable
internal fun ConversationDialogs(
    state: ConversationState,
    viewModel: ConversationViewModel,
    sheets: ConversationSheets,
    composer: ComposerDraft,
    mediaSelection: EmbeddedMediaSelection,
    summary: ChatSummaryViewModel,
    startSummary: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var draft by composer
    /** Poslední otevřená část výběru — uložená v nastavení, takže platí i po restartu aplikace. */
    val expressionMode by viewModel.expressionMode.collectAsStateWithLifecycle()
    val summaryState by summary.state.collectAsStateWithLifecycle()
    val summarySourceState by summary.source.collectAsStateWithLifecycle()

    /*
     * Výměna přílohy má VLASTNÍ paměť výběru: zaškrtnutí patří vyměňované fotce, ne přílohám nad
     * polem — se společnou by se v galerii ukázaly zaškrtnuté přílohy rozepsané zprávy.
     */
    val replacementSelection = rememberEmbeddedMediaSelection()
    if (sheets.replacementPicker) {
        MediaPickerSheet(
            selection = replacementSelection,
            pickedPaths = listOfNotNull((state.editReplacement as? MediaReplacement.File)?.attachment?.path),
            allowPhotos = state.content.photos,
            allowVideos = state.content.videos,
            onPicked = viewModel::replaceEditMedia,
            // Odznačená nová příloha = zpátky k původní.
            onUnpicked = { viewModel.keepEditMedia() },
            onError = viewModel::reportError,
            onDismiss = { sheets.replacementPicker = false },
            single = true,
        )
    }

    if (sheets.mediaPicker) {
        MediaPickerSheet(
            selection = mediaSelection,
            pickedPaths = state.attachments.map { state.galleryPath(it.path) },
            allowPhotos = state.content.photos,
            allowVideos = state.content.videos,
            onPicked = viewModel::addAttachments,
            onUnpicked = viewModel::removeAttachments,
            onError = viewModel::reportError,
            onDismiss = { sheets.mediaPicker = false },
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

    if (sheets.locationPicker) {
        PlacePickerHost(
            searcher = viewModel.places,
            title = stringResource(Res.string.conversation_location_title),
            onPick = { place ->
                sheets.locationPicker = false
                haptics.confirm()
                viewModel.sendLocation(place)
            },
            onDismiss = { sheets.locationPicker = false },
            autoFocusSearch = false,
            // Živou polohu jen tam, kde ji platforma umí posouvat, a ne v kanálu — příspěvek
            // kanálu s polohou, která se hýbe, oficiální klienti nenabízejí.
            onShareLive = {
                sheets.locationPicker = false
                sheets.livePeriod = true
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
    if (sheets.livePeriod) {
        LiveLocationPeriodDialog(
            onPick = { period ->
                sheets.livePeriod = false
                haptics.confirm()
                liveLocation.request { point -> viewModel.shareLiveLocation(point, period) }
            },
            onDismiss = { sheets.livePeriod = false },
        )
    }

    if (sheets.gifReplacement) {
        // Tentýž výběr jako při psaní, jen s GIFy: GIF se vyměňuje zase GIFem.
        StickerPickerSheet(
            mode = ExpressionMode.Gifs,
            onModeChange = {},
            stickersAvailable = true,
            draft = TextFieldValue(),
            onDraftEdit = {},
            onPick = {},
            chatId = viewModel.chatId,
            onPickGif = { gif ->
                sheets.gifReplacement = false
                viewModel.replaceEditGif(gif)
            },
            onDismiss = { sheets.gifReplacement = false },
            modes = listOf(ExpressionMode.Gifs),
            // GIF se tu vybírá, neposílá — odejde až s uložením úpravy.
            pickLabel = Res.string.menu_select,
        )
    }

    if (sheets.stickerPicker) {
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
            draftFormats = composer.formats,
            onCustomEmoji = {
                composer.insertCustomEmoji(it.emoji, it.id)
                viewModel.onDraftChanged(composer.composed)
            },
            onPick = { sticker ->
                sheets.stickerPicker = false
                haptics.confirm()
                viewModel.sendSticker(sticker)
            },
            sendMenu = sendMenuOffer(state).copy(effects = false),
            onPickWith = { sticker, choice ->
                sheets.stickerPicker = false
                when (choice) {
                    SendMenuChoice.Silent -> {
                        haptics.confirm()
                        viewModel.sendSticker(sticker, SendOptions(silent = true))
                    }
                    SendMenuChoice.WhenOnline -> {
                        haptics.confirm()
                        viewModel.sendSticker(sticker, SendOptions(schedule = MessageSchedule.WhenOnline))
                    }
                    SendMenuChoice.Schedule -> sheets.scheduleTarget = ScheduleTarget.Sticker(sticker)
                }
            },
            chatId = viewModel.chatId,
            onPickGif = { gif ->
                sheets.stickerPicker = false
                haptics.confirm()
                viewModel.sendGif(gif)
            },
            onPickGifWith = { gif, choice ->
                sheets.stickerPicker = false
                when (choice) {
                    SendMenuChoice.Silent -> {
                        haptics.confirm()
                        viewModel.sendGif(gif, SendOptions(silent = true))
                    }
                    SendMenuChoice.WhenOnline -> {
                        haptics.confirm()
                        viewModel.sendGif(gif, SendOptions(schedule = MessageSchedule.WhenOnline))
                    }
                    SendMenuChoice.Schedule -> sheets.scheduleTarget = ScheduleTarget.Gif(gif)
                }
            },
            onDismiss = { sheets.stickerPicker = false },
        )
    }

    // Sada z odkazu `t.me/addstickers/…` otevře týž sheet jako ťuknutí na samolepku.
    LaunchedEffect(state.openStickerSet) {
        val setId = state.openStickerSet ?: return@LaunchedEffect
        viewModel.consumeOpenStickerSet()
        sheets.openStickerSetId = setId
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

    sheets.openStickerSetId?.let { setId ->
        StickerSetSheet(
            setId = setId,
            onPick = { sticker ->
                sheets.openStickerSetId = null
                haptics.confirm()
                viewModel.sendSticker(sticker)
            },
            onDismiss = { sheets.openStickerSetId = null },
        )
    }

    if (summaryState != ChatSummaryState.Idle) {
        ChatSummarySheet(
            state = summaryState,
            source = summarySourceState,
            onRetry = { summary.retry(startSummary) },
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

    if (state.addingChecklistTask != null) {
        AddChecklistTaskDialog(
            maxLength = state.checklistTaskMax,
            onConfirm = viewModel::addChecklistTask,
            onDismiss = viewModel::dismissAddChecklistTask,
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

    state.pendingDeletePollOption?.let { pending ->
        DeletePollOptionDialog(text = pending.text, onConfirm = viewModel::confirmDeletePollOption, onDismiss = viewModel::cancelDeletePollOption)
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

    state.scheduledMessages?.let { scheduled ->
        ScheduledMessagesSheet(
            messages = scheduled,
            onSendNow = { message -> viewModel.reschedule(message.id, null) },
            onReschedule = { message -> sheets.scheduleTarget = ScheduleTarget.Existing(message.id) },
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
                sheets.scheduleTarget = ScheduleTarget.Reminder(messageId)
            },
            onDismiss = viewModel::closeRemind,
        )
    }

    if (sheets.scheduleTarget != null && sheets.scheduleDay == null) {
        FutureDayDialog(
            now = Clock.System.now().epochSeconds,
            maxAheadSeconds = MAX_SCHEDULE_AHEAD_SECONDS,
            onPick = { day -> sheets.scheduleDay = day },
            onDismiss = {
                // Hlasovka čekala pozastavená; bez času se nahrává dál.
                if (sheets.scheduleTarget == ScheduleTarget.Voice) viewModel.resumeRecording()
                sheets.scheduleTarget = null
            },
        )
    }
    val target = sheets.scheduleTarget
    val day = sheets.scheduleDay
    if (target != null && day != null) {
        TimeOfDayDialog(
            title = stringResource(
                if (target is ScheduleTarget.Reminder) Res.string.remind_time else Res.string.conversation_schedule_time,
            ),
            // Nabídne se za hodinu od teď — nejčastěji se plánuje „na později dnes".
            initial = Clock.System.now().epochSeconds + SCHEDULE_DEFAULT_AHEAD_SECONDS,
            onPick = { hour, minute ->
                sheets.scheduleTarget = null
                sheets.scheduleDay = null
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
                if (sheets.scheduleTarget == ScheduleTarget.Voice) viewModel.resumeRecording()
                sheets.scheduleTarget = null
                sheets.scheduleDay = null
            },
        )
    }

    if (sheets.effectPicker) {
        val effects by viewModel.effectChoices.collectAsStateWithLifecycle()
        EffectPickerSheet(
            effects = effects,
            onPick = { effect ->
                sheets.effectPicker = false
                haptics.confirm()
                viewModel.send(composer.composed, SendOptions(effectId = effect.id))
                draft = TextFieldValue()
            },
            onDismiss = { sheets.effectPicker = false },
        )
    }

    if (sheets.allPinned && state.pinned.isNotEmpty()) {
        PinnedMessagesSheet(
            messages = state.pinned,
            canUnpin = state.canUnpin,
            onOpen = { message -> viewModel.jumpToMessage(message.id, anchor = ScrollAnchor.Top) },
            onUnpin = viewModel::unpin,
            onUnpinAll = viewModel::unpinAll,
            onDismiss = { sheets.allPinned = false },
        )
    }
}

/** Co se plánuje: rozepsaná zpráva z pole, nebo už naplánovaná, které se mění čas. */
internal sealed interface ScheduleTarget {
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
