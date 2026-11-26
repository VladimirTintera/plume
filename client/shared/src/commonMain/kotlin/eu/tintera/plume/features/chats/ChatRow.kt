package eu.tintera.plume.features.chats

import eu.tintera.plume.core.accounts.AccountBadges
import eu.tintera.plume.core.accounts.BadgeAccount
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Immutable
import androidx.compose.material3.ripple
import androidx.compose.foundation.interaction.MutableInteractionSource
import eu.tintera.plume.core.ui.LocalChatPreviewLines
import eu.tintera.plume.core.ui.animatedGroupItemShape
import eu.tintera.plume.core.settings.ThemeColor
import eu.tintera.plume.app.root.presentation.LocalIsDarkTheme
import eu.tintera.plume.app.root.presentation.theme.chatColorScheme
import eu.tintera.plume.app.root.presentation.theme.seed
import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import eu.tintera.plume.core.ui.asUpcomingTime
import eu.tintera.plume.resources.snooze_24dp
import eu.tintera.plume.resources.chats_snooze_reminder
import eu.tintera.plume.resources.chats_snoozed
import eu.tintera.plume.resources.chats_snoozed_until
import eu.tintera.plume.resources.chats_swipe_snooze
import eu.tintera.plume.resources.chats_swipe_unsnooze
import eu.tintera.plume.resources.chats_swipe_archive
import eu.tintera.plume.resources.chats_swipe_unarchive
import eu.tintera.plume.resources.chats_swipe_read
import eu.tintera.plume.resources.chats_swipe_unread
import eu.tintera.plume.resources.chats_swipe_mute
import eu.tintera.plume.resources.chats_swipe_unmute
import eu.tintera.plume.resources.chats_swipe_pin
import eu.tintera.plume.resources.chats_swipe_unpin
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import androidx.compose.runtime.rememberUpdatedState
import org.jetbrains.compose.resources.StringResource
import eu.tintera.plume.resources.keep_off_24dp
import eu.tintera.plume.resources.notifications_24dp
import eu.tintera.plume.resources.mark_chat_unread_24dp
import eu.tintera.plume.core.ui.SwipeThresholdBadge
import androidx.compose.runtime.derivedStateOf
import eu.tintera.plume.core.ui.RollingNumber
import eu.tintera.plume.core.ui.SwipeDirection
import eu.tintera.plume.core.ui.detectHorizontalSwipe
import androidx.compose.material3.LocalContentColor
import eu.tintera.plume.core.ui.SecretChatLock
import eu.tintera.plume.core.ui.DirectMessagesMark
import eu.tintera.plume.core.ui.VerificationBadge
import eu.tintera.plume.core.ui.EmojiStatusBadge
import eu.tintera.plume.core.ui.appendWithCustomEmoji
import eu.tintera.plume.core.ui.customEmojiInlineContent
import eu.tintera.plume.core.ui.groupRow
import eu.tintera.plume.core.ui.threshold
import eu.tintera.plume.resources.favorite_filled_24dp
import eu.tintera.plume.resources.alternate_email_24dp
import eu.tintera.plume.resources.chats_unread_reaction
import eu.tintera.plume.resources.ballot_24dp
import eu.tintera.plume.resources.chats_unread_poll_vote
import eu.tintera.plume.resources.chats_unread_mention
import eu.tintera.plume.resources.chats_awaiting_reply
import eu.tintera.plume.resources.reply_24dp
import eu.tintera.plume.core.ui.isSavedMessages
import eu.tintera.plume.core.ui.chatTitle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import eu.tintera.plume.core.ui.contextClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.appendInlineContent
import eu.tintera.plume.features.conversation.items.DeliveryStateIcon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import eu.tintera.plume.core.ui.OnlineDot
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.layout.onSizeChanged
import eu.tintera.plume.core.telegram.ChatSponsor
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.core.ui.Avatar
import eu.tintera.plume.core.ui.StoryRingState
import eu.tintera.plume.core.ui.storyRing
import eu.tintera.plume.resources.stories_open
import eu.tintera.plume.core.ui.accentOn
import eu.tintera.plume.core.ui.asRelativeTime
import eu.tintera.plume.core.ui.chatAvatarKey
import eu.tintera.plume.core.ui.rememberChatContainerSource
import eu.tintera.plume.core.ui.chatTitleKey
import eu.tintera.plume.core.ui.label
import eu.tintera.plume.core.ui.resolve
import eu.tintera.plume.core.ui.sharedNavElement
import eu.tintera.plume.core.ui.sharedNavText
import eu.tintera.plume.core.ui.tabularNumbers
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.chats_sponsor_announcement
import eu.tintera.plume.resources.chats_sponsor_proxy
import eu.tintera.plume.resources.archive_24dp
import eu.tintera.plume.resources.unarchive_24dp
import eu.tintera.plume.resources.chats_draft_prefix
import eu.tintera.plume.resources.chats_muted
import eu.tintera.plume.resources.chats_section_pinned
import eu.tintera.plume.resources.notifications_off_24dp
import eu.tintera.plume.resources.push_pin_filled_24dp
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import eu.tintera.plume.core.ui.avatarShape
import org.jetbrains.compose.resources.DrawableResource
import kotlinx.coroutines.launch
import eu.tintera.plume.resources.chats_action_archive
import eu.tintera.plume.resources.chats_action_unarchive
import eu.tintera.plume.resources.chats_action_mark_read
import eu.tintera.plume.resources.chats_action_mark_unread
import eu.tintera.plume.resources.chats_action_mute
import eu.tintera.plume.resources.chats_action_unmute
import eu.tintera.plume.resources.chats_action_pin
import eu.tintera.plume.resources.chats_action_unpin
import eu.tintera.plume.resources.chats_action_snooze
import eu.tintera.plume.resources.chats_action_unsnooze
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * Chat tahem do strany: akce podle Nastavení, zvlášť pro tah doprava ([right]) a doleva
 * ([left]); `null` = na tu stranu tah nejde. Nevratné nic z toho není — archivace má snackbar
 * „Vrátit" jako z nabídky ([ChatActionsHost]) a ostatní akce se týmž tahem otočí, takže se tah
 * nepotvrzuje.
 *
 * **Vlastní detektor, ne `SwipeToDismissBox`.** Ten stojí na `draggable`, které si tah vezme po
 * obyčejném touch slopu a je přitom směrově agnostické — palec jde na začátku scrollu obloukem do
 * strany, takže seznamu gesto sebral a chaty odlétaly do archivu při obyčejném scrollování.
 * A archivoval i ZKRATKOU PŘES RYCHLOST: tah rychlejší než 125 dp/s bral jako dokončený bez
 * ohledu na to, kam došel, a vypnout to zvenku nejde. Tady rozhoduje [detectHorizontalSwipe]
 * (výrazně vodorovný tah, dvojnásobek slopu) a [chatSwipeThreshold] (kus šířky řádku);
 * rychlost se nepočítá vůbec.
 *
 * **Čtečka obrazovky tah neudělá**, proto jsou obě akce i vlastními akcemi řádku
 * (`customActions`, v TalkBacku „Akce"). [content] dostane modifikátor, který je nese, a musí ho
 * dát uzlu s ťuknutím: akce patří ke chatu, na který čtečka ukazuje, a samostatný obal by byl
 * pro ni prázdný uzel, na který se nedá zaměřit.
 */
@Composable
internal fun SwipeableChat(
    right: ChatSwipeEffect?,
    left: ChatSwipeEffect?,
    shape: RoundedCornerShape,
    onSwipe: (ChatSwipeEffect) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (swipeActions: Modifier) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val thresholdCap = with(LocalDensity.current) { SWIPE_THRESHOLD_MAX.toPx() }

    /*
     * Posun drží `remember`, NE `rememberSaveable`: `LazyColumn` si stav položky pamatuje podle
     * jejího klíče i poté, co položka ze seznamu zmizí — a chat odtažený tahem právě mizí. Po
     * „Vrátit" by se tedy vrátil rovnou odtažený, tedy neviditelný.
     */
    val drag = remember { Animatable(0f) }
    /** Tah je právě za prahem; hlídá, aby se haptika ozvala jednou za každé překročení. */
    var armed by remember { mutableStateOf(false) }
    // Spec se musí vzít v kompozici — konec gesta je obyčejná suspend funkce, ne composable.
    val returnSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    // Detektor běží v `pointerInput(Unit)` přes celý život položky; akce se mění se stavem
    // chatu (ztlumený → odtlumit), takže se čtou čerstvé, ne ty z prvního snímku.
    val currentRight by rememberUpdatedState(right)
    val currentLeft by rememberUpdatedState(left)
    // Totéž pro akci: lambda drží chat z kompozice, ve které vznikla, a ten ze prvního snímku
    // by tvrdil, že nepřipnutý chat pořád není připnutý — druhý tah by ho připnul znovu.
    val currentOnSwipe by rememberUpdatedState(onSwipe)

    // Šířka řádku pro práh mimo `pointerInput` — podklad ji potřebuje ke zvýraznění ikony.
    var width by remember { mutableIntStateOf(0) }
    // Ikona ukazuje, jestli by se akce provedla PRÁVĚ TEĎ: vrácený prst ji vrátí do kolečka.
    val overThreshold by remember {
        derivedStateOf { width > 0 && abs(drag.value) >= chatSwipeThreshold(width.toFloat(), thresholdCap) }
    }

    /*
     * Strana, ze které se podklad odkrývá. Čte se hrubě přes `derivedStateOf`: `drag.value` se
     * mění každý snímek tahu a podklad se má překreslit jen při změně strany.
     */
    val side by remember {
        derivedStateOf {
            when {
                drag.value > 0f -> SwipeSide.Start
                drag.value < 0f -> SwipeSide.End
                else -> SwipeSide.None
            }
        }
    }

    // Klíč je směr, ne akce: změna akce (ztlumený chat) detektor nerestartuje, změna toho, kam
    // táhnout jde (Nastavení, vstup do výběru), ano — jinak by se chytal tah na zakázanou stranu.
    val direction = when {
        right != null && left != null -> SwipeDirection.Either
        right != null -> SwipeDirection.Right
        left != null -> SwipeDirection.Left
        else -> null
    }

    Box(
        modifier.onSizeChanged { width = it.width }.then(
            if (direction == null) {
                Modifier
            } else {
                Modifier.pointerInput(direction) {
                    val maxDrag = { size.width.toFloat() }
                    val threshold = { chatSwipeThreshold(size.width.toFloat(), thresholdCap) }
                    detectHorizontalSwipe(
                        direction = direction,
                        onDrag = { delta ->
                            coroutineScope.launch {
                                // Na stranu bez akce se řádek nepohne, ani když tah začal opačně.
                                val min = if (currentLeft != null) -maxDrag() else 0f
                                val max = if (currentRight != null) maxDrag() else 0f
                                val next = (drag.value + delta).coerceIn(min, max)
                                drag.snapTo(next)
                                // Vrácený prst práh zase odjistí: další překročení v témže tahu
                                // se musí ozvat znovu, stejně jako se znovu mění ikona.
                                val past = abs(next) >= threshold()
                                if (past && !armed) haptics.threshold()
                                armed = past
                            }
                        },
                        onEnd = { released ->
                            val committed = (if (drag.value > 0f) currentRight else currentLeft)
                                ?.takeIf { released && abs(drag.value) >= threshold() }
                            val target = if (drag.value > 0f) maxDrag() else -maxDrag()
                            armed = false
                            coroutineScope.launch {
                                /*
                                 * Akce se provede při PUŠTĚNÍ, ne při překročení prahu — kdo si to
                                 * rozmyslí, vrátí prst zpátky a nic se nestane. Akce odchází
                                 * PŘED animací odjezdu: odscrollovaná položka se uklidí
                                 * z kompozice i s tímhle rozsahem a animace by ji vzala s sebou.
                                 */
                                committed?.let(currentOnSwipe)
                                if (committed?.leavesList == true) {
                                    drag.animateTo(target, returnSpec)
                                    /*
                                     * Chat, který po archivaci ze seznamu odešel, se uklidí i s tímhle
                                     * rozsahem a sem se nedojde. Zůstal-li (složka s archivovanými
                                     * chaty, chyba od TDLib), vrátí se — jinak by v seznamu visel
                                     * neviditelný řádek, na který jde ťuknout.
                                     */
                                    delay(LEFT_ROW_RETURN_DELAY)
                                }
                                drag.animateTo(0f, returnSpec)
                            }
                        },
                    )
                }
            },
        ),
    ) {
        /*
         * Bez tahu se podklad NEKRESLÍ VŮBEC, ani jeho plocha. Řádek nad ním má tentýž tvar,
         * jenže vyhlazené hrany (zaoblení, okraje na zlomcích pixelu) jsou poloprůhledné a
         * podklad jimi prosvítal: v tmavém tématu byl kolem každého chatu světlý lem
         * `primaryContainer` na mnohem tmavším řádku.
         */
        val effect = when (side) {
            SwipeSide.Start -> right
            SwipeSide.End -> left
            SwipeSide.None -> null
        }
        if (effect != null) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = if (side == SwipeSide.End) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                val icon = @Composable {
                    // Za prahem kolečko naroste do sušenky a vyplní se — spolu s haptikou.
                    SwipeThresholdBadge(
                        armed = overThreshold,
                        painter = painterResource(effect.icon),
                        surface = MaterialTheme.colorScheme.primaryContainer,
                        idleFill = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = SWIPE_BADGE_IDLE_ALPHA),
                        idleIcon = MaterialTheme.colorScheme.onPrimaryContainer,
                        size = 40.dp,
                        iconSize = 24.dp,
                    )
                }
                val label = @Composable {
                    Text(
                        text = stringResource(effect.label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Row(
                    // 16 + (40 − 24) / 2 = 24 dp: ikona stojí tam, kde stávala bez kolečka.
                    Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Ikona patří k hraně, ze které se podklad odkrývá — objeví se první
                    // a popisek ji jen doplní. Opačné pořadí ukáže na začátku tahu konec
                    // věty, ze kterého se nepozná, co se stane.
                    if (side == SwipeSide.End) {
                        label()
                        icon()
                    } else {
                        icon()
                        label()
                    }
                }
            }
        }
        // Popisky z nabídky nad chatem: v seznamu akcí stojí samy, bez ikony a podkladu.
        val labels = listOfNotNull(right, left).map { it to stringResource(it.actionLabel) }
        val swipeActions = if (labels.isEmpty()) {
            Modifier
        } else {
            Modifier.semantics {
                customActions = labels.map { (effect, label) ->
                    CustomAccessibilityAction(label) {
                        currentOnSwipe(effect)
                        true
                    }
                }
            }
        }
        // Posun se čte až v layoutu, takže tah nic nerekomponuje.
        Box(Modifier.offset { IntOffset(drag.value.roundToInt(), 0) }) {
            content(swipeActions)
        }
    }
}

/**
 * Popisek akce na podkladu pod taženým chatem: říká, co se STANE, ne jaký je stav. Kratší než
 * věty z nabídky nad chatem — popisek leží v odkryté části a „Označit jako nepřečtené" by
 * u prahu (40 % šířky) zůstalo z půlky pod řádkem.
 */
private val ChatSwipeEffect.label: StringResource
    get() = when (this) {
        ChatSwipeEffect.Archive -> Res.string.chats_swipe_archive
        ChatSwipeEffect.Unarchive -> Res.string.chats_swipe_unarchive
        ChatSwipeEffect.MarkRead -> Res.string.chats_swipe_read
        ChatSwipeEffect.MarkUnread -> Res.string.chats_swipe_unread
        ChatSwipeEffect.Mute -> Res.string.chats_swipe_mute
        ChatSwipeEffect.Unmute -> Res.string.chats_swipe_unmute
        ChatSwipeEffect.Pin -> Res.string.chats_swipe_pin
        ChatSwipeEffect.Unpin -> Res.string.chats_swipe_unpin
        ChatSwipeEffect.Snooze -> Res.string.chats_swipe_snooze
        ChatSwipeEffect.Unsnooze -> Res.string.chats_swipe_unsnooze
    }

/**
 * Akce tahu pro čtečku obrazovky. Celá věta z nabídky nad chatem, ne krátký popisek z podkladu:
 * „Přečteno" nebo „Vrátit" bez taženého řádku nic neřeknou.
 */
private val ChatSwipeEffect.actionLabel: StringResource
    get() = when (this) {
        ChatSwipeEffect.Archive -> Res.string.chats_action_archive
        ChatSwipeEffect.Unarchive -> Res.string.chats_action_unarchive
        ChatSwipeEffect.MarkRead -> Res.string.chats_action_mark_read
        ChatSwipeEffect.MarkUnread -> Res.string.chats_action_mark_unread
        ChatSwipeEffect.Mute -> Res.string.chats_action_mute
        ChatSwipeEffect.Unmute -> Res.string.chats_action_unmute
        ChatSwipeEffect.Pin -> Res.string.chats_action_pin
        ChatSwipeEffect.Unpin -> Res.string.chats_action_unpin
        ChatSwipeEffect.Snooze -> Res.string.chats_action_snooze
        ChatSwipeEffect.Unsnooze -> Res.string.chats_action_unsnooze
    }

/** Ikony z nabídky nad chatem, ať tah a nabídka mluví stejně. */
private val ChatSwipeEffect.icon: DrawableResource
    get() = when (this) {
        ChatSwipeEffect.Archive -> Res.drawable.archive_24dp
        ChatSwipeEffect.Unarchive -> Res.drawable.unarchive_24dp
        ChatSwipeEffect.MarkRead, ChatSwipeEffect.MarkUnread -> Res.drawable.mark_chat_unread_24dp
        ChatSwipeEffect.Mute -> Res.drawable.notifications_off_24dp
        ChatSwipeEffect.Unmute -> Res.drawable.notifications_24dp
        ChatSwipeEffect.Pin -> Res.drawable.push_pin_filled_24dp
        ChatSwipeEffect.Unpin -> Res.drawable.keep_off_24dp
        ChatSwipeEffect.Snooze, ChatSwipeEffect.Unsnooze -> Res.drawable.snooze_24dp
    }

/** Podklad ikony akce do prahu: jemný tón barvy popředí na `primaryContainer`. */
private const val SWIPE_BADGE_IDLE_ALPHA = 0.12f

/**
 * Za jak dlouho po odjezdu se řádek vrátí, když chat v seznamu zůstal. Delší než odpověď TDLib
 * na archivaci a zmizení položky z obou seznamů, aby se odjetý chat nevracel do seznamu, ze
 * kterého právě odchází.
 */
private val LEFT_ROW_RETURN_DELAY = 1.seconds

/** Ze které hrany se odkrývá podklad pod taženým chatem. */
private enum class SwipeSide { None, Start, End }

/**
 * Strop prahu akce tahem. Na široké ploše (tablet, dvoupanel) by podíl šířky znamenal tah přes
 * půl obrazovky; na telefonu se neuplatní.
 */
private val SWIPE_THRESHOLD_MAX = 160.dp

/**
 * Co o chatu ví seznam navíc k [ChatSummary] a řádek to ukazuje značkou: výběr, odložení,
 * čekání na odpověď a příběhy autora.
 */
@Immutable
internal data class ChatRowMarks(
    /** Značka výběru víc chatů; `null` = výběr neběží (nebo chat vybrat nejde). */
    val selectionMark: Boolean?,
    /** Odložení chatu; `null` = neodložený. */
    val snooze: RowSnooze?,
    /** Model v zařízení usoudil, že chat čeká na odpověď (`ChatTriage`). */
    val awaitingReply: Boolean,
    /** Kroužek příběhů kolem avataru; `null` = autor žádné nemá. */
    val stories: StoryRingState? = null,
    /**
     * Účty, kterým chat patří, ve společném seznamu víc účtů; prázdné = jeden účet. Spojená
     * společná skupina jich má víc. Fotka účtu, ne barva: barvy už nesou chaty (`ChatColors`)
     * a pletly by se.
     */
    val accounts: List<BadgeAccount> = emptyList(),
)

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun ChatRow(
    chat: ChatSummary,
    selected: Boolean,
    marks: ChatRowMarks,
    /** Tvar bloku — počítá ho [animatedGroupItemShape] vedle, protože ho potřebuje i podklad pod tahem. */
    shape: RoundedCornerShape,
    /** Stisk řádku; tvar bloku z něj počítá volající (stisknutý řádek se vyčlení). */
    interactionSource: MutableInteractionSource,
    /** Paleta z barvy konverzace (odznaky nepřečtených); `null` = barvy aplikace. */
    accent: ColorScheme?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Ťuknutí na avatar otevře příběhy; zbytek řádku dál otevírá chat. */
    onAvatarClick: (() -> Unit)? = null,
) {
    /*
     * Spící odložený chat se tváří jako přečtený: jméno bez tučnosti, čas bez akcentu, odznaky
     * v neutrální barvě a místo náhledu věta, do kdy spí. Ze seznamu nezmizí — ztratit chat
     * z očí i z hledání pohledem by bylo víc, než si uživatel řekl.
     */
    val sleeping = marks.snooze is RowSnooze.Sleeping
    val showUnread = chat.hasUnread && !sleeping
    val previewLines = LocalChatPreviewLines.current
    /*
     * Blok je SVĚTLEJŠÍ než pozadí, ne tmavší.
     *
     * Původně to bylo obráceně — pozadí `surface` (nejsvětlejší role) a řádky `surfaceContainerHighest`
     * o tři stupně tmavší. Z položek pak byly šedé plochy na světlém a seznam působil těžce.
     * Takhle je to vztah „světlý panel na tónované stránce", což je i to, čím odstup dělá
     * Google Messages: jeho řádky samy žádnou výplň nemají.
     *
     * Výběr v list-detail je navigační stav, ne zvýraznění obsahu → `secondaryContainer`,
     * stejná role, jakou používá vybraná položka v nav railu.
     */
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.groupRow
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    // Čas a ikony: `onSurfaceVariant` je počítaný na surface role. Na vybrané položce by v paletě
    // se zvýšeným kontrastem (světlý `secondaryContainer` v tmavém tématu) nebyl vidět.
    val secondaryColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Řádek se při otevření chatu rozroste v jeho obrazovku (container transform).
    val containerSource = rememberChatContainerSource(chat.id)

    // `Card(onClick = …)` umí jen krátké ťuknutí. Dlouhý stisk potřebuje `combinedClickable`,
    // takže se klikání přesouvá na Surface — Card zůstává jen tvarem a barvou.
    Surface(
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .then(containerSource.container)
            .fillMaxWidth()
            .clip(shape)
            .contextClickable(
                interactionSource = interactionSource,
                indication = ripple(),
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .then(if (marks.selectionMark != null) Modifier.semantics { this.selected = marks.selectionMark } else Modifier),
    ) {
        Row(
            containerSource.content.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val avatarShape = chat.kind.avatarShape()
            val openStories = stringResource(Res.string.stories_open, chat.title)
            Box(
                Modifier
                    // U víceřádkového náhledu by avatar uprostřed utekl od jména.
                    .then(if (previewLines > 1) Modifier.align(Alignment.Top) else Modifier)
                    .storyRing(
                        ring = marks.stories,
                        shape = avatarShape,
                        unreadColor = if (marks.stories?.closeFriends == true) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                        readColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                    .then(
                        if (onAvatarClick == null) {
                            Modifier
                        } else {
                            Modifier.contextClickable(
                                interactionSource = null,
                                indication = ripple(bounded = false, radius = CHAT_ROW_AVATAR_SIZE / 2 + STORY_RING_OUTSET),
                                onClickLabel = openStories,
                                onLongClick = onLongClick,
                                onClick = onAvatarClick,
                            )
                        },
                    ),
            ) {
                Avatar(
                    chatId = chat.avatarColorId,
                    title = chat.title,
                    avatarFileId = chat.avatarFileId,
                    size = CHAT_ROW_AVATAR_SIZE,
                    isSavedMessages = isSavedMessages(chat.id),
                    // Druh chatu tvarem: člověk kruh, skupina sušenka, kanál čtverec.
                    shape = avatarShape,
                    // Ve sbaleném sloupci je avatar sám a přeletí tam (`collapseFlight`).
                    modifier = Modifier.collapseFlight(collapseAvatarKey(chat.id)).sharedNavElement(chatAvatarKey(chat.id)),
                )

                // Tečka sedí NA avataru, ne vedle jména: v seznamu se očima jede po levém
                // sloupci, takže se dostupnost přečte spolu s tím, kdo to je.
                // Ve výběru má roh avataru značka výběru; tečka dostupnosti by se s ní prala.
                if (marks.selectionMark != null) {
                    SelectionMark(
                        selected = marks.selectionMark,
                        rim = containerColor,
                        contentColor = contentColor,
                        modifier = Modifier.align(Alignment.BottomEnd).offset(x = 4.dp, y = 4.dp),
                    )
                } else if (chat.presence is Presence.Online) {
                    OnlineDot(rim = containerColor, modifier = Modifier.align(Alignment.BottomEnd), chatId = chat.id)
                }
                // Odznak účtu naproti tečce dostupnosti, s obroučkou barvou řádku jako ona.
                AccountBadges(
                    marks.accounts,
                    rim = containerColor,
                    modifier = Modifier.align(Alignment.BottomStart),
                    chatId = chat.id,
                    flight = { collapseBadgeFlight(chat.id, it) },
                )
            }

            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Vynořená připomínka: ikona na místě připínáčku a v akcentu, ať je poznat,
                    // proč chat stojí nahoře.
                    if (marks.snooze == RowSnooze.Due) {
                        Icon(
                            painterResource(Res.drawable.snooze_24dp),
                            contentDescription = stringResource(Res.string.chats_snooze_reminder),
                            modifier = Modifier.size(14.dp),
                            tint = accentOn(containerColor, MaterialTheme.colorScheme.primary, secondaryColor),
                        )
                    } else if (chat.isPinned) {
                        Icon(
                            painterResource(Res.drawable.push_pin_filled_24dp),
                            contentDescription = stringResource(Res.string.chats_section_pinned),
                            modifier = Modifier.size(14.dp),
                            tint = secondaryColor,
                        )
                    }
                    // Jméno a štítek ověření vyplní zbytek řádku, takže čas sedí na konci.
                    // Jméno uvnitř má váhu BEZ vyplnění: dlouhé se zkrátí a štítek za ním
                    // zůstane vidět, krátké nechá štítek hned za sebou.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        val badgeAccent = accentOn(
                            background = containerColor,
                            accent = MaterialTheme.colorScheme.primary,
                            fallback = LocalContentColor.current,
                        )
                        if (chat.isSecret) SecretChatLock(tint = badgeAccent)
                        if (chat.isDirectMessages) DirectMessagesMark(tint = badgeAccent)
                        Text(
                            text = chatTitle(chat.id, chat.title),
                            // `bodyLarge` místo `titleMedium`: stejná velikost, ale běžný řez.
                            // Polotučné jméno u KAŽDÉHO chatu znamená, že nevyčnívá žádný —
                            // tučností se proto odlišuje jen nepřečtený, jako v Google Messages.
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (showUnread) FontWeight.SemiBold else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).sharedNavText(chatTitleKey(chat.id)),
                        )
                        chat.verification?.let { VerificationBadge(verification = it, accent = badgeAccent) }
                        chat.emojiStatus?.let { EmojiStatusBadge(it, accent = badgeAccent) }
                    }

                    if (chat.isMuted) {
                        Icon(
                            painterResource(Res.drawable.notifications_off_24dp),
                            contentDescription = stringResource(Res.string.chats_muted),
                            modifier = Modifier.size(14.dp),
                            tint = secondaryColor,
                        )
                    }
                    if (sleeping) {
                        Icon(
                            painterResource(Res.drawable.snooze_24dp),
                            contentDescription = stringResource(Res.string.chats_snoozed),
                            modifier = Modifier.size(14.dp),
                            tint = secondaryColor,
                        )
                    }

                    // Čas je zarovnaný doprava na konec řádku s názvem, ne pod něj — jinak by
                    // soupeřil o místo s náhledem zprávy, který bývá dlouhý.
                    // Koncept posouvá chat v seznamu nahoru, takže čas musí jít s ním — jinak by
                    // u chatu nahoře stálo datum starší než u těch pod ním.
                    // Sponzorovaný chat má místo času důvod, proč v seznamu je: uživatel do něj
                    // nevstoupil a bez štítku by vypadal jako kanál, který si přidal a zapomněl.
                    val sponsor = chat.sponsor
                    if (sponsor != null) {
                        Text(
                            text = stringResource(
                                when (sponsor) {
                                    ChatSponsor.Proxy -> Res.string.chats_sponsor_proxy
                                    is ChatSponsor.Announcement -> Res.string.chats_sponsor_announcement
                                },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = secondaryColor,
                            maxLines = 1,
                        )
                    } else {
                        // Stav doručení vlastní zprávy před časem, jako v ostatních klientech. Ne
                        // u konceptu: čas pak patří jemu a fajfka by tvrdila, že odešel.
                        if (chat.draft == null) {
                            chat.previewDelivery()?.let { delivery ->
                                DeliveryStateIcon(delivery, tint = secondaryColor)
                            }
                        }
                        listOfNotNull(chat.lastMessageTimestamp, chat.draft?.timestamp).maxOrNull()?.let { timestamp ->
                            Text(
                                text = timestamp.asRelativeTime(),
                                // Časy stojí pod sebou u pravého okraje — stejně široké číslice
                                // je drží v zákrytu.
                                style = MaterialTheme.typography.labelMedium.tabularNumbers(),
                                // U nepřečteného chatu je čas akcentem, stejně jako odznak pod ním —
                                // oči jdou po pravém sloupci a hledají, kde se něco stalo.
                                // Ztlumený chat ne: počet tam je, ale nevolá (viz `UnreadBadges`).
                                color = if (showUnread && !selected && !chat.isMuted) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    secondaryColor
                                },
                                maxLines = 1,
                            )
                        }
                    }
                }
                /*
                 * Činnost > koncept > náhled poslední zprávy.
                 *
                 * „Píše…" je nejčerstvější informace. Koncept je to, co uživatel v chatu nechal
                 * rozdělané, a hledá ho spíš než poslední cizí zprávu. Obojí najednou se do
                 * řádku nevejde.
                 */
                val draftLabelColor = accentOn(containerColor, MaterialTheme.colorScheme.tertiary, contentColor)
                // Vlastní emoji v náhledu (a konceptu) jako obrázek, ne náhradní znak.
                val customEmoji = mutableSetOf<Long>()
                val sender = chat.previewSender()
                // Obrázek jen u náhledu poslední zprávy; činnost, koncept a odložení ho nemají.
                val thumbnail = chat.lastMessageThumbnail
                    ?.takeIf { marks.snooze !is RowSnooze.Sleeping && chat.activity == null && chat.draft == null }
                val sleepingUntil = (marks.snooze as? RowSnooze.Sleeping)?.let { (it.until / 1000).asUpcomingTime() }
                val subtitle: AnnotatedString? = sleepingUntil?.let { AnnotatedString(stringResource(Res.string.chats_snoozed_until, it)) }
                    ?: chat.activity?.let { AnnotatedString(it.label()) }
                    ?: chat.draft?.let { draft ->
                        buildAnnotatedString {
                            // Štítek jinou barvou než „píše…" (primary) — jde o náš text, ne o dění.
                            withStyle(SpanStyle(color = draftLabelColor)) { append(stringResource(Res.string.chats_draft_prefix)) }
                            // Zalomení by náhled useklo hned na začátku; mezera je stejně
                            // dlouhá, takže offsety emoji sedí dál.
                            customEmoji += appendWithCustomEmoji(draft.text.replace('\n', ' '), draft.entities)
                        }
                    }
                    ?: chat.lastMessagePreview?.resolve()?.let { preview ->
                        buildAnnotatedString {
                            // Kdo psal, barvou popředí jako v hledání a u témat: jméno je pevný
                            // bod, podle kterého oko řádek čte, i když je náhled přečtený.
                            sender?.let {
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurface)) { append(it) }
                                append(": ")
                            }
                            if (thumbnail != null) {
                                appendInlineContent(PREVIEW_THUMBNAIL_KEY, alternateText = " ")
                                append(' ')
                            }
                            // Zalomení by náhled useklo dřív, než zaplní svoje řádky; mezera je
                            // stejně dlouhá, takže offsety emoji sedí dál (jako u konceptu).
                            customEmoji += appendWithCustomEmoji(preview.replace('\n', ' '), chat.lastMessagePreviewEntities)
                        }
                    }

                // Odznaky na řádku náhledu, pod časem: dokud stály ve vlastním sloupci vedle obou
                // řádků, posunuly čas nepřečteného chatu doleva a časy nestály pod sebou.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        subtitle?.let {
                            Text(
                                text = it,
                                inlineContent = customEmojiInlineContent(customEmoji) + previewThumbnailInlineContent(thumbnail),
                                style = MaterialTheme.typography.bodyMedium,
                                // Nepřečtený náhled se odliší barvou, ne řezem: s polotučným názvem nad
                                // ním by byly na jednom řádku dvě úrovně tučnosti a blok by ztěžkl.
                                color = when {
                                    selected -> MaterialTheme.colorScheme.onSecondaryContainer
                                    sleeping -> secondaryColor
                                    // Činnost se odliší barvou, aby bylo poznat, že to není text zprávy.
                                    chat.activity != null -> accentOn(containerColor, MaterialTheme.colorScheme.primary, contentColor)
                                    chat.hasUnread -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = previewLines,
                                overflow = TextOverflow.Ellipsis,
                                // Jeden řádek se vejde do výšky avataru, řádek chatu tedy nemění výšku vůbec.
                                modifier = if (previewLines > 1) {
                                    Modifier.smoothPreviewHeight(chat.id, holdHeight = chat.activity != null)
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }
                    // Odznak (16 dp) na střed prvního řádku náhledu (20 sp), i u víc řádků.
                    Box(Modifier.padding(top = 2.dp)) {
                        UnreadBadges(chat, quiet = sleeping, quietCount = chat.isMuted, accent = accent, awaitingReply = marks.awaitingReply)
                    }
                }
            }
        }
    }
}

/**
 * Víceřádkový náhled má výšku podle obsahu a mění ji plynule, řádky pod ním jedou s ním.
 *
 * Effects spec, ne spatial: překmit výšky by rozkýval celý seznam pod řádkem. Při [holdHeight]
 * („píše…") drží poslední výšku: činnost je krátká a dlouhý náhled by se kvůli ní pokaždé
 * smrskl a zase roztáhl.
 */
@Composable
private fun Modifier.smoothPreviewHeight(chatId: Long, holdHeight: Boolean): Modifier {
    var settledHeight by remember(chatId) { mutableIntStateOf(0) }
    val minHeight = if (holdHeight) with(LocalDensity.current) { settledHeight.toDp() } else 0.dp
    return fillMaxWidth()
        .animateContentSize(MaterialTheme.motionScheme.defaultEffectsSpec())
        .heightIn(min = minHeight)
        .onSizeChanged { if (!holdHeight) settledHeight = it.height }
}

/** Odložení chatu z pohledu řádku: ještě spí (do [Sleeping.until], ms od epochy), nebo se vynořil. */
internal sealed interface RowSnooze {
    data class Sleeping(val until: Long) : RowSnooze
    data object Due : RowSnooze
}

/**
 * Odznaky vpravo v řádku: reakce, zmínka, počet nepřečtených — v tomhle pořadí, počet u kraje.
 *
 * Zmínka a reakce mají vlastní odznak, protože se v počtu ztratí: „@" ve skupině se stovkou
 * nepřečtených je to jediné, kvůli čemu ji uživatel otevře. Číslo nenesou (kolik jich je, řekne
 * tlačítko v konverzaci) — jde o to, ŽE nějaké jsou.
 *
 * Ztlumený chat ([quietCount]) má neutrální jen počet: zprávy v něm jsou, ale ztlumení říká, že
 * nevolají. Zmínka a reakce míří na uživatele a TDLib na ně upozorní i ve ztlumeném chatu, takže
 * akcent drží. Stejně to kreslí přepínač chatů v konverzaci (`ChatSwitcherButton`).
 *
 * Odznak „čeká na odpověď" ([awaitingReply], šipka odpovědi) je stejného druhu a stojí hned
 * u počtu: týká se právě těch nepřečtených zpráv. Tvar ikonového odznaku, ne štítek s textem —
 * řádek nemá kam růst a slovní štítek by se pral s časem i náhledem.
 */
@Composable
private fun UnreadBadges(
    chat: ChatSummary,
    /** Odznaky v neutrální barvě — u odloženého chatu, který se má do té doby neozývat. */
    quiet: Boolean = false,
    /** Počet nepřečtených v neutrální barvě — u ztlumeného chatu. */
    quietCount: Boolean = false,
    /** Paleta z barvy konverzace; odznak je pak jediné místo v řádku, které ji nese. */
    accent: ColorScheme? = null,
    awaitingReply: Boolean = false,
) {
    val scheme = accent ?: MaterialTheme.colorScheme
    val container = if (quiet) MaterialTheme.colorScheme.outlineVariant else scheme.primary
    val content = if (quiet) MaterialTheme.colorScheme.onSurfaceVariant else scheme.onPrimary
    if (chat.unreadPollVoteCount == 0 && chat.unreadReactionCount == 0 && chat.unreadMentionCount == 0 && chat.unreadCount == 0) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (chat.unreadPollVoteCount > 0) {
            IconBadge(Res.drawable.ballot_24dp, stringResource(Res.string.chats_unread_poll_vote), container, content)
        }
        if (chat.unreadReactionCount > 0) {
            IconBadge(Res.drawable.favorite_filled_24dp, stringResource(Res.string.chats_unread_reaction), container, content)
        }
        if (chat.unreadMentionCount > 0) {
            IconBadge(Res.drawable.alternate_email_24dp, stringResource(Res.string.chats_unread_mention), container, content)
        }
        if (awaitingReply) {
            IconBadge(Res.drawable.reply_24dp, stringResource(Res.string.chats_awaiting_reply), container, content)
        }
        if (chat.unreadCount > 0) {
            // `Badge` je ve výchozím stavu chybově červený. Nepřečtená zpráva ale není
            // chyba — v odlehčeném seznamu byla ta červená jediná sytá barva na obrazovce
            // a přebíjela všechno ostatní.
            Badge(
                containerColor = if (quietCount) MaterialTheme.colorScheme.outlineVariant else container,
                contentColor = if (quietCount) MaterialTheme.colorScheme.onSurfaceVariant else content,
            ) {
                RollingNumber(chat.unreadCount, style = LocalTextStyle.current.tabularNumbers())
            }
        }
    }
}

/** Kulatý odznak s ikonou, velký jako odznak s jednou číslicí. */
@Composable
private fun IconBadge(icon: DrawableResource, description: String, container: Color, content: Color) {
    Badge(
        containerColor = container,
        contentColor = content,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(12.dp))
    }
}

/**
 * Palety barev, které mají některé chaty — jedna na barvu, ne na řádek: vygenerovat paletu stojí
 * desítky výpočtů v HCT a řádky se při scrollu skládají pořád znovu.
 *
 * Barva chatu je v seznamu JEN v odznaku nepřečtených. Celý řádek v barvě by ze seznamu udělal
 * pestrou mozaiku a barva by přestala cokoli znamenat; odznak je sytá tečka, kterou oko hledá
 * stejně, a nepřečtený chat je právě ten, u kterého na rozlišení záleží.
 */
@Composable
internal fun rememberChatAccents(used: Set<ThemeColor>): Map<ThemeColor, ColorScheme> {
    val isDark = LocalIsDarkTheme.current
    val accents = HashMap<ThemeColor, ColorScheme>()
    ThemeColor.entries.forEach { color ->
        key(color) {
            if (color in used) accents[color] = chatColorScheme(isDark, color.seed)
        }
    }
    return accents
}

/** Avatar. Větší než výchozích 48 dp — v odlehčeném řádku nese hlavní váhu on, ne písmo. */
internal val CHAT_ROW_AVATAR_SIZE = 52.dp

/** Jak daleko za okraj avataru sahá kroužek příběhů (mezera a čára, `storyRing`). */
private val STORY_RING_OUTSET = 5.dp
