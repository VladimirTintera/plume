package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.StateFlow

/**
 * Seznam chatů hlavní složky, živě aktualizovaný.
 *
 * Doménová hranice — implementace nad TDLib je [TdlChatRepository]. Seznam není stránkovaný
 * v klasickém smyslu „načti stránku a vrať ji": TDLib drží lokální databázi a **posílá změny
 * updaty**. [loadMore] jen řekne TDLib, ať dotáhne další kus ze serveru; výsledek se objeví
 * v [chats] sám, až updaty dorazí.
 */
interface ChatRepository {

    val chats: StateFlow<ChatListState>

    /** Požádá TDLib o dotažení dalších chatů ze serveru. Výsledek přijde updaty do [chats]. */
    suspend fun loadMore()

    /**
     * Připne nebo odepne chat.
     *
     * Počet připnutých chatů je omezený (`pinned_chat_count_max`) a Premium ho zvedá; při
     * překročení vrátí TDLib chybu, kterou je potřeba ukázat — jinak to vypadá, že se nic nestalo.
     */
    suspend fun setPinned(chatId: Long, pinned: Boolean): Result<Unit>

    /** Ztlumí chat nebo ztlumení zruší. */
    suspend fun setMuted(chatId: Long, muted: Boolean): Result<Unit>

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
}

sealed interface ChatListState {

    data object Loading : ChatListState

    data class Ready(
        val chats: List<ChatSummary>,
        /** `false`, když TDLib ohlásil, že další chaty už nejsou. */
        val canLoadMore: Boolean,
    ) : ChatListState

    data class Failed(val message: String) : ChatListState
}

/**
 * Položka seznamu chatů.
 *
 * [order] je pořadové číslo z TDLib (`ChatPosition.order`) — ne index. Je to int64, do kterého
 * Telegram kóduje i připnutí, takže se podle něj řadí sestupně a nic dalšího se nedopočítává.
 */
data class ChatSummary(
    val id: Long,
    val title: String,
    val unreadCount: Int,
    val lastMessagePreview: String?,
    val isPinned: Boolean,
    val order: Long,
    /** `fileId` malé varianty avataru, nebo `null` když chat obrázek nemá. */
    val avatarFileId: Int?,
    /** Ztlumený chat notifikace nevydává. TDLib to řeší za nás, tohle je jen pro zobrazení. */
    val isMuted: Boolean,
    /** Ruční příznak „nepřečteno" — nezávislý na [unreadCount]. */
    val isMarkedAsUnread: Boolean,
    /**
     * Soukromý chat s jedním člověkem.
     *
     * Rozhoduje o tom, co znamená odstranění: u soukromého chatu smazání historie, u skupiny
     * a kanálu odchod. Jsou to dvě různé věci a nabídnout je pod jedním popiskem by lhalo.
     */
    val isPrivate: Boolean,
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
