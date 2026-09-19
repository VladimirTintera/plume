package eu.tintera.plume.features.conversation.items

import androidx.compose.ui.layout.Layout
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
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
        val quote = quoted ?: return Bubble { Content() }

        /*
         * Odpověď je KARTA ZE DVOU ČÁSTÍ, stejně jako náhled odkazu: nahoře citace v tónované
         * ploše, pod ní odpověď v barvě bubliny. Dřív byla citace subkartička uvnitř bubliny —
         * zaoblená plocha v ploše, dvě vnořené karty.
         */
        QuotedBubble(
            quote = {
                QuotedMessage(
                    message = quote,
                    partnerName = partnerName,
                    onClick = message.replyToMessageId?.let { id -> { act(MessageAction.JumpTo(id)) } },
                    onLongClick = ::requestMenu,
                    // Citace někoho ze skupiny nese jeho barvu, jako jméno nad jeho zprávou;
                    // jinak akcent tónované plochy.
                    accent = if (!quote.isOutgoing && quote.senderName != null) {
                        senderColor(quote.senderId)
                    } else {
                        MaterialTheme.colorScheme.tertiary
                    },
                    contained = false,
                )
            },
            content = { Content() },
        )
    }

    /** Obsah bubliny: jméno, příloha nebo album, text, náhled odkazu, „upraveno". */
    @Composable
    private fun MessageBodyScope.Content() {
        Column {
            SenderName()

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

/**
 * Karta odpovědi: [quote] nahoře v tónované ploše ([insetContainerColor]), [content] pod ní
 * v barvě bubliny.
 *
 * **Vlastní `Layout`, ne `IntrinsicSize`.** Obě plochy musí mít šířku té širší části, a pod
 * citací může být fotka nebo album v `BoxWithConstraints`, který intrinsické míry neumí (viz
 * „Gotchas"). Citace se změří v přirozené šířce, obsah pak dostane aspoň tu šířku jako minimum
 * (takže „upraveno" stojí u pravého okraje karty), a plochy kreslí karta sama za obsahem.
 */
@Composable
internal fun MessageBodyScope.QuotedBubble(
    quote: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val quoteColor = insetContainerColor()
    val onQuoteColor = MaterialTheme.colorScheme.onTertiaryContainer
    val bubble = bubbleColor
    // Výška citace z měření pro kreslení ploch. Pole, ne stav: kreslí se až po layoutu téhož snímku.
    val split = remember { floatArrayOf(0f) }
    Layout(
        contents = listOf(
            { CompositionLocalProvider(LocalContentColor provides onQuoteColor) { quote() } },
            { CompositionLocalProvider(LocalContentColor provides onBubbleColor) { PaddedColumn(content = content) } },
        ),
        modifier = Modifier.drawBehind {
            drawRect(quoteColor, size = Size(size.width, split[0]))
            drawRect(bubble, topLeft = Offset(0f, split[0]), size = Size(size.width, size.height - split[0]))
        },
    ) { (quoteMeasurables, contentMeasurables), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val quotePlaceable = quoteMeasurables.first().measure(loose)
        val contentPlaceable = contentMeasurables.first().measure(
            loose.copy(minWidth = quotePlaceable.width.coerceAtMost(loose.maxWidth)),
        )
        split[0] = quotePlaceable.height.toFloat()
        layout(maxOf(quotePlaceable.width, contentPlaceable.width), quotePlaceable.height + contentPlaceable.height) {
            quotePlaceable.place(0, 0)
            contentPlaceable.place(0, quotePlaceable.height)
        }
    }
}

/** Nejvyšší šířka textové bubliny jako podíl řádku. */
internal const val TEXT_MAX_WIDTH_FRACTION = 0.82f
