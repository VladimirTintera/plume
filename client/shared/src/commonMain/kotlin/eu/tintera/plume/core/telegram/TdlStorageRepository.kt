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
import dev.g000sha256.tdl.dto.NetworkStatisticsEntry
import dev.g000sha256.tdl.dto.NetworkStatisticsEntryCall
import dev.g000sha256.tdl.dto.NetworkStatisticsEntryFile
import dev.g000sha256.tdl.dto.NetworkType
import dev.g000sha256.tdl.dto.NetworkTypeMobile
import dev.g000sha256.tdl.dto.NetworkTypeMobileRoaming
import dev.g000sha256.tdl.dto.NetworkTypeNone
import dev.g000sha256.tdl.dto.NetworkTypeOther
import dev.g000sha256.tdl.dto.NetworkTypeWiFi
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

    override suspend fun networkUsage(): Result<NetworkUsage> =
        client.getNetworkStatistics(onlyCurrent = false).toResult()
            .map { statistics -> networkUsageOf(statistics.sinceDate, statistics.entries.toRecords()) }
            .onFailure { logger.w(it) { "Statistiku přenesených dat se nepodařilo načíst." } }

    override suspend fun resetNetworkUsage(): Result<Unit> =
        client.resetNetworkStatistics().toResult().map { }
            .onFailure { logger.w(it) { "Statistiku přenesených dat se nepodařilo vynulovat." } }
}

/**
 * Položky statistiky po našem. Síť „žádná" se vynechá, data bez souboru (`FileTypeNone`, v TDLib
 * „common") jsou zprávy.
 *
 * **Soubory se slučují podle počítadla, ne podle typu.** TDLib ukládá statistiku pod klíčem
 * `net_stats_<jméno>` a několik typů jméno sdílí (`get_file_type_unique_name` v `FileType.cpp`):
 * fotka, mizející fotka, živá fotka a mizející živá fotka jsou všechny „photos". Uložená čísla
 * se tak vracejí u každého z nich znovu — na emulátoru čtyři položky s přesně 26 544 / 849 729 B.
 * Sečtené daly čtyřnásobek a živá fotka navíc přibyla k videím. Ze skupiny se proto bere
 * maximum: uložená část je u všech stejná, liší se jen přenos od spuštění u typu, který ho měl.
 */
internal fun Array<NetworkStatisticsEntry>.toRecords(): List<TrafficRecord> {
    val files = filterIsInstance<NetworkStatisticsEntryFile>().mapNotNull { entry ->
        val network = entry.networkType.usageNetwork() ?: return@mapNotNull null
        val slot = entry.fileType.statsSlot()
        Triple(network, slot, entry)
    }
    val fileRecords = files.groupBy { (network, slot, _) -> network to slot }
        .map { (key, group) ->
            TrafficRecord(
                network = key.first,
                kind = key.second.kind,
                sentBytes = group.maxOf { it.third.sentBytes },
                receivedBytes = group.maxOf { it.third.receivedBytes },
            )
        }
    val calls = filterIsInstance<NetworkStatisticsEntryCall>().mapNotNull { call ->
        call.networkType.usageNetwork()?.let { network ->
            TrafficRecord(network, TrafficKind.Calls, call.sentBytes, call.receivedBytes, callSeconds = call.duration.toLong())
        }
    }
    return fileRecords + calls
}

/** Počítadlo statistiky TDLib (jméno klíče) a druh, kam patří. */
private enum class StatsSlot(val kind: TrafficKind) {
    Common(TrafficKind.Messages),
    Photos(TrafficKind.Photos),
    Stories(TrafficKind.Photos),
    VideoStories(TrafficKind.Videos),
    Videos(TrafficKind.Videos),
    Voice(TrafficKind.VoiceMessages),
    VideoNotes(TrafficKind.VoiceMessages),
    Documents(TrafficKind.Files),
    Music(TrafficKind.Music),
    Animations(TrafficKind.Gifs),
    Stickers(TrafficKind.Stickers),
    Thumbnails(TrafficKind.Other),
    ProfilePhotos(TrafficKind.Other),
    Wallpapers(TrafficKind.Other),
    NotificationSounds(TrafficKind.Other),
    Passport(TrafficKind.Other),
    Secret(TrafficKind.Other),
    SecretThumbnails(TrafficKind.Other),
    Temp(TrafficKind.Other),
}

/** Podle `get_file_type_unique_name` (TDLib `FileType.cpp`). `when` bez `else`: nový typ rozbije překlad. */
private fun FileType?.statsSlot(): StatsSlot = when (this) {
    null, is FileTypeNone -> StatsSlot.Common
    is FileTypePhoto, is FileTypeSelfDestructingPhoto, is FileTypeLivePhotoVideo,
    is FileTypeSelfDestructingLivePhotoVideo -> StatsSlot.Photos
    is FileTypePhotoStory -> StatsSlot.Stories
    is FileTypeVideoStory -> StatsSlot.VideoStories
    is FileTypeVideo, is FileTypeSelfDestructingVideo -> StatsSlot.Videos
    is FileTypeVoiceNote, is FileTypeSelfDestructingVoiceNote -> StatsSlot.Voice
    is FileTypeVideoNote, is FileTypeSelfDestructingVideoNote -> StatsSlot.VideoNotes
    is FileTypeDocument -> StatsSlot.Documents
    is FileTypeAudio -> StatsSlot.Music
    is FileTypeAnimation -> StatsSlot.Animations
    is FileTypeSticker -> StatsSlot.Stickers
    is FileTypeThumbnail -> StatsSlot.Thumbnails
    is FileTypeProfilePhoto -> StatsSlot.ProfilePhotos
    is FileTypeWallpaper -> StatsSlot.Wallpapers
    is FileTypeNotificationSound -> StatsSlot.NotificationSounds
    is FileTypeSecure -> StatsSlot.Passport
    is FileTypeSecret -> StatsSlot.Secret
    is FileTypeSecretThumbnail -> StatsSlot.SecretThumbnails
    is FileTypeUnknown -> StatsSlot.Temp
}

private fun NetworkType.usageNetwork(): UsageNetwork? = when (this) {
    is NetworkTypeMobile -> UsageNetwork.Mobile
    is NetworkTypeMobileRoaming -> UsageNetwork.Roaming
    is NetworkTypeWiFi -> UsageNetwork.WiFi
    is NetworkTypeOther -> UsageNetwork.Other
    is NetworkTypeNone -> null
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
