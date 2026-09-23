package eu.tintera.plume.core.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.format_quote_24dp
import org.jetbrains.compose.resources.painterResource
import eu.tintera.plume.core.telegram.MessageEntity
import eu.tintera.plume.core.telegram.MessageEntityType

/**
 * Text zprávy i s formátováním, které k němu Telegram posílá.
 *
 * Telegram markup neposílá — text je holý řetězec a formát je seznam rozsahů nad ním
 * ([MessageEntity]). Tady se z toho skládá `AnnotatedString`: text se připojí jednou a každá
 * entita jen přidá svůj styl na svůj úsek. Díky tomu se **vnořené rozsahy vyřeší samy** (tučné
 * uvnitř odkazu je prostě dva styly přes sebe) a nemusí se skládat strom.
 *
 * Bez entit ([entities] prázdné) je výsledek k nerozeznání od obyčejného [Text] — což je většina
 * zpráv, takže se tu nic nepředpočítává dopředu.
 */
@Composable
fun RichText(
    text: String,
    entities: List<MessageEntity>,
    style: TextStyle,
    modifier: Modifier = Modifier,
    /**
     * Barva odkazu.
     *
     * **Musí jít předat.** Uvnitř odchozí bubliny je pozadí `primary`, takže natvrdo zadaná
     * `primary` by z odkazu udělala neviditelný text na stejné barvě — tatáž past jako
     * u citace. Podtržení nese odlišení i tam, kde barva splývá s okolním textem.
     */
    linkColor: Color = MaterialTheme.colorScheme.primary,
    onLinkClick: (String) -> Unit = {},
    /**
     * Ťuknutí na zmínku nebo hashtag ([MessageEntityType.Mention], [MessageEntityType.MentionUser],
     * [MessageEntityType.Hashtag]). Vedou dovnitř aplikace, ne na adresu, takže jdou zvlášť.
     */
    onEntityClick: (MessageEntityType) -> Unit = {},
    /**
     * Dlouhý stisk NAD ODKAZEM. Mimo odkazy ho dostane rodič (bublina) sám.
     *
     * Odkaz je uvnitř `Text` samostatný `combinedClickable` bez `onLongClick`, a ten dlouhý stisk
     * k rodiči nepustí — po puštění ho navíc vyhodnotí jako obyčejné ťuknutí. Nad odkazem se
     * tak místo nabídky otevíral prohlížeč. Proto se dlouhý stisk nad odkazem hlídá tady.
     */
    onLongClick: (() -> Unit)? = null,
    /**
     * Úseky, které se mají zvýraznit (hledané slovo, viz [highlightRanges]). Úsek uvnitř
     * zakrytého spoileru se nezvýrazní — zvýraznění by skryté slovo prozradilo.
     */
    highlights: List<IntRange> = emptyList(),
    /** Barva písma zvýrazněného úseku. */
    highlightColor: Color = Color.Unspecified,
    /** Podklad zvýrazněného úseku — zaoblená pilulka za textem, po řádcích. */
    highlightBackground: Color = Color.Unspecified,
    /**
     * Podklad citace v textu ([MessageEntityType.Quote]) — tónovaný blok za jejími řádky.
     *
     * Musí jít předat ze stejného důvodu jako [linkColor]: barva se počítá proti ploše, na které
     * zpráva leží (`MessageBodyScope.insetColor`). Bez barvy se blok nekreslí a citace zůstane
     * obyčejným textem.
     */
    quoteBackground: Color = Color.Unspecified,
    /**
     * O kolik pás citace přesáhne za text nahoru a dolů, když sahá na PRVNÍ, respektive POSLEDNÍ
     * řádek — tedy o vnitřní odsazení bubliny, aby dosedl na její okraj.
     *
     * Musí to podat volající: `drawBehind` se na hranice textu neořezává, takže uvnitř by se
     * hádalo, jestli nad textem nestojí značka přeposlání nebo hlavička citované zprávy, přes
     * jejichž šev pás přelézt nesmí.
     */
    quoteBleedTop: Dp = 0.dp,
    quoteBleedBottom: Dp = 0.dp,
    /**
     * Geometrie pásu citace, kterou podává volající — ať je stejná jako u ostatních dílů zprávy
     * (v konverzaci `MESSAGE_PADDING_H` / `MESSAGE_PADDING_V`).
     *
     * [quoteBleed] je přesah do stran: přesně vnitřní odsazení karty, takže pás dosedne na její
     * okraj. [quotePadding] je svislé odsazení uvnitř pásu, kde citace nesahá na kraj textu.
     */
    quoteBleed: Dp = 0.dp,
    quotePadding: Dp = 0.dp,
    /** Sbalená citace se ořízne na pár řádků; zbytek ukáže rozbalovač pod ní. */
    maxLines: Int = Int.MAX_VALUE,
    /** Hlásí, jestli se text do [maxLines] nevešel — podle toho se nabízí rozbalovač. */
    onOverflow: ((Boolean) -> Unit)? = null,
    /**
     * Citace je CELÝ text zprávy, takže se odsadí `padding`em celý `Text`, ne `ParagraphStyle`em.
     *
     * Odsazení odstavcem by se s ořezem na [maxLines] pralo: **Compose ellipsizovaný poslední
     * řádek zalomí na plnou šířku a teprve pak ho posune o odsazení**, takže mu konec i s výpustkou
     * přeteče za okraj a `Text` ho ořízne — poslední řádek pak končí uprostřed slova a bez „…".
     */
    quoteIndentAsPadding: Boolean = false,
) {
    /*
     * Spoilery se odkrývají po zprávě, ne po jednotlivých úsecích.
     *
     * Kdo ťukne na jeden zakrytý kus, chce vidět zprávu — dohledávat zbytek po kouskách je
     * otrava. `remember(text)` je tu proto, aby se odkrytí nepřeneslo na jinou zprávu, až
     * `LazyColumn` tuhle položku recykluje.
     */
    var spoilersRevealed by remember(text) { mutableStateOf(false) }

    val contentColor = LocalContentColor.current

    // Obsluha kliknutí se drží stranou od klíčů: je to lambda, která se při každé rekompozici
    // vyrábí znovu, takže by `remember` níž nikdy neplatil a text by se skládal pořád dokola.
    val linkHandler by rememberUpdatedState(onLinkClick)
    val entityHandler by rememberUpdatedState(onEntityClick)
    val longClickHandler by rememberUpdatedState(onLongClick)

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    /*
     * Dlouhý stisk už proběhl, takže se puštění prstu nesmí brát jako ťuknutí na odkaz.
     * Obyčejné pole místo stavu: čte se v obsluze kliknutí, ne v kompozici.
     */
    val suppressLinkClick = remember { booleanArrayOf(false) }
    val haptics = LocalHapticFeedback.current

    val visible = remember(text, entities, highlights, spoilersRevealed) {
        visibleHighlights(text, entities, highlights, spoilersRevealed)
    }
    val quotes = remember(text, entities) { quoteRanges(text, entities) }
    /*
     * Uvozovky v levém žlábku citace. Kreslí se až v `drawBehind`, ne jako `InlineTextContent`:
     * inline obsah by se musel vejít do řádku a posouval by první slovo, kdežto tady sedí
     * v odsazení, které si citace stejně dělá.
     */
    val quoteIcon = if (quotes.isEmpty()) null else painterResource(Res.drawable.format_quote_24dp)
    // Odsazení se počítá z dp, ne ze sp: lícuje s ikonou vedle sebe, ne s velikostí písma.
    val quoteIndent = if (quoteIndentAsPadding) null else with(LocalDensity.current) { QUOTE_INDENT.toSp() }
    val annotated = remember(text, entities, linkColor, contentColor, spoilersRevealed, visible, highlightColor, quoteIndent) {
        buildMessageText(
            text = text,
            entities = entities,
            quoteIndent = quoteIndent,
            highlights = visible,
            highlightColor = highlightColor,
            linkColor = linkColor,
            overlayColor = contentColor,
            spoilersRevealed = spoilersRevealed,
            onLinkClick = {
                if (suppressLinkClick[0]) suppressLinkClick[0] = false else linkHandler(it)
            },
            onEntityClick = {
                if (suppressLinkClick[0]) suppressLinkClick[0] = false else entityHandler(it)
            },
            onSpoilerClick = { spoilersRevealed = true },
        )
    }

    Text(
        text = annotated,
        style = style,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = {
            layout = it
            onOverflow?.invoke(it.hasVisualOverflow)
        },
        modifier = modifier.drawBehind {
            // Napřed pás citace, teprve na něm pilulka zvýraznění — hledané slovo musí být vidět
            // i uvnitř citace.
            if (quotes.isNotEmpty() && quoteBackground.isSpecified) {
                layout?.let {
                    drawQuoteBlocks(
                        layout = it,
                        ranges = quotes,
                        color = quoteBackground,
                        bleed = quoteBleed,
                        padding = quotePadding,
                        bleedTop = quoteBleedTop,
                        bleedBottom = quoteBleedBottom,
                        icon = quoteIcon,
                        iconColor = contentColor.copy(alpha = QUOTE_ICON_ALPHA),
                    )
                }
            }
            if (visible.isNotEmpty() && highlightBackground.isSpecified) {
                layout?.let { drawHighlightPills(it, visible, highlightBackground) }
            }
        }.padding(start = if (quoteIndentAsPadding && quotes.isNotEmpty()) QUOTE_INDENT else 0.dp).then(
            if (onLongClick == null) {
                Modifier
            } else {
                Modifier.pointerInput(annotated) {
                    awaitEachGesture {
                        // `Initial` a nic se nekonzumuje: odkaz i bublina dostanou událost
                        // beze změny, tady se jen pozoruje.
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        suppressLinkClick[0] = false

                        val offset = layout?.getOffsetForPosition(down.position) ?: return@awaitEachGesture
                        // Mimo odkaz si dlouhý stisk obslouží bublina; hlídat ho i tady by
                        // nabídku otevřelo dvakrát.
                        if (annotated.getLinkAnnotations(offset, offset + 1).isEmpty()) return@awaitEachGesture

                        // `null` = vypršel čas a prst je pořád dole na místě.
                        val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val change = awaitPointerEvent(PointerEventPass.Initial)
                                    .changes.firstOrNull { it.id == down.id } ?: break
                                if (!change.pressed) break
                                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
                            }
                        }

                        if (released == null) {
                            suppressLinkClick[0] = true
                            haptics.longPress()
                            longClickHandler?.invoke()
                        }
                    }
                }
            },
        ),
    )
}

/**
 * Poskládá text se styly.
 *
 * Rozsahy se **ořezávají do délky textu**. Server posílá platné hodnoty, ale `addStyle` mimo
 * rozsah je pád kvůli jedné zprávě a to je příliš vysoká cena za důvěru v cizí data.
 */
private fun buildMessageText(
    text: String,
    entities: List<MessageEntity>,
    /** `null` = citace se odsazuje `padding`em celého textu, ne odstavcem. */
    quoteIndent: TextUnit?,
    highlights: List<IntRange>,
    highlightColor: Color,
    linkColor: Color,
    overlayColor: Color,
    spoilersRevealed: Boolean,
    onLinkClick: (String) -> Unit,
    onEntityClick: (MessageEntityType) -> Unit,
    onSpoilerClick: () -> Unit,
): AnnotatedString = buildAnnotatedString {
    append(text)

    entities.forEach { entity ->
        val start = entity.offset.coerceIn(0, text.length)
        val end = (entity.offset + entity.length).coerceIn(start, text.length)
        if (start == end) return@forEach

        when (val type = entity.type) {
            is MessageEntityType.Link -> addLink(
                url = LinkAnnotation.Url(
                    url = type.url,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                        ),
                    ),
                    // Vlastní posluchač schválně: bez něj by odkaz otevíral `LocalUriHandler`
                    // a my bychom neměli kam vložit obsluhu interních `t.me` odkazů.
                    linkInteractionListener = { onLinkClick(type.url) },
                ),
                start = start,
                end = end,
            )

            /*
             * Zmínka a hashtag: barva odkazu, ale bez podtržení. Podtržení zůstává adresám, které
             * odvedou z aplikace; tohle vede dovnitř, jako v ostatních klientech. VÝJIMKA je
             * odchozí bublina: barva odkazu je tam barva textu (na `primary` jiná nejde), takže
             * bez podtržení by zmínka nebyla k poznání.
             */
            is MessageEntityType.Mention, is MessageEntityType.MentionUser, is MessageEntityType.Hashtag -> addLink(
                clickable = LinkAnnotation.Clickable(
                    tag = ENTITY_TAG,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = if (linkColor == overlayColor) TextDecoration.Underline else null,
                        ),
                    ),
                    linkInteractionListener = { onEntityClick(type) },
                ),
                start = start,
                end = end,
            )

            MessageEntityType.Bold ->
                addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)

            MessageEntityType.Italic ->
                addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)

            MessageEntityType.Underline ->
                addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)

            MessageEntityType.Strikethrough ->
                addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)

            // Podklad pod kódem se odvozuje z barvy textu, ne ze `surface` role — v odchozí
            // bublině je pozadí `primary` a světlý obdélník by z kódu udělal cizí těleso.
            MessageEntityType.Code -> addStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = overlayColor.copy(alpha = CODE_BACKGROUND_ALPHA),
                ),
                start,
                end,
            )

            /*
             * Citace se PODKLÁDÁ v `drawQuoteBlocks`, ne stylem písma — podklad ve `SpanStyle`
             * je hranatý obdélník po řádcích. Ze stylu si bere jen odsazení: `ParagraphStyle`
             * rozdělí text na odstavce přesně na hranicích citace a odsadí všechny její řádky.
             *
             * Odsazuje se jen zleva. `ParagraphStyle` odsazení zprava nemá, takže odstup od
             * pravého okraje nese přesah pásu ([QUOTE_BLEED]) — viz `drawQuoteBlocks`.
             */
            is MessageEntityType.Quote -> {
                addStyle(SpanStyle(fontSize = QUOTE_FONT_SCALE.em), start, end)
                if (quoteIndent != null) {
                    addStyle(
                        ParagraphStyle(textIndent = TextIndent(firstLine = quoteIndent, restLine = quoteIndent)),
                        start,
                        end,
                    )
                }
            }

            MessageEntityType.Spoiler -> if (spoilersRevealed) {
                // Po odkrytí zůstane jen jemný podklad, aby bylo poznat, co bylo skryté.
                addStyle(
                    SpanStyle(background = overlayColor.copy(alpha = CODE_BACKGROUND_ALPHA)),
                    start,
                    end,
                )
            } else {
                /*
                 * Zakrytý spoiler musí být OPRAVDU nečitelný.
                 *
                 * Průhledný text pod plnou plochou; obarvit ho jinak by obsah vyzradilo, což je
                 * přesně to, čemu se spoiler vyhýbá. Kliknutí musí být `LinkAnnotation`, ne
                 * `Modifier.clickable` — to by platilo pro celou zprávu, ne jen pro skrytý úsek.
                 */
                addLink(
                    clickable = LinkAnnotation.Clickable(
                        tag = SPOILER_TAG,
                        styles = TextLinkStyles(
                            style = SpanStyle(color = Color.Transparent, background = overlayColor),
                        ),
                        linkInteractionListener = { onSpoilerClick() },
                    ),
                    start = start,
                    end = end,
                )
            }
        }
    }

    // Až ZA entitami, aby barva zvýraznění přebila styl pod ní (kód, odkaz). Podklad kreslí
    // `drawHighlightPills` — podklad ve `SpanStyle` zaoblit nejde.
    if (highlightColor.isSpecified) highlights.forEach { addStyle(SpanStyle(color = highlightColor), it.first, it.last + 1) }
}

/**
 * Pás citace: tónovaný proužek za jejími řádky, od kraje bubliny ke kraji.
 *
 * **Pás, ne zaoblená plocha uvnitř.** Zaoblený obdélník odsazený od okraje byl karta v kartě —
 * tatáž chyba, kterou už jednou udělala citace nad odpovědí (subkartička v bublině) a náhled
 * odkazu. Náprava je pokaždé stejná: tvar nese vnější karta a části uvnitř jsou rovné a přes
 * celou šířku. Kde pás dosáhne na nároží bubliny, ořízne ho její tvar.
 *
 * **Plocha, ne proužek vlevo.** Proužek je jazyk M2; plocha navíc odliší převzatý text i tam,
 * kde je citace jediný obsah zprávy.
 *
 * Svisle se pás drží mezi `lineTop` a `lineBottom`, takže do sousedních řádků nezasahuje:
 * v těch mezích je i proklad řádku, a ten dělá písmu uvnitř odstup. Do stran přetéká
 * o [QUOTE_BLEED] — jinak by písmo doléhalo přímo k okraji.
 */
private fun DrawScope.drawQuoteBlocks(
    layout: TextLayoutResult,
    ranges: List<IntRange>,
    color: Color,
    bleed: Dp,
    padding: Dp,
    bleedTop: Dp,
    bleedBottom: Dp,
    icon: Painter?,
    iconColor: Color,
) {
    val bleedPx = bleed.toPx()
    val paddingPx = padding.toPx()
    val iconSize = QUOTE_ICON_SIZE.toPx()
    ranges.forEach { range ->
        val last = range.last.coerceAtMost(layout.layoutInput.text.length - 1)
        if (last < range.first) return@forEach
        val firstLine = layout.getLineForOffset(range.first)
        val lastLine = layout.getLineForOffset(last)
        /*
         * Pás má svislé odsazení, aby text nesedel na jeho hraně. Uvnitř zprávy ho vezme
         * z prázdné řádky, kterou tam stejně nechává rozdělení na odstavce ([QUOTE_PADDING]) —
         * z místa KOLEM pásu se tím stane místo UVNITŘ. Na kraji textu rozhoduje volající:
         * u bubliny to je její vnitřní odsazení, takže pás dosedne na okraj.
         */
        val top = layout.getLineTop(firstLine) - if (firstLine == 0) bleedTop.toPx() else paddingPx
        val bottom = layout.getLineBottom(lastLine) +
            if (lastLine == layout.lineCount - 1) bleedBottom.toPx() else paddingPx
        drawRect(
            color = color,
            topLeft = Offset(-bleedPx, top),
            size = Size(size.width + 2 * bleedPx, bottom - top),
        )
        if (icon == null) return@forEach
        val lineTop = layout.getLineTop(firstLine)
        val y = lineTop + (layout.getLineBottom(firstLine) - lineTop - iconSize) / 2
        translate(left = 0f, top = y) {
            with(icon) { draw(Size(iconSize, iconSize), colorFilter = ColorFilter.tint(iconColor)) }
        }
    }
}

/** Odsazení textu citace zleva; ve žlábku, který tím vznikne, stojí uvozovky. */
private val QUOTE_INDENT = 20.dp

/**
 * O kolik je písmo citace menší než okolní text.
 *
 * V `em`, ne v `sp`: platí pro každý styl, ve kterém se zpráva kreslí. Kurzíva by byla
 * zřetelnější, jenže projekt kurzivní řez nemá (v `composeResources/font/` jsou jen přímé),
 * takže by ji platforma dopočítala sklonem — a to je u víceřádkové citace vidět.
 */
private const val QUOTE_FONT_SCALE = 0.94f

private val QUOTE_ICON_SIZE = 14.dp

/** Uvozovky jsou značka, ne obsah — ustoupí textu, jako značka přeposlání. */
private const val QUOTE_ICON_ALPHA = 0.45f

/** Úseky citací, oříznuté do textu a bez prázdných — kreslí se za nimi blok. */
internal fun quoteRanges(text: String, entities: List<MessageEntity>): List<IntRange> =
    entities.mapNotNull { entity ->
        if (entity.type !is MessageEntityType.Quote) return@mapNotNull null
        val start = entity.offset.coerceIn(0, text.length)
        val end = (entity.offset + entity.length).coerceIn(start, text.length)
        if (start == end) null else start until end
    }

/**
 * Podklad zvýrazněných úseků: zaoblená pilulka za každým řádkem úseku. Hranaté obdélníky
 * `SpanStyle.background` v zaoblených (a u zvýrazněné zprávy zvlněných) bublinách působily
 * jako cizí prvek. Pilulka jde kousek do stran, aby písmo nedoléhalo k okraji.
 */
private fun DrawScope.drawHighlightPills(layout: TextLayoutResult, ranges: List<IntRange>, color: Color) {
    val padding = HIGHLIGHT_PADDING.toPx()
    val inset = HIGHLIGHT_LINE_INSET.toPx()
    val radius = CornerRadius(HIGHLIGHT_RADIUS.toPx())
    ranges.forEach { range ->
        val last = range.last.coerceAtMost(layout.layoutInput.text.length - 1)
        if (last < range.first) return@forEach
        for (line in layout.getLineForOffset(range.first)..layout.getLineForOffset(last)) {
            val start = maxOf(range.first, layout.getLineStart(line))
            val end = minOf(last, layout.getLineEnd(line, visibleEnd = true) - 1)
            if (end < start) continue
            val left = minOf(layout.getBoundingBox(start).left, layout.getBoundingBox(end).left)
            val right = maxOf(layout.getBoundingBox(start).right, layout.getBoundingBox(end).right)
            val top = layout.getLineTop(line) + inset
            val bottom = layout.getLineBottom(line) - inset
            drawRoundRect(
                color = color,
                topLeft = Offset(left - padding, top),
                size = Size(right - left + 2 * padding, bottom - top),
                cornerRadius = radius,
            )
        }
    }
}

private val HIGHLIGHT_PADDING = 3.dp
private val HIGHLIGHT_LINE_INSET = 1.dp
private val HIGHLIGHT_RADIUS = 6.dp

/**
 * Úseky ke zvýraznění oříznuté do textu, bez těch, které zasahují do ZAKRYTÉHO spoileru —
 * zvýraznění by skryté slovo prozradilo. Po odkrytí spoilerů se zvýrazní i ony.
 */
internal fun visibleHighlights(
    text: String,
    entities: List<MessageEntity>,
    highlights: List<IntRange>,
    spoilersRevealed: Boolean,
): List<IntRange> {
    if (highlights.isEmpty()) return emptyList()
    val hidden = if (spoilersRevealed) {
        emptyList()
    } else {
        entities.filter { it.type == MessageEntityType.Spoiler }.map { it.offset until it.offset + it.length }
    }
    return highlights.mapNotNull { range ->
        val start = range.first.coerceIn(0, text.length)
        val end = (range.last + 1).coerceIn(start, text.length)
        when {
            start == end -> null
            hidden.any { start <= it.last && it.first < end } -> null
            else -> start until end
        }
    }
}

/** Jak výrazný je podklad kódu a odkrytého spoileru. Dost na odlišení, málo na křik. */
private const val CODE_BACKGROUND_ALPHA = 0.15f

private const val SPOILER_TAG = "spoiler"

/** Značka klikatelné zmínky a hashtagu; obsluhu nese posluchač, značka jen odliší typ anotace. */
private const val ENTITY_TAG = "entity"
