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

    /** Zablokovat / odblokovat uživatele nebo bota (`setMessageSenderBlockList`). */
    suspend fun setBlocked(userId: Long, blocked: Boolean): Result<Unit>

    /**
     * Soukromý chat smaže jen u nás (`deleteChatHistory`, `revoke = false`), ze skupiny a kanálu
     * odejde (`leaveChat`) — stejně jako „Smazat" v seznamu chatů, viz „Akce nad chatem".
     */
    suspend fun deleteOrLeave(profile: ChatProfile): Result<Unit>
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
    /** Uživatel za soukromým chatem; `null` u skupiny a kanálu. */
    val userId: Long? = null,
    /** Uživatel nebo bot je zablokovaný. */
    val isBlocked: Boolean = false,
    /** Hlavní pozvánka do skupiny / kanálu — server ji vydá jen správci s právem zvát. */
    val inviteLink: String? = null,
    /** Uživatel ve skupině / kanálu je (smí odejít); u soukromého chatu `false`. */
    val isMember: Boolean = false,
) {
    /** Odkaz ke sdílení: veřejné jméno, jinak pozvánka; `null` = není co sdílet. */
    val link: String?
        get() = usernames.firstOrNull()?.let { "https://t.me/$it" } ?: inviteLink

    /** Soukromý chat (i s botem) jde smazat, ze skupiny a kanálu se odchází. */
    val isPrivate: Boolean get() = kind == ProfileKind.User || kind == ProfileKind.Bot
}

enum class ProfileKind { User, Bot, SavedMessages, Group, Channel }

data class ProfileMember(
    val sender: SenderInfo,
    val role: MemberRole,
    /**
     * Štítek člena ve skupině (`ChatMember.tag`), třeba „Moderátor". Dřív ho směl mít jen správce
     * jako „custom title"; novější TDLib ho nese u každého člena. Prázdný od serveru → `null`.
     */
    val tag: String? = null,
    val isBot: Boolean = false,
)

enum class MemberRole { Owner, Admin, Member }

/** Kolik členů profil ukáže; víc se nedá rozumně procházet a hledání má vlastní výběr. */
const val MAX_PROFILE_MEMBERS = 200
