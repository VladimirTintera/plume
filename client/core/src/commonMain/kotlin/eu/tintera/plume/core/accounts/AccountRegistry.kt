package eu.tintera.plume.core.accounts

import co.touchlab.kermit.Logger
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * Seznam účtů na disku: sloty, jejich user id a výchozí účet ([Accounts]).
 *
 * Malý soubor JSON vedle dat účtů (`accounts/accounts.json`), ne `AppSettings`: seznam potřebuje
 * vrstva kolem TDLib dřív, než vznikne cokoli z aplikace, a nastavení je v `:client:shared`.
 * Zapisuje se přes dočasný soubor a přejmenování, takže pád uprostřed zápisu nechá starý seznam.
 *
 * **Chybějící nebo nečitelný soubor = jen primární účet.** Tak vypadá každá instalace z doby
 * před víc účty, a tak se má chovat i poškozený soubor: primární slot je na dnešních cestách,
 * takže o jeho data nikdo nepřijde. Další sloty by se v tom případě ztratily z aplikace, ale
 * jejich adresáře zůstanou na disku.
 */
class AccountRegistry(
    private val path: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) {

    constructor(path: String) : this(path.toPath())

    private val logger = Logger.withTag("Accounts")

    internal fun load(): SavedAccounts? {
        if (!fileSystem.exists(path)) return null
        return runCatching { json.decodeFromString<SavedAccounts>(fileSystem.read(path) { readUtf8() }) }
            .onFailure { logger.w(it) { "Seznam účtů nejde přečíst, beru jen primární účet." } }
            .getOrNull()
    }

    internal fun save(saved: SavedAccounts) {
        path.parent?.let(fileSystem::createDirectories)
        val temporary = path.parent?.div("${path.name}.tmp") ?: "${path.name}.tmp".toPath()
        fileSystem.write(temporary) { writeUtf8(json.encodeToString(SavedAccounts.serializer(), saved)) }
        fileSystem.atomicMove(temporary, path)
    }

    /**
     * Smaže adresáře slotů vedle seznamu, které v něm nejsou ([keep]): přidání účtu přerušené
     * zabitím procesu po sobě nechá adresář TDLib, ke kterému se už nikdo nedostane.
     */
    internal fun removeOrphans(keep: Set<String>) {
        val directory = path.parent ?: return
        val orphans = runCatching { fileSystem.list(directory) }.getOrDefault(emptyList())
            .filter { it != path && it.name !in keep && fileSystem.metadataOrNull(it)?.isDirectory == true }
        orphans.forEach { orphan ->
            logger.i { "Mažu adresář slotu ${orphan.name}, který v seznamu účtů není." }
            runCatching { fileSystem.deleteRecursively(orphan) }
        }
    }

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
internal data class SavedAccounts(
    val accounts: List<SavedAccount>,
    /** Slot výchozího účtu; neznámý nebo chybějící = první v seznamu. */
    val default: String? = null,
)

@Serializable
internal data class SavedAccount(
    val slot: String,
    /** User id přihlášeného účtu; `null`, dokud se nepřihlásil. */
    val userId: Long? = null,
    /** Id příjemce pushů z poslední registrace; podle něj se pozná, komu push patří. */
    val pushReceiverId: Long? = null,
)

/** Soubor se seznamem účtů v datech aplikace (viz [AccountRegistry]). */
const val ACCOUNTS_REGISTRY_FILE = "$ACCOUNTS_DIRECTORY/accounts.json"
