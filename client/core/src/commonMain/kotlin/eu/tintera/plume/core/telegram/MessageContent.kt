package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.ui.orText
import eu.tintera.plume.core.ui.UiText
import dev.g000sha256.tdl.dto.PushMessageContentBasicGroupChatCreate
import dev.g000sha256.tdl.dto.PushMessageContentChatChangeTitle
import dev.g000sha256.tdl.dto.PushMessageContentChatChangePhoto
import dev.g000sha256.tdl.dto.PushMessageContentChatAddMembers
import dev.g000sha256.tdl.dto.PushMessageContentChatDeleteMember
import dev.g000sha256.tdl.dto.PushMessageContentChatJoinByLink
import dev.g000sha256.tdl.dto.PushMessageContentChatJoinByRequest
import dev.g000sha256.tdl.dto.PushMessageContentChatSetBackground
import dev.g000sha256.tdl.dto.PushMessageContentChatSetTheme
import dev.g000sha256.tdl.dto.PushMessageContentChecklistTasksAdded
import dev.g000sha256.tdl.dto.PushMessageContentChecklistTasksDone
import dev.g000sha256.tdl.dto.PushMessageContentContactRegistered
import dev.g000sha256.tdl.dto.PushMessageContentInviteVideoChatParticipants
import dev.g000sha256.tdl.dto.PushMessageContentMessageForwards
import dev.g000sha256.tdl.dto.PushMessageContentPollOptionAdded
import dev.g000sha256.tdl.dto.PushMessageContentProximityAlertTriggered
import dev.g000sha256.tdl.dto.PushMessageContentRecurringPayment
import dev.g000sha256.tdl.dto.PushMessageContentScreenshotTaken
import dev.g000sha256.tdl.dto.PushMessageContentSuggestProfilePhoto
import dev.g000sha256.tdl.dto.PushMessageContentSuggestBirthdate
import dev.g000sha256.tdl.dto.PushMessageContentVideoChatStarted
import dev.g000sha256.tdl.dto.PushMessageContentVideoChatEnded
import eu.tintera.plume.resources.push_group_created
import eu.tintera.plume.resources.push_title_changed
import eu.tintera.plume.resources.push_photo_changed
import eu.tintera.plume.resources.push_member_returned
import eu.tintera.plume.resources.push_added_you
import eu.tintera.plume.resources.push_member_added
import eu.tintera.plume.resources.push_member_left
import eu.tintera.plume.resources.push_removed_you
import eu.tintera.plume.resources.push_member_removed
import eu.tintera.plume.resources.push_joined_by_link
import eu.tintera.plume.resources.push_joined_by_request
import eu.tintera.plume.resources.push_contact_joined
import eu.tintera.plume.resources.push_video_chat_invited_you
import eu.tintera.plume.resources.push_forwarded_messages
import eu.tintera.plume.resources.push_recurring_payment
import eu.tintera.plume.resources.push_screenshot
import eu.tintera.plume.resources.push_game_score
import eu.tintera.plume.resources.service_background_changed
import eu.tintera.plume.resources.service_theme_reset
import eu.tintera.plume.resources.service_theme_changed
import eu.tintera.plume.resources.service_checklist_tasks_added
import eu.tintera.plume.resources.service_checklist_tasks_done
import eu.tintera.plume.resources.service_video_chat_invite
import eu.tintera.plume.resources.service_poll_option_added_named
import eu.tintera.plume.resources.service_poll_option_added
import eu.tintera.plume.resources.service_nearby
import eu.tintera.plume.resources.service_video_chat_started
import eu.tintera.plume.resources.service_video_chat_ended
import eu.tintera.plume.core.ui.grouped
import eu.tintera.plume.core.ui.distanceUiText
import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.LinkPreview as TdlLinkPreview
import dev.g000sha256.tdl.dto.LinkPreviewTypeArticle
import dev.g000sha256.tdl.dto.LinkPreviewTypeEmbeddedAnimationPlayer
import dev.g000sha256.tdl.dto.LinkPreviewTypeEmbeddedVideoPlayer
import dev.g000sha256.tdl.dto.LinkPreviewTypePhoto
import dev.g000sha256.tdl.dto.LinkPreviewTypeVideo
import dev.g000sha256.tdl.dto.LinkPreviewType
import dev.g000sha256.tdl.dto.LinkPreviewTypeApp
import dev.g000sha256.tdl.dto.LinkPreviewTypeChannelBoost
import dev.g000sha256.tdl.dto.LinkPreviewTypeChat
import dev.g000sha256.tdl.dto.LinkPreviewTypeLiveStory
import dev.g000sha256.tdl.dto.LinkPreviewTypeMessage
import dev.g000sha256.tdl.dto.LinkPreviewTypeSticker
import dev.g000sha256.tdl.dto.LinkPreviewTypeStickerSet
import dev.g000sha256.tdl.dto.LinkPreviewTypeStory
import dev.g000sha256.tdl.dto.LinkPreviewTypeSupergroupBoost
import dev.g000sha256.tdl.dto.LinkPreviewTypeUser
import dev.g000sha256.tdl.dto.LinkPreviewTypeWebApp
import dev.g000sha256.tdl.dto.LinkPreviewTypeAlbum
import dev.g000sha256.tdl.dto.LinkPreviewTypeAnimation
import dev.g000sha256.tdl.dto.LinkPreviewTypeDocument
import dev.g000sha256.tdl.dto.LinkPreviewAlbumMediaPhoto
import dev.g000sha256.tdl.dto.LinkPreviewAlbumMediaVideo
import dev.g000sha256.tdl.dto.InviteLinkChatTypeChannel
import dev.g000sha256.tdl.dto.ChatPhoto as TdlChatPhoto
import dev.g000sha256.tdl.dto.DiceStickers
import dev.g000sha256.tdl.dto.DiceStickersRegular
import dev.g000sha256.tdl.dto.DiceStickersSlotMachine
import dev.g000sha256.tdl.dto.Document
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.PollMedia
import dev.g000sha256.tdl.dto.PollMediaAnimation
import dev.g000sha256.tdl.dto.PollMediaAudio
import dev.g000sha256.tdl.dto.PollMediaDocument
import dev.g000sha256.tdl.dto.PollMediaLink
import dev.g000sha256.tdl.dto.PollMediaLocation
import dev.g000sha256.tdl.dto.PollMediaPhoto
import dev.g000sha256.tdl.dto.PollMediaSticker
import dev.g000sha256.tdl.dto.PollMediaVenue
import dev.g000sha256.tdl.dto.PollMediaVideo
import dev.g000sha256.tdl.dto.AlternativeVideo
import dev.g000sha256.tdl.dto.Video
import dev.g000sha256.tdl.dto.MessageAnimatedEmoji
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.Audio
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.VoiceNote
import dev.g000sha256.tdl.dto.MessageCall
import dev.g000sha256.tdl.dto.CallDiscardReason
import dev.g000sha256.tdl.dto.CallDiscardReasonDeclined
import dev.g000sha256.tdl.dto.CallDiscardReasonDisconnected
import dev.g000sha256.tdl.dto.CallDiscardReasonEmpty
import dev.g000sha256.tdl.dto.CallDiscardReasonHungUp
import dev.g000sha256.tdl.dto.CallDiscardReasonMissed
import dev.g000sha256.tdl.dto.CallDiscardReasonUpgradeToGroupCall
import dev.g000sha256.tdl.dto.MessageChecklist
import dev.g000sha256.tdl.dto.MessageContact
import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessageDice
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessageGame
import dev.g000sha256.tdl.dto.MessageInvoice
import dev.g000sha256.tdl.dto.MessageLiveLocation
import dev.g000sha256.tdl.dto.MessageLocation
import dev.g000sha256.tdl.dto.MessagePaidMedia
import dev.g000sha256.tdl.dto.PaidMediaPreview
import dev.g000sha256.tdl.dto.PaidMediaVideo
import dev.g000sha256.tdl.dto.PaidMediaPhoto
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessagePoll
import dev.g000sha256.tdl.dto.PollTypeQuiz
import dev.g000sha256.tdl.dto.PollVoteRestrictionReason
import dev.g000sha256.tdl.dto.PollVoteRestrictionReasonClosed
import dev.g000sha256.tdl.dto.PollVoteRestrictionReasonCountryRestricted
import dev.g000sha256.tdl.dto.PollVoteRestrictionReasonMembershipRequired
import dev.g000sha256.tdl.dto.PollVoteRestrictionReasonOther
import dev.g000sha256.tdl.dto.PollVoteRestrictionReasonScheduled
import dev.g000sha256.tdl.dto.PollVoteRestrictionReasonYetUnsent
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.MessageSelfDestructType
import dev.g000sha256.tdl.dto.MessageSelfDestructTypeImmediately
import dev.g000sha256.tdl.dto.MessageSelfDestructTypeTimer
import dev.g000sha256.tdl.dto.MessageStakeDice
import dev.g000sha256.tdl.dto.MessageSticker
import dev.g000sha256.tdl.dto.MessageStory
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageTonConnectRequest
import dev.g000sha256.tdl.dto.MessageTonWalletTransfer
import dev.g000sha256.tdl.dto.MessageUnsupported
import dev.g000sha256.tdl.dto.MessageVenue
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageVideoNote
import dev.g000sha256.tdl.dto.MessageVoiceNote
import dev.g000sha256.tdl.dto.Photo
import dev.g000sha256.tdl.dto.PhotoSize
import dev.g000sha256.tdl.dto.MessageGiveaway
import dev.g000sha256.tdl.dto.MessageGiveawayWinners
import dev.g000sha256.tdl.dto.MessageGift
import dev.g000sha256.tdl.dto.MessageUpgradedGift
import dev.g000sha256.tdl.dto.MessageRefundedUpgradedGift
import dev.g000sha256.tdl.dto.MessageGiftedPremium
import dev.g000sha256.tdl.dto.MessagePremiumGiftCode
import dev.g000sha256.tdl.dto.MessageGiftedStars
import dev.g000sha256.tdl.dto.MessageGiveawayPrizeStars
import dev.g000sha256.tdl.dto.MessageGiftedGrams
import dev.g000sha256.tdl.dto.MessageGroupCall
import dev.g000sha256.tdl.dto.MessageRichMessage
import dev.g000sha256.tdl.dto.RichMessage
import dev.g000sha256.tdl.dto.PageBlock
import dev.g000sha256.tdl.dto.PageBlockHeader
import dev.g000sha256.tdl.dto.PageBlockKicker
import dev.g000sha256.tdl.dto.PageBlockParagraph
import dev.g000sha256.tdl.dto.PageBlockSectionHeading
import dev.g000sha256.tdl.dto.PageBlockSubheader
import dev.g000sha256.tdl.dto.PageBlockSubtitle
import dev.g000sha256.tdl.dto.PageBlockTitle
import dev.g000sha256.tdl.dto.PushMessageContentGift
import dev.g000sha256.tdl.dto.PushMessageContentGiveaway
import dev.g000sha256.tdl.dto.PushMessageContentMediaAlbum
import dev.g000sha256.tdl.dto.PushMessageContentPremiumGiftCode
import dev.g000sha256.tdl.dto.PushMessageContentUpgradedGift
import dev.g000sha256.tdl.dto.RichText
import dev.g000sha256.tdl.dto.RichTextAnchorLink
import dev.g000sha256.tdl.dto.RichTextBankCardNumber
import dev.g000sha256.tdl.dto.RichTextBold
import dev.g000sha256.tdl.dto.RichTextBotCommand
import dev.g000sha256.tdl.dto.RichTextCashtag
import dev.g000sha256.tdl.dto.RichTextCustomEmoji
import dev.g000sha256.tdl.dto.RichTextDateTime
import dev.g000sha256.tdl.dto.RichTextDiff
import dev.g000sha256.tdl.dto.RichTextEmailAddress
import dev.g000sha256.tdl.dto.RichTextFixed
import dev.g000sha256.tdl.dto.RichTextHashtag
import dev.g000sha256.tdl.dto.RichTextItalic
import dev.g000sha256.tdl.dto.RichTextMarked
import dev.g000sha256.tdl.dto.RichTextMention
import dev.g000sha256.tdl.dto.RichTextMentionName
import dev.g000sha256.tdl.dto.RichTextPhoneNumber
import dev.g000sha256.tdl.dto.RichTextPlain
import dev.g000sha256.tdl.dto.RichTextReference
import dev.g000sha256.tdl.dto.RichTextReferenceLink
import dev.g000sha256.tdl.dto.RichTextStrikethrough
import dev.g000sha256.tdl.dto.RichTextSubscript
import dev.g000sha256.tdl.dto.RichTextSuperscript
import dev.g000sha256.tdl.dto.RichTextTonAddress
import dev.g000sha256.tdl.dto.RichTextUnderline
import dev.g000sha256.tdl.dto.RichTextUrl
import dev.g000sha256.tdl.dto.RichTexts
import dev.g000sha256.tdl.dto.PushMessageContent
import dev.g000sha256.tdl.dto.PushMessageContentAnimation
import dev.g000sha256.tdl.dto.PushMessageContentAudio
import dev.g000sha256.tdl.dto.PushMessageContentChecklist
import dev.g000sha256.tdl.dto.PushMessageContentContact
import dev.g000sha256.tdl.dto.PushMessageContentDocument
import dev.g000sha256.tdl.dto.PushMessageContentGame
import dev.g000sha256.tdl.dto.PushMessageContentGameScore
import dev.g000sha256.tdl.dto.PushMessageContentHidden
import dev.g000sha256.tdl.dto.PushMessageContentInvoice
import dev.g000sha256.tdl.dto.PushMessageContentLocation
import dev.g000sha256.tdl.dto.PushMessageContentPaidMedia
import dev.g000sha256.tdl.dto.PushMessageContentPhoto
import dev.g000sha256.tdl.dto.PushMessageContentPoll
import dev.g000sha256.tdl.dto.PushMessageContentSticker
import dev.g000sha256.tdl.dto.PushMessageContentStory
import dev.g000sha256.tdl.dto.PushMessageContentText
import dev.g000sha256.tdl.dto.PushMessageContentTonConnectRequest
import dev.g000sha256.tdl.dto.PushMessageContentTonWalletTransfer
import dev.g000sha256.tdl.dto.PushMessageContentVideo
import dev.g000sha256.tdl.dto.PushMessageContentVideoNote
import dev.g000sha256.tdl.dto.PushMessageContentVoiceNote
import dev.g000sha256.tdl.dto.Sticker
import dev.g000sha256.tdl.dto.StickerFormatTgs
import dev.g000sha256.tdl.dto.StickerFormatWebm
import dev.g000sha256.tdl.dto.StickerFormatWebp
import dev.g000sha256.tdl.dto.TextEntity
import dev.g000sha256.tdl.dto.TextEntityTypeBlockQuote
import dev.g000sha256.tdl.dto.TextEntityTypeBold
import dev.g000sha256.tdl.dto.TextEntityTypeBotCommand
import dev.g000sha256.tdl.dto.TextEntityTypeCode
import dev.g000sha256.tdl.dto.TextEntityTypeCustomEmoji
import dev.g000sha256.tdl.dto.TextEntityTypeEmailAddress
import dev.g000sha256.tdl.dto.TextEntityTypeExpandableBlockQuote
import dev.g000sha256.tdl.dto.TextEntityTypeHashtag
import dev.g000sha256.tdl.dto.TextEntityTypeItalic
import dev.g000sha256.tdl.dto.TextEntityTypeMention
import dev.g000sha256.tdl.dto.TextEntityTypeMentionName
import dev.g000sha256.tdl.dto.TextEntityTypeMediaTimestamp
import dev.g000sha256.tdl.dto.TextEntityTypeDateTime
import dev.g000sha256.tdl.dto.DateTimeFormattingType
import dev.g000sha256.tdl.dto.DateTimeFormattingTypeAbsolute
import dev.g000sha256.tdl.dto.DateTimeFormattingTypeRelative
import dev.g000sha256.tdl.dto.DateTimePartPrecision
import dev.g000sha256.tdl.dto.DateTimePartPrecisionLong
import dev.g000sha256.tdl.dto.DateTimePartPrecisionNone
import dev.g000sha256.tdl.dto.DateTimePartPrecisionShort
import dev.g000sha256.tdl.dto.TextEntityTypePhoneNumber
import dev.g000sha256.tdl.dto.TextEntityTypePre
import dev.g000sha256.tdl.dto.TextEntityTypePreCode
import dev.g000sha256.tdl.dto.TextEntityTypeSpoiler
import dev.g000sha256.tdl.dto.TextEntityTypeStrikethrough
import dev.g000sha256.tdl.dto.TextEntityTypeTextUrl
import dev.g000sha256.tdl.dto.TextEntityTypeUnderline
import dev.g000sha256.tdl.dto.TextEntityTypeUrl
import dev.g000sha256.tdl.dto.Thumbnail
import dev.g000sha256.tdl.dto.ThumbnailFormatJpeg
import dev.g000sha256.tdl.dto.ThumbnailFormatMpeg4
import dev.g000sha256.tdl.dto.ThumbnailFormatPng
import dev.g000sha256.tdl.dto.ThumbnailFormatWebp
import dev.g000sha256.tdl.dto.ThumbnailFormatWebm
import dev.g000sha256.tdl.dto.ThumbnailFormatTgs
import eu.tintera.plume.core.audio.decodeWaveform
import org.jetbrains.compose.resources.getString
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.content_album
import eu.tintera.plume.resources.content_audio
import eu.tintera.plume.resources.content_gift
import eu.tintera.plume.resources.content_giveaway
import eu.tintera.plume.resources.content_giveaway_winners
import eu.tintera.plume.resources.content_rich_message
import eu.tintera.plume.resources.content_stake_dice
import eu.tintera.plume.resources.content_suggested_birthdate
import eu.tintera.plume.resources.content_suggested_photo
import eu.tintera.plume.resources.content_ton_connect_request
import eu.tintera.plume.resources.content_ton_connect_request_named
import eu.tintera.plume.resources.content_ton_received
import eu.tintera.plume.resources.content_ton_received_comment
import eu.tintera.plume.resources.content_ton_sent
import eu.tintera.plume.resources.content_ton_sent_comment
import eu.tintera.plume.resources.ton_amount
import eu.tintera.plume.resources.content_checklist
import eu.tintera.plume.resources.content_contact
import eu.tintera.plume.resources.content_file
import eu.tintera.plume.resources.content_game
import eu.tintera.plume.resources.content_gif
import eu.tintera.plume.resources.content_invoice
import eu.tintera.plume.resources.content_live_location
import eu.tintera.plume.resources.content_location
import eu.tintera.plume.resources.content_paid
import eu.tintera.plume.resources.content_poll
import eu.tintera.plume.resources.content_story
import eu.tintera.plume.resources.content_unsupported
import eu.tintera.plume.resources.content_venue
import eu.tintera.plume.resources.content_video_note
import eu.tintera.plume.resources.conversation_voice_message
import eu.tintera.plume.resources.media_photo
import eu.tintera.plume.resources.media_self_destruct_photo
import eu.tintera.plume.resources.media_self_destruct_video
import eu.tintera.plume.resources.media_sticker
import eu.tintera.plume.resources.media_video
import eu.tintera.plume.resources.notification_new_message
import eu.tintera.plume.resources.notification_pinned
import eu.tintera.plume.resources.service_message_pinned
import co.touchlab.kermit.Logger
import kotlin.concurrent.Volatile
import kotlin.math.abs

/**
 * Textová podoba obsahu zprávy pro **náhled v seznamu chatů**.
 *
 * V seznamu není kam kreslit obrázek, takže i fotka musí mít slovní podobu. Text se bere doslova,
 * u médií se použije popisek, a když ho zpráva nemá, název typu — prázdný řádek u fotky vypadá
 * jako chyba načítání.
 */
internal fun Message.previewText(): UiText =
    previewTextOrNull() ?: UiText(Res.string.content_unsupported)

/**
 * Text zprávy pro **notifikaci**: totéž co [previewText], jen typ, který neumíme popsat, dostane
 * „Nová zpráva". Věta „tenhle typ zprávy zatím neumíme zobrazit" patří do aplikace, kde stojí na
 * místě zprávy; na liště vypadá jako chybové hlášení místo zprávy.
 */
internal fun Message.notificationText(): UiText =
    previewTextOrNull() ?: UiText(Res.string.notification_new_message)

// Omezená zpráva má místo obsahu důvod — i v náhledu a v notifikaci, jinak by tam obsah prosákl.
private fun Message.previewTextOrNull(): UiText? =
    restrictionText?.let(UiText::Raw) ?: content.previewTextOrNull(
        isOutgoing = isOutgoing,
        isChannelPost = isChannelPost,
        selfDestructs = selfDestructType != null,
    )

/** Entity náhledu v seznamu chatů; omezená zpráva má místo obsahu důvod, a ten entity nenese. */
internal fun Message.previewEntities(): List<MessageEntity> =
    if (restrictionText != null) emptyList() else content.previewEntities(selfDestructs = selfDestructType != null)

/**
 * Slovní podoba obsahu, i bez celé zprávy — odpověď na zprávu z jiného chatu nese jen obsah
 * citované zprávy, ne zprávu samotnou. [isOutgoing] je směr zprávy, které obsah patří (podle
 * něj se jmenuje zpráva o hovoru); u citace z jiného chatu ho neznáme a je `false`.
 * [isChannelPost] je `Message.isChannelPost` téže zprávy (kanál, nebo skupina, viz [serviceText]).
 * [selfDestructs] = mizející médium (`Message.selfDestructType`); citace ho nezná a je `false`.
 */
internal fun MessageContent.previewTextOrNull(
    isOutgoing: Boolean,
    isChannelPost: Boolean,
    selfDestructs: Boolean = false,
): UiText? = when (val content = this) {
    is MessageText -> UiText.Raw(content.text.text)
    // Mizející médium jménem, ne „Fotka": v seznamu ani v notifikaci by se nepoznalo, že se
    // otevře jen jednou. Popisek k mizejícímu obsahu patří, takže se do náhledu nedává.
    is MessagePhoto -> if (selfDestructs) {
        UiText(Res.string.media_self_destruct_photo)
    } else {
        content.caption.text.orText(Res.string.media_photo)
    }
    is MessageVideo -> if (selfDestructs) {
        UiText(Res.string.media_self_destruct_video)
    } else {
        content.caption.text.orText(Res.string.media_video)
    }
    is MessageAnimation -> content.caption.text.orText(Res.string.content_gif)
    is MessageDocument -> content.caption.text.orText(Res.string.content_file)
    // Skladba se v náhledu pozná jménem — „Audio" u každé by v seznamu nic neřeklo.
    is MessageAudio -> content.caption.text.ifBlank {
        listOf(content.audio.performer, content.audio.title).filter { it.isNotBlank() }.joinToString(" – ")
    }.orText(Res.string.content_audio)
    is MessageVoiceNote -> content.caption.text.orText(Res.string.conversation_voice_message)
    is MessageSticker -> content.sticker.emoji.orText(Res.string.media_sticker)
    is MessageAnimatedEmoji -> UiText.Raw(content.emoji)
    is MessageDice -> UiText.Raw(content.diceText())
    // Směr a zmeškání zná jen zpráva (`isOutgoing`), obsah sám ne — proto tady, ne v `describe`.
    is MessageCall -> UiText(content.outcome(isOutgoing).title(content.isVideo))
    is MessageGroupCall -> UiText(content.outcome(isOutgoing).title(content.isVideo, isGroup = true, isActive = content.isActive))
    else -> content.describe(isChannelPost)
}

/**
 * Vlastní emoji v náhledu ([previewTextOrNull]) — rozsahy platí pro týž text, protože náhled
 * bere text zprávy nebo popisek doslova. Spoilery jdou s nimi: emoji ve skrytém úseku má zůstat
 * náhradním znakem, jako v bublině (`appendWithCustomEmoji`). Ostatní formátování náhled nenese.
 *
 * U popisku, který je prázdný, stojí v náhledu název typu („Fotka") — a prázdný popisek žádné
 * entity nemá, takže se rozsahy s textem nerozejdou.
 */
internal fun MessageContent.previewEntities(selfDestructs: Boolean = false): List<MessageEntity> {
    val formatted = when (this) {
        is MessageText -> text
        // Mizející médium má v náhledu název, ne popisek ([previewTextOrNull]).
        is MessagePhoto -> caption.takeIf { !selfDestructs } ?: return emptyList()
        is MessageVideo -> caption.takeIf { !selfDestructs } ?: return emptyList()
        is MessageAnimation -> caption
        is MessageDocument -> caption
        is MessageAudio -> caption
        is MessageVoiceNote -> caption
        else -> return emptyList()
    }
    return formatted.toBody().entities.filter {
        it.type is MessageEntityType.CustomEmoji || it.type == MessageEntityType.Spoiler
    }
}

/**
 * Text notifikace z obsahu **rozšifrovaného z pushe** (`NotificationTypeNewPushMessage`).
 *
 * Push nese vlastní, chudší podobu obsahu než zpráva — TDLib ji vydá, když samotnou zprávu ještě
 * nemá. Slova jsou stejná jako v [previewText], ať notifikace nevypadá jinak podle toho, kudy
 * přišla. Co push nepopisuje nijak užitečně (servisní události), dostane obecné
 * „Nová zpráva" — pravdivé a bez doslovného názvu typu.
 */
internal fun PushMessageContent.previewText(): UiText {
    val content = contentText()
    return when {
        !isPinned() -> content ?: UiText(Res.string.notification_new_message)
        content != null -> UiText(Res.string.notification_pinned, content)
        else -> UiText(Res.string.service_message_pinned)
    }
}

/**
 * Připnutí zprávy chodí v pushi jako TENTÝŽ typ obsahu, jen s `isPinned` — „Alice připnula
 * Fotku" je `PushMessageContentPhoto(isPinned = true)`. Dokud se příznak nečetl, ukázala
 * notifikace připnutou zprávu jako novou, i se starým textem. Příznak nese jen dvacet typů,
 * proto výčet: ostatní připnout nejde.
 */
private fun PushMessageContent.isPinned(): Boolean = when (this) {
    is PushMessageContentText -> isPinned
    is PushMessageContentPhoto -> isPinned
    is PushMessageContentVideo -> isPinned
    is PushMessageContentAnimation -> isPinned
    is PushMessageContentDocument -> isPinned
    is PushMessageContentAudio -> isPinned
    is PushMessageContentVoiceNote -> isPinned
    is PushMessageContentVideoNote -> isPinned
    is PushMessageContentSticker -> isPinned
    is PushMessageContentPoll -> isPinned
    is PushMessageContentChecklist -> isPinned
    is PushMessageContentLocation -> isPinned
    is PushMessageContentContact -> isPinned
    is PushMessageContentGame -> isPinned
    is PushMessageContentGameScore -> isPinned
    is PushMessageContentInvoice -> isPinned
    is PushMessageContentStory -> isPinned
    is PushMessageContentPaidMedia -> isPinned
    is PushMessageContentGiveaway -> isPinned
    is PushMessageContentHidden -> isPinned
    else -> false
}

/** Popis obsahu z pushe, nebo `null`, když ho popsat neumíme. */
private fun PushMessageContent.contentText(): UiText? = when (this) {
    is PushMessageContentText -> UiText.Raw(text)
    // `isSecret` je v pushi mizející médium; slova jako u zprávy ([previewTextOrNull]).
    is PushMessageContentPhoto -> if (isSecret) UiText(Res.string.media_self_destruct_photo) else caption.orText(Res.string.media_photo)
    is PushMessageContentVideo -> if (isSecret) UiText(Res.string.media_self_destruct_video) else caption.orText(Res.string.media_video)
    is PushMessageContentAnimation -> caption.orText(Res.string.content_gif)
    is PushMessageContentDocument -> UiText(Res.string.content_file)
    is PushMessageContentAudio -> UiText(Res.string.content_audio)
    is PushMessageContentVoiceNote -> UiText(Res.string.conversation_voice_message)
    is PushMessageContentVideoNote -> UiText(Res.string.content_video_note)
    is PushMessageContentSticker -> emoji.orText(Res.string.media_sticker)
    is PushMessageContentPoll -> UiText(Res.string.content_poll, question)
    is PushMessageContentChecklist -> UiText(Res.string.content_checklist, title)
    is PushMessageContentLocation ->
        UiText(if (isLive) Res.string.content_live_location else Res.string.content_location)
    is PushMessageContentContact -> UiText(Res.string.content_contact, name)
    is PushMessageContentGame -> UiText(Res.string.content_game, title)
    is PushMessageContentStory -> UiText(Res.string.content_story)
    is PushMessageContentPaidMedia -> UiText(Res.string.content_paid)
    is PushMessageContentMediaAlbum -> UiText(Res.string.content_album)
    is PushMessageContentGift, is PushMessageContentUpgradedGift, is PushMessageContentPremiumGiftCode ->
        UiText(Res.string.content_gift)
    is PushMessageContentGiveaway -> UiText(Res.string.content_giveaway)
    // Push nese jen přijaté převody a částku jako text od serveru (TDLib `MESSAGE_GRAM_TRANSFER`
    // ji předá beze změny). Jestli v ní jednotka je, z TDLib poznat nejde — přidá se jen, když chybí.
    is PushMessageContentTonWalletTransfer -> tonTransferText(
        amount = amount.trim().let { if (it.contains("TON", ignoreCase = true)) UiText.Raw(it) else UiText(Res.string.ton_amount, it) },
        isOutgoing = false,
        comment = comment,
    )
    is PushMessageContentTonConnectRequest -> tonConnectRequestText(dappName)
    else -> pushServiceText()
}

/** Převod TON slovy, stejně ve zprávě i v pushi: „Přijato 1,5 TON: Za pizzu". */
private fun tonTransferText(amount: UiText, isOutgoing: Boolean, comment: String?): UiText {
    val text = comment?.trim().orEmpty()
    return when {
        text.isEmpty() -> UiText(if (isOutgoing) Res.string.content_ton_sent else Res.string.content_ton_received, amount)
        isOutgoing -> UiText(Res.string.content_ton_sent_comment, amount, text)
        else -> UiText(Res.string.content_ton_received_comment, amount, text)
    }
}

/** Žádost aplikace (dApp) přes TON Connect; jméno aplikace TDLib nemusí znát. */
private fun tonConnectRequestText(dappName: String): UiText =
    dappName.trim().ifEmpty { null }?.let { UiText(Res.string.content_ton_connect_request_named, it) }
        ?: UiText(Res.string.content_ton_connect_request)

/**
 * Servisní událost z pushe. Věta nemá podmět: řádek notifikace už nese jméno odesílatele
 * („Jana: Změnil(a) název na „Výlet""), a to je u těchhle událostí právě ten, kdo je udělal.
 * Dřív dostaly všechny „Nová zpráva", i když push nese, co se stalo.
 */
private fun PushMessageContent.pushServiceText(): UiText? = when (this) {
    is PushMessageContentBasicGroupChatCreate -> UiText(Res.string.push_group_created)
    is PushMessageContentChatChangeTitle -> UiText(Res.string.push_title_changed, title)
    is PushMessageContentChatChangePhoto -> UiText(Res.string.push_photo_changed)
    is PushMessageContentChatAddMembers -> when {
        isReturned -> UiText(Res.string.push_member_returned)
        isCurrentUser -> UiText(Res.string.push_added_you)
        else -> UiText(Res.string.push_member_added, memberName)
    }
    is PushMessageContentChatDeleteMember -> when {
        isLeft -> UiText(Res.string.push_member_left)
        isCurrentUser -> UiText(Res.string.push_removed_you)
        else -> UiText(Res.string.push_member_removed, memberName)
    }
    is PushMessageContentChatJoinByLink -> UiText(Res.string.push_joined_by_link)
    is PushMessageContentChatJoinByRequest -> UiText(Res.string.push_joined_by_request)
    is PushMessageContentChatSetBackground -> UiText(Res.string.service_background_changed)
    is PushMessageContentChatSetTheme -> UiText(if (name.isBlank()) Res.string.service_theme_reset else Res.string.service_theme_changed)
    is PushMessageContentChecklistTasksAdded -> UiText(Res.string.service_checklist_tasks_added)
    is PushMessageContentChecklistTasksDone -> UiText(Res.string.service_checklist_tasks_done)
    is PushMessageContentContactRegistered -> UiText(Res.string.push_contact_joined)
    is PushMessageContentInviteVideoChatParticipants ->
        UiText(if (isCurrentUser) Res.string.push_video_chat_invited_you else Res.string.service_video_chat_invite)
    is PushMessageContentMessageForwards -> UiText(Res.plurals.push_forwarded_messages, totalCount, totalCount)
    is PushMessageContentPollOptionAdded ->
        text.ifBlank { null }?.let { UiText(Res.string.service_poll_option_added_named, quotedTask(it)) }
            ?: UiText(Res.string.service_poll_option_added)
    is PushMessageContentProximityAlertTriggered -> UiText(Res.string.service_nearby, distanceUiText(distance))
    // Částku posílá push už naformátovanou („$5"), přeložit ji není co.
    is PushMessageContentRecurringPayment -> UiText(Res.string.push_recurring_payment, amount)
    is PushMessageContentScreenshotTaken -> UiText(Res.string.push_screenshot)
    is PushMessageContentSuggestProfilePhoto -> UiText(Res.string.content_suggested_photo)
    is PushMessageContentSuggestBirthdate -> UiText(Res.string.content_suggested_birthdate)
    is PushMessageContentVideoChatStarted -> UiText(Res.string.service_video_chat_started)
    is PushMessageContentVideoChatEnded -> UiText(Res.string.service_video_chat_ended)
    is PushMessageContentGameScore -> UiText(Res.string.push_game_score, score.grouped(), title)
    else -> null
}

/**
 * Jednořádkový popis obsahu, který nemá vlastní text.
 *
 * Poslední záchrana pro typy, které neumíme vykreslit. Dřív tu bylo pro všechny jedno „Zpráva",
 * takže se anketa, poloha i systémové oznámení tvářily stejně — a hlavně to vypadalo jako chyba
 * načítání, ne jako „tohle zatím neumíme".
 */
private fun MessageContent.describe(isChannelPost: Boolean): UiText? = when (this) {
    is MessageVideoNote -> UiText(Res.string.content_video_note)
    is MessagePoll -> UiText(Res.string.content_poll, poll.question.text)
    is MessageChecklist -> UiText(Res.string.content_checklist, list.title.text)
    is MessageLocation -> UiText(Res.string.content_location)
    is MessageLiveLocation -> UiText(Res.string.content_live_location)
    is MessageVenue -> UiText(Res.string.content_venue, venue.title)
    is MessageContact -> UiText(Res.string.content_contact, "${contact.firstName} ${contact.lastName}".trim())
    is MessageGame -> UiText(Res.string.content_game, game.title)
    // Sázková kostka (TDLib 15): jako `MessageDice`, ale s vkladem v TON. Hod se sázkou kreslí
    // příloha (`toDiceMedia`); tenhle popis je pro náhled v seznamu a pro hod bez animace.
    is MessageStakeDice -> UiText(Res.string.content_stake_dice)
    // Převod z peněženky TON (TDLib 1.8.68). Peněženku Plume nemá, převod jen popíše; záporná
    // částka je odchozí. Zašifrovaný komentář je nečitelný bez klíče peněženky, takže se vynechá.
    is MessageTonWalletTransfer -> tonTransferText(
        amount = UiText(Res.string.ton_amount, tonAmount(abs(amount))),
        isOutgoing = amount < 0,
        comment = comment.takeUnless { isCommentEncrypted },
    )
    is MessageTonConnectRequest -> tonConnectRequestText(dappName)
    is MessageInvoice -> UiText(Res.string.content_invoice, productInfo.title)
    is MessageStory -> UiText(Res.string.content_story)
    is MessagePaidMedia -> caption.text.orText(Res.string.content_paid)
    is MessageGiveaway -> UiText(Res.string.content_giveaway)
    is MessageGiveawayWinners -> UiText(Res.string.content_giveaway_winners)
    // Článek složený z bloků (TDLib 15). Kreslit ho neumíme, jeho text ano — prázdný je jen
    // článek ze samých obrázků.
    is MessageRichMessage -> message.blocks.plainText().orText(Res.string.content_rich_message)

    // Klient je starší než server. Doslovný název typu by uživateli neřekl nic; v aplikaci
    // dostane „neumíme zobrazit", v notifikaci „Nová zpráva" — stejně jako neznámý typ.
    is MessageUnsupported -> {
        reportUnknownContent(this)
        null
    }

    // Systémová oznámení mají vlastní formulace i vykreslení. `null` = typ neumíme popsat
    // a jakou větou to říct, rozhodne volající (v aplikaci jinou než v notifikaci).
    else -> serviceText(isChannelPost) ?: run {
        reportUnknownContent(this)
        null
    }
}

private val logger = Logger.withTag("MessageContent")

// Neměnná množina místo zámku: volá se z více vláken a commonMain zámek nemá. Souběh znamená
// nanejvýš jeden řádek logu navíc, poškozenou kolekci ne.
@Volatile
private var reportedUnknownContent = emptySet<String>()

/**
 * Zapíše název typu, který neumíme popsat — jednou za typ a běh procesu, jinak by ho každé
 * překreslení seznamu logovalo znovu. Obsah zprávy do logu nejde, jen typ.
 *
 * **Název se bere z `toString()`, ne z `::class.simpleName`.** R8 třídy přejmenovává (viz „R8"
 * v docs/poznatky/release.md), `toString()` z TDLib ale název nese jako řetězcový literál — ten přejmenování
 * přežije. `w`, ne `i`: log jde do kontextu hlášení v Crashlytics a podle něj přibude popis.
 */
private fun reportUnknownContent(content: MessageContent) {
    val name = content.toString().substringBefore('(')
    if (name in reportedUnknownContent) return
    reportedUnknownContent = reportedUnknownContent + name
    logger.w { "Neznámý typ obsahu zprávy: $name" }
}

/**
 * Holý text článku: nadpisy a odstavce po řádcích. Obrázky, tabulky a vložený obsah se vynechají,
 * text je tu jen náhled.
 */
private fun Array<PageBlock>.plainText(): String = mapNotNull { block ->
    when (block) {
        is PageBlockTitle -> block.title
        is PageBlockSubtitle -> block.subtitle
        is PageBlockHeader -> block.header
        is PageBlockSubheader -> block.subheader
        is PageBlockSectionHeading -> block.text
        is PageBlockKicker -> block.kicker
        is PageBlockParagraph -> block.text
        else -> null
    }?.plainText()?.trim()?.takeIf { it.isNotEmpty() }
}.joinToString("\n")

/**
 * Formátování se zahazuje, text zůstává. **Spoiler se vynechá** — náhled by ho prozradil; stejně
 * tak co text nenese (ikona, tlačítko, vzorec).
 */
internal fun RichText.plainText(): String = when (this) {
    is RichTextPlain -> text
    is RichTexts -> texts.joinToString("") { it.plainText() }
    is RichTextBold -> text.plainText()
    is RichTextItalic -> text.plainText()
    is RichTextUnderline -> text.plainText()
    is RichTextStrikethrough -> text.plainText()
    is RichTextFixed -> text.plainText()
    is RichTextMarked -> text.plainText()
    is RichTextSubscript -> text.plainText()
    is RichTextSuperscript -> text.plainText()
    is RichTextUrl -> text.plainText()
    is RichTextEmailAddress -> text.plainText()
    is RichTextPhoneNumber -> text.plainText()
    is RichTextBankCardNumber -> text.plainText()
    is RichTextTonAddress -> text.plainText()
    is RichTextMention -> text.plainText()
    is RichTextMentionName -> text.plainText()
    is RichTextHashtag -> text.plainText()
    is RichTextCashtag -> text.plainText()
    is RichTextBotCommand -> text.plainText()
    is RichTextAnchorLink -> text.plainText()
    is RichTextReference -> text.plainText()
    is RichTextReferenceLink -> text.plainText()
    is RichTextDateTime -> text.plainText()
    is RichTextDiff -> text.plainText()
    is RichTextCustomEmoji -> alternativeText
    else -> ""
}

/**
 * Text zprávy pro **konverzaci**, i s formátováním.
 *
 * Na rozdíl od [previewText] vrací prázdno, když zpráva žádný vlastní text nemá — v bublině je
 * vidět samotná fotka a dopisovat pod ni slovo „Fotka" by byl šum. Typ přílohy nese [messageMedia].
 *
 * Text a entity vrací **jedna funkce**, ne dvě. Entity jsou rozsahy do konkrétního řetězce;
 * kdyby se každá půlka rozhodovala zvlášť, stačilo by přidat větev do jedné z nich a rozsahy
 * by ukazovaly do jiného textu, než který se vykreslí. Stejný důvod, proč o servisních zprávách
 * rozhoduje jediná `serviceText()`.
 */
internal suspend fun Message.captionBody(
    /** Celý článek, když ho repository dočetla (`getFullRichMessage`); jinak se bere, co přišlo. */
    fullArticle: RichMessage? = null,
    hideSensitive: Boolean = false,
): MessageBody = when (val content = this.content) {
    is MessageText -> content.text.toBody()
    is MessagePhoto -> content.caption.toBody()
    is MessageVideo -> content.caption.toBody()
    is MessageAnimation -> content.caption.toBody()
    is MessageDocument -> content.caption.toBody()
    is MessageAudio -> content.caption.toBody()
    is MessageVoiceNote -> content.caption.toBody()
    // Samolepka se kreslí, ne popisuje — její emoji by pod obrázkem bylo zdvojení.
    is MessageSticker -> MessageBody.Empty

    /*
     * Samotné emoji NENÍ textová zpráva.
     *
     * Telegram ho posílá jako `MessageAnimatedEmoji` s animovanou podobou navíc, takže spadlo
     * do větve `else` a v bublině z něj bylo slovo „Zpráva".
     *
     * Když je animace `.tgs`, kreslí se jako příloha (`MessageMedia.AnimatedEmoji`) a text zůstává
     * prázdný — jinak by pod animací stál ještě jednou jako znak. Bez animace, nebo v jiném formátu
     * (prémiová emoji můžou být cokoli), se vrací unicode znak: ten se vykreslí sám a chytí se
     * na něj vykreslení velkých emoji.
     */
    is MessageAnimatedEmoji ->
        if (content.animationFileId() != null) MessageBody.Empty else MessageBody(content.emoji)

    // Hod s animací je příloha a výsledek je v ní nakreslený; text by ho zdvojil.
    is MessageDice -> if (content.toDiceMedia() != null) MessageBody.Empty else MessageBody(content.diceText())
    // Bez animace zůstává popis „Kostka se sázkou" (`describe`), s ní je hod i sázka v příloze.
    is MessageStakeDice -> if (content.toDiceMedia() != null) MessageBody.Empty else MessageBody(text = "", description = previewText())

    // Typy, které mají v bublině vlastní přílohu s popiskem — text by ho zdvojil.
    is MessageVideoNote, is MessageGame, is MessageInvoice -> MessageBody.Empty
    // Soutěž je karta s výhrou a podmínkami; „Soutěž" jako text by ji jen pojmenovala podruhé.
    is MessageGiveaway, is MessageGiveawayWinners -> MessageBody.Empty
    // Hovor je řádek s ikonou a názvem; text by ho zdvojil.
    is MessageCall, is MessageGroupCall -> MessageBody.Empty
    // Kontakt je karta se jménem a číslem; „Kontakt: Jana" by ho zdvojil.
    is MessageContact -> MessageBody.Empty
    // Mapa s názvem místa pod sebou; „Poloha" jako text by ji jen popisovala podruhé.
    is MessageLocation, is MessageLiveLocation, is MessageVenue -> MessageBody.Empty
    is MessagePaidMedia -> content.caption.toBody()
    // Článek jako text s formátováním; ze samých obrázků je prázdný, pak zůstane náš popis.
    is MessageRichMessage -> (fullArticle ?: content.message).articleBody(hideSensitive)
        .takeIf { it.text.isNotBlank() }
        ?: MessageBody(text = "", description = previewText())

    /*
     * Zbytek nemá vlastní text, jen náš popis — a ten žádné entity nenese.
     *
     * Připojit k němu entity z původní zprávy by znamenalo rozsahy posunuté do jiného řetězce:
     * v lepším případě nesmyslně obarvené slovo, v horším pád na indexu mimo text.
     */
    else -> MessageBody(text = "", description = previewText())
}

/**
 * Článek jako text s formátováním; média v něm jsou štítky („[Fotka]") na svém místě. Sdílí ho
 * zpráva v konverzaci ([captionBody]) i rozepsaná odpověď bota ([pendingBody]).
 */
internal suspend fun RichMessage.articleBody(hideSensitive: Boolean): MessageBody =
    blocks.toRichBody(
        RichMediaLabels(
            photo = getString(Res.string.media_photo),
            video = getString(Res.string.media_video),
            gif = getString(Res.string.content_gif),
            audio = getString(Res.string.content_audio),
            voice = getString(Res.string.conversation_voice_message),
            file = getString(Res.string.content_file),
        ),
        isFull = isFull,
        hideSensitive = hideSensitive,
    )

/**
 * Text rozepsané odpovědi bota. TDLib ji posílá jen jako `messageText` nebo `messageRichMessage`;
 * cokoli jiného je prázdné, a taková odpověď se pak nekreslí.
 */
internal suspend fun MessageContent.pendingBody(): MessageBody = when (this) {
    is MessageText -> text.toBody()
    is MessageRichMessage -> message.articleBody(hideSensitive = false)
    else -> MessageBody.Empty
}

/**
 * Text zprávy i s rozsahy formátování.
 *
 * Drží se pohromadě proto, aby se ty dvě věci nemohly rozejít — entity bez svého textu jsou
 * jen dvojice čísel a špatně spárované ukazují jinam, než by měly.
 */
data class MessageBody(
    val text: String,
    val entities: List<MessageEntity> = emptyList(),
    /**
     * Náš popis zprávy, která vlastní text nemá (servisní oznámení, „Anketa: otázka", „Tenhle
     * typ zatím neumíme zobrazit"). [text] je pak prázdný: popis se překládá až tam, kde se
     * kreslí, jinak by po změně jazyka zůstal v tom starém.
     */
    val description: UiText? = null,
    /** U článku: kde v [text] stojí média a jestli je článek celý. Offsety platí jen pro [text]. */
    val article: Article? = null,
) {
    companion object {
        val Empty = MessageBody(text = "")
    }
}

internal fun FormattedText.toBody(): MessageBody =
    MessageBody(text = text, entities = entities.mapNotNull { it.toEntity(text) })

/**
 * Převod jedné entity z TDLib.
 *
 * `null` znamená „tenhle typ zatím nevykreslujeme" — text po něm zůstane obyčejný. Přeskakují se
 * cashtagy: obarvit je umíme, ale kliknutí by nemělo kam vést.
 * Viz [MessageEntityType].
 */
private fun TextEntity.toEntity(text: String): MessageEntity? {
    val mapped = when (val type = this.type) {
        // Adresa je přímo v textu. Bez schématu by ji `UrlOpener` nevzal.
        is TextEntityTypeUrl -> MessageEntityType.Link(text.range(this).withScheme())
        // Text je popisek, adresa přišla zvlášť — a ta už schéma má vždycky.
        is TextEntityTypeTextUrl -> MessageEntityType.Link(type.url)
        is TextEntityTypeEmailAddress -> MessageEntityType.Link("mailto:${text.range(this)}")
        is TextEntityTypePhoneNumber -> MessageEntityType.Link("tel:${text.range(this)}")
        is TextEntityTypeMention -> MessageEntityType.Mention(text.range(this).removePrefix("@"))
        is TextEntityTypeMentionName -> MessageEntityType.MentionUser(type.userId)
        is TextEntityTypeHashtag -> MessageEntityType.Hashtag(text.range(this))
        is TextEntityTypeBotCommand -> MessageEntityType.BotCommand(text.range(this))
        is TextEntityTypeMediaTimestamp -> MessageEntityType.MediaTimestamp(type.mediaTimestamp)
        // Bez formátu má text zůstat, jak ho pisatel napsal.
        is TextEntityTypeDateTime -> type.formattingType?.let {
            MessageEntityType.DateTime(unixTime = type.unixTime.toLong(), format = it.toFormat())
        }

        is TextEntityTypeBold -> MessageEntityType.Bold
        is TextEntityTypeItalic -> MessageEntityType.Italic
        is TextEntityTypeUnderline -> MessageEntityType.Underline
        is TextEntityTypeStrikethrough -> MessageEntityType.Strikethrough
        // Inline kód i celý blok se kreslí stejně; blok stojí na vlastních řádcích.
        is TextEntityTypeCode -> MessageEntityType.Code()
        is TextEntityTypePre -> MessageEntityType.Code(block = true)
        is TextEntityTypePreCode -> MessageEntityType.Code(block = true, language = type.language)
        is TextEntityTypeSpoiler -> MessageEntityType.Spoiler
        is TextEntityTypeCustomEmoji -> MessageEntityType.CustomEmoji(type.customEmojiId)
        // Sbalitelná citace se zatím kreslí celá; příznak se drží kvůli „Zobrazit víc".
        is TextEntityTypeBlockQuote -> MessageEntityType.Quote(expandable = false)
        is TextEntityTypeExpandableBlockQuote -> MessageEntityType.Quote(expandable = true)

        else -> null
    }

    return mapped?.let { MessageEntity(offset = offset, length = length, type = it) }
}

internal fun DateTimeFormattingType.toFormat(): DateTimeFormat = when (this) {
    is DateTimeFormattingTypeRelative -> DateTimeFormat.Relative
    is DateTimeFormattingTypeAbsolute -> DateTimeFormat.Absolute(
        time = timePrecision.toPrecision(),
        date = datePrecision.toPrecision(),
        dayOfWeek = showDayOfWeek,
    )
}

private fun DateTimePartPrecision.toPrecision(): DateTimePrecision = when (this) {
    is DateTimePartPrecisionNone -> DateTimePrecision.None
    is DateTimePartPrecisionShort -> DateTimePrecision.Short
    is DateTimePartPrecisionLong -> DateTimePrecision.Long
}

/**
 * Úsek textu, na který entita ukazuje.
 *
 * Rozsahy se ořezávají do délky textu. Server posílá platné hodnoty, ale `substring` mimo rozsah
 * je pád aplikace kvůli jedné zprávě — a to je cena, kterou za důvěru platit nechceme.
 */
private fun String.range(entity: TextEntity): String {
    val start = entity.offset.coerceIn(0, length)
    val end = (entity.offset + entity.length).coerceIn(start, length)
    return substring(start, end)
}

/**
 * Doplní schéma odkazu psanému bez něj.
 *
 * TDLib označí za odkaz i holé `example.com`; `UrlOpener` by z toho na Androidu udělal relativní
 * URI bez schématu a intent by nikam nevedl.
 */
private fun String.withScheme(): String = if (contains("://")) this else "https://$this"

/**
 * Kostka (🎲, 🎯, 🎰 …).
 *
 * Hozená hodnota je součástí *animace*, kterou neumíme přehrát, takže se dopisuje číslem —
 * bez něj by ze zprávy zbylo jen „hodil jsem kostkou" bez výsledku. Nula znamená, že server
 * výsledek ještě neposlal; dorazí updatem.
 */
/**
 * Konec sdílení živé polohy v sekundách od epochy. `livePeriod` se počítá od ODESLÁNÍ zprávy,
 * ne od teď; `0x7FFFFFFF` je sdílení bez konce ([Long.MAX_VALUE]).
 */
internal fun liveLocationUntil(sentAt: Int, livePeriod: Int): Long =
    if (livePeriod == LIVE_FOREVER) Long.MAX_VALUE else sentAt.toLong() + livePeriod

/** `livePeriod` živé polohy sdílené bez konce. */
const val LIVE_FOREVER: Int = 0x7FFFFFFF

private fun MessageDice.diceText(): String = if (value > 0) "$emoji $value" else emoji

/**
 * Animace hodu, nebo `null` — pak zůstává text „🎲 6“.
 *
 * S výsledkem se bere koncová animace (`finalState`), bez něj úvodní. Automat se skládá z pěti
 * vrstev a všechny musí být `.tgs`: bez kterékoli z nich by na obrázku chyběl válec, a hod by
 * ukazoval jiný výsledek, než jaký padl. Koncová animace bez výsledku (nebo výsledek bez ní)
 * nedává smysl, takže se na druhý stav nepadá — radši text.
 */
internal fun MessageDice.toDiceMedia(): MessageMedia.Dice? = diceMedia(
    initialState = initialState,
    finalState = finalState,
    emoji = emoji,
    value = value,
    successFrame = successAnimationFrameNumber,
)

/**
 * Kostka se sázkou (TDLib 15): tentýž hod jako [MessageDice.toDiceMedia], navíc vklad a výhra
 * v TON. Emoji nenese — hází se vždycky kostkou — a snímek oslavy server neposílá.
 */
internal fun MessageStakeDice.toDiceMedia(): MessageMedia.Dice? = diceMedia(
    initialState = initialState,
    finalState = finalState,
    emoji = STAKE_DICE_EMOJI,
    value = value,
    successFrame = 0,
)?.copy(
    stake = MessageMedia.DiceStake(stake = stakeGramAmount, prize = prizeGramAmount.takeIf { value > 0 && it >= 0 }),
)

/** Kostka se sázkou se hází vždycky kostkou; `MessageStakeDice` emoji nenese. */
internal const val STAKE_DICE_EMOJI = "🎲"

private fun diceMedia(
    initialState: DiceStickers?,
    finalState: DiceStickers?,
    emoji: String,
    value: Int,
    successFrame: Int,
): MessageMedia.Dice? {
    val stickers = (if (value > 0) finalState else initialState) ?: return null
    val layers = when (stickers) {
        is DiceStickersRegular -> listOf(stickers.sticker)
        is DiceStickersSlotMachine -> listOf(
            stickers.background,
            stickers.lever,
            stickers.leftReel,
            stickers.centerReel,
            stickers.rightReel,
        )
    }
    if (layers.any { it.format !is StickerFormatTgs }) return null
    return MessageMedia.Dice(
        emoji = emoji,
        value = value,
        layers = layers.map { it.sticker.id },
        successFrame = successFrame.takeIf { value > 0 } ?: 0,
    )
}

/** Náhled, který jde nakreslit jako obrázek — animovaný MPEG-4 Coil neotevře. */
internal fun Thumbnail?.picture(): Thumbnail? = this?.takeIf { it.format !is ThumbnailFormatMpeg4 }

internal fun MessageSelfDestructType.toSelfDestructType(): SelfDestructType = when (this) {
    is MessageSelfDestructTypeImmediately -> SelfDestructType.Immediately
    is MessageSelfDestructTypeTimer -> SelfDestructType.Timer(selfDestructTime)
}

/**
 * Placené médium v doménové podobě.
 *
 * **Zaplacené je obyčejná fotka nebo video** (`PaidMediaPhoto` / `PaidMediaVideo`), a tak se
 * i kreslí — i s prohlížečem a reakcemi. Nezaplacené (`PaidMediaPreview`) jsou jen náhledy
 * s cenou, všech položek (konverzace je kreslí mřížkou). Ze ZAPLACENÝCH víc položek se ukazuje
 * PRVNÍ: prohlížeč i reakce stojí na tom, že jedna zpráva je jedna fotka (alba jsou jinak víc
 * zpráv), a ověřit by to nešlo — zaplatit Plume neumí.
 */
internal fun MessagePaidMedia.toPaidMedia(hideSensitive: Boolean): MessageMedia? {
    val items = media.filterNotNull()
    val first = items.firstOrNull() ?: return null
    val locked = items.any { it !is PaidMediaPhoto && it !is PaidMediaVideo }
    if (!locked) {
        return when (first) {
            is PaidMediaPhoto -> first.photo.toMedia(hasSpoiler = hideSensitive, isSensitive = hideSensitive)
            is PaidMediaVideo -> first.video.toMedia(hasSpoiler = hideSensitive, isSensitive = hideSensitive)
            else -> null
        }
    }
    return MessageMedia.PaidMedia(
        starCount = starCount,
        previews = items.map { item ->
            val preview = item as? PaidMediaPreview
            MessageMedia.PaidPreview(
                width = preview?.width ?: 0,
                height = preview?.height ?: 0,
                durationSeconds = preview?.duration ?: 0,
                minithumbnail = preview?.minithumbnail?.toDomain(),
            )
        },
    )
}

/**
 * Příloha citované zprávy z JINÉHO chatu (`MessageReplyToMessage.content`) — jen kvůli náhledu
 * v citaci. Celý převod ([messageMedia]) stojí na `Message`, kterou odtud nemáme; náhled stačí
 * u druhů, které obrázek nesou. Citlivost cizího chatu nevíme, spoiler ano.
 */
internal fun MessageContent.replyMedia(): MessageMedia? = when (this) {
    is MessagePhoto -> photo.toMedia(hasSpoiler = hasSpoiler, isSensitive = false, isSecret = isSecret, selfDestructType = null)
    is MessageVideo -> video.toMedia(hasSpoiler = hasSpoiler, isSensitive = false, isSecret = isSecret, selfDestructType = null)
    is MessageAnimation -> animation.toMedia().copy(hasSpoiler = hasSpoiler)
    is MessageSticker -> sticker.toMedia()
    else -> null
}

/**
 * Uživatel za sdíleným kontaktem, jak ho zná TDLib: profilovka a jestli jde přidat do kontaktů
 * (je na Telegramu, není to bot ani vlastní účet a v adresáři ještě není).
 */
internal class SharedContactUser(val photoFileId: Int?, val canBeAdded: Boolean)

/**
 * Příloha zprávy, nebo `null` u čistě textové.
 *
 * [hideSensitive] skryje fotku, video i GIF za rozostření jako spoiler (viz [SensitiveContent]);
 * rozhoduje o tom volající, protože k tomu potřebuje volbu z TDLib.
 */
internal suspend fun Message.messageMedia(
    hideSensitive: Boolean = false,
    /** Uživatel sdíleného kontaktu (`MessageContact.userId`); `getUser` je na klientovi, ne tady. */
    contactUser: SharedContactUser? = null,
    /**
     * Jméno odesílatele podle id ve tvaru odesílatele zprávy (kladné uživatel, záporné chat) —
     * kanály soutěže a její výherci. Dohledává se na klientovi, ne tady.
     */
    senderName: suspend (Long) -> String? = { null },
    /**
     * Sdílený příběh podle autora a id (`getStory`); `null` = už není. Dotaz na server, proto
     * na klientovi a s keší — ostatní zprávy na něj nesáhnou.
     */
    story: suspend (posterChatId: Long, storyId: Int) -> dev.g000sha256.tdl.dto.Story? = { _, _ -> null },
): MessageMedia? = when (val content = this.content) {
    is MessagePhoto -> content.photo.toMedia(
        hasSpoiler = content.hasSpoiler || hideSensitive,
        isSensitive = hideSensitive,
        isSecret = content.isSecret,
        selfDestructType = selfDestructType?.toSelfDestructType(),
    )

    is MessageVideo -> content.video.toMedia(
        hasSpoiler = content.hasSpoiler || hideSensitive,
        isSensitive = hideSensitive,
        isSecret = content.isSecret,
        selfDestructType = selfDestructType?.toSelfDestructType(),
    ).copy(
        startSeconds = content.startTimestamp,
        alternatives = content.alternativeVideos.toQualities(),
        // Server jich posílá víc velikostí; větší dlaždice je ostřejší a pořád jde o jeden JPEG.
        storyboard = content.storyboards.maxByOrNull { it.width }?.let { board ->
            VideoStoryboard(
                imageFileId = board.storyboardFile.id,
                mapFileId = board.mapFile.id,
                tileWidth = board.width,
                tileHeight = board.height,
            )
        },
    )

    is MessageAnimation -> content.animation.toMedia().copy(
        hasSpoiler = content.hasSpoiler || hideSensitive,
        isSensitive = hideSensitive,
    )

    is MessagePaidMedia -> content.toPaidMedia(hideSensitive)

    is MessageDocument -> content.document.toMedia(
        fileName = content.document.fileName.ifBlank { getString(Res.string.content_file) },
    ).copy(isSensitive = hideSensitive)

    is MessageAudio -> content.audio.toMedia(fallbackName = getString(Res.string.content_audio))
    is MessageVoiceNote -> content.voiceNote.toMedia(isListened = content.isListened)
    is MessageSticker -> content.sticker.toMedia().copy(isSensitive = hideSensitive)

    // O textu a příloze rozhoduje jedna funkce, `animationFileId` — obě větve se nesmí rozejít.
    is MessageAnimatedEmoji -> content.animationFileId()?.let { fileId ->
        MessageMedia.AnimatedEmoji(animationFileId = fileId, emoji = content.emoji)
    }

    is MessageDice -> content.toDiceMedia()
    is MessageStakeDice -> content.toDiceMedia()

    is MessageVideoNote -> MessageMedia.VideoNote(
        fileId = content.videoNote.video.id,
        thumbnailFileId = content.videoNote.thumbnail.picture()?.file?.id,
        durationSeconds = content.videoNote.duration,
        sizeBytes = content.videoNote.video.expectedSize,
        isViewed = content.isViewed,
        minithumbnail = content.videoNote.minithumbnail?.toDomain(),
        transcript = content.videoNote.speechRecognitionResult?.toTranscript(),
    )

    is MessageGame -> MessageMedia.Product(
        kind = ProductKind.Game,
        title = content.game.title,
        description = content.game.description,
        photo = content.game.photo.toMedia(),
        price = null,
    )

    is MessageLocation -> MessageMedia.Location(content.location.latitude, content.location.longitude)
    is MessageVenue -> MessageMedia.Location(
        latitude = content.venue.location.latitude,
        longitude = content.venue.location.longitude,
        title = content.venue.title.ifBlank { null },
        address = content.venue.address.ifBlank { null },
    )
    is MessageLiveLocation -> MessageMedia.Location(
        latitude = content.location.location.latitude,
        longitude = content.location.location.longitude,
        liveUntil = liveLocationUntil(sentAt = date, livePeriod = content.location.livePeriod),
        heading = content.location.heading.takeIf { it in 1..360 } ?: 0,
        proximityAlertRadius = content.location.proximityAlertRadius.takeIf { canSetProximityAlert() },
    )

    is MessageCall -> MessageMedia.Call(
        outcome = content.outcome(isOutgoing),
        isVideo = content.isVideo,
        durationSeconds = content.duration,
    )

    // Skupinový hovor mimo chat (TDLib 15, i v soukromém chatu). Dřív bublina se slovem
    // „Skupinový hovor" — bez směru, zmeškání a délky, které má hovor ve dvou.
    is MessageGroupCall -> MessageMedia.Call(
        outcome = content.outcome(isOutgoing),
        isVideo = content.isVideo,
        durationSeconds = content.duration,
        isGroup = true,
        isActive = content.isActive,
    )

    is MessageContact -> MessageMedia.Contact(
        name = "${content.contact.firstName} ${content.contact.lastName}".trim(),
        phone = contactPhone(content.contact.phoneNumber),
        userId = content.contact.userId.takeIf { it != 0L },
        photoFileId = contactUser?.photoFileId,
        // Soukromý chat má id uživatele (Uložené zprávy id vlastního účtu).
        isThisChat = content.contact.userId != 0L && content.contact.userId == chatId,
        canAddToContacts = contactUser?.canBeAdded == true,
    )

    is MessageGiveaway -> content.toMedia(senderName)
    is MessageGiveawayWinners -> content.toMedia(senderName)
    is MessageGift -> content.toMedia(senderName)
    is MessageUpgradedGift -> content.toMedia(senderName)
    is MessageRefundedUpgradedGift -> content.toMedia(senderName)
    is MessageGiftedPremium -> content.toMedia(senderName)
    is MessagePremiumGiftCode -> content.toMedia(senderName)
    is MessageGiftedStars -> content.toMedia(senderName)
    is MessageGiveawayPrizeStars -> content.toMedia(senderName)
    is MessageGiftedGrams -> content.toMedia(senderName)
    is MessageStory -> content.toMedia(story(content.storyPosterChatId, content.storyId), senderName)

    is MessageInvoice -> MessageMedia.Product(
        kind = ProductKind.Invoice,
        title = content.productInfo.title,
        description = content.productInfo.description.text,
        photo = content.productInfo.photo?.toMedia(),
        price = Price(amount = content.totalAmount, currency = content.currency),
        isTest = content.isTest,
        isPaid = content.receiptMessageId != 0L,
    )

    else -> null
}

/**
 * Jak hovor dopadl z pohledu uživatele. [isOutgoing] je z TDLib, ne přepsaný jako u Uložených
 * zpráv — hovor sám se sebou neexistuje, takže tam nic nepřepisujeme.
 */
internal fun MessageCall.outcome(isOutgoing: Boolean): CallOutcome =
    callOutcome(isOutgoing = isOutgoing, end = discardReason.toCallEnd())

/** `wasMissed` u skupinového hovoru je „nevzal ani odmítl" — z pohledu volajícího totéž. */
internal fun MessageGroupCall.outcome(isOutgoing: Boolean): CallOutcome =
    callOutcome(isOutgoing = isOutgoing, end = if (wasMissed) CallEnd.Missed else CallEnd.HungUp)

private fun CallDiscardReason.toCallEnd(): CallEnd = when (this) {
    is CallDiscardReasonMissed -> CallEnd.Missed
    is CallDiscardReasonDeclined -> CallEnd.Declined
    is CallDiscardReasonDisconnected -> CallEnd.Disconnected
    is CallDiscardReasonHungUp -> CallEnd.HungUp
    // Hovor přešel do skupinového — mluvilo se, jen jinde.
    is CallDiscardReasonUpgradeToGroupCall -> CallEnd.HungUp
    is CallDiscardReasonEmpty -> CallEnd.Unknown
}

/** Video, jak ho kreslí bublina — sdílí to zpráva s videem i video v anketě. */
internal fun Video.toMedia(
    hasSpoiler: Boolean = false,
    isSensitive: Boolean = false,
    isSecret: Boolean = false,
    selfDestructType: SelfDestructType? = null,
) = MessageMedia.Video(
    fileId = video.id,
    // Náhled ve formátu MPEG-4 (animovaný) obrázek není; Coil ho neotevře.
    thumbnailFileId = thumbnail?.takeIf { it.format !is ThumbnailFormatMpeg4 }?.file?.id,
    width = width,
    height = height,
    durationSeconds = duration,
    hasSpoiler = hasSpoiler,
    isSensitive = isSensitive,
    isSecret = isSecret,
    selfDestructType = selfDestructType,
    supportsStreaming = supportsStreaming,
    mimeType = mimeType,
    minithumbnail = minithumbnail?.toDomain(),
    fileKey = video.remote.uniqueId.ifBlank { null },
    sizeBytes = video.expectedSize,
    hasStickers = hasStickers,
    fileName = fileName.ifBlank { null },
)

/** Přepočítané kvality videa (zpráva i anketa) i s HLS seznamem, viz [VideoQuality]. */
internal fun Array<AlternativeVideo>.toQualities(): List<VideoQuality> = map { alternative ->
    VideoQuality(
        fileId = alternative.video.id,
        width = alternative.width,
        height = alternative.height,
        codec = alternative.codec.lowercase(),
        sizeBytes = alternative.video.expectedSize,
        isOriginal = false,
        hlsFileId = alternative.hlsFile.id,
    )
}

/** Skladba; [fallbackName] je název, když soubor nemá ani titul, ani jméno. Zpráva i článek. */
internal fun Audio.toMedia(fallbackName: String) = MessageMedia.Audio(
    fileId = audio.id,
    title = title.ifBlank { fileName }.ifBlank { fallbackName },
    performer = performer.ifBlank { null },
    fileName = fileName.ifBlank { title }.ifBlank { fallbackName },
    mimeType = mimeType,
    durationSeconds = duration,
    sizeBytes = audio.expectedSize,
    coverFileId = albumCoverThumbnail.picture()?.file?.id,
    coverMinithumbnail = albumCoverMinithumbnail?.toDomain(),
    fileKey = audio.remote.uniqueId.ifBlank { null },
)

/** Hlasovka. V článku se poslech nehlásí, takže je tam vždycky „poslechnutá". */
internal fun VoiceNote.toMedia(isListened: Boolean = true) = MessageMedia.VoiceNote(
    fileId = voice.id,
    durationSeconds = duration,
    waveform = decodeWaveform(waveform),
    isListened = isListened,
    transcript = speechRecognitionResult?.toTranscript(),
    fileKey = voice.remote.uniqueId.ifBlank { null },
)

/** Soubor; [fileName] podává volající, prázdný název se nahrazuje jinde podle místa. */
internal fun Document.toMedia(fileName: String = this.fileName) = MessageMedia.Document(
    fileId = document.id,
    fileName = fileName,
    mimeType = mimeType,
    // `expectedSize` je známá i před stažením; `size` je nula, dokud soubor nedorazí.
    sizeBytes = document.expectedSize,
    thumbnailFileId = thumbnail.picture()?.file?.id,
    thumbnailWidth = thumbnail.picture()?.width ?: 0,
    thumbnailHeight = thumbnail.picture()?.height ?: 0,
)

/**
 * Příloha ankety nebo odpovědi (TDLib 15). Fotka, video, GIF, samolepka a soubor jsou tytéž
 * [MessageMedia] jako u zprávy, takže je nakreslí tentýž kód; odkaz a místo vlastní tvar nemají.
 *
 * `when` bez `else`: nový druh v novější TDLib rozbije překlad, místo aby tiše zmizel.
 */
internal fun PollMedia.toAttachment(): PollAttachment? = when (this) {
    is PollMediaPhoto -> photo.toMedia()?.let { PollAttachment.Media(it) }
    is PollMediaVideo -> PollAttachment.Media(video.toMedia().copy(alternatives = alternativeVideos.toQualities()))
    is PollMediaAnimation -> PollAttachment.Media(animation.toMedia())
    is PollMediaSticker -> PollAttachment.Media(sticker.toMedia())
    is PollMediaDocument -> PollAttachment.Media(document.toMedia())
    is PollMediaAudio -> PollAttachment.Media(
        MessageMedia.Attachment(
            label = UiText.Raw(
                listOf(audio.performer, audio.title).filter { it.isNotBlank() }.joinToString(" – ")
                    .ifBlank { audio.fileName },
            ),
            thumbnailFileId = audio.albumCoverThumbnail.picture()?.file?.id,
            fileId = audio.audio.id,
        ),
    )
    is PollMediaLink -> PollAttachment.Link(
        url = url,
        title = linkPreview?.let { previewText(it.title) ?: previewText(it.siteName) },
        photo = linkPreview?.toDomain()?.photo,
    )
    is PollMediaLocation -> PollAttachment.Media(MessageMedia.Location(location.latitude, location.longitude))
    is PollMediaVenue -> PollAttachment.Media(
        MessageMedia.Location(
            latitude = venue.location.latitude,
            longitude = venue.location.longitude,
            title = previewText(venue.title),
            address = previewText(venue.address),
        ),
    )
}

/**
 * Anketa do domény. Odpovědi jdou v pořadí, v jakém se mají ukázat (`optionOrder`, prázdné =
 * původní), a každá si nese svůj PŮVODNÍ index — tím se hlasuje.
 *
 * Neplatné indexy v `optionOrder` se zahodí a co v něm chybí, se připojí na konec: odpověď,
 * která se kvůli nesouladu pořadí neukáže, by šla přehlédnout i při hlasování.
 */
internal fun MessagePoll.toChatPoll(): ChatPoll {
    val all = poll.options.mapIndexed { index, option ->
        PollAnswer(
            index = index,
            text = option.text.text,
            voters = option.voterCount,
            percentage = option.votePercentage.coerceIn(0, 100),
            isChosen = option.isChosen,
            isBeingChosen = option.isBeingChosen,
            media = option.media?.toAttachment(),
            id = option.id,
        )
    }
    val order = poll.optionOrder.filter { it in all.indices }.distinct()
    val ordered = order.map { all[it] } + all.filterIndexed { index, _ -> index !in order }
    val description = description.toBody()
    return ChatPoll(
        question = poll.question.text,
        media = media?.toAttachment(),
        description = description.text,
        descriptionEntities = description.entities,
        options = ordered,
        totalVoters = poll.totalVoterCount,
        isAnonymous = poll.isAnonymous,
        multipleAnswers = poll.allowsMultipleAnswers,
        allowsRevoting = poll.allowsRevoting,
        isClosed = poll.isClosed,
        closeDate = poll.closeDate,
        canSeeResults = poll.canSeeResults,
        canGetVoters = poll.canGetVoters,
        quiz = (poll.type as? PollTypeQuiz)?.let { quiz ->
            val explanation = quiz.explanation.toBody()
            PollQuiz(
                correct = quiz.correctOptionIds.toSet(),
                explanation = explanation.text,
                explanationEntities = explanation.entities,
                explanationMedia = quiz.explanationMedia?.toAttachment(),
            )
        },
        restriction = poll.voteRestrictionReason?.toRestriction(),
        canAddOption = canAddOption && !poll.isClosed,
    )
}

/**
 * Seznam úkolů do domény. Kdo úkol odškrtl, je tu jen id ([ChecklistItem.doneById]); jméno
 * a fotku dohledá repository z keše odesílatelů, jako u tváří hlasujících v anketě.
 */
internal fun MessageChecklist.toChatChecklist(): ChatChecklist {
    val title = list.title.toBody()
    return ChatChecklist(
        title = title.text,
        titleEntities = title.entities,
        tasks = list.tasks.map { task ->
            val text = task.text.toBody()
            ChecklistItem(
                id = task.id,
                text = text.text,
                entities = text.entities,
                doneAt = task.completionDate,
                doneById = task.completedBy?.plainId() ?: 0,
            )
        },
        othersCanAddTasks = list.othersCanAddTasks,
        othersCanMarkTasksAsDone = list.othersCanMarkTasksAsDone,
        canAddTasks = list.canAddTasks,
        canMarkTasksAsDone = list.canMarkTasksAsDone,
    )
}

/** `when` bez `else`: nový důvod v novější TDLib rozbije překlad, místo aby tiše zmizel. */
private fun PollVoteRestrictionReason.toRestriction(): PollRestriction = when (this) {
    is PollVoteRestrictionReasonClosed -> PollRestriction.Closed
    is PollVoteRestrictionReasonMembershipRequired -> PollRestriction.MembershipRequired
    is PollVoteRestrictionReasonCountryRestricted -> PollRestriction.CountryRestricted
    is PollVoteRestrictionReasonScheduled, is PollVoteRestrictionReasonYetUnsent -> PollRestriction.NotSent
    is PollVoteRestrictionReasonOther -> PollRestriction.Other
}

/**
 * Samolepka na to, co se dá vykreslit.
 *
 * Coil umí `.webp`; `.tgs` je gzipovaná Lottie animace a `.webm` video — ani jedno se nedá poslat
 * do obrázkové knihovny. U nich zbývá statický náhled, který TDLib k samolepce dává, takže
 * z animované samolepky je aspoň nehybný obrázek místo slova „Samolepka".
 *
 * Náhled se bere jen v rastrovém formátu: `ThumbnailFormatTgs` a `Mpeg4` mají tentýž problém
 * jako samotná samolepka, takže by se jen přeneslo o úroveň níž.
 */
/**
 * Smí si odesílatel u téhle živé polohy nastavit upozornění na blízkost? Jen u vlastní zprávy
 * (poloměr cizí polohy TDLib neposílá) a ne v kanálu ani v Uložených zprávách — tam ho TDLib
 * nepovolí („Can't be enabled in direct messages chats, channels and Saved Messages").
 * Uložené zprávy se poznají tak, že chat je vlastní účet odesílatele.
 */
internal fun Message.canSetProximityAlert(): Boolean =
    isOutgoing && !isChannelPost && (senderId as? MessageSenderUser)?.userId != chatId

/**
 * Animace emoji, pokud je v přehratelném formátu.
 *
 * Společné rozhodnutí pro `captionBody()` i `messageMedia()`: kdyby každá větev rozhodovala sama,
 * mohla by zpráva skončit bez textu i bez přílohy, nebo s obojím.
 */
private fun MessageAnimatedEmoji.animationFileId(): Int? =
    animatedEmoji.sticker?.takeIf { it.format is StickerFormatTgs }?.sticker?.id

internal fun Sticker.toMedia(): MessageMedia.Sticker {
    val rasterThumbnail = thumbnail?.takeIf {
        it.format is ThumbnailFormatWebp ||
            it.format is ThumbnailFormatJpeg ||
            it.format is ThumbnailFormatPng
    }

    return MessageMedia.Sticker(
        stickerFileId = sticker.id,
        setId = setId,
        fileId = sticker.id.takeIf { format is StickerFormatWebp },
        animationFileId = sticker.id.takeIf { format is StickerFormatTgs },
        thumbnailFileId = rasterThumbnail?.file?.id,
        videoFileId = sticker.id.takeIf { format is StickerFormatWebm },
        width = width,
        height = height,
        emoji = emoji,
    )
}

/**
 * Obálka sady (`thumbnail` u `StickerSet` / `StickerSetInfo`, 100 × 100) ke kreslení jako
 * samolepka. Autor ji nastavit nemusí — pak je `null` a obálkou je první samolepka sady.
 */
internal fun Thumbnail.toSetCover(setId: Long): MessageMedia.Sticker? {
    val format = format
    if (format !is ThumbnailFormatWebp && format !is ThumbnailFormatPng &&
        format !is ThumbnailFormatTgs && format !is ThumbnailFormatWebm
    ) return null
    return MessageMedia.Sticker(
        stickerFileId = file.id,
        setId = setId,
        fileId = file.id.takeIf { format is ThumbnailFormatWebp || format is ThumbnailFormatPng },
        animationFileId = file.id.takeIf { format is ThumbnailFormatTgs },
        thumbnailFileId = null,
        videoFileId = file.id.takeIf { format is ThumbnailFormatWebm },
        width = width,
        height = height,
        emoji = "",
    )
}

/**
 * Náhled odkazu, nebo `null`.
 *
 * Obrázek se bere jen z druhů, které nějaký nesou jako fotku: článek, fotka, obal videa, náhled
 * vloženého přehrávače. Ostatní (samolepka, hlasovka, chat, dárek…) mají jiné soubory, které
 * neumíme vykreslit; náhled u nich zůstane textový. Zcela prázdný náhled se zahodí — rámeček
 * bez obsahu by vypadal jako chyba načítání.
 */
internal fun Message.linkPreview(hideSensitive: Boolean = false): LinkPreview? =
    (content as? MessageText)?.linkPreview?.toDomain()?.let { preview ->
        // Obrázek citlivé zprávy se skrývá jako fotka se spoilerem — jinak by ho náhled odkazu
        // ukázal ostrý, zatímco stejná fotka poslaná přímo je rozostřená.
        if (hideSensitive) preview.copy(photo = preview.photo?.copy(hasSpoiler = true, isSensitive = true)) else preview
    }

/**
 * Náhled od TDLib v doménové podobě. Společné pro přijatou zprávu i náhled při psaní
 * (`getLinkPreview`), aby obojí vypadalo stejně a prázdný náhled se zahodil na jednom místě.
 */
internal fun TdlLinkPreview.toDomain(): LinkPreview? {
    val preview = this

    val photo = preview.type.previewPhoto()
    val stickers = when (val type = preview.type) {
        is LinkPreviewTypeStickerSet -> type.stickers.filterNotNull().take(LINK_PREVIEW_STICKERS).map { it.toMedia() }
        is LinkPreviewTypeSticker -> listOf(type.sticker.toMedia())
        else -> emptyList()
    }
    val target = preview.type.target()

    val result = LinkPreview(
        url = preview.url,
        siteName = previewText(preview.siteName),
        title = previewText(preview.title),
        // Album popis nemívá, jen text alba (`caption`) — ten se ukáže místo něj.
        description = previewText(preview.description.text) ?: (preview.type as? LinkPreviewTypeAlbum)?.caption?.let(::previewText),
        photo = photo,
        largePhoto = preview.showLargeMedia,
        showAboveText = preview.showAboveText,
        canChangeSize = preview.hasLargeMedia,
        isVideo = preview.type is LinkPreviewTypeVideo || preview.type is LinkPreviewTypeEmbeddedVideoPlayer,
        instantView = preview.instantViewVersion > 0,
        target = target,
        stickers = stickers,
    )
    if (result.siteName == null && result.title == null && result.description == null && photo == null &&
        stickers.isEmpty()
    ) {
        return null
    }
    return result
}

/**
 * Obrázek náhledu: fotka stránky (článek, fotka, obal videa, vložený přehrávač), u odkazu na
 * Telegram profilovka chatu nebo obrázek mini aplikace. Profilovka nese tytéž `PhotoSize` jako
 * fotka ([photoMedia]).
 */
internal fun LinkPreviewType.previewPhoto(): MessageMedia.Photo? = when (this) {
    is LinkPreviewTypeArticle -> photo?.toMedia()
    is LinkPreviewTypePhoto -> photo?.toMedia()
    // Bez obalu aspoň náhled videa (snímek, který server k videu má).
    is LinkPreviewTypeVideo -> cover?.toMedia() ?: video.thumbnail?.toPreviewPhoto(video.minithumbnail)
    // GIF, soubor a album (odkaz na zprávu s médiem, přímý odkaz na soubor): náhled, který k nim
    // server posílá. GIF zůstane stát — animace by běžela v každé zprávě s odkazem.
    is LinkPreviewTypeAnimation -> animation.thumbnail?.toPreviewPhoto(animation.minithumbnail)
    is LinkPreviewTypeDocument -> document.thumbnail?.toPreviewPhoto(document.minithumbnail)
    is LinkPreviewTypeAlbum -> media.filterNotNull().firstNotNullOfOrNull { item ->
        when (item) {
            is LinkPreviewAlbumMediaPhoto -> item.photo.toMedia()
            is LinkPreviewAlbumMediaVideo -> item.video.thumbnail?.toPreviewPhoto(item.video.minithumbnail)
        }
    }
    is LinkPreviewTypeEmbeddedVideoPlayer -> thumbnail?.toMedia()
    is LinkPreviewTypeEmbeddedAnimationPlayer -> thumbnail?.toMedia()
    is LinkPreviewTypeChat -> photo?.toMedia()
    is LinkPreviewTypeUser -> photo?.toMedia()
    is LinkPreviewTypeChannelBoost -> photo?.toMedia()
    is LinkPreviewTypeSupergroupBoost -> photo?.toMedia()
    is LinkPreviewTypeWebApp -> photo?.toMedia()
    is LinkPreviewTypeApp -> photo?.toMedia()
    else -> null
}

/**
 * Náhled souboru (`Thumbnail`) jako obrázek náhledu odkazu. Jen rastrový — JPEG, WebP a PNG;
 * GIF a MPEG4 náhled (u animací bývá) Coil v kartě nekreslí, takže pak obrázek žádný.
 */
internal fun Thumbnail.toPreviewPhoto(minithumbnail: dev.g000sha256.tdl.dto.Minithumbnail?): MessageMedia.Photo? {
    val raster = format is ThumbnailFormatJpeg || format is ThumbnailFormatWebp || format is ThumbnailFormatPng
    if (!raster || width <= 0 || height <= 0) return null
    return MessageMedia.Photo(
        fileId = file.id,
        thumbnailFileId = null,
        fullFileId = file.id,
        width = width,
        height = height,
        minithumbnail = minithumbnail?.toDomain(),
    )
}

private fun TdlChatPhoto.toMedia(): MessageMedia.Photo? = photoMedia(sizes = sizes.filterNotNull(), minithumbnail = minithumbnail)

/** Kam vede odkaz na Telegram; `null` u běžné stránky a u druhů, ke kterým nemáme co říct. */
internal fun LinkPreviewType.target(): LinkPreviewTarget? = when (this) {
    is LinkPreviewTypeChat -> if (type is InviteLinkChatTypeChannel) LinkPreviewTarget.Channel else LinkPreviewTarget.Group
    is LinkPreviewTypeUser -> if (isBot) LinkPreviewTarget.Bot else LinkPreviewTarget.User
    is LinkPreviewTypeChannelBoost, is LinkPreviewTypeSupergroupBoost -> LinkPreviewTarget.Boost
    is LinkPreviewTypeStickerSet, is LinkPreviewTypeSticker -> LinkPreviewTarget.Stickers
    is LinkPreviewTypeWebApp, is LinkPreviewTypeApp -> LinkPreviewTarget.App
    is LinkPreviewTypeMessage -> LinkPreviewTarget.Message
    is LinkPreviewTypeStory, is LinkPreviewTypeLiveStory -> LinkPreviewTarget.Story
    else -> null
}

/**
 * Text náhledu odkazu, nebo `null`, když nic neříká.
 *
 * **Server posílá u některých odkazů titulek doslova jako řetězec „null"** (třeba
 * `share.google/…` — stránka zřejmě do `og:title` vypsala prázdnou hodnotu z JavaScriptu).
 * `ifBlank` ho nechytí a v bublině i v záložce Odkazy pak stálo „null". Slovo samo o sobě
 * není nic, co by uživateli něco řeklo, takže se zahazuje jako prázdný text.
 */
internal fun previewText(raw: String): String? =
    raw.trim().takeUnless { it.isEmpty() || it.equals("null", ignoreCase = true) }

/**
 * Vybere z nabízených velikostí tu k zobrazení a tu nejmenší jako rychlý náhled.
 *
 * TDLib drží fotku v několika velikostech (typy `s`, `m`, `x`, `y`, `w` — od miniatury po originál).
 * Do bubliny se bere největší varianta do [MAX_DISPLAY_EDGE]; originál by u fotky z foťáku znamenal
 * desítky megabajtů kvůli obrázku širokému pár set bodů.
 */
/** Obrázek do karty bota; bere se stejně jako fotka ve zprávě. */
internal fun Photo.toIntroPicture(): MessageMedia.Photo? = toMedia()

internal fun Photo.toMedia(
    hasSpoiler: Boolean = false,
    isSensitive: Boolean = false,
    isSecret: Boolean = false,
    selfDestructType: SelfDestructType? = null,
): MessageMedia.Photo? = photoMedia(
    sizes = sizes.filterNotNull(),
    minithumbnail = minithumbnail,
    hasStickers = hasStickers,
    hasSpoiler = hasSpoiler,
    isSensitive = isSensitive,
    isSecret = isSecret,
    selfDestructType = selfDestructType,
)

/**
 * Výběr velikostí z [sizes] — pro fotku ve zprávě i pro profilovou fotku (`ChatPhoto` nese
 * tytéž `PhotoSize`, jen v jiném obalu). Jedno pravidlo, aby se profilovka v prohlížeči
 * chovala jako fotka ze zprávy.
 */
internal fun photoMedia(
    sizes: List<PhotoSize>,
    minithumbnail: dev.g000sha256.tdl.dto.Minithumbnail?,
    hasSpoiler: Boolean = false,
    isSensitive: Boolean = false,
    isSecret: Boolean = false,
    selfDestructType: SelfDestructType? = null,
    hasStickers: Boolean = false,
): MessageMedia.Photo? {
    val available = sizes.ifEmpty { return null }

    val display = available
        .filter { maxOf(it.width, it.height) <= MAX_DISPLAY_EDGE }
        .maxByOrNull { it.width * it.height }
        // Když jsou všechny varianty větší než práh, vezme se ta nejmenší z nich.
        ?: available.minByOrNull { it.width * it.height }
        ?: return null

    val thumbnail = available.minByOrNull { it.width * it.height }
    // Do prohlížeče patří to nejlepší, co server má — ten strop nad [MAX_DISPLAY_EDGE] platí
    // jen pro bublinu.
    val full = available.maxByOrNull { it.width * it.height } ?: display

    return MessageMedia.Photo(
        fileId = display.photo.id,
        // Když je náhled tentýž soubor jako zobrazovaný, nemá smysl ho stahovat dvakrát.
        thumbnailFileId = thumbnail?.photo?.id?.takeIf { it != display.photo.id },
        fullFileId = full.photo.id,
        width = display.width,
        height = display.height,
        hasSpoiler = hasSpoiler,
        isSensitive = isSensitive,
        isSecret = isSecret,
        selfDestructType = selfDestructType,
        minithumbnail = minithumbnail?.toDomain(),
        hasStickers = hasStickers,
    )
}

/** Rozmazaný náhled ze zprávy; prázdná data jsou jako žádná. */
internal fun dev.g000sha256.tdl.dto.Minithumbnail.toDomain(): Minithumbnail? =
    data.takeIf { it.isNotEmpty() }?.let { Minithumbnail(it, width, height) }

/** Delší hrana zobrazované varianty fotky. Nad tím už jde o zbytečná data. */
private const val MAX_DISPLAY_EDGE = 1280

/**
 * Varianta fotky pro notifikaci: největší do [MAX_NOTIFICATION_EDGE], jinak nejmenší.
 *
 * Menší než do bubliny. Notifikace ji ukáže v pruhu široký pár set bodů a stahuje se v okně
 * pushe, kde rozhoduje každý kilobajt.
 */
internal fun Photo.notificationImage(): File? {
    val available = sizes.filterNotNull().ifEmpty { return null }
    val size = available
        .filter { maxOf(it.width, it.height) <= MAX_NOTIFICATION_EDGE }
        .maxByOrNull { it.width * it.height }
        ?: available.minByOrNull { it.width * it.height }
    return size?.photo
}

private const val MAX_NOTIFICATION_EDGE = 800

/**
 * Číslo kontaktu, jak se ukazuje: s „+" na začátku. TDLib ho posílá, jak ho odesílatel uložil —
 * mezinárodní bez plusu („420777…"), s plusem, nebo místní s mezerami. Plus se doplní jen
 * číslu ze samých číslic; cokoli jiného zůstane, jak přišlo, protože nevíme, jak ho číst.
 */
internal fun contactPhone(raw: String): String {
    val trimmed = raw.trim()
    return if (trimmed.isNotEmpty() && trimmed.all { it.isDigit() }) "+$trimmed" else trimmed
}
