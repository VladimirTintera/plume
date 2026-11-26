package eu.tintera.plume.features.conversation.items

import androidx.compose.ui.layout.Layout
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import eu.tintera.plume.features.conversation.collapsesLongText
import eu.tintera.plume.core.ui.messageTextStyle
import eu.tintera.plume.resources.conversation_read_less
import eu.tintera.plume.resources.conversation_read_more
import org.jetbrains.compose.resources.StringResource
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
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
import eu.tintera.plume.core.telegram.ArticleMedia
import eu.tintera.plume.core.ui.LocalArticleMediaFlight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LoadingIndicator
import eu.tintera.plume.resources.article_read_more
import eu.tintera.plume.core.ui.contextClickable
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
                when (val media = message.media) {
                    null -> Unit
                    // Kolečko s citací nebo přeposláním: i v bublině s přepisem, jako bez ní.
                    is MessageMedia.VideoNote -> VideoNoteWithTranscript(media, accent)
                    else -> Attachment(media = media, accent = accent)
                }
            }
            (message.media as? MessageMedia.Location)?.takeIf { it.hasDetails }?.let {
                LocationDetails(it, color = onBubbleColor, modifier = Modifier.padding(top = 6.dp), onStop = stopLiveLocation(), onProximityAlert = setProximityAlert())
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
                        onOpen = { act(preview.openAction()) },
                        onLongClick = ::requestMenu,
                        modifier = modifier,
                        messageId = message.id,
                    )
                }
            }

            if (preview?.showAboveText == true) previewCard?.invoke(Modifier.padding(bottom = 6.dp))
            when {
                message.isRestricted -> RestrictedNote()
                message.article?.media.orEmpty().isNotEmpty() -> ArticleText()
                message.text.isNotBlank() || message.description != null -> QuotableText()
            }
            if (message.article?.isPartial == true && !message.isRestricted) ArticleReadMore()
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
     * Článek s médii: text po úsecích a mezi nimi fotka, video nebo GIF na místě, kde v článku
     * stojí (viz `articleSegments`). Text zprávy zůstává jeden, takže kopírování, hledání i citace
     * pracují nad celým článkem; rozdělí se jen to, co se kreslí.
     *
     * Pás citace dosedne na okraj bubliny jen u prvního úseku nahoře a posledního dole — mezi
     * úseky stojí médium.
     */
    @Composable
    private fun MessageBodyScope.ArticleText() {
        val article = message.article ?: return
        val segments = remember(message.text, message.entities, article.media) {
            articleSegments(message.text, message.entities, article.media)
        }
        val atTop = quoted == null && album == null && message.media == null &&
            !message.hasOriginLabel && (continuesAbove || message.senderName == null)
        val atBottom = message.linkPreview == null && !message.isEdited && !article.isPartial
        segments.forEachIndexed { i, segment ->
            when (segment) {
                is ArticleSegment.Text -> MessageText(
                    linkColor = accent,
                    bleedTop = if (i == 0 && atTop) MESSAGE_PADDING_V else QUOTE_EDGE_TIGHT,
                    bleedBottom = if (i == segments.lastIndex && atBottom) contentBottom else QUOTE_EDGE_TIGHT,
                    segment = segment,
                )
                is ArticleSegment.Media -> {
                    val gap = Modifier.padding(
                        top = if (i == 0) 0.dp else ARTICLE_MEDIA_GAP,
                        bottom = if (i == segments.lastIndex) 0.dp else ARTICLE_MEDIA_GAP,
                    )
                    val cells = segment.media.map { AlbumCell(message.id, it.media, articleIndex = it.index) }
                    when {
                        segment.media.size == 1 -> ArticleMediaView(segment.media.single(), gap)
                        // Koláž jako mřížka alba. GIF ani nic jiného mimo fotky a videa do mřížky
                        // nepatří — pak zůstanou pod sebou.
                        cells.allTiles() -> Box(gap) {
                            AlbumGrid(
                                cells = cells,
                                onOpen = { act(MessageAction.OpenArticleMedia(it.articleIndex)) },
                                onRequestMenu = { requestMenu() },
                                onCancelUpload = {},
                            )
                        }
                        else -> Column(gap, verticalArrangement = Arrangement.spacedBy(ARTICLE_MEDIA_GAP)) {
                            segment.media.forEach { ArticleMediaView(it, Modifier) }
                        }
                    }
                }
            }
        }
    }

    /**
     * Médium článku. Klíč letu do prohlížeče nese pořadí média ([LocalArticleMediaFlight]): pod
     * jedním id zprávy je jich v článku víc, stejně jako v anketě.
     */
    @Composable
    private fun MessageBodyScope.ArticleMediaView(media: ArticleMedia, modifier: Modifier) {
        Box(modifier.fillMaxWidth()) {
            CompositionLocalProvider(LocalArticleMediaFlight provides media.index) {
                MessageAttachment(
                    media = media.media,
                    messageId = message.id,
                    onOpen = { act(MessageAction.OpenArticleMedia(media.index)) },
                    onRequestMenu = ::requestMenu,
                    file = FileTransfer(
                        documentProgress = items::documentProgress,
                        onOpenDocument = { act(MessageAction.OpenArticleDocument(media.index)) },
                    ),
                    // Zvuk a hlasovka v článku: tentýž přehrávač, klíč s pořadím v článku.
                    voice = VoiceHandlers(
                        playback = { items.voicePlayback(message.id, part = media.index) },
                        speed = { items.voiceSpeed() },
                        onToggle = { act(MessageAction.ToggleArticleAudio(media.index)) },
                        onSeek = { fraction -> act(MessageAction.SeekArticleAudio(media.index, fraction)) },
                        onCycleSpeed = { act(MessageAction.CycleVoiceSpeed) },
                        accent = accent,
                        onAccent = if (message.isOutgoing) bubbleColor else MaterialTheme.colorScheme.onPrimary,
                    ),
                    accent = accent,
                    // Článek je široký jako bublina; obrázek ji vyplní, jinak by vedle zúžené
                    // fotky zůstalo prázdné místo — tatáž úvaha jako u přílohy ankety.
                    fillWidth = true,
                )
            }
        }
    }

    /**
     * „Číst dál" pod useknutým článkem. Dlouhý článek posílá TDLib jen zčásti a zbytek vydá až
     * na požádání (`getFullRichMessage`); bez tlačítka by článek končil uprostřed a nebylo by
     * poznat proč. Ve stejném jazyce tvaru jako rozbalovač sbalitelné citace.
     */
    @Composable
    private fun MessageBodyScope.ArticleReadMore() {
        val loading = items.articleLoading(message.id)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .padding(top = MESSAGE_PADDING_V)
                .offset(x = -EXPANDER_TOUCH_H)
                .clip(RoundedCornerShape(QUOTE_EXPANDER_CORNER))
                .contextClickable(
                    enabled = !loading,
                    onLongClick = ::requestMenu,
                    onClick = { act(MessageAction.ReadFullArticle) },
                )
                .padding(horizontal = EXPANDER_TOUCH_H, vertical = EXPANDER_TOUCH_V),
        ) {
            Text(
                text = stringResource(Res.string.article_read_more),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = accent,
            )
            if (loading) {
                LoadingIndicator(color = accent, modifier = Modifier.size(QUOTE_EXPANDER_ICON))
            } else {
                Icon(
                    painter = painterResource(Res.drawable.keyboard_arrow_down_24dp),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(QUOTE_EXPANDER_ICON),
                )
            }
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
            LongText(atTop)
            return
        }

        var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
        /*
         * Že se text nevešel, se pozná až z layoutu — a po rozbalení se vejde vždycky, takže by
         * rozbalovač zmizel a nešlo by sbalit zpátky. Rozbalená citace proto drží, co bylo.
         *
         * **Sbalená bere POSLEDNÍ měření, ne kterékoli.** Čerstvě odeslaná zpráva se poprvé měří
         * v jiné šířce (přilétá z pole) a to měření přetečení nahlásí i u jednoho řádku. Dokud se
         * příznak jen zapínal, zůstalo u krátké citace „Zobrazit víc" viset, dokud se chat
         * neotevřel znovu.
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
                    Modifier.contextClickable(
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
                onOverflow = { if (!expanded) overflowed = it },
                wholeTextQuote = true,
            )
        }
        if (overflowed) QuoteExpander(expanded = expanded, onClick = toggle)
    }
}

/**
 * Kolik seznamu konverzace nahoře zakrývá lišta a pruhy nad ním (jeho horní `contentPadding`).
 * Čte ho jen [LongText], aby místo ořezu po rozbalení neskončilo pod nimi.
 */
internal val LocalConversationTopInset = compositionLocalOf { 0.dp }

/**
 * Text zprávy, který se po [COLLAPSED_TEXT_LINES] řádcích sbalí s „Číst dál" pod sebou.
 *
 * Dlouhá zpráva (vložený článek, přepis, výpis z logu) zabrala i několik obrazovek a kdo ji
 * číst nechtěl, musel přes ni rolovat — v obou směrech. Sbalená ukáže začátek a zbytek nabídne.
 *
 * - **Že se nevešla, říká layout** (`onOverflow`), ne počet znaků: záleží na šířce bubliny,
 *   velikosti písma i zalomení. Rozbalená drží, co bylo — jinak by po rozbalení „Sbalit" zmizelo
 *   (rozbalená se vejde vždycky). Sbalená bere POSLEDNÍ měření, viz sbalitelná citace.
 * - **Rozbalení se dorovná, aby se dalo číst dál.** Seznam konverzace je obrácený a drží spodní
 *   hranu: zpráva roste NAHORU, rozbalovač zůstane na místě a nad ním se objeví KONEC zprávy —
 *   řádek, u kterého se čtení zastavilo, odjede nahoru z obrazovky. Proto se po rozbalení
 *   požádá o zobrazení místa ořezu (`bringIntoViewRequester`); seznam ho dorolluje co nejméně,
 *   takže když je vidět, nestane se nic.
 * - **Nesbaluje se zpráva s nálezem hledání ani článek** ([collapsesLongText]).
 */
@Composable
internal fun MessageBodyScope.LongText(atTop: Boolean) {
    val collapsible = collapsesLongText(message, items.searchHighlight())
    var expanded by rememberSaveable(message.id) { mutableStateOf(false) }
    var overflowed by remember(message.id) { mutableStateOf(false) }
    val readMore = remember { BringIntoViewRequester() }
    var collapsedHeight by remember(message.id) { mutableIntStateOf(0) }
    val lineHeight = with(LocalDensity.current) { messageTextStyle().lineHeight.toPx() }
    val obscuredTop = with(LocalDensity.current) { LocalConversationTopInset.current.toPx() }
    val shown = !collapsible || expanded || !overflowed
    LaunchedEffect(expanded) {
        if (!expanded || collapsedHeight == 0) return@LaunchedEffect
        // Až po přeměření: rozbalený text musí být v layoutu, jinak by se ukazovalo místo,
        // které ještě neexistuje.
        withFrameNanos { }
        readMore.bringIntoView(
            Rect(
                left = 0f,
                // Seznam zarovná obdélník k SVÉ hraně, a ta je pod lištou a pruhy; o jejich výšku
                // výš, jinak by místo ořezu skončilo právě pod nimi.
                top = collapsedHeight - READ_MORE_CONTEXT_LINES * lineHeight - obscuredTop,
                right = 1f,
                bottom = collapsedHeight + READ_MORE_CONTEXT_LINES * lineHeight,
            ),
        )
    }
    Box(
        Modifier
            .bringIntoViewRequester(readMore)
            .onSizeChanged { if (!expanded) collapsedHeight = it.height },
    ) {
        MessageText(
            linkColor = accent,
            maxLines = if (collapsible && !expanded) COLLAPSED_TEXT_LINES else Int.MAX_VALUE,
            /*
             * Bez výpustky: Android ji na posledním řádku nad `maxLines` dělá ZÁMĚNOU znaku, i když
             * se řádek vejde — „Řádek20" se ukázal jako „Řádek2…" a vypadal jako překlep.
             * Pokračování ohlásí „Číst dál" pod textem.
             */
            overflow = TextOverflow.Clip,
            bleedTop = if (atTop) MESSAGE_PADDING_V else QUOTE_EDGE_TIGHT,
            // S rozbalovačem pod textem pás citace na okraj bubliny nesahá.
            bleedBottom = if (shown && message.linkPreview == null && !message.isEdited) contentBottom else QUOTE_EDGE_TIGHT,
            onOverflow = if (collapsible) {
                { if (!expanded) overflowed = it }
            } else {
                null
            },
        )
    }
    if (collapsible && overflowed) {
        QuoteExpander(
            expanded = expanded,
            onClick = { expanded = !expanded },
            expandLabel = Res.string.conversation_read_more,
            collapseLabel = Res.string.conversation_read_less,
            topPadding = MESSAGE_PADDING_V,
        )
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
            ?: message.externalReply?.target?.let { target -> { act(MessageAction.OpenForwardOrigin(target)) } }
            // Odpověď na příběh otevře příběh sám, ne chat jeho autora.
            ?: (message.externalReply?.quote?.media as? MessageMedia.Story)
                ?.let { story -> { act(MessageAction.OpenStory(story.posterChatId, story.storyId)) } },
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
private fun MessageBodyScope.QuoteExpander(
    expanded: Boolean,
    onClick: () -> Unit,
    expandLabel: StringResource = Res.string.conversation_quote_expand,
    collapseLabel: StringResource = Res.string.conversation_quote_collapse,
    /** U citace dvojnásobné (viz níž); pod obyčejným textem žádný pás není. */
    topPadding: Dp = MESSAGE_PADDING_V * 2,
) {
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
            .padding(top = topPadding)
            // Dotyková plocha je větší než text, ale popisek musí zůstat v jeho sloupci.
            .offset(x = -EXPANDER_TOUCH_H)
            .clip(RoundedCornerShape(QUOTE_EXPANDER_CORNER))
            .contextClickable(onLongClick = ::requestMenu, onClick = onClick)
            .padding(horizontal = EXPANDER_TOUCH_H, vertical = EXPANDER_TOUCH_V),
    ) {
        Text(
            text = stringResource(
                if (expanded) collapseLabel else expandLabel,
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
 * Na kolik řádků se sbalí dlouhá zpráva. Zhruba polovina obrazovky telefonu: dost na to, aby
 * se z ní dalo poznat, o čem je, a dost málo, aby nepřekryla všechno kolem.
 */
internal const val COLLAPSED_TEXT_LINES = 20

/** Kolik řádků kolem místa ořezu se po rozbalení ukáže — aby bylo vidět, kde čtení navazuje. */
private const val READ_MORE_CONTEXT_LINES = 2

/** Mezera mezi médiem článku a textem nad ním a pod ním. */
private val ARTICLE_MEDIA_GAP = 8.dp

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
