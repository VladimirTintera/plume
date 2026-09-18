package eu.tintera.plume.features.conversation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SponsoredMessage
import eu.tintera.plume.core.ui.localDate

/**
 * Blok v seznamu konverzace: nese svoje data a **sám se vykreslí**.
 *
 * Seznam o druzích bloků nic neví — jen volá [Content]. O tom, JAKÝ blok ze zprávy vznikne,
 * rozhoduje jediné místo, builder [conversationItems]; o tom, jak vypadá, blok sám. Postup
 * a důvody jsou v `docs/architektura-konverzace.md`.
 *
 * **Lambdy do dat bloku nepatří.** Rozbily by `equals` a s ním přeskakování položek, které se
 * nezměnily. Obsluha a rychle se měnící hodnoty jdou přes [ConversationItemScope].
 *
 * [Content] je nad [LazyItemScope], aby si blok sám řekl o animaci položky (`animateItem`) —
 * cíl letu odeslané zprávy se třeba nesmí zjevovat prolnutím.
 */
@Immutable
internal interface ConversationItem {
    /** Klíč položky v `LazyColumn`. Číselný mají jen zprávy — hlášení přečtení na tom stojí. */
    val key: Any

    /** Podle čeho `LazyColumn` recykluje kompozice. */
    val contentType: Any get() = this::class

    /** Patří zpráva s [messageId] k tomuhle bloku? Skok na zprávu podle toho hledá položku. */
    fun containsMessage(messageId: Long): Boolean = false

    @Composable
    fun LazyItemScope.Content(scope: ConversationItemScope)
}

/**
 * Co blok potřebuje od obrazovky: obsluhu a hodnoty, které se mění rychleji, než by se vyplatilo
 * přestavovat seznam bloků.
 *
 * Hodnoty jsou funkce, ne vlastnosti: čtou se až v tom kusu UI, který je kreslí, takže změna
 * pozice hlasovky překreslí jen ji, ne celou položku.
 */
@Stable
internal interface ConversationItemScope {
    /** Posun odkrývání časů tahem doleva; čte se až při kreslení vrstvy. */
    val timeReveal: () -> Float
    val menuHandlers: MessageMenuHandlers

    fun voicePlayback(messageId: Long): VoicePlayback?
    fun documentProgress(fileId: Int): Float?

    fun openMedia(messageId: Long)
    fun openStickerSet(setId: Long)
    fun openDocument(messageId: Long)
    fun cancelUpload(messageId: Long)
    fun toggleVoice(messageId: Long)
    fun seekVoice(messageId: Long, fraction: Float)
    fun jumpToMessage(messageId: Long)
    fun toggleReadReceipt(messageId: Long)
    fun showReactionDetails(messageId: Long)
    fun showMenu(messageId: Long)
    fun replyTo(messageId: Long)
    fun openLink(url: String)
    fun openSponsored(sponsored: SponsoredMessage)
}

/**
 * Předěl dne.
 *
 * Klíč je **datum**, ne zpráva: když se dotáhne starší stránka a předěl se přestěhuje nad starší
 * zprávu téhož dne, zůstane to tatáž položka a jen se posune. [key] přesto nese i klíč zprávy
 * pro případ, že by se den v seznamu objevil dvakrát (posunuté hodiny odesílatele) — dva stejné
 * klíče `LazyColumn` shodí.
 */
internal data class DayDividerItem(val timestamp: Int, override val key: String) : ConversationItem {
    @Composable
    override fun LazyItemScope.Content(scope: ConversationItemScope) {
        DayDivider(timestamp, Modifier.animateItem())
    }

    companion object {
        fun keyFor(timestamp: Int): String = "day-${timestamp.localDate()}"
    }
}

/** Předěl „Nepřečtené zprávy". V seznamu je nejvýš jeden. */
internal data object UnreadDividerItem : ConversationItem {
    override val key: Any = "unread"

    @Composable
    override fun LazyItemScope.Content(scope: ConversationItemScope) {
        UnreadDivider(Modifier.animateItem())
    }
}

/**
 * Systémové oznámení o dění v chatu. Nemá odesílatele, takže ani stranu, bublinu, reakce
 * ani stav doručení.
 */
internal data class ServiceMessageItem(
    override val key: Long,
    val message: ChatMessage,
) : ConversationItem {
    override fun containsMessage(messageId: Long): Boolean = message.id == messageId

    @Composable
    override fun LazyItemScope.Content(scope: ConversationItemScope) {
        RevealingTime(message.timestamp, scope.timeReveal, Modifier.animateItem()) {
            ServiceMessage(message)
        }
    }
}

/**
 * Zpráva v bublině (nebo bez ní — samolepka, velká emoji, holé médium).
 *
 * Přechodný blok: obaluje dosavadní [MessageBubble] a dodává jí data, která dřív počítala
 * obrazovka v `itemsIndexed`. V dalším kroku se rozpadne na bloky podle druhu obsahu.
 */
internal data class BubbleMessageItem(
    override val key: Long,
    /** Co bublina kreslí; u alba primární zpráva s časem a stavem celého alba (viz [MessageRow]). */
    val message: ChatMessage,
    /** Zprávy alba od nejstarší; u samostatné zprávy `null`. */
    val album: List<ChatMessage>?,
    val quoted: ChatMessage?,
    val partnerName: String?,
    val continuesAbove: Boolean,
    val continuesBelow: Boolean,
    val avatar: SenderAvatar,
    val lastReadOutboxMessageId: Long,
    val showsRead: Boolean,
    val highlighted: Boolean,
    /** V kanálu detail reakcí není (`BROADCAST_FORBIDDEN`), čip se proto nedá ťuknout. */
    val reactionsOpenable: Boolean,
    val flight: MessageFlight?,
    val menu: MessageMenu?,
) : ConversationItem {

    override fun containsMessage(messageId: Long): Boolean =
        message.id == messageId || album?.any { it.id == messageId } == true

    @Composable
    override fun LazyItemScope.Content(scope: ConversationItemScope) {
        // Cíl letu se nezjevuje prolnutím: během letu se kreslí v překryvu sdílených prvků, kam
        // průhlednost položky nedosáhne, a po přistání by skočil z plné viditelnosti do
        // rozjetého prolnutí.
        val modifier = Modifier.animateItem(
            fadeInSpec = if (flight != null) null else spring(stiffness = Spring.StiffnessMediumLow),
        )
        RevealingTime(message.timestamp, scope.timeReveal, modifier) {
            MessageBubble(
                message = message,
                album = album,
                onOpenAlbumItem = scope::openMedia,
                onCancelUpload = scope::cancelUpload,
                documentProgress = scope::documentProgress,
                onOpenDocument = scope::openDocument,
                voicePlayback = { scope.voicePlayback(message.id) },
                onToggleVoice = { scope.toggleVoice(message.id) },
                onSeekVoice = { fraction -> scope.seekVoice(message.id, fraction) },
                quoted = quoted,
                partnerName = partnerName,
                highlighted = highlighted,
                onQuoteClick = message.replyToMessageId?.let { id -> { scope.jumpToMessage(id) } },
                continuesAbove = continuesAbove,
                continuesBelow = continuesBelow,
                avatar = avatar,
                lastReadOutboxMessageId = lastReadOutboxMessageId,
                showsRead = showsRead,
                onToggleReadReceipt = { scope.toggleReadReceipt(message.id) },
                flight = flight,
                onOpenReactions = if (reactionsOpenable) scope::showReactionDetails else null,
                onRequestMenu = { scope.showMenu(message.id) },
                onReply = { scope.replyTo(message.id) },
                // Ťuknutí na samolepku otevře její sadu; na fotku prohlížeč.
                onOpenMedia = {
                    val setId = (message.media as? MessageMedia.Sticker)?.setId
                    when {
                        setId == null -> scope.openMedia(message.id)
                        setId != 0L -> scope.openStickerSet(setId)
                    }
                },
                // Nabídka se kreslí uvnitř bubliny, které se týká — jinak by neměla k čemu se
                // ukotvit.
                menu = menu,
                menuHandlers = scope.menuHandlers,
                onLinkClick = scope::openLink,
            )
        }
    }
}

/**
 * Sponzorovaná zpráva. Klíč s předponou [SPONSORED_KEY_PREFIX] hledá hlášení zhlédnutí.
 *
 * Jede s celou plochou při odkrývání časů, jen bez času — reklama žádný nemá. Kdyby stála,
 * vypadalo by to, že se odkrývání zaseklo.
 */
internal data class SponsoredItem(val sponsored: SponsoredMessage) : ConversationItem {
    override val key: Any = SPONSORED_KEY_PREFIX + sponsored.messageId

    @Composable
    override fun LazyItemScope.Content(scope: ConversationItemScope) {
        SponsoredCard(
            sponsored = sponsored,
            onClick = { scope.openSponsored(sponsored) },
            modifier = Modifier
                .graphicsLayer { translationX = -scope.timeReveal() }
                .padding(vertical = 8.dp),
        )
    }
}

/** Načítání starší historie; stojí nahoře, tedy na konci obráceného seznamu. */
internal data object LoadingOlderItem : ConversationItem {
    override val key: Any = "loading"

    @Composable
    override fun LazyItemScope.Content(scope: ConversationItemScope) {
        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
    }
}

internal const val SPONSORED_KEY_PREFIX = "sponsored-"
