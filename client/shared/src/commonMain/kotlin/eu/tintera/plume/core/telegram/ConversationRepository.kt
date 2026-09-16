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
     * Načte [limit] zpráv **starších** než [fromMessageId].
     *
     * `fromMessageId = 0` znamená „od nejnovější". Prázdný výsledek znamená konec historie —
     * TDLib nevrací žádný explicitní příznak, pozná se to jen podle toho, že nic nepřišlo.
     */
    suspend fun history(chatId: Long, fromMessageId: Long, limit: Int = 30): Result<List<ChatMessage>>

    /** Nové zprávy v tomhle chatu, jak přicházejí. */
    fun newMessages(chatId: Long): Flow<ChatMessage>

    /** Id smazaných zpráv. */
    fun deletedMessages(chatId: Long): Flow<List<Long>>

    /** Zprávy, jimž se změnil obsah (editace). */
    fun editedMessages(chatId: Long): Flow<ChatMessage>

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
     * Co se dá s konkrétní zprávou udělat.
     *
     * Ptá se **až při otevření nabídky**, ne při načtení historie. TDLib tyhle příznaky ve zprávě
     * neposílá — jsou za samostatným `getMessageProperties`, takže načítat je dopředu by znamenalo
     * jeden dotaz na každou zprávu v historii kvůli nabídce, kterou uživatel skoro nikdy neotevře.
     */
    suspend fun messageActions(chatId: Long, messageId: Long): Result<MessageActions>

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
     * Zprávy s fotkou kolem [messageId] (včetně ní), od nejstarší. Pro listování v prohlížeči.
     *
     * [older] a [newer] jsou horní meze — na začátku a konci chatu jich přijde míň. Konec se
     * pozná podle toho, že daným směrem nepřibyla žádná nová zpráva.
     */
    suspend fun photos(chatId: Long, messageId: Long, older: Int, newer: Int): Result<List<ChatMessage>>

    /** Kolikátá fotka v chatu je [messageId] a kolik jich je celkem, nebo `null`, když to server neví. */
    suspend fun photoPosition(chatId: Long, messageId: Long): Result<PhotoPosition?>

    /** Jedna konkrétní zpráva. Pro doplnění citace, která není v načtené historii. */
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
     * Odešle přílohy z disku zařízení, s popiskem. Jedna příloha jde jako obyčejná zpráva, víc jako
     * **album** (`sendMessageAlbum`, 2–10) — Telegram víc příloh v jedné zprávě nenese.
     *
     * Stejně jako [send] vrací rozepsané zprávy okamžitě; nahrávání běží na pozadí a jeho průběh
     * hlásí [FileRepository.uploadProgress]. [caption] patří první příloze, jak to dělá Telegram;
     * prázdný popisek se nepošle.
     */
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
     * Uloží rozepsanou zprávu do TDLib, odkud ji vidí seznam chatů i ostatní zařízení.
     *
     * Prázdný [text] koncept **smaže** — i kdyby zbývala citace. Odpověď bez textu Telegram jako
     * koncept nedrží a ponechat ji by znamenalo koncept, který v seznamu nic neukazuje.
     */
    suspend fun saveDraft(chatId: Long, text: String, replyToMessageId: Long?): Result<Unit>

    /**
     * Sponzorované zprávy pro daný chat. Mimo kanály vrací prázdný seznam.
     *
     * Zobrazení je podle Telegram ToS **povinné** — výsledek se nesmí filtrovat ani skrývat.
     */
    suspend fun sponsoredMessages(chatId: Long): Result<List<SponsoredMessage>>

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
sealed interface OutgoingAttachment {
    val path: String

    /**
     * Fotka jako fotka (s náhledem v bublině). [width] a [height] jsou rozměry připraveného souboru;
     * server je může přepsat, ale bez nich by bublina odesílané zprávy neměla poměr stran.
     */
    data class Photo(override val path: String, val width: Int, val height: Int) : OutgoingAttachment

    /** Cokoli jako soubor, beze změny. Název souboru se bere z cesty. */
    data class Document(override val path: String) : OutgoingAttachment
}

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
    /**
     * Několik posledních odesílatelů, jak je dává TDLib (`recentSenderIds`).
     *
     * Je jich **nejvýš pár, ne všichni** — u příspěvku se stovkou reakcí Telegram kompletní
     * seznam v datech zprávy neposílá. Proto se podle délky tohohle seznamu nedá poznat
     * celkový počet; ten je v [count].
     */
    val recentSenders: List<ReactionSender>,
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
        )
    }
}

/** Ten, kdo reagoval. Uživatel i kanál — u příspěvků v kanálech reaguje jménem kanálu. */
data class ReactionSender(
    val id: Long,
    val title: String,
    val avatarFileId: Int?,
)

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
    ) : MessageMedia

    /**
     * Ostatní přílohy — video, GIF, dokument, hlasovka.
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
