package eu.tintera.plume.core.telegram

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

    /** Nové zprávy v tomhle chatu, jak přicházejí. */
    fun newMessages(chatId: Long): Flow<ChatMessage>

    /** Id smazaných zpráv. */
    fun deletedMessages(chatId: Long): Flow<List<Long>>

    /** Zprávy, jimž se změnil obsah (editace). */
    fun editedMessages(chatId: Long): Flow<ChatMessage>

    /**
     * Id zpráv, jejichž obsah někdo otevřel — u hlasovky „poslechnuto". Chodí i pro naše
     * odchozí, když si je pustí protějšek.
     */
    fun contentOpenedUpdates(chatId: Long): Flow<Long>

    /** Nahlásí, že uživatel obsah zprávy otevřel (pustil hlasovku). */
    suspend fun openContent(chatId: Long, messageId: Long): Result<Unit>

    /** Reakce, které se u zprávy změnily. Klíč je `messageId`. */
    fun reactionUpdates(chatId: Long): Flow<MessageReactions>

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
    suspend fun forward(fromChatId: Long, messageIds: List<Long>, toChatId: Long): Result<Unit>

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
     * [replyToMessageId] je `null` u běžné zprávy.
     */
    suspend fun send(chatId: Long, text: String, replyToMessageId: Long?): Result<ChatMessage>

    /**
     * Odešle samolepku. Stejně jako [send] vrací rozepsanou zprávu okamžitě.
     *
     * Samolepka se zároveň zařadí mezi nedávné, aby byla ve výběru příště nahoře.
     */
    suspend fun sendSticker(chatId: Long, sticker: StickerItem, replyToMessageId: Long?): Result<ChatMessage>

    /**
     * Odešle GIF (z klávesnice) jako animaci, ne jako fotku — z fotky by zbyl první snímek.
     * Skutečný GIF převede na MP4 server sám. Vrací rozepsanou zprávu hned jako [send].
     */
    suspend fun sendAnimation(chatId: Long, animation: OutgoingAnimation, replyToMessageId: Long?): Result<ChatMessage>

    /**
     * Odešle GIF z výběru — soubor, který na serveru už je, takže se nic nenahrává. GIF se
     * zároveň zařadí na začátek uložených, jako samolepka mezi nedávné.
     */
    suspend fun sendGif(chatId: Long, gif: GifItem, replyToMessageId: Long?): Result<ChatMessage>

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
    ): Result<ChatMessage>

    suspend fun sendAttachments(
        chatId: Long,
        attachments: List<OutgoingAttachment>,
        caption: String,
        replyToMessageId: Long?,
    ): Result<List<ChatMessage>>

    /**
     * Nahradí text odeslané zprávy.
     *
     * Výsledek se do konverzace dostane updatem (`editedMessages`), stejně jako cizí úpravy —
     * návratová hodnota se proto nevrací. Entity se dohledávají jako při odeslání.
     */
    suspend fun editText(chatId: Long, messageId: Long, text: String): Result<Unit>

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

    suspend fun chatInfo(chatId: Long): Result<ChatInfo>

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
    suspend fun saveDraft(chatId: Long, text: String, replyToMessageId: Long?): Result<Unit>

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
)

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
)

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
)

/**
 * Reakce u zprávy.
 *
 * Zatím jen **standardní emoji**. Telegram umí i vlastní (placená) emoji, ta jsou ale samolepky —
 * vykreslují se jako stažitelný soubor a mají vlastní animaci, takže patří k médiím, ne sem.
 * Neznámé typy se prostě přeskočí, aby zbytek reakcí u zprávy nezmizel.
 */
data class MessageReaction(
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
data class MessageReactions(
    val messageId: Long,
    val reactions: List<MessageReaction>,
)

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
        val isSecret: Boolean = false,
        val selfDestructType: SelfDestructType? = null,
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
    ) : MessageMedia {
        /** Pustí se samo, nebo až na ťuknutí. */
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
