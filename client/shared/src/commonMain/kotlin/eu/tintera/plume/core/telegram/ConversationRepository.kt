package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.location.GeoPoint
import kotlinx.coroutines.flow.Flow

/**
 * Přístup k obsahu jedné konverzace.
 *
 * Rozhraní je **záměrně bezstavové**. Stránkovací stav (kde jsme v historii, co už je načtené)
 * drží `ConversationViewModel`, ne tohle — jednak je ta logika tím testovatelná bez TDLib,
 * jednak je stav přirozeně vázaný na životnost obrazovky, ne na aplikaci.
 *
 * Protějšek je [ChatRepository], který je naopak stavový: seznam chatů je jeden pro celou
 * aplikaci a musí žít i když na něj nikdo nekouká.
 */
interface ConversationRepository {

    /**
     * Načte [limit] zpráv **starších** než [fromMessageId] (i ji samu, když existuje).
     *
     * `fromMessageId = 0` znamená „od nejnovější". Prázdný výsledek znamená konec historie —
     * TDLib nevrací žádný explicitní příznak, pozná se to jen podle toho, že nic nepřišlo.
     *
     * Záporný [offset] přidá tolik zpráv NOVĚJŠÍCH než [fromMessageId] — okno kolem zprávy
     * a stránka směrem k přítomnosti ([limit] pak musí být aspoň `-offset`). TDLib smí vrátit
     * méně, než se chtělo, takže konec se i tady pozná jen podle toho, že nic nového nepřišlo.
     */
    suspend fun history(chatId: Long, fromMessageId: Long, limit: Int = 30, offset: Int = 0): Result<List<ChatMessage>>

    /**
     * Vlákno komentářů k příspěvku [messageId] kanálu [chatId] (`getMessageThread`). Selže
     * u příspěvku, který diskusi nemá, nebo když ji server nevydá (soukromá skupina).
     */
    suspend fun commentThread(chatId: Long, messageId: Long): Result<CommentThread>

    /**
     * Stránka vlákna [threadId] v chatu [chatId]; parametry jako u [history]. Kořenová zpráva
     * vlákna v ní NENÍ — tu si přidá [ThreadConversationRepository].
     */
    suspend fun threadHistory(chatId: Long, threadId: Long, fromMessageId: Long, limit: Int, offset: Int): Result<List<ChatMessage>>

    /** Nové zprávy v tomhle chatu, jak přicházejí. */
    fun newMessages(chatId: Long): Flow<ChatMessage>

    /** Id smazaných zpráv. */
    fun deletedMessages(chatId: Long): Flow<List<Long>>

    /**
     * Zprávy, jimž se změnil obsah (editace) — nebo jen tlačítka bota pod nimi: bot klávesnici
     * přepisuje i beze změny textu (odpočet pokusů, další krok menu).
     */
    fun editedMessages(chatId: Long): Flow<ChatMessage>

    /**
     * Ťuknutí na tlačítko bota se zpětným voláním ([BotButtonKind.Callback]).
     *
     * Tlačítko se určuje POZICÍ v klávesnici a data, která bot dostane, si repository dočte ze
     * zprávy samo — do domény nepatří (jsou to bajty od bota) a klávesnice se mezi vykreslením
     * a ťuknutím mohla změnit. Proto se kontroluje i [text]: když na tom místě už je jiné
     * tlačítko, nepošle se nic.
     *
     * Bot odpovídá, jak chce: textem, upozorněním, odkazem, nebo nijak (jen přepíše zprávu).
     */
    /**
     * Vlastní klávesnice bota nebo vynucená odpověď, která v chatu právě platí; `null` = žádná.
     *
     * Patří CHATU, ne zprávě: TDLib drží, ze které zprávy se bere (`Chat.replyMarkupMessageId`),
     * a změnu hlásí `UpdateChatReplyMarkup` — nová zpráva bota ji přepíše, jednorázová po použití
     * zmizí. Odběr stojí před prvním čtením, aby se změna mezi nimi neztratila.
     */
    fun replyMarkup(chatId: Long): Flow<ChatReplyMarkup?>

    /**
     * Klávesnice nebo vynucená odpověď se použila a má zmizet. TDLib to po jednorázové klávesnici
     * a po vynucené odpovědi výslovně chce (`deleteChatReplyMarkup`) — sám to neudělá.
     */
    suspend fun dismissReplyMarkup(chatId: Long, messageId: Long): Result<Unit>

    /**
     * Text z tlačítka vlastní klávesnice jako zpráva. **Rozepsaný koncept nemaže** — na rozdíl od
     * [send] (`clearDraft`): ťuknutí na tlačítko není odeslání toho, co je v poli.
     */
    suspend fun sendKeyboardText(chatId: Long, text: String, replyToMessageId: Long?): Result<ChatMessage>

    /**
     * Hlas v anketě. [answerIndexes] jsou pozice odpovědí v PŮVODNÍM pořadí
     * ([PollAnswer.index]), ne v tom, v jakém se ukazují; prázdný seznam hlas zruší.
     *
     * Výsledek dorazí změnou obsahu zprávy ([editedMessages]) — napřed s `isBeingChosen`,
     * po potvrzení se skutečnými počty. Lokálně se nepřepisuje nic.
     */
    suspend fun vote(chatId: Long, messageId: Long, answerIndexes: List<Int>): Result<Unit>

    /**
     * Odešle anketu nebo kvíz. V kanálu je anketa vždycky anonymní — neanonymní server do
     * kanálu nepustí.
     */
    suspend fun sendPoll(
        chatId: Long,
        poll: PollDraft,
        replyToMessageId: Long?,
        quote: ReplyQuote? = null,
    ): Result<ChatMessage>

    /** Meze nové ankety (`poll_answer_count_max`, `poll_open_period_max`). */
    suspend fun pollLimits(): PollLimits

    /** Uzavře anketu; smí jen autor (nebo správce), pozná se podle `canBeEdited`. */
    suspend fun stopPoll(chatId: Long, messageId: Long): Result<Unit>

    /** Kdo hlasoval pro odpověď s [answerIndex] (v původním pořadí). */
    suspend fun pollVoters(chatId: Long, messageId: Long, answerIndex: Int, offset: Int): Result<PollVotersPage>

    /** Vlastní telefonní číslo jako kontakt — tlačítko bota „sdílet číslo", po potvrzení. */
    suspend fun shareOwnPhone(chatId: Long, replyToMessageId: Long?): Result<ChatMessage>

    suspend fun pressBotButton(
        chatId: Long,
        messageId: Long,
        row: Int,
        column: Int,
        text: String,
    ): Result<BotAnswer>

    /**
     * Id zpráv, jejichž obsah někdo otevřel — u hlasovky „poslechnuto". Chodí i pro naše
     * odchozí, když si je pustí protějšek.
     */
    fun contentOpenedUpdates(chatId: Long): Flow<Long>

    /** Nahlásí, že uživatel obsah zprávy otevřel (pustil hlasovku). */
    suspend fun openContent(chatId: Long, messageId: Long): Result<Unit>

    /** Reakce a počty příspěvku, které se u zprávy změnily. Klíč je `messageId`. */
    fun interactionUpdates(chatId: Long): Flow<MessageInteractions>

    /**
     * Počty nepřečtených zmínek a reakcí v chatu, živě. Na tlačítka „@" a „❤" nad tlačítkem dolů.
     */
    fun unreadMarks(chatId: Long): Flow<UnreadMarks>

    /**
     * Změny příznaku nepřečtených reakcí a hlasů u zpráv ([ChatMessage.hasUnreadReactions],
     * [ChatMessage.hasUnreadPollVotes]).
     */
    fun unreadMarkChanges(chatId: Long): Flow<UnreadMarkChange>

    /**
     * Nejstarší nepřečtená zmínka nebo zpráva s nepřečtenou reakcí; `null`, když žádná není.
     * Skáče se od nejstarší, jako u nepřečtených zpráv: přečtení skokem ji odebere a další
     * ťuknutí vede na následující.
     */
    suspend fun oldestUnreadMark(chatId: Long, kind: UnreadMarkKind): Result<Long?>

    /** Označí všechny zmínky, nebo všechny reakce v chatu za přečtené. */
    suspend fun readAllMarks(chatId: Long, kind: UnreadMarkKind): Result<Unit>

    /**
     * Přidá nebo odebere reakci. TDLib má na to dvě samostatná volání, protože „přepnutí"
     * na jeho úrovni neexistuje — stav si musí pamatovat klient.
     */
    suspend fun setReaction(
        chatId: Long,
        messageId: Long,
        emoji: String,
        add: Boolean,
    ): Result<Unit>

    /** Emoji nabízená u téhle zprávy. Liší se chat od chatu — kanály si sadu omezují. */
    suspend fun availableReactions(chatId: Long, messageId: Long): Result<List<String>>

    /**
     * Kdo na zprávu reagoval a čím, od nejnovější reakce.
     *
     * Vrací **jednu stránku** (nejvýš [REACTION_DETAIL_LIMIT] řádků). U příspěvku s tisíci
     * reakcemi seznam nikdo neprojde a stránkovat ho by znamenalo držet offset ve stavu sheetu;
     * až to bude potřeba, přibude sem parametr.
     */
    suspend fun reactionDetails(chatId: Long, messageId: Long): Result<List<ReactionDetail>>

    /**
     * Podrobnosti o zprávě pro sheet „Informace" — viz [MessageInfo].
     *
     * Dílčí dotazy (odkaz, zhlédnutí, čas přečtení) se ptají **současně** a selhání kteréhokoli
     * z nich sheet nezahodí: chybějící údaj se prostě nevypíše. Celé volání selže jen tehdy,
     * když se nepodaří načíst ani samotnou zprávu.
     */
    suspend fun messageInfo(chatId: Long, messageId: Long): Result<MessageInfo>

    /**
     * Co se dá s konkrétní zprávou udělat.
     *
     * Ptá se **až při otevření nabídky**, ne při načtení historie. TDLib tyhle příznaky ve zprávě
     * neposílá — jsou za samostatným `getMessageProperties`, takže načítat je dopředu by znamenalo
     * jeden dotaz na každou zprávu v historii kvůli nabídce, kterou uživatel skoro nikdy neotevře.
     */
    suspend fun messageActions(chatId: Long, messageId: Long): Result<MessageActions>

    /**
     * Přepošle zprávy do jiného chatu.
     *
     * [messageIds] musí být **vzestupně** — tak to TDLib vyžaduje; album se přeposílá celé, takže
     * v cíli zůstane albem. Přeposlané zprávy dorazí do cílového chatu updaty jako každé jiné.
     */
    suspend fun forward(
        fromChatId: Long,
        messageIds: List<Long>,
        toChatId: Long,
        options: ForwardOptions = ForwardOptions(),
    ): Result<Unit>

    /**
     * Co přeposílané zprávy dovolí: jestli jdou poslat jako kopie bez odesílatele a jestli
     * nesou popisek, který by šlo vynechat. Když se to zjistit nepodaří, nedovolí nic — volba,
     * po které přijde chyba, je horší než volba, která tam není.
     */
    suspend fun forwardCapabilities(chatId: Long, messageIds: List<Long>): ForwardCapabilities

    /**
     * Smaže zprávy.
     *
     * [revoke] rozhoduje, jestli zmizí i protějšku. Není to volba klienta — co je povolené, říká
     * [MessageActions]; ve skupinách a kanálech je `revoke` vždycky `true`.
     */
    suspend fun deleteMessages(chatId: Long, messageIds: List<Long>, revoke: Boolean): Result<Unit>

    /**
     * Všechny připnuté zprávy chatu, od nejnovější.
     *
     * Nese i to, jestli je smí uživatel odepnout — právo připínat je v chatu jedno pro všechny
     * zprávy, takže se zjišťuje jednou, ne dotazem na každou.
     */
    suspend fun pinnedMessages(chatId: Long): Result<PinnedMessages>

    /** Odepne zprávu. Potvrzení dorazí přes [pinnedMessageChanges]. */
    suspend fun unpin(chatId: Long, messageId: Long): Result<Unit>

    /**
     * Připne zprávu. Potvrzení dorazí přes [pinnedMessageChanges].
     *
     * [notify] upozorní členy skupiny nebo odběratele kanálu. [onlyForSelf] dává smysl jen
     * v soukromém chatu — jinde server připíná vždycky všem.
     *
     * Výchozí hodnoty (tiše, pro všechny) jsou pro „Vrátit" po odepnutí: to je oprava omylu,
     * ne nové oznámení.
     */
    suspend fun pin(
        chatId: Long,
        messageId: Long,
        notify: Boolean = false,
        onlyForSelf: Boolean = false,
    ): Result<Unit>

    /**
     * Ohlásí, že se v chatu něco připnulo nebo odepnulo.
     *
     * Nenese novou připnutou zprávu, jen signál — odepnutí nejnovější znamená, že nejnovější je
     * teď jiná, a tu zná jen TDLib. Volající si seznam znovu přečte přes [pinnedMessages].
     */
    fun pinnedMessageChanges(chatId: Long): Flow<Unit>

    /**
     * Zprávy s fotkou nebo videem kolem [messageId] (včetně ní), od nejstarší. Pro listování v prohlížeči.
     *
     * [older] a [newer] jsou horní meze — na začátku a konci chatu jich přijde míň. Konec se
     * pozná podle toho, že daným směrem nepřibyla žádná nová zpráva.
     */
    suspend fun visualMedia(chatId: Long, messageId: Long, older: Int, newer: Int): Result<List<ChatMessage>>

    /** Kolikátá fotka v chatu je [messageId] a kolik jich je celkem, nebo `null`, když to server neví. */
    suspend fun visualMediaPosition(chatId: Long, messageId: Long): Result<PhotoPosition?>

    /** Jedna konkrétní zpráva. Pro doplnění citace, která není v načtené historii. */
    /**
     * Stránka sdíleného obsahu od nejnovější dozadu.
     *
     * [fromMessageId] `0` začíná u nejnovější, jinak se pokračuje od poslední načtené (ta sama
     * ve výsledku není). Konec se pozná prázdnou stránkou.
     */
    suspend fun sharedMedia(
        chatId: Long,
        kind: SharedMediaKind,
        fromMessageId: Long = 0,
    ): Result<SharedMediaPage>

    /** Počty pro všechny druhy naráz; profil podle nich schová prázdné řádky. */
    suspend fun sharedMediaCounts(chatId: Long): Result<SharedMediaCounts>

    suspend fun message(chatId: Long, messageId: Long): Result<ChatMessage>

    /**
     * Odešle zprávu a **okamžitě vrátí její rozepsanou podobu**.
     *
     * TDLib vrací zprávu hned, s dočasným id a stavem [SendState.Sending]; skutečné id přijde
     * až potvrzením v [sentMessageUpdates]. Díky tomu se dá bublina zobrazit bez čekání na server.
     *
     * [replyToMessageId] je `null` u běžné zprávy, [quote] je úryvek citované zprávy (jen
     * s [replyToMessageId]). [linkPreview] je volba odesílatele pro náhled odkazu; `null` nechá
     * rozhodnout server.
     */
    suspend fun send(
        chatId: Long,
        text: String,
        replyToMessageId: Long?,
        linkPreview: LinkPreviewOptions? = null,
        quote: ReplyQuote? = null,
        options: SendOptions = SendOptions(),
        mentions: List<NameMention> = emptyList(),
    ): Result<ChatMessage>

    /**
     * Efekty, se kterými jde zprávu poslat, v pořadí od serveru ([ids] z
     * [ChatRepository.availableEffectIds]). Prémiové vynechá, když účet Premium nemá — odeslání
     * by skončilo chybou. Efekt, který server nevydá, vypadne.
     */
    suspend fun sendableEffects(ids: List<Long>): List<MessageEffect>

    /**
     * Po kolika sekundách se nové zprávy chatu samy mažou; 0 = nemažou. Živé přes
     * `UpdateChatMessageAutoDeleteTime` — mění se i z jiného zařízení nebo od protějšku.
     */
    fun autoDeleteTime(chatId: Long): Flow<Int>

    /**
     * Má chat naplánované zprávy? Živé — `UpdateChatHasScheduledMessages` přijde i tehdy, když
     * naplánovaná zpráva odejde nebo se naplánuje z jiného zařízení.
     */
    fun hasScheduledMessages(chatId: Long): Flow<Boolean>

    /**
     * Co bot o sobě říká v prázdném chatu („Co tento bot umí?"). `null`, když chat není
     * soukromý chat s botem — podle toho obrazovka pozná, že má nabízet tlačítko Start.
     */
    suspend fun botIntro(chatId: Long): Result<BotIntro?>

    /**
     * Spustí bota (`sendBotStartMessage`) — v chatu se objeví „/start" a bot dostane [parameter]
     * (prázdný bez odkazu). Jako ostatní odesílání vrací rozepsanou zprávu hned.
     */
    suspend fun startBot(chatId: Long, parameter: String): Result<ChatMessage>

    /** Naplánované zprávy chatu, od nejdřív odcházející. */
    suspend fun scheduledMessages(chatId: Long): Result<List<ChatMessage>>

    /** Změní, kdy naplánovaná zpráva odejde; `null` = odeslat hned. */
    suspend fun reschedule(chatId: Long, messageId: Long, schedule: MessageSchedule?): Result<Unit>

    /**
     * Náhled odkazu, jak by ho server připojil k [text], nebo `null`, když text odkaz nemá.
     *
     * **Na síť se jde, jen když text odkaz opravdu obsahuje** (lokální `getTextEntities`).
     * `getLinkPreview` TDLib dokumentuje „do not call this function too often" a na text bez
     * odkazu vrací 404 — volat ho při psaní na každou pauzu by byl dotaz za nic.
     */
    suspend fun linkPreview(text: String): Result<LinkPreview?>

    /**
     * Hodí kostkou (nebo šipkami, míčem, automatem — podle [emoji] z [ChatRepository.diceEmojis]).
     * Výsledek určí server; rozepsaná zpráva ho ještě nemá a točí se úvodní animace.
     */
    suspend fun sendDice(
        chatId: Long,
        emoji: String,
        replyToMessageId: Long?,
        quote: ReplyQuote? = null,
    ): Result<ChatMessage>

    /**
     * Odešle samolepku. Stejně jako [send] vrací rozepsanou zprávu okamžitě.
     *
     * Samolepka se zároveň zařadí mezi nedávné, aby byla ve výběru příště nahoře.
     */
    suspend fun sendSticker(
        chatId: Long,
        sticker: StickerItem,
        replyToMessageId: Long?,
        quote: ReplyQuote? = null,
    ): Result<ChatMessage>

    /**
     * Odešle GIF (z klávesnice) jako animaci, ne jako fotku — z fotky by zbyl první snímek.
     * Skutečný GIF převede na MP4 server sám. Vrací rozepsanou zprávu hned jako [send].
     */
    suspend fun sendAnimation(
        chatId: Long,
        animation: OutgoingAnimation,
        replyToMessageId: Long?,
        quote: ReplyQuote? = null,
    ): Result<ChatMessage>

    /**
     * Odešle polohu, nebo místo s názvem a adresou (`InputMessageVenue`), když [place] název má.
     * Holá poloha („Moje poloha") jde jako `InputMessageLocation`. Vrací rozepsanou zprávu hned
     * jako [send].
     */
    suspend fun sendLocation(
        chatId: Long,
        place: Place,
        replyToMessageId: Long?,
        quote: ReplyQuote? = null,
    ): Result<ChatMessage>

    /**
     * Odešle GIF z výběru — soubor, který na serveru už je, takže se nic nenahrává. GIF se
     * zároveň zařadí na začátek uložených, jako samolepka mezi nedávné.
     */
    suspend fun sendGif(
        chatId: Long,
        gif: GifItem,
        replyToMessageId: Long?,
        quote: ReplyQuote? = null,
    ): Result<ChatMessage>

    /**
     * Inline bot podle jména z pole (`@gif`), nebo `null`, když takový účet botem není.
     *
     * Jestli bot inline dotazy UMÍ, se tu nezjistí — TDLib 15 příznak `is_inline` v `userTypeBot`
     * nenese. Pozná se to až odpovědí na dotaz ([inlineResults] selže).
     */
    suspend fun inlineBot(username: String): Result<InlineBotInfo?>

    /**
     * Jedna stránka výsledků inline dotazu. [offset] je [InlineResultsPage.nextOffset] předchozí
     * stránky, na začátku prázdný. [chatId] chce bot vědět, kam se píše; [location] jen botům,
     * kteří o ni stojí ([InlineBotInfo.needLocation]).
     */
    suspend fun inlineResults(
        botUserId: Long,
        chatId: Long,
        query: String,
        offset: String,
        location: GeoPoint?,
    ): Result<InlineResultsPage>

    /**
     * Odešle výsledek inline dotazu (`sendInlineQueryResultMessage`). Obsah zprávy si připravil
     * bot a u zprávy bude „přes @bot". Jako ostatní odesílání vrací rozepsanou zprávu hned.
     */
    suspend fun sendInlineResult(
        chatId: Long,
        queryId: Long,
        resultId: String,
        replyToMessageId: Long?,
        quote: ReplyQuote? = null,
    ): Result<ChatMessage>

    /**
     * Odešle přílohy z disku zařízení, s popiskem. Jedna příloha jde jako obyčejná zpráva, víc jako
     * **album** (`sendMessageAlbum`, 2–10) — Telegram víc příloh v jedné zprávě nenese.
     *
     * Stejně jako [send] vrací rozepsané zprávy okamžitě; nahrávání běží na pozadí a jeho průběh
     * hlásí [FileRepository.uploadProgress]. [caption] patří první příloze, jak to dělá Telegram;
     * prázdný popisek se nepošle.
     */
    /**
     * Odešle nahranou hlasovku. [waveform] je už zabalený (5 bitů na hodnotu), [path] Ogg/Opus
     * nebo M4A. Jako ostatní odesílání vrací rozepsanou zprávu hned.
     */
    suspend fun sendVoiceNote(
        chatId: Long,
        path: String,
        durationSeconds: Int,
        waveform: ByteArray,
        replyToMessageId: Long?,
        quote: ReplyQuote? = null,
    ): Result<ChatMessage>

    suspend fun sendAttachments(
        chatId: Long,
        attachments: List<OutgoingAttachment>,
        caption: String,
        replyToMessageId: Long?,
        quote: ReplyQuote? = null,
        options: SendOptions = SendOptions(),
        mentions: List<NameMention> = emptyList(),
    ): Result<List<ChatMessage>>

    /**
     * Nahradí text odeslané zprávy.
     *
     * Výsledek se do konverzace dostane updatem (`editedMessages`), stejně jako cizí úpravy —
     * návratová hodnota se proto nevrací. Entity se dohledávají jako při odeslání.
     */
    suspend fun editText(chatId: Long, messageId: Long, text: String): Result<Unit>

    /**
     * Upraví popisek přílohy. Je to JINÉ volání než [editText] — úprava textu by popisek nezměnila
     * a naopak.
     *
     * Prázdný [caption] popisek odebere; to je v Telegramu platná úprava, na rozdíl od prázdného
     * textu zprávy.
     */
    suspend fun editCaption(chatId: Long, messageId: Long, caption: String): Result<Unit>

    /**
     * Potvrzení nebo selhání odeslané zprávy.
     *
     * Nese **staré i nové id**: TDLib po potvrzení zprávě id změní, takže ji nestačí aktualizovat,
     * musí se v seznamu nahradit.
     */
    fun sentMessageUpdates(chatId: Long): Flow<SentMessageUpdate>

    /** Posun ukazatele „protějšek přečetl až sem". */
    fun readOutboxUpdates(chatId: Long): Flow<Long>

    /**
     * Dostupnost protějšku, jak se mění.
     *
     * Samostatný flow, ne pole v [ChatInfo]: hlavička se skládá jednou, ale „naposledy viděn"
     * se mění nezávisle na všem ostatním. U skupin a kanálů nevydá nic.
     */
    fun presenceUpdates(chatId: Long): Flow<Presence>

    /**
     * Ohlásí protějšku, že píšeme, nebo že jsme přestali.
     *
     * Server činnost sám zhasne zhruba po šesti vteřinách, takže při souvislém psaní se musí
     * opakovat — o to se stará volající. Selhání se jen zaloguje: kvůli „píše…" nemá smysl
     * uživatele obtěžovat chybou.
     */
    suspend fun sendTyping(chatId: Long, typing: Boolean): Result<Unit>

    /** Označí zprávy za přečtené. Bez tohohle zůstane odznak nepřečtených viset napořád. */
    suspend fun markRead(chatId: Long, messageIds: List<Long>): Result<Unit>

    /**
     * Řekne TDLib, že se uživatel na chat dívá.
     *
     * Není to jen informace navíc: u otevřeného chatu TDLib **odloží notifikaci aspoň o vteřinu**
     * (`min_delay_ms` v `add_new_message_notification`, platí, když je klient online), takže ji
     * stihne zrušit hlášení přečtení z [markRead] a na liště ani neproblikne. Kromě toho drží
     * zprávy chatu načtené, zvedá prioritu stahování souborů a u supergroup a kanálů se podle
     * toho řídí doručování updatů.
     *
     * Musí být v páru s [closeChat] — TDLib si otevření počítá.
     */
    suspend fun openChat(chatId: Long): Result<Unit>

    /** Protějšek [openChat]. Volá se při odchodu z konverzace. */
    suspend fun closeChat(chatId: Long): Result<Unit>

    suspend fun chatInfo(chatId: Long): Result<ChatInfo>

    /**
     * Příkazy botů v chatu (`/start`…) pro napovídání a seznam příkazů. V soukromém chatu
     * příkazy toho bota, ve skupině všech botů v ní; kanál a chat s člověkem žádné.
     */
    suspend fun botCommands(chatId: Long): Result<List<BotCommandItem>>

    /**
     * Změny toho, jestli se do chatu smí psát a jestli se k němu jde přidat (správce změní
     * práva, uživatel se přidá nebo odejde). Soukromý chat nic nevydá — tam se píše vždycky.
     */
    fun accessUpdates(chatId: Long): Flow<ChatAccess>

    /**
     * Uloží rozepsanou zprávu do TDLib, odkud ji vidí seznam chatů i ostatní zařízení.
     *
     * Prázdný [text] koncept **smaže** — i kdyby zbývala citace. Odpověď bez textu Telegram jako
     * koncept nedrží a ponechat ji by znamenalo koncept, který v seznamu nic neukazuje.
     */
    suspend fun saveDraft(
        chatId: Long,
        text: String,
        replyToMessageId: Long?,
        linkPreview: LinkPreviewOptions? = null,
        quote: ReplyQuote? = null,
    ): Result<Unit>

    /**
     * Zprávy s [query] v tomhle chatu, od nejnovější. Další stránka od [fromMessageId]
     * z předchozí ([ChatSearchPage.nextFromMessageId]); nula je první stránka.
     */
    suspend fun searchInChat(
        chatId: Long,
        query: String,
        fromMessageId: Long = 0,
        /** Jen zprávy tohohle uživatele; s ním smí být [query] i prázdný (všechny jeho zprávy). */
        senderUserId: Long? = null,
    ): Result<ChatSearchPage>

    /**
     * Členové skupiny podle jména, pro hledání podle odesílatele. Jen uživatelé — anonymní správce
     * vystupující za skupinu ani kanál se filtrovat nedá. Server u velké skupiny vrátí jen část,
     * proto se hledá podle [query], ne listuje.
     */
    suspend fun searchMembers(chatId: Long, query: String): Result<List<SenderInfo>>

    /**
     * Členové, které jde v chatu zmínit a jejichž jméno (nebo uživatelské jméno) začíná
     * [query]; prázdný dotaz vrátí ty, se kterými se v chatu píše nejvíc. Vlastní účet ne.
     */
    suspend fun mentionCandidates(chatId: Long, query: String): Result<List<MentionCandidate>>

    /**
     * Text (nebo popisek) zprávy přeložený do jazyka [languageCode] (`translateMessageText`).
     * Překládá server; bez Premium přijde prostý text, s ním i s formátováním.
     */
    suspend fun translateMessage(chatId: Long, messageId: Long, languageCode: String): Result<MessageBody>

    /**
     * Jméno a fotka uživatele nebo chatu s tímhle id — pro tvář toho, kdo ve skupině píše.
     * Z keše odesílatelů, takže opakované „píše…" téhož člověka se na nic neptá.
     */
    suspend fun sender(senderId: Long): Result<SenderInfo>

    /**
     * Sponzorované zprávy pro daný chat. Mimo kanály vrací prázdný seznam.
     *
     * Zobrazení je podle Telegram ToS **povinné** — výsledek se nesmí filtrovat ani skrývat.
     */
    suspend fun sponsoredMessages(chatId: Long): Result<List<SponsoredMessage>>

    /** Přidá se k veřejné skupině nebo kanálu (nalezenému v globálním hledání). */
    suspend fun joinChat(chatId: Long): Result<Unit>

    /**
     * Nahlásí, že uživatel sponzorovanou zprávu viděl.
     *
     * Volá se teprve, když je na obrazovce **celý text** reklamy (bez tlačítka) — tak to
     * vyžaduje TDLib. Hlásit ji při prvním pixelu by znamenalo vykazovat zhlédnutí, která
     * se nestala.
     */
    suspend fun reportSponsoredView(chatId: Long, messageId: Long): Result<Unit>

    /** Nahlásí kliknutí. Musí odejít dřív, než se otevře odkaz. */
    suspend fun reportSponsoredClick(chatId: Long, messageId: Long): Result<Unit>
}

/** Připnuté zprávy chatu. */
data class PinnedMessages(
    /** Od nejnovější po nejstarší. Prázdné, když nic připnuté není. */
    val messages: List<ChatMessage>,
    /** Smí uživatel zprávy odepínat? Ve skupině to je právo, v soukromém chatu vždycky. */
    val canUnpin: Boolean,
)

/**
 * Pořadí fotky v chatu, pro počítadlo „3 z 12".
 *
 * Server ho hlásí přibližně (`getChatMessagePosition`), na orientaci to stačí. Obě čísla jdou
 * z jednoho místa, aby počítadlo neukázalo víc, než kolik je celkem.
 */
data class PhotoPosition(
    /** Od nejstarší, od 1. */
    val number: Int,
    val total: Int,
)

/**
 * Příloha k odeslání, už připravená na disku aplikace.
 *
 * TDLib posílá soubor podle **cesty** (`InputFileLocal`), takže výběr z galerie nebo souborů ho musí
 * nejdřív zkopírovat k sobě — systémové URI (`content://`, iOS security-scoped URL) TDLib neotevře.
 */
/** Režim odesílání přílohy: běžné zobrazení, spoiler, samozničení nebo odeslání jako soubor. */
sealed interface AttachmentMode {
    /** Běžné zobrazení v bublině konverzace. */
    data object Standard : AttachmentMode

    /** Skryté pod rozostřením se třpytivou texturou, dokud příjemce neklepne. */
    data object Spoiler : AttachmentMode

    /** Jednorázové zhlédnutí: po zavření zaniká. */
    data object ViewOnce : AttachmentMode

    /** Odpočet po zadaný počet sekund od otevření. */
    data class Timer(val seconds: Int) : AttachmentMode

    /** Odeslat jako nekomprimovaný soubor (v plné kvalitě a bez úprav). */
    data object AsFile : AttachmentMode
}

sealed interface OutgoingAttachment {
    val path: String
    val mode: AttachmentMode get() = AttachmentMode.Standard
    val hasSpoiler: Boolean get() = mode is AttachmentMode.Spoiler
    val selfDestructType: SelfDestructType?
        get() = when (val m = mode) {
            is AttachmentMode.ViewOnce -> SelfDestructType.Immediately
            is AttachmentMode.Timer -> SelfDestructType.Timer(m.seconds)
            else -> null
        }

    /**
     * Fotka jako fotka (s náhledem v bublině). [width] a [height] jsou rozměry připraveného souboru;
     * server je může přepsat, ale bez nich by bublina odesílané zprávy neměla poměr stran.
     */
    data class Photo(
        override val path: String,
        val width: Int,
        val height: Int,
        override val mode: AttachmentMode = AttachmentMode.Standard,
    ) : OutgoingAttachment

    /**
     * Video jako video (přehrávatelná zpráva, v albu s fotkami). [durationSeconds], [width]
     * a [height] čte platforma ze souboru — server video nerozebírá a bez nich by zpráva neměla
     * délku ani poměr stran. [thumbnail] je první snímek jako JPEG do 320 px; ukazuje se nad
     * vstupním polem a odejde jako náhled. [streamable] jen u souboru, který má index na začátku
     * (iOS ho tak ukládá); u jiného by přehrávání během stahování nefungovalo.
     */
    data class Video(
        override val path: String,
        val width: Int,
        val height: Int,
        val durationSeconds: Int,
        val thumbnail: OutgoingThumbnail?,
        val streamable: Boolean,
        override val mode: AttachmentMode = AttachmentMode.Standard,
    ) : OutgoingAttachment

    /** Cokoli jako soubor, beze změny. Název souboru se bere z cesty. */
    data class Document(override val path: String) : OutgoingAttachment {
        override val mode: AttachmentMode get() = AttachmentMode.AsFile
    }
}

/**
 * GIF připravený na disku (GIF nebo MP4 bez zvuku). [durationSeconds] je u GIFu nula — délku zná
 * až server, který ho převádí; [thumbnail] má jen MP4, GIFu náhled vyrobí server.
 */
data class OutgoingAnimation(
    val path: String,
    val width: Int,
    val height: Int,
    val durationSeconds: Int,
    val thumbnail: OutgoingThumbnail?,
)

/** Náhled přílohy připravený na disku (JPEG). */
data class OutgoingThumbnail(val path: String, val width: Int, val height: Int)

/**
 * Stránka výsledků hledání v chatu. Stačí id — zprávu samotnou načte skok na ni.
 * [total] je odhad serveru (`null`, když ho neví), [nextFromMessageId] `null` = další nejsou.
 */
data class ChatSearchPage(
    val messageIds: List<Long>,
    val total: Int?,
    val nextFromMessageId: Long?,
)

/** Hlavička konverzace. */
data class ChatInfo(
    val id: Long,
    val title: String,
    /**
     * Id poslední zprávy, kterou si protějšek přečetl.
     *
     * Stav „přečteno" **není ve zprávě samotné** — je to posuvný ukazatel na chatu. Všechny
     * odchozí zprávy s menším nebo rovným id jsou přečtené. Mění se updatem, ne přeposláním zpráv.
     */
    val lastReadOutboxMessageId: Long,
    /**
     * Id poslední zprávy, kterou si přečetl uživatel.
     *
     * Protipól [lastReadOutboxMessageId]: ten mluví o protějšku, tenhle o nás. Všechno s vyšším
     * id je nepřečtené — právě nad tím se v konverzaci kreslí předěl.
     */
    val lastReadInboxMessageId: Long,
    /**
     * Kolik zpráv je nepřečtených.
     *
     * Bez něj se to z [lastReadInboxMessageId] nedá spolehlivě odvodit: v chatu, kde jsme poslední
     * zprávu napsali my, je ukazatel starší než ta zpráva, a přesto není co číst.
     */
    val unreadCount: Int,
    /** `fileId` malé varianty avataru, nebo `null` když chat obrázek nemá. */
    val avatarFileId: Int?,
    /**
     * U skupin a kanálů se nad zprávami zobrazuje jméno odesílatele; v soukromém chatu by to byl
     * šum, protože odesílatelé jsou právě dva.
     */
    val isGroup: Boolean,
    /** Kanál. Počítá se i do [isGroup]; tady jde o rozlišení „členové" vs. „odběratelé". */
    val isChannel: Boolean,
    /**
     * Uložené zprávy (chat sám se sebou). Připnutí tu nemá koho upozorňovat ani s kým se dělit,
     * takže se na nic neptá.
     */
    val isSavedMessages: Boolean,
    /** Rozepsaný text z minula (i z jiného zařízení), nebo `null`. */
    val draftText: String?,
    /**
     * Uživatel ve skupině nebo kanálu není a přidat se může — chat otevřel z globálního hledání.
     * Místo vstupního pole je pak tlačítko „Přidat se": psát do cizí skupiny nejde a v kanálu
     * nejde nikdy.
     */
    val canJoin: Boolean = false,
    /**
     * Smí se do chatu psát ([writeAccess]). Kde ne, konverzace místo vstupního pole ukáže, co se
     * dělat dá — pole, ze kterého zpráva skončí chybou, je horší než žádné.
     */
    val writeAccess: WriteAccess = WriteAccess.Allowed,
    /**
     * Které druhy obsahu chat přijme. Skupina může povolit text a zakázat fotky; sponka, výběr
     * samolepek a mikrofon se podle toho schovají, aby odeslání neskončilo chybou.
     */
    val content: ContentAccess = ContentAccess.All,
    /** Zpráva, na kterou koncept odpovídá, nebo `null`. */
    val draftReplyToMessageId: Long?,
    /** Volba náhledu odkazu uložená s konceptem, nebo `null` (rozhoduje server). */
    val draftLinkPreview: LinkPreviewOptions? = null,
    /** Citovaný úryvek odpovědi v konceptu, nebo `null` (odpověď na celou zprávu). */
    val draftReplyQuote: ReplyQuote? = null,
    /** Ověřený, podvod, falešný (viz [Verification]) — štítek u jména v hlavičce. */
    val verification: Verification? = null,
    /** Vlastní emoji statusu za jménem v hlavičce (viz [statusEmojiId]), nebo `null`. */
    val emojiStatus: Long? = null,
)

/**
 * Citace ČÁSTI zprávy v odpovědi: úryvek textu citované zprávy a kde v ní začíná.
 *
 * Nad odpovědí se pak ukáže jen úryvek, ne začátek celé zprávy. [text] musí být podřetězec
 * textu citované zprávy; [position] je podle TDLib jen PŘIBLIŽNÁ („approximate quote position"),
 * repository si přesné místo dohledá samo. Formátování úryvku (tučné, spoiler…) doplní
 * repository z původní zprávy — TDLib chce, aby v citaci zůstalo.
 */
data class ReplyQuote(val text: String, val position: Int)

/**
 * Jak má vypadat náhled odkazu v odesílané zprávě. Výchozí hodnota = nic nevybráno, rozhoduje
 * server (`linkPreviewOptions = null`).
 */
data class LinkPreviewOptions(
    /** Odeslat bez náhledu. */
    val disabled: Boolean = false,
    /** Velký obrázek (`true`), malý (`false`), nebo podle serveru (`null`). */
    val largeMedia: Boolean? = null,
    /** Náhled nad textem zprávy místo pod ním. */
    val aboveText: Boolean = false,
    /**
     * Adresa odkazu, ke kterému volba patří. **Vynucená velikost bez ní neplatí** — TDLib
     * `forceSmallMedia` i `forceLargeMedia` bez výslovné `url` ignoruje. Vyplňuje se až při
     * odeslání z náhledu, který uživatel právě vidí; držet ji v rozepsané volbě by po přepsání
     * odkazu v textu vynucovalo náhled toho starého.
     */
    val url: String? = null,
) {
    val isDefault: Boolean get() = copy(url = null) == LinkPreviewOptions()
}

/**
 * Zpráva v konverzaci.
 *
 * [text] je text zprávy, u médií popisek — může být prázdný, když má zpráva jen obrázek.
 * [media] je vyplněné jen u zpráv s přílohou.
 */
data class ChatMessage(
    val id: Long,
    val isOutgoing: Boolean,
    /** `null` u odchozích zpráv a v soukromých chatech, kde jméno nedává smysl zobrazovat. */
    val senderName: String?,
    /**
     * Profilovka odesílatele; vyplněná za stejných podmínek jako [senderName]. Bez fotky `null`,
     * pak se kreslí iniciály.
     */
    val senderAvatarFileId: Int?,
    val senderId: Long,
    val text: String,
    /**
     * Rozsahy formátování v [text] — odkazy, tučné, kód.
     *
     * Telegram markup neposílá; text je holý řetězec a tohle jsou rozsahy nad ním. Bez nich
     * je z odkazu neklikatelný text. Prázdný seznam je běžný stav, ne chyba.
     */
    val entities: List<MessageEntity>,
    val media: MessageMedia?,
    /** Náhled prvního odkazu v textu, jak ho server připravil. `null` u zpráv bez něj. */
    val linkPreview: LinkPreview?,
    val reactions: List<MessageReaction>,
    /**
     * Stav odeslání. `null` u příchozích zpráv — tam nedává smysl.
     *
     * Pozor, nezahrnuje „přečteno": to se odvozuje z [ChatInfo.lastReadOutboxMessageId], protože
     * se mění nezávisle na zprávě.
     */
    val sendState: SendState?,
    /**
     * Id zprávy, na kterou tahle odpovídá.
     *
     * Schválně jen id, ne celá citovaná zpráva: v konverzaci se odpovídá skoro vždy na něco,
     * co už je načtené, takže náhled si poskládá `ConversationViewModel` z vlastního seznamu
     * a ušetří jedno volání na každou odpověď.
     */
    val replyToMessageId: Long?,
    /**
     * Systémové oznámení o dění v chatu (někdo se připojil, změnil se název, připnutí zprávy).
     *
     * Nemá odesílatele, takže se nekreslí do bubliny, ale na střed. Reakce ani stav doručení
     * u něj nedávají smysl.
     */
    val isService: Boolean,
    /** Unixový čas v sekundách, jak ho dává TDLib. */
    val timestamp: Int,
    val isEdited: Boolean,
    /**
     * Album, do kterého zpráva patří; `0`, když do žádného.
     *
     * Telegram víc příloh v jedné zprávě nenese — album jsou samostatné zprávy (každá s vlastním id,
     * popiskem i reakcemi) spojené tímhle id. Do jedné bubliny je skládá až konverzace.
     */
    val mediaAlbumId: Long = 0L,
    /** Typ samozničení (view-once nebo časovač), nebo `null` u běžné zprávy. */
    val selfDestructType: SelfDestructType? = null,
    /**
     * Kolik vteřin zbývá do zničení, jak to hlásí TDLib v okamžiku čtení zprávy; `null`, dokud
     * odpočet neběží (zpráva ještě nebyla otevřena).
     *
     * **Tohle je autorita, ne délka časovače.** Odpočet běží od prvního otevření — i na jiném
     * zařízení nebo před restartem aplikace — a začít znovu od plné délky by obsah ukázalo déle,
     * než odesílatel dovolil.
     */
    val selfDestructIn: Int? = null,
    /** Odkud byla zpráva přeposlaná; `null` u zprávy, která přeposlaná není. */
    val forwardOrigin: ForwardOrigin? = null,
    /**
     * Kdy zprávu smaže automatické mazání chatu (epoch sekundy); `null` = nesmaže. TDLib posílá
     * zbývající čas, ten se při převodu přepočte na okamžik, aby se nepočítal od doby načtení.
     */
    val autoDeleteAt: Long? = null,
    /**
     * Jméno inline bota, přes kterého zpráva odešla (`@gif kočka` → „gif", bez zavináče); `null`
     * u zprávy napsané rovnou. U zprávy se ukazuje „přes @gif" a ťuknutí na něj dá bota do pole.
     */
    val viaBot: String? = null,
    /**
     * Efekt, se kterým zpráva odešla (konfety, srdce, oheň…); `null` = bez efektu. U zprávy je
     * vidět jako drobné emoji a animace se přehraje nad ní (viz [MessageEffect]).
     */
    val effect: MessageEffect? = null,
    /** Tlačítka bota pod zprávou; `null` = žádná. */
    val keyboard: BotKeyboard? = null,
    /** Anketa nebo kvíz; [text] pak nese jen „Anketa: otázka" pro místa, kde se kreslit nedá. */
    val poll: ChatPoll? = null,
    /**
     * Citovaný úryvek, když odpověď necituje celou zprávu, ale jen její část; `null` jinak.
     * Nad odpovědí se pak ukáže úryvek místo začátku citované zprávy.
     */
    val replyQuote: String? = null,
    /**
     * Odpověď na zprávu z JINÉHO chatu; `null` u odpovědi v rámci chatu (tu nese
     * [replyToMessageId]) i u zprávy, která neodpovídá.
     *
     * Citovaná zpráva v načtené historii není a nebude, takže se citace nedá poskládat z ní.
     * TDLib ale posílá obsah i původ citované zprávy přímo v odpovědi, takže se zástupná citace
     * skládá tady, bez dotazu navíc.
     */
    val externalReply: ExternalReply? = null,
    /** Zhlédnutí, přeposlání a komentáře; `null`, když zpráva žádné počty nenese. */
    val postStats: PostStats? = null,
    /**
     * Vlastní zpráva nese reakce, které jsme ještě neviděli. Taková zpráva se hlásí jako
     * zhlédnutá (`viewMessages`), i když je odchozí — jinak by reakce na ni zůstaly nepřečtené
     * napořád a tlačítko „❤" by nezhaslo.
     */
    val hasUnreadReactions: Boolean = false,
    /** Vlastní anketa s hlasy, které uživatel ještě neviděl (`containsUnreadPollVotes`). */
    val hasUnreadPollVotes: Boolean = false,
    /**
     * Vlákno, do kterého zpráva patří (`MessageTopicThread`) — u komentáře v diskusní skupině
     * id kořenové zprávy; `null` mimo vlákno. Podle něj si vlákno komentářů bere nové zprávy.
     */
    val threadId: Long? = null,
    /**
     * Naplánovaná zpráva: kdy odejde. `null` u běžné zprávy. Naplánovaná zpráva do historie
     * NEPATŘÍ — je jen v seznamu naplánovaných ([ConversationRepository.scheduledMessages]).
     */
    val schedule: MessageSchedule? = null,
    /**
     * Čas v médiu ([MessageEntityType.MediaTimestamp]) míří na médium TÉHLE zprávy; `false` =
     * na médium zprávy, na kterou odpovídá (`Message.hasTimestampedMedia`).
     */
    val hasTimestampedMedia: Boolean = false,
)

/**
 * Popis bota pro prázdný chat. [description] smí být prázdný, [photo] i [animation] chybět —
 * profil bota se kreslí i tak (tvář a jméno), a tlačítko Start taky.
 */
data class BotIntro(
    val description: String,
    val photo: MessageMedia.Photo? = null,
    val animation: MessageMedia.Animation? = null,
)

/** Kdy naplánovaná zpráva odejde. */
sealed interface MessageSchedule {
    /** V daný okamžik (sekundy od epochy). */
    data class At(val epochSeconds: Long) : MessageSchedule

    /** Jakmile bude protějšek online (jen soukromý chat, `MessageSchedulingStateSendWhenOnline`). */
    data object WhenOnline : MessageSchedule
}

/**
 * Jak zprávu odeslat: [silent] = protějšek nedostane zvuk ani vibraci (`disableNotification`),
 * [schedule] = neodejde hned, ale ve zvolený čas, [effectId] = efekt, který se nad zprávou
 * přehraje (0 = žádný; jen v soukromém chatu, viz [MessageEffect]).
 */
data class SendOptions(
    val silent: Boolean = false,
    val schedule: MessageSchedule? = null,
    val effectId: Long = 0,
)

/**
 * Vlákno komentářů k příspěvku kanálu. Komentáře NEJSOU v kanálu: jsou to zprávy v připojené
 * diskusní skupině ([chatId]), které odpovídají na kopii příspěvku ([rootMessageId]) — tu tam
 * server při zveřejnění sám přepošle.
 */
data class CommentThread(val chatId: Long, val rootMessageId: Long)

/**
 * Citace zprávy z jiného chatu. [quote] je zástupná zpráva jen pro kreslení citace (jméno,
 * text); [target] vede ťuknutím do chatu, kde citovaná zpráva je — `null`, když ho server
 * neprozradil (soukromá skupina, do které uživatel nemá přístup).
 */
data class ExternalReply(val quote: ChatMessage, val target: ForwardOrigin.Chat?)

/**
 * Tlačítka bota pod zprávou (inline klávesnice), po řádcích.
 *
 * Vlastní klávesnice místo systémové (`ReplyMarkupShowKeyboard`) a vynucená odpověď sem
 * nepatří — ty nejsou tlačítka zprávy, ale vstupního pole.
 */
data class BotKeyboard(val rows: List<List<BotButton>>)

data class BotButton(
    val text: String,
    val kind: BotButtonKind,
    val style: BotButtonStyle = BotButtonStyle.Default,
    /** Vlastní emoji před popiskem, jak ho bot tlačítku dal (`iconCustomEmojiId`); `null` = žádné. */
    val iconCustomEmojiId: Long? = null,
)

/** Co tlačítko udělá. Data zpětného volání tu nejsou — viz [ConversationRepository.pressBotButton]. */
sealed interface BotButtonKind {
    /** Zpětné volání botovi — kontrolní otázky, menu botů. */
    data object Callback : BotButtonKind

    /** Odkaz. Adresa na tlačítku vidět není, proto se odkaz mimo Telegram napřed potvrzuje. */
    data class Url(val url: String) : BotButtonKind

    data class CopyText(val text: String) : BotButtonKind

    /** Uživatel, na kterého tlačítko ukazuje. */
    data class User(val userId: Long) : BotButtonKind

    /** Bot tlačítko vypnul; kreslí se zašedlé a ťuknout nejde. */
    data object Disabled : BotButtonKind

    /**
     * Druh, který Plume zatím neumí. Tlačítko je VIDĚT a ťuknutí to vysvětlí — schované by
     * z klávesnice bota vykouslo řádek a změnilo, co bot nabízí.
     */
    data class Unsupported(val feature: UnsupportedBotButton) : BotButtonKind
}

enum class UnsupportedBotButton { WebApp, LoginUrl, SwitchInline, Buy, Game, Password }

/** Co bot chce od vstupního pole. */
sealed interface ChatReplyMarkup {
    /** Zpráva bota, ze které klávesnice nebo vynucená odpověď pochází. */
    val messageId: Long

    /** Nápověda do prázdného pole, kterou si bot přeje; prázdná = výchozí. */
    val placeholder: String

    /**
     * Vlastní klávesnice (`ReplyMarkupShowKeyboard`) — tlačítka posílají text nebo žádají
     * o sdílení. [oneTime] = po prvním použití zmizí.
     */
    data class Keyboard(
        override val messageId: Long,
        override val placeholder: String,
        val rows: List<List<ReplyKeyboardButton>>,
        val oneTime: Boolean,
    ) : ChatReplyMarkup

    /** Vynucená odpověď: pole se samo přepne na odpověď na tuhle zprávu. */
    data class ForceReply(
        override val messageId: Long,
        override val placeholder: String,
    ) : ChatReplyMarkup
}

data class ReplyKeyboardButton(
    val text: String,
    val kind: ReplyKeyboardButtonKind,
    val style: BotButtonStyle = BotButtonStyle.Default,
    /** Vlastní emoji před popiskem (`iconCustomEmojiId`); `null` = žádné. */
    val iconCustomEmojiId: Long? = null,
)

sealed interface ReplyKeyboardButtonKind {
    /** Pošle svůj text jako zprávu. */
    data object Text : ReplyKeyboardButtonKind

    /** Pošle vlastní telefonní číslo jako kontakt — po potvrzení, je to údaj o uživateli. */
    data object PhoneNumber : ReplyKeyboardButtonKind

    /** Otevře formulář nové ankety; bot si smí vynutit kvíz, nebo běžnou anketu. */
    data class Poll(val mode: PollComposerMode) : ReplyKeyboardButtonKind

    /** Druh, který Plume zatím neumí; tlačítko je vidět a ťuknutí to vysvětlí. */
    data class Unsupported(val feature: UnsupportedKeyboardButton) : ReplyKeyboardButtonKind
}

enum class UnsupportedKeyboardButton { Location, Users, Chat, WebApp, ManagedBot }

/** Barva, kterou si bot pro tlačítko přeje (TDLib `ButtonStyle`). */
enum class BotButtonStyle { Default, Primary, Success, Danger }

/**
 * Odpověď bota na zpětné volání. Všechno může být prázdné — bot často neodpoví nic a jen
 * přepíše zprávu nad tlačítky.
 */
data class BotAnswer(
    val text: String,
    /** Bot si přeje upozornění, které se musí potvrdit, ne jen krátkou hlášku. */
    val alert: Boolean,
    val url: String,
)

/**
 * Jak přeposlat.
 *
 * [hideSender] pošle zprávu jako KOPII: v cíli nebude „Přeposláno od …" a vypadá, jako by ji
 * napsal ten, kdo přeposílá. [hideCaptions] u kopie vynechá popisky médií; bez kopie ho TDLib
 * ignoruje, takže má smysl jen spolu s [hideSender].
 */
data class ForwardOptions(
    val hideSender: Boolean = false,
    val hideCaptions: Boolean = false,
)

/** Co jde u přeposlání nabídnout — viz [ConversationRepository.forwardCapabilities]. */
data class ForwardCapabilities(
    /** Všechny zprávy jdou zkopírovat (`canBeCopied`), takže jde skrýt odesílatele. */
    val canHideSender: Boolean = false,
    /** Aspoň jedna zpráva je médium s popiskem — jinak „skrýt popisky" nemá co skrýt. */
    val hasCaptions: Boolean = false,
)

/**
 * Původ přeposlané zprávy — co ukáže „Přeposláno od …" nad obsahem a kam vede ťuknutí na něj.
 *
 * [name] je `null`, když se jméno zjistit nepodařilo (smazaný účet, chat, který TDLib nezná);
 * značka pak řekne jen „Přeposlaná zpráva". Nepřeposlanou zprávu vydávat za nepřeposlanou by
 * bylo horší než neznat odesílatele.
 */
sealed interface ForwardOrigin {
    val name: String?

    /** Od uživatele; ťuknutí otevře soukromý chat s ním. */
    data class User(val userId: Long, override val name: String?) : ForwardOrigin

    /**
     * Od uživatele, který si v soukromí zakázal odkazovat na svůj účet při přeposílání. Server
     * pošle jen jméno, takže ťuknutí nikam nevede.
     */
    data class Hidden(override val name: String?) : ForwardOrigin

    /**
     * Z kanálu nebo za anonymního správce skupiny. [messageId] je původní příspěvek (jen
     * u kanálu), na který ťuknutí skočí; bez něj se otevře jen chat.
     */
    data class Chat(val chatId: Long, val messageId: Long?, override val name: String?) : ForwardOrigin
}

/**
 * Náhled odkazu pod textem zprávy.
 *
 * Skládá ho **server**, ne klient — Telegram stránku stáhne sám a klient dostane hotová data.
 * Nikam se proto nepřipojujeme a nic se nestahuje kromě obrázku, který k náhledu server přiložil.
 *
 * Prázdné řetězce se převádějí na `null`, aby UI nemuselo rozlišovat „chybí" a „je prázdné".
 */
data class LinkPreview(
    val url: String,
    val siteName: String?,
    val title: String?,
    val description: String?,
    val photo: MessageMedia.Photo?,
    /**
     * Obrázek přes celou šířku náhledu, ne malý čtverec vedle textu. Rozhoduje odesílatel
     * (Telegram mu to nabízí při psaní), takže se to jen respektuje.
     */
    val largePhoto: Boolean,
    /**
     * Náhled patří NAD text zprávy, ne pod něj. Rozhoduje odesílatel (Telegram mu to nabízí
     * při psaní), takže se to jen respektuje — stejně jako [largePhoto].
     */
    val showAboveText: Boolean,
    /**
     * Server má obrázek ve dvou velikostech, takže při psaní jde vybrat velký nebo malý
     * (`hasLargeMedia`). U přijaté zprávy nic neznamená.
     */
    val canChangeSize: Boolean = false,
)

/**
 * Rozmazaný náhled obrázku, který přišel rovnou se zprávou (`minithumbnail` v TDLib).
 *
 * Malý JPEG, zhruba 40 px na delší straně, přímo v datech zprávy — nic se nestahuje, takže je
 * k dispozici v prvním snímku, dřív než jakýkoli soubor. Místo šedé plochy ukáže barvy a obrys
 * snímku, než dorazí náhled nebo ostrá verze.
 *
 * **Není to data class kvůli `equals`.** `ByteArray` se v data class porovnává podle
 * reference, takže by každé nové namapování téže zprávy vypadalo jako změna a seznam by
 * přestal přeskakovat nezměněné položky.
 */
class Minithumbnail(val jpeg: ByteArray, val width: Int, val height: Int) {
    override fun equals(other: Any?) = other is Minithumbnail &&
        width == other.width && height == other.height && jpeg.contentEquals(other.jpeg)

    override fun hashCode() = 31 * (31 * width + height) + jpeg.contentHashCode()
}

/**
 * Reakce u zprávy.
 *
 * Obyčejné i vlastní emoji; [emoji] je klíč reakce (viz [customEmojiReaction]), vlastní emoji
 * se kreslí jako obrázek. Placená reakce (hvězdičky) se přeskočí, aby zbytek reakcí u zprávy
 * nezmizel.
 */
data class MessageReaction(
    /** Klíč reakce — obyčejné emoji, nebo [customEmojiReaction]. */
    val emoji: String,
    val count: Int,
    /** `true`, když tuhle reakci dal přihlášený uživatel. */
    val isChosen: Boolean,
)

/**
 * Co všechno jde s jednou zprávou udělat.
 *
 * Rozhoduje o tom server, ne klient: smazat cizí zprávu jde jen s právy, editovat jen chvíli po
 * odeslání a v kanálu s chráněným obsahem se nesmí ani kopírovat text. Nabídka nad zprávou podle
 * toho **skrývá položky**, místo aby je nabídla a pak ohlásila chybu.
 */
data class MessageActions(
    val canReply: Boolean,
    /**
     * Zkopírování textu do schránky.
     *
     * Vychází z `canBeSaved`: kanál si může obsah chránit a pak se z něj nesmí nic vynášet.
     * Samotná přítomnost textu je věc navíc, kterou si řeší nabídka.
     */
    val canCopy: Boolean,
    val canDeleteForSelf: Boolean,
    /** Smazat i protějšku. U skupin a kanálů je to jediná možnost, jak mazat. */
    val canDeleteForAll: Boolean,
    /** Úprava textu. Server ji povolí jen u vlastních zpráv a jen po omezenou dobu od odeslání. */
    val canEdit: Boolean,
    /**
     * Připnutí i odepnutí (`canBePinned`). Ve skupině a kanálu je to právo, v soukromém chatu
     * ho má každý.
     */
    val canPin: Boolean,
    /** Přeposlání jinam. Kanál s chráněným obsahem ho zakazuje, stejně jako kopírování. */
    val canForward: Boolean,
    /** Uložení média do telefonu. Tatáž ochrana obsahu (`canBeSaved`) jako u kopírování. */
    val canSave: Boolean,
    /** Nahlášení zprávy moderátorům (`canReportChat`) — průvodce `reportChat` s touhle zprávou. */
    val canReport: Boolean = false,
) {
    companion object {
        /**
         * Co se nabídne, když se příznaky nepodaří zjistit.
         *
         * Odpověď a kopírování jsou neškodné — nejhorší, co se stane, je chybová hláška.
         * Mazání se nenabídne: nabídnout nevratnou akci, o které nevíme, jestli je povolená,
         * je horší než ji nenabídnout vůbec.
         */
        val Fallback = MessageActions(
            canReply = true,
            canCopy = true,
            canDeleteForSelf = false,
            canDeleteForAll = false,
            // Úprava po lhůtě skončí chybou; bez příznaků se nenabízí, stejně jako mazání.
            canEdit = false,
            // Ve skupině bez práv by skončilo chybou; nevíme, tak se nenabízí.
            canPin = false,
            // Přeposlání chráněného obsahu server odmítne; bez příznaků se nenabízí.
            canForward = false,
            canSave = false,
        )
    }
}

/** Ten, kdo reagoval. Uživatel i kanál — u příspěvků v kanálech reaguje jménem kanálu. */
/**
 * Jeden řádek detailu reakcí: kdo a čím reagoval.
 *
 * Čipy u zprávy nesou jen emoji a počet, takže tohle je jediné místo, kde se dá zjistit KDO
 * reagoval. Načítá se až při otevření sheetu — u příspěvku se stovkou reakcí by to jinak byla
 * hromada dotazů na data, která nikdo neuvidí.
 */
data class ReactionDetail(
    val sender: SenderInfo,
    val emoji: String,
    /** `true` u vlastní reakce; v seznamu se místo jména vypíše „Vy". */
    val isOwn: Boolean,
)

data class SenderInfo(
    val id: Long,
    val title: String,
    val avatarFileId: Int?,
)

/**
 * Podrobnosti o zprávě pro sheet „Informace o zprávě".
 *
 * Skládá se z toho, co server o zprávě vydá **na vyžádání** — kdy ji protějšek přečetl, kdo ji
 * zhlédl a odkaz na ni. Každá z těch tří věcí má vlastní právo (`canGetReadDate`, `canGetViewers`,
 * `canGetLink`) a většina zpráv nemá ani jedno, takže se nic z toho nenačítá dopředu.
 */
data class MessageInfo(
    /** Kdy zpráva odešla. */
    val sentAt: Int,
    /** Kdy byla naposledy upravená; `null`, když upravená nebyla. */
    val editedAt: Int?,
    val readState: MessageReadState,
    /** Kdo zprávu zhlédl, od nejnovějšího. Prázdné, když to server nevydá. */
    val viewers: List<MessageViewer>,
    /** Odkaz na zprávu; `null`, když ho chat nemá (soukromé chaty a neveřejné skupiny). */
    val link: String?,
)

/** Kdo a kdy zprávu zhlédl. */
data class MessageViewer(
    val sender: SenderInfo,
    val viewedAt: Int,
)

/**
 * Kdy protějšek zprávu přečetl.
 *
 * Telegram to **záměrně nevydá vždycky**: čas čtení se schová podle nastavení soukromí (obou
 * stran) a u starých zpráv ho server nedrží. Každý ten případ je jiná věta pro uživatele, takže
 * je to pojmenovaný stav, ne `Int?`.
 */
sealed interface MessageReadState {
    data class Read(val date: Int) : MessageReadState
    data object Unread : MessageReadState
    /** Server už čas nedrží — u starších zpráv běžné. */
    data object TooOld : MessageReadState
    /** Protějšek má časy čtení skryté. */
    data object HiddenByOther : MessageReadState
    /** Skryté vlastní časy čtení znamenají, že je nevidím ani já. */
    data object HiddenByMe : MessageReadState
    /** Chat čas čtení nenabízí (skupina, kanál, vlastní zpráva bez práva). */
    data object Unavailable : MessageReadState
}

/** Kolik řádků detailu reakcí se načte. Víc než obrazovku a půl nikdo nepročítá. */
const val REACTION_DETAIL_LIMIT = 50

/** Kompletní sada reakcí jedné zprávy, jak dorazila updatem. */
/**
 * Co se u zprávy mění samo, bez úpravy obsahu: reakce a počty příspěvku. TDLib obojí posílá
 * jedním updatem (`UpdateMessageInteractionInfo`), proto jeden typ.
 */
data class MessageInteractions(
    val messageId: Long,
    val reactions: List<MessageReaction>,
    val postStats: PostStats?,
)

/**
 * Počty u příspěvku kanálu: zhlédnutí, přeposlání a komentáře.
 *
 * [comments] je `null`, když příspěvek diskusi nemá (kanál bez připojené skupiny); nula znamená
 * diskusi bez komentářů. Server komentáře počítá i u zpráv v diskusní skupině samotné, takže
 * o tom, jestli se ukážou, rozhoduje až konverzace podle druhu chatu.
 */
data class PostStats(val views: Int, val forwards: Int, val comments: Int?) {
    val isEmpty: Boolean get() = views == 0 && forwards == 0 && comments == null
}

/** Výsledek odeslání zprávy, jak ho oznámí TDLib. */
sealed interface SentMessageUpdate {

    /** [oldMessageId] je dočasné id, pod kterým zpráva vznikla; [message] má už skutečné. */
    data class Succeeded(val oldMessageId: Long, val message: ChatMessage) : SentMessageUpdate

    data class Failed(val oldMessageId: Long, val message: ChatMessage) : SentMessageUpdate
}

/**
 * Stav odeslání odchozí zprávy.
 *
 * Čtvrtý stav — „přečteno" — tu schválně není. Nepatří zprávě, ale chatu
 * ([ChatInfo.lastReadOutboxMessageId]); kdyby byl tady, musely by se při každém přečtení
 * přemapovat všechny zprávy v historii.
 */
enum class SendState {
    /** Odesílá se; server ještě nepotvrdil. */
    Sending,

    /** Doručeno na server. */
    Sent,

    Failed,
}

/** Typ samozničení zprávy (view-once nebo časovač). */
sealed interface SelfDestructType {
    /** Jednorázové zhlédnutí: po zavření zaniká. */
    data object Immediately : SelfDestructType

    /** Odpočet po zadaný počet sekund od otevření. */
    data class Timer(val seconds: Int) : SelfDestructType
}

/** Příloha zprávy. */
sealed interface MessageMedia {

    /**
     * Fotka vykreslovaná přímo v bublině.
     *
     * [thumbnailFileId] je nejmenší varianta (TDLib jich drží několik) — stáhne se v řádu
     * milisekund a poslouží jako okamžitý náhled, než dojde [fileId]. [width] a [height]
     * jsou rozměry zobrazované varianty; drží se kvůli poměru stran, aby bublina neposkočila,
     * až se obrázek načte.
     */
    data class Photo(
        val fileId: Int,
        val thumbnailFileId: Int?,
        /**
         * Největší varianta, kterou server nabízí. Patří do prohlížeče, ne do bubliny.
         *
         * V bublině by to byly desítky megabajtů kvůli obrázku širokému pár set bodů; stahuje se
         * proto až při otevření. Když je fotka malá, je to tentýž soubor jako [fileId] — pak
         * se nestahuje nic navíc.
         */
        val fullFileId: Int,
        val width: Int,
        val height: Int,
        val hasSpoiler: Boolean = false,
        /** Skryté kvůli citlivému obsahu, ne volbou odesílatele — viz [SensitiveContent]. */
        val isSensitive: Boolean = false,
        val isSecret: Boolean = false,
        val selfDestructType: SelfDestructType? = null,
        val minithumbnail: Minithumbnail? = null,
    ) : MessageMedia

    /**
     * Video: v bublině náhled s délkou, přehrává se v prohlížeči médií.
     *
     * [fileId] je samotné video (stahuje se až v prohlížeči; u odesílaného nese průběh nahrávání),
     * [thumbnailFileId] náhledový obrázek. Rozměry kvůli poměru stran bubliny.
     */
    data class Video(
        val fileId: Int,
        val thumbnailFileId: Int?,
        val width: Int,
        val height: Int,
        val durationSeconds: Int,
        val hasSpoiler: Boolean = false,
        /** Skryté kvůli citlivému obsahu, ne volbou odesílatele — viz [SensitiveContent]. */
        val isSensitive: Boolean = false,
        val isSecret: Boolean = false,
        val selfDestructType: SelfDestructType? = null,
        /**
         * Video se dá přehrávat během stahování.
         *
         * Říká to server (`Video.supportsStreaming`) a znamená to, že **index je na začátku
         * souboru** — bez něj přehrávač z rozestavěného souboru nepozná nic a musí se počkat
         * na celý. Odesílatel to ovlivňuje tím, jak soubor uložil (viz `streamable`
         * u odesílané přílohy).
         */
        val supportsStreaming: Boolean = false,
        val minithumbnail: Minithumbnail? = null,
    ) : MessageMedia

    /**
     * GIF — v Telegramu je to MP4 bez zvuku (`MessageAnimation`), ne skutečný GIF.
     *
     * Hraje v bublině ve smyčce, jakmile je vidět: GIF, který se nehýbe, není GIF. Stahuje se
     * proto sám, ale jen do [MAX_AUTOPLAY_ANIMATION_BYTES] — velký soubor by bez ptaní snědl
     * data, takže nad stropem se čeká na ťuknutí.
     *
     * [sizeBytes] je velikost na serveru (`expectedSize`), známá dřív, než soubor dorazí.
     * Rozměry kvůli poměru stran bubliny.
     */
    data class Animation(
        val fileId: Int,
        val thumbnailFileId: Int?,
        val width: Int,
        val height: Int,
        val sizeBytes: Long,
        val minithumbnail: Minithumbnail? = null,
        /** Skrytý do ťuknutí, stejně jako fotka se spoilerem — a do té doby NEHRAJE. */
        val hasSpoiler: Boolean = false,
        /** Skrytý kvůli citlivému obsahu, ne volbou odesílatele — viz [SensitiveContent]. */
        val isSensitive: Boolean = false,
    ) : MessageMedia {
        /** Pustí se samo, nebo až na ťuknutí. */
        val autoPlays: Boolean get() = sizeBytes in 1..MAX_AUTOPLAY_ANIMATION_BYTES
    }

    /**
     * Video kolečko (`MessageVideoNote`): čtvercové video bez okolí, kreslené v kruhu.
     *
     * Hraje samo ve smyčce a ztlumeně, jako GIF ([autoPlays] — stejný strop velikosti); zvuk až
     * na ťuknutí, jako hlasovka. [isViewed] je obdoba „poslechnuto" u hlasovky: příchozí kolečko
     * se po přehrání se zvukem hlásí jako zhlédnuté (`openMessageContent`).
     */
    data class VideoNote(
        val fileId: Int,
        val thumbnailFileId: Int?,
        val durationSeconds: Int,
        val sizeBytes: Long,
        val isViewed: Boolean,
        val minithumbnail: Minithumbnail? = null,
    ) : MessageMedia {
        val autoPlays: Boolean get() = sizeBytes in 1..MAX_AUTOPLAY_ANIMATION_BYTES
    }

    /**
     * Soubor: v bublině název a velikost, ťuknutí ho stáhne a otevře systémem.
     *
     * [mimeType] posílá odesílatel, takže se na něj nedá spoléhat — systém si typ často odvodí
     * sám z přípony. [sizeBytes] je velikost na serveru (`expectedSize`), aby šla ukázat dřív,
     * než se soubor stáhne.
     */
    data class Document(
        val fileId: Int,
        val fileName: String,
        val mimeType: String,
        val sizeBytes: Long,
        val thumbnailFileId: Int?,
        /**
         * Rozměry náhledu, nula bez něj. TDLib u dokumentu rozměry souboru nedává; poměr stran
         * náhledu je jediný, který známe, a prohlížeč podle něj staví kartu.
         */
        val thumbnailWidth: Int = 0,
        val thumbnailHeight: Int = 0,
    ) : MessageMedia

    /**
     * Hudba nebo jiný zvuk (`MessageAudio`): přehrává se v bublině, tímtéž přehrávačem jako
     * hlasovka — hraje tedy vždycky nejvýš jeden zvuk v aplikaci.
     *
     * [title] je název skladby, bez něj název souboru, bez obojího obecný popis; [performer]
     * `null`, když ho soubor nenese. [coverFileId] je obal alba (náhled od serveru).
     */
    data class Audio(
        val fileId: Int,
        val title: String,
        val performer: String?,
        val fileName: String,
        val mimeType: String,
        val durationSeconds: Int,
        val sizeBytes: Long,
        val coverFileId: Int? = null,
        val coverMinithumbnail: Minithumbnail? = null,
    ) : MessageMedia {
        /** Jako soubor — sdílení a „Uložit jako…" jdou stejnou cestou jako u dokumentu. */
        fun asDocument(): Document = Document(
            fileId = fileId,
            fileName = fileName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            thumbnailFileId = null,
        )
    }

    /**
     * Hlasovka: přehrává se přímo v bublině.
     *
     * [waveform] jsou hodnoty 0–31 rozbalené z Telegramu (viz `decodeWaveform`). [isListened]
     * u příchozí znamená, že jsme ji pustili; u odchozí, že si ji pustil protějšek.
     */
    data class VoiceNote(
        val fileId: Int,
        val durationSeconds: Int,
        val waveform: List<Int>,
        val isListened: Boolean,
    ) : MessageMedia

    /**
     * Ostatní přílohy — video, GIF, dokument.
     *
     * Zatím se nepřehrávají ani neotevírají; ukáže se náhledový obrázek (pokud ho zpráva má)
     * a popisek typu. Vykreslování a přehrávání je samostatná fáze.
     */
    data class Attachment(
        val label: String,
        val thumbnailFileId: Int?,
        /** Soubor samotný, pokud ho známe (dokument) — kvůli průběhu nahrávání odesílané zprávy. */
        val fileId: Int? = null,
    ) : MessageMedia

    /**
     * Samolepka.
     *
     * [fileId] je vyplněné jen u statického `.webp`, [animationFileId] jen u `.tgs` (gzipovaná
     * Lottie), kterou přehrává Compottie. Samolepky `.webm` jsou video s průhledností a přehrát
     * je zatím neumíme; zbývá u nich [thumbnailFileId] (statický náhled) a teprve když není ani
     * ten, [emoji]. Náhled slouží i u `.tgs`, než se animace načte.
     *
     * [width] a [height] jsou od odesílatele; drží se kvůli poměru stran, aby se samolepka
     * nevykreslila jako čtverec.
     */
    /**
     * Samotné emoji s animovanou podobou (`MessageAnimatedEmoji`).
     *
     * Jen když je animace `.tgs`, kterou umíme přehrát. Jinak zpráva zůstává textová a kreslí se
     * velkým unicode znakem. [emoji] je ten znak — slouží jako zástupce, než se animace načte,
     * a jako text všude, kde animace být nemůže (citace, kopírování, náhled v seznamu).
     */
    data class AnimatedEmoji(
        val animationFileId: Int,
        val emoji: String,
    ) : MessageMedia

    /**
     * Hod kostkou, šipkami, míčem nebo automatem (`MessageDice`).
     *
     * Hod je animace, kterou posílá Telegram, ne naše kresba: [layers] jsou soubory `.tgs`
     * kreslené přes sebe. Kostka má jednu vrstvu, automat pět (pozadí, páka a tři válce),
     * a výsledek je v nich už nakreslený — koncový snímek ukazuje, co padlo.
     *
     * [value] `0` = výsledek od serveru ještě nedorazil ([rolling]); vrstvy jsou pak úvodní
     * animace, která se točí, dokud nepřijde výsledná.
     */
    data class Dice(
        val emoji: String,
        val value: Int,
        val layers: List<Int>,
    ) : MessageMedia {
        val rolling: Boolean get() = value == 0

        /** Hod slovy („🎲 6“) — pro citaci, schránku a všude, kde animace být nemůže. */
        val label: String get() = if (rolling) emoji else "$emoji $value"
    }

    data class Sticker(
        /** Soubor samolepky v jakémkoli formátu. Podle něj se samolepka posílá i dává do oblíbených. */
        val stickerFileId: Int,
        /** Sada, do které samolepka patří; `0`, když do žádné nepatří. */
        val setId: Long,
        val fileId: Int?,
        val animationFileId: Int?,
        val thumbnailFileId: Int?,
        val width: Int,
        val height: Int,
        val emoji: String,
    ) : MessageMedia

    /**
     * Poloha, místo (`MessageVenue`) nebo živá poloha. Kreslí se statickou mapou od Telegramu
     * (`FileRepository.mapThumbnail`), ťuknutí otevře systémové mapy.
     *
     * [title] a [address] má jen místo. Živá poloha nese [liveUntil]: konec sdílení v sekundách
     * od epochy, [Long.MAX_VALUE] u sdílení „navždy", `0` u obyčejné polohy. Počítá se z data
     * zprávy a `livePeriod` — `expiresIn` TDLib změnou neposílá, takže by zastaral.
     */
    data class Location(
        val latitude: Double,
        val longitude: Double,
        val title: String? = null,
        val address: String? = null,
        val liveUntil: Long = 0,
    ) : MessageMedia {
        val isLive: Boolean get() = liveUntil != 0L
    }

    /**
     * Záznam o hovoru (`MessageCall`): jak dopadl, jestli to byl videohovor a jak dlouho se
     * mluvilo. Kreslí se jako řádek v bublině, ne jako holé „Hovor" — směr a zmeškání jsou
     * jediné, co na té zprávě uživatele zajímá. Hovory samotné Plume neumí, ťuknutí tedy nikam
     * nevede.
     */
    data class Call(
        val outcome: CallOutcome,
        val isVideo: Boolean,
        /** Nula u hovoru, ve kterém se nemluvilo (zmeškaný, zrušený). */
        val durationSeconds: Int,
    ) : MessageMedia

    /**
     * Sdílený kontakt (`MessageContact`): karta se jménem a číslem. [userId] je účet na
     * Telegramu, kterému číslo patří — `null`, když na Telegramu není (pak nejde napsat,
     * zbývá jen číslo). [photoFileId] je jeho profilovka, když ji TDLib zná.
     */
    data class Contact(
        val name: String,
        /** Číslo i s „+", jak se ukazuje. */
        val phone: String,
        val userId: Long?,
        val photoFileId: Int? = null,
    ) : MessageMedia
}

/** Přístup k chatu, jak se mění za běhu — viz [ConversationRepository.accessUpdates]. */
/**
 * Druh sdíleného obsahu v profilu chatu. Každý je vlastní filtr `searchChatMessages` — hledání
 * s filtrem, ne procházení historie: sdíleného obsahu bývá zlomek zpráv.
 */
enum class SharedMediaKind {
    /** Fotky a videa; mizející a tajná se vyřazují stejně jako v prohlížeči. */
    Media,

    /** Soubory poslané jako dokument. */
    Files,

    /** Zprávy s odkazem — filtruje je server, ne my. */
    Links,
}

/**
 * Stránka sdíleného obsahu. [reachedEnd] pozná jen prázdná odpověď, stejně jako konec historie —
 * TDLib žádný příznak „jsi na konci" nedává.
 */
data class SharedMediaPage(val messages: List<ChatMessage>, val reachedEnd: Boolean)

/** Kolik čeho v chatu je; podle toho profil řádek ukáže, nebo ne. */
data class SharedMediaCounts(val media: Int, val files: Int, val links: Int) {
    fun of(kind: SharedMediaKind): Int = when (kind) {
        SharedMediaKind.Media -> media
        SharedMediaKind.Files -> files
        SharedMediaKind.Links -> links
    }

    companion object {
        val Empty = SharedMediaCounts(media = 0, files = 0, links = 0)
    }
}

data class ChatAccess(
    val canJoin: Boolean,
    val writeAccess: WriteAccess,
    /** Co se smí poslat, když se smí psát. Viz [ContentAccess]. */
    val content: ContentAccess = ContentAccess.All,
)

/**
 * Do jaké velikosti se GIF stáhne sám.
 *
 * GIFy z Telegramu bývají pod megabajt, ale limit serveru je mnohem výš. Bez stropu by
 * autoplay stáhl bez ptaní i desetimegabajtový soubor — nad ním se proto čeká na ťuknutí.
 */
const val MAX_AUTOPLAY_ANIMATION_BYTES: Long = 5L * 1024 * 1024

/**
 * Efekt zprávy — animace, se kterou odesílatel zprávu poslal (`Message.effectId`).
 *
 * [emoji] je značka u zprávy; [animationFileId] je `.tgs`, který se přehraje nad ní. `null`, když
 * animace není v `.tgs` (Compottie jiný formát neumí) — pak zůstane jen značka.
 *
 * [premium] = efekt je prémiová samolepka (její „prémiová animace"), ne efekt reakce. Kreslí se
 * jinak velký: animace samolepky je navržená kolem samolepky, efekt reakce kolem malého emoji.
 */
data class MessageEffect(
    val id: Long,
    val emoji: String,
    val animationFileId: Int?,
    val premium: Boolean,
    /** Poslat ho smí jen účet s Premium (`MessageEffect.isPremium`); zobrazit ho vidí každý. */
    val needsPremium: Boolean = false,
)
