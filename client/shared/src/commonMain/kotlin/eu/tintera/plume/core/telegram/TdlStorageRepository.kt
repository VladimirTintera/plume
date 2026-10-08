package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.dto.FileType
import dev.g000sha256.tdl.dto.FileTypeAnimation
import dev.g000sha256.tdl.dto.FileTypeAudio
import dev.g000sha256.tdl.dto.FileTypeDocument
import dev.g000sha256.tdl.dto.FileTypeLivePhotoVideo
import dev.g000sha256.tdl.dto.FileTypeNone
import dev.g000sha256.tdl.dto.FileTypeNotificationSound
import dev.g000sha256.tdl.dto.FileTypePhoto
import dev.g000sha256.tdl.dto.FileTypePhotoStory
import dev.g000sha256.tdl.dto.FileTypeProfilePhoto
import dev.g000sha256.tdl.dto.FileTypeSecret
import dev.g000sha256.tdl.dto.FileTypeSecretThumbnail
import dev.g000sha256.tdl.dto.FileTypeSecure
import dev.g000sha256.tdl.dto.FileTypeSelfDestructingLivePhotoVideo
import dev.g000sha256.tdl.dto.FileTypeSelfDestructingPhoto
import dev.g000sha256.tdl.dto.FileTypeSelfDestructingVideo
import dev.g000sha256.tdl.dto.FileTypeSelfDestructingVideoNote
import dev.g000sha256.tdl.dto.FileTypeSelfDestructingVoiceNote
import dev.g000sha256.tdl.dto.FileTypeSticker
import dev.g000sha256.tdl.dto.FileTypeThumbnail
import dev.g000sha256.tdl.dto.FileTypeUnknown
import dev.g000sha256.tdl.dto.FileTypeVideo
import dev.g000sha256.tdl.dto.FileTypeVideoNote
import dev.g000sha256.tdl.dto.FileTypeVideoStory
import dev.g000sha256.tdl.dto.FileTypeVoiceNote
import dev.g000sha256.tdl.dto.FileTypeWallpaper
import dev.g000sha256.tdl.dto.StorageStatisticsByChat
import dev.g000sha256.tdl.toResult

/**
 * Úložiště nad TDLib: `getStorageStatistics*` a `optimizeStorage`.
 *
 * Bezstavové — přehled se čte při každém otevření sekce znovu; co se mezitím stáhlo nebo smazalo,
 * ví jen TDLib.
 */
class TdlStorageRepository(private val holder: TelegramClientHolder) : StorageRepository {

    private val logger = Logger.withTag("Storage")

    private val client: TdlClient get() = holder.client

    override suspend fun databaseBytes(): Result<Long> =
        client.getStorageStatisticsFast().toResult().map { fast -> fast.databaseSize + fast.languagePackDatabaseSize }

    override suspend fun usage(): Result<StorageUsage> = runCatching {
        val statistics = client.getStorageStatistics(chatLimit = CHAT_LIMIT).toResult().getOrThrow()
        val chats = statistics.byChat.filterNotNull().map { it.toChatStorage() }.ordered()
        StorageUsage(
            filesBytes = statistics.size,
            categories = chats.flatMap { it.categories }.merged(),
            chats = chats,
        )
    }

    override suspend fun clear(categories: Set<StorageCategory>, chatId: Long?): Result<Long> {
        // Prázdný seznam druhů by pro TDLib znamenal „výchozí výběr", tedy smazat i to, co
        // uživatel odškrtl.
        val types = categories.flatMap { it.deletableTypes() }
        if (types.isEmpty()) return Result.success(0)
        return optimize(
            // Nula u všech mezí = smazat všechno, co do výběru spadá, bez ohledu na stáří.
            size = 0,
            ttl = 0,
            count = 0,
            immunityDelay = 0,
            fileTypes = types,
            chatIds = listOfNotNull(chatId),
        )
    }

    override suspend fun clearOlderThan(olderThanSeconds: Int): Result<Long> = optimize(
        /*
         * Meze velikosti a počtu VÝSLOVNĚ bez omezení. S -1 by TDLib vzal výchozí hodnoty
         * z voleb `storage_max_files_size` / `storage_max_file_count`, a úklid podle stáří by pak
         * mazal i podle velikosti — tedy víc, než uživatel volbou „Uchovávat média" řekl.
         */
        size = Long.MAX_VALUE,
        ttl = olderThanSeconds,
        count = Int.MAX_VALUE,
        // Výchozí doba, po kterou je čerstvý soubor nedotknutelný — automatický úklid nemá
        // smazat obrázek, který se právě stáhl.
        immunityDelay = -1,
        // Prázdné = výchozí výběr TDLib: všechno kromě náhledů, avatarů, samolepek a tapet.
        fileTypes = emptyList(),
        chatIds = emptyList(),
    )

    private suspend fun optimize(
        size: Long,
        ttl: Int,
        count: Int,
        immunityDelay: Int,
        fileTypes: List<FileType>,
        chatIds: List<Long>,
    ): Result<Long> =
        client.optimizeStorage(
            size = size,
            ttl = ttl,
            count = count,
            immunityDelay = immunityDelay,
            fileTypes = fileTypes.toTypedArray(),
            chatIds = chatIds.toLongArray(),
            excludeChatIds = LongArray(0),
            // Statistika SMAZANÝCH souborů, ne celého úložiště — z ní je „uvolněno N MB".
            returnDeletedFileStatistics = true,
            chatLimit = 0,
        )
            .toResult()
            .map { deleted -> deleted.size }
            .onSuccess { freed -> logger.i { "Úklid úložiště uvolnil $freed B (druhy=${fileTypes.size}, chaty=$chatIds, ttl=$ttl)" } }
            .onFailure { failure -> logger.w(failure) { "optimizeStorage selhalo" } }

    private suspend fun StorageStatisticsByChat.toChatStorage(): ChatStorage {
        val categories = byFileType.filterNotNull()
            .map { CategoryUsage(it.fileType.category(), it.size, it.count) }
            .merged()
        // Zbytek nemá chat; název dodá UI.
        val chat = chatId.takeIf { it != OTHER_CHATS }?.let { client.getChat(chatId = it).toResult().getOrNull() }
        return ChatStorage(
            chatId = chatId,
            title = chat?.title.orEmpty(),
            avatarFileId = chat?.photo?.small?.id,
            kind = chat?.type?.chatKind() ?: ChatKind.Private,
            bytes = size,
            categories = categories,
        )
    }

    private companion object {
        /** Kolik chatů s největším využitím přehled vyjmenuje; zbytek je jeden řádek. */
        const val CHAT_LIMIT = 30
    }
}

/** Kam druh souboru TDLib patří v přehledu. `when` bez `else`: nový druh rozbije překlad. */
private fun FileType.category(): StorageCategory = when (this) {
    is FileTypePhoto, is FileTypePhotoStory, is FileTypeSelfDestructingPhoto -> StorageCategory.Photos
    is FileTypeVideo, is FileTypeVideoStory, is FileTypeLivePhotoVideo, is FileTypeSelfDestructingVideo,
    is FileTypeSelfDestructingLivePhotoVideo -> StorageCategory.Videos
    is FileTypeVoiceNote, is FileTypeVideoNote, is FileTypeSelfDestructingVoiceNote,
    is FileTypeSelfDestructingVideoNote -> StorageCategory.VoiceMessages
    is FileTypeAudio -> StorageCategory.Music
    is FileTypeDocument -> StorageCategory.Files
    is FileTypeAnimation -> StorageCategory.Gifs
    is FileTypeSticker -> StorageCategory.Stickers
    is FileTypeThumbnail, is FileTypeProfilePhoto, is FileTypeWallpaper, is FileTypeNotificationSound,
    is FileTypeSecure, is FileTypeSecret, is FileTypeSecretThumbnail, is FileTypeUnknown,
    is FileTypeNone -> StorageCategory.Other
}

/**
 * Druhy, které se při mazání kategorie předají `optimizeStorage`. U „Ostatních" jen to, co
 * smazat jde a smí: náhledy tajných chatů TDLib nesmaže, zvuky notifikací a údaje Passportu
 * se znovu nestáhnou.
 */
private fun StorageCategory.deletableTypes(): List<FileType> = when (this) {
    StorageCategory.Photos -> listOf(FileTypePhoto(), FileTypePhotoStory(), FileTypeSelfDestructingPhoto())
    StorageCategory.Videos -> listOf(
        FileTypeVideo(), FileTypeVideoStory(), FileTypeLivePhotoVideo(), FileTypeSelfDestructingVideo(),
        FileTypeSelfDestructingLivePhotoVideo(),
    )
    StorageCategory.VoiceMessages -> listOf(
        FileTypeVoiceNote(), FileTypeVideoNote(), FileTypeSelfDestructingVoiceNote(), FileTypeSelfDestructingVideoNote(),
    )
    StorageCategory.Music -> listOf(FileTypeAudio())
    StorageCategory.Files -> listOf(FileTypeDocument())
    StorageCategory.Gifs -> listOf(FileTypeAnimation())
    StorageCategory.Stickers -> listOf(FileTypeSticker())
    StorageCategory.Other -> listOf(FileTypeThumbnail(), FileTypeProfilePhoto(), FileTypeWallpaper(), FileTypeUnknown())
}
