package eu.tintera.plume.core.share

import co.touchlab.kermit.Logger
import eu.tintera.plume.core.telegram.OutgoingAttachment
import eu.tintera.plume.core.telegram.OutgoingThumbnail
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * Kde sdílení nesené čipem ([SharedContent.carried]) přečká zabití procesu.
 *
 * Volá ho jen [ShareRequests], a to mimo hlavní vlákno (kromě [load] při jeho vzniku). Na
 * platformách bez úložiště (iOS, desktop) žádné není a sdílení žije jen v paměti — na iOS
 * vybírá chat rozšíření samo, takže čip tam nese jen sdílení zevnitř aplikace.
 */
interface ShareStore {

    /** Uložené sdílení, nebo `null`. Soubory, které mezitím zmizely (úklid cache), vynechá. */
    fun load(): StoredShare?

    /** Zapíše [share]; `null` záznam smaže. Soubory příloh nechává být. */
    fun write(share: StoredShare?)

    /** Smaže kopie souborů zahozeného sdílení (jen ty, které leží v jeho vlastní cache). */
    fun deleteFiles(paths: List<String>)
}

/**
 * Záznam sdílení na disku — manifest vedle kopií souborů, jako inbox iOS rozšíření.
 *
 * Nese jen to, co přežít musí: cíl (vybraný chat) ani potvrzování ne. Sdílení s chatem
 * vybraným předem (Direct Share, iOS) se neukládá vůbec a sdílení obnovené po startu je vždycky
 * zase na čipu — i když ho uživatel těsně před zabitím procesu vkládal do konverzace.
 */
@Serializable
data class StoredShare(
    /** Kdy sdílení přišlo (epoch ms); po [SHARE_LIFETIME_MS] se zahodí. */
    val createdAt: Long,
    val text: String? = null,
    val attachments: List<StoredAttachment> = emptyList(),
    val failed: Int = 0,
    val denied: Int = 0,
    val sources: Map<String, String> = emptyMap(),
)

/** Příloha v manifestu. Režim (spoiler, mizení) se neukládá — sdílení přichází vždycky bez něj. */
@Serializable
sealed interface StoredAttachment {
    val path: String

    @Serializable
    @SerialName("photo")
    data class Photo(override val path: String, val width: Int, val height: Int) : StoredAttachment

    @Serializable
    @SerialName("video")
    data class Video(
        override val path: String,
        val width: Int,
        val height: Int,
        val durationSeconds: Int,
        val thumbnail: StoredThumbnail? = null,
        val streamable: Boolean = false,
    ) : StoredAttachment

    @Serializable
    @SerialName("document")
    data class Document(override val path: String) : StoredAttachment

    @Serializable
    @SerialName("audio")
    data class Audio(
        override val path: String,
        val title: String? = null,
        val performer: String? = null,
        val durationSeconds: Int = 0,
        val cover: StoredThumbnail? = null,
    ) : StoredAttachment
}

@Serializable
data class StoredThumbnail(val path: String, val width: Int, val height: Int)

/** Jak dlouho sdílení na čipu vydrží: den. Pak je to zapomenutá věc, ne rozdělaná práce. */
const val SHARE_LIFETIME_MS: Long = 24L * 60 * 60 * 1000

internal fun OutgoingAttachment.toStored(): StoredAttachment = when (this) {
    is OutgoingAttachment.Photo -> StoredAttachment.Photo(path, width, height)
    is OutgoingAttachment.Video -> StoredAttachment.Video(
        path = path,
        width = width,
        height = height,
        durationSeconds = durationSeconds,
        thumbnail = thumbnail?.toStored(),
        streamable = streamable,
    )
    is OutgoingAttachment.Document -> StoredAttachment.Document(path)
    is OutgoingAttachment.Audio -> StoredAttachment.Audio(path, title, performer, durationSeconds, cover?.toStored())
}

internal fun StoredAttachment.toOutgoing(): OutgoingAttachment = when (this) {
    is StoredAttachment.Photo -> OutgoingAttachment.Photo(path, width, height)
    is StoredAttachment.Video -> OutgoingAttachment.Video(
        path = path,
        width = width,
        height = height,
        durationSeconds = durationSeconds,
        thumbnail = thumbnail?.toOutgoing(),
        streamable = streamable,
    )
    is StoredAttachment.Document -> OutgoingAttachment.Document(path)
    is StoredAttachment.Audio -> OutgoingAttachment.Audio(path, title, performer, durationSeconds, cover?.toOutgoing())
}

private fun OutgoingThumbnail.toStored() = StoredThumbnail(path, width, height)

private fun StoredThumbnail.toOutgoing() = OutgoingThumbnail(path, width, height)

/** Všechny soubory přílohy na disku — i náhled videa a obal skladby. */
internal fun OutgoingAttachment.files(): List<String> = listOfNotNull(
    path,
    (this as? OutgoingAttachment.Video)?.thumbnail?.path,
    (this as? OutgoingAttachment.Audio)?.cover?.path,
)

/**
 * Manifest v adresáři s kopiemi sdílených souborů ([directory], na Androidu
 * `cache/outgoing`, kam je `ShareIntake` kopíruje hned při příjmu).
 *
 * **Maže jen uvnitř [directory].** Kopie leží každá ve vlastní podsložce
 * (`outgoing/<uuid>/soubor`), a ta se maže celá; cesta, která do [directory] nepatří, se nechá
 * být — za chybu v záznamu nesmí zaplatit cizí soubor.
 */
class FileShareStore(
    private val directory: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : ShareStore {

    private val logger = Logger.withTag("Share")
    private val manifest = directory / MANIFEST
    private val json = Json { ignoreUnknownKeys = true }

    override fun load(): StoredShare? {
        val text = runCatching { fileSystem.read(manifest) { readUtf8() } }.getOrNull() ?: return null
        val stored = runCatching { json.decodeFromString(StoredShare.serializer(), text) }.getOrElse {
            logger.w(it) { "Uložené sdílení nejde přečíst, zahazuji ho." }
            write(null)
            return null
        }
        val present = stored.attachments
            .filter { fileSystem.exists(it.path.toPath()) }
            .map { attachment ->
                // Chybějící náhled není důvod zahodit video — jen se bez něj ukáže a odejde.
                when (attachment) {
                    is StoredAttachment.Video -> attachment.copy(
                        thumbnail = attachment.thumbnail?.takeIf { fileSystem.exists(it.path.toPath()) },
                    )
                    is StoredAttachment.Audio -> attachment.copy(
                        cover = attachment.cover?.takeIf { fileSystem.exists(it.path.toPath()) },
                    )
                    else -> attachment
                }
            }
        val missing = stored.attachments.size - present.size
        return stored.copy(attachments = present, failed = stored.failed + missing)
    }

    override fun write(share: StoredShare?) {
        runCatching {
            if (share == null) {
                fileSystem.delete(manifest, mustExist = false)
                return
            }
            fileSystem.createDirectories(directory)
            // Napřed do dočasného souboru a pak přejmenovat: zabití procesu uprostřed zápisu
            // nesmí nechat napůl zapsaný manifest.
            val temporary = directory / "$MANIFEST.tmp"
            fileSystem.write(temporary) { writeUtf8(json.encodeToString(StoredShare.serializer(), share)) }
            fileSystem.atomicMove(temporary, manifest)
        }.onFailure { logger.w(it) { "Sdílení se nepodařilo uložit." } }
    }

    override fun deleteFiles(paths: List<String>) {
        paths.map { it.toPath() }
            .mapNotNull { it.parent }
            .filter { it.parent == directory }
            .distinct()
            .forEach { folder ->
                runCatching { fileSystem.deleteRecursively(folder, mustExist = false) }
                    .onFailure { logger.w(it) { "Kopii sdíleného souboru se nepodařilo smazat." } }
            }
    }

    private companion object {
        const val MANIFEST = "shared-content.json"
    }
}
