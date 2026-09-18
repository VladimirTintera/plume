package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.ui.senderColor

/**
 * Zpráva v bublině: text, soubor, hlasovka, popsaná příloha — a cokoli, co by jinak šlo na
 * plochu, ale nese citaci (citace bez pozadí se ztratí).
 *
 * Uvnitř bubliny se zarovnává na začátek: šířka je společná a text se čte zleva bez ohledu na to,
 * kdo ho napsal.
 */
@Immutable
internal data class BubbleMessageItem(
    override val frame: MessageFrameData,
    /** Zprávy alba od nejstarší; u samostatné zprávy `null`. */
    val album: List<ChatMessage>?,
    val quoted: ChatMessage?,
    /** Koho citace uvede v soukromém chatu, kde TDLib jméno odesílatele nevyplňuje. */
    val partnerName: String?,
) : MessageItem {

    /**
     * Text nechává protější stranu volnou: dlouhé zprávy obou stran šly přes celou šířku
     * a strany se daly rozlišit jen barvou. Google Zprávy i Telegram drží podobný okraj.
     */
    override val maxWidthFraction: Float get() = TEXT_MAX_WIDTH_FRACTION

    override fun containsMessage(messageId: Long): Boolean =
        frame.message.id == messageId || album?.any { it.id == messageId } == true

    @Composable
    override fun MessageBodyScope.Body() {
        Bubble {
            SenderName()

            quoted?.let {
                QuotedMessage(
                    message = it,
                    partnerName = partnerName,
                    onClick = message.replyToMessageId?.let { id -> { act(MessageAction.JumpTo(id)) } },
                    onLongClick = ::requestMenu,
                    // Citace někoho ze skupiny nese jeho barvu, jako jméno nad jeho zprávou. Jen
                    // v příchozí bublině — v odchozí `primary` by barva nemusela mít kontrast.
                    accent = if (!message.isOutgoing && !it.isOutgoing && it.senderName != null) {
                        senderColor(it.senderId)
                    } else {
                        accent
                    },
                )
            }

            if (album != null) {
                Album(members = album, accent = accent)
            } else {
                message.media?.let { Attachment(media = it, accent = accent) }
            }

            if (message.text.isNotBlank()) MessageText(linkColor = accent)

            message.linkPreview?.let { preview ->
                LinkPreviewCard(
                    preview = preview,
                    accent = accent,
                    // Ťuknutí jde stejnou cestou jako odkaz v textu, tedy přes ViewModel.
                    onOpen = { act(MessageAction.OpenLink(preview.url)) },
                    onLongClick = ::requestMenu,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            EditedLabel(color = onBubbleColor, modifier = Modifier.align(Alignment.End))
        }
    }
}

/** Nejvyšší šířka textové bubliny jako podíl řádku. */
private const val TEXT_MAX_WIDTH_FRACTION = 0.82f
