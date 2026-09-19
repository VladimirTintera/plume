package eu.tintera.plume.core.telegram

/**
 * Profil chatu: kdo nebo co to je, o čem je a kdo v něm je.
 *
 * Hranice kolem TDLib jako ostatní repository — obrazovka vidí jen [ChatProfile] a [ProfileMember].
 * Data se čtou při otevření profilu; živé je jen ztlumení ([ChatRepository.muted]) a dostupnost
 * ([ConversationRepository.presenceUpdates]).
 */
interface ProfileRepository {
    suspend fun profile(chatId: Long): Result<ChatProfile>

    /**
     * Členové skupiny s rolí, vlastník a správci napřed. Jen skupiny, kde je server vydá
     * ([ChatProfile.canListMembers]); u velké skupiny jen prvních [MAX_PROFILE_MEMBERS].
     */
    suspend fun members(chatId: Long): Result<List<ProfileMember>>
}

data class ChatProfile(
    val chatId: Long,
    val title: String,
    val kind: ProfileKind,
    /** Velká fotka do hlavičky; [photoThumbnailFileId] je malá, ta se ukáže hned. */
    val photoFileId: Int?,
    val photoThumbnailFileId: Int?,
    /** Aktivní uživatelská jména bez `@`, první je hlavní. */
    val usernames: List<String>,
    /** Bio uživatele nebo popis skupiny / kanálu; prázdné = není. */
    val about: String,
    /** Formátování a odkazy v [about] — klikatelné stejně jako ve zprávách. */
    val aboutEntities: List<MessageEntity>,
    /** Telefon, jen když ho uživatel ukazuje (jinak prázdný řetězec od TDLib → `null`). */
    val phone: String?,
    /** Počet členů / odběratelů; u soukromého chatu `null`. */
    val memberCount: Int?,
    /** Server vydá seznam členů (skupina, ne kanál; supergroup se skrytými členy ne). */
    val canListMembers: Boolean,
)

enum class ProfileKind { User, Bot, SavedMessages, Group, Channel }

data class ProfileMember(val sender: SenderInfo, val role: MemberRole)

enum class MemberRole { Owner, Admin, Member }

/** Kolik členů profil ukáže; víc se nedá rozumně procházet a hledání má vlastní výběr. */
const val MAX_PROFILE_MEMBERS = 200
