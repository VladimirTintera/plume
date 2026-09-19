package eu.tintera.plume.core.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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

    val annotated = remember(text, entities, linkColor, contentColor, spoilersRevealed) {
        buildMessageText(
            text = text,
            entities = entities,
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
        onTextLayout = { layout = it },
        modifier = modifier.then(
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
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
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
}

/** Jak výrazný je podklad kódu a odkrytého spoileru. Dost na odlišení, málo na křik. */
private const val CODE_BACKGROUND_ALPHA = 0.15f

private const val SPOILER_TAG = "spoiler"

/** Značka klikatelné zmínky a hashtagu; obsluhu nese posluchač, značka jen odliší typ anotace. */
private const val ENTITY_TAG = "entity"
