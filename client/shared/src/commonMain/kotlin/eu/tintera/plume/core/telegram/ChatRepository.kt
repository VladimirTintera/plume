package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration
import kotlinx.coroutines.flow.StateFlow
import eu.tintera.plume.core.ui.UiText

/**
 * Seznamy chatů (hlavní a archiv), živě aktualizované.
 *
 * Doménová hranice — implementace nad TDLib je [TdlChatRepository]. Seznam není stránkovaný
 * v klasickém smyslu „načti stránku a vrať ji": TDLib drží lokální databázi a **posílá změny
 * updaty**. [loadMore] jen řekne TDLib, ať dotáhne další kus ze serveru; výsledek se objeví
 * v [chats] sám, až updaty dorazí.
 */
interface ChatRepository {

    /**
     * Chaty jedné složky. Archiv se začne načítat až prvním [loadMore] — do té doby je
     * [ChatListState.Loading]; hlavní seznam se načítá sám po přihlášení.
     */
    fun chats(folder: ChatFolder = ChatFolder.Main): StateFlow<ChatListState>

    /**
     * Co se v chatu zrovna děje („píše…") a kdo to dělá, bez ohledu na to, ve které složce chat
     * je — i v chatu, který není v žádné (otevřený z notifikace, z odkazu). `null`, když nic.
     */
    fun typing(chatId: Long): Flow<ChatTyping?>

    /**
     * Id chatu Uložené zprávy; `null`, dokud není přihlášeno.
     *
     * Uložené zprávy jsou v Telegramu obyčejný soukromý chat s vlastním účtem, a id soukromého
     * chatu je id uživatele — takže je to id přihlášeného účtu. Kreslí se ale jinak (vlastní
     * název a ikona, ne jméno a fotka uživatele) a v seznamu chatů nejsou, pokud nejsou připnuté;
     * otevírají se z menu v horní liště.
     */
    val savedMessagesChatId: StateFlow<Long?>

    /**
     * Emoji, které odejdou jako hod (kostka, šipky, míč…), když je zpráva složená jen z jednoho
     * z nich. Seznam posílá server (`UpdateDiceEmojis`) hned po startu klienta a jen jednou,
     * proto ho drží eager repository chatů, ne konverzace, která vzniká až s obrazovkou.
     */
    val diceEmojis: StateFlow<List<String>>

    /**
     * Efekty, se kterými jde poslat zprávu (nejdřív efekty reakcí, pak prémiových samolepek).
     * Stejně jako [diceEmojis] je server pošle jednou po startu (`UpdateAvailableMessageEffects`),
     * proto je drží eager repository chatů.
     */
    val availableEffectIds: StateFlow<List<Long>>

    /**
     * Připraví chat Uložené zprávy k otevření a vrátí jeho id. TDLib ho zná, až když ho
     * vytvoří `createPrivateChat` — u účtu, který si nic neuložil, v databázi být nemusí.
     */
    suspend fun openSavedMessages(): Result<Long>

    /**
     * Co znamená odkaz ze zprávy. `null` = odkaz není interní, nebo je to druh, který aplikace
     * zatím neumí ukázat — ten patří do prohlížeče. Selhání = odkaz je interní, ale jeho cíl
     * se nenašel (neexistující jméno, neplatná pozvánka).
     */
    suspend fun resolveLink(url: String): Result<InternalLink?>

    /** Přidá se do chatu přes pozvánku ([InternalLink.Invite]). */
    suspend fun joinByInvite(link: String): Result<InviteJoinResult>

    /** Chat veřejného uživatele, skupiny nebo kanálu podle uživatelského jména (bez `@`). */
    suspend fun chatByUsername(username: String): Result<Long>

    /**
     * Soukromý chat s uživatelem. TDLib ho zná, až když ho vytvoří `createPrivateChat` (lokálně,
     * nic se neposílá) — u zmínky uživatele, se kterým se ještě nepsalo, v databázi není.
     */
    suspend fun privateChat(userId: Long): Result<Long>

    /**
     * Kontakty uživatele seřazené podle jména, s dostupností protějšku.
     * Pro každý kontakt zajistí privátní chat (lokálně v TDLib).
     */
    suspend fun contacts(): Result<List<ChatSummary>>

    /**
     * Chaty podle názvu a uživatelského jména, ze VŠECH známých — i archivované a chaty mimo
     * složky, na rozdíl od filtru nad [chats]. Bez [onServer] se hledá jen v lokální databázi
     * (okamžitě), s ním se ptá serveru, který zná i chaty, jež lokálně ještě nejsou.
     *
     * Pořadí dává TDLib (jako v hlavním seznamu). Uložené zprávy se najdou pod jménem účtu, ne
     * pod svým názvem — volající je proto hledá podle přeloženého názvu sám.
     */
    suspend fun searchChats(query: String, onServer: Boolean = false): Result<List<ChatSummary>>

    /**
     * Veřejné kanály, skupiny a uživatelé, které uživatel ještě nesleduje („globální hledání").
     * Hledá server v uživatelských jménech a názvech; chaty, které uživatel už má, vynechává.
     */
    suspend fun searchPublicChats(query: String): Result<List<ChatSummary>>

    /**
     * Zprávy se [query] napříč všemi chaty (hlavní seznam i archiv), od nejnovější.
     * Další stránka se chce s [FoundMessagesPage.nextOffset] z předchozí; prázdný offset je první
     * stránka.
     */
    suspend fun searchMessages(query: String, offset: String = "", filter: MessageSearchFilter = MessageSearchFilter()): Result<FoundMessagesPage>

    /** Naposledy nalezené chaty (nejvýš 50), nejnovější první. Historii vede TDLib. */
    suspend fun recentlyFoundChats(): Result<List<ChatSummary>>

    /** Zapíše chat do historie hledání — volá se po ťuknutí na výsledek. */
    suspend fun addRecentlyFound(chatId: Long): Result<Unit>

    suspend fun clearRecentlyFound(): Result<Unit>

    /**
     * Lidé, se kterými si uživatel píše nejčastěji. Prázdný seznam, když si to uživatel
     * v Telegramu vypnul (TDLib pak vrátí chybu, kterou tu nepovažujeme za selhání).
     */
    suspend fun topChats(): Result<List<ChatSummary>>

    /**
     * Kolik chatů v archivu je nepřečtených. Archiv je schovaný v menu účtu, takže bez tohohle
     * není odkud se dozvědět, že v něm něco přibylo.
     *
     * Počítá TDLib (`UpdateUnreadChatCount`), ne my nad [chats]: archiv se načítá až při otevření,
     * takže dokud ho nikdo neotevřel, není z čeho počítat.
     */
    val archiveUnread: StateFlow<ArchiveUnread>

    /**
     * Nastavení archivu. Čte se na vyžádání — TDLib na jeho změnu žádný update neposílá, takže
     * se pak drží u toho, kdo ho zobrazuje.
     */
    suspend fun archiveSettings(): Result<ArchiveSettings>

    /** Zapíše nastavení archivu. [ArchiveSettings.canArchiveUnknownSenders] se ignoruje — to říká server. */
    suspend fun setArchiveSettings(settings: ArchiveSettings): Result<Unit>

    /** Požádá TDLib o dotažení dalších chatů ze serveru. Výsledek přijde updaty do [chats]. */
    suspend fun loadMore(folder: ChatFolder = ChatFolder.Main)

    /**
     * Připne nebo odepne chat ve složce, ve které je. Připnutí je vlastnost pozice ve složce,
     * ne chatu: archiv má vlastní připnuté.
     *
     * Počet připnutých chatů je omezený (`pinned_chat_count_max`, v archivu
     * `pinned_archived_chat_count_max`) a Premium ho zvedá; při překročení vrátí TDLib chybu,
     * kterou je potřeba ukázat — jinak to vypadá, že se nic nestalo.
     */
    suspend fun setPinned(chatId: Long, folder: ChatFolder, pinned: Boolean): Result<Unit>

    /**
     * Přesune chat do archivu nebo z něj zpátky do hlavního seznamu. Vratné, takže bez potvrzení.
     * Uložené zprávy archivovat nejdou (TDLib to odmítne), nabídka je proto u nich nemá.
     */
    suspend fun setArchived(chatId: Long, archived: Boolean): Result<Unit>

    /** Je chat ztlumený? Živě, pro profil chatu; `null`, dokud chat repository nezná. */
    fun muted(chatId: Long): Flow<Boolean?>

    /** Ztlumí chat natrvalo, nebo ztlumení zruší. */
    suspend fun setMuted(chatId: Long, muted: Boolean): Result<Unit>

    /** Ztlumí chat na [duration]; pak se zvuk vrátí sám (odpočet vede server). */
    suspend fun muteFor(chatId: Long, duration: Duration): Result<Unit>

    /** Ruční příznak nepřečtení. Nezávislý na počtu nepřečtených zpráv. */
    suspend fun setMarkedAsUnread(chatId: Long, unread: Boolean): Result<Unit>

    /**
     * Odstraní chat ze seznamu. **Nevratné.**
     *
     * U soukromého chatu smaže historii jen u nás (`revoke = false`), u skupiny a kanálu z něj
     * odejde. `deleteChat` se schválně nepoužívá: ten maže obsah VŠEM členům, což je něco úplně
     * jiného, než uživatel od položky v seznamu čeká.
     */
    suspend fun deleteOrLeave(chatId: Long): Result<Unit>

    /**
     * Co jde s chatem udělat proti spamu — viz [SpamActions].
     *
     * Čte se až při otevření nabídky nad chatem, jako práva u nabídky nad zprávou: [ChatSummary]
     * to nenese a zjišťovat to dopředu by znamenalo dotaz na každý chat v seznamu kvůli nabídce,
     * kterou uživatel skoro nikdy neotevře.
     */
    suspend fun spamActions(chatId: Long): Result<SpamActions>

    /**
     * Zablokuje protějšek soukromého chatu, nebo blokování zruší (`setMessageSenderBlockList`).
     *
     * Blokovaný nenapíše ani nezavolá; u bota je to zároveň jeho zastavení. Chat v seznamu
     * ZŮSTÁVÁ — blokování a mazání jsou dvě různé věci a nabídka je nabízí každou zvlášť.
     */
    suspend fun setBlocked(chatId: Long, blocked: Boolean): Result<Unit>

    /**
     * Stránka blokovaných odesílatelů (`getBlockedMessageSenders`, hlavní seznam blokování).
     * Blokovat jde i chat (kanál, skupinu), ne jen člověka — proto odesílatel, ne uživatel.
     */
    suspend fun blockedSenders(offset: Int, limit: Int): Result<BlockedSendersPage>

    /**
     * Zablokuje odesílatele, nebo blokování zruší — podle [BlockedSender.id], tedy bez chatu.
     * Seznam blokovaných nese i ty, se kterými uživatel chat smazal; [setBlocked] by na ně
     * nedosáhl, protože stojí na chatu.
     */
    suspend fun setSenderBlocked(senderId: Long, blocked: Boolean): Result<Unit>

    /**
     * Jeden krok nahlášení chatu moderátorům Telegramu (`reportChat`).
     *
     * Je to PRŮVODCE, ne jedno volání: na úvodní prázdný [ReportRequest] server odpoví, co chce
     * dál — volbu důvodu, text, nebo zprávy —, a teprve po posledním kroku vrátí
     * [ReportStep.Done]. Jen chat se spamovým action barem ([SpamActions.reportsSpamDirectly])
     * nahlásí jako spam hned úvodní požadavek.
     */
    suspend fun report(chatId: Long, request: ReportRequest): Result<ReportStep>

    /**
     * Pruh proti spamu nad konverzací, živě (`UpdateChatActionBar`); `null` = žádný.
     */
    fun spamBar(chatId: Long): Flow<SpamBar?>

    /** Zavře pruh nad konverzací napořád (`removeChatActionBar`). */
    suspend fun dismissSpamBar(chatId: Long): Result<Unit>

    /**
     * Vrátí automaticky archivovaný chat mezi chaty ([SpamBar.canUnarchive]). Podle TDLib to
     * jsou DVĚ věci naráz: `addChatToList(ChatListMain)` a upozornění chatu zpátky na výchozí
     * — server chat od neznámého při archivaci i ztlumil. Pruh si pak TDLib upraví sám
     * (nahlášení a blokování z něj zmizí, přidání kontaktu zůstane).
     */
    suspend fun unarchiveFromSpamBar(chatId: Long): Result<Unit>

    /** Přidá protějšek soukromého chatu do kontaktů pod jménem, které má v Telegramu. */
    suspend fun addToContacts(chatId: Long): Result<Unit>
}

/**
 * Zablokovaný odesílatel. [id] je id uživatele (kladné), nebo chatu (záporné) — stejná
 * konvence jako odesílatel zprávy v [SenderInfo].
 */
data class BlockedSender(
    val id: Long,
    /** Prázdné u smazaného účtu — jméno pak dosadí UI. */
    val title: String,
    val avatarFileId: Int?,
    val kind: ChatKind,
    val username: String?,
    val isBot: Boolean,
)

/** Stránka seznamu blokovaných; [totalCount] je počet v celém seznamu, ne na stránce. */
data class BlockedSendersPage(val senders: List<BlockedSender>, val totalCount: Int)

/**
 * Co jde s chatem udělat proti spamu. Zjišťuje se na dotaz, ne v seznamu — viz
 * [ChatRepository.spamActions].
 *
 * Výchozí hodnoty znamenají „nenabízet nic": když se stav zjistit nepodaří, nesmí se nabídnout
 * akce, která skončí chybou od serveru.
 */
data class SpamActions(
    /** Jen soukromý chat s někým jiným — u skupiny, kanálu ani Uložených zpráv není koho blokovat. */
    val canBlock: Boolean = false,
    /** Je protějšek právě zablokovaný? Podle toho nabídka nabídne zablokování, nebo odblokování. */
    val isBlocked: Boolean = false,
    /** Jde chat nahlásit moderátorům (`canBeReported`, nebo spamový action bar)? */
    val canReport: Boolean = false,
    /**
     * Nahlásí ho jako spam už úvodní požadavek, bez průvodce s volbou důvodu — chat má spamový
     * action bar. Jinak se nahlášení ptá na důvod, a popisek to musí říct.
     */
    val reportsSpamDirectly: Boolean = false,
)

sealed interface ChatListState {

    data object Loading : ChatListState

    data class Ready(
        val chats: List<ChatSummary>,
        /** `false`, když TDLib ohlásil, že další chaty už nejsou. */
        val canLoadMore: Boolean,
    ) : ChatListState

    data class Failed(val message: UiText) : ChatListState
}

/** Stránka výsledků hledání zpráv. [nextOffset] `null` = další už nejsou. */
data class FoundMessagesPage(
    val messages: List<FoundMessage>,
    val nextOffset: String?,
)

/**
 * Nalezená zpráva: kde je, kdy přišla a její text, jak by ho ukázal seznam chatů.
 * [chat] nese název a avatar chatu, ve kterém zpráva je.
 */
data class FoundMessage(
    val chat: ChatSummary,
    val messageId: Long,
    val text: String,
    /** Unixový čas. */
    val date: Int,
    val isOutgoing: Boolean,
)

/**
 * Složka seznamu chatů. Vlastní složky (`ChatListFolder`) zatím nejsou.
 *
 * Chat je v nejvýš jedné z nich — archivace ho z hlavního seznamu přesune, nezkopíruje.
 */
enum class ChatFolder { Main, Archive }

/**
 * Nepřečtené chaty v archivu. [unmutedChats] je podmnožina [chats] — archiv je místo, kam se
 * ztlumené chaty ukládají, takže „je tam něco nepřečteného" a „je tam něco, co si žádá pozornost"
 * jsou dvě různé věci.
 */
data class ArchiveUnread(val chats: Int = 0, val unmutedChats: Int = 0)

/**
 * Nastavení archivu (`ArchiveChatListSettings`).
 *
 * `keepChatsFromFoldersArchived` tu není: týká se vlastních složek Telegramu, které aplikace
 * nemá, takže by to byla volba bez následku. Zápis ho přesto přenese z aktuálního stavu —
 * nastavit ho mohl jiný klient.
 */
data class ArchiveSettings(
    /** Nové chaty od lidí mimo kontakty jdou rovnou do archivu a ztlumené. */
    val archiveUnknownSenders: Boolean,
    /** Neztlumený chat zůstane v archivu i po nové zprávě, místo aby se vrátil mezi chaty. */
    val keepUnmutedArchived: Boolean,
    /**
     * Smí se [archiveUnknownSenders] vůbec zapnout? Server to povoluje jen některým účtům
     * (`can_archive_and_mute_new_chats_from_unknown_users`) a zapnutí jinde odmítne.
     */
    val canArchiveUnknownSenders: Boolean,
)

/**
 * Druh chatu tak, jak ho rozlišuje uživatel: s člověkem (i tajný chat a bot), skupina (základní
 * i supergroup), kanál. TDLib má typů víc, ale pro filtr a tvar avataru na víc nezáleží.
 */
enum class ChatKind { Private, Group, Channel }

/**
 * Položka seznamu chatů.
 *
 * [order] je pořadové číslo z TDLib (`ChatPosition.order`) — ne index. Je to int64, do kterého
 * Telegram kóduje i připnutí, takže se podle něj řadí sestupně a nic dalšího se nedopočítává.
 * [order] i [isPinned] platí pro [folder], ve které chat právě je.
 */
data class ChatSummary(
    val id: Long,
    val title: String,
    val unreadCount: Int,
    val lastMessagePreview: String?,
    /** Vlastní emoji (a spoilery) v [lastMessagePreview]; offsety platí pro jeho text. */
    val lastMessagePreviewEntities: List<MessageEntity> = emptyList(),
    /** Nepřečtené zmínky (i odpovědi na vlastní zprávy) — odznak „@" vedle počtu. */
    val unreadMentionCount: Int = 0,
    /** Nepřečtené reakce na vlastní zprávy — odznak „❤" vedle počtu. */
    val unreadReactionCount: Int = 0,
    /** Nepřečtené hlasy ve vlastních anketách — odznak s anketou vedle počtu. */
    val unreadPollVoteCount: Int = 0,
    val isPinned: Boolean,
    val order: Long,
    /** Složka, ze které položka pochází — podle ní se nabízí archivace, nebo vrácení z archivu. */
    val folder: ChatFolder = ChatFolder.Main,
    /** `fileId` malé varianty avataru, nebo `null` když chat obrázek nemá. */
    val avatarFileId: Int?,
    /** Ztlumený chat notifikace nevydává. TDLib to řeší za nás, tohle je jen pro zobrazení. */
    val isMuted: Boolean,
    /** Ruční příznak „nepřečteno" — nezávislý na [unreadCount]. */
    val isMarkedAsUnread: Boolean,
    /** Soukromý chat, skupina, nebo kanál — podle toho filtr v seznamu a tvar avataru. */
    val kind: ChatKind,
    /** Unixový čas poslední zprávy, nebo `null` u prázdného chatu. */
    val lastMessageTimestamp: Int?,
    /** Dostupnost protějšku. `null` u skupin a kanálů — ty žádnou nemají. */
    val presence: Presence?,
    /**
     * Co protějšek zrovna dělá. `null`, když nic.
     *
     * Je to **pomíjivý stav**, ne vlastnost chatu: TDLib ho posílá opakovaně, dokud činnost trvá,
     * a ukončuje ho zvlášť. V seznamu nahrazuje náhled poslední zprávy.
     */
    val activity: ChatActivity?,
    /**
     * Rozepsaná zpráva, nebo `null`. V seznamu nahrazuje náhled poslední zprávy — kdo se k chatu
     * vrací, hledá hlavně to, co tam nechal nedopsané.
     */
    val draft: ChatDraft?,
    /**
     * Ověřený, podvod, falešný (viz [Verification]); `null` u běžného účtu. `Chat` od TDLib ho
     * nenese — je na uživateli a supergroupě, dočítá se zvlášť a mění updatem.
     */
    val verification: Verification? = null,
    /** Vlastní emoji statusu za jménem (viz [statusEmojiId]), nebo `null`. */
    val emojiStatus: Long? = null,
)

/**
 * Soukromý chat s jedním člověkem.
 *
 * Rozhoduje o tom, co znamená odstranění: u soukromého chatu smazání historie, u skupiny
 * a kanálu odchod. Jsou to dvě různé věci a nabídnout je pod jedním popiskem by lhalo.
 */
val ChatSummary.isPrivate: Boolean get() = kind == ChatKind.Private

/** Rozepsaná zpráva chatu, jak ji drží TDLib (a s ním i ostatní zařízení). */
data class ChatDraft(
    val text: String,
    /** Unixový čas uložení konceptu. */
    val timestamp: Int,
    /** Vlastní emoji (a spoilery) v [text] — koncept je smí nést, když ho psal jiný klient. */
    val entities: List<MessageEntity> = emptyList(),
)

/**
 * Dostupnost uživatele.
 *
 * Telegram ji záměrně rozmazává: kdo si to v soukromí vypne, je vidět jen jako „naposledy nedávno"
 * a přesný čas nedostaneme. Proto to nejsou hodiny, ale několik pojmenovaných stupňů.
 */
sealed interface Presence {

    data object Online : Presence

    /** [wasOnline] je unixový čas posledního připojení. */
    data class Offline(val wasOnline: Int) : Presence

    /** Uživatel si přesný čas nepřeje zveřejňovat. */
    data object Recently : Presence

    data object LastWeek : Presence

    data object LastMonth : Presence
}

/**
 * Činnost v chatu i s tím, kdo ji dělá. [senderId] je id uživatele, u anonymního správce nebo
 * kanálu id chatu — ve skupině podle něj konverzace kreslí tvář toho, kdo píše.
 */
data class ChatTyping(val activity: ChatActivity, val senderId: Long)

/**
 * Činnost v chatu („píše…").
 *
 * Neznámé a exotické druhy spadnou do [Other] — hlásit „něco dělá" je pořád lepší než mlčet,
 * protože uživatele zajímá hlavně to, že protějšek je u telefonu.
 */
enum class ChatActivity {
    Typing,
    RecordingVoice,
    RecordingVideo,
    SendingPhoto,
    SendingVideo,
    SendingFile,
    ChoosingSticker,
    Other,
}
