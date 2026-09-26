package eu.tintera.plume.features.conversation.items

import androidx.compose.foundation.layout.Box
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
import eu.tintera.plume.core.audio.VoicePlayback
import eu.tintera.plume.core.audio.VoiceSpeed
import eu.tintera.plume.core.telegram.ChatMessage
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

    fun voicePlayback(messageId: Long): VoicePlayback?

    /** Zvolená rychlost přehrávání hlasovek; jedna pro celou aplikaci. */
    fun voiceSpeed(): VoiceSpeed

    fun documentProgress(fileId: Int): Float?

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

    /** Tlačítko bota u zprávy, na jehož odpověď se čeká (řádek, sloupec); `null` = žádné. */
    fun pressingBotButton(messageId: Long): Pair<Int, Int>? = null

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
 */
internal data class ServiceMessageItem(
    override val key: Long,
    val message: ChatMessage,
) : ConversationItem {
    override fun containsMessage(messageId: Long): Boolean = message.id == messageId

    @Composable
    override fun LazyItemScope.Content(scope: ConversationItemScope) {
        RevealingTime(message.timestamp, scope.timeReveal, Modifier.animateItem().gathered(key, scope)) {
            ServiceMessage(message)
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
