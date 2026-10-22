package eu.tintera.plume.features.conversation.items

import eu.tintera.plume.core.ai.EXPLAIN_MIN_CHARS
import eu.tintera.plume.features.conversation.firstUnreadId
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.key
import eu.tintera.plume.core.telegram.PostStats
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.files.saveAsSupported
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.core.ui.emojiClusterCount
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.content_expired_photo
import eu.tintera.plume.resources.content_expired_video
import eu.tintera.plume.core.ui.isSameDayAs
import eu.tintera.plume.features.conversation.ConversationState
import eu.tintera.plume.features.conversation.copyableText
import eu.tintera.plume.features.conversation.groupAlbums
import eu.tintera.plume.features.conversation.composerEdit
import eu.tintera.plume.features.conversation.readBadgeRows
import eu.tintera.plume.features.conversation.showsLinkPreviewCard
import eu.tintera.plume.features.conversation.showsBareMedia

/**
 * Z obsahu konverzace poskládá bloky seznamu (viz [ConversationItem]).
 *
 * Tady je **jediné** rozhodnutí o tom, jaký blok vznikne, a všechno, co vyžaduje znát sousedy:
 * předěly, seskupení bublin, profilovky, odznak přečtení. Dřív se to počítalo v `itemsIndexed`
 * obrazovky, uvnitř kompozice a bez testů.
 *
 * Seznam jde jako `LazyColumn(reverseLayout = true)`: **index 0 je dole**, takže nejdřív
 * sponzorované zprávy (patří pod nejnovější zprávu), pak zprávy od nejnovější a na konci
 * načítání starší historie. Předěl stojí NAD zprávou, a proto v seznamu ZA ní.
 *
 * Volá se v kompozici, ne ve ViewModelu — viz „Proč se builder volá v kompozici"
 * v `docs/architektura-konverzace.md`.
 */
internal fun conversationItems(state: ConversationState, expired: Set<Long> = emptySet()): List<ConversationItem> {
    val messages = state.messages.withExpiredNotices(expired)
    return buildItems(if (messages === state.messages) state else state.copy(messages = messages))
}

/**
 * Mizející fotka nebo video, které tenhle proces už spotřeboval ([expired] =
 * `SelfDestructTimers.consumedIds`), se kreslí jako oznámení o vypršení HNED — ne až po updatu
 * obsahu od TDLib (`MessageExpiredPhoto`, viz `serviceText`). Do té doby by v seznamu na chvíli
 * stála bublina, která se pak skokem vymění za oznámení na střed.
 *
 * Vrací tentýž seznam, když se nic nemění: množina spotřebovaných roste po celý proces
 * a většina z nich v tomhle chatu není.
 */
internal fun List<ChatMessage>.withExpiredNotices(expired: Set<Long>): List<ChatMessage> {
    if (expired.isEmpty() || none { it.id in expired && it.expiredNotice() != null }) return this
    return map { message -> message.takeIf { it.id in expired }?.expiredNotice() ?: message }
}

/** Zpráva v podobě, v jaké ji pošle TDLib po vypršení; `null`, když nejde o mizející médium. */
internal fun ChatMessage.expiredNotice(): ChatMessage? {
    val description = when (val media = media) {
        is MessageMedia.Photo -> UiText(Res.string.content_expired_photo)
            .takeIf { media.isSecret || media.selfDestructType != null }
        is MessageMedia.Video -> UiText(Res.string.content_expired_video)
            .takeIf { media.isSecret || media.selfDestructType != null }
        else -> null
    } ?: return null
    // Popisek mizející fotky TDLib po vypršení zahodí taky (`MessageExpiredPhoto` žádný nemá).
    return copy(isService = true, media = null, text = "", entities = emptyList(), description = description)
}

private fun buildItems(state: ConversationState): List<ConversationItem> {
    // Album jsou v Telegramu samostatné zprávy, v konverzaci jedna bublina — a na řádcích, ne na
    // zprávách, stojí sousedé.
    val rows = groupAlbums(state.messages)

    // Citace se hledá v mapě, ne průchodem seznamu: v každé bublině by to bylo kvadratické.
    val byId = state.messages.associateBy { it.id }

    val readBadgeKeys = readBadgeRows(rows, state.readReceiptFor).mapTo(HashSet()) { state.keyOf(it.oldest) }

    // Předěl nepřečtených stojí nad první nepřečtenou PŘÍCHOZÍ zprávou (viz [firstUnreadId]).
    val firstUnread = firstUnreadId(state.messages, state.unreadBoundaryId)

    val info = state.info
    val items = ArrayList<ConversationItem>(state.sponsored.size + rows.size + rows.size / 4 + 2)
    val usedDayKeys = HashSet<String>()

    // Reklama stojí pod nejnovější zprávou. Okno v historii nejnovější zprávy nemá, takže by
    // se karta vykreslila uprostřed minulosti; ukáže se, až se okno dostránkuje k přítomnosti.
    // Reklama u bota do seznamu nepatří — je to pruh nad konverzací (`SponsoredBar`).
    if (state.reachedEnd) state.sponsored.filterNot { it.inBotChat }.mapTo(items, ::SponsoredItem)

    // „Píše…" stojí tam, kam zpráva naskočí: nad reklamou, pod nejnovější zprávou. V okně
    // v historii nejnovější zpráva není, takže tam by stálo uprostřed minulosti. Rozepsaná
    // odpověď bota stojí na jeho místě: říká totéž a víc.
    if (state.reachedEnd) (pendingReplyItem(state) ?: typingItem(state))?.let(items::add)

    rows.forEachIndexed { index, row ->
        val message = row.display
        val key = state.keyOf(row.oldest)
        // Seznam jde od nejnovější, takže starší soused je o index VÝŠ a novější o index níž.
        val olderRow = rows.getOrNull(index + 1)
        val older = olderRow?.display

        /*
         * Začíná touhle zprávou nový den? U nejstarší načtené zprávy (`older == null`) se datum
         * ukáže taky — jakmile se dotáhne starší stránka, předěl se posune nahoru.
         */
        val startsDay = older == null || !older.timestamp.isSameDayAs(row.oldest.timestamp)

        /*
         * Po pauze aspoň hodinu dostane zpráva nad sebe čas (v bublině není). Na přelomu dne ho
         * nese předěl dne, takže dva předěly nad sebou nevzniknou.
         */
        val startsTimeGap = !startsDay && olderRow != null &&
            row.oldest.timestamp - olderRow.newest.timestamp >= TIME_DIVIDER_GAP_SECONDS

        /*
         * Hranici nepřečtených drží ViewModel z okamžiku otevření chatu, takže se předěl
         * neposouvá, jak se zprávy postupně označují za přečtené.
         */
        val startsUnread = firstUnread != null && row.messages.any { it.id == firstUnread }

        items += if (message.isService) {
            ServiceMessageItem(key = key, message = message, menu = state.noticeMenu?.takeIf { it.messageId == message.id })
        } else {
            // Za předělem dne i časovým předělem začíná nový blok, i když píše tentýž člověk.
            val olderInGroup = older.takeIf { !startsDay && !startsTimeGap }
            val continuesBelow = rows.getOrNull(index - 1)?.display.continues(message)
            val delivery = message.delivery(state.lastReadOutboxMessageId)

            val frame = MessageFrameData(
                key = key,
                message = message,
                continuesAbove = olderInGroup != null && message.continues(olderInGroup),
                continuesBelow = continuesBelow,
                /*
                 * Tvář odesílatele u POSLEDNÍ zprávy jeho bloku: blok se čte shora a tvář ho
                 * uzavírá. Kanál (odesílatel = chat sám) tvář nemá — nese ji hlavička. Přímé
                 * zprávy kanálu taky ne: baví se v nich dva, jako v soukromém chatu.
                 */
                avatar = when {
                    info?.isGroup != true || info.isDirectMessages || message.isOutgoing -> SenderAvatar.None
                    message.senderId == info.id -> SenderAvatar.None
                    continuesBelow -> SenderAvatar.Spacer
                    else -> SenderAvatar.Shown
                },
                delivery = delivery,
                badge = delivery?.takeIf { it != Delivery.Read || key in readBadgeKeys },
                highlighted = state.highlightedMessageId?.let(row::contains) == true,
                searchFocused = state.search?.current?.let(row::contains) == true,
                reactionsOpenable = info?.isChannel != true,
                namesReactors = info?.isGroup == true && !info.isChannel && !info.isDirectMessages,
                flight = state.flight?.takeIf { it.itemKey == key },
                menu = state.messageMenu?.takeIf { it.messageId == message.id },
                menuActions = menuActions(
                    row.primary,
                    isPinned = state.pinned.any { row.contains(it.id) },
                    customEmoji = state.customEmojiAllowed,
                ),
                /*
                 * Ťuknutí na VLASTNÍ zprávu si vyžádá odznak přečtení, druhé ho schová. Je to
                 * ubezpečení na vyžádání: automaticky se odznak ukazuje jen u nejnovější zprávy.
                 */
                onTap = MessageAction.ToggleReadReceipt.takeIf { message.isOutgoing },
                /*
                 * V režimu výběru nese každý blok, jestli je vybraný; mimo něj je to `null`
                 * a rám výběr vůbec nekreslí. Klíč bloku je u alba klíč celého alba, takže
                 * se vybírá album, ne jednotlivá fotka.
                 */
                selected = state.selection?.let { key in it.keys },
                postStats = message.postStats?.shownIn(isChannel = info?.isChannel == true),
            )

            messageItem(
                frame = frame,
                album = row.members.takeIf { row.isAlbum },
                // Odpověď na zprávu z jiného chatu nese zástupnou citaci sama — v historii téhle
                // konverzace citovaná zpráva není.
                // Ve vlákně komentářů odpovídá kořeni skoro každý komentář — citace příspěvku nad
                // každým z nich by byla jen opakování toho, co stojí nahoře.
                quoted = message.replyToMessageId
                    ?.takeIf { it != state.threadRootId }
                    ?.let { id -> byId[id] ?: state.quotes[id] }
                    ?: message.externalReply?.quote,
                // V soukromém chatu se jméno odesílatele u zpráv nevyplňuje, takže citace by
                // neměla koho uvést.
                partnerName = info?.title,
            )
        }

        if (startsUnread) items += UnreadDividerItem
        // Předěl stojí NAD zprávou, v obráceném seznamu tedy ZA ní (a nad předělem nepřečtených).
        if (startsTimeGap) items += TimeDividerItem(timestamp = row.oldest.timestamp, key = "time-$key")
        if (startsDay) {
            val dayKey = DayDividerItem.keyFor(row.oldest.timestamp)
                .let { if (usedDayKeys.add(it)) it else "$it-$key" }
            items += DayDividerItem(timestamp = message.timestamp, key = dayKey)
        }
    }

    if (state.loading) items += LoadingOlderItem
    return items
}

/** Rozepsaná odpověď bota, nebo `null`. Prázdná je „Přemýšlí…" (Bot API: „Thinking…" placeholder). */
private fun pendingReplyItem(state: ConversationState): PendingReplyItem? {
    val reply = state.pendingReply ?: return null
    return PendingReplyItem(
        draftId = reply.message.draftId,
        text = reply.message.text,
        entities = reply.message.entities,
        canStop = reply.canStop,
    )
}

/**
 * Blok „píše…", nebo `null`, když nikdo nic nedělá. Kanál ho nemá — příspěvky nepíše nikdo, kdo
 * by se dal ukázat, a hlavičku kanálu nese chat sám.
 */
private fun typingItem(state: ConversationState): TypingItem? {
    val typing = state.typing ?: return null
    val info = state.info
    if (info?.isChannel == true) return null
    val inGroup = info?.isGroup == true && !info.isDirectMessages
    val face = typing.sender?.let { TypingFace(it.id, it.title, it.avatarFileId) }
        // V soukromém chatu píše protějšek, a jeho tvář je tvář chatu.
        ?: info?.takeUnless { inGroup }?.let { TypingFace(it.id, it.title, it.avatarFileId) }
    return TypingItem(activity = typing.activity, face = face, inGroup = inGroup)
}

/**
 * Jaký blok ze zprávy vznikne — JEDINÉ místo, kde se o tom rozhoduje.
 *
 * Bez bubliny se kreslí to, co je obrázek samo o sobě: pár emoji, samolepka, fotka a video,
 * náhled odkazu s velkým obrázkem. Podmínkou je vždycky, že na zprávě nevisí nic, co pozadí
 * potřebuje — citace se bez něj ztratí, takže si bublinu vynutí.
 */
private fun messageItem(
    frame: MessageFrameData,
    album: List<ChatMessage>?,
    quoted: ChatMessage?,
    partnerName: String?,
): MessageItem {
    val message = frame.message
    val media = message.media
    // Popisek alba z pár emoji je pořád popisek pod fotkami, ne velké emoji bez bubliny.
    // Citace i značka přeposlání jsou údaj navíc a obsah s údajem navíc je karta: bublinu si
    // vynutí, jako popisek u fotky (holý text vedle samolepky by od ní odplul).
    val needsCard = message.replyToMessageId != null || message.hasOriginLabel
    val emojiCount = if (album != null || media != null || needsCard) {
        0
    } else {
        message.text.emojiClusterCount(MAX_LARGE_EMOJI)
    }
    val preview = message.linkPreview
    val poll = message.poll
    val checklist = message.checklist
    return when {
        poll != null -> PollMessageItem(frame, poll = poll, quoted = quoted, partnerName = partnerName)
        checklist != null -> ChecklistMessageItem(frame, checklist = checklist, quoted = quoted, partnerName = partnerName)
        emojiCount > 0 -> LargeEmojiMessageItem(frame, emojiCount)
        showsBareMedia(message, album) -> MediaMessageItem(frame, album)
        preview != null && showsLinkPreviewCard(message) -> LinkPreviewMessageItem(frame, preview)
        (media is MessageMedia.Sticker || media is MessageMedia.AnimatedEmoji || media is MessageMedia.Dice ||
            media is MessageMedia.VideoNote) &&
            !needsCard -> StickerMessageItem(frame, media)
        else -> BubbleMessageItem(frame, album = album, quoted = quoted, partnerName = partnerName)
    }
}

/**
 * Kandidáti do nabídky nad zprávou, v pořadí, v jakém se v ní ukážou (viz [MessageMenuAction]).
 *
 * Tady se rozhoduje jen podle toho, co zpráva JE; co server dovolí, se zjistí až při otevření
 * nabídky. [message] je skutečná zpráva, ne `MessageRow.display`: u alba má `display` přílohu
 * vynulovanou (nese ji mřížka), takže by popisek alba vypadal jako text a upravil by se jiným
 * voláním, než kterým se ukládá.
 */
internal fun menuActions(message: ChatMessage, isPinned: Boolean, customEmoji: Boolean = false): List<MessageMenuAction> = buildList {
    // Pořadí je pořadí UVNITŘ skupin; do skupin a úrovní je rozdělí `MessageMenuAction.slot`.
    //
    // Neodeslaná zpráva má jedinou akci, která ji spraví, a ta jde první. Kandidát je u každé:
    // u alba nese neodeslanou fotku jiná zpráva než ta, na které nabídka visí.
    add(MessageMenuAction.Resend)
    // Úprava jen u vlastní zprávy, a tam je to důvod, proč se nabídka otevírá — proto před odpovědí.
    if (message.composerEdit(customEmoji) != null) add(MessageMenuAction.Edit)
    if (message.checklist != null) add(MessageMenuAction.EditChecklist)
    add(MessageMenuAction.Reply)
    val poll = message.poll
    // Bez textu není co kopírovat, i kdyby to server dovolil. Text ankety je jen náš popis
    // („Anketa: otázka") pro citace a náhledy, ne obsah, který by stálo za to kopírovat.
    val hasText = poll == null && message.copyableText().isNotBlank()
    if (hasText) add(MessageMenuAction.Copy)
    add(MessageMenuAction.Forward)
    if (hasText) {
        // Překládá server text ZPRÁVY (`translateMessageText`); přepis hlasovky textem zprávy není.
        // Zpráva v jazyce překladu ho nenabídne (`MessageMenu.inTranslationLanguage`), takže nahoře
        // je jen u cizojazyčné zprávy.
        if (message.media !is MessageMedia.VoiceNote && message.media !is MessageMedia.VideoNote) {
            add(MessageMenuAction.Translate)
        }
    }
    if (message.media is MessageMedia.Sticker) add(MessageMenuAction.ToggleFavoriteSticker)
    // Skladba jde do hudby ve vlastním profilu — v tajném chatu ne, soubor je jen tam.
    if (message.media is MessageMedia.Audio) add(MessageMenuAction.ToggleProfileAudio)
    // Soubor, který není obrázek ani video, se do prohlížeče nedostane; ven jde odsud. Skladba
    // je taky soubor — v bublině se jen přehrává.
    if (message.media is MessageMedia.Document || message.media is MessageMedia.Audio) {
        add(MessageMenuAction.ShareFile)
        if (saveAsSupported) add(MessageMenuAction.SaveFileAs)
    }
    if (poll?.canRetract == true) add(MessageMenuAction.RetractVote)
    if (poll != null && !poll.isClosed) add(MessageMenuAction.StopPoll)
    if (hasText) {
        // Akce z textu (termín, účet, zásilka): jestli v něm něco je, zjistí ViewModel při
        // otevření nabídky — tady by se pravidla pouštěla u každé zprávy v seznamu.
        add(MessageMenuAction.ContextActions)
        add(MessageMenuAction.SelectText)
        if (message.summaryLanguage != null) add(MessageMenuAction.Summarize)
        // Vysvětlit jen DLOUHOU CIZÍ zprávu: vlastní zprávu člověk zná a krátkou přečte
        // rychleji, než model odpoví.
        if (!message.isOutgoing && message.text.length >= EXPLAIN_MIN_CHARS) add(MessageMenuAction.Explain)
    }
    add(MessageMenuAction.Info)
    add(MessageMenuAction.ToggleBookmark)
    add(MessageMenuAction.Remind)
    add(if (isPinned) MessageMenuAction.Unpin else MessageMenuAction.Pin)
    add(MessageMenuAction.Select)
    add(MessageMenuAction.Report)
    add(MessageMenuAction.BlockFromReplies)
    if (poll?.options?.any { it.canDelete } == true) add(MessageMenuAction.DeletePollOption)
    add(MessageMenuAction.Delete)
}

/**
 * Skutečný stav doručení odchozí zprávy; `null` u příchozí.
 *
 * „Přečteno" není ve zprávě, ale na chatu (`lastReadOutboxMessageId`): posuvný ukazatel, pod
 * který spadají všechny odchozí zprávy s menším id.
 */
internal fun ChatMessage.delivery(lastReadOutboxMessageId: Long): Delivery? = sendState?.let { sendState ->
    when {
        sendState == SendState.Failed -> Delivery.Failed
        sendState == SendState.Sending -> Delivery.Sending
        id <= lastReadOutboxMessageId -> Delivery.Read
        else -> Delivery.Sent
    }
}

/**
 * Navazuje tahle (novější) zpráva na [older] ve stejném bloku?
 *
 * Stejná strana, stejný odesílatel, stejný den a bez pauzy na časový předěl. Servisní oznámení
 * blok přeruší — kreslí se na střed a tvář nad ním by visela ve vzduchu. Přerušuje ho **z obou stran**: dřív se hlídala jen
 * novější zpráva, takže bublina pod oznámením měla stažené nároží, jako by na něj navazovala.
 */
private fun ChatMessage?.continues(older: ChatMessage): Boolean =
    this != null &&
        !isService &&
        !older.isService &&
        isOutgoing == older.isOutgoing &&
        senderId == older.senderId &&
        timestamp.isSameDayAs(older.timestamp) &&
        // Mezi nimi by stál časový předěl, a ten blok ukončuje stejně jako předěl dne.
        timestamp - older.timestamp < TIME_DIVIDER_GAP_SECONDS

/**
 * Tvář odesílatele vedle bubliny ve skupině.
 *
 * [Spacer] drží místo u zpráv uprostřed bloku — bez něj by bubliny téhož člověka skákaly
 * doleva a doprava podle toho, u které zrovna tvář je.
 */
internal enum class SenderAvatar { None, Spacer, Shown }

/**
 * Počty tak, jak se v konverzaci ukážou. Komentáře jen v kanálu: `replyInfo` má i zpráva
 * v diskusní skupině (počet odpovědí ve vláknu), a tam by pod každou zprávou stálo „0".
 */
internal fun PostStats.shownIn(isChannel: Boolean): PostStats? =
    (if (isChannel) this else copy(comments = null)).takeUnless { it.isEmpty }
