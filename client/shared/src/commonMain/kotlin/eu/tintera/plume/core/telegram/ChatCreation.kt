package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.ChatTypePrivate
import dev.g000sha256.tdl.dto.ChatTypeSupergroup
import dev.g000sha256.tdl.toResult

/** Člověk, kterého server do nové skupiny nepustil (soukromí); [name] může být prázdné. */
data class RefusedMember(val userId: Long, val name: String)

/**
 * Založená skupina nebo kanál. [refused] jsou lidé, které server nepustil; [usernameFailed] =
 * chat stojí, ale veřejný odkaz se nastavit nepodařilo (zůstal soukromý).
 */
data class CreatedChat(
    val chatId: Long,
    val refused: List<RefusedMember> = emptyList(),
    val usernameFailed: Boolean = false,
)

/**
 * Zakládání skupin a kanálů. Samostatné rozhraní, ne další metoda [ChatRepository]: ten má
 * desítky podvrhů v testech a založení chatu s jeho seznamem nesouvisí.
 */
interface ChatCreation {

    /**
     * Založí supergroupu ([channel] = `false`) nebo kanál a do skupiny přidá [memberChatIds]
     * (soukromé chaty, tedy lidi a boty). Nová skupina dostane výchozí dobu automatického
     * mazání z nastavení účtu ([DefaultAutoDeleteSettings]). Neprázdné [username] udělá chat
     * veřejným (`t.me/username`).
     */
    suspend fun create(
        title: String,
        description: String,
        channel: Boolean,
        memberChatIds: List<Long>,
        username: String = "",
    ): Result<CreatedChat>

    /**
     * Dá se [username] použít pro chat, který se teprve zakládá? Volá se až nad jménem, které
     * projde [usernameFormatError].
     */
    suspend fun checkUsername(username: String): Result<UsernameCheck>
}

/**
 * **Vždycky supergroupa, nikdy základní skupina.** `createNewBasicGroupChat` by u malé skupiny
 * stačil, jenže základní skupina nemá témata, část práv správců ani veřejný odkaz a při prvním
 * takovém kroku se převádí — jiné id chatu, které by aplikaci rozsypalo zásobník i koncepty.
 * Skupina a kanál tak jdou jedním voláním a liší se jen příznakem.
 */
class TdlChatCreation(private val holder: TelegramClientHolder) : ChatCreation {

    private val client: TdlClient get() = holder.client

    private val logger = Logger.withTag("ChatCreation")

    override suspend fun create(
        title: String,
        description: String,
        channel: Boolean,
        memberChatIds: List<Long>,
        username: String,
    ): Result<CreatedChat> = runCatching {
        // Výchozí doba platí „pro nově založené chaty", ale u supergroupy ji TDLib sám nepoužije —
        // posílá ji klient. Nezjištěná doba založení neblokuje.
        val autoDelete = newChatAutoDelete(client.getDefaultMessageAutoDeleteTime().toResult().getOrNull()?.time)
        val chat = client.createNewSupergroupChat(
            title = title,
            isForum = false,
            isChannel = channel,
            description = description,
            location = null,
            messageAutoDeleteTime = autoDelete,
            forImport = false,
        ).toResult().getOrThrow()

        val usernameFailed = username.isNotEmpty() && setUsername(chat.type, username).isFailure
        if (channel || memberChatIds.isEmpty()) return@runCatching CreatedChat(chat.id, usernameFailed = usernameFailed)

        // Výběr dává chaty; soukromý chat má id uživatele, ale ověřuje se to přes typ chatu —
        // tajný chat má id jiné.
        val userIds = memberChatIds.mapNotNull { chatId ->
            (client.getChat(chatId = chatId).toResult().getOrNull()?.type as? ChatTypePrivate)?.userId
        }
        if (userIds.isEmpty()) return@runCatching CreatedChat(chat.id, usernameFailed = usernameFailed)

        // Skupina už stojí: selhání přidání ji nesmí zahodit, jen se ohlásí, kdo v ní chybí.
        val failed = client.addChatMembers(chatId = chat.id, userIds = userIds.toLongArray()).toResult()
            .onFailure { logger.w(it) { "Členy se do nové skupiny ${chat.id} nepodařilo přidat." } }
            .map { result -> result.failedToAddMembers.map { it.userId } }
            .getOrElse { userIds }
        CreatedChat(
            chatId = chat.id,
            refused = failed.map { userId -> RefusedMember(userId, userName(userId)) },
            usernameFailed = usernameFailed,
        )
    }

    /**
     * Chat, který ještě neexistuje, se ptá s `chatId = 0` — tak to `checkChatUsername` pro
     * zakládaný chat chce. Strop veřejných chatů hlásí jako `PublicChatsTooMany`.
     */
    override suspend fun checkUsername(username: String): Result<UsernameCheck> =
        client.checkChatUsername(chatId = 0, username = username).toResult().map { it.toUsernameCheck() }

    /**
     * Veřejné jméno jde nastavit až stojící supergroupě (`createNewSupergroupChat` ho nebere).
     * Když to selže (jméno mezitím někdo vzal), chat zůstane soukromý — zakládat ho kvůli tomu
     * znovu by vyrobilo druhý.
     */
    private suspend fun setUsername(type: dev.g000sha256.tdl.dto.ChatType, username: String): Result<Unit> {
        val supergroupId = (type as? ChatTypeSupergroup)?.supergroupId
            ?: return Result.failure(IllegalStateException("Nový chat není supergroupa."))
        return client.setSupergroupUsername(supergroupId = supergroupId, username = username).toResult().map { }
            .onFailure { logger.w(it) { "Veřejné jméno nového chatu se nepodařilo nastavit." } }
    }

    private suspend fun userName(userId: Long): String =
        client.getUser(userId = userId).toResult().getOrNull()
            ?.let { user -> listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" ") }
            .orEmpty()
}

/**
 * Doba automatického mazání pro `createNewSupergroupChat`: TDLib bere jen celé dny do roku
 * a jinou hodnotu by odmítl i se založením. Neznámá nebo neplatná = bez mazání.
 */
internal fun newChatAutoDelete(defaultSeconds: Int?): Int =
    defaultSeconds?.takeIf { it in 0..MAX_AUTO_DELETE && it % DAY == 0 } ?: 0

private const val DAY = 86_400
private const val MAX_AUTO_DELETE = 365 * DAY
