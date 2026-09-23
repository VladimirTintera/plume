package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

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

    /**
     * Soukromý chat smaže jen u nás (`deleteChatHistory`, `revoke = false`), ze skupiny a kanálu
     * odejde (`leaveChat`) — stejně jako „Smazat" v seznamu chatů, viz „Akce nad chatem".
     */
    suspend fun deleteOrLeave(profile: ChatProfile): Result<Unit>

    /**
     * Co jde na chatu upravit a jak to teď vypadá. Čte se až při otevření úpravy — profil
     * z toho potřebuje jen [ChatProfile.editRights], aby věděl, jestli položku nabídnout.
     */
    suspend fun editableChat(chatId: Long): Result<EditableChat>

    suspend fun setTitle(chatId: Long, title: String): Result<Unit>

    suspend fun setDescription(chatId: Long, description: String): Result<Unit>

    /** Nová fotka chatu z lokálního souboru; kulatý ořez dělá až vykreslení. */
    suspend fun setPhoto(chatId: Long, path: String): Result<Unit>

    suspend fun deletePhoto(chatId: Long): Result<Unit>

    /**
     * Výchozí práva členů. Zapisuje se **celým objektem**, takže co aplikace nenabízí
     * (hudba, video kolečka, ankety, témata…), se přenese z aktuálního stavu — nastavit to
     * mohl jiný klient a přepsat mu to tiše by bylo horší než to nenabízet.
     */
    suspend fun setPermissions(chatId: Long, permissions: GroupPermissions): Result<Unit>

    /**
     * Jeden člen se vším, co jeho správa potřebuje — role, práva, omezení a hlavně
     * [MemberDetail.actions], tedy co s ním smí právě tenhle uživatel.
     *
     * Čte se až při otevření nabídky nad členem: `getChatMember` je dotaz na server a ptát se
     * na každého ze seznamu dopředu by znamenalo dvě stě dotazů za otevření profilu.
     */
    suspend fun member(chatId: Long, userId: Long): Result<MemberDetail>

    /**
     * Ozve se, kdykoli se v chatu změní postavení některého člena.
     *
     * Seznam se na to ČTE ZNOVU, neodvozuje se z updatu — stejně jako připnuté zprávy: jeden
     * update nese jednoho člena, a co změna doopravdy udělala (a jestli server vůbec poslechl),
     * ví jen server. TDLib `UpdateChatMember` navíc posílá jen správcům, tedy právě těm, kdo
     * členy mění.
     */
    fun memberUpdates(chatId: Long): Flow<Unit>

    /**
     * Práva správce. **Prázdná práva ([AdminRights.any] `false`) člena ze správcovství sesadí** —
     * tak to bere TDLib, takže „Sesadit" není vlastní volání.
     *
     * Zapisuje se celým objektem: co aplikace nenabízí (videohovory, stories, témata), se přenese
     * z jeho současných práv, stejně jako u [setPermissions].
     */
    suspend fun setAdminRights(chatId: Long, userId: Long, rights: AdminRights): Result<Unit>

    /**
     * Omezit, co člen smí posílat; [duration] `null` = navždy. Jen v supergroupě — jinde TDLib
     * odpoví „Can't restrict users in basic group chats".
     */
    suspend fun restrictMember(
        chatId: Long,
        userId: Long,
        permissions: GroupPermissions,
        duration: Duration?,
    ): Result<Unit>

    /** Zrušit omezení: člen se vrátí k výchozím právům skupiny. */
    suspend fun unrestrictMember(chatId: Long, userId: Long): Result<Unit>

    /**
     * Vyhodit ze skupiny; [duration] `null` = navždy. Zprávy po něm zůstanou — mazání historie
     * je jiná věc a v nabídce by se vedle vyhození dalo snadno ťuknout omylem.
     */
    suspend fun banMember(chatId: Long, userId: Long, duration: Duration?): Result<Unit>

    /** Vlastní štítek člena; prázdný řetězec ho odebere. */
    suspend fun setMemberTag(chatId: Long, userId: Long, tag: String): Result<Unit>

    /**
     * Jestli účet vůbec smí předávat vlastnictví. Ptát se je potřeba PŘED zadáním hesla:
     * bez dvoufázového ověření (nebo s čerstvě nastaveným heslem) to Telegram nedovolí
     * a uživatel by jinak psal heslo zbytečně.
     */
    suspend fun canTransferOwnership(): Result<OwnershipTransferCheck>

    /**
     * Předat vlastnictví skupiny nebo kanálu. **Nevratné** a vyžaduje heslo dvoufázového
     * ověření — to jde rovnou do TDLib a nikde se nedrží.
     */
    suspend fun transferOwnership(chatId: Long, userId: Long, password: String): Result<Unit>
}

/** Chat otevřený k úpravě: co se dá změnit a jak to teď vypadá. */
data class EditableChat(
    val chatId: Long,
    val title: String,
    val description: String,
    val photoFileId: Int?,
    val photoThumbnailFileId: Int?,
    val isChannel: Boolean,
    val rights: ChatEditRights,
    /** Výchozí práva členů; u kanálu se nenabízejí a jsou tu jen jako výchozí hodnoty. */
    val permissions: GroupPermissions,
)

/**
 * Výchozí práva členů skupiny, zúžená na to, co aplikace umí a na čem uživateli záleží.
 *
 * Druhy obsahu jsou tytéž jako v [ContentAccess] — tedy ty, které vstupní pole opravdu omezuje.
 * Hudba, video kolečka, ankety, náhledy odkazů a témata tu nejsou: aplikace je neposílá, takže
 * nabízet u nich přepínač by slibovalo vliv na něco, co stejně nejde udělat.
 */
data class GroupPermissions(
    val sendMessages: Boolean,
    val sendPhotos: Boolean,
    val sendVideos: Boolean,
    val sendDocuments: Boolean,
    val sendVoiceNotes: Boolean,
    /** Samolepky, animovaná emoji a GIFy — TDLib je vede pod jedním `canSendOtherMessages`. */
    val sendStickers: Boolean,
    val react: Boolean,
    val inviteUsers: Boolean,
    val pinMessages: Boolean,
    /** Smí člen měnit název, popis a fotku skupiny. */
    val changeInfo: Boolean,
)

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
    /** Co smí uživatel na chatu upravovat; prázdné = položka „Upravit" se nenabízí. */
    val editRights: ChatEditRights = ChatEditRights(),
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
