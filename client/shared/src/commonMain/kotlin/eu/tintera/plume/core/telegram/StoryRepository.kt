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
}

/** Vlastní příběh připravený ke zveřejnění. [photoPath] je JPEG přesně 1080 × 1920 (`StoryPhoto`). */
data class StoryDraft(
    val photoPath: String,
    val caption: String,
    val audience: StoryAudience,
    /** Ponechat příběh v profilu i po vypršení. */
    val keepOnProfile: Boolean,
    /** Zakázat přeposílání a snímky obrazovky. */
    val protectContent: Boolean,
)

/** Kdo příběh uvidí. Blízcí přátelé chybí vědomě: jejich seznam Plume zatím upravovat neumí. */
sealed interface StoryAudience {
    data object Everyone : StoryAudience
    data object Contacts : StoryAudience
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
) {
    val unreadCount: Int get() = stories.count { it.id > maxReadStoryId }

    val hasUnread: Boolean get() = unreadCount > 0

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
