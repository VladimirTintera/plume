package eu.tintera.plume.features.conversation.items

import eu.tintera.plume.resources.profile_open
import eu.tintera.plume.core.ui.chatAvatarKey
import eu.tintera.plume.core.ui.sharedNavElement
import androidx.compose.foundation.clickable
import eu.tintera.plume.core.ui.highlightRanges
import eu.tintera.plume.core.telegram.MessageEntityType
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.MessageReaction
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.RichText
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.core.ui.senderColor
import eu.tintera.plume.core.ui.surfaceApart
import eu.tintera.plume.core.ui.sharedFlight
import eu.tintera.plume.features.conversation.MessageFlight
import eu.tintera.plume.features.conversation.MessageMenu
import eu.tintera.plume.features.conversation.REACTION_BADGE_ROOM
import eu.tintera.plume.features.conversation.REACTION_ROW_HEIGHT
import eu.tintera.plume.features.conversation.REACTION_SHADOW_ROOM
import eu.tintera.plume.features.conversation.ReactionRow
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.check_circle_filled_24dp
import eu.tintera.plume.resources.conversation_selection_deselect
import eu.tintera.plume.resources.conversation_selection_select
import eu.tintera.plume.resources.radio_button_unchecked_24dp
import eu.tintera.plume.resources.conversation_edited
import eu.tintera.plume.resources.conversation_forwarded
import eu.tintera.plume.resources.conversation_forwarded_from
import eu.tintera.plume.resources.forward_24dp
import eu.tintera.plume.core.telegram.ForwardOrigin
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import eu.tintera.plume.resources.reply_24dp
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Co mají všechny zprávy společné a co proto nese RÁM, ne obsah: stranu, sousedy, tvář
 * odesílatele, reakce, doručení, zvýraznění, let a nabídku.
 *
 * Počítá to builder ([conversationItems]), takže se o tom rozhoduje jednou a testovaně.
 */
@Immutable
internal data class MessageFrameData(
    val key: Long,
    /** Co blok kreslí; u alba primární zpráva s časem a stavem celého alba (viz [MessageRow]). */
    val message: ChatMessage,
    /** Navazuje zpráva na STARŠÍHO souseda (tedy na zprávu nad ní)? */
    val continuesAbove: Boolean,
    /** Navazuje na zprávu NOVĚJŠÍ soused (zpráva pod ní)? */
    val continuesBelow: Boolean,
    val avatar: SenderAvatar,
    /** Skutečný stav doručení — ukazuje ho nabídka nad zprávou. `null` u příchozí zprávy. */
    val delivery: Delivery?,
    /** Co ukazuje odznak pod zprávou; „přečteno" jen u vybraných zpráv (viz [readBadgeRows]). */
    val badge: Delivery?,
    /** Krátce po skoku (citace, výsledek hledání) — zpráva, na kterou se skočilo. */
    val highlighted: Boolean,
    /** Ukázaný výsledek hledání v chatu — zvýrazněný, dokud se nepřejde jinam nebo hledání nezavře. */
    val searchFocused: Boolean = false,
    /** V kanálu detail reakcí není (`BROADCAST_FORBIDDEN`), čip se proto nedá ťuknout. */
    val reactionsOpenable: Boolean,
    /** Let ze vstupního pole, který přistává právě v téhle zprávě; jinak `null`. */
    val flight: MessageFlight?,
    /** Vyplněné jen u zprávy, nad kterou je nabídka otevřená. */
    val menu: MessageMenu?,
    /** Kandidáti do nabídky; co z nich server dovolí, se zjistí až při otevření. */
    val menuActions: List<MessageMenuAction>,
    /** Co udělá ťuknutí na zprávu samotnou (ne na odkaz, fotku…); `null` = nic. */
    val onTap: MessageAction?,
    /** Je blok vybraný? `null` = režim výběru neběží a rám ho vůbec nekreslí. */
    val selected: Boolean? = null,
) {
    /**
     * Zpráva je zvýrazněná: jednorázově po skoku ([highlighted]), nebo trvale jako ukázaný
     * výsledek hledání ([searchFocused]). Vypadá to stejně — liší se jen tím, jak dlouho to trvá.
     */
    val emphasized: Boolean get() = highlighted || searchFocused
}

/**
 * Blok se zprávou. Rám (tvář, tah na odpověď, zvýraznění, reakce, doručení, kotva nabídky) je
 * pro všechny společný a implementuje ho tohle rozhraní; blok dodá jen [Body].
 *
 * Jestli má zpráva bublinu, **není příznak rámu**: [Body] buď použije sdílený kus
 * [MessageBodyScope.Bubble], nebo kreslí rovnou na plochu konverzace.
 */
internal interface MessageItem : ConversationItem {
    val frame: MessageFrameData

    override val key: Any get() = frame.key

    override fun containsMessage(messageId: Long): Boolean = frame.message.id == messageId

    /**
     * Nejvyšší šířka zprávy jako podíl řádku. Protější strana zůstává volná, aby bylo na první
     * pohled poznat, kdo zprávu poslal — u holého snímku je ten pruh jediné vodítko, bublinu
     * ani její barvu nemá (viz [MESSAGE_MAX_WIDTH_FRACTION]).
     *
     * Blok to smí přebít, ale je to výjimka, kterou musí odůvodnit.
     */
    val maxWidthFraction: Float get() = MESSAGE_MAX_WIDTH_FRACTION

    @Composable
    fun MessageBodyScope.Body()

    @Composable
    override fun LazyItemScope.Content(scope: ConversationItemScope) {
        // Cíl letu se nezjevuje prolnutím: během letu se kreslí v překryvu sdílených prvků, kam
        // průhlednost položky nedosáhne, a po přistání by skočil z plné viditelnosti do
        // rozjetého prolnutí.
        val modifier = Modifier.animateItem(
            fadeInSpec = if (frame.flight != null) null else spring(stiffness = Spring.StiffnessMediumLow),
        ).arrival(frame, scope).gathered(frame.key, scope)
        RevealingTime(frame.message.timestamp, scope.timeReveal, modifier) {
            MessageFrame(frame, scope, maxWidthFraction) { Body() }
        }
    }
}

/**
 * Co blok zprávy dostane od rámu pro kreslení obsahu: tvar a barvy bubliny a obsluhu.
 *
 * Barvy bubliny jsou tu i pro bloky, které bublinu nemají — popisek pod holou fotkou a text
 * u náhledu odkazu je potřebují pro svůj kontejner.
 */
@Stable
internal class MessageBodyScope(
    val items: ConversationItemScope,
    val frame: MessageFrameData,
    /** Tvar bubliny se staženými nárožími podle sousedů; bloky bez bubliny z něj odvozují svůj. */
    val shape: RoundedCornerShape,
    val bubbleColor: Color,
    val onBubbleColor: Color,
    /**
     * Akcent (citace, odkaz, proužek náhledu) uvnitř bubliny. V odchozí bublině je pozadí samo
     * `primary`, takže tam akcent nese barva popředí — `primary` by byla neviditelná.
     */
    val accent: Color,
    /**
     * Plocha vloženého obsahu — citace nahoře na kartě zprávy, popis webu u náhledu odkazu dole.
     * `tertiaryContainer`, dokud se od bubliny pozná; jinak posunutá (viz [surfaceApart]).
     */
    val insetColor: Color,
    /**
     * Kolik místa si obsah nechá dole. Čip reakce leží PŘES dolní nároží — dolní půlka mu visí
     * ven, horní na zprávě —, takže se rezervuje jen ta horní půlka. Platí pro to, co je ve
     * zprávě poslední: bublinu, popisek pod holou fotkou i popis náhledu odkazu.
     */
    val contentBottom: Dp,
    /** Kde zpráva v okně právě je; nabídka z toho místa vyjede. U kopie v nabídce prázdná. */
    private val anchor: MenuAnchor,
) {
    val message: ChatMessage get() = frame.message
    val continuesAbove: Boolean get() = frame.continuesAbove

    /** Akce nad touhle zprávou, nebo nad [messageId] — u alba nad konkrétní fotkou. */
    fun act(action: MessageAction, messageId: Long = message.id) = items.onAction(messageId, action)

    /**
     * Dlouhý stisk: nabídka s kandidáty, které nabízí blok. Potomci s vlastním klikáním (odkaz,
     * citace, fotka) ho k rámu nepustí, takže ho musí zopakovat sami — proto je tu, ne v rámu.
     */
    fun requestMenu() {
        items.onMenuAnchor(anchor)
        act(MessageAction.ShowMenu(frame.menuActions))
    }
}

/**
 * Tvar, barvy a obsluha pro obsah zprávy — v seznamu i v kopii, kterou kreslí nabídka.
 * Obě místa tak kreslí zprávu stejně, jinak by let sdíleného prvku skončil skokem.
 */
@Composable
internal fun rememberMessageBodyScope(
    frame: MessageFrameData,
    scope: ConversationItemScope,
    anchor: MenuAnchor,
): MessageBodyScope {
    val message = frame.message

    /*
     * Odchozí bublina je `primary`, ne `primaryContainer`.
     *
     * S `primaryContainer` byly obě strany konverzace světlé plochy s podobnou světlostí a při
     * rychlém průletu očima nebylo poznat, kdo co napsal — rozlišovala je jen strana. Sytá
     * `primary` proti světlé `surfaceContainerHigh` dělá ten rozdíl barvou, takže se čte
     * i bez čtení zarovnání. Stejným způsobem to řeší Google Messages.
     *
     * Vrstvení jinak drží surface role, ne elevation — viz konvence v CLAUDE.md.
     */
    /*
     * Zvýrazněná zpráva (skok, výsledek hledání) přejde do TERCIÁRNÍ role — jediné, kterou
     * konverzace jinak nepoužívá pro bubliny, takže se nesplete s žádnou stranou. Odchozí
     * sytá `tertiary`, příchozí světlá `tertiaryContainer`: rozdíl mezi stranami zůstane.
     * Barva se prolíná, ne přepíná.
     */
    val colors = MaterialTheme.colorScheme
    val emphasisSpec = MaterialTheme.motionScheme.slowEffectsSpec<Color>()
    val targetBubble = when {
        message.isOutgoing && frame.emphasized -> colors.tertiary
        message.isOutgoing -> colors.primary
        frame.emphasized -> colors.tertiaryContainer
        else -> colors.surfaceContainerHigh
    }
    val targetOnBubble = when {
        message.isOutgoing && frame.emphasized -> colors.onTertiary
        message.isOutgoing -> colors.onPrimary
        frame.emphasized -> colors.onTertiaryContainer
        else -> colors.onSurface
    }
    val bubbleColor by animateColorAsState(targetBubble, emphasisSpec, label = "bubble-color")
    val onBubbleColor by animateColorAsState(targetOnBubble, emphasisSpec, label = "bubble-content-color")
    /*
     * Akcent (odkaz, citace, proužek náhledu) se bere z role, ale MUSÍ projít kontrolou proti
     * ploše, na které leží — viz [accentOn]. V odchozí bublině je pozadí samo `primary`, takže
     * tam akcent nese rovnou barva popředí; u příchozí je to `primary` na `surfaceContainerHigh`
     * a u zvýrazněné `tertiary` na `tertiaryContainer`. To druhé se v paletě se zvýšeným
     * kontrastem potkalo na téže světlosti a odkaz zmizel.
     *
     * Rozhoduje se z CÍLOVÝCH barev a výsledek se prolíná: kdyby se kontrast počítal z právě
     * animované plochy, přepnul by se akcent uprostřed přechodu skokem.
     */
    val accent by animateColorAsState(
        targetValue = when {
            message.isOutgoing -> targetOnBubble
            else -> accentOn(
                background = targetBubble,
                accent = if (frame.emphasized) colors.tertiary else colors.primary,
                fallback = targetOnBubble,
            )
        },
        animationSpec = emphasisSpec,
        label = "bubble-accent",
    )
    /*
     * Plocha citace a popisu webu se počítá tady se zbytkem barev, ne v místě kreslení: rozhoduje
     * se z CÍLOVÉ barvy bubliny (z animované by se přepnula uprostřed přechodu) a výsledek se
     * prolíná spolu s ní. Dřív skákala, protože visela rovnou na `frame.emphasized`.
     */
    val insetColor by animateColorAsState(
        targetValue = surfaceApart(
            background = targetBubble,
            container = colors.tertiaryContainer,
            toward = colors.onTertiaryContainer,
        ),
        animationSpec = emphasisSpec,
        label = "inset-color",
    )

    /*
     * Stažené nároží drží skupinu pohromadě, takže je jen TAM, KDE NA SEBE BUBLINY NAVAZUJÍ.
     *
     * Dřív bylo spodní nároží na straně odesílatele stažené VŽDY, bez ohledu na souseda. Díky tomu
     * vznikla dvojice „spodek předchozí + vršek navazující" bez toho, aby bublina musela cokoli
     * vědět o novějším sousedovi — jenže tím pádem chybělo zaoblení i tam, kde blok SKONČIL:
     * poslední bublina odchozí skupiny měla ostré pravé dolní nároží (u příchozích zrcadlově levé).
     *
     * Obě strany se proto teď ptají stejně. Stahuje se jen nároží na straně odesílatele — na té
     * druhé jde bublina k okraji obrazovky a stažené nároží by tam nemělo co navazovat.
     */
    /*
     * Nároží se ANIMUJÍ. Když přijde další zpráva a tahle se stane navazující, stáhne se jí
     * spodní nároží — skokem to vypadalo jako chyba vykreslení. Stejně to dělá seznam chatů
     * u skupin (`animateDpAsState` přes spatial spec).
     */
    /*
     * Zvýrazněná zpráva se z bloku vyčlení i tvarem: stažená nároží se zakulatí. Totéž hrající
     * hlasovka — rozvlní se (viz `MessageBox`), a vlnky vedené přes stažené nároží by v něm
     * dělaly špičku.
     */
    val detached = frame.emphasized || rememberVoicePlaying(message, scope)
    val topInnerAnimated by animateDpAsState(
        targetValue = if (frame.continuesAbove && !detached) BUBBLE_TIGHT else BUBBLE_CORNER,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "bubble-corner-top",
    )
    val bottomInnerAnimated by animateDpAsState(
        targetValue = if (frame.continuesBelow && !detached) BUBBLE_TIGHT else BUBBLE_CORNER,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "bubble-corner-bottom",
    )
    // Pružina přestřelí; zaoblení pod nulou by `RoundedCornerShape` odmítlo.
    val topInner = topInnerAnimated.coerceAtLeast(0.dp)
    val bottomInner = bottomInnerAnimated.coerceAtLeast(0.dp)
    val shape = if (message.isOutgoing) {
        RoundedCornerShape(
            topStart = BUBBLE_CORNER,
            topEnd = topInner,
            bottomEnd = bottomInner,
            bottomStart = BUBBLE_CORNER,
        )
    } else {
        RoundedCornerShape(
            topStart = topInner,
            topEnd = BUBBLE_CORNER,
            bottomEnd = BUBBLE_CORNER,
            bottomStart = bottomInner,
        )
    }

    // Obsah si pro čip reakce místo NEDĚLÁ: bublina má s reakcí i bez ní stejnou velikost
    // (viz `MessageBox`).
    val contentBottom = 8.dp

    return remember(scope, frame, shape, bubbleColor, onBubbleColor, accent, insetColor, contentBottom) {
        MessageBodyScope(
            items = scope,
            frame = frame,
            shape = shape,
            bubbleColor = bubbleColor,
            onBubbleColor = onBubbleColor,
            accent = accent,
            insetColor = insetColor,
            contentBottom = contentBottom,
            anchor = anchor,
        )
    }
}

/**
 * Rám zprávy.
 *
 * Zprávy téhož odesílatele v řadě se **seskupují**: jméno se ukáže jen u první a nároží u sebe
 * navazujících bublin se zaoblí míň. Bez toho vypadá delší monolog jako dvacet nesouvisejících
 * kartiček.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun MessageFrame(
    frame: MessageFrameData,
    scope: ConversationItemScope,
    maxWidthFraction: Float,
    body: @Composable MessageBodyScope.() -> Unit,
) {
    val message = frame.message

    /*
     * Kde zpráva v okně je — pro nabídku, která z toho místa vyjede. Drží se souřadnice, ne stav:
     * čtou se jen při dlouhém stisku, a zápis stavu při každém layoutu by položku rekomponoval.
     */
    val boxCoordinates = remember { arrayOfNulls<LayoutCoordinates>(1) }
    // Nejvyšší šířka, kterou zpráva v seznamu dostala — kopie v nabídce se složí se stejnou.
    val boxMaxWidth = remember { IntArray(1) { -1 } }
    val anchor = remember {
        MenuAnchor(
            bounds = { boxCoordinates[0]?.takeIf { it.isAttached }?.let { Rect(it.positionInWindow(), it.size.toSize()) } },
            maxWidth = { boxMaxWidth[0].takeIf { it >= 0 } },
        )
    }
    val bodyScope = rememberMessageBodyScope(frame, scope, anchor)

    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    val drag = remember { Animatable(0f) }
    val threshold = with(density) { REPLY_THRESHOLD.toPx() }
    val maxDrag = with(density) { REPLY_MAX_DRAG.toPx() }
    // Spec se musí vzít v kompozici — konec gesta je obyčejná suspend funkce, ne composable.
    val returnSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()

    /** Prah už byl v tomhle tahu překročen; hlídá, aby haptika i odpověď odešly jen jednou. */
    var armed by remember { mutableStateOf(false) }

    // Detektor gesta vzniká jednou za život položky, ale id zprávy se po potvrzení odeslání
    // mění (klíč položky ne) — odpověď musí mířit na aktuální id, ne na dočasné.
    val currentMessageId by rememberUpdatedState(message.id)

    /*
     * Zvýraznění po skoku: zpráva dvakrát krátce PULZNE měřítkem.
     *
     * Dřív to byl pruh přes celou šířku, který se rozsvítil a vyhasl. Pulz je výraznější, patří
     * jen té zprávě (u alba celé bublině) a nemění barvy — obarvit bublinu by u odchozí `primary`
     * nebylo vidět. Měřítko se čte až při kreslení, takže pulz nic nerekomponuje.
     */
    val pulse = remember { Animatable(1f) }
    val pulseSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    LaunchedEffect(frame.highlighted) {
        if (!frame.highlighted) return@LaunchedEffect
        repeat(HIGHLIGHT_PULSES) {
            pulse.animateTo(HIGHLIGHT_SCALE, pulseSpec)
            pulse.animateTo(1f, pulseSpec)
        }
    }

    /*
     * Odesílaná zpráva je průhlednější a po potvrzení se „usadí". Odznak s hodinami je malý
     * a snadno se přehlédne; tohle je vidět hned. Klíč položky se potvrzením nemění, takže
     * přechod plynule dobíhá na téže bublině.
     */
    val sendingAlpha by animateFloatAsState(
        targetValue = if (frame.delivery == Delivery.Sending) SENDING_ALPHA else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "sending-alpha",
    )

    /*
     * Vybraná zpráva má za sebou pruh PŘES CELOU ŠÍŘKU, ne obarvenou bublinu: odchozí bublina je
     * sytá `primary` a barvu by nenesla, a u zprávy bez bubliny (fotka, samolepka) by nebylo co
     * obarvit. `secondaryContainer` je tatáž role, jakou má vybraná položka v seznamu chatů —
     * výběr je v M3 selekční stav, ne zvýraznění obsahu. Průsvitný, aby text zůstal čitelný.
     *
     * Pruh zahrnuje i mezeru nad zprávou, takže z vybraných sousedů vznikne souvislý blok.
     */
    val selectionTint by animateColorAsState(
        targetValue = if (frame.selected == true) {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = SELECTION_TINT_ALPHA)
        } else {
            Color.Transparent
        },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "selection-tint",
    )

    Box(Modifier.fillMaxWidth().background(selectionTint)) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Zprávy od jednoho odesílatele drží u sebe, nová skupina se oddělí. Tvar bublin
            // to už rozlišuje staženým nárožím, mezera tomu jen dává prostor.
            .padding(top = if (frame.continuesAbove) 0.dp else GROUP_SPACING)
            /*
             * Tažení do strany = odpověď.
             *
             * Vlastní detektor, ne `draggable` ani `detectHorizontalDragGestures`. Ten druhý
             * konzumuje hned od prvního pixelu a rozbil by svislý scroll seznamu; `draggable`
             * je po slopu **směrově agnostický** a vezme si i tah DOLEVA, který patří odkrývání
             * časů nad seznamem. Tenhle detektor rozhodne jednou za gesto: doprava spotřebuje
             * (odpověď), doleva nespotřebuje nic a nechá gesto propadnout rodiči.
             *
             * Nespotřebovaný pohyb `awaitHorizontalTouchSlopOrCancellation` nezahodí — počítá
             * slop dál, takže si ho může vzít seznam. Stejný vzor jako vlastní detektor
             * v prohlížeči médií.
             *
             * Táhne se **doprava u obou stran konverzace**. Zrcadlit směr podle odesílatele by
             * znamenalo, že uživatel musí před každým gestem přemýšlet, čí zpráva to je.
             *
             * V režimu výběru gesto NENÍ: překryv nad zprávou bere ťuknutí, ale tah by mu
             * propadl a založil odpověď uprostřed vybírání.
             */
            .then(if (frame.selected != null) Modifier else Modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)

                    var startOffset = 0f
                    val claimed = awaitHorizontalTouchSlopOrCancellation(down.id) { change, overSlop ->
                        if (overSlop > 0f) {
                            change.consume()
                            startOffset = overSlop
                        }
                    } ?: return@awaitEachGesture

                    coroutineScope.launch { drag.snapTo(startOffset.coerceIn(0f, maxDrag)) }

                    val finished = horizontalDrag(claimed.id) { change ->
                        val delta = change.positionChange().x
                        change.consume()
                        coroutineScope.launch {
                            // Za prahem klade tah odpor — jinak by šla bublina odtáhnout přes půl
                            // obrazovky a nebylo by poznat, že se dál nic nestane.
                            val resistance = if (drag.value < threshold) 1f else DRAG_RESISTANCE
                            val next = (drag.value + delta * resistance).coerceIn(0f, maxDrag)
                            drag.snapTo(next)

                            if (!armed && next >= threshold) {
                                armed = true
                                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            }
                        }
                    }

                    coroutineScope.launch {
                        // Odpověď odchází při PUŠTĚNÍ, ne při překročení prahu. Kdo si to rozmyslí,
                        // vrátí prst zpátky a nic se nestane. Zrušené gesto (přerušení systémem)
                        // bublinu jen vrátí.
                        if (finished && armed && drag.value >= threshold) scope.onAction(currentMessageId, MessageAction.SwipeReply)
                        armed = false
                        drag.animateTo(0f, returnSpec)
                    }
                }
            }),
        horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start,
        // Tvář sedí u spodní hrany bubliny — u dlouhé zprávy by uprostřed nebylo poznat, ke které
        // části bloku patří.
        verticalAlignment = Alignment.Bottom,
    ) {
        if (frame.selected != null && message.isOutgoing) SelectionMark(frame.selected)

        // Mimo posouvaný `Box`: při tažení na odpověď jede bublina, tvář zůstává na místě.
        when (frame.avatar) {
            SenderAvatar.None -> Unit
            SenderAvatar.Spacer -> Spacer(Modifier.width(SENDER_AVATAR_SLOT))
            SenderAvatar.Shown -> {
                // Profil jen u člověka: záporné id je chat (anonymní správce, kanál), ne uživatel.
                val userId = message.senderId.takeIf { it > 0 }
                val profileLabel = stringResource(Res.string.profile_open)
                Avatar(
                    chatId = message.senderId,
                    title = message.senderName.orEmpty(),
                    avatarFileId = message.senderAvatarFileId,
                    size = SENDER_AVATAR_SIZE,
                    modifier = Modifier
                        .padding(end = SENDER_AVATAR_SLOT - SENDER_AVATAR_SIZE)
                        .then(
                            if (userId != null && scope.isProfileFlight(message.id)) {
                                Modifier.sharedNavElement(chatAvatarKey(userId))
                            } else {
                                Modifier
                            },
                        )
                        .clip(CircleShape)
                        .then(
                            if (userId != null) {
                                Modifier.clickable(onClickLabel = profileLabel) {
                                    scope.onAction(message.id, MessageAction.OpenSenderProfile(userId))
                                }
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }

        Column(
            // Posun se čte až ve fázi layoutu (`offset { }`), ne v kompozici. Jinak by se celá
            // zpráva i s obsahem přeskládávala na každý snímek tahu.
            Modifier.offset { IntOffset(drag.value.roundToInt(), 0) },
        ) {
            /*
             * Zpráva sama. Když je nad ní otevřená nabídka, SKRYJE se, ale drží místo — nabídka ji
             * kreslí u sebe (viz `MessageMenuLayer`), vysune ji do pohledu a při zavření vrátí.
             * Stejně to dělají Google Zprávy: místo zprávy zůstane prázdné.
             *
             * Odznak doručení do ní nepatří: stojí až za ní, ve sloupci.
             */
            MessageBox(
                frame = frame,
                bodyScope = bodyScope,
                maxWidthFraction = maxWidthFraction,
                // Dokud zprávu kreslí nabídka, drží se reakce z doby před jejím otevřením —
                // nová reakce pak naskočí, až se zpráva vrátí na místo, a je ji vidět.
                reactions = heldReactions(frame.message.reactions, hold = scope.isInMenu(frame.key)),
                body = body,
                modifier = Modifier
                    .layout { measurable, constraints ->
                        boxMaxWidth[0] = constraints.maxWidth
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                    }
                    .onGloballyPositioned { boxCoordinates[0] = it }
                    // Průhledná, ne vyřazená z layoutu: místo zůstane prázdné a kopie v nabídce se
                    // na něj po zavření vrátí. Čte se až při kreslení, takže nic nerekomponuje.
                    .graphicsLayer {
                        alpha = if (scope.isInMenu(frame.key)) 0f else sendingAlpha
                        scaleX = pulse.value
                        scaleY = pulse.value
                        transformOrigin = TransformOrigin(if (message.isOutgoing) 1f else 0f, 0.5f)
                    },
                // Cílová strana letu ze vstupního pole. Sdílí se CELÁ zpráva, ne jen text —
                // kdyby se sdílel jen text, doletěl by sám a pozadí by pod ním na konci
                // skokem naskočilo.
                beforeClip = frame.flight?.let { Modifier.sharedFlight(it.sharedKey, visible = true) } ?: Modifier,
                /*
                 * Ťuknutí na VLASTNÍ zprávu si vyžádá odznak přečtení, druhé ho schová.
                 *
                 * Je to ubezpečení na vyžádání: odznak se automaticky ukazuje jen u nejnovější
                 * zprávy, takže u starší se jinak nedá zjistit, jestli ji protějšek přečetl.
                 *
                 * Potomci s vlastním klikáním (odkaz v textu, citace, fotka, náhled odkazu,
                 * samolepka) si ťuknutí berou sami, takže u zprávy složené z fotky není kam
                 * ťuknout. Přidávat jim ho by znamenalo brát ťuknutí těm akcím — u fotky
                 * otevření prohlížeče —, a to je horší než chybějící zkratka.
                 */
                afterClip = Modifier.combinedClickable(
                    onLongClick = bodyScope::requestMenu,
                    onClick = { frame.onTap?.let(bodyScope::act) },
                ),
                onReactionClick = if (frame.reactionsOpenable) {
                    { bodyScope.act(MessageAction.ShowReactions) }
                } else {
                    null
                },
            ) {
                /*
                 * Šipka odpovědi. Sedí hned vedle zprávy a jede s ní, takže je jasné, čeho se gesto
                 * týká. Posazená `offset`em mimo `Box` do layoutu nezasahuje.
                 *
                 * Vlevo u OBOU stran konverzace: tam vzniká mezera, když se táhne doprava.
                 */
                ReplyHint(
                    progress = { (drag.value / threshold).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = -REPLY_ICON_SLOT),
                )
            }

            DeliveryBadge(
                delivery = frame.badge,
                modifier = Modifier.align(Alignment.End),
            )
        }

        /*
         * Zaškrtnutí stojí na VOLNÉ straně zprávy — u odchozí vlevo, u příchozí vpravo. Volný
         * pruh tam je vždycky (zpráva zabere nejvýš [MESSAGE_MAX_WIDTH_FRACTION] řádku), takže
         * se kvůli němu nic neposouvá a oko ho najde tam, kde se stejně dívá.
         */
        if (frame.selected != null && !message.isOutgoing) SelectionMark(frame.selected)
    }

        /*
         * V režimu výběru bere ťuknutí i dlouhý stisk CELÝ řádek, přes překryv nad obsahem.
         * Bez něj by fotka otevřela prohlížeč, odkaz prohlížeč a citace skok — potomci
         * s vlastním klikáním si ťuknutí berou sami a k rámu ho nepustí.
         */
        if (frame.selected != null) {
            val label = stringResource(if (frame.selected) Res.string.conversation_selection_deselect else Res.string.conversation_selection_select)
            Box(
                Modifier
                    .matchParentSize()
                    .combinedClickable(
                        onClickLabel = label,
                        onLongClickLabel = label,
                        onLongClick = { scope.onAction(currentMessageId, MessageAction.ToggleSelection) },
                        onClick = { scope.onAction(currentMessageId, MessageAction.ToggleSelection) },
                    ),
            )
        }
    }
}

/**
 * Kolečko výběru vedle zprávy. Vybrané je plné v akcentní barvě, nevybrané prázdné — samotné
 * podbarvení řádku by u jediné vybrané zprávy nedalo poznat, že jdou vybírat i další.
 */
@Composable
private fun RowScope.SelectionMark(selected: Boolean) {
    Icon(
        painter = painterResource(
            if (selected) Res.drawable.check_circle_filled_24dp else Res.drawable.radio_button_unchecked_24dp,
        ),
        contentDescription = null,
        tint = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier
            .align(Alignment.CenterVertically)
            .padding(horizontal = SELECTION_MARK_PADDING)
            .size(SELECTION_MARK_SIZE),
    )
}

/**
 * Zpráva bez rámu řádku: obsah bloku ve tvaru bubliny a čipy reakcí přes její dolní nároží.
 * Kreslí ji seznam i nabídka nad zprávou (viz [MenuCopy]), a proto je to samostatný kus.
 *
 * [beforeClip] a [afterClip] jsou modifikátory před ořezem tvarem a za ním. Ořez MUSÍ být před
 * klikáním, jinak by se vlnka dlouhého stisku kreslila do obdélníku a přetekla přes nároží.
 */
@Composable
internal fun MessageBox(
    frame: MessageFrameData,
    bodyScope: MessageBodyScope,
    body: @Composable MessageBodyScope.() -> Unit,
    modifier: Modifier = Modifier,
    beforeClip: Modifier = Modifier,
    afterClip: Modifier = Modifier,
    /** Ťuknutí na čip reakce; `null` = čip se ťuknout nedá (kanál, kopie v nabídce). */
    onReactionClick: (() -> Unit)?,
    /** Podíl šířky, kterou zpráva dostane; kopie v nabídce má šířku napevno, tam je 1. */
    maxWidthFraction: Float = 1f,
    /** Reakce, které se mají kreslit — viz [heldReactions]. */
    reactions: List<MessageReaction> = frame.message.reactions,
    extra: @Composable BoxScope.() -> Unit = {},
) {
    val message = frame.message
    /*
     * Místo na čip reakce POD zprávou, a animované.
     *
     * Bublina má s reakcí i bez ní STEJNOU velikost — čip leží jako odznak přes její spodní
     * nároží ([REACTION_ON_BUBBLE]), dolní půlkou visí pod ní. Místo pod zprávou se otevře pružinou, takže
     * zpráva při první reakci plynule popojede, místo aby poskočila. Dřív si obsah bubliny dělal
     * místo uvnitř a zpráva se o čip skokem zvětšila.
     *
     * Čip patří ke zprávě, takže místo je uvnitř toho, co kreslí i nabídka nad zprávou.
     */
    val overhang by animateDpAsState(
        targetValue = if (reactions.isEmpty()) 0.dp else REACTION_OVERHANG,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "reaction-overhang",
    )
    /*
     * Zvýraznění: okraj zprávy se rozvlní a vlnky pomalu obíhají (viz `WavyShape`). Zvlňuje se
     * sám ořez zprávy, žádný obrys navíc. Průběh i fáze se čtou až ve vrstvě, takže obíhání
     * nerekomponuje.
     *
     * Stejně se rozvlní hrající hlasovka: vlnka v aplikaci znamená „teď zní hlas" (i karta
     * vstupního pole při nahrávání). V klidu je hlasovka hladká — stálá vlna nic neoznamovala
     * a vypadala jako rozbitý okraj.
     */
    val waving = frame.emphasized || rememberVoicePlaying(message, bodyScope.items)
    val wave = animateFloatAsState(
        targetValue = if (waving) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "emphasis-wave",
    )
    val wavePhase = rememberWavePhase(running = waving)
    Box(modifier) {
        Box(
            Modifier
                .padding(bottom = overhang.coerceAtLeast(0.dp))
                .maxWidthFraction(maxWidthFraction)
                .widthIn(max = 560.dp)
                .then(beforeClip)
                .graphicsLayer {
                    shape = WavyShape(bodyScope.shape, progress = wave.value, phase = wavePhase.value)
                    clip = true
                }
                .then(afterClip),
        ) {
            // Bez bubliny se kreslí na plochu konverzace, takže výchozí barva popředí patří
            // k NÍ. Bublina (`Bubble`) si ji přepíše svou.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                bodyScope.body()
            }
        }

        // Čipy reakcí LEŽÍ NA zprávě, u dolního nároží na straně odesílatele, a jedou s ní i při
        // tahu na odpověď — posun sedí na sloupci kolem.
        ReactionRow(
            reactions = reactions,
            onOpenDetails = onReactionClick,
            modifier = Modifier
                .align(if (message.isOutgoing) Alignment.BottomEnd else Alignment.BottomStart)
                // Kolečko lícuje s hranou bubliny, jako v Google Zprávách: řádek má kolem sebe
                // místo na stín a vpravo od kolečka ještě místo na odznak počtu, o to se vysune.
                .offset(
                    x = if (message.isOutgoing) {
                        REACTION_SHADOW_ROOM + REACTION_BADGE_ROOM
                    } else {
                        -REACTION_SHADOW_ROOM
                    },
                ),
        )

        extra()
    }
}

/**
 * Hraje právě tahle hlasovka? Přes `derivedStateOf`: pozice přehrávání se mění dvacetkrát za
 * vteřinu a položka se má překreslit jen při spuštění a zastavení.
 */
@Composable
private fun rememberVoicePlaying(message: ChatMessage, scope: ConversationItemScope): Boolean {
    if (message.media !is MessageMedia.VoiceNote) return false
    val playing by remember(scope, message.id) {
        derivedStateOf { scope.voicePlayback(message.id)?.playing == true }
    }
    return playing
}

/**
 * Zpráva, jak ji kreslí nabídka nad zprávou: tentýž obsah i tvar jako v seznamu — živý, takže
 * i samolepka hraje dál —, ale bez gest; nabídka nad ní rozhoduje sama.
 */
@Composable
internal fun MessageItem.MenuCopy(scope: ConversationItemScope, modifier: Modifier = Modifier) {
    val bodyScope = rememberMessageBodyScope(frame, scope, NoMenuAnchor)
    MessageBox(
        frame = frame,
        bodyScope = bodyScope,
        body = { Body() },
        modifier = modifier,
        // Kopie dostává stejné omezení šířky jako zpráva v seznamu (`MenuAnchor.maxWidth`),
        // takže i podíl šířky musí být stejný — jinak by se text zalomil jinak.
        maxWidthFraction = maxWidthFraction,
        onReactionClick = null,
        // Kopie ukazuje reakce z okamžiku otevření: výběr reakce nabídku hned zavře a kopie letí
        // zpátky — nová reakce patří až zprávě v seznamu, kde ji je vidět naskočit.
        reactions = heldReactions(frame.message.reactions, hold = true),
    )
}

/** Kopie v nabídce nabídku znovu neotevírá, takže nemá odkud vyjet. */
private val NoMenuAnchor = MenuAnchor(bounds = { null }, maxWidth = { null })

/**
 * Reakce, které se mají kreslit: dokud [hold] platí, drží se ty, co byly vidět naposledy.
 *
 * Reakce se přidává v nabídce nad zprávou a ta se tím zavře. Kdyby se změna projevila hned,
 * doběhla by animace náběhu čipu na zprávě, která je v tu chvíli skrytá (kreslí ji nabídka),
 * a uživatel by čip viděl už jen hotový. Takhle naskočí, až se zpráva vrátí na své místo —
 * stejně to dělají Google Zprávy.
 */
@Composable
private fun heldReactions(current: List<MessageReaction>, hold: Boolean): List<MessageReaction> {
    var held by remember { mutableStateOf(current) }
    if (!hold) held = current
    return held
}

/**
 * Obsah zprávy s odsazením. Bez pozadí je to celý obsah samolepky nebo velkých emoji,
 * v [Bubble] vnitřek bubliny.
 *
 * Se čipy reakcí se dole přidá místo, které zaberou — leží NA zprávě a bez toho by podlézaly
 * poslední řádek.
 */
@Composable
internal fun MessageBodyScope.PaddedColumn(
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier.padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = contentBottom),
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

/** Bublina: pozadí ve tvaru a barvě podle strany a sousedů, obsah odsazený. */
@Composable
internal fun MessageBodyScope.Bubble(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = bubbleColor, contentColor = onBubbleColor, shape = shape) {
        PaddedColumn(content = content)
    }
}

/**
 * Jméno odesílatele — jen u první zprávy bloku a jen tam, kde ho TDLib vyplní (skupiny). Ve stálé
 * barvě odesílatele ([senderColor]), aby se dal poznat i bez čtení jména.
 *
 * [background] je plocha, na které jméno leží: uvnitř bubliny její barva, u bloků bez bubliny
 * (holá fotka, album, karta náhledu odkazu) plocha konverzace. Barva odesílatele je laděná na
 * plochu konverzace, takže na zvýrazněné bublině může splynout — pak ustoupí barvě popředí
 * (viz [accentOn]).
 */
@Composable
internal fun MessageBodyScope.SenderName(
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.background,
) {
    if (continuesAbove) return
    message.senderName?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.labelLarge,
            color = accentOn(background, senderColor(message.senderId), LocalContentColor.current),
            modifier = modifier,
        )
    }
}

/**
 * „Přeposláno od …" nad obsahem přeposlané zprávy. Na rozdíl od jména odesílatele u KAŽDÉ
 * zprávy bloku — přeposlané zprávy téhož člověka bývají každá odjinud.
 *
 * Barva je barva popředí plochy, jen ztlumená: značka říká, odkud obsah je, a nesmí se
 * přetahovat se jménem odesílatele nad ní ani s textem pod ní. Jméno zdroje nese řez, ne
 * barvu — barva zdroje by se musela kontrolovat proti každé ploše zvlášť (viz [accentOn]).
 *
 * Ťuknutí vede ke zdroji ([MessageAction.OpenForwardOrigin]); vlastní klikání by bez
 * opakovaného dlouhého stisku nad značkou spolklo nabídku zprávy (stejná past jako u citace).
 */
@Composable
internal fun MessageBodyScope.ForwardLabel(modifier: Modifier = Modifier) {
    val origin = message.forwardOrigin ?: return
    val color = LocalContentColor.current.copy(alpha = FORWARD_LABEL_ALPHA)
    val name = origin.name
    val template = stringResource(Res.string.conversation_forwarded_from, FORWARD_NAME_SLOT)
    val fallback = stringResource(Res.string.conversation_forwarded)
    val text = remember(name, template, fallback) {
        if (name == null) {
            AnnotatedString(fallback)
        } else {
            // Jméno se vkládá až sem, aby šlo zvýraznit řezem, a pořadí slov zůstalo na překladu.
            val start = template.indexOf(FORWARD_NAME_SLOT)
            buildAnnotatedString {
                append(template.substring(0, start))
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(name) }
                append(template.substring(start + FORWARD_NAME_SLOT.length))
            }
        }
    }
    val clickable = origin !is ForwardOrigin.Hidden
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .then(
                if (clickable) {
                    Modifier.combinedClickable(
                        onClick = { act(MessageAction.OpenForwardOrigin(origin)) },
                        onLongClick = ::requestMenu,
                    )
                } else {
                    Modifier
                },
            )
            .padding(vertical = 2.dp),
    ) {
        Icon(
            painter = painterResource(Res.drawable.forward_24dp),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Značka přeposlání je doplněk, ne obsah — ustoupí textu zprávy. */
private const val FORWARD_LABEL_ALPHA = 0.78f

/** Zástupný řetězec za jméno v šabloně „Přeposláno od %1$s"; ve jménech se nevyskytne. */
private const val FORWARD_NAME_SLOT = "\u0001"

/**
 * Text zprávy s formátováním a odkazy. [linkColor] podle pozadí — viz [MessageBodyScope.accent].
 *
 * Při hledání v chatu je v textu zvýrazněné hledané slovo, u každé zprávy, ne jen u ukázaného
 * výsledku: je vidět, proč se zpráva našla, i u sousedů. Zvýraznění je OBRÁCENÁ bublina —
 * podklad v barvě textu, písmo v barvě bubliny. Funguje tak na každé z nich (odchozí, příchozí,
 * zvýrazněná, popisek pod fotkou), bez jediné pevné barvy.
 */
@Composable
internal fun MessageBodyScope.MessageText(
    linkColor: Color,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val query = items.searchHighlight()
    val highlights = remember(message.text, query) { query?.let { highlightRanges(message.text, it) }.orEmpty() }
    RichText(
        highlights = highlights,
        highlightColor = bubbleColor,
        highlightBackground = onBubbleColor,
        text = message.text,
        entities = message.entities,
        style = style,
        linkColor = linkColor,
        onLinkClick = { act(MessageAction.OpenLink(it)) },
        onEntityClick = { type ->
            when (type) {
                is MessageEntityType.Mention -> act(MessageAction.OpenMention(type.username))
                is MessageEntityType.MentionUser -> act(MessageAction.OpenUser(type.userId))
                is MessageEntityType.Hashtag -> act(MessageAction.SearchHashtag(type.tag))
                else -> Unit
            }
        },
        onLongClick = ::requestMenu,
    )
}

/**
 * „Upraveno". Čas v bublině není — datum nese předěl dne a plovoucí datum, čas tah doleva —,
 * ale že se zpráva změnila, se jinde nedozvíme.
 */
@Composable
internal fun MessageBodyScope.EditedLabel(color: Color, modifier: Modifier = Modifier) {
    if (!message.isEdited) return
    Text(
        text = stringResource(Res.string.conversation_edited),
        style = MaterialTheme.typography.labelSmall,
        color = color.copy(alpha = 0.7f),
        modifier = modifier,
    )
}

/**
 * Příloha zprávy s obsluhou napojenou na [ConversationItemScope].
 *
 * [mediaShape] je tvar fotky a videa bez bubliny; `null` znamená přílohu uvnitř bubliny.
 */
@Composable
internal fun MessageBodyScope.Attachment(
    media: MessageMedia,
    accent: Color,
    mediaShape: Shape? = null,
    fillWidth: Boolean = false,
) {
    val id = message.id
    MessageAttachment(
        media = media,
        messageId = id,
        onOpen = { openMedia(media) },
        onRequestMenu = ::requestMenu,
        uploading = message.sendState == SendState.Sending,
        onCancelUpload = { act(MessageAction.CancelUpload) },
        documentProgress = items::documentProgress,
        onOpenDocument = { act(MessageAction.OpenDocument) },
        accent = accent,
        mediaShape = mediaShape,
        fillWidth = fillWidth,
        voice = VoiceHandlers(
            playback = { items.voicePlayback(id) },
            onToggle = { act(MessageAction.ToggleVoice) },
            onSeek = { fraction -> act(MessageAction.SeekVoice(fraction)) },
            accent = accent,
            onAccent = if (message.isOutgoing) bubbleColor else MaterialTheme.colorScheme.onPrimary,
        ),
    )
}

/** Mřížka (nebo seznam) alba. [shape] je tvar mřížky bez bubliny; `null` uvnitř bubliny. */
@Composable
internal fun MessageBodyScope.Album(members: List<ChatMessage>, accent: Color, shape: Shape? = null) {
    AlbumContent(
        members = members,
        // Ťuknutí míří na KONKRÉTNÍ zprávu alba, ne na blok.
        onOpen = { id -> act(MessageAction.OpenMedia, id) },
        onRequestMenu = ::requestMenu,
        onCancelUpload = { id -> act(MessageAction.CancelUpload, id) },
        documentProgress = items::documentProgress,
        onOpenDocument = { id -> act(MessageAction.OpenDocument, id) },
        accent = accent,
        shape = shape,
    )
}

/** Ťuknutí na samolepku otevře její sadu, na fotku a video prohlížeč. */
private fun MessageBodyScope.openMedia(media: MessageMedia) {
    when (media) {
        is MessageMedia.Sticker -> if (media.setId != 0L) act(MessageAction.OpenStickerSet(media.setId))
        else -> act(MessageAction.OpenMedia)
    }
}

/**
 * Šipka, která se odkrývá při tažení zprávy.
 *
 * [progress] je **funkce, ne hodnota**. Čte se až ve vykreslovací fázi (`graphicsLayer`), takže
 * se při tahu nic nerekomponuje — jinak by každý snímek gesta znamenal přeskládání bubliny.
 */
@Composable
private fun ReplyHint(progress: () -> Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(REPLY_ICON_SIZE)
            .graphicsLayer {
                val current = progress()
                alpha = current
                // Roste s tahem, takže je poznat, jak daleko k prahu zbývá.
                val scale = REPLY_ICON_MIN_SCALE + (1f - REPLY_ICON_MIN_SCALE) * current
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(Res.drawable.reply_24dp),
            // Popis by se předčítal u každé zprávy v seznamu; gesto samo o sobě není prvek,
            // se kterým by čtečka obrazovky pracovala.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Profilovka odesílatele ve skupině. Menší než v seznamu chatů — doprovází bublinu, nevede ji. */
internal val SENDER_AVATAR_SIZE = 32.dp

/** Místo pro profilovku i s mezerou k bublině; stejné u zpráv s tváří i bez ní. */
internal val SENDER_AVATAR_SLOT = 40.dp


/**
 * Kolik z kolečka čipu leží NA bublině. Čip je odznak zprávy, takže přes nároží zasahovat musí —
 * ale jen o spodní odsazení obsahu (8 dp), ne středem: jinak by zakryl začátek posledního řádku.
 * Google Zprávy reakce taky nepřilepují středem.
 */
private val REACTION_CHIP_ON_BUBBLE = 8.dp

/** Kolik z řádku reakcí leží na bublině: kousek kolečka a nad ním místo na odznak počtu. */
private val REACTION_ON_BUBBLE = REACTION_SHADOW_ROOM + REACTION_BADGE_ROOM + REACTION_CHIP_ON_BUBBLE

/** Místo pod bublinou: celý čip i s místem na odznak počtu, bez části, která leží na bublině. */
private val REACTION_OVERHANG = REACTION_ROW_HEIGHT - REACTION_ON_BUBBLE


/**
 * Jak daleko se musí zpráva odtáhnout, aby se z gesta stala odpověď.
 *
 * Dost na to, aby to nešlo udělat omylem při scrollu, ale ne tolik, aby se palec musel
 * překládat.
 */
private val REPLY_THRESHOLD = 56.dp

/** Kam až se dá zpráva odtáhnout. Za prahem už jen kus navíc, aby gesto mělo doraz. */
private val REPLY_MAX_DRAG = 76.dp

/** Jak moc se tah za prahem brzdí. */
private const val DRAG_RESISTANCE = 0.3f

private val REPLY_ICON_SIZE = 32.dp

/** O kolik vlevo od bubliny šipka sedí — velikost ikony a mezera. */
private val REPLY_ICON_SLOT = 40.dp

/** Jak malá je šipka na začátku tahu. */
private const val REPLY_ICON_MIN_SCALE = 0.6f

/** Omezí šířku obsahu na [fraction] nabídnuté šířky (na rozdíl od `fillMaxWidth` jen shora). */
private fun Modifier.maxWidthFraction(fraction: Float): Modifier =
    if (fraction >= 1f) {
        this
    } else {
        layout { measurable, constraints ->
            val max = if (constraints.hasBoundedWidth) (constraints.maxWidth * fraction).toInt() else constraints.maxWidth
            val placeable = measurable.measure(
                constraints.copy(maxWidth = max, minWidth = constraints.minWidth.coerceAtMost(max)),
            )
            layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }
    }

/**
 * Pružný příchod nové zprávy: naskočí z menšího měřítka od strany odesílatele, s jemným
 * přeskokem (spatial spec). Jen u zpráv, které přišly, když byla konverzace otevřená, a jen
 * jednou — položka se při scrollu skládá znovu a pružit by nemělo co.
 *
 * Zpráva s letem ze vstupního pole má vlastní animaci, ta se nepřebíjí.
 */
@Composable
private fun Modifier.arrival(frame: MessageFrameData, scope: ConversationItemScope): Modifier {
    val start = remember(frame.key) {
        if (frame.flight == null && scope.claimArrival(frame.key, frame.message.timestamp)) ARRIVAL_SCALE else 1f
    }
    if (start == 1f) return this
    val scale = remember(frame.key) { Animatable(start) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(frame.key) { scale.animateTo(1f, spec) }
    val outgoing = frame.message.isOutgoing
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        transformOrigin = TransformOrigin(if (outgoing) 1f else 0f, 1f)
    }
}

/** Z jakého měřítka nová zpráva naskočí. */
private const val ARRIVAL_SCALE = 0.85f

/** Pulz zvýraznění po skoku: kolikrát a na jaké měřítko. */
private const val HIGHLIGHT_PULSES = 2
private const val HIGHLIGHT_SCALE = 1.06f

/** Průhlednost odesílané zprávy, než ji server potvrdí. */
private const val SENDING_ALPHA = 0.65f

/** Jak silný je pruh za vybranou zprávou. Průsvitný, aby text i bubliny zůstaly čitelné. */
private const val SELECTION_TINT_ALPHA = 0.55f

/** Kolečko výběru je menší než ikona v liště — je to značka vedle zprávy, ne tlačítko. */
private val SELECTION_MARK_SIZE = 20.dp

/** Odstup kolečka od zprávy i od kraje. */
private val SELECTION_MARK_PADDING = 6.dp
