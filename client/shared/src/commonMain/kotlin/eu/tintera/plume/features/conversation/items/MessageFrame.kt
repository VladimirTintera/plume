package eu.tintera.plume.features.conversation.items

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.RichText
import eu.tintera.plume.core.ui.sharedFlight
import eu.tintera.plume.features.conversation.MessageFlight
import eu.tintera.plume.features.conversation.MessageMenu
import eu.tintera.plume.features.conversation.MessageMenuOverlay
import eu.tintera.plume.features.conversation.ReactionRow
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.conversation_edited
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
    /** Krátce po skoku na citaci — zpráva, na kterou se skočilo. */
    val highlighted: Boolean,
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
)

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

    @Composable
    fun MessageBodyScope.Body()

    @Composable
    override fun LazyItemScope.Content(scope: ConversationItemScope) {
        // Cíl letu se nezjevuje prolnutím: během letu se kreslí v překryvu sdílených prvků, kam
        // průhlednost položky nedosáhne, a po přistání by skočil z plné viditelnosti do
        // rozjetého prolnutí.
        val modifier = Modifier.animateItem(
            fadeInSpec = if (frame.flight != null) null else spring(stiffness = Spring.StiffnessMediumLow),
        )
        RevealingTime(frame.message.timestamp, scope.timeReveal, modifier) {
            MessageFrame(frame, scope) { Body() }
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
     * Kolik místa si obsah nechá dole. Čip reakce leží PŘES dolní nároží — dolní půlka mu visí
     * ven, horní na zprávě —, takže se rezervuje jen ta horní půlka. Platí pro to, co je ve
     * zprávě poslední: bublinu, popisek pod holou fotkou i popis náhledu odkazu.
     */
    val contentBottom: Dp,
) {
    val message: ChatMessage get() = frame.message
    val continuesAbove: Boolean get() = frame.continuesAbove

    /** Akce nad touhle zprávou, nebo nad [messageId] — u alba nad konkrétní fotkou. */
    fun act(action: MessageAction, messageId: Long = message.id) = items.onAction(messageId, action)

    /**
     * Dlouhý stisk: nabídka s kandidáty, které nabízí blok. Potomci s vlastním klikáním (odkaz,
     * citace, fotka) ho k rámu nepustí, takže ho musí zopakovat sami — proto je tu, ne v rámu.
     */
    fun requestMenu() = act(MessageAction.ShowMenu(frame.menuActions))
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
    body: @Composable MessageBodyScope.() -> Unit,
) {
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
    val bubbleColor = if (message.isOutgoing) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val onBubbleColor = if (message.isOutgoing) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val accent = if (message.isOutgoing) onBubbleColor else MaterialTheme.colorScheme.primary

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
    val topInner = if (frame.continuesAbove) BUBBLE_TIGHT else BUBBLE_CORNER
    val bottomInner = if (frame.continuesBelow) BUBBLE_TIGHT else BUBBLE_CORNER
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

    val contentBottom = if (message.reactions.isEmpty()) 8.dp else REACTION_STRIP_SPACE

    val bodyScope = remember(scope, frame, shape, bubbleColor, onBubbleColor, accent, contentBottom) {
        MessageBodyScope(
            items = scope,
            frame = frame,
            shape = shape,
            bubbleColor = bubbleColor,
            onBubbleColor = onBubbleColor,
            accent = accent,
            contentBottom = contentBottom,
        )
    }

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
     * Zvýraznění po skoku: pruh přes celou šířku, který se rozsvítí a pomalu vyhasne.
     *
     * Přes celou šířku, ne jen bublina — obarvit bublinu by u odchozí `primary` nebylo vidět
     * a u příchozí by to vypadalo jako změna stavu zprávy. Průhlednost se čte až při kreslení,
     * takže vyhasínání nic nerekomponuje.
     */
    val highlightAlpha by animateFloatAsState(
        targetValue = if (frame.highlighted) HIGHLIGHT_ALPHA else 0f,
        animationSpec = if (frame.highlighted) {
            MaterialTheme.motionScheme.fastEffectsSpec()
        } else {
            MaterialTheme.motionScheme.slowEffectsSpec()
        },
        label = "highlight",
    )
    val highlightColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Zprávy od jednoho odesílatele drží u sebe, nová skupina se oddělí. Tvar bublin
            // to už rozlišuje staženým nárožím, mezera tomu jen dává prostor.
            .padding(top = if (frame.continuesAbove) 0.dp else GROUP_SPACING)
            .drawBehind {
                if (highlightAlpha > 0f) {
                    drawRoundRect(
                        color = highlightColor,
                        alpha = highlightAlpha,
                        cornerRadius = CornerRadius(HIGHLIGHT_CORNER.toPx()),
                    )
                }
            }
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
             */
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
            },
        horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start,
        // Tvář sedí u spodní hrany bubliny — u dlouhé zprávy by uprostřed nebylo poznat, ke které
        // části bloku patří.
        verticalAlignment = Alignment.Bottom,
    ) {
        // Mimo posouvaný `Box`: při tažení na odpověď jede bublina, tvář zůstává na místě.
        when (frame.avatar) {
            SenderAvatar.None -> Unit
            SenderAvatar.Spacer -> Spacer(Modifier.width(SENDER_AVATAR_SLOT))
            SenderAvatar.Shown -> Avatar(
                chatId = message.senderId,
                title = message.senderName.orEmpty(),
                avatarFileId = message.senderAvatarFileId,
                size = SENDER_AVATAR_SIZE,
                modifier = Modifier.padding(end = SENDER_AVATAR_SLOT - SENDER_AVATAR_SIZE),
            )
        }

        Column(
            // Posun se čte až ve fázi layoutu (`offset { }`), ne v kompozici. Jinak by se celá
            // zpráva i s obsahem přeskládávala na každý snímek tahu.
            Modifier.offset { IntOffset(drag.value.roundToInt(), 0) },
        ) {
            /*
             * `Box` musí obepínat SAMOTNOU zprávu, ne zprávu s odznakem.
             *
             * Nabídka nad zprávou si z něj bere `anchorBounds` a vykrajuje podle nich díru do
             * závoje; kdyby v něm byl i odznak doručení, byla by díra o ten kus vyšší a vedle
             * bubliny. Odznak proto stojí až za ním, ve sloupci.
             */
            // Viditelná část zprávy pro nabídku (viz `MessageMenuOverlay`). Měří se jen, když je
            // nabídka otevřená — jinak by každá položka při scrollu zapisovala stav.
            var visibleBounds by remember { mutableStateOf<Rect?>(null) }

            /*
             * Snímek zprávy pro nabídku, která ji posune do pohledu (viz `liftIntoView`).
             *
             * Nahrává se do vrstvy jen při otevřené nabídce a pořídí se z ní jednou, jakmile je
             * nahraná. Vrstva kreslí CELÝ obsah, i část, kterou seznam ořízl — proto jde vysunout
             * i zpráva, která začíná nad okrajem obrazovky.
             */
            val snapshotLayer = rememberGraphicsLayer()
            var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
            val menuOpen = frame.menu != null
            LaunchedEffect(menuOpen) {
                snapshot = null
                if (!menuOpen) return@LaunchedEffect
                // Nahrání proběhne až při kreslení, tedy nejdřív v příštím snímku.
                repeat(SNAPSHOT_FRAMES) {
                    withFrameNanos { }
                    if (snapshotLayer.size != IntSize.Zero) {
                        snapshot = snapshotLayer.toImageBitmap()
                        return@LaunchedEffect
                    }
                }
            }

            Box(
                if (menuOpen) {
                    Modifier
                        .onGloballyPositioned { visibleBounds = it.boundsInWindow() }
                        .drawWithContent {
                            // S pořízeným snímkem zůstane místo zprávy PRÁZDNÉ, jako v Google
                            // Zprávách: zpráva „odjela" do nabídky a pod závojem by jinak byla
                            // podruhé, ztmavená a jinde.
                            if (snapshot != null) return@drawWithContent
                            snapshotLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(snapshotLayer)
                        }
                } else {
                    Modifier
                },
            ) {
                Box(
                    Modifier
                        /*
                         * Místo na přesah čipu reakce POD zprávou.
                         *
                         * Je uvnitř `Box`u, který ukotvuje nabídku, takže přesah zůstane v díře
                         * vykrojené do závoje — čip se při dlouhém stisku nesmí ztmavit, patří
                         * ke zprávě. Bez reakcí si nebere nic.
                         */
                        .padding(bottom = if (message.reactions.isEmpty()) 0.dp else REACTION_OVERHANG)
                        .widthIn(max = 560.dp)
                        // Cílová strana letu ze vstupního pole. Sdílí se CELÁ zpráva, ne jen text —
                        // kdyby se sdílel jen text, doletěl by sám a pozadí by pod ním na konci
                        // skokem naskočilo.
                        .then(
                            frame.flight?.let { Modifier.sharedFlight(it.sharedKey, visible = true) }
                                ?: Modifier,
                        )
                        // Ořez MUSÍ být před klikáním, jinak by se vlnka dlouhého stisku kreslila
                        // do obdélníku a přetekla přes zaoblená nároží.
                        .clip(shape)
                        /*
                         * Ťuknutí na VLASTNÍ zprávu si vyžádá odznak přečtení, druhé ho schová.
                         *
                         * Je to ubezpečení na vyžádání: odznak se automaticky ukazuje jen
                         * u nejnovější zprávy, takže u starší se jinak nedá zjistit, jestli ji
                         * protějšek přečetl.
                         *
                         * Potomci s vlastním klikáním (odkaz v textu, citace, fotka, náhled odkazu,
                         * samolepka) si ťuknutí berou sami, takže u zprávy složené z fotky není kam
                         * ťuknout. Přidávat jim ho by znamenalo brát ťuknutí těm akcím — u fotky
                         * otevření prohlížeče —, a to je horší než chybějící zkratka.
                         */
                        .combinedClickable(
                            onLongClick = bodyScope::requestMenu,
                            onClick = { frame.onTap?.let(bodyScope::act) },
                        ),
                ) {
                    // Bez bubliny se kreslí na plochu konverzace, takže výchozí barva popředí
                    // patří k NÍ. Bublina (`Bubble`) si ji přepíše svou.
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                        bodyScope.body()
                    }
                }

                /*
                 * Čipy reakcí LEŽÍ NA zprávě, u dolního nároží na straně odesílatele.
                 *
                 * Jsou uvnitř `Box`u, který ukotvuje nabídku, takže zůstávají i uvnitř díry
                 * v závoji — při dlouhém stisku se nesmí ztmavit, patří ke zprávě. A jedou s ní
                 * i při tahu na odpověď, protože posun sedí na sloupci kolem tohohle `Box`u.
                 */
                ReactionRow(
                    reactions = message.reactions,
                    onOpenDetails = if (frame.reactionsOpenable) {
                        { bodyScope.act(MessageAction.ShowReactions) }
                    } else {
                        null
                    },
                    modifier = Modifier
                        .align(if (message.isOutgoing) Alignment.BottomEnd else Alignment.BottomStart)
                        .padding(horizontal = REACTION_INSET),
                )

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

                /*
                 * Nabídka visí v kompozici POD zprávou, i když se kreslí přes celou obrazovku.
                 *
                 * Popup si díky tomu vezme rozměry zprávy sám — `calculatePosition` dostane
                 * `anchorBounds` toho prvku, pod kterým je deklarovaný. Past: nesmí viset uvnitř
                 * OBSAHU bubliny, tam by ukotvením byla plocha zmenšená o odsazení a díra v závoji
                 * by byla menší než bublina. Proto ten vnější `Box`.
                 */
                frame.menu?.let {
                    MessageMenuOverlay(
                        menu = it,
                        bubbleShape = shape,
                        onAction = bodyScope::act,
                        // Do nabídky jde SKUTEČNÝ stav, ne to, co zrovna ukazuje odznak.
                        delivery = frame.delivery,
                        visibleBounds = visibleBounds,
                        snapshot = snapshot,
                        contentTop = scope.contentTop(),
                    )
                }
            }

            DeliveryBadge(
                delivery = frame.badge,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
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

/** Jméno odesílatele — jen u první zprávy bloku a jen tam, kde ho TDLib vyplní (skupiny). */
@Composable
internal fun MessageBodyScope.SenderName(modifier: Modifier = Modifier) {
    if (continuesAbove) return
    message.senderName?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = modifier,
        )
    }
}

/** Text zprávy s formátováním a odkazy. [linkColor] podle pozadí — viz [MessageBodyScope.accent]. */
@Composable
internal fun MessageBodyScope.MessageText(
    linkColor: Color,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    RichText(
        text = message.text,
        entities = message.entities,
        style = style,
        linkColor = linkColor,
        onLinkClick = { act(MessageAction.OpenLink(it)) },
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
private val SENDER_AVATAR_SIZE = 32.dp

/** Místo pro profilovku i s mezerou k bublině; stejné u zpráv s tváří i bez ní. */
private val SENDER_AVATAR_SLOT = 40.dp

/** Jak výrazné je zvýraznění zprávy po skoku. Jemné: má ukázat místo, ne překrýt obsah. */
private const val HIGHLIGHT_ALPHA = 0.14f

private val HIGHLIGHT_CORNER = 12.dp

/** Kolik z čipu reakce visí POD bublinou. Zhruba jeho půlka, jako v Google Messages. */
private val REACTION_OVERHANG = 16.dp

/** Kolik místa si obsah bubliny nechá dole pod poslední řádkou, aby ho čip nezakryl. */
private val REACTION_STRIP_SPACE = 20.dp

/** Odsazení čipů od svislého okraje bubliny. */
private val REACTION_INSET = 10.dp

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

/** Kolik snímků se nejvýš čeká, než se vrstva se zprávou nahraje. Pak nabídka zůstane u díry. */
private const val SNAPSHOT_FRAMES = 3
