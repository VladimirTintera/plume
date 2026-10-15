package eu.tintera.plume.features.conversation.items

import eu.tintera.plume.core.telegram.PendingChecklistMark

import eu.tintera.plume.core.ai.ScamKind
import eu.tintera.plume.features.conversation.ContextFinding
import eu.tintera.plume.core.telegram.SpeechTranscript
import eu.tintera.plume.core.ui.contextClickable
import eu.tintera.plume.core.ui.ExpressiveMenu
import eu.tintera.plume.core.ui.MenuAction
import eu.tintera.plume.features.conversation.NoticeMenu
import eu.tintera.plume.resources.Res
import eu.tintera.plume.resources.delete_24dp
import eu.tintera.plume.resources.menu_delete
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.material3.LoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import eu.tintera.plume.core.audio.VoiceKey
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.audio.VoiceSpeed
import eu.tintera.plume.core.telegram.TranscribeOffer
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.SponsoredMessage
import eu.tintera.plume.core.ui.localDate
import eu.tintera.plume.features.conversation.SponsoredCard

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
 * Položka seznamu konverzace. Seznam volá tohle, ne [ConversationItem.Content] přímo.
 *
 * Nová zpráva posune indexy všech položek a `LazyColumn` kvůli tomu znovu spustí obsah každé
 * viditelné. [ConversationItem.Content] je člen rozhraní, tedy otevřená funkce, a ty Compose
 * přeskakovat neumí — skládal se proto obal každé zprávy (`animateItem`, odkrývání časů, nový
 * `graphicsLayer`), i když se nezměnila. Tahle funkce přeskočitelná je: blok se porovná přes
 * `equals` ([ConversationItem] je `@Immutable`) a nezměněná položka se nesloží vůbec.
 */
@Composable
internal fun LazyItemScope.ConversationItemContent(item: ConversationItem, scope: ConversationItemScope) {
    with(item) { Content(scope) }
}

/**
 * Co blok potřebuje od obrazovky: cestu pro akce a hodnoty, které se mění rychleji, než by se
 * vyplatilo přestavovat seznam bloků.
 *
 * Hodnoty jsou funkce, ne vlastnosti: čtou se až v tom kusu UI, který je kreslí, takže změna
 * pozice hlasovky překreslí jen ji, ne celou položku.
 */
@Stable
internal interface ConversationItemScope {
    /** Posun odkrývání časů tahem doleva; čte se až při kreslení vrstvy. */
    val timeReveal: () -> Float

    /** Co hraje ze zprávy; [part] je pořadí zvuku v článku (`VoiceKey.NO_PART` = příloha zprávy). */
    fun voicePlayback(messageId: Long, part: Int = VoiceKey.NO_PART): VoicePlayback?

    /** Hraje video kolečko téhle zprávy se zvukem? Nejvýš jedno v konverzaci. */
    fun videoNoteSounding(messageId: Long): Boolean = false

    /** Přehrávač kolečka téhle zprávy, když hraje se zvukem; drží ho obrazovka, ne položka. */
    fun soundingVideoNote(messageId: Long): SoundingVideoNote? = null

    /** Zvolená rychlost přehrávání hlasovek; jedna pro celou aplikaci. */
    fun voiceSpeed(): VoiceSpeed

    /** Jestli a jak nabídnout přepis záznamu té délky (Premium a zkušební přepisy účtu). */
    fun transcribeOffer(durationSeconds: Int): TranscribeOffer = TranscribeOffer.Hidden

    /** Přepis záznamu z modelu v telefonu (`LocalTranscripts`); server ho nese ve zprávě. */
    fun localTranscript(messageId: Long): SpeechTranscript? = null

    /**
     * Varování před podvodem u zprávy (`ScamWarnings`), nebo `null`. Skládací, protože tok čte
     * až položka — `ConversationScreen` je v dexu na hraně a další `collectAsState` mu nepřidáme.
     */
    @Composable
    fun scamWarning(messageId: Long): ScamKind? = null

    /** Uživatel varování před podvodem skryl. */
    fun dismissScamWarning(messageId: Long) {}

    /**
     * Co se dá s textem zprávy udělat (termín, účet, zásilka — `findMessageContext`), nebo prázdný
     * seznam. Skládací ze stejného důvodu jako [scamWarning]: posudky modelu čte až položka.
     */
    @Composable
    fun messageContext(message: ChatMessage): List<ContextFinding> = emptyList()

    /** Ťuknutí na ikonku akcí u zprávy. */
    fun openMessageContext(messageId: Long, findings: List<ContextFinding>) {}

    fun documentProgress(fileId: Int): Float?

    /** Dočítá se useknutý článek téhle zprávy („Číst dál")? */
    fun articleLoading(messageId: Long): Boolean = false

    /**
     * Horní hrana seznamu v souřadnicích okna — spodek hlavičky chatu. Nabídka nad zprávou nad ni
     * nesmí, aby hlavička zůstala vidět. `null`, dokud není změřená.
     */
    fun contentTop(): Float?

    /**
     * Zpráva hlásí při dlouhém stisku, jak zjistit, kde v okně je — nabídka z toho místa vyjede.
     * Funkce, ne hodnota: po otevření nabídky se schová klávesnice, seznam se posune a nabídka
     * musí sledovat, kde zpráva je TEĎ.
     */
    fun onMenuAnchor(anchor: MenuAnchor)

    /**
     * Je zpráva s tímhle klíčem právě v nabídce (otevřené, nebo se z ní vrací)? Pak ji kreslí
     * nabídka a v seznamu po ní zůstane prázdné místo. Čte se až při kreslení.
     */
    fun isInMenu(key: Long): Boolean

    /**
     * Má zpráva s tímhle klíčem pružně naskočit? Jen jednou a jen když přišla (podle [timestamp])
     * až po otevření konverzace — ne při každém složení položky při scrollu.
     */
    fun claimArrival(key: Long, timestamp: Int): Boolean

    /**
     * Má hod kostkou s tímhle klíčem proběhnout před očima? Jen jednou a jen u hodu, který přišel
     * (podle [timestamp]) až po otevření konverzace; z historie je vidět rovnou výsledek.
     */
    fun claimDiceThrow(key: Long, timestamp: Int): Boolean

    /**
     * Má efekt zprávy s tímhle klíčem proběhnout sám? Jen jednou, a to u zprávy, která přišla
     * až po otevření konverzace, nebo u NEPŘEČTENÉ příchozí ([effectAutoplays]). Z přečtené
     * historie se přehraje ťuknutím na značku.
     */
    fun claimEffect(key: Long, message: ChatMessage, unreadBoundary: Long?): Boolean = false

    /**
     * Hranice nepřečtených zachycená při otevření chatu (`unreadBoundaryId`); dorazí až po
     * prvních zprávách. **Nečíst v kompozici**, jen v efektu (`snapshotFlow`): čtená
     * v kompozici invalidovala položku s každou změnou stavu konverzace, i když se hranice
     * nezměnila a obrazovka ji podává přes `derivedStateOf`.
     */
    fun unreadBoundary(): Long? = null

    /**
     * Efekty animovaných emoji, které vidí obě strany (ťuknutí, efekt od protějšku); `null` =
     * emoji na ťuknutí nereaguje — kopie v nabídce nad zprávou, kde se nic neposílá.
     */
    fun emojiBursts(): EmojiBursts? = null

    /** Jediná cesta akcí ze zprávy ven (viz [MessageAction]). */
    fun onAction(messageId: Long, action: MessageAction)

    /** Sponzorovaná zpráva není zpráva chatu, takže nemá ani [MessageAction]. */
    fun openSponsored(sponsored: SponsoredMessage)

    /**
     * Co se hledá v chatu (dotaz, ke kterému patří výsledky); `null` mimo hledání. Bubliny ho
     * v textu zvýrazní. Čte se v kompozici textu, takže ho obrazovka podává přes `derivedStateOf`
     * — jinak by každá změna stavu konverzace překreslila text všech bublin.
     */
    fun searchHighlight(): String? = null

    /**
     * Nese tvář odesílatele u téhle zprávy sdílený prvek do profilu? Jen ta ŤUKNUTÁ: tentýž
     * člověk má ve skupině tvář u každého bloku zpráv a o jeden klíč by se přetahovaly všechny.
     */
    fun isProfileFlight(messageId: Long): Boolean = false

    /**
     * Jde ťuknutím na `/příkaz` v textu poslat příkaz? Jen v chatu, do kterého se smí psát —
     * jinde zůstane příkaz obyčejným textem. Čte se v kompozici textu.
     */
    fun canSendCommands(): Boolean = false

    /** Přidá dvojité ťuknutí na zprávu rychlou reakci (Nastavení → Chaty)? */
    fun doubleTapReaction(): Boolean = false

    /** Jde na zprávu odpovědět — smí se do chatu psát? Tlačítko Odpovědět po najetí myší jen tehdy. */
    fun canReply(): Boolean = false

    /**
     * Vybírá se text zprávy přímo v bublině myší (`bubbleTextSelectable`, desktop)? Ne v kopii
     * v nabídce — ta má výběr vlastní — a ne v chatu s chráněným obsahem, odkud se nesmí
     * kopírovat.
     */
    fun selectableText(): Boolean = false

    /** Tlačítko bota u zprávy, na jehož odpověď se čeká (řádek, sloupec); `null` = žádné. */
    fun pressingBotButton(messageId: Long): Pair<Int, Int>? = null

    /** Odškrtnutí úkolů v seznamu zprávy, která čekají na server (viz `PendingChecklistMark`). */
    fun pendingChecklistMarks(messageId: Long): List<PendingChecklistMark> = emptyList()

    /** Svislý posun položky při příjezdu po přeskoku (viz `JumpTransition`); čte se až při kreslení. */
    fun gatherShift(key: Any): Float = 0f

    /** Průhlednost položky při příjezdu po přeskoku; čte se až při kreslení. */
    fun gatherAlpha(key: Any): Float = 1f
}

/**
 * Položka při příjezdu po přeskoku sjíždí ke zprávě, na kterou se skočilo (viz `JumpTransition`).
 * Patří na KOŘEN každého bloku, za `animateItem`; čte se až ve vrstvě, takže nic nerekomponuje.
 */
internal fun Modifier.gathered(key: Any, scope: ConversationItemScope): Modifier = graphicsLayer {
    translationY = scope.gatherShift(key)
    alpha = scope.gatherAlpha(key)
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
        DayDivider(timestamp, Modifier.animateItem().gathered(key, scope))
    }

    companion object {
        fun keyFor(timestamp: Int): String = "day-${timestamp.localDate()}"
    }
}

/**
 * Časový předěl uvnitř dne — nad zprávou po pauze aspoň [TIME_DIVIDER_GAP_SECONDS].
 * Patří zprávě pod sebou, proto má klíč odvozený od jejího klíče.
 */
internal data class TimeDividerItem(val timestamp: Int, override val key: String) : ConversationItem {
    @Composable
    override fun LazyItemScope.Content(scope: ConversationItemScope) {
        TimeDivider(timestamp, Modifier.animateItem().gathered(key, scope))
    }
}

/**
 * Jak dlouhá pauza mezi dvěma zprávami téhož dne vyžaduje časový předěl. Hodina: souvislý
 * rozhovor zůstane bez přerušení, nová dávka zpráv (ráno, odpoledne, večer) dostane svůj čas.
 */
internal const val TIME_DIVIDER_GAP_SECONDS = 60 * 60

/** Předěl „Nepřečtené zprávy". V seznamu je nejvýš jeden. */
internal data object UnreadDividerItem : ConversationItem {
    override val key: Any = "unread"

    @Composable
    override fun LazyItemScope.Content(scope: ConversationItemScope) {
        UnreadDivider(Modifier.animateItem().gathered(key, scope))
    }
}

/**
 * Systémové oznámení o dění v chatu. Nemá odesílatele, takže ani stranu, bublinu, reakce
 * ani stav doručení.
 *
 * Dlouhý stisk nabídne smazání ([menu], jen když ho server dovolí). Plná nabídka nad zprávou
 * se sem nehodí: kreslí kopii bubliny a nabízí reakce a odpověď, které oznámení nemá.
 */
internal data class ServiceMessageItem(
    override val key: Long,
    val message: ChatMessage,
    val menu: NoticeMenu? = null,
) : ConversationItem {
    override fun containsMessage(messageId: Long): Boolean = message.id == messageId

    @Composable
    override fun LazyItemScope.Content(scope: ConversationItemScope) {
        RevealingTime(message.timestamp, scope.timeReveal, Modifier.animateItem().gathered(key, scope)) {
            // Dárek je karta, ne věta: řekne, co se darovalo a od koho.
            val gift = message.media as? MessageMedia.Gift
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        // Jen na šířku textu: nabídka se ukotví k oznámení, ne k levému okraji
                        // seznamu, a dlouhý stisk vedle textu nic nedělá. Dárek šířku má sám
                        // (a intrinsické míry by v kartě nemusely projít).
                        .then(if (gift == null) Modifier.width(IntrinsicSize.Max) else Modifier)
                        .contextClickable(
                            interactionSource = null,
                            indication = null,
                            onClick = {},
                            onLongClick = { scope.onAction(message.id, MessageAction.ShowNoticeMenu) },
                        ),
                ) {
                    if (gift != null) {
                        Box(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
                            GiftCard(gift, onAction = { scope.onAction(message.id, it) })
                        }
                    } else {
                        ServiceMessage(message, onOpenChat = { scope.onAction(message.id, MessageAction.OpenChat(it)) })
                    }
                    ExpressiveMenu(
                        expanded = menu != null,
                        onDismiss = { scope.onAction(message.id, MessageAction.CloseMenu) },
                        groups = listOf(
                            listOf(
                                MenuAction(
                                    label = stringResource(Res.string.menu_delete),
                                    icon = Res.drawable.delete_24dp,
                                    destructive = true,
                                ) {
                                    val rights = menu ?: return@MenuAction
                                    scope.onAction(
                                        message.id,
                                        MessageAction.DeleteNotice(rights.canDeleteForSelf, rights.canDeleteForAll),
                                    )
                                },
                            ),
                        ),
                    )
                }
            }
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
                .gathered(key, scope)
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
        Box(Modifier.gathered(key, scope).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            LoadingIndicator()
        }
    }
}

internal const val SPONSORED_KEY_PREFIX = "sponsored-"

/**
 * Odkud nabídka nad zprávou vyjede a jak zprávu v kopii složit.
 *
 * - [bounds]: kde zpráva v okně je. Funkce, ne hodnota — nabídka ji čte každý snímek (viz
 *   [ConversationItemScope.onMenuAnchor]).
 * - [maxWidth]: nejvyšší šířka (px), kterou zpráva dostala v seznamu. Kopie v nabídce se musí
 *   složit se STEJNÝM omezením: obsah, který se řídí dostupnou šířkou (fotka s popiskem má
 *   podlahu 75 %), by jinak vyšel jinak — v nabídce užší a po návratu by skokem dorostl.
 */
class MenuAnchor(val bounds: () -> Rect?, val maxWidth: () -> Int?)
