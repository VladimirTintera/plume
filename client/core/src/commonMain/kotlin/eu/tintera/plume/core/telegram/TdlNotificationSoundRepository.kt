package eu.tintera.plume.core.telegram

import co.touchlab.kermit.Logger
import dev.g000sha256.tdl.dto.InputFileLocal
import dev.g000sha256.tdl.dto.OptionValueInteger
import dev.g000sha256.tdl.toResult
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds
import dev.g000sha256.tdl.dto.NotificationSound as TdlNotificationSound

class TdlNotificationSoundRepository(private val holder: TelegramClientHolder) : NotificationSoundRepository {

    private val logger = Logger.withTag("NotificationSounds")

    /**
     * Update se nepřekládá, jen spouští nové čtení — a podle dokumentace nemusí přijít, dokud se
     * na tóny nikdo nezeptal, proto odběr a hned čtení.
     */
    override val tones: Flow<List<NotificationTone>> = channelFlow {
        launch(start = CoroutineStart.UNDISPATCHED) {
            holder.updates { savedNotificationSoundsUpdates }.collect { send(load()) }
        }
        send(load())
    }

    private suspend fun load(): List<NotificationTone> =
        holder.client.getSavedNotificationSounds().toResult()
            .onFailure { logger.w(it) { "Tóny notifikací se nepodařilo načíst." } }
            .getOrNull()?.notificationSounds?.map { it.toDomain() }.orEmpty()

    override suspend fun limits(): ToneLimits = ToneLimits(
        maxBytes = option("notification_sound_size_max") ?: DEFAULT_MAX_BYTES,
        maxSeconds = option("notification_sound_duration_max")?.toInt() ?: DEFAULT_MAX_SECONDS,
        maxCount = option("notification_sound_count_max")?.toInt() ?: DEFAULT_MAX_COUNT,
    )

    private suspend fun option(name: String): Long? =
        (holder.client.getOption(name = name).toResult().getOrNull() as? OptionValueInteger)?.value?.takeIf { it > 0 }

    override suspend fun add(path: String): Result<NotificationTone> =
        holder.client.addSavedNotificationSound(sound = InputFileLocal(path = path)).toResult()
            .map { it.toDomain() }
            .onFailure { logger.w(it) { "Tón se nepodařilo nahrát." } }

    override suspend fun remove(id: Long): Result<Unit> =
        holder.client.removeSavedNotificationSound(notificationSoundId = id).toResult().map { }
            .onFailure { logger.w(it) { "Tón se nepodařilo smazat." } }

    /**
     * Stáhne tón synchronně (je malý, desítky až stovky kB). Strop [DOWNLOAD_TIMEOUT]: volá se
     * i při skládání notifikace, která nesmí čekat donekonečna — bez tónu půjde výchozím kanálem.
     */
    override suspend fun tonePath(id: Long): String? = withTimeoutOrNull(DOWNLOAD_TIMEOUT) {
        val sound = holder.client.getSavedNotificationSound(notificationSoundId = id).toResult().getOrNull()
            ?: return@withTimeoutOrNull null
        val local = sound.sound.local
        if (local.isDownloadingCompleted && local.path.isNotBlank()) return@withTimeoutOrNull local.path
        holder.client.downloadFile(fileId = sound.sound.id, priority = 1, offset = 0, limit = 0, synchronous = true)
            .toResult()
            .onFailure { logger.w(it) { "Tón $id se nepodařilo stáhnout." } }
            .getOrNull()?.local?.takeIf { it.isDownloadingCompleted }?.path
    }

    override suspend fun toneTitle(id: Long): String? =
        holder.client.getSavedNotificationSound(notificationSoundId = id).toResult().getOrNull()?.title

    private fun TdlNotificationSound.toDomain() = NotificationTone(id = id, title = title, durationSeconds = duration)

    private companion object {
        val DOWNLOAD_TIMEOUT = 4.seconds
        const val DEFAULT_MAX_BYTES = 300L * 1024
        const val DEFAULT_MAX_SECONDS = 5
        const val DEFAULT_MAX_COUNT = 100
    }
}
