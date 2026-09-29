package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow

/**
 * Pozvánky do skupiny nebo kanálu a žádosti o vstup. Spravovat je smí vlastník a správce
 * s právem zvát ([ChatEditRights.canManageInvites]).
 *
 * Seznam obsahuje jen odkazy, které vytvořil **přihlášený účet** — tak je vede i TDLib
 * (`getChatInviteLinks` bere tvůrce). Odkazy ostatních správců vidí jen vlastník a zatím se
 * nenabízejí.
 */
interface InviteLinkRepository {
    suspend fun links(chatId: Long): Result<InviteLinks>

    suspend fun create(chatId: Long, draft: InviteLinkDraft): Result<InviteLink>

    suspend fun edit(chatId: Long, url: String, draft: InviteLinkDraft): Result<InviteLink>

    /** Zruší odkaz. U hlavního odkazu vznikne nový hlavní — starý přestane platit. */
    suspend fun revoke(chatId: Long, url: String): Result<Unit>

    suspend fun deleteRevoked(chatId: Long, url: String): Result<Unit>

    suspend fun deleteAllRevoked(chatId: Long): Result<Unit>

    /** Počet čekajících žádostí o vstup; živě podle `UpdateChatPendingJoinRequests`. */
    fun pendingJoinRequests(chatId: Long): Flow<Int>

    suspend fun joinRequests(chatId: Long): Result<List<JoinRequest>>

    suspend fun processJoinRequest(chatId: Long, userId: Long, approve: Boolean): Result<Unit>

    suspend fun processAllJoinRequests(chatId: Long, approve: Boolean): Result<Unit>
}

/** Vlastní odkazy: platné (hlavní první) a zrušené. */
data class InviteLinks(
    val active: List<InviteLink>,
    val revoked: List<InviteLink>,
) {
    val primary: InviteLink? get() = active.firstOrNull { it.isPrimary }
    val additional: List<InviteLink> get() = active.filterNot { it.isPrimary }
}

data class InviteLink(
    val url: String,
    val name: String,
    val isPrimary: Boolean,
    val isRevoked: Boolean,
    /** Okamžik vytvoření (unix, s). */
    val createdAt: Int,
    /** Konec platnosti (unix, s); 0 = napořád. */
    val expiresAt: Int,
    /** Kolik lidí se smí odkazem přidat; 0 = bez omezení. */
    val memberLimit: Int,
    val memberCount: Int,
    val pendingRequests: Int,
    /** Nový člen musí projít schválením správcem. */
    val createsJoinRequest: Boolean,
    /** Placené předplatné (hvězdy) — aplikace ho neumí vytvořit ani upravit, jen ukázat. */
    val isSubscription: Boolean = false,
)

/** Co se zadává při vytvoření a úpravě odkazu. */
data class InviteLinkDraft(
    val name: String = "",
    /** Konec platnosti (unix, s); 0 = napořád. */
    val expiresAt: Int = 0,
    /** 0 = bez omezení. */
    val memberLimit: Int = 0,
    val createsJoinRequest: Boolean = false,
) {
    /**
     * Tvar, který server přijme: bez mezer kolem jména, a **se schvalováním bez limitu** — TDLib
     * `member_limit` u odkazu se žádostmi odmítne. Limit se proto ve formuláři i schová.
     */
    fun normalized(): InviteLinkDraft = copy(
        name = name.trim().take(INVITE_LINK_NAME_MAX),
        memberLimit = if (createsJoinRequest) 0 else memberLimit.coerceIn(0, INVITE_LINK_MEMBER_LIMIT_MAX),
    )

    companion object {
        fun of(link: InviteLink): InviteLinkDraft = InviteLinkDraft(
            name = link.name,
            expiresAt = link.expiresAt,
            memberLimit = link.memberLimit,
            createsJoinRequest = link.createsJoinRequest,
        )
    }
}

/** Žádost o vstup: kdo, kdy a jeho bio (podle něj se správce rozhoduje). */
data class JoinRequest(
    val userId: Long,
    val name: String,
    val avatarFileId: Int?,
    /** Okamžik žádosti (unix, s). */
    val date: Int,
    val bio: String,
)

/** Jak odkaz stojí — podle toho podtitul a jestli ho má smysl kopírovat. */
enum class InviteLinkStatus { Active, Expired, Full, Revoked }

/**
 * Stav odkazu k okamžiku [now] (unix, s). Zrušený má přednost, pak vypršelý, pak naplněný.
 *
 * Server vypršelý ani naplněný odkaz sám nezruší, jen přestane pouštět dovnitř; bez tohohle by
 * v seznamu vypadal jako platný.
 */
fun inviteLinkStatus(link: InviteLink, now: Long): InviteLinkStatus = when {
    link.isRevoked -> InviteLinkStatus.Revoked
    link.expiresAt != 0 && link.expiresAt <= now -> InviteLinkStatus.Expired
    link.memberLimit != 0 && link.memberCount >= link.memberLimit -> InviteLinkStatus.Full
    else -> InviteLinkStatus.Active
}

/** Jméno odkazu je jen pro správce; TDLib bere nejvýš 32 znaků. */
const val INVITE_LINK_NAME_MAX = 32

/** Strop limitu členů odkazu podle TDLib (`member_limit` 0–99 999). */
const val INVITE_LINK_MEMBER_LIMIT_MAX = 99_999
