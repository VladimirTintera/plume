package eu.tintera.plume.core.telegram

import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.LinkPreview as TdlLinkPreview
import dev.g000sha256.tdl.dto.LinkPreviewTypeArticle
import dev.g000sha256.tdl.dto.LinkPreviewTypeEmbeddedAnimationPlayer
import dev.g000sha256.tdl.dto.LinkPreviewTypeEmbeddedVideoPlayer
import dev.g000sha256.tdl.dto.LinkPreviewTypePhoto
import dev.g000sha256.tdl.dto.LinkPreviewTypeVideo
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageAnimatedEmoji
import dev.g000sha256.tdl.dto.MessageAnimation
import dev.g000sha256.tdl.dto.MessageAudio
import dev.g000sha256.tdl.dto.MessageCall
import dev.g000sha256.tdl.dto.MessageChecklist
import dev.g000sha256.tdl.dto.MessageContact
import dev.g000sha256.tdl.dto.MessageContent
import dev.g000sha256.tdl.dto.MessageDice
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessageExpiredPhoto
import dev.g000sha256.tdl.dto.MessageExpiredVideo
import dev.g000sha256.tdl.dto.MessageExpiredVideoNote
import dev.g000sha256.tdl.dto.MessageExpiredVoiceNote
import dev.g000sha256.tdl.dto.MessageGame
import dev.g000sha256.tdl.dto.MessageInvoice
import dev.g000sha256.tdl.dto.MessageLiveLocation
import dev.g000sha256.tdl.dto.MessageLocation
import dev.g000sha256.tdl.dto.MessagePaidMedia
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
import dev.g000sha256.tdl.dto.MessageSelfDestructType
import dev.g000sha256.tdl.dto.MessageSelfDestructTypeImmediately
import dev.g000sha256.tdl.dto.MessageSelfDestructTypeTimer
import dev.g000sha256.tdl.dto.MessageStakeDice
import dev.g000sha256.tdl.dto.MessageSticker
import dev.g000sha256.tdl.dto.MessageStory
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageUnsupported
import dev.g000sha256.tdl.dto.MessageVenue
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.MessageVideoNote
import dev.g000sha256.tdl.dto.MessageVoiceNote
import dev.g000sha256.tdl.dto.Photo
import dev.g000sha256.tdl.dto.MessageChatShared
import dev.g000sha256.tdl.dto.MessageGiveaway
import dev.g000sha256.tdl.dto.MessageGiveawayWinners
import dev.g000sha256.tdl.dto.MessageGroupCall
import dev.g000sha256.tdl.dto.MessageRichMessage
import dev.g000sha256.tdl.dto.MessageSuggestBirthdate
import dev.g000sha256.tdl.dto.MessageSuggestProfilePhoto
import dev.g000sha256.tdl.dto.MessageUsersShared
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
import dev.g000sha256.tdl.dto.PushMessageContentLocation
import dev.g000sha256.tdl.dto.PushMessageContentPaidMedia
import dev.g000sha256.tdl.dto.PushMessageContentPhoto
import dev.g000sha256.tdl.dto.PushMessageContentPoll
import dev.g000sha256.tdl.dto.PushMessageContentSticker
import dev.g000sha256.tdl.dto.PushMessageContentStory
import dev.g000sha256.tdl.dto.PushMessageContentText
import dev.g000sha256.tdl.dto.PushMessageContentVideo
import dev.g000sha256.tdl.dto.PushMessageContentVideoNote
import dev.g000sha256.tdl.dto.PushMessageContentVoiceNote
import dev.g000sha256.tdl.dto.Sticker
import dev.g000sha256.tdl.dto.StickerFormatTgs
import dev.g000sha256.tdl.dto.StickerFormatWebp
import dev.g000sha256.tdl.dto.TextEntity
import dev.g000sha256.tdl.dto.TextEntityTypeBlockQuote
import dev.g000sha256.tdl.dto.TextEntityTypeBold
import dev.g000sha256.tdl.dto.TextEntityTypeCode
import dev.g000sha256.tdl.dto.TextEntityTypeEmailAddress
import dev.g000sha256.tdl.dto.TextEntityTypeExpandableBlockQuote
import dev.g000sha256.tdl.dto.TextEntityTypeHashtag
import dev.g000sha256.tdl.dto.TextEntityTypeItalic
import dev.g000sha256.tdl.dto.TextEntityTypeMention
import dev.g000sha256.tdl.dto.TextEntityTypeMentionName
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
import eu.tintera.plume.core.audio.decodeWaveform
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.content_album
import eu.tintera.plume.resources.content_audio
import eu.tintera.plume.resources.content_gift
import eu.tintera.plume.resources.content_giveaway
import eu.tintera.plume.resources.content_giveaway_winners
import eu.tintera.plume.resources.content_group_call
import eu.tintera.plume.resources.content_rich_message
import eu.tintera.plume.resources.content_shared_chat
import eu.tintera.plume.resources.content_stake_dice
import eu.tintera.plume.resources.content_shared_users
import eu.tintera.plume.resources.content_suggested_birthdate
import eu.tintera.plume.resources.content_suggested_photo
import eu.tintera.plume.resources.content_call
import eu.tintera.plume.resources.content_checklist
import eu.tintera.plume.resources.content_contact
import eu.tintera.plume.resources.content_expired_photo
import eu.tintera.plume.resources.content_expired_video
import eu.tintera.plume.resources.content_expired_video_note
import eu.tintera.plume.resources.content_expired_voice_note
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
import eu.tintera.plume.resources.content_video_call
import eu.tintera.plume.resources.content_video_note
import eu.tintera.plume.resources.conversation_voice_message
import eu.tintera.plume.resources.media_photo
import eu.tintera.plume.resources.media_sticker
import eu.tintera.plume.resources.media_video
import eu.tintera.plume.resources.notification_new_message
import org.jetbrains.compose.resources.getString
import co.touchlab.kermit.Logger
import kotlin.concurrent.Volatile

/**
 * Textová podoba obsahu zprávy pro **náhled v seznamu chatů**.
 *
 * V seznamu není kam kreslit obrázek, takže i fotka musí mít slovní podobu. Text se bere doslova,
 * u médií se použije popisek, a když ho zpráva nemá, název typu — prázdný řádek u fotky vypadá
 * jako chyba načítání.
 */
internal suspend fun Message.previewText(): String =
    previewTextOrNull() ?: getString(Res.string.content_unsupported)

/**
 * Text zprávy pro **notifikaci**: totéž co [previewText], jen typ, který neumíme popsat, dostane
 * „Nová zpráva". Věta „tenhle typ zprávy zatím neumíme zobrazit" patří do aplikace, kde stojí na
 * místě zprávy; na liště vypadá jako chybové hlášení místo zprávy.
 */
internal suspend fun Message.notificationText(): String =
    previewTextOrNull() ?: getString(Res.string.notification_new_message)

private suspend fun Message.previewTextOrNull(): String? = when (val content = this.content) {
    is MessageText -> content.text.text
    is MessagePhoto -> content.caption.text.ifBlankGet { getString(Res.string.media_photo) }
    is MessageVideo -> content.caption.text.ifBlankGet { getString(Res.string.media_video) }
    is MessageAnimation -> content.caption.text.ifBlankGet { getString(Res.string.content_gif) }
    is MessageDocument -> content.caption.text.ifBlankGet { getString(Res.string.content_file) }
    is MessageAudio -> content.caption.text.ifBlankGet { getString(Res.string.content_audio) }
    is MessageVoiceNote -> content.caption.text.ifBlankGet { getString(Res.string.conversation_voice_message) }
    is MessageSticker -> content.sticker.emoji.ifBlankGet { getString(Res.string.media_sticker) }
    is MessageAnimatedEmoji -> content.emoji
    is MessageDice -> content.diceText()
    else -> content.describe()
}

/**
 * Text notifikace z obsahu **rozšifrovaného z pushe** (`NotificationTypeNewPushMessage`).
 *
 * Push nese vlastní, chudší podobu obsahu než zpráva — TDLib ji vydá, když samotnou zprávu ještě
 * nemá. Slova jsou stejná jako v [previewText], ať notifikace nevypadá jinak podle toho, kudy
 * přišla. Co push nepopisuje nijak užitečně (servisní události), dostane obecné
 * „Nová zpráva" — pravdivé a bez doslovného názvu typu.
 */
internal suspend fun PushMessageContent.previewText(): String = when (this) {
    is PushMessageContentText -> text
    is PushMessageContentPhoto -> caption.ifBlankGet { getString(Res.string.media_photo) }
    is PushMessageContentVideo -> caption.ifBlankGet { getString(Res.string.media_video) }
    is PushMessageContentAnimation -> caption.ifBlankGet { getString(Res.string.content_gif) }
    is PushMessageContentDocument -> getString(Res.string.content_file)
    is PushMessageContentAudio -> getString(Res.string.content_audio)
    is PushMessageContentVoiceNote -> getString(Res.string.conversation_voice_message)
    is PushMessageContentVideoNote -> getString(Res.string.content_video_note)
    is PushMessageContentSticker -> emoji.ifBlankGet { getString(Res.string.media_sticker) }
    is PushMessageContentPoll -> getString(Res.string.content_poll, question)
    is PushMessageContentChecklist -> getString(Res.string.content_checklist, title)
    is PushMessageContentLocation ->
        getString(if (isLive) Res.string.content_live_location else Res.string.content_location)
    is PushMessageContentContact -> getString(Res.string.content_contact, name)
    is PushMessageContentGame -> getString(Res.string.content_game, title)
    is PushMessageContentStory -> getString(Res.string.content_story)
    is PushMessageContentPaidMedia -> getString(Res.string.content_paid)
    is PushMessageContentMediaAlbum -> getString(Res.string.content_album)
    is PushMessageContentGift, is PushMessageContentUpgradedGift, is PushMessageContentPremiumGiftCode ->
        getString(Res.string.content_gift)
    is PushMessageContentGiveaway -> getString(Res.string.content_giveaway)
    else -> getString(Res.string.notification_new_message)
}

/**
 * Jednořádkový popis obsahu, který nemá vlastní text.
 *
 * Poslední záchrana pro typy, které neumíme vykreslit. Dřív tu bylo pro všechny jedno „Zpráva",
 * takže se anketa, poloha i systémové oznámení tvářily stejně — a hlavně to vypadalo jako chyba
 * načítání, ne jako „tohle zatím neumíme".
 */
private suspend fun MessageContent.describe(): String? = when (this) {
    is MessageVideoNote -> getString(Res.string.content_video_note)
    is MessagePoll -> getString(Res.string.content_poll, poll.question.text)
    is MessageChecklist -> getString(Res.string.content_checklist, list.title.text)
    is MessageLocation -> getString(Res.string.content_location)
    is MessageLiveLocation -> getString(Res.string.content_live_location)
    is MessageVenue -> getString(Res.string.content_venue, venue.title)
    is MessageContact -> getString(Res.string.content_contact, "${contact.firstName} ${contact.lastName}".trim())
    is MessageGame -> getString(Res.string.content_game, game.title)
    // Sázková kostka (TDLib 15): jako `MessageDice`, ale s vkladem v TON. Hodnotu ani vklad
    // nevypisujeme — bez animace by z čísla nebylo poznat, že jde o hod.
    is MessageStakeDice -> getString(Res.string.content_stake_dice)
    is MessageInvoice -> getString(Res.string.content_invoice, productInfo.title)
    is MessageStory -> getString(Res.string.content_story)
    is MessagePaidMedia -> caption.text.ifBlankGet { getString(Res.string.content_paid) }
    is MessageCall -> getString(if (isVideo) Res.string.content_video_call else Res.string.content_call)
    is MessageGroupCall -> getString(Res.string.content_group_call)
    is MessageGiveaway -> getString(Res.string.content_giveaway)
    is MessageGiveawayWinners -> getString(Res.string.content_giveaway_winners)
    is MessageSuggestProfilePhoto -> getString(Res.string.content_suggested_photo)
    is MessageSuggestBirthdate -> getString(Res.string.content_suggested_birthdate)
    is MessageChatShared -> getString(Res.string.content_shared_chat)
    is MessageUsersShared -> getString(Res.string.content_shared_users)
    // Článek složený z bloků (TDLib 15). Kreslit ho neumíme, jeho text ano — prázdný je jen
    // článek ze samých obrázků.
    is MessageRichMessage -> message.blocks.plainText().ifBlankGet { getString(Res.string.content_rich_message) }

    // Zprávy s časovým omezením, které vypršely. Soubor už na serveru není, takže není co stáhnout.
    is MessageExpiredPhoto -> getString(Res.string.content_expired_photo)
    is MessageExpiredVideo -> getString(Res.string.content_expired_video)
    is MessageExpiredVideoNote -> getString(Res.string.content_expired_video_note)
    is MessageExpiredVoiceNote -> getString(Res.string.content_expired_voice_note)

    // Klient je starší než server. Doslovný název typu by uživateli neřekl nic; v aplikaci
    // dostane „neumíme zobrazit", v notifikaci „Nová zpráva" — stejně jako neznámý typ.
    is MessageUnsupported -> {
        reportUnknownContent(this)
        null
    }

    // Systémová oznámení mají vlastní formulace i vykreslení. `null` = typ neumíme popsat
    // a jakou větou to říct, rozhodne volající (v aplikaci jinou než v notifikaci).
    else -> serviceText() ?: run {
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
 * v CLAUDE.md), `toString()` z TDLib ale název nese jako řetězcový literál — ten přejmenování
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
private fun RichText.plainText(): String = when (this) {
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
 * Jako `ifBlank`, ale s pozastavitelnou náhradou — text typu se čte z prostředků, a to je
 * `suspend`.
 */
private suspend inline fun String.ifBlankGet(fallback: () -> String): String =
    if (isBlank()) fallback() else this

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
internal suspend fun Message.captionBody(): MessageBody = when (val content = this.content) {
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

    is MessageDice -> MessageBody(content.diceText())

    // Typy, které mají v bublině vlastní přílohu s popiskem — text by ho zdvojil.
    is MessageVideoNote, is MessageGame, is MessageInvoice -> MessageBody.Empty
    is MessagePaidMedia -> content.caption.toBody()

    /*
     * Zbytek nemá vlastní text, jen náš popis — a ten žádné entity nenese.
     *
     * Připojit k němu entity z původní zprávy by znamenalo rozsahy posunuté do jiného řetězce:
     * v lepším případě nesmyslně obarvené slovo, v horším pád na indexu mimo text.
     */
    else -> MessageBody(previewText())
}

/**
 * Text zprávy i s rozsahy formátování.
 *
 * Drží se pohromadě proto, aby se ty dvě věci nemohly rozejít — entity bez svého textu jsou
 * jen dvojice čísel a špatně spárované ukazují jinam, než by měly.
 */
internal data class MessageBody(
    val text: String,
    val entities: List<MessageEntity> = emptyList(),
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
 * příkazy botů a cashtagy: obarvit je umíme, ale kliknutí by nemělo kam vést.
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

        is TextEntityTypeBold -> MessageEntityType.Bold
        is TextEntityTypeItalic -> MessageEntityType.Italic
        is TextEntityTypeUnderline -> MessageEntityType.Underline
        is TextEntityTypeStrikethrough -> MessageEntityType.Strikethrough
        // Inline kód i celý blok skončí stejně; liší se jen tím, že blok stojí na vlastních řádcích.
        is TextEntityTypeCode, is TextEntityTypePre, is TextEntityTypePreCode -> MessageEntityType.Code
        is TextEntityTypeSpoiler -> MessageEntityType.Spoiler
        // Sbalitelná citace se zatím kreslí celá; příznak se drží kvůli „Zobrazit víc".
        is TextEntityTypeBlockQuote -> MessageEntityType.Quote(expandable = false)
        is TextEntityTypeExpandableBlockQuote -> MessageEntityType.Quote(expandable = true)

        else -> null
    }

    return mapped?.let { MessageEntity(offset = offset, length = length, type = it) }
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
private fun MessageDice.diceText(): String = if (value > 0) "$emoji $value" else emoji

/** Náhled, který jde nakreslit jako obrázek — animovaný MPEG-4 Coil neotevře. */
private fun Thumbnail?.picture(): Thumbnail? = this?.takeIf { it.format !is ThumbnailFormatMpeg4 }

internal fun MessageSelfDestructType.toSelfDestructType(): SelfDestructType = when (this) {
    is MessageSelfDestructTypeImmediately -> SelfDestructType.Immediately
    is MessageSelfDestructTypeTimer -> SelfDestructType.Timer(selfDestructTime)
}

/** Příloha zprávy, nebo `null` u čistě textové. */
internal suspend fun Message.messageMedia(): MessageMedia? = when (val content = this.content) {
    is MessagePhoto -> content.photo.toMedia(
        hasSpoiler = content.hasSpoiler,
        isSecret = content.isSecret,
        selfDestructType = selfDestructType?.toSelfDestructType(),
    )

    is MessageVideo -> MessageMedia.Video(
        fileId = content.video.video.id,
        // Náhled ve formátu MPEG-4 (animovaný) obrázek není; Coil ho neotevře.
        thumbnailFileId = content.video.thumbnail?.takeIf { it.format !is ThumbnailFormatMpeg4 }?.file?.id,
        width = content.video.width,
        height = content.video.height,
        durationSeconds = content.video.duration,
        hasSpoiler = content.hasSpoiler,
        isSecret = content.isSecret,
        selfDestructType = selfDestructType?.toSelfDestructType(),
        supportsStreaming = content.video.supportsStreaming,
        minithumbnail = content.video.minithumbnail?.toDomain(),
    )

    is MessageAnimation -> content.animation.toMedia()

    is MessageDocument -> MessageMedia.Document(
        fileId = content.document.document.id,
        fileName = content.document.fileName.ifBlankGet { getString(Res.string.content_file) },
        mimeType = content.document.mimeType,
        // `expectedSize` je známá i před stažením; `size` je nula, dokud soubor nedorazí.
        sizeBytes = content.document.document.expectedSize,
        thumbnailFileId = content.document.thumbnail.picture()?.file?.id,
        thumbnailWidth = content.document.thumbnail.picture()?.width ?: 0,
        thumbnailHeight = content.document.thumbnail.picture()?.height ?: 0,
    )

    is MessageAudio -> MessageMedia.Attachment(label = getString(Res.string.content_audio), thumbnailFileId = null)
    is MessageVoiceNote -> MessageMedia.VoiceNote(
        fileId = content.voiceNote.voice.id,
        durationSeconds = content.voiceNote.duration,
        waveform = decodeWaveform(content.voiceNote.waveform),
        isListened = content.isListened,
    )
    is MessageSticker -> content.sticker.toMedia()

    // O textu a příloze rozhoduje jedna funkce, `animationFileId` — obě větve se nesmí rozejít.
    is MessageAnimatedEmoji -> content.animationFileId()?.let { fileId ->
        MessageMedia.AnimatedEmoji(animationFileId = fileId, emoji = content.emoji)
    }

    // Kolečko s videem. Náhled je čtvercový, kulatý ořez by patřil k přehrávání, které neumíme.
    is MessageVideoNote -> MessageMedia.Attachment(
        label = getString(Res.string.content_video_note),
        thumbnailFileId = content.videoNote.thumbnail?.file?.id,
    )

    is MessageGame -> MessageMedia.Attachment(
        label = getString(Res.string.content_game, content.game.title),
        thumbnailFileId = content.game.photo.sizes.filterNotNull()
            .minByOrNull { it.width * it.height }?.photo?.id,
    )

    is MessageInvoice -> MessageMedia.Attachment(
        label = getString(Res.string.content_invoice, content.productInfo.title),
        thumbnailFileId = content.productInfo.photo?.sizes?.filterNotNull()
            ?.minByOrNull { it.width * it.height }?.photo?.id,
    )

    else -> null
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
        )
    }
    val order = poll.optionOrder.filter { it in all.indices }.distinct()
    val ordered = order.map { all[it] } + all.filterIndexed { index, _ -> index !in order }
    val description = description.toBody()
    return ChatPoll(
        question = poll.question.text,
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
            )
        },
        restriction = poll.voteRestrictionReason?.toRestriction(),
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
        width = width,
        height = height,
        emoji = emoji,
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
internal fun Message.linkPreview(): LinkPreview? =
    (content as? MessageText)?.linkPreview?.toDomain()

/**
 * Náhled od TDLib v doménové podobě. Společné pro přijatou zprávu i náhled při psaní
 * (`getLinkPreview`), aby obojí vypadalo stejně a prázdný náhled se zahodil na jednom místě.
 */
internal fun TdlLinkPreview.toDomain(): LinkPreview? {
    val preview = this

    val photo = when (val type = preview.type) {
        is LinkPreviewTypeArticle -> type.photo
        is LinkPreviewTypePhoto -> type.photo
        is LinkPreviewTypeVideo -> type.cover
        is LinkPreviewTypeEmbeddedVideoPlayer -> type.thumbnail
        is LinkPreviewTypeEmbeddedAnimationPlayer -> type.thumbnail
        else -> null
    }?.toMedia()

    val result = LinkPreview(
        url = preview.url,
        siteName = previewText(preview.siteName),
        title = previewText(preview.title),
        description = previewText(preview.description.text),
        photo = photo,
        largePhoto = preview.showLargeMedia,
        showAboveText = preview.showAboveText,
        canChangeSize = preview.hasLargeMedia,
    )
    if (result.siteName == null && result.title == null && result.description == null && photo == null) {
        return null
    }
    return result
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
private fun Photo.toMedia(
    hasSpoiler: Boolean = false,
    isSecret: Boolean = false,
    selfDestructType: SelfDestructType? = null,
): MessageMedia.Photo? {
    val available = sizes.filterNotNull().ifEmpty { return null }

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
        isSecret = isSecret,
        selfDestructType = selfDestructType,
        minithumbnail = minithumbnail?.toDomain(),
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
