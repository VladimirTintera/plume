package eu.tintera.plume.core.telegram

/**
 * Místo, které na disku zabírají soubory TDLib (stažená média, avatary, samolepky).
 *
 * Soubory jde smazat bez ztráty čehokoli — zůstávají na serveru a stáhnou se znovu, až je někdo
 * otevře. Databázi (historie zpráv a chatů) tohle rozhraní nemaže: TDLib na to nemá volání,
 * zmizí jen s odhlášením.
 */
interface StorageRepository {

    /**
     * Velikost databáze TDLib (zprávy, chaty, jazykový balíček) — rychle, bez procházení souborů
     * (`getStorageStatisticsFast`). Velikost SOUBORŮ z téhož volání se nebere: je to zastaralý
     * odhad (na emulátoru 12 MB proti 65 MB z [usage]).
     */
    suspend fun databaseBytes(): Result<Long>

    /** Podrobný přehled po druzích souborů a po chatech. Může trvat vteřiny. */
    suspend fun usage(): Result<StorageUsage>

    /**
     * Smaže stažené soubory daných druhů — v chatu [chatId], nebo všude. Vrací uvolněné bajty.
     * Smaže i soubory, které právě vznikly; co se zrovna stahuje nebo nahrává, TDLib nechá být.
     */
    suspend fun clear(categories: Set<StorageCategory>, chatId: Long? = null): Result<Long>

    /**
     * Smaže soubory, na které se nesáhlo déle než [olderThanSeconds]. Úklid podle „Uchovávat
     * média"; samolepky, avatary a náhledy nechá být (výchozí výběr TDLib). Vrací uvolněné bajty.
     */
    suspend fun clearOlderThan(olderThanSeconds: Int): Result<Long>
}

/** Druh souboru, jak ho rozlišuje přehled. Pořadí je pořadí v dialogu mazání. */
enum class StorageCategory {
    Photos,
    Videos,

    /** Hlasovky a video kolečka — obojí je „vzkaz", ne médium, které se archivuje. */
    VoiceMessages,
    Music,
    Files,
    Gifs,
    Stickers,

    /** Avatary, náhledy, tapety a soubory, které TDLib nezařadí. */
    Other,
}

/** Co se v dialogu mazání předvybere: média ano, samolepky a avatary (malé, stále potřebné) ne. */
val DEFAULT_CLEAR_CATEGORIES: Set<StorageCategory> =
    StorageCategory.entries.toSet() - StorageCategory.Stickers - StorageCategory.Other

data class CategoryUsage(val category: StorageCategory, val bytes: Long, val count: Int)

data class ChatStorage(
    /** [OTHER_CHATS] = soubory chatů mimo nejzabranější a soubory, které k chatu nepatří. */
    val chatId: Long,
    val title: String,
    val avatarFileId: Int?,
    val kind: ChatKind,
    val bytes: Long,
    val categories: List<CategoryUsage>,
)

data class StorageUsage(
    val filesBytes: Long,
    /** Seřazené od největšího, bez prázdných. */
    val categories: List<CategoryUsage>,
    /** Seřazené od největšího; [ChatStorage.chatId] == [OTHER_CHATS] je vždycky poslední. */
    val chats: List<ChatStorage>,
)

/** Chat v přehledu, za kterým se skrývá zbytek — TDLib ho vrací s id 0. */
const val OTHER_CHATS = 0L

/**
 * Sečte využití po kategoriích (jeden druh souboru TDLib je jen část kategorie), zahodí prázdné
 * a seřadí od největšího. Stejné pořadí pak drží přehled i dialog mazání.
 */
fun List<CategoryUsage>.merged(): List<CategoryUsage> =
    groupBy { it.category }
        .map { (category, parts) -> CategoryUsage(category, parts.sumOf { it.bytes }, parts.sumOf { it.count }) }
        .filter { it.bytes > 0 }
        .sortedWith(compareByDescending<CategoryUsage> { it.bytes }.thenBy { it.category.ordinal })

/**
 * Chaty seřazené od největšího, se zbytkem ([OTHER_CHATS]) na konci a sloučeným do jednoho
 * řádku — TDLib ho může vrátit víckrát. Prázdné chaty pryč.
 */
fun List<ChatStorage>.ordered(): List<ChatStorage> {
    val (others, chats) = partition { it.chatId == OTHER_CHATS }
    val rest = others.takeIf { it.isNotEmpty() }?.let { parts ->
        parts.first().copy(
            bytes = parts.sumOf { it.bytes },
            categories = parts.flatMap { it.categories }.merged(),
        )
    }
    return (chats.sortedByDescending { it.bytes } + listOfNotNull(rest)).filter { it.bytes > 0 }
}
