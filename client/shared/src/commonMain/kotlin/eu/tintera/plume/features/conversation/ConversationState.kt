package eu.tintera.plume.features.conversation

import eu.tintera.plume.core.telegram.SpeechTranscript
import eu.tintera.plume.core.currency.RateTable
import eu.tintera.plume.core.bookmarks.MessageBookmarks
import eu.tintera.plume.core.telegram.ChatSummary
import eu.tintera.plume.core.telegram.ReplyQuote
import eu.tintera.plume.core.telegram.FormatSpan
import eu.tintera.plume.core.telegram.BotCommandItem
import eu.tintera.plume.core.telegram.BotChatRequest
import eu.tintera.plume.core.telegram.BotMenuApp
import eu.tintera.plume.core.telegram.BotUsersRequest
import eu.tintera.plume.core.telegram.UnreadMarks
import eu.tintera.plume.core.telegram.ChatReplyMarkup
import eu.tintera.plume.core.telegram.PollComposerMode
import eu.tintera.plume.core.telegram.SwitchInlineTarget
import eu.tintera.plume.core.telegram.LoginUrlPrompt
import eu.tintera.plume.core.telegram.SenderInfo
import eu.tintera.plume.core.telegram.GiveawayStatus
import eu.tintera.plume.core.telegram.FolderInvitePreview
import eu.tintera.plume.core.telegram.InvitePreview
import eu.tintera.plume.core.telegram.AppScreen
import eu.tintera.plume.core.telegram.InternalLink
import eu.tintera.plume.core.telegram.LoginLinkKind
import eu.tintera.plume.core.telegram.MessageBody
import eu.tintera.plume.core.telegram.MentionCandidate
import androidx.lifecycle.ViewModel
import eu.tintera.plume.core.settings.AppSettings
import eu.tintera.plume.core.telegram.ChatActivity
import eu.tintera.plume.core.telegram.ChatInfo
import eu.tintera.plume.core.telegram.ContentAccess
import eu.tintera.plume.core.navigation.BotStartRequests
import eu.tintera.plume.core.telegram.BotIntro
import eu.tintera.plume.core.telegram.SpeechTrial
import eu.tintera.plume.core.telegram.WriteAccess
import eu.tintera.plume.core.telegram.ChatMessage
import eu.tintera.plume.core.telegram.LinkPreview
import eu.tintera.plume.core.telegram.LinkPreviewOptions
import eu.tintera.plume.core.telegram.SpamActions
import eu.tintera.plume.core.telegram.MessageActions
import eu.tintera.plume.core.telegram.MessageInfo
import eu.tintera.plume.core.telegram.MessageMedia
import eu.tintera.plume.core.telegram.MessageReaction
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.Presence
import eu.tintera.plume.core.telegram.ReactionDetail
import eu.tintera.plume.core.telegram.SendState
import eu.tintera.plume.core.telegram.SponsoredMessage
import eu.tintera.plume.core.ui.UiText
import eu.tintera.plume.features.conversation.items.MessageAction
import eu.tintera.plume.features.conversation.items.MessageMenuAction
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import eu.tintera.plume.core.telegram.InlineQueryState

/**
 * Text na cestě ze vstupního pole do bubliny.
 *
 * Existuje jen po dobu animace. [itemKey] je `null`, dokud TDLib nevrátí rozepsanou zprávu —
 * do té doby se text kreslí u vstupního pole, pak ho převezme bublina.
 *
 * Míří na **klíč položky**, ne na id zprávy. Potvrzení ze serveru často dorazí dřív, než let
 * doběhne, a zprávě změní id; klíč zůstává (viz [ConversationState.itemKeys]).
 *
 * [id] rozlišuje jednotlivé lety: bez něj by dvě rychle po sobě odeslané zprávy sdílely jeden
 * klíč a animace by se popraly.
 */
data class MessageFlight(
    val id: Long,
    val text: String,
    val itemKey: Long?,
) {
    /** Klíč sdíleného prvku. Musí být na obou stranách stejný, jinak se prvky nespárují. */
    val sharedKey: String get() = "message-flight-$id"
}

/**
 * Naváže čekající let na právě příchozí zprávu, pokud je to ta odesílaná.
 *
 * Pozná se podle textu a stavu `Sending` — id zatím neznáme, `send` ho ještě nevrátil. Zpráva
 * odeslaná z jiného zařízení v tomhle stavu nepřijde, takže se nespáruje omylem.
 */
internal fun MessageFlight?.landingOn(message: ChatMessage): MessageFlight? = when {
    this == null || itemKey != null -> this
    message.isOutgoing && message.sendState == SendState.Sending && message.text == text ->
        copy(itemKey = message.id)
    else -> this
}

/** Členové napovídaní ke zmínce, a ke kterému dotazu patří. */
data class MentionSuggestions(val query: String, val candidates: List<MentionCandidate>)

/**
 * Text, který má obrazovka vložit do vstupního pole.
 *
 * [onlyIfEmpty] u konceptu z TDLib: než dorazí, mohl uživatel začít psát a přepsat mu to by bylo
 * horší než o koncept přijít. Úprava zprávy a návrat z ní pole naopak přepisují vždycky.
 */
data class InputFill(
    val text: String,
    val onlyIfEmpty: Boolean,
    /** Ruční formátování [text] (koncept, upravovaná zpráva). */
    val formats: List<FormatSpan> = emptyList(),
)

/** Kam na obrazovce má zpráva po skoku dosednout. */
enum class ScrollAnchor {
    /** K horní hraně — čte se od ní dolů. Skok na první nepřečtenou. */
    Top,

    /** Na střed — kolem je vidět, na co zpráva navazuje. Skok na citaci. */
    Center,

    /** Úplně dolů (index 0). Návrat z okna v historii k nejnovějším zprávám. */
    Bottom,
}

/** Požadavek na odrolování ke zprávě. Obrazovka ho po provedení vynuluje. */
data class ScrollTarget(
    val messageId: Long,
    val anchor: ScrollAnchor,
    /** Dojet k ní, ne skočit — jen když zpráva v seznamu už byla (viz `jumpToMessage`). */
    val animate: Boolean = false,
    /**
     * Je starší než to, co bylo v seznamu? Vyplněné jen u zprávy, která načtená nebyla — u načtené
     * to obrazovka pozná sama z pozice v seznamu.
     */
    val older: Boolean? = null,
)

/** Otevřený výběr člena: dotaz a nalezení členové. */
data class MemberPicker(val query: String = "", val members: List<SenderInfo> = emptyList(), val loading: Boolean = true)

/** Chat k otevření nad konverzací; s [messageId] se v něm skočí na zprávu. */
data class ChatTarget(
    val chatId: Long,
    val messageId: Long? = null,
    /** Vlákno komentářů v [chatId] (kořenová zpráva); `null` = celý chat. */
    val threadId: Long? = null,
    /** Téma skupiny s tématy; `null` = celý chat. */
    val topicId: Int? = null,
    /**
     * Skupina s tématy bez určeného tématu ani zprávy: otevře se seznam témat, ne konverzace —
     * stejně jako ze seznamu chatů. Zjišťuje ViewModel ([ConversationViewModel.openTarget]).
     */
    val forum: Boolean = false,
)

/** Video k otevření v prohlížeči od [seconds] (čas v médiu). */
data class MediaAt(val messageId: Long, val seconds: Int)

/** Otevřený náhled pozvánky; [joining] = přidávání běží a tlačítko je neaktivní. */
data class InviteSheetState(val link: String, val preview: InvitePreview, val joining: Boolean = false)

/**
 * Náhled sdílené složky. Vybrané jsou zpočátku všechny chaty, ke kterým se dá připojit — tak to
 * dělají oficiální klienti; kdo nechce do některého, odškrtne ho.
 */
data class FolderInviteSheetState(
    val link: String,
    val preview: FolderInvitePreview,
    val selected: Set<Long> = preview.missingChats.mapTo(LinkedHashSet()) { it.id },
    val adding: Boolean = false,
) {
    /**
     * Novou složku jde přidat i bez připojení k chatům, když v některých už uživatel je (složka
     * nebude prázdná). Do složky, kterou už má, se přidává jen to, co vybral.
     */
    val canAdd: Boolean
        get() = selected.isNotEmpty() || (preview.folderId == 0 && preview.alreadyJoined > 0)
}

/** Skok na zprávu, která se teprve načítá; obrazovka mezitím odjíždí směrem k ní. */
data class PendingJump(val messageId: Long, val older: Boolean)

/**
 * Otevřené informace o zprávě.
 *
 * [info] je `null`, dokud se načítají — sheet se otevře hned s indikátorem, místo aby vyskočil
 * prázdný a obsah do něj naskákal. Kus dat je v [message], protože ho konverzace už má: text,
 * odesílatel i stav doručení se kvůli sheetu nemusí ptát serveru znovu.
 */
data class MessageInfoSheet(
    val message: ChatMessage,
    val info: MessageInfo? = null,
)

/** Připomínka zprávy [messageId]; [suggestedAt] = termín ze zprávy (sekundy od epochy), `null` = žádný. */
/** Co rozhoduje o přepisu záznamu — viz [ConversationViewModel.transcribeContext]. */
data class TranscribeContext(
    val isPremium: Boolean = false,
    val trial: SpeechTrial? = null,
    /** Umí přepis model v telefonu (zapnutá AI a připravený model se zvukem). */
    val onDevice: Boolean = false,
    /** Přepisy z telefonu v tomhle chatu podle id zprávy. */
    val local: Map<Long, SpeechTranscript> = emptyMap(),
)

/**
 * Otevřený sheet akcí u zprávy [messageId]; [findings] jsou spočítané v okamžiku ťuknutí.
 * [rates] jen když je mezi nimi cizí měna — kurz se stahuje až s otevřením sheetu.
 */
data class ContextSheet(
    val messageId: Long,
    val findings: List<ContextFinding>,
    val homeCurrency: String? = null,
    val rates: RatesState? = null,
)

/** Kurzovní lístek pro převod měny v sheetu akcí. */
sealed interface RatesState {
    data object Loading : RatesState

    data class Loaded(val table: RateTable) : RatesState

    data object Failed : RatesState
}

/**
 * Nabízí zpráva akce ze svého textu? Ne systémová oznámení, mizející zprávy (ty nesmí ven, stejně
 * jako chráněný obsah), zprávy omezené serverem a zprávy služby Telegramu.
 */
internal fun ChatMessage.offersContext(): Boolean =
    !isService && !isRestricted && selfDestructType == null && text.isNotBlank() && senderId != TELEGRAM_SERVICE_ID

data class RemindRequest(
    val messageId: Long,
    val suggestedAt: Long?,
    /** Termín hledá model v zařízení (pravidla nic nenašla); sheet ukáže „Hledám termín…". */
    val suggesting: Boolean = false,
    /** [suggestedAt] je odhad modelu, ne pravidel — sheet to řekne ikonou. */
    val fromModel: Boolean = false,
)

/**
 * Otevřený překlad zprávy do jazyka [languageCode]. [body] je `null`, dokud překlad nedorazí;
 * [failed] = server ho nevydal.
 */
data class TranslationSheet(
    val message: ChatMessage,
    val languageCode: String,
    val body: MessageBody? = null,
    /** Proč se to nepovedlo; `null`, dokud se čeká nebo když se to povedlo. */
    val error: UiText? = null,
    /** Shrnutí od serveru místo překladu — tentýž sheet, jiné volání a jiné texty. */
    val summary: Boolean = false,
    /**
     * Jestli smí výsledek do schránky. Překlad se u chráněného obsahu nenabízí vůbec, shrnutí
     * ano (dostupnost řídí server) — jen bez kopírování, chráněný obsah ven nesmí ani převyprávěný.
     */
    val canCopy: Boolean = true,
    /** Překlad v telefonu, ne na serveru; `null` = telefon to neumí (přepínač se nekreslí). */
    val onDevice: Boolean? = null,
    /** Pro překlad v telefonu chybí jazykové balíčky; čeká se, až stažení uživatel potvrdí. */
    val needsDownload: Boolean = false,
    /** Stahují se jazykové balíčky (trvá to déle než překlad, sheet to říká). */
    val downloading: Boolean = false,
)

/**
 * Otevřený detail reakcí: kdo a čím reagoval.
 *
 * Načítá se až při otevření — čip u zprávy nese jen emoji a počet, takže jména a profilovky
 * nikdo jiný nepotřebuje. [loading] drží sheet otevřený s indikátorem, místo aby vyskočil
 * prázdný a obsah do něj naskákal.
 */
data class ReactionDetails(
    val messageId: Long,
    val loading: Boolean = true,
    val items: List<ReactionDetail> = emptyList(),
    /** Reakce zprávy s počty, jak byly při otevření — podle nich jsou tlačítka filtru. */
    val counts: List<MessageReaction> = emptyList(),
    /** Vybraný filtr (klíč reakce); `null` = všechny. */
    val filter: String? = null,
)

/**
 * Otevřená nabídka nad zprávou.
 *
 * Jsou v ní dvě různé věci: **rychlé reakce** (pár emoji na jedno ťuknutí) a **akce** nad zprávou.
 * Proto se kreslí jako dva samostatné kusy, ne jako jeden seznam — reagovat a odpovědět nejsou
 * varianty téhož.
 *
 * Nese jen data, **žádné souřadnice**. Kde přesně bublina na obrazovce leží, ví layout, ne
 * ViewModel; ten by se tím navázal na rozvržení, které se mění s velikostí okna.
 */
data class MessageMenu(
    val messageId: Long,
    /**
     * Kdy zpráva přišla. Čas se jinak dá přečíst jen tahem doleva a to je gesto, ke kterému se
     * čtečka obrazovky nedostane — v nabídce proto stojí jako první řádek.
     */
    val timestamp: Int,
    /** Podle strany bubliny se nabídka zarovná, aby vyrůstala od zprávy, ne od kraje obrazovky. */
    val isOutgoing: Boolean,
    /** Nabídnutá emoji. Prázdné je běžný stav (Uložené zprávy), ne chyba. */
    val emoji: List<String>,
    /** Emoji, která uživatel u téhle zprávy už dal — v rychlém řádku se zvýrazní. */
    val chosenEmoji: Set<String>,
    /** Co server dovolí; načítá se až při otevření nabídky. */
    val actions: MessageActions,
    /**
     * Kandidáti do nabídky od bloku — ten ví, co zpráva JE (holý text, samolepka, připnutá…).
     * Nabídka z nich ukáže ty, které server dovolí ([MessageMenuAction.presentation]).
     */
    val items: List<MessageMenuAction>,
    /**
     * Je samolepka zprávy v oblíbených? `null`, když zpráva samolepka není — pak se položka
     * nenabízí vůbec.
     */
    val favoriteSticker: Boolean?,
    /** Je zpráva v záložkách (`MessageBookmarks`)? `null` = záložky nejsou k dispozici, položka se nenabízí. */
    val bookmarked: Boolean? = null,
    /**
     * Zpráva pod prstem, když se liší od [messageId]: fotka uprostřed alba. Blok alba patří zprávě
     * s popiskem (nebo nejstarší), takže bez tohohle odpověď citovala tu, ne fotku, na kterou
     * uživatel podržel. Ostatní položky (reakce, přeposlání, smazání) jdou dál na celý blok.
     */
    val pressedMessageId: Long? = null,
    /**
     * Text zprávy je už v jazyce překladu (rozpoznáno v zařízení, [alreadyInLanguage]) —
     * „Přeložit" se nenabízí. Nerozpoznaný jazyk je `false`: položka navíc nevadí, chybějící ano.
     */
    val inTranslationLanguage: Boolean = false,
    /** Umí telefon vysvětlit zprávu modelem (zapnutá AI, model zařízení podporuje)? */
    val explainAvailable: Boolean = false,
    /** Co se v textu zprávy našlo pro akce (termín, účet, zásilka); prázdné = položka není. */
    val contextFindings: List<ContextFinding> = emptyList(),
    /** Zpráva (u alba všechny jeho neodeslané) neodešla a jde poslat znovu — „Poslat znovu". */
    val resendable: Boolean = false,
) {
    /** Do rychlého řádku se vejde jen pár; zbytek je za „+". */
    val quickEmoji: List<String> get() = emoji.take(MAX_QUICK_REACTIONS)

    /** „+" má smysl jen tehdy, když za ním něco navíc je. */
    val hasMoreEmoji: Boolean get() = emoji.size > MAX_QUICK_REACTIONS
}

/**
 * Zpráva čekající na potvrzení smazání.
 *
 * Drží se **zvlášť od [MessageMenu]**: mezi zavřením nabídky a potvrzením nesmí zůstat viset
 * obojí. Stejný důvod jako u `target`/`pendingDelete` v akcích nad chatem.
 */
/** Nabídka nad systémovým oznámením; otevírá se jen s právem ho smazat. */
data class NoticeMenu(
    val messageId: Long,
    val canDeleteForSelf: Boolean,
    val canDeleteForAll: Boolean,
)

data class PendingDelete(
    val messageId: Long,
    /** Co se opravdu smaže: zpráva sama, u alba všechny jeho načtené zprávy. */
    val messageIds: List<Long> = listOf(messageId),
    val canDeleteForSelf: Boolean,
    val canDeleteForAll: Boolean,
    /**
     * Kolik zpráv to je z pohledu uživatele — u alba jedna, z výběru tolik, kolik jich vybral.
     * Není to `messageIds.size`: album je v bublině jedna věc, i když je to pět zpráv.
     */
    val count: Int = 1,
)

/** Na co míří „Odpovědět" z nabídky: na fotku pod prstem, jinak na zprávu bloku. */
internal val MessageMenu.replyTarget: Long get() = pressedMessageId ?: messageId

/**
 * Režim výběru víc zpráv; `null` = vypnutý.
 *
 * Vybírají se **bloky seznamu** ([keys], u alba klíč celého alba), ne jednotlivé zprávy —
 * v konverzaci je album jedna věc a mazat nebo přeposílat půlku alba nedává smysl. Na id zpráv
 * se klíče rozbalí až při akci (`albumMemberIds`), stejně jako u nabídky nad jednou zprávou.
 *
 * Práva od serveru se načítají pro každý vybraný blok zvlášť ([rights]) — z nich se teprve
 * poskládá, co smí výběr JAKO CELEK. Dokud nedorazí všechna, akce se nenabízejí: tlačítko,
 * které se po ťuknutí zeptá serveru a pak zhasne, je horší než tlačítko, které chvíli není.
 */
data class MessageSelection(
    val keys: Set<Long> = emptySet(),
    /** Práva vybraného bloku; klíč tu chybí, dokud odpověď nedorazí. */
    val rights: Map<Long, MessageActions> = emptyMap(),
    /** Vybrané bloky, ze kterých je co zkopírovat. Text zná konverzace, ne server. */
    val withText: Set<Long> = emptySet(),
) {
    val count: Int get() = keys.size

    /** Práva ještě nedorazila u všech vybraných. */
    val loading: Boolean get() = keys.any { it !in rights }

    private val ready: Boolean get() = keys.isNotEmpty() && !loading

    /**
     * Akce platí pro CELÝ výběr, takže se práva skládají přes `all`, ne `any`. Přeposlat půlku
     * vybraného a o druhé mlčet by znamenalo, že uživatel neví, co odešlo.
     */
    val canForward: Boolean get() = ready && rights.values.all { it.canForward }

    val canCopy: Boolean get() = ready && withText.isNotEmpty() && rights.values.all { it.canCopy }

    val canDelete: Boolean get() = ready && rights.values.all { it.canDeleteForSelf || it.canDeleteForAll }

    /** Nahlásit jde jen výběr, u kterého server nahlášení dovolí VŠEM zprávám (`canReportChat`). */
    val canReport: Boolean get() = ready && rights.values.all { it.canReport }

    /** Smazat i ostatním jde, jen když to jde u všech vybraných. */
    val canDeleteForAll: Boolean get() = ready && rights.values.all { it.canDeleteForAll }

    val canDeleteForSelf: Boolean get() = ready && rights.values.all { it.canDeleteForSelf }
}

/** Zpráva čekající na potvrzení připnutí. Zvlášť od [MessageMenu] ze stejného důvodu jako [PendingDelete]. */
data class PendingPin(
    val messageId: Long,
    val scope: PinScope,
)

/** Komu se připnutí týká — podle toho dialog nabízí jinou volbu. */
enum class PinScope {
    /** Volba: připnout i protějšku. */
    Private,

    /** Volba: upozornit členy. */
    Group,

    /** Volba: upozornit odběratele. */
    Channel,
}

/**
 * Činnost v chatu pro konverzaci. [sender] je `null` v soukromém chatu (tvář nese [ChatInfo])
 * a tam, kde se odesílatele nepodařilo zjistit.
 */
data class Typing(val activity: ChatActivity, val sender: SenderInfo?)

/**
 * Které zprávy z viditelných položek ([visibleKeys] jsou KLÍČE, ne id) se hlásí jako zhlédnuté.
 *
 * Cizí zprávy vždycky; album celé, protože je v seznamu jednou položkou s klíčem nejstarší
 * zprávy a ostatní fotky by jinak zůstaly v počtu nepřečtených napořád. Vlastní zprávu tehdy,
 * když nese nepřečtené reakce nebo hlasy v anketě — přečte je právě zhlédnutí —, **a když leží
 * nad hranicí nepřečtených** ([ConversationState.unreadBoundaryId]).
 *
 * To poslední je kvůli tématům: server v tématu počítá jako nepřečtené i VLASTNÍ zprávy nad
 * hranicí (servisní „přibyli členové" po přidání, zpráva poslaná z jiného zařízení). Cizí zpráva
 * nad hranicí žádná nebyla, takže se nehlásilo nic, hranice stála a odznak „2" u tématu zůstal
 * napořád, i když v něm uživatel četl a psal. Nahlášená vlastní zpráva hranici posune přes ni.
 */
/** Zprávy, jejichž zhlédnutí něco přečte — nepřečtená reakce nebo hlas v anketě. */
internal fun ConversationState.unreadMarkedIds(): Set<Long> =
    messages.filter { it.hasUnreadReactions || it.hasUnreadPollVotes }.mapTo(HashSet()) { it.id }

internal fun ConversationState.messagesToView(visibleKeys: Set<Long>): List<Long> {
    val albums = messages
        .filter { keyOf(it) in visibleKeys }
        .map { it.mediaAlbumId }
        .filter { it != 0L }
        .toSet()
    return messages
        .filter {
            (!it.isOutgoing || it.hasUnreadReactions || it.hasUnreadPollVotes || it.id > (unreadBoundaryId ?: Long.MAX_VALUE)) &&
                (keyOf(it) in visibleKeys || it.mediaAlbumId in albums)
        }
        .map { it.id }
}

/** Otevřený dialog upozornění na blízkost: u které zprávy a jaký poloměr je nastavený teď. */
data class ProximityAlertPrompt(val messageId: Long, val currentRadius: Int)

data class ConversationState(
    val info: ChatInfo? = null,
    /** Vlákno komentářů (kořenová zpráva); `null` u běžné konverzace. */
    val threadRootId: Long? = null,
    /** Téma skupiny s tématy; `null` u běžné konverzace. */
    val forumTopicId: Int? = null,
    /** Téma Uložených zpráv; `null` u běžné konverzace. */
    val savedTopicId: Long? = null,
    /**
     * Nepřečtené zmínky a reakce v chatu — tlačítka „@" a „❤" nad tlačítkem dolů. Ve vlákně
     * komentářů zůstávají nulové: počty patří celé diskusní skupině.
     */
    val unreadMarks: UnreadMarks = UnreadMarks(),
    /** Zpráva otevřená k výběru úryvku textu; `null` = sheet je zavřený. */
    val selectingText: ChatMessage? = null,
    /** Dostupnost protějšku pro podtitul hlavičky. `null` u skupin a kanálů. */
    val presence: Presence? = null,
    /**
     * Co protějšek (ve skupině kdokoli) právě dělá. Kreslí se v seznamu zpráv tam, kam jeho zpráva
     * naskočí (`TypingItem`), ne v hlavičce.
     */
    val typing: Typing? = null,
    /** Otevřená nabídka nad zprávou (rychlé reakce + akce). */
    val messageMenu: MessageMenu? = null,
    /** Otevřená nabídka nad systémovým oznámením (jen smazání). */
    val noticeMenu: NoticeMenu? = null,
    /** Reaguje dvojité ťuknutí na zprávu rychlou reakcí? Z nastavení aplikace. */
    val doubleTapReaction: Boolean = true,
    /**
     * Nepřečtené zprávy, které diskrétní čtení (`AppSettings.discreetReading`) drží
     * nenahlášené; 0, když je volba vypnutá. Podle toho se ukáže „Označit jako přečtené".
     */
    val discreetUnread: Int = 0,
    /** Zpráva čekající na potvrzení smazání. */
    val pendingDelete: PendingDelete? = null,
    /** Zpráva čekající na potvrzení připnutí. */
    val pendingPin: PendingPin? = null,
    /** Zpráva, na kterou se právě chystá odpověď. Zobrazuje se nad vstupním polem. */
    val replyingTo: ChatMessage? = null,
    /**
     * Citovaný úryvek [replyingTo], když se neodpovídá na celou zprávu (označený text
     * v nabídce nad zprávou → „Citovat"). Bez [replyingTo] nemá smysl a maže se s ním.
     */
    val replyQuote: ReplyQuote? = null,
    /**
     * Citované zprávy, které nejsou v [messages] — doplněné jednotlivě.
     * Náhled odpovědi se hledá nejdřív v [messages], teprve pak tady.
     */
    val quotes: Map<Long, ChatMessage> = emptyMap(),
    /** Protějšek přečetl odchozí zprávy až po tohle id včetně. */
    val lastReadOutboxMessageId: Long = 0L,
    /**
     * Poslední zpráva, kterou uživatel při otevření chatu měl přečtenou. Nad ní se kreslí předěl
     * „Nepřečtené zprávy". `null`, když chat nepřečtené neměl.
     */
    val unreadBoundaryId: Long? = null,
    /** Zpráva, na kterou má obrazovka odrolovat. Vynuluje se po provedení. */
    val scrollTarget: ScrollTarget? = null,
    /**
     * Text k vložení do vstupního pole — koncept z TDLib, text upravované zprávy nebo návrat
     * z úpravy. Obrazovka ho po použití vynuluje ([ConversationViewModel.consumeInputFill]).
     */
    val inputFill: InputFill? = null,
    /** Připnuté zprávy pro pruh pod hlavičkou, od nejnovější. */
    val pinned: List<ChatMessage> = emptyList(),
    /** Smí uživatel připnuté zprávy odepínat. */
    val canUnpin: Boolean = false,
    /** Otevřený detail reakcí; `null`, když je zavřený. */
    val reactionDetails: ReactionDetails? = null,
    /** Otevřené informace o zprávě; `null`, když je sheet zavřený. */
    val messageInfo: MessageInfoSheet? = null,
    /** Otevřený překlad zprávy; `null`, když je sheet zavřený. */
    val translation: TranslationSheet? = null,
    /**
     * Zpráva, u které si uživatel ťuknutím vyžádal odznak přečtení; `null`, když žádná.
     *
     * Jedno id, ne množina: ťuknutí na jinou zprávu odznak přepne tam. Odchod z chatu ho zahodí
     * i s celým stavem — je to zobrazení na chvíli, ne nastavení.
     */
    val readReceiptFor: Long? = null,
    /** Průběh stahování souborů, které uživatel právě otevírá (podle `fileId`). */
    val documentDownloads: Map<Int, Float> = emptyMap(),
    /** Zprávy, které uživatel chce přeposlat; obrazovka na to otevře výběr cílového chatu. */
    val forwardRequest: List<Long>? = null,
    /** Zpráva, ke které se vybírá čas připomínky (`RemindSheet`); `null` = nic. */
    val remindRequest: RemindRequest? = null,
    /** Režim výběru víc zpráv; `null` = vypnutý (běžná konverzace). */
    val selection: MessageSelection? = null,
    /** Přílohy vybrané k odeslání, čekající nad vstupním polem na popisek a odeslání. */
    val attachments: List<OutgoingAttachment> = emptyList(),
    /**
     * Upravené fotky nad polem podle SOUČASNÉ cesty: z čeho vznikly a jakými tahy. Další úprava
     * začíná z originálu s těmito tahy (viz [ConversationViewModel.applyPhotoMarkup]).
     */
    val photoEdits: Map<String, PhotoEdit> = emptyMap(),
    /** Úprava fotky se právě vykresluje do souboru. */
    val markupBusy: Boolean = false,
    /** Krátké potvrzení akce, která v konverzaci nic viditelného nezmění (snackbar). */
    val notice: UiText? = null,
    /** Právě odepnutá zpráva — obrazovka k ní ukáže snackbar s „Vrátit". */
    val unpinned: ChatMessage? = null,
    /** Uživatel, kterého karta kontaktu právě přidala — obrazovka ukáže snackbar s „Vrátit". */
    val addedContact: Long? = null,
    /** Zavolat zpátky (ťuknutí na zprávu o hovoru); vyřídí ho tlačítko hovoru v hlavičce. */
    val callRequest: CallRequest? = null,
    /** Zpráva, jejíž text se právě upravuje ve vstupním poli; `null` při běžném psaní. */
    val editing: ChatMessage? = null,
    /** Náhled odkazu v rozepsaném textu, jak ho vrátil server; `null`, když text odkaz nemá. */
    val linkPreview: LinkPreview? = null,
    /** Odkazy v rozepsaném textu, jak stojí za sebou; víc než jeden = jde vybrat, ke kterému náhled. */
    val linkPreviewLinks: List<String> = emptyList(),
    /** Co uživatel s náhledem udělal (vybral odkaz, vypnul, velikost, poloha). Patří ke konceptu. */
    val linkPreviewOptions: LinkPreviewOptions = LinkPreviewOptions(),
    /** Zpráva krátce zvýrazněná po skoku na citaci; `null` většinu času. */
    val highlightedMessageId: Long? = null,
    /** Skok na nenačtenou zprávu, která se právě načítá (viz [PendingJump]). */
    val pendingJump: PendingJump? = null,
    /** Hledání v chatu; `null` = zavřené (hlavička ukazuje chat, dole je vstupní pole). */
    val search: InChatSearch? = null,
    /** Jednorázová událost: otevřít chat ze zmínky nebo odkazu, případně se skokem na zprávu. */
    val openChat: ChatTarget? = null,
    /** Jednorázová událost: odkaz na obrazovku aplikace (`tg://settings/…`, `tg://newgroup`). */
    val openScreen: AppScreen? = null,
    /** Jednorázová událost: otevřít příběh v prohlížeči (citace odpovědi, odkaz na příběh). */
    val openStory: StoryTarget? = null,
    /** Přihlašovací odkaz, který se z ťuknutí neotevírá — jen upozornění; `null` = žádný. */
    val loginWarning: LoginLinkKind? = null,
    /** Jednorázová událost: odkaz `t.me/bot?startgroup=…` — otevřít výběr skupiny. */
    val addBotToGroup: InternalLink.BotStartInGroup? = null,
    /** Tlačítko bota s inline dotazem do jiného chatu: obrazovka otevře výběr chatu. */
    val switchInline: SwitchInlinePick? = null,
    /** Jednorázová událost: otevřít profil chatu (odesílatele ze skupiny). */
    val openProfile: Long? = null,
    /** Výběr člena pro hledání podle odesílatele; `null` = zavřený. */
    val memberPicker: MemberPicker? = null,
    /** Náhled chatu z pozvánky; `null` = zavřený. */
    val invite: InviteSheetState? = null,
    /** Náhled sdílené složky z odkazu `t.me/addlist/…`; `null` = zavřený. */
    val folderInvite: FolderInviteSheetState? = null,
    /** Vlastní klávesnice bota nebo vynucená odpověď v chatu; `null` = žádná. */
    val replyMarkup: ChatReplyMarkup? = null,
    /** Příkazy botů v chatu — napovídání po „/" a seznam z tlačítka u pole; prázdné = žádní boti. */
    val botCommands: List<BotCommandItem> = emptyList(),
    /** Seznam příkazů otevřený tlačítkem u pole (bez ohledu na to, co je v poli). */
    val commandsOpen: Boolean = false,
    /** Mini aplikace z tlačítka nabídky bota; když je, stojí u pole místo tlačítka příkazů. */
    val botMenuApp: BotMenuApp? = null,
    /** Po kolika sekundách se nové zprávy chatu samy mažou; 0 = nemažou (odznak v hlavičce). */
    val autoDeleteTime: Int = 0,
    /** Rozepsaný dotaz na inline bota (`@gif kočka`) a jeho výsledky; `null` = žádný. */
    val inline: InlineQueryState? = null,
    /** Napovídání zmínky (`@ja`): členové k dotazu u kurzoru; `null` = nic se nenapovídá. */
    val mentionSuggestions: MentionSuggestions? = null,
    /** Tlačítko bota chce sdílet telefonní číslo a čeká se na potvrzení. */
    val confirmSharePhone: Boolean = false,
    /** Tlačítko bota chce polohu; po potvrzení ji zjistí obrazovka. */
    val confirmShareLocation: Boolean = false,
    /** Odkaz z tlačítka bota, který čeká na potvrzení; `null` = žádný. */
    val confirmLink: String? = null,
    /** Tlačítko bota chce uživatele přihlásit na web; čeká se na potvrzení v dialogu. */
    val loginRequest: LoginRequest? = null,
    /** Upozornění od bota, které se musí potvrdit (`showAlert`); `null` = žádné. */
    val botAlert: String? = null,
    /** Tlačítko bota, na jehož odpověď se čeká; `null` = žádné. */
    val pressingBotButton: BotButtonRef? = null,
    /** Video kolečko, které hraje se zvukem; `null` = všechna hrají potichu (nebo vůbec). */
    val soundingVideoNote: Long? = null,
    /** Odkud kolečko se zvukem hraje (čas v médiu, viz [MessageAction.PlayMediaAt]); jinak nula. */
    val soundingVideoNoteFromMs: Long = 0L,
    /** Video otevřít v prohlížeči od daného místa (čas v médiu); `null` = nic. */
    val openMediaAt: MediaAt? = null,
    /** Popis bota; `null` = chat není s botem (a Start se nenabízí). */
    val botIntro: BotIntro? = null,
    /** Parametr ze startovacího odkazu, který čeká na Start ([BotStartRequests]). */
    val botStartParameter: String? = null,
    /** Start odešel a čeká se na odpověď — tlačítko je neaktivní. */
    val startingBot: Boolean = false,
    /** Má chat naplánované zprávy? Pak je u pole hodiny, které otevřou jejich seznam. */
    val hasScheduledMessages: Boolean = false,
    /** Otevřený seznam naplánovaných zpráv; `null` = zavřený. */
    val scheduledMessages: List<ChatMessage>? = null,
    /** Zpráva s anketou, jejíž hlasující ukazuje sheet; `null` = zavřený. */
    val pollVoters: Long? = null,
    /** Sheet se stavem účasti v soutěži; `null` = zavřený. */
    val giveaway: GiveawaySheet? = null,
    /** Zpráva s anketou, do které se přidává odpověď (dialog); `null` = zavřený. */
    val addingPollOption: Long? = null,
    /** Anketa, kterou uživatel chce ukončit a čeká se na potvrzení; `null` = žádná. */
    val pendingStopPoll: Long? = null,
    /** Useknuté články, které se právě dočítají („Číst dál"). */
    val loadingArticles: Set<Long> = emptySet(),
    /** Sheet výběru kontaktu ze sponky; `null` = zavřený. */
    val contactPicker: ContactPicker? = null,
    /** Dialog upozornění na blízkost u vlastní živé polohy; `null` = zavřený. */
    val proximityAlert: ProximityAlertPrompt? = null,
    /**
     * Živé polohy, které se v chatu právě sdílí (vlastní napřed) — pro dock nad konverzací.
     * Nezávisí na načtené historii: zpráva s polohou může být dávno odscrollovaná nebo mimo okno.
     */
    val liveLocations: List<ChatMessage> = emptyList(),
    /** Kontakt, který čeká na potvrzení odeslání (jméno a číslo); `null` = žádný. */
    val pendingContact: MessageMedia.Contact? = null,
    /** Jednorázová událost: tlačítko bota chce formulář nové ankety (v tomhle režimu). */
    val openPollComposer: PollComposerMode? = null,
    /** Jednorázová událost: tlačítko bota chce výběr lidí nebo chatu. */
    val openBotShare: BotShareRequest? = null,
    /** Mini aplikace k otevření — obrazovka ji otevře a událost vynuluje. */
    val openWebApp: WebAppRequest? = null,
    /** Jednorázová událost: otevřít sheet sady samolepek z odkazu `t.me/addstickers/…`. */
    val openStickerSet: Long? = null,
    /** Běží přidání ke skupině / kanálu ([ConversationViewModel.join]). */
    val joining: Boolean = false,
    /** Ztlumení chatu; `null`, dokud se nepřečte. V nabídce ⋮ a v kanálu bez práva publikovat. */
    val muted: Boolean? = null,
    /** Co jde s chatem udělat proti spamu (nahlásit, zablokovat); `null`, dokud to nedorazí. */
    val spam: SpamActions? = null,
    /** Reklamy k zobrazení. Podle Telegram ToS se nesmí filtrovat ani skrývat. */
    val sponsored: List<SponsoredMessage> = emptyList(),
    /** Text právě letící ze vstupního pole do bubliny; `null` když se nic neodesílá. */
    val flight: MessageFlight? = null,
    /** Od nejnovější po nejstarší. */
    val messages: List<ChatMessage> = emptyList(),
    /**
     * Klíč položky v seznamu pro zprávy, kterým TDLib po odeslání změnil id: nové id → dočasné.
     *
     * Bez toho by potvrzení doručení znamenalo pro `LazyColumn` odebrání jedné položky a přidání
     * jiné — bublina by problikla. Ostatní zprávy tu nejsou a klíčem je jim rovnou id.
     */
    val itemKeys: Map<Long, Long> = emptyMap(),
    val loading: Boolean = false,
    val reachedStart: Boolean = false,
    /**
     * Načtené okno sahá až k NEJNOVĚJŠÍ zprávě chatu. Po skoku na starou zprávu (hledání,
     * citace) je v [messages] jen okno kolem ní a nejnovější zprávy chybí — dokud se k nim
     * nedostránkuje, nové zprávy se do seznamu nepřidávají, jinak by v něm vznikla díra.
     */
    val reachedEnd: Boolean = true,
    /**
     * Pořadové číslo načteného okna. Zvedá se, kdykoli se [messages] VYMĚNÍ celé (skok do
     * historie, návrat k přítomnosti), ne při stránkování. Obrazovka podle něj zakládá nový stav
     * seznamu — viz `listState` v `ConversationScreen`.
     */
    val window: Int = 0,
    val error: UiText? = null,
) {
    /** Konverzace zúžená na část chatu (vlákno komentářů, téma): hlavička patří jí, ne chatu. */
    val narrowed: Boolean get() = threadRootId != null || forumTopicId != null || savedTopicId != null

    /**
     * Jestli se u připravené přílohy smí nabídnout mizení — pro [attachmentCount] příloh.
     * Pravidlo je [selfDestructAllowed]; dokud hlavička chatu nedorazí, nesmí se nic.
     */
    /** Do chatu se smí psát: vstupní pole, odpověď swipem. Dokud hlavička nedorazí, bere se, že ano. */
    val canWrite: Boolean
        get() = info == null || (!info.canJoin && info.writeAccess == WriteAccess.Allowed)

    /**
     * Které druhy obsahu chat přijme. Dokud hlavička nedorazí, neomezuje se nic — ikona, která
     * by problikla a zmizela, je horší než ikona, po které přijde chyba jednou za čas.
     */
    val content: ContentAccess
        get() = info?.content ?: ContentAccess.All

    fun canSelfDestruct(attachmentCount: Int = attachments.size): Boolean =
        info != null && selfDestructAllowed(!info.isGroup, info.isSavedMessages, attachmentCount)

    /**
     * Klíč BLOKU, do kterého zpráva patří — u alba klíč celého alba, ne ťuknuté fotky.
     * `null`, když zpráva v načteném okně není.
     */
    fun blockKeyOf(messageId: Long): Long? {
        val message = messages.firstOrNull { it.id == messageId } ?: return null
        val album = message.mediaAlbumId
        // Blok nese klíč své NEJSTARŠÍ zprávy, stejně jako položka v seznamu (viz `conversationItems`).
        val oldest = if (album == 0L) message else messages.filter { it.mediaAlbumId == album }.minBy { it.id }
        return keyOf(oldest)
    }

    /**
     * Id vybraných zpráv od nejstarší, i s ostatními zprávami vybraných alb.
     *
     * Vzestupně podle id: id zpráv jsou v chatu rostoucí, takže je to chronologické pořadí —
     * a v něm se má kopírovat i přeposílat.
     */
    fun selectedMessageIds(): List<Long> {
        val keys = selection?.keys.orEmpty()
        if (keys.isEmpty()) return emptyList()
        return messages.filter { keyOf(it) in keys }
            .flatMap { albumMemberIds(it.id) }
            .distinct()
            .sorted()
    }

    /** Id všech načtených zpráv alba, do kterého zpráva patří; u samostatné zprávy jen ona. */
    fun albumMemberIds(messageId: Long): List<Long> {
        val album = messages.firstOrNull { it.id == messageId }?.mediaAlbumId ?: return listOf(messageId)
        if (album == 0L) return listOf(messageId)
        return messages.filter { it.mediaAlbumId == album }.map { it.id }
    }

    /** Stabilní klíč položky zprávy; přežije změnu id po odeslání. */
    fun keyOf(message: ChatMessage): Long = itemKeys[message.id] ?: message.id

    /**
     * Zpráva, která dorazila updatem nebo se vrátila z odeslání.
     *
     * **Rozepsanou zprávu, kterou už nahradilo potvrzení odeslání, ZAHODÍ.** Potvrzení totiž
     * nemusí dorazit až po ní: `newMessages` a `sentMessageUpdates` jsou dva samostatné odběry
     * a jejich pořadí není ničím zaručené. Když se prohodí, vrátila by se do seznamu dočasná
     * verze zprávy, která už v něm je pod novým id — a protože si podle [itemKeys] obě drží
     * TENTÝŽ klíč, `LazyColumn` na to spadl („Key … was already used").
     *
     * Ověřuje se to proti HODNOTÁM [itemKeys], tedy dočasným id potvrzených zpráv. Klíče jsou
     * nová id a těch se dotaz netýká.
     */
    fun withMessage(message: ChatMessage): ConversationState = when {
        message.id in itemKeys.values -> this
        reachedEnd -> copy(messages = merge(messages, listOf(message)))
        /*
         * Okno je v minulosti (viz [reachedEnd]). Cizí nová zpráva by za oknem udělala díru,
         * takže se jen zahodí — dorazí se stránkováním k novějším. Vlastní ODESÍLANÁ zpráva ale
         * znamená, že uživatel chce být dole: seznam začne znovu od ní, starší se dotáhnou
         * obvyklým stránkováním a obrazovka odroluje dolů.
         */
        message.isOutgoing && message.sendState == SendState.Sending -> copy(
            messages = listOf(message),
            reachedStart = false,
            reachedEnd = true,
            window = window + 1,
            scrollTarget = ScrollTarget(message.id, ScrollAnchor.Bottom),
        )
        else -> this
    }

    /**
     * Okno kolem zprávy (skok do historie). Nahradí, co bylo načtené — mezi starým a novým
     * oknem by jinak zůstala díra. O obou koncích se neví nic, zjistí se stránkováním.
     */
    fun withWindow(page: List<ChatMessage>): ConversationState =
        copy(messages = merge(emptyList(), page), reachedStart = false, reachedEnd = false, window = window + 1)

    /**
     * Stránka novějších zpráv nad [newestId]. Jen když okno mezitím nikdo nevyměnil (skok jinam,
     * odeslání zprávy) — jinak by se do nového seznamu vmíchala stránka ze starého. Stránka bez
     * novějších zpráv znamená, že okno došlo k přítomnosti.
     */
    fun withNewerPage(newestId: Long, page: List<ChatMessage>): ConversationState {
        if (reachedEnd || messages.firstOrNull()?.id != newestId) return this
        val newer = page.filter { it.id > newestId }
        return copy(messages = merge(messages, newer), reachedEnd = newer.isEmpty())
    }

    /** Stránka starších zpráv pod [oldestId]; stejná pojistka jako [withNewerPage]. */
    fun withOlderPage(oldestId: Long, page: List<ChatMessage>): ConversationState {
        if ((messages.lastOrNull()?.id ?: 0L) != oldestId) return this
        return copy(messages = merge(messages, page), reachedStart = page.isEmpty())
    }

    /**
     * Potvrzení (nebo selhání) odeslání. TDLib zprávě ZMĚNÍ id, takže ji nestačí aktualizovat —
     * původní se musí ze seznamu odebrat, jinak by tam byla dvakrát.
     *
     * Klíč položky ale zůstává PŮVODNÍ. Změna klíče znamená pro `LazyColumn` jinou položku: stará
     * by odprchla, nová naskočila a z potvrzení doručení by byl problik celé bubliny — navíc
     * uprostřed letu ze vstupního pole, jehož cíl by zmizel.
     */
    fun withSentMessage(oldId: Long, message: ChatMessage): ConversationState = copy(
        messages = merge(messages.filterNot { it.id == oldId }, listOf(message)),
        itemKeys = itemKeys - oldId + (message.id to (itemKeys[oldId] ?: oldId)),
    )
}

/** Tlačítko bota určené pozicí v klávesnici zprávy. */
data class BotButtonRef(val messageId: Long, val row: Int, val column: Int)

/**
 * Nápověda do prázdného pole, kterou si přeje bot; `null` = výchozí. U vynucené odpovědi jen
 * tehdy, když se opravdu odpovídá na tu zprávu — jinak by nápověda mluvila o něčem jiném.
 */
val ConversationState.inputPlaceholder: String?
    get() = when (val markup = replyMarkup) {
        is ChatReplyMarkup.Keyboard -> markup.placeholder.ifEmpty { null }
        is ChatReplyMarkup.ForceReply -> markup.placeholder.takeIf { it.isNotEmpty() && replyingTo?.id == markup.messageId }
        null -> null
    }

/**
 * Spotřebovala odpověď odeslaná jinde ([sent], formulář ankety) tu rozepsanou tady? Jen když
 * se odpovídá pořád na tutéž zprávu — mezitím mohl uživatel začít odpovídat jinam.
 *
 * Odpověď se nezahazuje hned při otevření formuláře — kdo ho zavře, má ji mít dál.
 */
/**
 * Karty sdíleného kontaktu [userId] nabízejí (ne)přidání do kontaktů podle [addable] — po přidání
 * a po jeho vrácení. Týká se všech karet téhož člověka v konverzaci, ne jen té ťuknuté.
 */
internal fun ConversationState.withContactAddable(userId: Long, addable: Boolean): ConversationState =
    copy(
        messages = messages.map { message ->
            val contact = message.media as? MessageMedia.Contact
            if (contact?.userId == userId) message.copy(media = contact.copy(canAddToContacts = addable)) else message
        },
    )

internal fun ConversationState.replyConsumedBy(sent: ReplyRequest): Boolean =
    replyingTo?.id == sent.messageId


/** Výběr kontaktu ke sdílení; filtruje se lokálně podle jména, kontaktů bývají desítky. */
data class ContactPicker(
    val loading: Boolean = false,
    val contacts: List<ChatSummary> = emptyList(),
    val query: String = "",
    /** Vlastní účet — řádek „Můj kontakt" nad kontakty; `null`, dokud se nenačte. */
    val me: MessageMedia.Contact? = null,
) {
    /** Vlastní kontakt se nabízí jen bez dotazu: hledá se v kontaktech, ne v sobě. */
    val showsMe: Boolean get() = me != null && query.isBlank()

    val shown: List<ChatSummary>
        get() = query.trim().let { q -> if (q.isEmpty()) contacts else contacts.filter { it.title.contains(q, ignoreCase = true) } }
}

/**
 * Mini aplikace, kterou chce konverzace otevřít. [launch] = přes `openWebApp` (tlačítko pod
 * zprávou, nabídka bota), jinak jen adresa (`getWebAppUrl`: vlastní klávesnice, tlačítko nad
 * výsledky inline bota). [keyboardButtonText] jen u vlastní klávesnice — s ním smí mini aplikace
 * poslat data. [main] = hlavní mini aplikace bota (`getMainWebApp`), [shortName] = pojmenovaná
 * z odkazu `t.me/bot/app`; [url] je u obou prázdná.
 */
data class WebAppRequest(
    val botUserId: Long,
    val url: String,
    val launch: Boolean,
    val keyboardButtonText: String? = null,
    val main: Boolean = false,
    val startParameter: String = "",
    /** Pojmenovaná mini aplikace z odkazu `t.me/bot/app` (`getWebAppLinkUrl`). */
    val shortName: String = "",
)

/** Výběr pro tlačítko bota „vyber lidi" ([users]) / „vyber chat" ([chat]) ve zprávě [messageId]. */
data class BotShareRequest(val messageId: Long, val users: BotUsersRequest? = null, val chat: BotChatRequest? = null)

/** Přihlášení na web z tlačítka bota, které čeká na potvrzení. */
data class LoginRequest(val messageId: Long, val buttonId: Long, val prompt: LoginUrlPrompt.Confirm)

/** Tlačítko bota s inline dotazem do chatu, který se teprve vybere ([SwitchInlineTarget.Chosen]). */
data class SwitchInlinePick(val bot: String, val query: String, val target: SwitchInlineTarget.Chosen)

/** Sheet se stavem účasti v soutěži ze zprávy [messageId]; [status] je `null`, dokud nedorazí. */
data class GiveawaySheet(
    val messageId: Long,
    val status: GiveawayStatus? = null,
    val failed: Boolean = false,
    val codeCopied: Boolean = false,
)

/** Příběh, který se má otevřít v prohlížeči: kdo ho zveřejnil a jeho id. */
data class StoryTarget(val posterChatId: Long, val storyId: Int)

/** Cesta, pod kterou přílohu zná vestavěná galerie (viz [galleryPath] nad záznamy úprav). */
fun ConversationState.galleryPath(path: String): String = photoEdits.galleryPath(path)

/** Požadavek na hovor ze zprávy o hovoru; každý je nová instance, i dva stejné po sobě. */
class CallRequest(val video: Boolean)

/**
 * Sloučí novou dávku do stávajícího seznamu.
 *
 * Deduplikuje podle id (překryv stránek i editace přijdou jako tatáž zpráva) a udrží řazení
 * od nejnovější. Novější verze téže zprávy přebíjí starší.
 */
internal fun merge(current: List<ChatMessage>, incoming: List<ChatMessage>): List<ChatMessage> {
    if (incoming.isEmpty()) return current

    val byId = LinkedHashMap<Long, ChatMessage>(current.size + incoming.size)
    current.forEach { byId[it.id] = it }
    incoming.forEach { byId[it.id] = it }

    return byId.values.sortedByDescending { it.id }
}

/**
 * Služba Telegramu (přihlašovací kódy, upozornění na nové přihlášení). Její zprávy akce nemají:
 * kód k přihlášení se nikam nekopíruje a nic jiného, s čím by šlo něco udělat, v nich není.
 */
private const val TELEGRAM_SERVICE_ID = 777000L

/**
 * Kolik emoji se vejde do rychlého řádku.
 *
 * Šest jako v Google Messages — na jeden řádek se vejdou i na úzkém telefonu vedle tlačítka „+".
 */
private const val MAX_QUICK_REACTIONS = 6

/** Médium článku podle pořadí; jen z načtené zprávy — co je vidět, je načtené. */
internal fun ConversationState.articleMedia(messageId: Long, index: Int): MessageMedia? =
    messages.firstOrNull { it.id == messageId }?.article?.media?.firstOrNull { it.index == index }?.media
