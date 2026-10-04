package eu.tintera.plume.core.telegram

/**
 * Obsah složky Telegramu, jak ho upravuje editor. Složka nese dva druhy pravidel: vybrané chaty
 * ([pinnedChatIds], [includedChatIds]) a druhy chatů (kontakty, skupiny…), z nichž se pak
 * vynechávají [excludedChatIds] a ztlumené, přečtené či archivované.
 *
 * - **Co aplikace nenabízí, se přenáší** ([iconName], [colorId], [isShareable], připnuté
 *   chaty, vlastní emoji v názvu) — nastavit to mohl jiný klient a úprava názvu by to tiše
 *   smazala. Stejná úvaha jako zápis ztlumení celým objektem.
 */
data class ChatFolderDraft(
    val title: String = "",
    /** Vlastní emoji v názvu; platí jen pro [title], ve kterém přišly. */
    val titleEntities: List<MessageEntity> = emptyList(),
    val iconName: String? = null,
    val colorId: Int = -1,
    val isShareable: Boolean = false,
    val pinnedChatIds: List<Long> = emptyList(),
    val includedChatIds: List<Long> = emptyList(),
    val excludedChatIds: List<Long> = emptyList(),
    val includeContacts: Boolean = false,
    val includeNonContacts: Boolean = false,
    val includeGroups: Boolean = false,
    val includeChannels: Boolean = false,
    val includeBots: Boolean = false,
    val excludeMuted: Boolean = false,
    val excludeRead: Boolean = false,
    val excludeArchived: Boolean = false,
) {
    /** Výslovně vybrané chaty, připnuté napřed — tak je složka i ukazuje. */
    val chosenChatIds: List<Long> get() = (pinnedChatIds + includedChatIds).distinct()

    val includesAnyKind: Boolean
        get() = includeContacts || includeNonContacts || includeGroups || includeChannels || includeBots

    /**
     * Sdílet jde jen složku z výslovně vybraných chatů: TDLib odkaz na složku s druhy chatů
     * nebo vynechanými odmítne („Shareable folders can't have excluded chats").
     */
    val canBeShared: Boolean
        get() = !includesAnyKind && excludedChatIds.isEmpty() && !excludeMuted && !excludeRead && !excludeArchived

    /** Nový název; vlastní emoji patří starému textu, takže se zahodí. */
    fun withTitle(text: String): ChatFolderDraft =
        if (text == title) this else copy(title = text, titleEntities = emptyList())

    /** Výslovně vybrané chaty po úpravě v editoru; připnuté, které zůstaly, zůstávají připnuté. */
    fun withChosen(ids: List<Long>): ChatFolderDraft {
        val pinned = pinnedChatIds.filter { it in ids }
        return copy(
            pinnedChatIds = pinned,
            includedChatIds = ids.filterNot { it in pinned },
            // Chat, který je vybraný, nemůže být zároveň vynechaný.
            excludedChatIds = excludedChatIds.filterNot { it in ids },
        )
    }

    fun withExcluded(ids: List<Long>): ChatFolderDraft = copy(
        excludedChatIds = ids,
        pinnedChatIds = pinnedChatIds.filterNot { it in ids },
        includedChatIds = includedChatIds.filterNot { it in ids },
    )

    /**
     * Chat ven ze složky: z vybraných se odebere, a když by ho tam pořád držel druh chatu
     * ([coveredByKind]), přidá se mezi vynechané. Jinak by „Odebrat ze složky" nic neudělalo.
     */
    fun withoutChat(chatId: Long, coveredByKind: Boolean): ChatFolderDraft {
        val draft = copy(
            pinnedChatIds = pinnedChatIds - chatId,
            includedChatIds = includedChatIds - chatId,
        )
        return if (coveredByKind && chatId !in draft.excludedChatIds) {
            draft.copy(excludedChatIds = draft.excludedChatIds + chatId)
        } else {
            draft
        }
    }
}

/** Co v konceptu složky brání uložení; `null` = uložit jde. */
enum class ChatFolderProblem { MissingTitle, TitleTooLong, NoChats }

fun ChatFolderDraft.problem(): ChatFolderProblem? = when {
    title.isBlank() -> ChatFolderProblem.MissingTitle
    title.trim().length > CHAT_FOLDER_TITLE_MAX -> ChatFolderProblem.TitleTooLong
    // Server prázdnou složku odmítne: musí v ní být aspoň jeden chat nebo druh chatů.
    chosenChatIds.isEmpty() && !includesAnyKind -> ChatFolderProblem.NoChats
    else -> null
}

/** Nejdelší název složky, který TDLib přijme („1-12 characters"). */
const val CHAT_FOLDER_TITLE_MAX = 12

/** Složka, kterou Telegram doporučuje založit (Nepřečtené, Osobní…), s větou, co v ní bude. */
data class RecommendedChatFolder(val draft: ChatFolderDraft, val description: String, val iconName: String? = null)

/** Odkaz na sdílenou složku (`t.me/addlist/…`) a kolik chatů z ní nabízí. */
data class ChatFolderLink(val url: String, val name: String, val chatIds: List<Long>)

/** Ve kterých složkách chat je a do kterých jde přidat — pro „Přidat do složky" nad chatem. */
data class ChatFolderMembership(val member: Set<Int>, val canAdd: Set<Int>)

/**
 * Úpravy složek Telegramu. Seznam složek a jejich pořadí jsou živé v [ChatRepository.chatFolders]
 * (`UpdateChatFolders`), takže tady se jen zapisuje a čte obsah jedné složky.
 */
interface ChatFolderRepository {
    suspend fun folder(id: Int): Result<ChatFolderDraft>
    suspend fun create(draft: ChatFolderDraft): Result<Int>
    suspend fun edit(id: Int, draft: ChatFolderDraft): Result<Unit>
    suspend fun delete(id: Int): Result<Unit>
    suspend fun reorder(ids: List<Int>, mainPosition: Int): Result<Unit>
    suspend fun recommended(): Result<List<RecommendedChatFolder>>
    /**
     * Ikona, kterou by složce dal Telegram, kdyby si uživatel žádnou nevybral — podle toho, co ve
     * složce je (jen boti → robot…). Lokální výpočet, smí se volat při každé změně konceptu.
     */
    suspend fun defaultIconName(draft: ChatFolderDraft): Result<String>
    suspend fun membership(chatId: Long): Result<ChatFolderMembership>
    suspend fun addChat(folderId: Int, chatId: Long): Result<Unit>
    suspend fun removeChat(folderId: Int, chatId: Long): Result<Unit>

    /** Vlastní odkazy na sdílenou složku. */
    suspend fun links(folderId: Int): Result<List<ChatFolderLink>>

    /**
     * Nový odkaz se všemi chaty složky, které sdílet jde (skupiny a kanály, kam smí zvát;
     * `getChatsForChatFolderInviteLink`). Složka se tím stane sdílenou.
     */
    suspend fun createLink(folderId: Int): Result<ChatFolderLink>
    suspend fun deleteLink(folderId: Int, url: String): Result<Unit>
}

/** Ve složce není nic, co by šlo sdílet — jen lidé a boti, nebo skupiny bez práva zvát. */
class NothingToShareException : IllegalStateException("Ve složce není chat, který jde sdílet.")
