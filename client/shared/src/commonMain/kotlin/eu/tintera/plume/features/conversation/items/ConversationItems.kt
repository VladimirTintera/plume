package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.key
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.ui.emojiClusterCount
import eu.tintera.plume.core.ui.isSameDayAs
import eu.tintera.plume.features.conversation.ConversationState
import eu.tintera.plume.features.conversation.groupAlbums
import eu.tintera.plume.features.conversation.readBadgeRows
import eu.tintera.plume.features.conversation.showsBareLinkPreview
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
internal fun conversationItems(state: ConversationState): List<ConversationItem> {
    // Album jsou v Telegramu samostatné zprávy, v konverzaci jedna bublina — a na řádcích, ne na
    // zprávách, stojí sousedé.
    val rows = groupAlbums(state.messages)

    // Citace se hledá v mapě, ne průchodem seznamu: v každé bublině by to bylo kvadratické.
    val byId = state.messages.associateBy { it.id }

    val readBadgeKeys = readBadgeRows(rows, state.readReceiptFor).mapTo(HashSet()) { state.keyOf(it.oldest) }

    val info = state.info
    val items = ArrayList<ConversationItem>(state.sponsored.size + rows.size + rows.size / 4 + 2)
    val usedDayKeys = HashSet<String>()

    state.sponsored.mapTo(items, ::SponsoredItem)

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
         * Hranici nepřečtených drží ViewModel z okamžiku otevření chatu, takže se předěl
         * neposouvá, jak se zprávy postupně označují za přečtené.
         */
        val startsUnread = state.unreadBoundaryId?.let { boundary ->
            row.oldest.id > boundary && olderRow != null && olderRow.newest.id <= boundary
        } == true

        items += if (message.isService) {
            ServiceMessageItem(key = key, message = message)
        } else {
            // Za předělem dne začíná nový blok, i když píše tentýž člověk.
            val olderInGroup = older.takeIf { !startsDay }
            val continuesBelow = rows.getOrNull(index - 1)?.display.continues(message)
            val delivery = message.delivery(state.lastReadOutboxMessageId)

            val frame = MessageFrameData(
                key = key,
                message = message,
                continuesAbove = olderInGroup != null && message.continues(olderInGroup),
                continuesBelow = continuesBelow,
                /*
                 * Tvář odesílatele u POSLEDNÍ zprávy jeho bloku: blok se čte shora a tvář ho
                 * uzavírá. Kanál (odesílatel = chat sám) tvář nemá — nese ji hlavička.
                 */
                avatar = when {
                    info?.isGroup != true || message.isOutgoing -> SenderAvatar.None
                    message.senderId == info.id -> SenderAvatar.None
                    continuesBelow -> SenderAvatar.Spacer
                    else -> SenderAvatar.Shown
                },
                delivery = delivery,
                badge = delivery?.takeIf { it != Delivery.Read || key in readBadgeKeys },
                highlighted = state.highlightedMessageId?.let(row::contains) == true,
                reactionsOpenable = info?.isChannel != true,
                flight = state.flight?.takeIf { it.itemKey == key },
                menu = state.messageMenu?.takeIf { it.messageId == message.id },
            )

            messageItem(
                frame = frame,
                album = row.members.takeIf { row.isAlbum },
                quoted = message.replyToMessageId?.let { id -> byId[id] ?: state.quotes[id] },
                // V soukromém chatu se jméno odesílatele u zpráv nevyplňuje, takže citace by
                // neměla koho uvést.
                partnerName = info?.title,
            )
        }

        if (startsUnread) items += UnreadDividerItem
        if (startsDay) {
            val dayKey = DayDividerItem.keyFor(row.oldest.timestamp)
                .let { if (usedDayKeys.add(it)) it else "$it-$key" }
            items += DayDividerItem(timestamp = message.timestamp, key = dayKey)
        }
    }

    if (state.loading) items += LoadingOlderItem
    return items
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
    val emojiCount = if (album != null || media != null || message.replyToMessageId != null) {
        0
    } else {
        message.text.emojiClusterCount(MAX_LARGE_EMOJI)
    }
    val preview = message.linkPreview
    return when {
        emojiCount > 0 -> LargeEmojiMessageItem(frame, emojiCount)
        showsBareMedia(message, album) -> MediaMessageItem(frame, album)
        preview != null && showsBareLinkPreview(message) -> LinkPreviewMessageItem(frame, preview)
        (media is MessageMedia.Sticker || media is MessageMedia.AnimatedEmoji) &&
            message.replyToMessageId == null -> StickerMessageItem(frame, media)
        else -> BubbleMessageItem(frame, album = album, quoted = quoted, partnerName = partnerName)
    }
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
 * Stejná strana, stejný odesílatel, stejný den. Servisní oznámení blok přeruší — kreslí se na
 * střed a tvář nad ním by visela ve vzduchu. Přerušuje ho **z obou stran**: dřív se hlídala jen
 * novější zpráva, takže bublina pod oznámením měla stažené nároží, jako by na něj navazovala.
 */
private fun ChatMessage?.continues(older: ChatMessage): Boolean =
    this != null &&
        !isService &&
        !older.isService &&
        isOutgoing == older.isOutgoing &&
        senderId == older.senderId &&
        timestamp.isSameDayAs(older.timestamp)

/**
 * Tvář odesílatele vedle bubliny ve skupině.
 *
 * [Spacer] drží místo u zpráv uprostřed bloku — bez něj by bubliny téhož člověka skákaly
 * doleva a doprava podle toho, u které zrovna tvář je.
 */
internal enum class SenderAvatar { None, Spacer, Shown }
