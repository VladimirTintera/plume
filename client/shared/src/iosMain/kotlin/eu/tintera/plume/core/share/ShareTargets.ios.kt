package eu.tintera.plume.core.share

import org.jetbrains.compose.resources.getString
import eu.tintera.plume.resources.saved_messages
import eu.tintera.plume.resources.Res
import co.touchlab.kermit.Logger
import eu.tintera.plume.core.navigation.ChatOpenRequests
import eu.tintera.plume.core.telegram.ChatListState
import eu.tintera.plume.core.telegram.ChatRepository
import eu.tintera.plume.core.telegram.FileRepository
import eu.tintera.plume.core.telegram.FileState
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.TelegramClientHolder
import eu.tintera.plume.core.ui.PICK_LIMIT
import eu.tintera.plume.core.ui.outgoingPath
import eu.tintera.plume.core.ui.prepareDocument
import eu.tintera.plume.core.ui.preparePhotoData
import eu.tintera.plume.core.ui.prepareVideoAt
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/*
 * Sdílení do aplikace na iOS jde přes rozšíření (Share Extension, `iosApp/ShareExtension`), které
 * je SAMOSTATNÝ PROCES: TDLib v něm neběží a databázi aplikace nevidí. Obě strany si proto
 * předávají soubory přes sdílený kontejner App Group:
 *
 *   share/targets.json               snímek chatů pro výběr v rozšíření (píše aplikace)
 *   share/avatars/<chat>-<file>.jpg  profilovky ke snímku
 *   share/inbox/<uuid>/manifest.json co a kam sdílet (píše rozšíření, POSLEDNÍ — až je vše zkopírované)
 *   share/inbox/<uuid>/<soubory>
 *
 * Formát musí sedět s `ShareModels.swift` v rozšíření.
 */

/** Adresář `share` v kontejneru App Group; `null`, když skupina není (build bez entitlementu). */
@OptIn(ExperimentalForeignApi::class)
internal fun shareDirectory(): String? {
    val group = NSBundle.mainBundle.objectForInfoDictionaryKey(APP_GROUP_KEY) as? String ?: return null
    val container = NSFileManager.defaultManager.containerURLForSecurityApplicationGroupIdentifier(group) ?: return null
    return container.path + "/share"
}

/** Klíč Info.plist s identifikátorem App Group; stejný v aplikaci i v rozšíření. */
private const val APP_GROUP_KEY = "PlumeAppGroup"

@Serializable
internal data class ShareSnapshot(val chats: List<ShareSnapshotChat>, val recent: List<Long> = emptyList())

@Serializable
internal data class ShareSnapshotChat(val id: Long, val title: String, val avatar: String? = null, val saved: Boolean = false)

@Serializable
internal data class ShareManifest(val chatId: Long, val text: String? = null, val items: List<ShareManifestItem> = emptyList())

/** [kind]: `image`, `video`, nebo `file`. [file] je jméno souboru ve složce sdílení. */
@Serializable
internal data class ShareManifestItem(val file: String, val kind: String)

private val json = Json { ignoreUnknownKeys = true }

/**
 * Snímek chatů pro výběr v rozšíření.
 *
 * Nahoře naposledy použité ([markUsed] — stejný signál jako Direct Share na Androidu), pod nimi
 * zbytek v pořadí seznamu chatů, nejvýš [MAX_CHATS]. Přepisuje se s odstupem po každé změně
 * seznamu; profilovky se kopírují jen stažené (rozšíření si je stáhnout neumí) a staré se mažou.
 * **Po odhlášení se snímek smaže** — rozšíření by jinak nabízelo chaty předchozího účtu.
 */
@OptIn(FlowPreview::class, ExperimentalForeignApi::class)
class IosShareSnapshot(
    private val chats: ChatRepository,
    private val files: FileRepository,
    private val holder: TelegramClientHolder,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("Share")
    private val recent = MutableStateFlow(readSnapshot()?.recent.orEmpty())
    private val writing = Mutex()

    fun markUsed(chatId: Long) {
        recent.update { (listOf(chatId) + it.filterNot { id -> id == chatId }).take(MAX_RECENT) }
    }

    fun start() {
        scope.launch {
            combine(
                chats.chats.filterIsInstance<ChatListState.Ready>(),
                recent,
                chats.savedMessagesChatId,
            ) { list, used, saved -> Triple(list.chats, used, saved) }
                .debounce(WRITE_DEBOUNCE_MS)
                .collect { (list, used, saved) -> writing.withLock { write(list, used, saved) } }
        }
        scope.launch {
            holder.replaced.collect {
                recent.value = emptyList()
                writing.withLock { shareDirectory()?.let { wipe(it) } }
            }
        }
    }

    private suspend fun write(
        list: List<eu.tintera.plume.core.telegram.ChatSummary>,
        used: List<Long>,
        savedMessagesChatId: Long?,
    ) = withContext(Dispatchers.IO) {
        val directory = shareDirectory() ?: return@withContext
        val avatars = "$directory/avatars"
        val manager = NSFileManager.defaultManager
        manager.createDirectoryAtPath(avatars, withIntermediateDirectories = true, attributes = null, error = null)

        val byId = list.associateBy { it.id }
        val ordered = (used.mapNotNull { byId[it] } + list.filterNot { it.id in used })
            .filterNot { it.id == savedMessagesChatId }
            .take(MAX_CHATS)

        // Uložené zprávy vždy první a pod vlastním názvem, i když v seznamu chatů nejsou.
        val saved = savedMessagesChatId?.let {
            ShareSnapshotChat(id = it, title = getString(Res.string.saved_messages), saved = true)
        }

        val entries = listOfNotNull(saved) + ordered.map { chat ->
            val avatar = chat.avatarFileId?.let { fileId ->
                val name = "${chat.id}-$fileId.jpg"
                val target = "$avatars/$name"
                if (manager.fileExistsAtPath(target)) return@let name
                val source = withTimeoutOrNull(AVATAR_WAIT_MS) {
                    (files.file(fileId).first { it is FileState.Ready || it is FileState.Failed } as? FileState.Ready)?.path
                } ?: return@let null
                name.takeIf { manager.copyItemAtPath(source, toPath = target, error = null) }
            }
            ShareSnapshotChat(id = chat.id, title = chat.title, avatar = avatar)
        }

        // Profilovky, které už ve snímku nejsou (jiný chat, nová fotka), pryč.
        val kept = entries.mapNotNull { it.avatar }.toSet()
        manager.contentsOfDirectoryAtPath(avatars, error = null)
            ?.filterIsInstance<String>()
            ?.filterNot { it in kept }
            ?.forEach { manager.removeItemAtPath("$avatars/$it", error = null) }

        val text = json.encodeToString(ShareSnapshot.serializer(), ShareSnapshot(entries, used))
        val written = (text as NSString).writeToFile("$directory/targets.json", atomically = true, encoding = NSUTF8StringEncoding, error = null)
        if (!written) logger.w { "Snímek chatů pro sdílení se nepodařilo zapsat." }
    }

    private fun wipe(directory: String) {
        val manager = NSFileManager.defaultManager
        manager.removeItemAtPath("$directory/targets.json", error = null)
        manager.removeItemAtPath("$directory/avatars", error = null)
        // Nevyzvednuté sdílení patří odhlášenému účtu a jeho chatům.
        manager.removeItemAtPath("$directory/inbox", error = null)
    }

    private fun readSnapshot(): ShareSnapshot? {
        val path = (shareDirectory() ?: return null) + "/targets.json"
        val text = NSString.stringWithContentsOfFile(path, encoding = NSUTF8StringEncoding, error = null) ?: return null
        return runCatching { json.decodeFromString(ShareSnapshot.serializer(), text) }.getOrNull()
    }

    private companion object {
        const val MAX_CHATS = 50
        const val MAX_RECENT = 10
        const val WRITE_DEBOUNCE_MS = 1_000L
        const val AVATAR_WAIT_MS = 500L
    }
}

/** iOS: použitý chat jde ve snímku pro rozšíření nahoru (obdoba Direct Share). */
class IosShareTargetPublisher(private val snapshot: IosShareSnapshot) : ShareTargetPublisher {
    override fun publish(chatId: Long, title: String, avatarPath: String?, isSavedMessages: Boolean) =
        snapshot.markUsed(chatId)
}

/**
 * Vyzvedne sdílení, která rozšíření nechalo v `share/inbox`, a předá je [ShareRequests].
 *
 * Běží při každém návratu aplikace do popředí ([isForeground]) — rozšíření aplikaci po výběru
 * chatu otevře, a kdyby to nevyšlo, sdílení počká na nejbližší spuštění. Chat je vybraný už
 * v rozšíření, takže se výběr v aplikaci přeskočí a otevře se rovnou ten chat.
 *
 * **Soubory se přesunou do `outgoing` a teprve pak připraví**, stejnou cestou jako výběr příloh
 * (fotka zmenšená na JPEG, video přebalené do MP4). Složka v inboxu se smaže hned — kontejner
 * App Group systém sám neuklízí.
 */
@OptIn(ExperimentalForeignApi::class)
class IosShareInbox(
    private val requests: ShareRequests,
    private val chatOpenRequests: ChatOpenRequests,
    private val scope: CoroutineScope,
) {
    private val logger = Logger.withTag("Share")
    private val collecting = Mutex()

    fun start(isForeground: Flow<Boolean>) {
        scope.launch {
            isForeground.filter { it }.collect { collect() }
        }
    }

    private suspend fun collect() = collecting.withLock {
        val inbox = (shareDirectory() ?: return@withLock) + "/inbox"
        val manager = NSFileManager.defaultManager
        val shares = manager.contentsOfDirectoryAtPath(inbox, error = null)?.filterIsInstance<String>().orEmpty()
        // Nejnovější poslední: když jich čeká víc, platí to, co uživatel sdílel naposledy.
        shares.sorted().forEach { id -> receive("$inbox/$id") }
    }

    private suspend fun receive(directory: String) {
        val manager = NSFileManager.defaultManager
        val text = NSString.stringWithContentsOfFile("$directory/manifest.json", encoding = NSUTF8StringEncoding, error = null)
            // Rozšíření ještě zapisuje (manifest jde poslední), nebo je složka rozbitá — počká se.
            ?: return
        val manifest = runCatching { json.decodeFromString(ShareManifest.serializer(), text) }.getOrElse {
            logger.w(it) { "Sdílení z rozšíření nejde přečíst: $directory" }
            manager.removeItemAtPath(directory, error = null)
            return
        }

        val items = manifest.items.take(PICK_LIMIT)
        // Soubory se přesunou k nám dřív, než se složka smaže; připravují se až potom.
        val moved = items.mapNotNull { item ->
            val target = outgoingPath(item.file)
            target.takeIf { manager.moveItemAtPath("$directory/${item.file}", toPath = it, error = null) }?.let { it to item.kind }
        }
        manager.removeItemAtPath(directory, error = null)

        val id = requests.start(text = manifest.text, hasFiles = moved.isNotEmpty())
        requests.choose(manifest.chatId)
        chatOpenRequests.request(manifest.chatId)
        if (moved.isEmpty()) return

        // Fotky a videa jako média, jen když jsou médii všechny — stejné pravidlo jako Android.
        val visual = moved.all { (_, kind) -> kind == KIND_IMAGE || kind == KIND_VIDEO }
        val results = moved
            .map { (path, kind) -> scope.async(Dispatchers.IO) { runCatching { prepare(path, kind, visual) } } }
            .awaitAll()
        results.forEach { result -> result.exceptionOrNull()?.let { logger.w(it) { "Sdílený soubor se nepodařilo připravit." } } }
        requests.prepared(
            id = id,
            attachments = results.mapNotNull { it.getOrNull() },
            failed = results.count { it.isFailure } + (manifest.items.size - moved.size),
        )
    }

    private suspend fun prepare(path: String, kind: String, visual: Boolean): OutgoingAttachment = when {
        !visual -> prepareDocument(NSURL.fileURLWithPath(path))
        kind == KIND_VIDEO -> prepareVideoAt(path)
        else -> preparePhotoData(NSData.dataWithContentsOfFile(path) ?: error("Obrázek nejde načíst."))
    }

    private companion object {
        const val KIND_IMAGE = "image"
        const val KIND_VIDEO = "video"
    }
}
