package eu.tintera.plume.features.conversation.items

import androidx.compose.ui.layout.Layout
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import eu.tintera.plume.features.conversation.COLLAPSED_QUOTE_LINES
import eu.tintera.plume.features.conversation.collapsesQuote
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.block_24dp
import eu.tintera.plume.resources.conversation_quote_collapse
import eu.tintera.plume.resources.conversation_quote_expand
import eu.tintera.plume.resources.keyboard_arrow_down_24dp
import eu.tintera.plume.resources.keyboard_arrow_up_24dp
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import eu.tintera.plume.core.ui.accentOn
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
            quote = { ReplyQuote(quote, partnerName) },
            content = { Content() },
        )
    }

    /** Obsah bubliny: jméno, příloha nebo album, text, náhled odkazu, „upraveno". */
    @Composable
    private fun MessageBodyScope.Content() {
        Column {
            SenderName(background = bubbleColor)
            OriginLabel()

            if (album != null) {
                Album(members = album, accent = accent)
            } else {
                message.media?.let { Attachment(media = it, accent = accent) }
            }
            (message.media as? MessageMedia.Location)?.takeIf { it.hasDetails }?.let {
                LocationDetails(it, color = onBubbleColor, modifier = Modifier.padding(top = 6.dp))
            }

            // Náhled patří nad text, když si to přál odesílatel (`showAboveText`) — mezera
            // pak jde na druhou stranu, ať karta nikdy nedoléhá k textu.
            val preview = message.linkPreview
            val previewCard: (@Composable (Modifier) -> Unit)? = preview?.let {
                { modifier ->
                    LinkPreviewCard(
                        preview = preview,
                        accent = accent,
                        // Ťuknutí jde stejnou cestou jako odkaz v textu, tedy přes ViewModel.
                        onOpen = { act(MessageAction.OpenLink(preview.url)) },
                        onLongClick = ::requestMenu,
                        modifier = modifier,
                    )
                }
            }

            if (preview?.showAboveText == true) previewCard?.invoke(Modifier.padding(bottom = 6.dp))
            when {
                message.isRestricted -> RestrictedNote()
                message.text.isNotBlank() -> QuotableText()
            }
            if (preview?.showAboveText != true) previewCard?.invoke(Modifier.padding(top = 6.dp))

            // Živou polohu posouvá úprava zprávy; „upraveno" by u ní svítilo pořád.
            if (!message.isLiveLocation) EditedLabel(color = onBubbleColor, modifier = Modifier.align(Alignment.End))
        }
    }

    /**
     * Důvod, proč server obsah tady nedovolí ukázat. Ikona a ztlumená barva říkají, že to NEPSAL
     * odesílatel — holý text v bublině by se četl jako jeho zpráva.
     */
    @Composable
    private fun MessageBodyScope.RestrictedNote() {
        Row(
            Modifier.padding(horizontal = MESSAGE_PADDING_H, vertical = MESSAGE_PADDING_V),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(Res.drawable.block_24dp),
                contentDescription = null,
                tint = onBubbleColor.copy(alpha = RESTRICTED_ALPHA),
                modifier = Modifier.padding(end = 8.dp).size(18.dp),
            )
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = onBubbleColor.copy(alpha = RESTRICTED_ALPHA),
            )
        }
    }

    /**
     * Text zprávy — a když je celá sbalitelná citace, pás citace nahoře a rozbalovač pod ním.
     *
     * Pás dosedne na okraj bubliny jen tam, kde nad textem (pod ním) opravdu nic nestojí; jinak
     * by přelezl přes šev hlavičky citované zprávy nebo přes značku přeposlání — `drawBehind` se
     * na hranice textu neořezává (viz `RichText`).
     */
    @Composable
    private fun MessageBodyScope.QuotableText() {
        val atTop = quoted == null && album == null && message.media == null &&
            !message.hasOriginLabel && (continuesAbove || message.senderName == null)
        val collapses = remember(message.id, message.text, message.entities) { collapsesQuote(message) }
        if (!collapses) {
            MessageText(
                linkColor = accent,
                bleedTop = if (atTop) MESSAGE_PADDING_V else QUOTE_EDGE_TIGHT,
                bleedBottom = if (message.linkPreview == null && !message.isEdited) contentBottom else QUOTE_EDGE_TIGHT,
            )
            return
        }

        var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
        /*
         * Že se text nevešel, se pozná až z layoutu — a po rozbalení se vejde vždycky, takže by
         * rozbalovač zmizel a nešlo by sbalit zpátky. Drží se proto, že se to stalo.
         */
        var overflowed by remember(message.id) { mutableStateOf(false) }
        /*
         * Rozbaluje a sbaluje i ŤUKNUTÍ NA SAMOTNOU CITACI, ne jen na rozbalovač — tak to dělá
         * i oficiální klient a pruh tří řádků je mnohem větší cíl než jeden řádek popisku.
         *
         * Bez vlnky: viditelná odezva je samo rozbalení, a vlnka by se kreslila přes text
         * v obdélníku, který se s pásem (ten jde od kraje bubliny) nekryje. Dlouhý stisk se
         * opakuje, jinak by nad citací nešla otevřít nabídka nad zprávou. Odkazy uvnitř si
         * ťuknutí berou samy, takže o ně tenhle obal nepřijde.
         */
        val toggle = { expanded = !expanded }
        Box(
            Modifier.then(
                if (!overflowed) {
                    Modifier
                } else {
                    Modifier.combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onLongClick = ::requestMenu,
                        onClick = toggle,
                    )
                },
            ),
        ) {
            MessageText(
                linkColor = accent,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_QUOTE_LINES,
                bleedTop = if (atTop) MESSAGE_PADDING_V else QUOTE_EDGE_TIGHT,
                // Dolů pás na okraj bubliny nesahá — pod ním stojí rozbalovač na její běžné
                // ploše —, ale odsazení uvnitř má stejné jako nahoře.
                bleedBottom = MESSAGE_PADDING_V,
                onOverflow = { if (it) overflowed = true },
                wholeTextQuote = true,
            )
        }
        if (overflowed) QuoteExpander(expanded = expanded, onClick = toggle)
    }
}

/**
 * Citovaná zpráva nad odpovědí — horní díl karty [QuotedBubble]. Sdílí ji bublina i anketa.
 * Ťuknutí skočí na citovanou zprávu, dlouhý stisk otevře nabídku (citace má vlastní klikání).
 */
@Composable
internal fun MessageBodyScope.ReplyQuote(quote: ChatMessage, partnerName: String?) {
    QuotedMessage(
        message = quote,
        partnerName = partnerName,
        // Citace z jiného chatu vede do toho chatu, stejně jako značka „Přeposláno od".
        onClick = message.replyToMessageId?.let { id -> { act(MessageAction.JumpTo(id)) } }
            ?: message.externalReply?.target?.let { target -> { act(MessageAction.OpenForwardOrigin(target)) } },
        onLongClick = ::requestMenu,
        quoteText = message.replyQuote,
        // Citace někoho ze skupiny nese jeho barvu, jako jméno nad jeho zprávou; jinak akcent
        // tónované plochy. Obojí projde kontrolou proti té ploše — ve zvýrazněné zprávě se
        // `tertiary` s `tertiaryContainer` může potkat na téže světlosti (viz accentOn).
        accent = accentOn(
            background = insetColor,
            accent = if (!quote.isOutgoing && quote.senderName != null) {
                senderColor(quote.senderId)
            } else {
                MaterialTheme.colorScheme.tertiary
            },
            fallback = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        contained = false,
    )
}

/**
 * Rozbalovač sbalitelné citace: leží POD pásem, na běžné ploše bubliny — citace je vrchní část
 * zprávy, tohle je ovládání k ní.
 *
 * Dlouhý stisk se opakuje: řádek má vlastní klikání, a bez toho by nad ním nešla otevřít nabídka
 * nad zprávou (tatáž past jako u citace a značky přeposlání).
 */
@Composable
private fun MessageBodyScope.QuoteExpander(expanded: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            /*
             * Odsazení je DVOJNÁSOBNÉ, protože pás si svoje spodní odsazení kreslí POD posledním
             * řádkem citace — první polovina tedy padne na něj a teprve druhá je mezera, kterou
             * je vidět. S jedním [MESSAGE_PADDING_V] seděl rozbalovač pásu na okraji: nad textem
             * 5,7 dp, pod ním 15,2 dp.
             *
             * Dotykové odsazení se od toho NEODEČÍTÁ: pod popiskem leží taky (a k němu ještě
             * spodní odsazení bubliny), takže se tím obě mezery srovnají na 12 dp.
             */
            .padding(top = MESSAGE_PADDING_V * 2)
            // Dotyková plocha je větší než text, ale popisek musí zůstat v jeho sloupci.
            .offset(x = -EXPANDER_TOUCH_H)
            .clip(RoundedCornerShape(QUOTE_EXPANDER_CORNER))
            .combinedClickable(onLongClick = ::requestMenu, onClick = onClick)
            .padding(horizontal = EXPANDER_TOUCH_H, vertical = EXPANDER_TOUCH_V),
    ) {
        Text(
            text = stringResource(
                if (expanded) Res.string.conversation_quote_collapse else Res.string.conversation_quote_expand,
            ),
            /*
             * Zvýrazněný řez, ne jen barva: v ODCHOZÍ bublině je akcent barva bubliny, takže
             * `accentOn` ustoupí barvě textu a rozbalovač by se od zprávy lišil jen šipkou.
             * Tatáž úvaha jako u podtržení odkazů tam, kde barva splývá.
             */
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = accent,
        )
        Icon(
            painter = painterResource(
                if (expanded) Res.drawable.keyboard_arrow_up_24dp else Res.drawable.keyboard_arrow_down_24dp,
            ),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(QUOTE_EXPANDER_ICON),
        )
    }
}

/**
 * Odsazení pásu tam, kde citace sice končí u kraje textu, ale nad ním (pod ním) něco stojí —
 * jméno odesílatele, značka přeposlání, hlavička citované zprávy. Plné [MESSAGE_PADDING_V] by pás
 * protáhlo pod jejich písmo: `drawBehind` se na hranice textu neořezává.
 */
private val QUOTE_EDGE_TIGHT = MESSAGE_PADDING_V / 2

private val QUOTE_EXPANDER_CORNER = 6.dp

/** O kolik je dotyková plocha rozbalovače větší než jeho popisek. */
private val EXPANDER_TOUCH_V = 4.dp

private val EXPANDER_TOUCH_H = 6.dp

private val QUOTE_EXPANDER_ICON = 18.dp

/**
 * Karta odpovědi: [quote] nahoře v tónované ploše ([MessageBodyScope.insetColor]), [content] pod ní
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
    val quoteColor = insetColor
    val onQuoteColor = MaterialTheme.colorScheme.onTertiaryContainer
    val bubble = bubbleColor
    // Výška citace z měření pro kreslení ploch. Pole, ne stav: kreslí se až po layoutu téhož snímku.
    val split = remember { floatArrayOf(0f) }
    Layout(
        contents = listOf(
            { CompositionLocalProvider(LocalContentColor provides onQuoteColor) { quote() } },
            {
                CompositionLocalProvider(
                    LocalContentColor provides onBubbleColor,
                    LocalTextSelectionColors provides selectionColors,
                ) { PaddedColumn(content = content) }
            },
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

/** Ztlumení důvodu omezení — pořád nad AA, jen ustoupí vlastnímu textu zpráv. */
private const val RESTRICTED_ALPHA = 0.8f
