package eu.tintera.plume.core.telegram

import eu.tintera.plume.core.ui.UiText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Aktivní příběhy (stories) po autorech a jejich prohlížení.
 *
 * Kdo má příběh, se dozvídáme jen updaty (`updateChatActiveStories`), proto implementace nad
 * TDLib **vzniká eagerně** — stejně jako seznam chatů.
 */
interface StoryRepository {

    /**
     * Autoři, kteří právě mají aspoň jeden aktivní příběh, podle id chatu autora. Kdo příběhy
     * nemá, v mapě není.
     */
    val active: StateFlow<Map<Long, ActiveStories>>

    /** Obsah jednoho příběhu; `isAvailable = false`, když už není (vypršel, smazaný). */
    suspend fun story(posterChatId: Long, storyId: Int): MessageMedia.Story

    /**
     * Příběh je na obrazovce. **Bez toho se zhlédnutí nepočítá** — TDLib ho podle toho označí
     * za přečtený (posune `maxReadStoryId`) a autor uvidí zhlédnutí.
     */
    suspend fun open(posterChatId: Long, storyId: Int)

    /** Příběh z obrazovky zmizel. Ke každému [open] patří jedno [close]. */
    suspend fun close(posterChatId: Long, storyId: Int)

    /** Dá na příběh reakci (klíč, viz [customEmojiReaction]), nebo ji sundá (`null`). */
    suspend fun react(posterChatId: Long, storyId: Int, reaction: String?): Result<Unit>

    /**
     * Odpoví na příběh zprávou do chatu s autorem. Zpráva v chatu cituje příběh
     * (`MessageReplyToStory`), takže autor ví, na co odpovídáme.
     */
    suspend fun reply(posterChatId: Long, storyId: Int, text: String): Result<Unit>

    /**
     * Smí účet právě teď zveřejnit příběh? Ptá se předem, ne až po výběru fotky: limit (u free
     * účtu jeden příběh za den) se pozná dřív, než si uživatel příběh připraví.
     */
    suspend fun canPost(): StoryPostCheck

    /**
     * Zveřejní vlastní příběh. Návrat znamená jen „TDLib přijal" — nahrávání běží dál a jak
     * dopadlo, řekne [postResults].
     */
    suspend fun post(draft: StoryDraft): Result<Unit>

    /** Jak dopadlo zveřejnění (`updateStoryPostSucceeded` / `updateStoryPostFailed`). */
    val postResults: Flow<StoryPostResult>

    /** Smaže vlastní příběh. */
    suspend fun delete(posterChatId: Long, storyId: Int): Result<Unit>

    /**
     * Kdo vlastní příběh [storyId] viděl, po stránkách; [offset] je `nextOffset` předchozí
     * stránky (prázdný = první). Nejdřív ti s reakcí, pak podle času.
     */
    suspend fun viewers(storyId: Int, offset: String = ""): Result<StoryViewersPage>

    /** Id uživatelů na seznamu blízkých přátel. Seznam je nastavení ÚČTU (sdílí ho všichni klienti). */
    suspend fun closeFriends(): Result<List<Long>>

    /** Nahradí seznam blízkých přátel; smí na něm být jen kontakty. */
    suspend fun setCloseFriends(userIds: List<Long>): Result<Unit>

    /**
     * Skryje autorovy příběhy do archivu ([hidden]), nebo je vrátí mezi ostatní. Skrýt jde jen
     * autora s [ActiveStories.canBeArchived], vrátit skrytého (vlastní příběhy ani jedno).
     */
    suspend fun setHidden(chatId: Long, hidden: Boolean): Result<Unit>

    /**
     * Příběhy, které autor nechal v profilu (i vypršelé), po stránkách: první ([fromStoryId] 0)
     * začíná připnutými, pak od nejnovějšího. Další stránka od id posledního příběhu.
     */
    suspend fun profileStories(chatId: Long, fromStoryId: Int = 0): Result<ProfileStoriesPage>

    /** Jméno, fotka a druh autora pro hlavičku prohlížeče — i když teď žádný aktivní příběh nemá. */
    suspend fun author(chatId: Long): ActiveStories?

    /**
     * Jeden krok nahlášení příběhu (`reportStory`). Je to PRŮVODCE jako u chatu: prázdný
     * [request] se zeptá na důvod, pak případně na text — viz [ChatRepository.report].
     */
    suspend fun report(posterChatId: Long, storyId: Int, request: ReportRequest): Result<ReportStep>

    /** Alba příběhů, která autor v profilu vystavil (`getChatStoryAlbums`). */
    suspend fun albums(chatId: Long): Result<List<StoryAlbum>>

    /** Příběhy jednoho alba po stránkách; [offset] = kolik už je načtených. */
    suspend fun albumStories(chatId: Long, albumId: Int, offset: Int = 0): Result<ProfileStoriesPage>

    /** Nové album autora [posterChatId] (vlastní účet, kanál, který spravujeme) s příběhy [storyIds]. */
    suspend fun createAlbum(posterChatId: Long, name: String, storyIds: List<Int>): Result<StoryAlbum>

    /** Přidá příběh do alba ([inAlbum]), nebo ho z něj vyndá. */
    suspend fun setInAlbum(chatId: Long, albumId: Int, storyId: Int, inAlbum: Boolean): Result<Unit>

    suspend fun renameAlbum(chatId: Long, albumId: Int, name: String): Result<StoryAlbum>

    /** Smaže album; příběhy v něm zůstanou. */
    suspend fun deleteAlbum(chatId: Long, albumId: Int): Result<Unit>

    /** Nové pořadí alb autora (tak se ukazují v profilu). */
    suspend fun reorderAlbums(chatId: Long, albumIds: List<Int>): Result<Unit>

    /**
     * Nové pořadí příběhů v albu. TDLib dané příběhy posune na začátek a zbytek nechá za nimi,
     * proto se posílá celé pořadí.
     */
    suspend fun reorderAlbumStories(chatId: Long, albumId: Int, storyIds: List<Int>): Result<Unit>
}

/** Telegram bere název alba o 1–12 znacích (`createStoryAlbum`, `setStoryAlbumName`). */
const val STORY_ALBUM_NAME_MAX = 12

data class StoryAlbum(val id: Int, val name: String)

data class ProfileStoriesPage(
    /** Obsah příběhů (fotka nebo video) v pořadí od serveru. */
    val stories: List<MessageMedia.Story>,
    val totalCount: Int,
    /** Připnuté příběhy; jen na první stránce. */
    val pinnedIds: Set<Int>,
)

data class StoryViewersPage(
    val viewers: List<StoryViewer>,
    /** Přibližný počet všech (server ho zaokrouhluje). */
    val totalCount: Int,
    /** Posun další stránky; `null` = víc jich není. */
    val nextOffset: String?,
)

data class StoryViewer(
    val chatId: Long,
    val title: String,
    val avatarFileId: Int?,
    val kind: ChatKind,
    /** Kdy příběh viděl (nebo přeposlal), unix čas. */
    val date: Int,
    /** Reakce, kterou dal (klíč, viz [customEmojiReaction]); `null` = žádná. */
    val reaction: String? = null,
    val action: StoryViewerAction = StoryViewerAction.Viewed,
)

enum class StoryViewerAction { Viewed, Forwarded, Reposted }

/** Vlastní příběh připravený ke zveřejnění. */
data class StoryDraft(
    val media: StoryMedia,
    val caption: String,
    val audience: StoryAudience,
    /** Ponechat příběh v profilu i po vypršení. */
    val keepOnProfile: Boolean,
    /** Zakázat přeposílání a snímky obrazovky. */
    val protectContent: Boolean,
)

/** Obsah příběhu už převedený do tvaru, který TDLib vyžaduje. */
sealed interface StoryMedia {
    /** JPEG přesně 1080 × 1920 (`StoryPhoto`). */
    data class Photo(val path: String) : StoryMedia

    /**
     * MP4 720 × 1280 (`StoryVideo`); bez zvuku jde jako animace. [coverSeconds] je obálka
     * (snímek náhledu) v čase od začátku převedeného videa.
     */
    data class Video(val path: String, val durationSeconds: Double, val hasAudio: Boolean, val coverSeconds: Double = 0.0) : StoryMedia
}

/** Kdo příběh uvidí. */
sealed interface StoryAudience {
    data object Everyone : StoryAudience
    data object Contacts : StoryAudience

    /** Lidé ze seznamu blízkých přátel (`setCloseFriends`). */
    data object CloseFriends : StoryAudience
    data class Selected(val userIds: List<Long>) : StoryAudience
}

sealed interface StoryPostCheck {
    data object Allowed : StoryPostCheck

    /** Nejde (limit, Premium); [reason] je věta pro uživatele. Nákup Premium se nenabízí. */
    data class Denied(val reason: UiText) : StoryPostCheck
}

sealed interface StoryPostResult {
    data object Posted : StoryPostResult
    data class Failed(val reason: UiText) : StoryPostResult
}

/** Seznam příběhů: hlavní, nebo skrytý do archivu. */
enum class StoryListKind { Main, Archive }

/**
 * Aktivní příběhy jednoho autora. Jméno a fotka jsou tu proto, že autor nemusí mít chat
 * v seznamu (kontakt, se kterým jsme si nikdy nepsali) — hromádka v pilulce ho ukázat musí.
 *
 * [list] je `null`, když autorovy příběhy v žádném seznamu nejsou a jsou vidět jen u něj
 * (kroužek v řádku chatu, profil).
 */
data class ActiveStories(
    val chatId: Long,
    val title: String,
    val avatarFileId: Int?,
    val kind: ChatKind,
    val list: StoryListKind?,
    val order: Long,
    /** Id posledního zhlédnutého příběhu; novější jsou nezhlédnuté. */
    val maxReadStoryId: Int,
    /** Od nejstaršího po nejnovější, jak je posílá TDLib. */
    val stories: List<StoryRef>,
    val avatarColorId: Long = chatId,
    /**
     * Jde autorovy příběhy skrýt do archivu (`canBeArchived`). **TDLib ho dává jen v hlavním
     * seznamu** — u už skrytého autora je `false`, vrátit ho ale jde ([isHidden]). Vlastní ne.
     */
    val canBeArchived: Boolean = false,
) {
    /** Autorovy příběhy jsou skryté v archivu. */
    val isHidden: Boolean get() = list == StoryListKind.Archive

    val unreadCount: Int get() = stories.count { it.id > maxReadStoryId }

    val hasUnread: Boolean get() = unreadCount > 0

    /** Mezi nezhlédnutými je příběh jen pro blízké přátele (autor nás má na seznamu). */
    val hasUnreadForCloseFriends: Boolean get() = stories.any { it.isForCloseFriends && it.id > maxReadStoryId }

    /** Kde se má autor otevřít: na prvním nezhlédnutém, a když jsou všechny zhlédnuté, od začátku. */
    val firstToShow: Int get() = stories.indexOfFirst { it.id > maxReadStoryId }.coerceAtLeast(0)
}

data class StoryRef(
    val id: Int,
    /** Unix čas zveřejnění. */
    val date: Int,
    val isLive: Boolean = false,
    val isForCloseFriends: Boolean = false,
)
