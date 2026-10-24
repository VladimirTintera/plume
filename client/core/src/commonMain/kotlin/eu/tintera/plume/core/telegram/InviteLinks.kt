package eu.tintera.plume.core.telegram

import kotlinx.coroutines.flow.Flow

/**
 * Pozvánky do skupiny nebo kanálu a žádosti o vstup. Spravovat je smí vlastník a správce
 * s právem zvát ([ChatEditRights.canManageInvites]).
 *
 * Seznam se vede po tvůrcích — tak je vede i TDLib (`getChatInviteLinks` bere tvůrce).
 * Vlastní odkazy vidí každý, kdo pozvánky spravuje; odkazy ostatních správců jen vlastník
 * ([otherAdminsLinks]).
 */
interface InviteLinkRepository {
    /** Odkazy jednoho tvůrce; `null` = přihlášený účet. */
    suspend fun links(chatId: Long, creatorUserId: Long? = null): Result<InviteLinks>

    /**
     * Ostatní správci, kteří mají odkazy, a kolik jich mají. Jen pro vlastníka — komukoli jinému
     * server odpoví chybou, a to je prázdný seznam, ne selhání.
     */
    suspend fun otherAdminsLinks(chatId: Long): Result<List<AdminInviteLinks>>

    /** Kdo se odkazem přidal, od nejnovějšího. */
    suspend fun linkMembers(chatId: Long, url: String): Result<List<InviteLinkMember>>

    suspend fun create(chatId: Long, draft: InviteLinkDraft): Result<InviteLink>

    suspend fun edit(chatId: Long, url: String, draft: InviteLinkDraft): Result<InviteLink>

    /** Zruší odkaz. U hlavního odkazu vznikne nový hlavní — starý přestane platit. */
    suspend fun revoke(chatId: Long, url: String): Result<Unit>

    suspend fun deleteRevoked(chatId: Long, url: String): Result<Unit>

    /** Smaže zrušené odkazy jednoho tvůrce; `null` = přihlášený účet. */
    suspend fun deleteAllRevoked(chatId: Long, creatorUserId: Long? = null): Result<Unit>

    /**
     * Čekající žádosti o vstup: počet a tváře posledních žadatelů; živě podle
     * `UpdateChatPendingJoinRequests`. Server je posílá jen tomu, kdo žádosti smí vyřizovat.
     */
    fun pendingJoinRequests(chatId: Long): Flow<PendingJoinRequests>

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

/**
 * Odkazy jiného správce: kdo a kolik platných a zrušených.
 *
 * - **Počty se můžou chvíli rozcházet se seznamem** (`getChatInviteLinkCounts`): hned po
 *   založení odkazů hlásil server u bota 1 platný, zatímco jeho seznam vrátil dva (hlavní
 *   a založený); po pár minutách už 2. Pravdu má seznam, který se čte při otevření.
 * - **Odkazy bota smí zrušit a smazat jen bot sám** ([isBot]); vlastníkovi server odpoví
 *   `EDIT_BOT_INVITE_FORBIDDEN`. Nabízí se proto jen sdílet a kopírovat.
 */
data class AdminInviteLinks(
    val userId: Long,
    val name: String,
    val avatarFileId: Int?,
    val active: Int,
    val revoked: Int,
    val isBot: Boolean = false,
)

/** Kdo se odkazem přidal a kdy. */
data class InviteLinkMember(
    val userId: Long,
    val name: String,
    val avatarFileId: Int?,
    /** Okamžik vstupu (unix, s). */
    val joinedAt: Int,
    /** Přidal se se sdílenou složkou, ne samotnou pozvánkou. */
    val viaFolder: Boolean,
)

/** Čekající žádosti o vstup pro pruh nad konverzací: počet a tváře posledních žadatelů. */
data class PendingJoinRequests(
    val count: Int,
    val recent: List<SenderInfo> = emptyList(),
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
